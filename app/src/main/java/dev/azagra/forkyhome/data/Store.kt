package dev.azagra.forkyhome.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("launcher")

private object K {
    val theme = stringPreferencesKey("theme")
    val dynamic = booleanPreferencesKey("dynamic")
    val drawerBg = stringPreferencesKey("drawer_bg")
    val drawerAlpha = floatPreferencesKey("drawer_alpha")
    val iconPack = stringPreferencesKey("icon_pack")
    val iconShape = stringPreferencesKey("icon_shape")
    val themed = booleanPreferencesKey("themed")
    val badges = booleanPreferencesKey("badges")
    val columns = intPreferencesKey("columns")
    val drawerIconScale = floatPreferencesKey("drawer_icon_scale")
    val labels = booleanPreferencesKey("labels")
    val pages = intPreferencesKey("pages")
    val homeCols = intPreferencesKey("home_cols")
    val homeRows = intPreferencesKey("home_rows")
    val homeIconScale = floatPreferencesKey("home_icon_scale")
    val homeLabels = booleanPreferencesKey("home_labels")
    val lockDesktop = booleanPreferencesKey("lock_desktop")
    val autoAddApps = booleanPreferencesKey("auto_add_apps")
    val swipeDownNotif = booleanPreferencesKey("swipe_down_notif")
    val doubleTapSleep = booleanPreferencesKey("double_tap_sleep")
    val atAGlance = booleanPreferencesKey("at_a_glance")
    val smartspaceShowDate = booleanPreferencesKey("smartspace_show_date")
    val smartspaceShowTime = booleanPreferencesKey("smartspace_show_time")
    val smartspaceTimeFormat = stringPreferencesKey("smartspace_time_format")
    val smartspaceShowCalendar = booleanPreferencesKey("smartspace_show_calendar")
    val smartspaceShowAlarm = booleanPreferencesKey("smartspace_show_alarm")
    val smartspaceShowBattery = booleanPreferencesKey("smartspace_show_battery")
    val customIcons = stringSetPreferencesKey("custom_icons")
    val hiddenApps = stringSetPreferencesKey("hidden_apps")
    val protectedApps = stringSetPreferencesKey("protected_apps")
    val dock = stringPreferencesKey("dock")
    val dockInit = booleanPreferencesKey("dock_init")
    val home = stringPreferencesKey("home")
}

class SettingsStore(private val ctx: Context) {

    val flow: Flow<LauncherSettings> = ctx.store.data.map { readSettingsFromPreferences(it) }

    suspend fun update(block: (LauncherSettings) -> LauncherSettings) {
        ctx.store.edit { writeSettingsToPreferences(it, block(readSettingsFromPreferences(it))) }
    }

    suspend fun addHomeItem(kind: ItemKind, data: String, sx: Int, sy: Int, page: Int): Boolean {
        var ok = false
        update { s ->
            val compacted = compactHomePages(s.home)
            val currentPages = activePageCount(compacted, isDraggingToWorkspace = false)
            val safeSx = sx.coerceIn(1, s.homeCols)
            val safeSy = sy.coerceIn(1, s.homeRows)
            val slot = findFree(
                items = compacted,
                pages = currentPages,
                startPage = page.coerceIn(0, currentPages - 1),
                sx = safeSx,
                sy = safeSy,
                cols = s.homeCols,
                rows = s.homeRows,
            ) ?: Triple(
                if (compacted.isEmpty()) 0 else currentPages,
                0,
                0,
            )
            ok = true
            val id = (compacted.maxOfOrNull { it.id } ?: 0) + 1
            val updatedHome = compactHomePages(
                compacted + HomeItem(id, kind, slot.first, slot.second, slot.third, safeSx, safeSy, data)
            )
            s.copy(home = updatedHome, homePages = activePageCount(updatedHome))
        }
        return ok
    }
}

