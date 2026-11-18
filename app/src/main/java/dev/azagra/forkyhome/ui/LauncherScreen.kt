package dev.azagra.forkyhome.ui

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.azagra.forkyhome.Container
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.DOCK_MAX
import dev.azagra.forkyhome.data.FolderPayload
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.data.IconConfig
import dev.azagra.forkyhome.data.IconPacks
import dev.azagra.forkyhome.data.ItemKind
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.activePageCount
import dev.azagra.forkyhome.data.compactHomePages
import dev.azagra.forkyhome.data.resolveIconSizeDp
import dev.azagra.forkyhome.notif.NotificationCounter
import dev.azagra.forkyhome.util.LockScreenService
import dev.azagra.forkyhome.widgets.WidgetController
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FloatingMenuShape = RoundedCornerShape(24.dp)
private val FolderCardShape = RoundedCornerShape(28.dp)
private val CellHighlightShape = RoundedCornerShape(20.dp)
private val DockShape = RoundedCornerShape(32.dp)

internal sealed interface DragSource {
    data class Home(val item: HomeItem) : DragSource
    data class Dock(val index: Int, val appKey: String) : DragSource
    data class Drawer(val app: AppInfo) : DragSource
    data class Folder(val folderId: Int, val app: AppInfo) : DragSource
}

internal data class DragSession(
    val source: DragSource,
    val previewApp: AppInfo?,
    val widgetTouchOffset: Offset = Offset.Zero,
)

internal fun isDropOverDock(
    session: DragSession,
    posInRoot: Offset,
    dockBounds: Rect,
    topMarginPx: Float,
): Boolean {
    val isDraggingNonApp = (session.source as? DragSource.Home)?.item?.let { it.kind != ItemKind.APP } == true
    return !isDraggingNonApp &&
        dockBounds.width > 0f &&
        posInRoot.y >= (dockBounds.top - topMarginPx)
}

internal fun resolveDockSlot(
    session: DragSession,
    posInRoot: Offset,
    dockBounds: Rect,
    currentDockSize: Int,
    topMarginPx: Float,
): Int? {
    if (!isDropOverDock(session, posInRoot, dockBounds, topMarginPx)) return null
    val count = when (session.source) {
        is DragSource.Dock -> currentDockSize.coerceAtLeast(1)
        is DragSource.Home, is DragSource.Drawer, is DragSource.Folder ->
            (currentDockSize + 1).coerceAtMost(DOCK_MAX).coerceAtLeast(1)
    }
    val relX = (posInRoot.x - dockBounds.left).coerceIn(0f, (dockBounds.width - 1f).coerceAtLeast(1f))
    return ((relX / dockBounds.width) * count).toInt().coerceIn(0, count - 1)
}

internal fun resolveGridTarget(
    session: DragSession,
    posInRoot: Offset,
    gridBounds: Rect,
    dockBounds: Rect,
    cols: Int,
    rows: Int,
    topMarginPx: Float,
): Triple<Int, Int, Boolean>? {
    if (isDropOverDock(session, posInRoot, dockBounds, topMarginPx)) return null
    if (gridBounds.width <= 0f || gridBounds.height <= 0f) return null

    val homeSrc = session.source as? DragSource.Home
    if (homeSrc != null && homeSrc.item.kind == ItemKind.WIDGET) {
        val cw = gridBounds.width / cols
        val ch = gridBounds.height / rows
        val topLeft = posInRoot - session.widgetTouchOffset
        val maxCol = (cols - homeSrc.item.spanX).coerceAtLeast(0)
        val maxRow = (rows - homeSrc.item.spanY).coerceAtLeast(0)
        val col = ((topLeft.x - gridBounds.left) / cw).roundToInt().coerceIn(0, maxCol)
        val row = ((topLeft.y - gridBounds.top) / ch).roundToInt().coerceIn(0, maxRow)
        return Triple(col, row, false)
    }

    val relX = (posInRoot.x - gridBounds.left).coerceIn(0f, (gridBounds.width - 1f).coerceAtLeast(1f))
    val relY = (posInRoot.y - gridBounds.top).coerceIn(0f, (gridBounds.height - 1f).coerceAtLeast(1f))
    val exactCol = (relX / gridBounds.width) * cols
    val exactRow = (relY / gridBounds.height) * rows
    val col = exactCol.toInt().coerceIn(0, cols - 1)
    val row = exactRow.toInt().coerceIn(0, rows - 1)
    val fracX = exactCol - col
    val fracY = exactRow - row
    val inCenter = session.source is DragSource.Drawer ||
        session.source is DragSource.Folder ||
        (fracX in 0.15f..0.85f && fracY in 0.15f..0.85f)
    return Triple(col, row, inCenter)
}

internal fun placeOrResizeWidget(
    home: List<HomeItem>,
    widgetId: Int,
    targetPage: Int,
    targetCol: Int,
    targetRow: Int,
    targetSpanX: Int,
    targetSpanY: Int,
    cols: Int,
    rows: Int,
): List<HomeItem>? {
    val safeSpanX = targetSpanX.coerceIn(1, cols)
    val safeSpanY = targetSpanY.coerceIn(1, rows)
    val safeCol = targetCol.coerceIn(0, cols - safeSpanX)
    val safeRow = targetRow.coerceIn(0, rows - safeSpanY)

    val otherOnPage = home.filter { it.page == targetPage && it.id != widgetId }

    val overlapsWidget = otherOnPage.any { other ->
        other.kind == ItemKind.WIDGET &&
            safeCol < (other.col + other.spanX) &&
            (safeCol + safeSpanX) > other.col &&
            safeRow < (other.row + other.spanY) &&
            (safeRow + safeSpanY) > other.row
    }
    if (overlapsWidget) return null

    val displaced = otherOnPage.filter { other ->
        other.kind != ItemKind.WIDGET &&
            other.col in safeCol until (safeCol + safeSpanX) &&
            other.row in safeRow until (safeRow + safeSpanY)
    }

    if (displaced.isEmpty()) {
        return home.map {
            if (it.id == widgetId) {
                it.copy(page = targetPage, col = safeCol, row = safeRow, spanX = safeSpanX, spanY = safeSpanY)
            } else it
        }
    }

    val displacedIds = displaced.map { it.id }.toSet()
    val fixedItems = otherOnPage.filter { it.id !in displacedIds }
    val freeCells = mutableListOf<Pair<Int, Int>>()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (c in safeCol until (safeCol + safeSpanX) && r in safeRow until (safeRow + safeSpanY)) continue
            val occupied = fixedItems.any { f ->
                c in f.col until (f.col + f.spanX) && r in f.row until (f.row + f.spanY)
            }
            if (!occupied) freeCells.add(c to r)
        }
    }

    if (freeCells.size < displaced.size) return null

    val relocated = mutableMapOf<Int, Pair<Int, Int>>()
    for (item in displaced) {
        val nearest = freeCells.minByOrNull { (fc, fr) ->
            val dx = fc - item.col
            val dy = fr - item.row
            dx * dx + dy * dy
        } ?: return null
        freeCells.remove(nearest)
        relocated[item.id] = nearest
    }

    return home.map { item ->
        when {
            item.id == widgetId -> item.copy(
                page = targetPage,
                col = safeCol,
                row = safeRow,
                spanX = safeSpanX,
                spanY = safeSpanY,
            )
            item.id in relocated -> {
                val (nc, nr) = relocated.getValue(item.id)
                item.copy(col = nc, row = nr)
            }
            else -> item
        }
    }
}

