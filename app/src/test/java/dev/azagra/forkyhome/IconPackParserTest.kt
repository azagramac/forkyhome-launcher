package dev.azagra.forkyhome

import dev.azagra.forkyhome.data.clampIconPackScale
import dev.azagra.forkyhome.data.registerComponentMappingToMaps
import dev.azagra.forkyhome.data.resolveCalendarDrawableName
import dev.azagra.forkyhome.data.resolvePackDrawableName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackParserTest {

    @Test
    fun `registerComponentMappingToMaps normalizes short and full ComponentInfo formats`() {
        val compMap = mutableMapOf<String, String>()
        val pkgMap = mutableMapOf<String, String>()

        registerComponentMappingToMaps(
            rawComp = "ComponentInfo{com.whatsapp/.Main}",
            target = "whatsapp_icon",
            compOut = compMap,
            pkgOut = pkgMap,
        )

        assertEquals("whatsapp_icon", compMap["com.whatsapp/com.whatsapp.Main"])
        assertEquals("whatsapp_icon", compMap["com.whatsapp/.Main"])
        assertEquals("whatsapp_icon", compMap["ComponentInfo{com.whatsapp/com.whatsapp.Main}"])
        assertEquals("whatsapp_icon", compMap["ComponentInfo{com.whatsapp/.Main}"])
        assertEquals("whatsapp_icon", pkgMap["com.whatsapp"])
    }

    @Test
    fun `resolvePackDrawableName falls back to package name when main activity changes`() {
        val compMap = mutableMapOf<String, String>()
        val pkgMap = mutableMapOf<String, String>()

        registerComponentMappingToMaps(
            rawComp = "ComponentInfo{org.telegram.messenger/org.telegram.ui.LaunchActivity}",
            target = "telegram_drawable",
            compOut = compMap,
            pkgOut = pkgMap,
        )

        assertEquals(
            "telegram_drawable",
            resolvePackDrawableName("org.telegram.messenger", "org.telegram.ui.LaunchActivity", compMap, pkgMap),
        )

        assertEquals(
            "telegram_drawable",
            resolvePackDrawableName("org.telegram.messenger", "org.telegram.messenger.DefaultIconAlias", compMap, pkgMap),
        )

        assertNull(resolvePackDrawableName("com.unknown.app", "com.unknown.app.MainActivity", compMap, pkgMap))
    }

    @Test
    fun `resolveCalendarDrawableName builds valid dynamic calendar resource names`() {
        assertEquals("calendar_1", resolveCalendarDrawableName("calendar_", 1))
        assertEquals("calendar_15", resolveCalendarDrawableName("calendar_", 15))
        assertEquals("google_calendar_31", resolveCalendarDrawableName("google_calendar_", 31))
        assertEquals("cal_1", resolveCalendarDrawableName("cal_", 0))
        assertEquals("cal_31", resolveCalendarDrawableName("cal_", 45))
    }

    @Test
    fun `clampIconPackScale keeps mask scale factor in safe rendering range`() {
        assertEquals(0.85f, clampIconPackScale(0.85f), 0.001f)
        assertEquals(0.4f, clampIconPackScale(0.05f), 0.001f)
        assertEquals(1.2f, clampIconPackScale(3.5f), 0.001f)
    }
}