internal fun readSettingsFromPreferences(p: Preferences): LauncherSettings {
    val rawHome = p[K.home]?.lines()?.mapNotNull { HomeItem.decode(it) } ?: emptyList()
    val compactedHome = compactHomePages(rawHome)
    return LauncherSettings(
        themeMode = p[K.theme].toEnum(ThemeMode.SYSTEM),
        dynamicColor = p[K.dynamic] ?: true,
        drawerBg = p[K.drawerBg].toEnum(DrawerBg.SYSTEM),
        drawerAlpha = p[K.drawerAlpha] ?: 0.96f,
        iconPack = p[K.iconPack],
        iconShape = p[K.iconShape].toEnum(IconShape.DEFAULT),
        themedIcons = p[K.themed] ?: false,
        badgeNumbers = p[K.badges] ?: true,
        columns = p[K.columns] ?: 5,
        drawerIconScale = (p[K.drawerIconScale] ?: 1.0f).coerceIn(0.80f, 1.20f),
        showLabels = p[K.labels] ?: true,
        homePages = activePageCount(compactedHome),
        homeCols = (p[K.homeCols] ?: HOME_COLS).coerceIn(4, 6),
        homeRows = (p[K.homeRows] ?: HOME_ROWS).coerceIn(4, 7),
        homeIconScale = (p[K.homeIconScale] ?: 1.0f).coerceIn(0.80f, 1.20f),
        showHomeLabels = p[K.homeLabels] ?: true,
        lockDesktop = p[K.lockDesktop] ?: false,
        autoAddNewApps = p[K.autoAddApps] ?: false,
        swipeDownNotifications = p[K.swipeDownNotif] ?: true,
        doubleTapToSleep = p[K.doubleTapSleep] ?: false,
        showAtAGlance = p[K.atAGlance] ?: true,
        smartspaceShowDate = p[K.smartspaceShowDate] ?: true,
        smartspaceShowTime = p[K.smartspaceShowTime] ?: false,
        smartspaceTimeFormat = p[K.smartspaceTimeFormat].toEnum(SmartspaceTimeFormat.SYSTEM),
        smartspaceShowCalendar = p[K.smartspaceShowCalendar] ?: true,
        smartspaceShowAlarm = p[K.smartspaceShowAlarm] ?: true,
        smartspaceShowBattery = p[K.smartspaceShowBattery] ?: true,
        customIcons = p[K.customIcons]?.mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0 && idx < entry.lastIndex) entry.substring(0, idx) to entry.substring(idx + 1) else null
        }?.toMap() ?: emptyMap(),
        hiddenApps = p[K.hiddenApps] ?: emptySet(),
        protectedApps = p[K.protectedApps] ?: emptySet(),
        dock = p[K.dock]?.lines()?.filter { it.isNotBlank() } ?: emptyList(),
        dockInit = p[K.dockInit] ?: false,
        home = compactedHome,
    )
}

internal fun writeSettingsToPreferences(p: MutablePreferences, s: LauncherSettings) {
    val compactedHome = compactHomePages(s.home)
    p[K.theme] = s.themeMode.name
    p[K.dynamic] = s.dynamicColor
    p[K.drawerBg] = s.drawerBg.name
    p[K.drawerAlpha] = s.drawerAlpha
    val pack = s.iconPack
    if (pack == null) p.remove(K.iconPack) else p[K.iconPack] = pack
    p[K.iconShape] = s.iconShape.name
    p[K.themed] = s.themedIcons
    p[K.badges] = s.badgeNumbers
    p[K.columns] = s.columns
    p[K.drawerIconScale] = s.drawerIconScale
    p[K.labels] = s.showLabels
    p[K.pages] = activePageCount(compactedHome)
    p[K.homeCols] = s.homeCols
    p[K.homeRows] = s.homeRows
    p[K.homeIconScale] = s.homeIconScale
    p[K.homeLabels] = s.showHomeLabels
    p[K.lockDesktop] = s.lockDesktop
    p[K.autoAddApps] = s.autoAddNewApps
    p[K.swipeDownNotif] = s.swipeDownNotifications
    p[K.doubleTapSleep] = s.doubleTapToSleep
    p[K.atAGlance] = s.showAtAGlance
    p[K.smartspaceShowDate] = s.smartspaceShowDate
    p[K.smartspaceShowTime] = s.smartspaceShowTime
    p[K.smartspaceTimeFormat] = s.smartspaceTimeFormat.name
    p[K.smartspaceShowCalendar] = s.smartspaceShowCalendar
    p[K.smartspaceShowAlarm] = s.smartspaceShowAlarm
    p[K.smartspaceShowBattery] = s.smartspaceShowBattery
    p[K.customIcons] = s.customIcons.entries.map { (k, v) -> "$k=$v" }.toSet()
    p[K.hiddenApps] = s.hiddenApps
    p[K.protectedApps] = s.protectedApps
    p[K.dock] = s.dock.take(DOCK_MAX).joinToString("\n")
    p[K.dockInit] = s.dockInit
    p[K.home] = compactedHome.joinToString("\n") { it.encode() }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    runCatching { enumValueOf<E>(this!!) }.getOrDefault(default)
