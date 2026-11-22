package dev.azagra.forkyhome

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
    private var isStopped = false

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
        if (isStopped) {
            isStopped = false
            container.revealEvents.tryEmit(Unit)
        }
        widgets.host.startListening()
    }

    override fun onResume() {
        super.onResume()
        applyHighRefreshRate()
        lifecycleScope.launch(Dispatchers.IO) {
            syncLauncherDrawerIconVisibility()
        }
    }

    private fun applyHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (!window.frameRateBoostOnTouchEnabled) {
                window.frameRateBoostOnTouchEnabled = true
            }
            window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        }
        display?.let { d ->
            val cur = d.mode
            d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.let { highMode ->
                    val attrs = window.attributes
                    if (highMode.modeId != cur.modeId && attrs.preferredDisplayModeId != highMode.modeId) {
                        attrs.preferredDisplayModeId = highMode.modeId
                        window.attributes = attrs
                    }
                }
        }
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
        isStopped = true
        container.prepareRevealEvents.tryEmit(Unit)
        widgets.host.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) container.homeEvents.tryEmit(!isStopped)
    }

    @Deprecated("Necesario para AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
