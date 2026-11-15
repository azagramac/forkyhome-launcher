package dev.azagra.launcher_aosp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import dev.azagra.launcher_aosp.data.AppInfo
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.ui.DragSession
import dev.azagra.launcher_aosp.ui.DragSource
import dev.azagra.launcher_aosp.ui.isDropOverDock
import dev.azagra.launcher_aosp.ui.resolveDockSlot
import dev.azagra.launcher_aosp.ui.resolveGridTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragAndDropResolutionTest {

    private val dummyIcon = object : ImageBitmap {
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
    private val sampleApp = AppInfo(
        pkg = "com.android.chrome",
        cls = "com.google.android.apps.chrome.Main",
        label = "Chrome",
        icon = dummyIcon,
        homeIcon = dummyIcon,
    )
    private val gridBounds = Rect(left = 0f, top = 0f, right = 400f, bottom = 500f)
    private val dockBounds = Rect(left = 0f, top = 520f, right = 400f, bottom = 600f)

    @Test
    fun `isDropOverDock allows apps from drawer and home but rejects folders and widgets`() {
        val drawerDrag = DragSession(DragSource.Drawer(sampleApp), sampleApp)
        val homeAppDrag = DragSession(
            DragSource.Home(HomeItem(1, ItemKind.APP, 0, 0, 0, 1, 1, sampleApp.key)),
            sampleApp,
        )
        val folderDrag = DragSession(
            DragSource.Home(HomeItem(2, ItemKind.FOLDER, 0, 0, 0, 1, 1, "Folder\t${sampleApp.key}")),
            sampleApp,
        )
        val widgetDrag = DragSession(
            DragSource.Home(HomeItem(3, ItemKind.WIDGET, 0, 0, 0, 2, 2, "101")),
            null,
        )

        val dropInDockPos = Offset(200f, 550f)
        assertTrue(isDropOverDock(drawerDrag, dropInDockPos, dockBounds, topMarginPx = 20f))
        assertTrue(isDropOverDock(homeAppDrag, dropInDockPos, dockBounds, topMarginPx = 20f))
        assertFalse(isDropOverDock(folderDrag, dropInDockPos, dockBounds, topMarginPx = 20f))
        assertFalse(isDropOverDock(widgetDrag, dropInDockPos, dockBounds, topMarginPx = 20f))
    }

    @Test
    fun `resolveDockSlot maps horizontal finger coordinate to target dock index`() {
        val session = DragSession(DragSource.Drawer(sampleApp), sampleApp)
        assertEquals(0, resolveDockSlot(session, Offset(20f, 550f), dockBounds, currentDockSize = 4, topMarginPx = 20f))
        assertEquals(2, resolveDockSlot(session, Offset(200f, 550f), dockBounds, currentDockSize = 4, topMarginPx = 20f))
        assertEquals(4, resolveDockSlot(session, Offset(390f, 550f), dockBounds, currentDockSize = 4, topMarginPx = 20f))
    }

    @Test
    fun `resolveGridTarget sets inCenter true for drawer and folder drags`() {
        val drawerSession = DragSession(DragSource.Drawer(sampleApp), sampleApp)
        val folderSession = DragSession(DragSource.Folder(folderId = 9, app = sampleApp), sampleApp)

        val targetFromDrawer = resolveGridTarget(drawerSession, Offset(5f, 5f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)
        val targetFromFolder = resolveGridTarget(folderSession, Offset(5f, 5f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)

        assertEquals(Triple(0, 0, true), targetFromDrawer)
        assertEquals(Triple(0, 0, true), targetFromFolder)
    }

    @Test
    fun `resolveGridTarget distinguishes cell center vs edge for home items`() {
        val homeItem = HomeItem(1, ItemKind.APP, 0, 0, 0, 1, 1, sampleApp.key)
        val homeSession = DragSession(DragSource.Home(homeItem), sampleApp)

        val centerTarget = resolveGridTarget(homeSession, Offset(150f, 250f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)
        assertEquals(Triple(1, 2, true), centerTarget)

        val edgeTarget = resolveGridTarget(homeSession, Offset(102f, 202f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)
        assertEquals(Triple(1, 2, false), edgeTarget)
    }

    @Test
    fun `resolveGridTarget clamps multi-cell widget coordinates within grid boundaries`() {
        val widgetItem = HomeItem(5, ItemKind.WIDGET, page = 0, col = 0, row = 0, spanX = 3, spanY = 2, data = "105")
        val widgetSession = DragSession(
            source = DragSource.Home(widgetItem),
            previewApp = null,
            widgetTouchOffset = Offset(50f, 50f),
        )

        val target = resolveGridTarget(widgetSession, Offset(390f, 490f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)
        assertNotNull(target)
        assertEquals(1, target!!.first)
        assertEquals(3, target.second)
    }

    @Test
    fun `resolveGridTarget returns null when pointer is over the dock`() {
        val drawerSession = DragSession(DragSource.Drawer(sampleApp), sampleApp)
        val target = resolveGridTarget(drawerSession, Offset(200f, 560f), gridBounds, dockBounds, cols = 4, rows = 5, topMarginPx = 20f)
        assertNull(target)
    }
}
