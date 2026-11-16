package dev.azagra.launcher_aosp.data

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.Collator
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {

    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    val packs = IconPacks(ctx)
    private val cacheDir = File(ctx.cacheDir, "icon_cache").apply { mkdirs() }

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _newPackages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val newPackages: SharedFlow<String> = _newPackages

    @Volatile
    private var config = IconConfig()
    private var job: Job? = null

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val action = i?.action ?: return
                val pkg = i.data?.schemeSpecificPart
                val replacing = i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (pkg != null) {
                    if (action == Intent.ACTION_PACKAGE_REMOVED && !replacing) {
                        removePackage(pkg)
                    } else {
                        refreshPackage(pkg)
                    }
                } else {
                    refresh()
                }
                if (action == Intent.ACTION_PACKAGE_ADDED && !replacing && pkg != null) {
                    _newPackages.tryEmit(pkg)
                }
            }
        }, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun configure(c: IconConfig) {
        if (c != config) {
            config = c
            refresh()
        }
    }

    fun refresh() {
        job?.cancel()
        val c = config
        job = scope.launch(Dispatchers.IO) {
            val list = load(c)
            ensureActive()
            _apps.value = list
        }
    }

    private fun removePackage(pkg: String) {
        scope.launch(Dispatchers.IO) {
            invalidatePackageCache(pkg)
            val current = _apps.value
            if (current.any { it.pkg == pkg }) {
                _apps.value = current.filter { it.pkg != pkg }
            }
        }
    }

    private fun refreshPackage(pkg: String) {
        val c = config
        scope.launch(Dispatchers.IO) {
            invalidatePackageCache(pkg)
            packs.load(c.pack)
            val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
            val updatedForPkg = profiles.flatMap { user ->
                runCatching { launcherApps.getActivityList(pkg, user) }.getOrDefault(emptyList())
                    .filter { it.componentName.packageName != ctx.packageName }
                    .map { info -> buildAppInfo(info, user, c) }
            }
            val collator = Collator.getInstance()
            val merged = (_apps.value.filter { it.pkg != pkg } + updatedForPkg)
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
            _apps.value = merged
        }
    }

    fun launchApp(context: Context, app: AppInfo, sourceBounds: Rect? = null) {
        val opts = if (sourceBounds != null && sourceBounds.width() > 0 && sourceBounds.height() > 0) {
            ActivityOptions.makeClipRevealAnimation(
                (context as? android.app.Activity)?.window?.decorView ?: return launchFallback(context, app),
                sourceBounds.left,
                sourceBounds.top,
                sourceBounds.width(),
                sourceBounds.height(),
            ).toBundle()
        } else {
            null
        }
        val launched = runCatching {
            launcherApps.startMainActivity(app.component, app.user ?: Process.myUserHandle(), sourceBounds, opts)
        }.isSuccess
        if (!launched) {
            launchFallback(context, app)
        }
    }

    private fun launchFallback(context: Context, app: AppInfo) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(i) }
    }

    fun getShortcuts(pkg: String): List<AppShortcut> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().apply {
            setPackage(pkg)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        }
        launcherApps.getShortcuts(q, Process.myUserHandle())
            ?.filter { it.isEnabled }
            ?.sortedBy { it.rank }
            ?.take(4)
            ?.mapNotNull { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                AppShortcut(info.id, info.`package`, label)
            }
            ?: emptyList()
    }.getOrDefault(emptyList())

    fun launchShortcut(shortcut: AppShortcut) {
        runCatching {
            launcherApps.startShortcut(shortcut.pkg, shortcut.id, null, null, Process.myUserHandle())
        }
    }

    fun defaultDock(apps: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull { i ->
            val pkg = pm.resolveActivity(
                i, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )?.activityInfo?.packageName
            apps.firstOrNull { it.component.packageName == pkg }?.key
        }.distinct().take(DOCK_MAX)
    }

    private fun load(c: IconConfig): List<AppInfo> {
        packs.load(c.pack)
        val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val collator = Collator.getInstance()
        return profiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .filter { it.componentName.packageName != ctx.packageName }
                .map { info -> buildAppInfo(info, user, c) }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun buildAppInfo(info: LauncherActivityInfo, user: UserHandle, c: IconConfig): AppInfo {
        val (normalIcon, homeIcon) = loadOrRender(info, user, c)
        val userKey = if (user == Process.myUserHandle()) {
            info.componentName.flattenToString()
        } else {
            "${info.componentName.flattenToString()}#${user.hashCode()}"
        }
        return AppInfo(
            component = info.componentName,
            label = info.label.toString(),
            icon = normalIcon,
            homeIcon = homeIcon,
            pkg = info.componentName.packageName ?: "",
            key = userKey,
            user = user,
        )
    }

    private fun loadOrRender(
        info: LauncherActivityInfo,
        user: UserHandle,
        c: IconConfig,
    ): Pair<ImageBitmap, ImageBitmap> {
        val daySuffix = if (c.pack != null) "_d${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)}" else ""
        val cfgKey = "${c.pack ?: "sys"}_${c.themed}_${c.dark}_${c.shape.name}$daySuffix"
        val baseId = "${info.componentName.packageName}_${info.componentName.className}_${user.hashCode()}_${cfgKey.hashCode()}"
        val normalFile = File(cacheDir, "${baseId}_n.png")
        val homeFile = File(cacheDir, "${baseId}_h.png")

        val cachedNormal = decodeCached(normalFile)
        val cachedHome = if (c.themed && c.pack == null) decodeCached(homeFile) else cachedNormal
        if (cachedNormal != null && cachedHome != null) {
            return cachedNormal to cachedHome
        }

        val (normalBmp, homeBmp) = renderBitmaps(info, c)
        saveCached(normalFile, normalBmp)
        if (homeBmp !== normalBmp) {
            saveCached(homeFile, homeBmp)
        }
        return normalBmp.asImageBitmap() to homeBmp.asImageBitmap()
    }

    private fun decodeCached(file: File): ImageBitmap? {
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    private fun saveCached(file: File, bmp: Bitmap) {
        runCatching {
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    private fun invalidatePackageCache(pkg: String) {
        val prefix = "${pkg}_"
        cacheDir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix)) {
                f.delete()
            }
        }
    }

    private fun renderBitmaps(info: LauncherActivityInfo, c: IconConfig): Pair<Bitmap, Bitmap> {
        val systemDrawable: Drawable = info.getIcon(0)
        val packBitmap = if (c.pack != null) {
            packs.renderBitmap(info.componentName, systemDrawable, ICON_PX)
        } else null

        val rawNormal = packBitmap ?: systemDrawable.toBitmap(ICON_PX, ICON_PX)
        val normalBitmap = clipBitmapToShape(rawNormal, c.shape)
        if (normalBitmap !== rawNormal) rawNormal.recycle()

        val homeBitmap =
            if (packBitmap == null && c.themed && systemDrawable is AdaptiveIconDrawable) {
                val rawThemed = themed(systemDrawable, c.dark)?.toBitmap(ICON_PX, ICON_PX)
                if (rawThemed != null) {
                    val clipped = clipBitmapToShape(rawThemed, c.shape)
                    if (clipped !== rawThemed) rawThemed.recycle()
                    clipped
                } else {
                    normalBitmap
                }
            } else {
                normalBitmap
            }
        return normalBitmap to homeBitmap
    }

    private fun clipBitmapToShape(src: Bitmap, shape: IconShape): Bitmap {
        if (shape == IconShape.DEFAULT) return src
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = Path().apply {
            when (shape) {
                IconShape.DEFAULT -> addRect(0f, 0f, w, h, Path.Direction.CW)
                IconShape.CIRCLE -> addOval(0f, 0f, w, h, Path.Direction.CW)
                IconShape.SQUIRCLE -> addRoundRect(0f, 0f, w, h, w * 0.30f, h * 0.30f, Path.Direction.CW)
                IconShape.ROUNDED -> addRoundRect(0f, 0f, w, h, w * 0.20f, h * 0.20f, Path.Direction.CW)
                IconShape.TEARDROP -> addRoundRect(
                    RectF(0f, 0f, w, h),
                    floatArrayOf(w * 0.50f, h * 0.50f, w * 0.50f, h * 0.50f, w * 0.14f, h * 0.14f, w * 0.50f, h * 0.50f),
                    Path.Direction.CW,
                )
            }
        }
        canvas.clipPath(path)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun themed(d: AdaptiveIconDrawable, dark: Boolean): Drawable? {
        val mono = d.monochrome?.mutate() ?: return null
        val bg = ctx.getColor(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100)
        val fg = ctx.getColor(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_800)
        mono.setTint(fg)
        return AdaptiveIconDrawable(ColorDrawable(bg), mono)
    }

    private companion object {
        const val ICON_PX = 192
    }
}
