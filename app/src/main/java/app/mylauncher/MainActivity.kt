package app.mylauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.mylauncher.data.HOME_COLS
import app.mylauncher.data.HOME_ROWS
import app.mylauncher.data.ItemKind
import app.mylauncher.data.LauncherSettings
import app.mylauncher.ui.LauncherScreen
import app.mylauncher.ui.LauncherTheme
import app.mylauncher.widgets.WidgetController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: Container
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as LauncherApp).container

        widgets = WidgetController(this) { id, info ->
            val d = resources.displayMetrics.density
            val sx = (((info.minWidth / d) + 30) / 70).toInt().coerceIn(1, HOME_COLS)
            val sy = (((info.minHeight / d) + 30) / 70).toInt().coerceIn(1, HOME_ROWS)
            lifecycleScope.launch {
                val ok = container.settings.addHomeItem(ItemKind.WIDGET, id.toString(), sx, sy, container.currentPage)
                if (!ok) {
                    widgets.delete(id)
                    Toast.makeText(this@MainActivity, "No hay sitio en el escritorio", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            // null hasta que DataStore carga: evita pisar el dock guardado con valores por defecto
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
        // Home pulsado estando ya en el launcher: cierra cajón/ajustes y vuelve a la primera página
        if (intent.action == Intent.ACTION_MAIN) container.homeEvents.tryEmit(Unit)
    }

    @Deprecated("Necesario para la pantalla de configuración de widgets")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
