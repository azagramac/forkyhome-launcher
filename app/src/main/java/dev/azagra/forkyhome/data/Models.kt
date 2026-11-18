package dev.azagra.forkyhome.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

const val HOME_COLS = 4
const val HOME_ROWS = 6
const val DOCK_MAX = 5

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DrawerBg { SYSTEM, WHITE, BLACK, DARK }
enum class IconShape { DEFAULT, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP }
enum class ItemKind { APP, WIDGET, FOLDER }
enum class SmartspaceTimeFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }

@Immutable
class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: ImageBitmap,
    val homeIcon: ImageBitmap = icon,
    val pkg: String = component.packageName ?: "",
    val key: String = component.flattenToString() ?: "",
    val user: UserHandle? = null,
) {
    constructor(
        pkg: String,
        cls: String,
        label: String,
        icon: ImageBitmap,
        homeIcon: ImageBitmap = icon,
    ) : this(
        component = ComponentName(pkg, cls),
        label = label,
        icon = icon,
        homeIcon = homeIcon,
        pkg = pkg,
        key = "$pkg/$cls",
    )
}

@Immutable
data class AppShortcut(
    val id: String,
    val pkg: String,
    val label: String,
)

@Immutable
data class IconConfig(
    val pack: String? = null,
    val themed: Boolean = false,
    val dark: Boolean = false,
    val shape: IconShape = IconShape.DEFAULT,
    val customIcons: Map<String, String> = emptyMap(),
)

@Immutable
data class FolderPayload(
    val title: String,
    val apps: List<String>,
) {
    fun encode(): String {
        val safeTitle = title
            .replace(";", " ")
            .replace("\t", " ")
            .replace("\n", " ")
            .replace("\r", " ")
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

@Immutable
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

@Immutable
data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val drawerBg: DrawerBg = DrawerBg.SYSTEM,
    val drawerAlpha: Float = 0.96f,
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.DEFAULT,
    val themedIcons: Boolean = false,
    val badgeNumbers: Boolean = true,
    val columns: Int = 5,
    val drawerIconScale: Float = 1.0f,
    val showLabels: Boolean = true,
    val homePages: Int = 1,
    val homeCols: Int = HOME_COLS,
    val homeRows: Int = HOME_ROWS,
    val homeIconScale: Float = 1.0f,
    val showHomeLabels: Boolean = true,
    val lockDesktop: Boolean = false,
    val autoAddNewApps: Boolean = false,
    val swipeDownNotifications: Boolean = true,
    val doubleTapToSleep: Boolean = false,
    val showAtAGlance: Boolean = true,
    val onboardingDone: Boolean = false,
    val smartspaceShowDate: Boolean = true,
    val smartspaceShowTime: Boolean = false,
    val smartspaceTimeFormat: SmartspaceTimeFormat = SmartspaceTimeFormat.SYSTEM,
    val smartspaceShowCalendar: Boolean = true,
    val smartspaceShowAlarm: Boolean = true,
    val smartspaceShowBattery: Boolean = true,
    val customIcons: Map<String, String> = emptyMap(),
    val hiddenApps: Set<String> = emptySet(),
    val protectedApps: Set<String> = emptySet(),
    val dock: List<String> = emptyList(),
    val dockInit: Boolean = false,
    val home: List<HomeItem> = emptyList(),
)

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

fun resolveIconSizeDp(cols: Int, scale: Float): Float {
    val base = when {
        cols <= 4 -> 60f
        cols == 5 -> 54f
        else -> 48f
    }
    val maxAllowed = when {
        cols <= 4 -> 68f
        cols == 5 -> 60f
        else -> 54f
    }
    return (base * scale.coerceIn(0.80f, 1.20f)).coerceIn(38f, maxAllowed)
}

fun compactHomePages(items: List<HomeItem>): List<HomeItem> {
    if (items.isEmpty()) return emptyList()
    val distinctPages = items.map { it.page.coerceAtLeast(0) }.distinct().sorted()
    val pageMap = distinctPages.withIndex().associate { (newIdx, oldPage) -> oldPage to newIdx }
    return items.map { item ->
        val mappedPage = pageMap[item.page.coerceAtLeast(0)] ?: 0
        if (mappedPage == item.page) item else item.copy(page = mappedPage)
    }
}

fun activePageCount(items: List<HomeItem>, isDraggingToWorkspace: Boolean = false): Int {
    val compacted = compactHomePages(items)
    val baseCount = if (compacted.isEmpty()) 1 else (compacted.maxOf { it.page } + 1).coerceAtLeast(1)
    return if (isDraggingToWorkspace && compacted.any { it.page == baseCount - 1 }) {
        baseCount + 1
    } else {
        baseCount
    }
}

