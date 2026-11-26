package dev.azagra.forkyhome

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.azagra.forkyhome.data.HOME_COLS
import dev.azagra.forkyhome.data.HOME_ROWS
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.ui.LauncherScreen
import dev.azagra.forkyhome.ui.LauncherTheme
import dev.azagra.forkyhome.widgets.WidgetController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: Container
    private lateinit var widgets: WidgetController
    private var wasStopped = false
    private var pausedAtMs = 0L
    private var lastAppReturnMs = 0L
    private var highModeId = 0
    private val restoreRefreshRate = Runnable { setPreferredDisplayMode(0) }

    private companion object {
        const val HIGH_REFRESH_HOLD_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyHighRefreshRate()
        container = (application as LauncherApp).container

        widgets = WidgetController(this) { id, info ->
            val d = resources.displayMetrics.density
            val sx = (if (info.targetCellWidth > 0) info.targetCellWidth else (((info.minWidth / d) + 30) / 70).toInt()).coerceIn(1, HOME_COLS)
            val sy = (if (info.targetCellHeight > 0) info.targetCellHeight else (((info.minHeight / d) + 30) / 70).toInt()).coerceIn(1, HOME_ROWS)
            lifecycleScope.launch {
                val ok = container.settings.addHomeItem(
                    kind = ItemKind.WIDGET,
                    data = id.toString(),
                    sx = sx,
                    sy = sy,
                    page = container.currentPage,
                )
                if (!ok) {
                    widgets.delete(id)
                    Toast.makeText(this@MainActivity, getString(R.string.toast_no_space_on_home), Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            val s: LauncherSettings? by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            s?.let { st ->
                LauncherTheme(st.themeMode, st.dynamicColor) {
                    LauncherScreen(st, widgets, container)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.host.startListening()
    }

    override fun onResume() {
        super.onResume()
        wasStopped = false
        pausedAtMs = 0L
        applyHighRefreshRate()
        lifecycleScope.launch(Dispatchers.IO) {
            syncLauncherDrawerIconVisibility()
        }
    }

    override fun onPause() {
        pausedAtMs = SystemClock.uptimeMillis()
        window.decorView.removeCallbacks(restoreRefreshRate)
        setPreferredDisplayMode(0)
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (highModeId != 0) {
            val decor = window.decorView
            decor.removeCallbacks(restoreRefreshRate)
            setPreferredDisplayMode(highModeId)
            decor.postDelayed(restoreRefreshRate, HIGH_REFRESH_HOLD_MS)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setPreferredDisplayMode(modeId: Int) {
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != modeId) {
            attrs.preferredDisplayModeId = modeId
            window.attributes = attrs
        }
    }

    // La frecuencia alta solo se fuerza mientras el usuario interactúa; en reposo se
    // devuelve el control al sistema para que el panel pueda bajar de Hz y ahorrar batería.
    private fun applyHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (!window.frameRateBoostOnTouchEnabled) {
                window.frameRateBoostOnTouchEnabled = true
            }
        }
        highModeId = display?.let { d ->
            val cur = d.mode
            d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.modeId
        } ?: 0
        setPreferredDisplayMode(0)
    }

    private fun syncLauncherDrawerIconVisibility() {
        runCatching {
            val roleManager = getSystemService(RoleManager::class.java)
            val isDefaultByRole = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolvedHome = packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )?.activityInfo?.packageName
            val isDefaultHome = isDefaultByRole || resolvedHome == packageName
            val desiredState = if (isDefaultHome) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            val alias = ComponentName(this, "$packageName.LauncherEntryAlias")
            if (packageManager.getComponentEnabledSetting(alias) != desiredState) {
                packageManager.setComponentEnabledSetting(
                    alias,
                    desiredState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
        widgets.host.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            val now = SystemClock.uptimeMillis()
            val broughtToFront = (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0
            val pausedByOverlay = pausedAtMs != 0L && (now - pausedAtMs) > 250L
            val justReturned = (now - lastAppReturnMs) < 450L
            val wasAlreadyOnHome = !wasStopped && !broughtToFront && !pausedByOverlay && !justReturned
            if (!wasAlreadyOnHome) {
                lastAppReturnMs = now
            }
            container.homeEvents.tryEmit(wasAlreadyOnHome)
        }
    }

    @Deprecated("Necesario para AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
