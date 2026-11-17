package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.FolderPayload
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.ui.removeAppFromFolderInList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOperationsTest {

    @Test
    fun `removeAppFromFolderInList deletes folder item completely when last app is removed`() {
        val folder = FolderPayload("EmptySoon", listOf("app.only/.Main"))
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.FOLDER, page = 0, col = 2, row = 2, spanX = 1, spanY = 1, data = folder.encode()),
        )
        val after = removeAppFromFolderInList(initial, folderId = 1, appKey = "app.only/.Main")
        assertTrue(after.isEmpty())
    }

    @Test
    fun `FolderPayload sanitizes tabs and newlines in folder title and round-trips inside HomeItem`() {
        val dirtyTitle = "Juegos\tFavoritos\n2026 | Top"
        val apps = listOf("com.game.one/.Main", "com.game.two/.Main", "com.game.three/.Main")
        val payload = FolderPayload(title = dirtyTitle, apps = apps)

        val homeItem = HomeItem(
            id = 99,
            kind = ItemKind.FOLDER,
            page = 1,
            col = 3,
            row = 4,
            spanX = 1,
            spanY = 1,
            data = payload.encode(),
        )

        val decodedItem = HomeItem.decode(homeItem.encode())
        assertNotNull(decodedItem)
        val decodedPayload = FolderPayload.decode(decodedItem!!.data)
        assertEquals("Juegos Favoritos 2026 | Top", decodedPayload.title)
        assertEquals(apps, decodedPayload.apps)
    }

    @Test
    fun `removeAppFromFolderInList leaves other home items untouched`() {
        val folder = FolderPayload("Work", listOf("app.one/.Main", "app.two/.Main"))
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.APP, page = 0, col = 0, row = 0, spanX = 1, spanY = 1, data = "app.keep/.Main"),
            HomeItem(id = 2, kind = ItemKind.FOLDER, page = 0, col = 1, row = 1, spanX = 1, spanY = 1, data = folder.encode()),
        )
        val result = removeAppFromFolderInList(initial, folderId = 2, appKey = "app.one/.Main")
        assertEquals(2, result.size)
        assertEquals("app.keep/.Main", result.first { it.id == 1 }.data)
        assertEquals(ItemKind.APP, result.first { it.id == 2 }.kind)
        assertEquals("app.two/.Main", result.first { it.id == 2 }.data)
    }
}
