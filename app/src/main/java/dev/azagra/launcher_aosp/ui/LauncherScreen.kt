package dev.azagra.launcher_aosp.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.azagra.launcher_aosp.Container
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.data.AppInfo
import dev.azagra.launcher_aosp.data.DOCK_MAX
import dev.azagra.launcher_aosp.data.FolderPayload
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.IconConfig
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.notif.NotificationCounter
import dev.azagra.launcher_aosp.widgets.WidgetController
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FloatingMenuShape = RoundedCornerShape(24.dp)
private val CellHighlightShape = RoundedCornerShape(20.dp)
private val DockShape = RoundedCornerShape(32.dp)

private sealed interface DragSource {
    data class Home(val item: HomeItem) : DragSource
    data class Dock(val index: Int, val appKey: String) : DragSource
}

private data class DragSession(
    val source: DragSource,
    val previewApp: AppInfo?,
)

private fun isDropOverDock(
    session: DragSession,
    posInRoot: Offset,
    dockBounds: Rect,
    topMarginPx: Float,
): Boolean {
    val isDraggingFolder = (session.source as? DragSource.Home)?.item?.kind == ItemKind.FOLDER
    return !isDraggingFolder &&
        dockBounds.width > 0f &&
        posInRoot.y >= (dockBounds.top - topMarginPx)
}

private fun resolveDockSlot(
    session: DragSession,
    posInRoot: Offset,
    dockBounds: Rect,
    currentDockSize: Int,
    topMarginPx: Float,
): Int? {
    if (!isDropOverDock(session, posInRoot, dockBounds, topMarginPx)) return null
    val count = when (session.source) {
        is DragSource.Dock -> currentDockSize.coerceAtLeast(1)
        is DragSource.Home -> (currentDockSize + 1).coerceAtMost(DOCK_MAX).coerceAtLeast(1)
    }
    val relX = (posInRoot.x - dockBounds.left).coerceIn(0f, (dockBounds.width - 1f).coerceAtLeast(1f))
    return ((relX / dockBounds.width) * count).toInt().coerceIn(0, count - 1)
}

private fun resolveGridTarget(
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
    val relX = (posInRoot.x - gridBounds.left).coerceIn(0f, (gridBounds.width - 1f).coerceAtLeast(1f))
    val relY = (posInRoot.y - gridBounds.top).coerceIn(0f, (gridBounds.height - 1f).coerceAtLeast(1f))
    val exactCol = (relX / gridBounds.width) * cols
    val exactRow = (relY / gridBounds.height) * rows
    val col = exactCol.toInt().coerceIn(0, cols - 1)
    val row = exactRow.toInt().coerceIn(0, rows - 1)
    val fracX = exactCol - col
    val fracY = exactRow - row
    val inCenter = fracX in 0.25f..0.75f && fracY in 0.25f..0.75f
    return Triple(col, row, inCenter)
}

