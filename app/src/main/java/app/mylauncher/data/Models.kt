package app.mylauncher.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

const val HOME_COLS = 4
const val HOME_ROWS = 6
const val DOCK_MAX = 5

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DrawerBg { SYSTEM, WHITE, BLACK, DARK }
enum class ItemKind { APP, WIDGET }

@Immutable
class AppInfo(val component: ComponentName, val label: String, val icon: ImageBitmap) {
    val key: String = component.flattenToString()
    val pkg: String get() = component.packageName
}

data class IconConfig(
    val pack: String? = null,
    val themed: Boolean = false,
    val dark: Boolean = false,
)

/** Elemento del escritorio. data = componente (APP) o appWidgetId (WIDGET). */
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
    val themedIcons: Boolean = false,
    val badgeNumbers: Boolean = true,
    val columns: Int = 5,
    val showLabels: Boolean = true,
    val homePages: Int = 2,
    val dock: List<String> = emptyList(),      // máx. DOCK_MAX
    val dockInit: Boolean = false,
    val home: List<HomeItem> = emptyList(),
)

/** Primer hueco libre (page, col, row) para un elemento de sx × sy celdas. */
fun findFree(items: List<HomeItem>, pages: Int, startPage: Int, sx: Int, sy: Int): Triple<Int, Int, Int>? {
    for (i in 0 until pages) {
        val page = (startPage + i) % pages
        val occ = Array(HOME_ROWS) { BooleanArray(HOME_COLS) }
        items.filter { it.page == page }.forEach { it2 ->
            for (r in it2.row until minOf(HOME_ROWS, it2.row + it2.spanY))
                for (c in it2.col until minOf(HOME_COLS, it2.col + it2.spanX)) occ[r][c] = true
        }
        for (r in 0..HOME_ROWS - sy) for (c in 0..HOME_COLS - sx) {
            var free = true
            for (rr in r until r + sy) for (cc in c until c + sx) if (occ[rr][cc]) free = false
            if (free) return Triple(page, c, r)
        }
    }
    return null
}