internal fun removeAppFromFolderInList(
    home: List<HomeItem>,
    folderId: Int,
    appKey: String,
): List<HomeItem> {
    val folderItem = home.firstOrNull { it.id == folderId && it.kind == ItemKind.FOLDER } ?: return home
    val payload = FolderPayload.decode(folderItem.data)
    val remaining = payload.apps.filter { it != appKey }
    return when {
        remaining.isEmpty() -> home.filter { it.id != folderId }
        remaining.size == 1 -> home.map {
            if (it.id == folderId) it.copy(kind = ItemKind.APP, data = remaining.first()) else it
        }
        else -> home.map {
            if (it.id == folderId) it.copy(data = payload.copy(apps = remaining).encode()) else it
        }
    }
}

@SuppressLint("WrongConstant")
private fun expandNotificationsPanel(ctx: Context) {
    runCatching {
        val sbm = ctx.getSystemService("statusbar") ?: return
        val method = sbm.javaClass.getMethod("expandNotificationsPanel")
        method.invoke(sbm)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LauncherScreen(s: LauncherSettings, widgets: WidgetController, container: Container) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val repo = container.apps
    val store = container.settings

    val apps by repo.apps.collectAsStateWithLifecycle()
    val counts by NotificationCounter.counts.collectAsStateWithLifecycle()
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val dark = resolveDark(s.themeMode)
    val latestSettings by rememberUpdatedState(s)

    val drawer = remember { DrawerState(scope) }
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0f } }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var showWidgets by remember { mutableStateOf(false) }
    var resizingWidgetId by remember { mutableStateOf<Int?>(null) }
    var openFolderId by remember { mutableStateOf<Int?>(null) }
    var editingIconApp by remember { mutableStateOf<AppInfo?>(null) }

    var gridBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var isHoldingItem by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var dragPosInRoot by remember { mutableStateOf(Offset.Zero) }

    val pageCount = remember(s.home, dragSession) {
        activePageCount(s.home, isDraggingToWorkspace = dragSession != null)
    }
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.animateScrollToPage((pageCount - 1).coerceAtLeast(0))
        }
    }

    val keyguardManager = remember { ctx.getSystemService(KeyguardManager::class.java) }
    val mainExecutor = remember(ctx) { ctx.mainExecutor }
    fun authenticateUser(action: () -> Unit) {
        if (keyguardManager?.isDeviceSecure != true) {
            action()
            return
        }
        runCatching {
            val prompt = BiometricPrompt.Builder(ctx)
                .setTitle(ctx.getString(R.string.auth_protected_app_title))
                .setSubtitle(ctx.getString(R.string.auth_protected_app_desc))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        action()
                    }
                },
            )
        }.onFailure {
            Toast.makeText(ctx, ctx.getString(R.string.auth_protected_app_title), Toast.LENGTH_SHORT).show()
        }
    }

    val homeCols = s.homeCols.coerceIn(4, 6)
    val homeRows = s.homeRows.coerceIn(4, 7)
    val dockMarginPx = with(density) { 20.dp.toPx() }

    LaunchedEffect(s.iconPack, s.themedIcons, dark, s.iconShape, s.customIcons) {
        repo.configure(IconConfig(s.iconPack, s.themedIcons, dark, s.iconShape, s.customIcons))
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) store.update {
            if (it.dockInit) it else it.copy(dock = repo.defaultDock(apps), dockInit = true)
        }
    }
    LaunchedEffect(s.autoAddNewApps, s.lockDesktop) {
        if (s.autoAddNewApps && !s.lockDesktop) {
            repo.newPackages.collect { pkg ->
                delay(500)
                val newApp = repo.apps.value.firstOrNull { it.pkg == pkg } ?: return@collect
                val alreadyOnHome = latestSettings.home.any { it.kind == ItemKind.APP && it.data == newApp.key }
                if (!alreadyOnHome) {
                    store.addHomeItem(ItemKind.APP, newApp.key, 1, 1, pagerState.currentPage)
                }
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) { container.currentPage = pagerState.currentPage }
    LaunchedEffect(Unit) {
        container.homeEvents.collect {
            showSettings = false
            showMenu = false
            openFolderId = null
            editingIconApp = null
            resizingWidgetId = null
            dragSession = null
            isHoldingItem = false
            if (drawer.isOpen) drawer.animateTo(0f) else pagerState.animateScrollToPage(0)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = openFolderId != null) { openFolderId = null }
    BackHandler(enabled = resizingWidgetId != null) { resizingWidgetId = null }
    BackHandler(enabled = drawerVisible && !showSettings && openFolderId == null && resizingWidgetId == null) { drawer.animateTo(0f) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun edit(block: (LauncherSettings) -> LauncherSettings) { scope.launch { store.update(block) } }
    fun checkUnlocked(): Boolean {
        if (latestSettings.lockDesktop) {
            toast(ctx.getString(R.string.toast_desktop_locked))
            return false
        }
        return true
    }
    fun runWithProtection(pkg: String, action: () -> Unit) {
        if (pkg in latestSettings.protectedApps) {
            authenticateUser(action)
        } else {
            action()
        }
    }
    fun details(pkg: String) = ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    fun uninstall(pkg: String) = ctx.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    fun openApp(app: AppInfo) {
        runWithProtection(app.pkg) {
            repo.launchApp(ctx, app)
        }
    }
    fun shortcutActions(app: AppInfo): List<MenuAction> =
        repo.getShortcuts(app.pkg).map { sc ->
            MenuAction(sc.label, Icons.Default.PlayArrow, isShortcut = true) {
                runWithProtection(app.pkg) { repo.launchShortcut(sc) }
            }
        }
    fun common(app: AppInfo): List<MenuAction> = buildList {
        addAll(shortcutActions(app))
        val isProtected = app.pkg in latestSettings.protectedApps
        add(
            MenuAction(
                label = ctx.getString(if (isProtected) R.string.action_unprotect_app else R.string.action_protect_app),
                icon = Icons.Default.Lock,
            ) {
                runWithProtection(app.pkg) {
                    edit { st ->
                        val next = if (isProtected) st.protectedApps - app.pkg else st.protectedApps + app.pkg
                        st.copy(protectedApps = next)
                    }
                }
            }
        )
        add(MenuAction(ctx.getString(R.string.action_edit_icon), Icons.Default.Edit) {
            editingIconApp = app
        })
        add(MenuAction(ctx.getString(R.string.action_app_info), Icons.Default.Info) { details(app.pkg) })
        add(MenuAction(ctx.getString(R.string.action_uninstall), Icons.Default.Delete) { uninstall(app.pkg) })
    }
    fun drawerActions(app: AppInfo): List<MenuAction> = buildList {
        add(MenuAction(ctx.getString(R.string.action_hide_app), Icons.Default.Close) {
            edit { st -> st.copy(hiddenApps = st.hiddenApps + app.pkg) }
        })
        addAll(common(app))
    }
    fun homeActions(item: HomeItem, app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_home), Icons.Default.Close) {
            if (checkUnlocked()) edit { st ->
                val nextHome = compactHomePages(st.home.filter { it.id != item.id })
                st.copy(home = nextHome, homePages = activePageCount(nextHome))
            }
        },
    ) + common(app)
    fun dockActions(app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_dock), Icons.Default.Close) {
            if (checkUnlocked()) edit { st -> st.copy(dock = st.dock.filter { it != app.key }) }
        },
    ) + common(app)

    fun finishDrag() {
        val session = dragSession ?: return
        val dropPos = dragPosInRoot
        val currentGridBounds = gridBoundsInRoot
        val currentDockBounds = dockBoundsInRoot
        val currentPage = pagerState.currentPage
        val defaultFolderName = ctx.getString(R.string.folder_default_name)

        dragSession = null
        isHoldingItem = false
        if (session.source is DragSource.Folder) {
            openFolderId = null
        }

        if (latestSettings.lockDesktop) {
            toast(ctx.getString(R.string.toast_desktop_locked))
            return
        }

        edit { st ->
            val cols = st.homeCols.coerceIn(4, 6)
            val rows = st.homeRows.coerceIn(4, 7)
            val dockSlot = resolveDockSlot(
                session = session,
                posInRoot = dropPos,
                dockBounds = currentDockBounds,
                currentDockSize = st.dock.size,
                topMarginPx = dockMarginPx,
            )
            val gridInfo = resolveGridTarget(
                session = session,
                posInRoot = dropPos,
                gridBounds = currentGridBounds,
                dockBounds = currentDockBounds,
                cols = cols,
                rows = rows,
                topMarginPx = dockMarginPx,
            )

            val nextState = when {
                dockSlot != null -> {
                    when (val src = session.source) {
                        is DragSource.Dock -> {
                            val list = st.dock.toMutableList()
                            if (src.index in list.indices) {
                                val moved = list.removeAt(src.index)
                                val dest = dockSlot.coerceIn(0, list.size)
                                list.add(dest, moved)
                            }
                            st.copy(dock = list.take(DOCK_MAX))
                        }
                        is DragSource.Home -> {
                            if (src.item.kind != ItemKind.APP) return@edit st
                            val list = st.dock.toMutableList()
                            val existingIdx = list.indexOf(src.item.data)
                            if (existingIdx != -1) {
                                list.removeAt(existingIdx)
                                list.add(dockSlot.coerceIn(0, list.size), src.item.data)
                                st.copy(
                                    dock = list.take(DOCK_MAX),
                                    home = st.home.filter { it.id != src.item.id },
                                )
                            } else if (list.size < DOCK_MAX) {
                                list.add(dockSlot.coerceIn(0, list.size), src.item.data)
                                st.copy(
                                    dock = list.take(DOCK_MAX),
                                    home = st.home.filter { it.id != src.item.id },
                                )
                            } else {
                                val targetIdx = dockSlot.coerceIn(0, list.lastIndex)
                                val displacedAppKey = list[targetIdx]
                                list[targetIdx] = src.item.data
                                st.copy(
                                    dock = list,
                                    home = st.home.map {
                                        if (it.id == src.item.id) it.copy(data = displacedAppKey) else it
                                    },
                                )
                            }
                        }
                        is DragSource.Drawer -> {
                            val appKey = src.app.key
                            val list = st.dock.toMutableList()
                            val existingIdx = list.indexOf(appKey)
                            if (existingIdx != -1) {
                                list.removeAt(existingIdx)
                                list.add(dockSlot.coerceIn(0, list.size), appKey)
                            } else if (list.size < DOCK_MAX) {
                                list.add(dockSlot.coerceIn(0, list.size), appKey)
                            } else {
                                list[dockSlot.coerceIn(0, list.lastIndex)] = appKey
                            }
                            st.copy(dock = list.take(DOCK_MAX))
                        }
                        is DragSource.Folder -> {
                            val appKey = src.app.key
                            val updatedHome = removeAppFromFolderInList(st.home, src.folderId, appKey)
                            val list = st.dock.toMutableList()
                            val existingIdx = list.indexOf(appKey)
                            if (existingIdx != -1) {
                                list.removeAt(existingIdx)
                                list.add(dockSlot.coerceIn(0, list.size), appKey)
                            } else if (list.size < DOCK_MAX) {
                                list.add(dockSlot.coerceIn(0, list.size), appKey)
                            } else {
                                list[dockSlot.coerceIn(0, list.lastIndex)] = appKey
                            }
                            st.copy(dock = list.take(DOCK_MAX), home = updatedHome)
                        }
                    }
                }
                gridInfo != null -> {
                    val (destCol, destRow, inCenter) = gridInfo
                    val draggedWidget = (session.source as? DragSource.Home)?.item?.takeIf { it.kind == ItemKind.WIDGET }
                    if (draggedWidget != null) {
                        val updatedHome = placeOrResizeWidget(
                            home = st.home,
                            widgetId = draggedWidget.id,
                            targetPage = currentPage,
                            targetCol = destCol,
                            targetRow = destRow,
                            targetSpanX = draggedWidget.spanX,
                            targetSpanY = draggedWidget.spanY,
                            cols = cols,
                            rows = rows,
                        )
                        if (updatedHome != null) st.copy(home = updatedHome) else st
                    } else {
                        val widgetBlocker = st.home.firstOrNull {
                            it.page == currentPage &&
                                it.kind == ItemKind.WIDGET &&
                                destCol in it.col until (it.col + it.spanX) &&
                                destRow in it.row until (it.row + it.spanY)
                        }
                        if (widgetBlocker != null) {
                            st
                        } else {
                            val occupant = st.home.firstOrNull {
                                it.page == currentPage &&
                                    (it.kind == ItemKind.APP || it.kind == ItemKind.FOLDER) &&
                                    it.col == destCol &&
                                    it.row == destRow
                            }
                            when (val src = session.source) {
                                is DragSource.Home -> {
                                    if (occupant != null && occupant.id != src.item.id) {
                                        if (src.item.kind == ItemKind.APP && (occupant.kind == ItemKind.FOLDER || inCenter)) {
                                            val updatedOccupant = if (occupant.kind == ItemKind.FOLDER) {
                                                val payload = FolderPayload.decode(occupant.data)
                                                val merged = (payload.apps + src.item.data).distinct()
                                                occupant.copy(data = payload.copy(apps = merged).encode())
                                            } else {
                                                val merged = listOf(occupant.data, src.item.data).distinct()
                                                if (merged.size < 2) occupant else occupant.copy(
                                                    kind = ItemKind.FOLDER,
                                                    data = FolderPayload(defaultFolderName, merged).encode(),
                                                )
                                            }
                                            st.copy(
                                                home = st.home
                                                    .filter { it.id != src.item.id }
                                                    .map { if (it.id == occupant.id) updatedOccupant else it }
                                            )
                                        } else {
                                            st.copy(
                                                home = st.home.map {
                                                    when (it.id) {
                                                        src.item.id -> it.copy(page = currentPage, col = destCol, row = destRow)
                                                        occupant.id -> it.copy(page = src.item.page, col = src.item.col, row = src.item.row)
                                                        else -> it
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        st.copy(
                                            home = st.home.map {
                                                if (it.id == src.item.id) it.copy(page = currentPage, col = destCol, row = destRow) else it
                                            }
                                        )
                                    }
                                }
                                is DragSource.Dock -> {
                                    val list = st.dock.toMutableList()
                                    if (occupant != null) {
                                        if (occupant.kind == ItemKind.FOLDER || inCenter) {
                                            if (src.index in list.indices) list.removeAt(src.index)
                                            val updatedOccupant = if (occupant.kind == ItemKind.FOLDER) {
                                                val payload = FolderPayload.decode(occupant.data)
                                                occupant.copy(data = payload.copy(apps = (payload.apps + src.appKey).distinct()).encode())
                                            } else {
                                                val merged = listOf(occupant.data, src.appKey).distinct()
                                                if (merged.size < 2) occupant else occupant.copy(
                                                    kind = ItemKind.FOLDER,
                                                    data = FolderPayload(defaultFolderName, merged).encode(),
                                                )
                                            }
                                            st.copy(
                                                dock = list,
                                                home = st.home.map { if (it.id == occupant.id) updatedOccupant else it },
                                            )
                                        } else if (occupant.kind == ItemKind.APP) {
                                            if (src.index in list.indices) {
                                                list[src.index] = occupant.data
                                            }
                                            st.copy(
                                                dock = list,
                                                home = st.home.map {
                                                    if (it.id == occupant.id) it.copy(data = src.appKey) else it
                                                },
                                            )
                                        } else st
                                    } else {
                                        if (src.index in list.indices) list.removeAt(src.index)
                                        val nextId = (st.home.maxOfOrNull { it.id } ?: 0) + 1
                                        val newHomeItem = HomeItem(
                                            id = nextId,
                                            kind = ItemKind.APP,
                                            page = currentPage,
                                            col = destCol,
                                            row = destRow,
                                            spanX = 1,
                                            spanY = 1,
                                            data = src.appKey,
                                        )
                                        st.copy(
                                            dock = list,
                                            home = st.home + newHomeItem,
                                        )
                                    }
                                }
                                is DragSource.Drawer -> {
                                    val appKey = src.app.key
                                    if (occupant != null) {
                                        val updatedOccupant = if (occupant.kind == ItemKind.FOLDER) {
                                            val payload = FolderPayload.decode(occupant.data)
                                            occupant.copy(data = payload.copy(apps = (payload.apps + appKey).distinct()).encode())
                                        } else {
                                            val merged = listOf(occupant.data, appKey).distinct()
                                            if (merged.size < 2) occupant else occupant.copy(
                                                kind = ItemKind.FOLDER,
                                                data = FolderPayload(defaultFolderName, merged).encode(),
                                            )
                                        }
                                        st.copy(
                                            home = st.home.map { if (it.id == occupant.id) updatedOccupant else it }
                                        )
                                    } else {
                                        val nextId = (st.home.maxOfOrNull { it.id } ?: 0) + 1
                                        val newHomeItem = HomeItem(
                                            id = nextId,
                                            kind = ItemKind.APP,
                                            page = currentPage,
                                            col = destCol,
                                            row = destRow,
                                            spanX = 1,
                                            spanY = 1,
                                            data = appKey,
                                        )
                                        st.copy(home = st.home + newHomeItem)
                                    }
                                }
                                is DragSource.Folder -> {
                                    val appKey = src.app.key
                                    val baseHome = removeAppFromFolderInList(st.home, src.folderId, appKey)
                                    val occupantAfterRemoval = baseHome.firstOrNull {
                                        it.page == currentPage &&
                                            (it.kind == ItemKind.APP || it.kind == ItemKind.FOLDER) &&
                                            it.col == destCol &&
                                            it.row == destRow
                                    }
                                    if (occupantAfterRemoval != null) {
                                        val updatedOccupant = if (occupantAfterRemoval.kind == ItemKind.FOLDER) {
                                            val payload = FolderPayload.decode(occupantAfterRemoval.data)
                                            occupantAfterRemoval.copy(data = payload.copy(apps = (payload.apps + appKey).distinct()).encode())
                                        } else {
                                            val merged = listOf(occupantAfterRemoval.data, appKey).distinct()
                                            if (merged.size < 2) occupantAfterRemoval else occupantAfterRemoval.copy(
                                                kind = ItemKind.FOLDER,
                                                data = FolderPayload(defaultFolderName, merged).encode(),
                                            )
                                        }
                                        st.copy(
                                            home = baseHome.map { if (it.id == occupantAfterRemoval.id) updatedOccupant else it }
                                        )
                                    } else {
                                        val nextId = (baseHome.maxOfOrNull { it.id } ?: 0) + 1
                                        val newHomeItem = HomeItem(
                                            id = nextId,
                                            kind = ItemKind.APP,
                                            page = currentPage,
                                            col = destCol,
                                            row = destRow,
                                            spanX = 1,
                                            spanY = 1,
                                            data = appKey,
                                        )
                                        st.copy(home = baseHome + newHomeItem)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> st
            }
            val compactedHome = compactHomePages(nextState.home)
            nextState.copy(home = compactedHome, homePages = activePageCount(compactedHome))
        }
    }

    val activeDrag = dragSession
    val isDraggingOrHolding = activeDrag != null || isHoldingItem
    val draggedDrawerAppKey = (activeDrag?.source as? DragSource.Drawer)?.app?.key
    val isDraggingFromFolder = activeDrag?.source is DragSource.Folder
    val targetDockSlot by remember(dockMarginPx) {
        derivedStateOf {
            val d = dragSession ?: return@derivedStateOf null
            resolveDockSlot(d, dragPosInRoot, dockBoundsInRoot, latestSettings.dock.size, dockMarginPx)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val dragSpanPx = remember(hPx) { (hPx * 0.68f).coerceAtLeast(1f) }
        val edgeZonePx = with(density) { 38.dp.toPx() }
        var downwardPullPx by remember { mutableStateOf(0f) }

        val edgeDir by remember(wPx, edgeZonePx, pageCount) {
            derivedStateOf {
                when {
                    dragSession == null || targetDockSlot != null -> 0
                    dragPosInRoot.x > 0f && dragPosInRoot.x < edgeZonePx && pagerState.currentPage > 0 -> -1
                    dragPosInRoot.x > (wPx - edgeZonePx) && pagerState.currentPage < pageCount - 1 -> 1
                    else -> 0
                }
            }
        }
        LaunchedEffect(edgeDir, pagerState.currentPage, pageCount) {
            if (edgeDir != 0) {
                delay(450)
                val next = (pagerState.currentPage + edgeDir).coerceIn(0, pageCount - 1)
                if (next != pagerState.currentPage) {
                    pagerState.animateScrollToPage(next)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress.coerceIn(0f, 1f)
                    alpha = (1f - p * 1.1f).coerceIn(0f, 1f)
                    val sScale = 1f - 0.06f * p
                    scaleX = sScale
                    scaleY = sScale
                    translationY = -p * 28.dp.toPx()
                }
                .draggable(
                    enabled = !isDraggingOrHolding && openFolderId == null,
                    state = rememberDraggableState { dy ->
                        if (dy < 0f || drawer.progress > 0f) {
                            downwardPullPx = 0f
                            drawer.drag(-dy / dragSpanPx)
                        } else if (s.swipeDownNotifications) {
                            downwardPullPx += dy
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drawer.progress == 0f && s.swipeDownNotifications && (downwardPullPx > hPx * 0.11f || v > 850f)) {
                            downwardPullPx = 0f
                            expandNotificationsPanel(ctx)
                        } else {
                            downwardPullPx = 0f
                            drawer.settle(-v / dragSpanPx)
                        }
                    },
                )
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isDraggingOrHolding && openFolderId == null && resizingWidgetId == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                val pageDrag = dragSession
                val highlightedCell by remember(page, homeCols, homeRows, dockMarginPx) {
                    derivedStateOf {
                        val d = dragSession
                        if (d != null && page == pagerState.currentPage) {
                            resolveGridTarget(
                                session = d,
                                posInRoot = dragPosInRoot,
                                gridBounds = gridBoundsInRoot,
                                dockBounds = dockBoundsInRoot,
                                cols = homeCols,
                                rows = homeRows,
                                topMarginPx = dockMarginPx,
                            )
                        } else null
                    }
                }
                val draggedHomeItem = (pageDrag?.source as? DragSource.Home)?.item

                HomePage(
                    page = page,
                    items = s.home,
                    appMap = appMap,
                    counts = counts,
                    s = s,
                    homeCols = homeCols,
                    homeRows = homeRows,
                    widgets = widgets,
                    isDragging = pageDrag != null,
                    gridOriginInRoot = gridBoundsInRoot.topLeft,
                    onGridBoundsChanged = { bounds ->
                        if (page == pagerState.currentPage) {
                            gridBoundsInRoot = bounds
                        }
                    },
                    draggedHomeItem = draggedHomeItem,
                    resizingWidgetId = if (pageDrag == null) resizingWidgetId else null,
                    highlightedCell = highlightedCell,
                    onLongPressEmpty = { localOffset ->
                        resizingWidgetId = null
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOffsetInRoot = Offset(
                            x = gridBoundsInRoot.left + localOffset.x,
                            y = gridBoundsInRoot.top + localOffset.y,
                        )
                        showMenu = true
                    },
                    onTapEmpty = {
                        resizingWidgetId = null
                    },
                    onDoubleTapEmpty = {
                        resizingWidgetId = null
                        if (s.doubleTapToSleep) {
                            val locked = LockScreenService.lockScreen(ctx)
                            if (!locked) {
                                toast(ctx.getString(R.string.toast_enable_accessibility_lock))
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    },
                    actionsFor = ::homeActions,
                    onLaunch = ::openApp,
                    onOpenFolder = { folderItem ->
                        resizingWidgetId = null
                        openFolderId = folderItem.id
                    },
                    onActivateWidgetResize = { item ->
                        if (checkUnlocked()) {
                            showMenu = false
                            openFolderId = null
                            resizingWidgetId = item.id
                        }
                    },
                    onDismissWidgetResize = {
                        resizingWidgetId = null
                    },
                    onResizeWidget = { item, newCol, newRow, newSpanX, newSpanY ->
                        if (!checkUnlocked()) false
                        else {
                            val updated = placeOrResizeWidget(
                                home = latestSettings.home,
                                widgetId = item.id,
                                targetPage = page,
                                targetCol = newCol,
                                targetRow = newRow,
                                targetSpanX = newSpanX,
                                targetSpanY = newSpanY,
                                cols = homeCols,
                                rows = homeRows,
                            )
                            if (updated != null) {
                                val compacted = compactHomePages(updated)
                                edit { st -> st.copy(home = compacted, homePages = activePageCount(compacted)) }
                                true
                            } else false
                        }
                    },
                    onRemoveWidget = { item ->
                        if (checkUnlocked()) {
                            resizingWidgetId = null
                            item.data.toIntOrNull()?.let { widgets.delete(it) }
                            edit { st ->
                                val compacted = compactHomePages(st.home.filter { it.id != item.id })
                                st.copy(home = compacted, homePages = activePageCount(compacted))
                            }
                        }
                    },
                    onItemHoldChange = { holding -> isHoldingItem = holding },
                    onStartItemDrag = { item, previewApp, startRootPos, widgetTouchOffset ->
                        if (!checkUnlocked()) return@HomePage
                        showMenu = false
                        openFolderId = null
                        if (item.kind == ItemKind.WIDGET) {
                            resizingWidgetId = item.id
                        } else {
                            resizingWidgetId = null
                        }
                        dragPosInRoot = startRootPos
                        dragSession = DragSession(DragSource.Home(item), previewApp, widgetTouchOffset)
                    },
                    onDragItem = { rootPos -> dragPosInRoot = rootPos },
                    onEndItemDrag = { finishDrag() },
                )
            }

            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { i ->
                    val sel = i == pagerState.currentPage
                    val dotWidth by animateDpAsState(if (sel) 20.dp else 8.dp, label = "pageDot")
                    Box(
                        Modifier
                            .width(dotWidth)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                    )
                }
            }

            val dockBorderColor = if (targetDockSlot != null) MaterialTheme.colorScheme.primary else Color.Transparent
            val dockIconSize = remember(s.homeIconScale) {
                (52f * s.homeIconScale.coerceIn(0.85f, 1.15f)).coerceIn(42f, 58f).dp
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onPlaced { dockBoundsInRoot = it.boundsInRoot() }
                    .clip(DockShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f))
                    .border(2.dp, dockBorderColor, DockShape)
                    .heightIn(min = 76.dp)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                s.dock.take(DOCK_MAX).forEachIndexed { idx, appKey ->
                    val app = appMap[appKey] ?: return@forEachIndexed
                    val isDraggedDockItem = (activeDrag?.source as? DragSource.Dock)?.index == idx
                    val isHighlightedSlot = targetDockSlot == idx
                    Box(
                        modifier = Modifier
                            .clip(CellHighlightShape)
                            .background(
                                if (isHighlightedSlot) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            app = app,
                            badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                            showLabel = false,
                            labelColor = Color.Unspecified,
                            iconSize = dockIconSize,
                            actionsProvider = { dockActions(app) },
                            onClick = { openApp(app) },
                            useHomeIcon = true,
                            iconShape = s.iconShape,
                            enableDrag = !s.lockDesktop,
                            isBeingDragged = isDraggedDockItem,
                            onPressHoldChange = { holding -> isHoldingItem = holding },
                            onDragStart = { startRootPos ->
                                if (!checkUnlocked()) return@AppIcon
                                showMenu = false
                                openFolderId = null
                                dragPosInRoot = startRootPos
                                dragSession = DragSession(DragSource.Dock(idx, appKey), app)
                            },
                            onDrag = { rootPos -> dragPosInRoot = rootPos },
                            onDragEnd = { finishDrag() },
                        )
                    }
                }
            }
        }

        val openFolderItem = remember(s.home, openFolderId) {
            s.home.firstOrNull { it.id == openFolderId && it.kind == ItemKind.FOLDER }
        }
        var lastVisibleFolder by remember { mutableStateOf<HomeItem?>(null) }
        if (openFolderItem != null) {
            lastVisibleFolder = openFolderItem
        }
        val keyboardController = LocalSoftwareKeyboardController.current
        AnimatedVisibility(
            visible = openFolderItem != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            val activeFolder = openFolderItem ?: lastVisibleFolder ?: return@AnimatedVisibility
            val payload = remember(activeFolder.data) { FolderPayload.decode(activeFolder.data) }
            val defaultTitle = stringResource(R.string.folder_default_name)
            var folderTitleField by remember(activeFolder.id) {
                val initial = payload.title.ifBlank { defaultTitle }
                mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
            }
            val folderApps = remember(payload.apps, appMap) {
                payload.apps.mapNotNull { appMap[it] }
            }
            val rows = remember(folderApps) { folderApps.chunked(3) }

            fun commitFolderTitle() {
                val cleanTitle = folderTitleField.text.trim().ifBlank { defaultTitle }
                if (cleanTitle != payload.title) {
                    edit { st ->
                        st.copy(
                            home = st.home.map {
                                if (it.id == activeFolder.id) it.copy(data = payload.copy(title = cleanTitle).encode()) else it
                            }
                        )
                    }
                }
            }

            fun saveTitleAndClose() {
                keyboardController?.hide()
                focusManager.clearFocus()
                commitFolderTitle()
                openFolderId = null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isDraggingFromFolder) 0f else 1f
                    }
                    .background(Color.Black.copy(alpha = if (isDraggingFromFolder) 0f else 0.38f))
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isDraggingFromFolder,
                        onClick = { saveTitleAndClose() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = FolderCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .animateEnterExit(
                            enter = scaleIn(
                                initialScale = 0.90f,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                            ),
                            exit = scaleOut(
                                targetScale = 0.90f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            ),
                        )
                        .padding(horizontal = 28.dp)
                        .widthIn(min = 260.dp, max = 360.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rows.forEach { rowApps ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    rowApps.forEach { app ->
                                        key(app.key) {
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AppIcon(
                                                    app = app,
                                                    badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                                                    showLabel = true,
                                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                                    iconSize = 54.dp,
                                                    useHomeIcon = true,
                                                    iconShape = s.iconShape,
                                                    enableDrag = !s.lockDesktop,
                                                    isBeingDragged = (activeDrag?.source as? DragSource.Folder)?.app?.key == app.key,
                                                    actionsProvider = {
                                                        listOf(
                                                            MenuAction(ctx.getString(R.string.action_remove_from_folder), Icons.Default.Close) {
                                                                if (!checkUnlocked()) return@MenuAction
                                                                val remainingCount = payload.apps.count { it != app.key }
                                                                edit { st ->
                                                                    val nextHome = compactHomePages(removeAppFromFolderInList(st.home, activeFolder.id, app.key))
                                                                    st.copy(home = nextHome, homePages = activePageCount(nextHome))
                                                                }
                                                                if (remainingCount <= 1) openFolderId = null
                                                            }
                                                        ) + common(app)
                                                    },
                                                    onClick = {
                                                        keyboardController?.hide()
                                                        openFolderId = null
                                                        openApp(app)
                                                    },
                                                    onPressHoldChange = { holding -> isHoldingItem = holding },
                                                    onDragStart = { startRootPos ->
                                                        if (!checkUnlocked()) return@AppIcon
                                                        keyboardController?.hide()
                                                        focusManager.clearFocus()
                                                        dragPosInRoot = startRootPos
                                                        dragSession = DragSession(DragSource.Folder(activeFolder.id, app), app)
                                                    },
                                                    onDrag = { rootPos -> dragPosInRoot = rootPos },
                                                    onDragEnd = { finishDrag() },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                    repeat(3 - rowApps.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )

                        BasicTextField(
                            value = folderTitleField,
                            onValueChange = { folderTitleField = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitFolderTitle()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        commitFolderTitle()
                                    }
                                },
                        )
                    }
                }
            }
        }

        if (showMenu && activeDrag == null) {
            val dpOffset = with(density) {
                DpOffset(menuOffsetInRoot.x.toDp(), menuOffsetInRoot.y.toDp())
            }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showMenu = false },
                offset = dpOffset,
                shape = FloatingMenuShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_widgets), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    onClick = {
                        showMenu = false
                        if (checkUnlocked()) showWidgets = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_wallpaper), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    onClick = { showMenu = false; openWallpaperPicker(ctx) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_launcher_settings), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    onClick = { showMenu = false; showSettings = true },
                )
            }
        }

        AppDrawer(
            apps = apps,
            counts = counts,
            s = s,
            drawer = drawer,
            heightPx = hPx,
            isHoldingOrDragging = isDraggingOrHolding,
            draggedDrawerAppKey = draggedDrawerAppKey,
            onLaunch = ::openApp,
            actionsFor = ::drawerActions,
            onItemHoldChange = { holding -> isHoldingItem = holding },
            onStartAppDrag = { app, startRootPos ->
                if (!checkUnlocked()) return@AppDrawer
                showMenu = false
                openFolderId = null
                dragPosInRoot = startRootPos
                dragSession = DragSession(DragSource.Drawer(app), app)
                drawer.animateTo(0f)
            },
            onDragApp = { rootPos -> dragPosInRoot = rootPos },
            onEndAppDrag = { finishDrag() },
        )

        if (activeDrag != null) {
            val draggedWidgetItem = (activeDrag.source as? DragSource.Home)?.item?.takeIf { it.kind == ItemKind.WIDGET }
            if (activeDrag.previewApp != null) {
                val iconPx = with(density) { 56.dp.toPx() }
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = dragPosInRoot.x - iconPx / 2f
                            translationY = dragPosInRoot.y - iconPx / 2f
                            scaleX = 1.18f
                            scaleY = 1.18f
                            alpha = 0.92f
                        }
                ) {
                    Image(
                        bitmap = activeDrag.previewApp.homeIcon,
                        contentDescription = activeDrag.previewApp.label,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.size(56.dp),
                    )
                }
            } else if (draggedWidgetItem != null && gridBoundsInRoot.width > 0f && gridBoundsInRoot.height > 0f) {
                val cwDp = with(density) { (gridBoundsInRoot.width / homeCols).toDp() }
                val chDp = with(density) { (gridBoundsInRoot.height / homeRows).toDp() }
                Box(
                    Modifier
                        .graphicsLayer {
                            val topLeft = dragPosInRoot - activeDrag.widgetTouchOffset
                            translationX = topLeft.x
                            translationY = topLeft.y
                        }
                        .size(cwDp * draggedWidgetItem.spanX, chDp * draggedWidgetItem.spanY)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                )
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            val packs by produceState<List<IconPacks.Pack>>(emptyList(), showSettings) {
                if (showSettings) {
                    value = withContext(Dispatchers.IO) { repo.packs.installed() }
                }
            }
            SettingsScreen(
                s = s,
                packs = packs,
                allApps = apps,
                onAuthenticate = { action -> authenticateUser(action) },
                onChange = { edit(it) },
                onClose = { showSettings = false },
            )
        }
    }

    if (showWidgets) {
        WidgetPicker(
            controller = widgets,
            onPick = { info -> showWidgets = false; widgets.request(info) },
            onDismiss = { showWidgets = false },
        )
    }

    editingIconApp?.let { targetApp ->
        IconOverrideDialog(
            app = targetApp,
            activePack = s.iconPack,
            currentOverride = s.customIcons[targetApp.key],
            packs = repo.packs,
            onSelectOverride = { newVal ->
                edit { st ->
                    val next = if (newVal == null) st.customIcons - targetApp.key else st.customIcons + (targetApp.key to newVal)
                    st.copy(customIcons = next)
                }
                editingIconApp = null
            },
            onDismiss = { editingIconApp = null },
        )
    }
}

@Composable
private fun HomePage(
    page: Int,
    items: List<HomeItem>,
    appMap: Map<String, AppInfo>,
    counts: Map<String, Int>,
    s: LauncherSettings,
    homeCols: Int,
    homeRows: Int,
    widgets: WidgetController,
    isDragging: Boolean,
    gridOriginInRoot: Offset,
    onGridBoundsChanged: (Rect) -> Unit,
    draggedHomeItem: HomeItem?,
    resizingWidgetId: Int?,
    highlightedCell: Triple<Int, Int, Boolean>?,
    onLongPressEmpty: (Offset) -> Unit,
    onTapEmpty: () -> Unit,
    onDoubleTapEmpty: () -> Unit,
    actionsFor: (HomeItem, AppInfo) -> List<MenuAction>,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeItem) -> Unit,
    onActivateWidgetResize: (HomeItem) -> Unit,
    onDismissWidgetResize: () -> Unit,
    onResizeWidget: (HomeItem, Int, Int, Int, Int) -> Boolean,
    onRemoveWidget: (HomeItem) -> Unit,
    onItemHoldChange: (Boolean) -> Unit,
    onStartItemDrag: (HomeItem, AppInfo?, Offset, Offset) -> Unit,
    onDragItem: (Offset) -> Unit,
    onEndItemDrag: () -> Unit,
) {
    val pageItems = remember(items, page) { items.filter { it.page == page } }
    val draggedHomeItemId = draggedHomeItem?.id

    Column(Modifier.fillMaxSize()) {
        if (page == 0 && s.showAtAGlance) {
            AtAGlanceHeader(s = s)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPlaced { onGridBoundsChanged(it.boundsInRoot()) }
                .pointerInput(pageItems, homeCols, homeRows, s.doubleTapToSleep) {
                    detectTapGestures(
                        onTap = { onTapEmpty() },
                        onDoubleTap = { offset ->
                            val col = ((offset.x / size.width) * homeCols).toInt().coerceIn(0, homeCols - 1)
                            val row = ((offset.y / size.height) * homeRows).toInt().coerceIn(0, homeRows - 1)
                            val occupied = pageItems.any {
                                col in it.col until (it.col + it.spanX) && row in it.row until (it.row + it.spanY)
                            }
                            if (!occupied) {
                                onDoubleTapEmpty()
                            }
                        },
                        onLongPress = { offset ->
                            val col = ((offset.x / size.width) * homeCols).toInt().coerceIn(0, homeCols - 1)
                            val row = ((offset.y / size.height) * homeRows).toInt().coerceIn(0, homeRows - 1)
                            val occupied = pageItems.any {
                                col in it.col until (it.col + it.spanX) && row in it.row until (it.row + it.spanY)
                            }
                            if (!occupied) {
                                onLongPressEmpty(offset)
                            }
                        },
                    )
                }
        ) {
            val cw = maxWidth / homeCols
            val ch = maxHeight / homeRows
            val homeIconSize = remember(homeCols, s.homeIconScale) {
                resolveIconSizeDp(homeCols, s.homeIconScale).dp
            }

            if (pageItems.isEmpty() && page > 0 && isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.28f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.new_page_drop_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

        if (highlightedCell != null) {
            val (hCol, hRow, inCenter) = highlightedCell
            val isDraggingWidget = draggedHomeItem?.kind == ItemKind.WIDGET
            val spanW = if (isDraggingWidget) draggedHomeItem!!.spanX else 1
            val spanH = if (isDraggingWidget) draggedHomeItem!!.spanY else 1
            val hasOccupant = !isDraggingWidget && pageItems.any {
                it.id != draggedHomeItemId &&
                    (it.kind == ItemKind.APP || it.kind == ItemKind.FOLDER) &&
                    it.col == hCol && it.row == hRow
            }
            val shape = if (hasOccupant && inCenter) CircleShape else CellHighlightShape
            val animHighlightX by animateDpAsState(
                targetValue = cw * hCol,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "highlightX",
            )
            val animHighlightY by animateDpAsState(
                targetValue = ch * hRow,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "highlightY",
            )
            Box(
                Modifier
                    .offset(animHighlightX, animHighlightY)
                    .size(cw * spanW, ch * spanH)
                    .padding(6.dp)
                    .clip(shape)
                    .background(
                        if (hasOccupant && inCenter) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            )
        }

        val density = LocalDensity.current
        val cwPx = with(density) { cw.toPx() }
        val chPx = with(density) { ch.toPx() }
        pageItems.forEach { item ->
            key(item.id) {
                val targetXPx = cwPx * item.col
                val targetYPx = chPx * item.row
                val animXPx = remember { Animatable(targetXPx) }
                val animYPx = remember { Animatable(targetYPx) }
                var lastCol by remember { mutableStateOf(item.col) }
                var lastRow by remember { mutableStateOf(item.row) }
                LaunchedEffect(targetXPx, item.col) {
                    if (item.col != lastCol) {
                        lastCol = item.col
                        animXPx.animateTo(
                            targetXPx,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
                        )
                    } else if (animXPx.value != targetXPx) {
                        animXPx.snapTo(targetXPx)
                    }
                }
                LaunchedEffect(targetYPx, item.row) {
                    if (item.row != lastRow) {
                        lastRow = item.row
                        animYPx.animateTo(
                            targetYPx,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
                        )
                    } else if (animYPx.value != targetYPx) {
                        animYPx.snapTo(targetYPx)
                    }
                }
                Box(
                    Modifier
                        .offset {
                            IntOffset(animXPx.value.roundToInt(), animYPx.value.roundToInt())
                        }
                        .size(cw * item.spanX, ch * item.spanY)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (item.kind) {
                        ItemKind.APP -> appMap[item.data]?.let { app ->
                            AppIcon(
                                app = app,
                                badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                                showLabel = s.showHomeLabels,
                                labelColor = Color.White,
                                iconSize = homeIconSize,
                                actionsProvider = { actionsFor(item, app) },
                                onClick = { onLaunch(app) },
                                modifier = Modifier.fillMaxWidth(),
                                useHomeIcon = true,
                                iconShape = s.iconShape,
                                enableDrag = !s.lockDesktop,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onPressHoldChange = onItemHoldChange,
                                onDragStart = { startRootPos -> onStartItemDrag(item, app, startRootPos, Offset.Zero) },
                                onDrag = onDragItem,
                                onDragEnd = onEndItemDrag,
                            )
                        }
                        ItemKind.FOLDER -> {
                            val payload = remember(item.data) { FolderPayload.decode(item.data) }
                            val previewApps = remember(payload.apps, appMap) {
                                payload.apps.mapNotNull { appMap[it] }
                            }
                            val defaultName = stringResource(R.string.folder_default_name)
                            FolderIcon(
                                title = payload.title.ifBlank { defaultName },
                                previewApps = previewApps,
                                showLabel = s.showHomeLabels,
                                labelColor = Color.White,
                                iconSize = homeIconSize,
                                useHomeIcon = true,
                                enableDrag = !s.lockDesktop,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onClick = { onOpenFolder(item) },
                                onPressHoldChange = onItemHoldChange,
                                onDragStart = { startRootPos -> onStartItemDrag(item, previewApps.firstOrNull(), startRootPos, Offset.Zero) },
                                onDrag = onDragItem,
                                onDragEnd = onEndItemDrag,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ItemKind.WIDGET -> item.data.toIntOrNull()?.let { id ->
                            WidgetView(
                                id = id,
                                controller = widgets,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onPressHoldChange = onItemHoldChange,
                                onActivateResizeFrame = { onActivateWidgetResize(item) },
                                onStartDrag = { startRootPos, touchOffset ->
                                    onStartItemDrag(item, null, startRootPos, touchOffset)
                                },
                                onDrag = onDragItem,
                                onEndDrag = onEndItemDrag,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        val resizingWidget = pageItems.firstOrNull { it.id == resizingWidgetId && it.kind == ItemKind.WIDGET }
        if (resizingWidget != null && !s.lockDesktop) {
            val appWidgetId = resizingWidget.data.toIntOrNull()
            val canReconfigure = remember(appWidgetId) {
                appWidgetId != null && widgets.canReconfigure(appWidgetId)
            }
            TrebuchetWidgetResizeFrame(
                item = resizingWidget,
                cw = cw,
                ch = ch,
                homeCols = homeCols,
                homeRows = homeRows,
                gridOriginInRoot = gridOriginInRoot,
                canReconfigure = canReconfigure,
                onReconfigure = {
                    if (appWidgetId != null) widgets.reconfigure(appWidgetId)
                },
                onRemove = { onRemoveWidget(resizingWidget) },
                onResize = { newCol, newRow, newSpanX, newSpanY ->
                    onResizeWidget(resizingWidget, newCol, newRow, newSpanX, newSpanY)
                },
                onPressHoldChange = onItemHoldChange,
                onStartMoveDrag = { startRootPos, touchOffset ->
                    onStartItemDrag(resizingWidget, null, startRootPos, touchOffset)
                },
                onMoveDrag = onDragItem,
                onEndMoveDrag = onEndItemDrag,
                onDismiss = onDismissWidgetResize,
            )
        }
        }
    }
}
