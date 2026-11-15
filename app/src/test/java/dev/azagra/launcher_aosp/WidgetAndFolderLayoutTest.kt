package dev.azagra.launcher_aosp

import dev.azagra.launcher_aosp.data.FolderPayload
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.ui.placeOrResizeWidget
import dev.azagra.launcher_aosp.ui.removeAppFromFolderInList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAndFolderLayoutTest {

    @Test
    fun `placeOrResizeWidget moves widget into empty area`() {
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 2, data = "101"),
        )
        val updated = placeOrResizeWidget(
            home = initial,
            widgetId = 1,
            targetPage = 0,
            targetCol = 2,
            targetRow = 2,
            targetSpanX = 2,
            targetSpanY = 2,
            cols = 4,
            rows = 5,
        )
        assertNotNull(updated)
        val moved = updated!!.first { it.id == 1 }
        assertEquals(2, moved.col)
        assertEquals(2, moved.row)
        assertEquals(2, moved.spanX)
        assertEquals(2, moved.spanY)
    }

    @Test
    fun `placeOrResizeWidget relocates displaced 1x1 app icons when resizing`() {
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 1, data = "101"),
            HomeItem(id = 2, kind = ItemKind.APP, page = 0, col = 2, row = 0, spanX = 1, spanY = 1, data = "app.one/.Main"),
        )
        val updated = placeOrResizeWidget(
            home = initial,
            widgetId = 1,
            targetPage = 0,
            targetCol = 0,
            targetRow = 0,
            targetSpanX = 4,
            targetSpanY = 1,
            cols = 4,
            rows = 5,
        )
        assertNotNull(updated)
        val widget = updated!!.first { it.id == 1 }
        val relocatedApp = updated.first { it.id == 2 }

        assertEquals(4, widget.spanX)
        assertEquals(1, widget.spanY)
        assertTrue(relocatedApp.row >= 1)
    }

    @Test
    fun `placeOrResizeWidget prevents overlapping another widget on same page`() {
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 2, data = "101"),
            HomeItem(id = 2, kind = ItemKind.WIDGET, page = 0, col = 2, row = 0, spanX = 2, spanY = 2, data = "102"),
        )
        val result = placeOrResizeWidget(
            home = initial,
            widgetId = 1,
            targetPage = 0,
            targetCol = 1,
            targetRow = 0,
            targetSpanX = 2,
            targetSpanY = 2,
            cols = 4,
            rows = 5,
        )
        assertNull(result)
    }

    @Test
    fun `removeAppFromFolderInList converts folder back to normal app when 1 app remains`() {
        val folder = FolderPayload("Tools", listOf("app.a/.Main", "app.b/.Main"))
        val initial = listOf(
            HomeItem(id = 10, kind = ItemKind.FOLDER, page = 0, col = 1, row = 1, spanX = 1, spanY = 1, data = folder.encode()),
        )
        val afterRemoval = removeAppFromFolderInList(initial, folderId = 10, appKey = "app.a/.Main")
        assertEquals(1, afterRemoval.size)
        assertEquals(ItemKind.APP, afterRemoval.first().kind)
        assertEquals("app.b/.Main", afterRemoval.first().data)
    }

    @Test
    fun `removeAppFromFolderInList keeps folder when 2 or more apps remain`() {
        val folder = FolderPayload("Tools", listOf("app.a/.Main", "app.b/.Main", "app.c/.Main"))
        val initial = listOf(
            HomeItem(id = 10, kind = ItemKind.FOLDER, page = 0, col = 1, row = 1, spanX = 1, spanY = 1, data = folder.encode()),
        )
        val afterRemoval = removeAppFromFolderInList(initial, folderId = 10, appKey = "app.b/.Main")
        assertEquals(1, afterRemoval.size)
        assertEquals(ItemKind.FOLDER, afterRemoval.first().kind)
        val decoded = FolderPayload.decode(afterRemoval.first().data)
        assertEquals(listOf("app.a/.Main", "app.c/.Main"), decoded.apps)
    }
}
