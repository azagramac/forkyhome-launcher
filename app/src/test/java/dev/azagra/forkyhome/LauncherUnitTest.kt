package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.DOCK_MAX
import dev.azagra.forkyhome.data.DrawerBg
import dev.azagra.forkyhome.data.FolderPayload
import dev.azagra.forkyhome.data.HOME_COLS
import dev.azagra.forkyhome.data.HOME_ROWS
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.IconShape
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.ThemeMode
import dev.azagra.forkyhome.data.activePageCount
import dev.azagra.forkyhome.data.compactHomePages
import dev.azagra.forkyhome.data.findFree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUnitTest {

    @Test
    fun `HomeItem encode and decode preserves all fields`() {
        val original = HomeItem(
            id = 7,
            kind = ItemKind.APP,
            page = 1,
            col = 2,
            row = 3,
            spanX = 1,
            spanY = 1,
            data = "com.android.settings/.Settings",
        )
        val encoded = original.encode()
        val decoded = HomeItem.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `HomeItem decode supports pipe characters inside data field`() {
        val original = HomeItem(
            id = 12,
            kind = ItemKind.WIDGET,
            page = 0,
            col = 0,
            row = 1,
            spanX = 4,
            spanY = 2,
            data = "custom|payload|with|pipes",
        )
        val decoded = HomeItem.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun `HomeItem supports FOLDER kind with FolderPayload`() {
        val folder = FolderPayload(
            title = "Google",
            apps = listOf("com.android.chrome/.Main", "com.google.android.gm/.GmailActivity"),
        )
        val item = HomeItem(
            id = 42,
            kind = ItemKind.FOLDER,
            page = 0,
            col = 1,
            row = 2,
            spanX = 1,
            spanY = 1,
            data = folder.encode(),
        )
        val decodedItem = HomeItem.decode(item.encode())
        assertNotNull(decodedItem)
        assertEquals(ItemKind.FOLDER, decodedItem!!.kind)

        val decodedFolder = FolderPayload.decode(decodedItem.data)
        assertEquals("Google", decodedFolder.title)
        assertEquals(2, decodedFolder.apps.size)
        assertEquals(folder.apps, decodedFolder.apps)
    }

    @Test
    fun `HomeItem decode returns null for invalid strings`() {
        assertNull(HomeItem.decode(""))
        assertNull(HomeItem.decode("invalid"))
        assertNull(HomeItem.decode("1|UNKNOWN|0|0|0|1|1|data"))
    }

    @Test
    fun `findFree finds first free cell on empty home screen`() {
        val slot = findFree(
            items = emptyList(),
            pages = 2,
            startPage = 0,
            sx = 1,
            sy = 1,
        )
        assertEquals(Triple(0, 0, 0), slot)
    }

    @Test
    fun `findFree skips occupied cells and places item in next slot`() {
        val existing = listOf(
            HomeItem(1, ItemKind.APP, page = 0, col = 0, row = 0, spanX = 1, spanY = 1, data = "app1"),
            HomeItem(2, ItemKind.APP, page = 0, col = 1, row = 0, spanX = 1, spanY = 1, data = "app2"),
        )
        val slot = findFree(existing, pages = 2, startPage = 0, sx = 1, sy = 1)
        assertEquals(Triple(0, 2, 0), slot)
    }

    @Test
    fun `findFree respects custom grid cols and rows`() {
        val existing = (0 until 4).map { c ->
            HomeItem(c + 1, ItemKind.APP, page = 0, col = c, row = 0, spanX = 1, spanY = 1, data = "app$c")
        }
        val slot = findFree(existing, pages = 1, startPage = 0, sx = 1, sy = 1, cols = 4, rows = 4)
        assertEquals(Triple(0, 0, 1), slot)
    }

    @Test
    fun `findFree advances to next page when current page has insufficient space`() {
        val existing = (0 until HOME_ROWS).map { r ->
            HomeItem(r + 1, ItemKind.APP, page = 0, col = 0, row = r, spanX = 1, spanY = 1, data = "app$r")
        }
        val slot = findFree(existing, pages = 2, startPage = 0, sx = HOME_COLS, sy = 2)
        assertEquals(Triple(1, 0, 0), slot)
    }

    @Test
    fun `findFree returns null when all pages are full`() {
        val fullItem = HomeItem(
            id = 1,
            kind = ItemKind.WIDGET,
            page = 0,
            col = 0,
            row = 0,
            spanX = HOME_COLS,
            spanY = HOME_ROWS,
            data = "100",
        )
        val slot = findFree(listOf(fullItem), pages = 1, startPage = 0, sx = 1, sy = 1)
        assertNull(slot)
    }

    @Test
    fun `LauncherSettings has valid default values including Trebuchet features`() {
        val defaults = LauncherSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertTrue(defaults.dynamicColor)
        assertEquals(DrawerBg.SYSTEM, defaults.drawerBg)
        assertEquals(5, defaults.columns)
        assertEquals(HOME_COLS, defaults.homeCols)
        assertEquals(HOME_ROWS, defaults.homeRows)
        assertTrue(defaults.showLabels)
        assertTrue(defaults.showHomeLabels)
        assertFalse(defaults.lockDesktop)
        assertFalse(defaults.autoAddNewApps)
        assertEquals(IconShape.DEFAULT, defaults.iconShape)
        assertTrue(defaults.swipeDownNotifications)
        assertFalse(defaults.doubleTapToSleep)
        assertTrue(defaults.showAtAGlance)
        assertTrue(defaults.hiddenApps.isEmpty())
        assertTrue(defaults.protectedApps.isEmpty())
        assertTrue(defaults.homePages in 1..5)
        assertTrue(DOCK_MAX == 5)
    }

    @Test
    fun `compactHomePages eliminates empty gap pages and reindexes contiguously`() {
        val sparseItems = listOf(
            HomeItem(1, ItemKind.APP, page = 0, col = 0, row = 0, spanX = 1, spanY = 1, data = "app0"),
            HomeItem(2, ItemKind.APP, page = 3, col = 1, row = 1, spanX = 1, spanY = 1, data = "app3"),
            HomeItem(3, ItemKind.WIDGET, page = 5, col = 0, row = 2, spanX = 4, spanY = 2, data = "101"),
        )
        val compacted = compactHomePages(sparseItems)

        assertEquals(listOf(0, 1, 2), compacted.map { it.page })
        assertEquals(1, activePageCount(emptyList(), isDraggingToWorkspace = false))
        assertEquals(3, activePageCount(compacted, isDraggingToWorkspace = false))
        assertEquals(4, activePageCount(compacted, isDraggingToWorkspace = true))
    }
}
