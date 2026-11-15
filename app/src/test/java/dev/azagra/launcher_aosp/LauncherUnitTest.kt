package dev.azagra.launcher_aosp

import dev.azagra.launcher_aosp.data.DOCK_MAX
import dev.azagra.launcher_aosp.data.DrawerBg
import dev.azagra.launcher_aosp.data.HOME_COLS
import dev.azagra.launcher_aosp.data.HOME_ROWS
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.data.ThemeMode
import dev.azagra.launcher_aosp.data.findFree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUnitTest {

    @Test
    fun `HomeItem encode y decode conserva todos los campos`() {
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
    fun `HomeItem decode soporta caracteres pipe dentro del campo data`() {
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
    fun `HomeItem decode devuelve null ante cadenas invalidas`() {
        assertNull(HomeItem.decode(""))
        assertNull(HomeItem.decode("invalido"))
        assertNull(HomeItem.decode("1|UNKNOWN|0|0|0|1|1|data"))
    }

    @Test
    fun `findFree encuentra la primera celda libre en un escritorio vacio`() {
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
    fun `findFree salta celdas ocupadas y coloca en el siguiente hueco`() {
        val existing = listOf(
            HomeItem(1, ItemKind.APP, page = 0, col = 0, row = 0, spanX = 1, spanY = 1, data = "app1"),
            HomeItem(2, ItemKind.APP, page = 0, col = 1, row = 0, spanX = 1, spanY = 1, data = "app2"),
        )
        val slot = findFree(existing, pages = 2, startPage = 0, sx = 1, sy = 1)
        assertEquals(Triple(0, 2, 0), slot)
    }

    @Test
    fun `findFree pasa a la siguiente pagina cuando la pagina actual no tiene hueco suficiente`() {
        // Ocupamos la página 0 con un widget que deja menos de 4 columnas libres por fila
        val existing = (0 until HOME_ROWS).map { r ->
            HomeItem(r + 1, ItemKind.APP, page = 0, col = 0, row = r, spanX = 1, spanY = 1, data = "app$r")
        }
        // Un widget de 4 columnas de ancho no cabe en la página 0, debe ir a la página 1
        val slot = findFree(existing, pages = 2, startPage = 0, sx = HOME_COLS, sy = 2)
        assertEquals(Triple(1, 0, 0), slot)
    }

    @Test
    fun `findFree devuelve null cuando todas las paginas estan llenas`() {
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
    fun `LauncherSettings tiene valores por defecto coherentes`() {
        val defaults = LauncherSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertTrue(defaults.dynamicColor)
        assertEquals(DrawerBg.SYSTEM, defaults.drawerBg)
        assertEquals(5, defaults.columns)
        assertTrue(defaults.homePages in 1..5)
        assertTrue(DOCK_MAX == 5)
    }
}
