package dev.azagra.launcher_aosp

import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.azagra.launcher_aosp.data.DrawerBg
import dev.azagra.launcher_aosp.data.FolderPayload
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.IconShape
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.data.ThemeMode
import dev.azagra.launcher_aosp.data.readSettingsFromPreferences
import dev.azagra.launcher_aosp.data.writeSettingsToPreferences
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
            homePages = 4,
            homeCols = 5,
            homeRows = 6,
            homeIconScale = 0.90f,
            showHomeLabels = false,
            lockDesktop = true,
            autoAddNewApps = true,
            swipeDownNotifications = false,
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
}
