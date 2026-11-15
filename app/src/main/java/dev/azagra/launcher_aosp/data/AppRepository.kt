package dev.azagra.launcher_aosp.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {

    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    val packs = IconPacks(ctx)

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
                refresh()
                if (i?.action == Intent.ACTION_PACKAGE_ADDED && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    i.data?.schemeSpecificPart?.let { pkg -> _newPackages.tryEmit(pkg) }
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
        job = scope.launch {
            val list = load(c)
            ensureActive()
            _apps.value = list
        }
    }

    fun launchApp(context: Context, app: AppInfo) {
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
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != ctx.packageName }
            .map { info ->
                val (normalIcon, homeIcon) = render(info, c)
                AppInfo(info.componentName, info.label.toString(), normalIcon, homeIcon)
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun render(info: LauncherActivityInfo, c: IconConfig): Pair<ImageBitmap, ImageBitmap> {
        val systemDrawable: Drawable = info.getIcon(0)
        val packBitmap = if (c.pack != null) {
            packs.renderBitmap(info.componentName, systemDrawable, ICON_PX)?.asImageBitmap()
        } else null

        val normalBitmap = packBitmap ?: systemDrawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        val homeBitmap =
            if (packBitmap == null && c.themed && systemDrawable is AdaptiveIconDrawable) {
                themed(systemDrawable, c.dark)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() ?: normalBitmap
            } else {
                normalBitmap
            }
        return normalBitmap to homeBitmap
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
