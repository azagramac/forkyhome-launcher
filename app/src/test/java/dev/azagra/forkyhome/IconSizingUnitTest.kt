package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.resolveIconSizeDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSizingUnitTest {

    @Test
    fun `resolveIconSizeDp increases base size when 4 columns are selected`() {
        val size4Cols = resolveIconSizeDp(cols = 4, scale = 1.0f)
        val size5Cols = resolveIconSizeDp(cols = 5, scale = 1.0f)
        val size6Cols = resolveIconSizeDp(cols = 6, scale = 1.0f)

        assertEquals(60f, size4Cols, 0.01f)
        assertEquals(54f, size5Cols, 0.01f)
        assertEquals(48f, size6Cols, 0.01f)
        assertTrue(size4Cols > size5Cols)
        assertTrue(size5Cols > size6Cols)
    }

    @Test
    fun `resolveIconSizeDp respects scale factor within safe bounds`() {
        val scaledDown = resolveIconSizeDp(cols = 4, scale = 0.85f)
        val scaledUp = resolveIconSizeDp(cols = 4, scale = 1.10f)

        assertEquals(51f, scaledDown, 0.01f)
        assertEquals(66f, scaledUp, 0.01f)
    }

    @Test
    fun `resolveIconSizeDp clamps extreme scale values to prevent overlapping`() {
        val max4Cols = resolveIconSizeDp(cols = 4, scale = 2.0f)
        val max5Cols = resolveIconSizeDp(cols = 5, scale = 2.0f)
        val max6Cols = resolveIconSizeDp(cols = 6, scale = 2.0f)
        val min6Cols = resolveIconSizeDp(cols = 6, scale = 0.2f)

        assertTrue(max4Cols <= 68f)
        assertTrue(max5Cols <= 60f)
        assertTrue(max6Cols <= 54f)
        assertTrue(min6Cols >= 36f)
    }
}