@SuppressLint("WrongConstant")
private fun expandNotificationsPanel(ctx: Context) {
    runCatching {
        val sbm = ctx.getSystemService("statusbar") ?: return
        val method = sbm.javaClass.getMethod("expandNotificationsPanel")
        method.invoke(sbm)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(s: LauncherSettings, widgets: WidgetController, container: Container) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
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
    val pagerState = rememberPagerState { s.homePages }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var showWidgets by remember { mutableStateOf(false) }
    var widgetToManage by remember { mutableStateOf<HomeItem?>(null) }
    var openFolderId by remember { mutableStateOf<Int?>(null) }

    // Autenticación de apps protegidas (Trebuchet Protected Apps)
    val keyguardManager = remember { ctx.getSystemService(KeyguardManager::class.java) }
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            pendingProtectedAction?.invoke()
        }
        pendingProtectedAction = null
    }

    // Coordenadas en tiempo real para Drag & Drop entre Escritorio y Dock
    var gridBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var isHoldingItem by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var dragPosInRoot by remember { mutableStateOf(Offset.Zero) }

    val homeCols = s.homeCols.coerceIn(4, 6)
    val homeRows = s.homeRows.coerceIn(4, 7)
    val dockMarginPx = with(density) { 20.dp.toPx() }

    LaunchedEffect(s.iconPack, s.themedIcons, dark) {
        repo.configure(IconConfig(s.iconPack, s.themedIcons, dark))
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) store.update {
            if (it.dockInit) it else it.copy(dock = repo.defaultDock(apps), dockInit = true)
        }
    }
    // Auto-añadir nuevas apps instaladas al escritorio si el ajuste de Trebuchet está activo
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
            dragSession = null
            isHoldingItem = false
            if (drawer.isOpen) drawer.animateTo(0f) else pagerState.animateScrollToPage(0)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = openFolderId != null) { openFolderId = null }
    BackHandler(enabled = drawerVisible && !showSettings && openFolderId == null) { drawer.animateTo(0f) }

    // ---- helpers ----
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
        if (pkg in latestSettings.protectedApps && keyguardManager?.isDeviceSecure == true) {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                ctx.getString(R.string.auth_protected_app_title),
                ctx.getString(R.string.auth_protected_app_desc),
            )
            if (intent != null) {
                pendingProtectedAction = action
                authLauncher.launch(intent)
                return
            }
        }
        action()
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
        add(MenuAction(ctx.getString(R.string.action_app_info), Icons.Default.Info) { details(app.pkg) })
        add(MenuAction(ctx.getString(R.string.action_uninstall), Icons.Default.Delete) { uninstall(app.pkg) })
    }
    fun drawerActions(app: AppInfo): List<MenuAction> = buildList {
        add(MenuAction(ctx.getString(R.string.action_add_to_home), Icons.Default.Add) {
            if (!checkUnlocked()) return@MenuAction
            scope.launch {
                if (store.addHomeItem(ItemKind.APP, app.key, 1, 1, pagerState.currentPage)) toast(ctx.getString(R.string.toast_added_to_home))
                else toast(ctx.getString(R.string.toast_no_space_on_home))
            }
        })
        if (app.key !in latestSettings.dock && latestSettings.dock.size < DOCK_MAX) {
            add(MenuAction(ctx.getString(R.string.action_add_to_dock), Icons.Default.Add) {
                if (!checkUnlocked()) return@MenuAction
                edit { it.copy(dock = (it.dock + app.key).take(DOCK_MAX)) }
            })
        }
        add(MenuAction(ctx.getString(R.string.action_hide_app), Icons.Default.Close) {
            edit { st -> st.copy(hiddenApps = st.hiddenApps + app.pkg) }
        })
        addAll(common(app))
    }
    fun homeActions(item: HomeItem, app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_home), Icons.Default.Close) {
            if (checkUnlocked()) edit { st -> st.copy(home = st.home.filter { it.id != item.id }) }
        },
    ) + common(app)
    fun dockActions(app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_dock), Icons.Default.Close) {
            if (checkUnlocked()) edit { st -> st.copy(dock = st.dock.filter { it != app.key }) }
        },
    ) + common(app)

    // Leemos los estados remember directamente dentro de finishDrag() para evitar cualquier captura obsoleta
    fun finishDrag() {
        val session = dragSession ?: return
        val dropPos = dragPosInRoot
        val currentGridBounds = gridBoundsInRoot
        val currentDockBounds = dockBoundsInRoot
        val currentPage = pagerState.currentPage
        val defaultFolderName = ctx.getString(R.string.folder_default_name)

        dragSession = null
        isHoldingItem = false

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

            when {
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
                    }
                }
                gridInfo != null -> {
                    val (destCol, destRow, inCenter) = gridInfo
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
                                    // Si soltamos una APP en el centro de otra APP o CARPETA -> Crear / Añadir a Carpeta
                                    if (src.item.kind == ItemKind.APP && (occupant.kind == ItemKind.FOLDER || inCenter)) {
                                        val updatedOccupant = if (occupant.kind == ItemKind.FOLDER) {
                                            val payload = FolderPayload.decode(occupant.data)
                                            val merged = (payload.apps + src.item.data).distinct()
                                            occupant.copy(data = payload.copy(apps = merged).encode())
                                        } else {
                                            val merged = listOf(occupant.data, src.item.data).distinct()
                                            occupant.copy(
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
                                        // Intercambiar posiciones entre ambos elementos del escritorio
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
                                    // Mover a celda vacía
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
                                            occupant.copy(
                                                kind = ItemKind.FOLDER,
                                                data = FolderPayload(defaultFolderName, listOf(occupant.data, src.appKey).distinct()).encode(),
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
                        }
                    }
                }
                else -> st
            }
        }
    }

    val activeDrag = dragSession
    val targetDockSlot: Int? = if (activeDrag != null) {
        resolveDockSlot(activeDrag, dragPosInRoot, dockBoundsInRoot, s.dock.size, dockMarginPx)
    } else null

    // ---- UI ----
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(density) { maxHeight.toPx() }
        var downwardPullPx by remember { mutableStateOf(0f) }

        // Escritorio: se atenúa y encoge un poco al abrir el cajón
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress
                    alpha = 1f - p
                    scaleX = 1f - 0.06f * p
                    scaleY = 1f - 0.06f * p
                }
                .draggable(
                    enabled = activeDrag == null && !isHoldingItem,
                    state = rememberDraggableState { dy ->
                        if (dy < 0f || drawer.progress > 0f) {
                            downwardPullPx = 0f
                            drawer.drag(-dy / hPx)
                        } else if (s.swipeDownNotifications) {
                            downwardPullPx += dy
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drawer.progress == 0f && s.swipeDownNotifications && (downwardPullPx > hPx * 0.12f || v > 900f)) {
                            downwardPullPx = 0f
                            expandNotificationsPanel(ctx)
                        } else {
                            downwardPullPx = 0f
                            drawer.settle(-v / hPx)
                        }
                    },
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = activeDrag == null && !isHoldingItem,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { gridBoundsInRoot = it.boundsInRoot() },
                beyondViewportPageCount = 1,
            ) { page ->
                // Leemos dragSession y dragPosInRoot dentro del ámbito de HorizontalPager para que
                // la celda resaltada se actualice en tiempo real a medida que movemos el dedo
                val pageDrag = dragSession
                val highlightedCell = if (pageDrag != null && page == pagerState.currentPage) {
                    resolveGridTarget(
                        session = pageDrag,
                        posInRoot = dragPosInRoot,
                        gridBounds = gridBoundsInRoot,
                        dockBounds = dockBoundsInRoot,
                        cols = homeCols,
                        rows = homeRows,
                        topMarginPx = dockMarginPx,
                    )
                } else null

                HomePage(
                    page = page,
                    items = s.home,
                    appMap = appMap,
                    counts = counts,
                    s = s,
                    homeCols = homeCols,
                    homeRows = homeRows,
                    widgets = widgets,
                    draggedHomeItemId = (pageDrag?.source as? DragSource.Home)?.item?.id,
                    highlightedCell = highlightedCell,
                    onLongPressEmpty = { localOffset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOffsetInRoot = Offset(
                            x = gridBoundsInRoot.left + localOffset.x,
                            y = gridBoundsInRoot.top + localOffset.y,
                        )
                        showMenu = true
                    },
                    actionsFor = ::homeActions,
                    onLaunch = ::openApp,
                    onOpenFolder = { folderItem -> openFolderId = folderItem.id },
                    onWidgetLong = {
                        if (checkUnlocked()) widgetToManage = it
                    },
                    onItemHoldChange = { holding -> isHoldingItem = holding },
                    onStartItemDrag = { item, previewApp, startRootPos ->
                        if (!checkUnlocked()) return@HomePage
                        showMenu = false
                        dragPosInRoot = startRootPos
                        dragSession = DragSession(DragSource.Home(item), previewApp)
                    },
                    onDragItem = { rootPos -> dragPosInRoot = rootPos },
                    onEndItemDrag = { finishDrag() },
                )
            }

            // Indicadores de página estilo Material You (píldora animada)
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(s.homePages) { i ->
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

            // Dock Material You (máx. 5) con soporte de reordenación por arrastre
            val dockBorderColor = if (targetDockSlot != null) MaterialTheme.colorScheme.primary else Color.Transparent
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onGloballyPositioned { dockBoundsInRoot = it.boundsInRoot() }
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
                            iconSize = 52.dp,
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

        // Capa flotante del icono mientras se arrastra por el escritorio o dock
        if (activeDrag != null && activeDrag.previewApp != null) {
            val iconPx = with(density) { 56.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (dragPosInRoot.x - iconPx / 2f).roundToInt(),
                            y = (dragPosInRoot.y - iconPx / 2f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = 0.92f
                    }
            ) {
                Image(
                    bitmap = activeDrag.previewApp.homeIcon,
                    contentDescription = activeDrag.previewApp.label,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .size(56.dp)
                        .let { if (s.iconShape != dev.azagra.launcher_aosp.data.IconShape.DEFAULT) it.clip(iconMaskShape(s.iconShape)) else it },
                )
            }
        }

        // Menú emergente flotante Material You al mantener pulsado un hueco vacío del escritorio
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

        // Mantenemos AppDrawer montado y trasladado por GPU (graphicsLayer) para evitar tirones al iniciar el gesto a 120fps
        AppDrawer(
            apps = apps,
            counts = counts,
            s = s,
            drawer = drawer,
            heightPx = hPx,
            onLaunch = ::openApp,
            actionsFor = ::drawerActions,
        )

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            val packs = remember { repo.packs.installed() }
            SettingsScreen(
                s = s,
                packs = packs,
                allApps = apps,
                onAuthenticate = { action ->
                    if (keyguardManager?.isDeviceSecure == true) {
                        @Suppress("DEPRECATION")
                        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            ctx.getString(R.string.auth_protected_app_title),
                            ctx.getString(R.string.auth_protected_app_desc),
                        )
                        if (intent != null) {
                            pendingProtectedAction = action
                            authLauncher.launch(intent)
                        } else action()
                    } else action()
                },
                onChange = { edit(it) },
                onClose = { showSettings = false },
            )
        }
    }

    // Diálogo flotante Material You para abrir y editar Carpetas (Trebuchet Folders)
    val openFolderItem = remember(s.home, openFolderId) {
        s.home.firstOrNull { it.id == openFolderId && it.kind == ItemKind.FOLDER }
    }
    if (openFolderItem != null) {
        val payload = remember(openFolderItem.data) { FolderPayload.decode(openFolderItem.data) }
        val defaultTitle = stringResource(R.string.folder_default_name)
        var folderTitle by remember(openFolderItem.id, payload.title) {
            mutableStateOf(payload.title.ifBlank { defaultTitle })
        }
        val folderApps = remember(payload.apps, appMap) {
            payload.apps.mapNotNull { appMap[it] }
        }
        AlertDialog(
            onDismissRequest = {
                if (folderTitle != payload.title) {
                    edit { st ->
                        st.copy(
                            home = st.home.map {
                                if (it.id == openFolderItem.id) it.copy(data = payload.copy(title = folderTitle).encode()) else it
                            }
                        )
                    }
                }
                openFolderId = null
            },
            shape = FloatingMenuShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                OutlinedTextField(
                    value = folderTitle,
                    onValueChange = { newTitle ->
                        folderTitle = newTitle
                        edit { st ->
                            st.copy(
                                home = st.home.map {
                                    if (it.id == openFolderItem.id) it.copy(data = payload.copy(title = newTitle).encode()) else it
                                }
                            )
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.folder_rename_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(folderApps, key = { it.key }) { app ->
                        AppIcon(
                            app = app,
                            badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                            showLabel = true,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            iconSize = 52.dp,
                            useHomeIcon = true,
                            iconShape = s.iconShape,
                            actionsProvider = {
                                listOf(
                                    MenuAction(ctx.getString(R.string.action_remove_from_folder), Icons.Default.Close) {
                                        if (!checkUnlocked()) return@MenuAction
                                        val remaining = payload.apps.filter { it != app.key }
                                        edit { st ->
                                            val updatedHome = when {
                                                remaining.isEmpty() -> st.home.filter { it.id != openFolderItem.id }
                                                remaining.size == 1 -> st.home.map {
                                                    if (it.id == openFolderItem.id) it.copy(kind = ItemKind.APP, data = remaining.first()) else it
                                                }
                                                else -> st.home.map {
                                                    if (it.id == openFolderItem.id) it.copy(data = payload.copy(apps = remaining).encode()) else it
                                                }
                                            }
                                            st.copy(home = updatedHome)
                                        }
                                        if (remaining.size <= 1) openFolderId = null
                                    }
                                ) + common(app)
                            },
                            onClick = {
                                openFolderId = null
                                openApp(app)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({ openFolderId = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showWidgets) {
        WidgetPicker(
            controller = widgets,
            onPick = { info -> showWidgets = false; widgets.request(info) },
            onDismiss = { showWidgets = false },
        )
    }

    // Diálogo Material You para redimensionar o quitar un Widget
    widgetToManage?.let { item ->
        var spanX by remember(item.id) { mutableIntStateOf(item.spanX.coerceIn(1, homeCols)) }
        var spanY by remember(item.id) { mutableIntStateOf(item.spanY.coerceIn(1, homeRows)) }
        AlertDialog(
            onDismissRequest = { widgetToManage = null },
            shape = FloatingMenuShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_remove_widget_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_remove_widget_message))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.widget_width_cols, spanX),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = spanX.toFloat(),
                        onValueChange = { spanX = it.roundToInt().coerceIn(1, homeCols) },
                        valueRange = 1f..homeCols.toFloat(),
                        steps = (homeCols - 2).coerceAtLeast(0),
                    )
                    Text(
                        stringResource(R.string.widget_height_rows, spanY),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = spanY.toFloat(),
                        onValueChange = { spanY = it.roundToInt().coerceIn(1, homeRows) },
                        valueRange = 1f..homeRows.toFloat(),
                        steps = (homeRows - 2).coerceAtLeast(0),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        val id = item.id
                        widgetToManage = null
                        item.data.toIntOrNull()?.let { widgets.delete(it) }
                        edit { st -> st.copy(home = st.home.filter { it.id != id }) }
                    }) {
                        Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton({
                        val id = item.id
                        val newW = spanX.coerceIn(1, homeCols - item.col)
                        val newH = spanY.coerceIn(1, homeRows - item.row)
                        widgetToManage = null
                        edit { st ->
                            st.copy(
                                home = st.home.map {
                                    if (it.id == id) it.copy(spanX = newW, spanY = newH) else it
                                }
                            )
                        }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            },
            dismissButton = {
                TextButton({ widgetToManage = null }) { Text(stringResource(R.string.action_cancel)) }
            },
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
    draggedHomeItemId: Int?,
    highlightedCell: Triple<Int, Int, Boolean>?,
    onLongPressEmpty: (Offset) -> Unit,
    actionsFor: (HomeItem, AppInfo) -> List<MenuAction>,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeItem) -> Unit,
    onWidgetLong: (HomeItem) -> Unit,
    onItemHoldChange: (Boolean) -> Unit,
    onStartItemDrag: (HomeItem, AppInfo?, Offset) -> Unit,
    onDragItem: (Offset) -> Unit,
    onEndItemDrag: () -> Unit,
) {
    val pageItems = remember(items, page) { items.filter { it.page == page } }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(pageItems, homeCols, homeRows) {
            detectTapGestures(onLongPress = { offset ->
                // Verificamos que el toque haya caído en una celda realmente vacía
                val col = ((offset.x / size.width) * homeCols).toInt().coerceIn(0, homeCols - 1)
                val row = ((offset.y / size.height) * homeRows).toInt().coerceIn(0, homeRows - 1)
                val occupied = pageItems.any {
                    col in it.col until (it.col + it.spanX) && row in it.row until (it.row + it.spanY)
                }
                if (!occupied) {
                    onLongPressEmpty(offset)
                }
            })
        }
    ) {
        val cw = maxWidth / homeCols
        val ch = maxHeight / homeRows

        // Indicador visual Material You de la celda destino (círculo si creará carpeta, celda redondeada si moverá)
        if (highlightedCell != null) {
            val (hCol, hRow, inCenter) = highlightedCell
            val hasOccupant = pageItems.any {
                it.id != draggedHomeItemId &&
                    (it.kind == ItemKind.APP || it.kind == ItemKind.FOLDER) &&
                    it.col == hCol && it.row == hRow
            }
            val shape = if (hasOccupant && inCenter) CircleShape else CellHighlightShape
            Box(
                Modifier
                    .offset(cw * hCol, ch * hRow)
                    .size(cw, ch)
                    .padding(6.dp)
                    .clip(shape)
                    .background(
                        if (hasOccupant && inCenter) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            )
        }

        pageItems.forEach { item ->
            key(item.id) {
                Box(
                    Modifier
                        .offset(cw * item.col, ch * item.row)
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
                                iconSize = 56.dp,
                                actionsProvider = { actionsFor(item, app) },
                                onClick = { onLaunch(app) },
                                modifier = Modifier.fillMaxWidth(),
                                useHomeIcon = true,
                                iconShape = s.iconShape,
                                enableDrag = !s.lockDesktop,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onPressHoldChange = onItemHoldChange,
                                onDragStart = { startRootPos -> onStartItemDrag(item, app, startRootPos) },
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
                                iconSize = 56.dp,
                                useHomeIcon = true,
                                enableDrag = !s.lockDesktop,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onClick = { onOpenFolder(item) },
                                onPressHoldChange = onItemHoldChange,
                                onDragStart = { startRootPos -> onStartItemDrag(item, previewApps.firstOrNull(), startRootPos) },
                                onDrag = onDragItem,
                                onDragEnd = onEndItemDrag,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ItemKind.WIDGET -> item.data.toIntOrNull()?.let { id ->
                            WidgetView(id, widgets, onLongPress = { onWidgetLong(item) }, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
