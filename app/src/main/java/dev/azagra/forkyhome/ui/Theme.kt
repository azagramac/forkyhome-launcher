package dev.azagra.forkyhome.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.ThemeMode

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LauncherTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = resolveDark(mode)
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

fun notificationAccessGranted(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun openWallpaperPicker(ctx: Context) {
    ctx.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), ctx.getString(R.string.menu_wallpaper))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
