package dev.azagra.launcher_aosp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.azagra.launcher_aosp.data.HOME_COLS
import dev.azagra.launcher_aosp.data.HOME_ROWS
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.ui.LauncherScreen
import dev.azagra.launcher_aosp.ui.LauncherTheme
import dev.azagra.launcher_aosp.widgets.WidgetController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: Container
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.frameRateBoostOnTouchEnabled = true
        display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { highMode ->
            window.attributes = window.attributes.apply { preferredDisplayModeId = highMode.modeId }
        }
        container = (application as LauncherApp).container

        widgets = WidgetController(this) { id, info ->
            val d = resources.displayMetrics.density
            lifecycleScope.launch {
                val ok = container.settings.addHomeItem(
                    kind = ItemKind.WIDGET,
                    data = id.toString(),
                    sx = (((info.minWidth / d) + 30) / 70).toInt().coerceIn(1, HOME_COLS),
                    sy = (((info.minHeight / d) + 30) / 70).toInt().coerceIn(1, HOME_ROWS),
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

    override fun onStop() {
        super.onStop()
        widgets.host.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) container.homeEvents.tryEmit(Unit)
    }

    @Deprecated("Necesario para AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
