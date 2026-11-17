package dev.azagra.forkyhome

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.ui.buildAlphabetIndexMap
import dev.azagra.forkyhome.ui.filterDrawerApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSearchAndIndexTest {

    private val dummyBmp = object : ImageBitmap {
        override val width: Int = 1
        override val height: Int = 1
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = true
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }

    private fun app(pkg: String, label: String) = AppInfo(
        pkg = pkg,
        cls = "$pkg.MainActivity",
        label = label,
        icon = dummyBmp,
        homeIcon = dummyBmp,
    )

    private val sampleCatalog = listOf(
        app("com.one.calc", "1Password"),
        app("com.android.calculator2", "Calculator"),
        app("com.android.calendar", "Calendar"),
        app("com.android.chrome", "Chrome"),
        app("com.google.android.gm", "Gmail"),
        app("org.telegram.messenger", "Telegram"),
        app("com.whatsapp", "WhatsApp"),
    )

    @Test
    fun `filterDrawerApps excludes hidden packages and keys`() {
        val hidden = setOf("com.whatsapp", "org.telegram.messenger")
        val visible = filterDrawerApps(sampleCatalog, query = "", hiddenApps = hidden)

        assertEquals(5, visible.size)
        assertTrue(visible.none { it.pkg in hidden })
    }

    @Test
    fun `filterDrawerApps performs case-insensitive trimmed search`() {
        val matches = filterDrawerApps(sampleCatalog, query = "  cal  ", hiddenApps = emptySet())
        assertEquals(listOf("Calculator", "Calendar"), matches.map { it.label })
    }

    @Test
    fun `buildAlphabetIndexMap maps leading letters and groups numbers under hash`() {
        val indexMap = buildAlphabetIndexMap(sampleCatalog.map { it.label })

        assertEquals(0, indexMap['#'])
        assertEquals(1, indexMap['C'])
        assertEquals(4, indexMap['G'])
        assertEquals(5, indexMap['T'])
        assertEquals(6, indexMap['W'])
    }
}
