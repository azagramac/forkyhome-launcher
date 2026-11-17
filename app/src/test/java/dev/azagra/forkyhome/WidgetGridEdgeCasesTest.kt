package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.ui.placeOrResizeWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetGridEdgeCasesTest {

    @Test
    fun `placeOrResizeWidget returns null when page has no free cells for displaced apps`() {
        val items = mutableListOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 2, data = "200"),
        )
        var nextId = 2
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (c < 2 && r < 2) continue
                items.add(HomeItem(id = nextId++, kind = ItemKind.APP, page = 0, col = c, row = r, spanX = 1, spanY = 1, data = "app.$nextId"))
            }
        }

        val result = placeOrResizeWidget(
            home = items,
            widgetId = 1,
            targetPage = 0,
            targetCol = 0,
            targetRow = 0,
            targetSpanX = 3,
            targetSpanY = 2,
            cols = 4,
            rows = 4,
        )
        assertNull(result)
    }

    @Test
    fun `placeOrResizeWidget swaps widget with displaced icons using newly vacated cells`() {
        val items = mutableListOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 2, data = "200"),
        )
        var nextId = 2
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (c < 2 && r < 2) continue
                items.add(HomeItem(id = nextId++, kind = ItemKind.APP, page = 0, col = c, row = r, spanX = 1, spanY = 1, data = "app.$nextId"))
            }
        }

        val result = placeOrResizeWidget(
            home = items,
            widgetId = 1,
            targetPage = 0,
            targetCol = 2,
            targetRow = 0,
            targetSpanX = 2,
            targetSpanY = 2,
            cols = 4,
            rows = 4,
        )
        assertNotNull(result)
        val movedWidget = result!!.first { it.id == 1 }
        assertEquals(2, movedWidget.col)
        assertEquals(0, movedWidget.row)

        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        for (item in result) {
            for (r in item.row until (item.row + item.spanY)) {
                for (c in item.col until (item.col + item.spanX)) {
                    val added = occupiedCells.add(c to r)
                    assertEquals("Collision at ($c, $r)", true, added)
                }
            }
        }
        assertEquals(16, occupiedCells.size)
    }

    @Test
    fun `placeOrResizeWidget clamps oversized spans to current grid dimensions`() {
        val initial = listOf(
            HomeItem(id = 1, kind = ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 2, spanY = 2, data = "201"),
        )
        val result = placeOrResizeWidget(
            home = initial,
            widgetId = 1,
            targetPage = 0,
            targetCol = 5,
            targetRow = 6,
            targetSpanX = 10,
            targetSpanY = 10,
            cols = 4,
            rows = 5,
        )
        assertNotNull(result)
        val w = result!!.first()
        assertEquals(0, w.col)
        assertEquals(0, w.row)
        assertEquals(4, w.spanX)
        assertEquals(5, w.spanY)
    }
}
