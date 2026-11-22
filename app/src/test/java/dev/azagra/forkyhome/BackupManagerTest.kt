package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.BackupManager
import dev.azagra.forkyhome.data.FolderPayload
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.IconShape
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private fun sampleSettings() = LauncherSettings(
        themeMode = ThemeMode.DARK,
        dynamicColor = false,
        iconPack = "com.example.pack",
        iconShape = IconShape.SQUIRCLE,
        columns = 6,
        homeCols = 5,
        homeRows = 6,
        hiddenApps = setOf("com.secret.app"),
        protectedApps = setOf("com.bank.app"),
        customIcons = mapOf("com.example.app/.Main" to "custom_res"),
        dock = listOf("com.example.app/.Main", "com.missing.app/.Main"),
        home = listOf(
            HomeItem(1, ItemKind.APP, 0, 0, 0, 1, 1, "com.example.app/.Main"),
            HomeItem(2, ItemKind.WIDGET, 0, 1, 0, 2, 2, "42"),
            HomeItem(
                3,
                ItemKind.FOLDER,
                1,
                0,
                0,
                1,
                1,
                FolderPayload("Tools", listOf("com.example.app/.Main", "com.missing.app/.Main")).encode(),
            ),
        ),
    )

    @Test
    fun plainRoundTripPreservesSettings() {
        val original = sampleSettings()
        val json = BackupManager.exportToJson(original, password = null)
        val inspected = BackupManager.inspect(json)
        assertTrue(inspected is BackupManager.InspectResult.Plain)
        val restored = (inspected as BackupManager.InspectResult.Plain).settings
        assertEquals(original.themeMode, restored.themeMode)
        assertEquals(original.iconShape, restored.iconShape)
        assertEquals(original.hiddenApps, restored.hiddenApps)
        assertEquals(original.protectedApps, restored.protectedApps)
        assertEquals(original.customIcons, restored.customIcons)
        assertEquals(original.dock, restored.dock)
        assertEquals(original.home.size, restored.home.size)
        assertTrue(restored.onboardingDone)
    }

    @Test
    fun encryptedRoundTripRequiresCorrectPassword() {
        val original = sampleSettings()
        val json = BackupManager.exportToJson(original, password = "MySecretPassword123!")
        val inspected = BackupManager.inspect(json)
        assertTrue(inspected is BackupManager.InspectResult.NeedsPassword)

        val wrong = BackupManager.decrypt(json, "WrongPassword")
        assertTrue(wrong is BackupManager.DecryptResult.WrongPassword)

        val ok = BackupManager.decrypt(json, "MySecretPassword123!")
        assertTrue(ok is BackupManager.DecryptResult.Success)
        val restored = (ok as BackupManager.DecryptResult.Success).settings
        assertEquals(original.hiddenApps, restored.hiddenApps)
        assertEquals(original.home.size, restored.home.size)
    }

    @Test
    fun prepareForRestoreFiltersWidgetsAndMissingApps() {
        val original = sampleSettings()
        val plan = BackupManager.prepareForRestore(
            imported = original,
            installedKeys = setOf("com.example.app/.Main"),
            installedPacks = emptySet(),
        )
        assertEquals(1, plan.skippedWidgets)
        assertEquals(2, plan.missingApps) // 1 in folder + 1 in dock
        assertTrue(plan.missingIconPack)
        assertNull(plan.settings.iconPack)
        assertEquals(listOf("com.example.app/.Main"), plan.settings.dock)
        assertEquals(2, plan.settings.home.size)
        assertFalse(plan.settings.home.any { it.kind == ItemKind.WIDGET })
        assertTrue(plan.settings.onboardingDone)
        assertTrue(plan.settings.dockInit)
    }
}
