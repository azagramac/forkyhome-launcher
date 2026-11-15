package app.mylauncher.data

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {

    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    val packs = IconPacks(ctx)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

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
            override fun onReceive(c: Context?, i: Intent?) = refresh()
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

    /** Dock inicial: teléfono, mensajes, navegador y cámara (si existen). */
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
            .map { AppInfo(it.componentName, it.label.toString(), render(it, c)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun render(info: LauncherActivityInfo, c: IconConfig): ImageBitmap {
        val fromPack = packs.icon(info.componentName)
        val base: Drawable = fromPack ?: info.getIcon(0)
        val d: Drawable =
            if (fromPack == null && c.themed && base is AdaptiveIconDrawable) themed(base, c.dark) ?: base
            else base
        return d.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    }

    /** Icono temático Material You: capa monocroma teñida con el acento del sistema. */
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
