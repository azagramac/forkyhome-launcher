package dev.azagra.launcher_aosp.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

const val HOME_COLS = 4
const val HOME_ROWS = 6
const val DOCK_MAX = 5

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DrawerBg { SYSTEM, WHITE, BLACK, DARK }
enum class IconShape { DEFAULT, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP }
enum class ItemKind { APP, WIDGET, FOLDER }

@Immutable
class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: ImageBitmap,
    val homeIcon: ImageBitmap = icon,
) {
    val key: String = component.flattenToString()
    val pkg: String get() = component.packageName
}

@Immutable
data class AppShortcut(
    val id: String,
    val pkg: String,
    val label: String,
)

data class IconConfig(
    val pack: String? = null,
    val themed: Boolean = false,
    val dark: Boolean = false,
)

/** Datos de una carpeta en el escritorio: nombre y lista de component keys. */
@Immutable
data class FolderPayload(
    val title: String,
    val apps: List<String>,
) {
    fun encode(): String {
        val safeTitle = title.replace(";", " ").replace("|", " ")
        return "$safeTitle;${apps.joinToString(",")}"
    }

    companion object {
        fun decode(raw: String): FolderPayload {
            val idx = raw.indexOf(';')
            if (idx == -1) {
                val list = raw.split(',').filter { it.isNotBlank() }
                return FolderPayload("", list)
            }
            val title = raw.substring(0, idx)
            val list = raw.substring(idx + 1).split(',').filter { it.isNotBlank() }
            return FolderPayload(title, list)
        }
    }
}

/** Elemento del escritorio. data = componente (APP), appWidgetId (WIDGET) o FolderPayload (FOLDER). */
data class HomeItem(
    val id: Int,
    val kind: ItemKind,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
    val data: String,
) {
    fun encode() = listOf(id, kind.name, page, col, row, spanX, spanY, data).joinToString("|")

    companion object {
        fun decode(s: String): HomeItem? = runCatching {
            val p = s.split('|', limit = 8)
            HomeItem(
                p[0].toInt(), ItemKind.valueOf(p[1]), p[2].toInt(), p[3].toInt(),
                p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7],
            )
        }.getOrNull()
    }
}

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,          // Material You
    val drawerBg: DrawerBg = DrawerBg.SYSTEM,
    val drawerAlpha: Float = 0.96f,
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.DEFAULT,
    val themedIcons: Boolean = false,
    val badgeNumbers: Boolean = true,
    val columns: Int = 5,
    val showLabels: Boolean = true,
    val homePages: Int = 2,
    val homeCols: Int = HOME_COLS,
    val homeRows: Int = HOME_ROWS,
    val showHomeLabels: Boolean = true,
    val lockDesktop: Boolean = false,
    val autoAddNewApps: Boolean = false,
    val swipeDownNotifications: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    val protectedApps: Set<String> = emptySet(),
    val dock: List<String> = emptyList(),      // máx. DOCK_MAX
    val dockInit: Boolean = false,
    val home: List<HomeItem> = emptyList(),
)

/** Primer hueco libre (page, col, row) para un elemento de sx × sy celdas. */
fun findFree(
    items: List<HomeItem>,
    pages: Int,
    startPage: Int,
    sx: Int,
    sy: Int,
    cols: Int = HOME_COLS,
    rows: Int = HOME_ROWS,
): Triple<Int, Int, Int>? {
    val safeSx = sx.coerceIn(1, cols)
    val safeSy = sy.coerceIn(1, rows)
    for (i in 0 until pages) {
        val page = (startPage + i) % pages
        val occ = Array(rows) { BooleanArray(cols) }
        items.filter { it.page == page }.forEach { it2 ->
            for (r in it2.row until minOf(rows, it2.row + it2.spanY))
                for (c in it2.col until minOf(cols, it2.col + it2.spanX))
                    if (r in 0 until rows && c in 0 until cols) occ[r][c] = true
        }
        for (r in 0..rows - safeSy) for (c in 0..cols - safeSx) {
            var free = true
            for (rr in r until r + safeSy) for (cc in c until c + safeSx) if (occ[rr][cc]) free = false
            if (free) return Triple(page, c, r)
        }
    }
    return null
}
