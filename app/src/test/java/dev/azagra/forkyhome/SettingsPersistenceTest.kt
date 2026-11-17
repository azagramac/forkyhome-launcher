package dev.azagra.forkyhome

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.azagra.forkyhome.data.DrawerBg
import dev.azagra.forkyhome.data.FolderPayload
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.IconShape
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.SmartspaceTimeFormat
import dev.azagra.forkyhome.data.ThemeMode
import dev.azagra.forkyhome.data.readSettingsFromPreferences
import dev.azagra.forkyhome.data.writeSettingsToPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPersistenceTest {

    @Test
    fun `writeSettingsToPreferences and readSettingsFromPreferences round-trip all fields`() {
        val customSettings = LauncherSettings(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            drawerBg = DrawerBg.BLACK,
            drawerAlpha = 0.82f,
            iconPack = "com.lollipop.iconpack",
            iconShape = IconShape.TEARDROP,
            themedIcons = true,
            badgeNumbers = false,
            columns = 4,
            drawerIconScale = 1.15f,
            showLabels = false,
            homePages = 2,
            homeCols = 5,
            homeRows = 6,
            homeIconScale = 0.90f,
            showHomeLabels = false,
            lockDesktop = true,
            autoAddNewApps = true,
            swipeDownNotifications = false,
            doubleTapToSleep = true,
            showAtAGlance = false,
            smartspaceShowDate = false,
            smartspaceShowTime = true,
            smartspaceTimeFormat = SmartspaceTimeFormat.TWENTY_FOUR_HOUR,
            smartspaceShowCalendar = false,
            smartspaceShowAlarm = false,
            smartspaceShowBattery = false,
            hiddenApps = setOf("com.secret.app1", "com.secret.app2"),
            protectedApps = setOf("com.bank.app"),
            dock = listOf("com.android.dialer/.Main", "com.android.mms/.Main"),
            dockInit = true,
            home = listOf(
                HomeItem(1, ItemKind.APP, 0, 0, 0, 1, 1, "com.android.chrome/.Main"),
                HomeItem(
                    2,
                    ItemKind.FOLDER,
                    0,
                    1,
                    0,
                    1,
                    1,
                    FolderPayload("Social", listOf("com.whatsapp/.Main", "org.telegram/.Main")).encode(),
                ),
                HomeItem(3, ItemKind.WIDGET, 1, 0, 1, 4, 2, "1024"),
            ),
        )

        val prefs = mutablePreferencesOf()
        writeSettingsToPreferences(prefs, customSettings)
        val restored = readSettingsFromPreferences(prefs)

        assertEquals(customSettings, restored)
    }

    @Test
    fun `readSettingsFromPreferences returns safe defaults when preferences are empty`() {
        val emptyPrefs = mutablePreferencesOf()
        val restored = readSettingsFromPreferences(emptyPrefs)

        assertEquals(LauncherSettings(), restored)
        assertNull(restored.iconPack)
    }

    @Test
    fun `writeSettingsToPreferences truncates dock to DOCK_MAX items`() {
        val overfullDock = (1..8).map { "com.app.$it/.Main" }
        val settings = LauncherSettings(dock = overfullDock, dockInit = true)

        val prefs = mutablePreferencesOf()
        writeSettingsToPreferences(prefs, settings)
        val restored = readSettingsFromPreferences(prefs)

        assertEquals(5, restored.dock.size)
        assertEquals(overfullDock.take(5), restored.dock)
    }

    @Test
    fun `readSettingsFromPreferences falls back safely on invalid enums and clamps out-of-range values`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("theme") to "INVALID_THEME",
            stringPreferencesKey("drawer_bg") to "NEON",
            stringPreferencesKey("icon_shape") to "STAR",
            stringPreferencesKey("smartspace_time_format") to "INVALID_FMT",
            intPreferencesKey("home_cols") to 99,
            intPreferencesKey("home_rows") to 1,
            floatPreferencesKey("drawer_icon_scale") to 5.0f,
            floatPreferencesKey("home_icon_scale") to 0.1f,
        )

        val restored = readSettingsFromPreferences(prefs)

        assertEquals(ThemeMode.SYSTEM, restored.themeMode)
        assertEquals(DrawerBg.SYSTEM, restored.drawerBg)
        assertEquals(IconShape.DEFAULT, restored.iconShape)
        assertEquals(SmartspaceTimeFormat.SYSTEM, restored.smartspaceTimeFormat)
        assertEquals(6, restored.homeCols)
        assertEquals(4, restored.homeRows)
        assertEquals(1.20f, restored.drawerIconScale, 0.001f)
        assertEquals(0.80f, restored.homeIconScale, 0.001f)
    }
}
