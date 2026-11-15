package dev.azagra.launcher_aosp.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.azagra.launcher_aosp.data.HOME_COLS
import dev.azagra.launcher_aosp.data.HOME_ROWS
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.data.IconConfig
import dev.azagra.launcher_aosp.data.ItemKind
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.notif.NotificationCounter
import dev.azagra.launcher_aosp.widgets.WidgetController
import kotlin.math.roundToInt
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
    val app: AppInfo,
    val startCenterInRoot: Offset,
)

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

    val drawer = remember { DrawerState(scope) }
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0f } }
    val pagerState = rememberPagerState { s.homePages }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var showWidgets by remember { mutableStateOf(false) }
    var widgetToRemove by remember { mutableStateOf<HomeItem?>(null) }

    // Coordenadas para Drag & Drop entre Escritorio y Dock
    var gridBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(s.iconPack, s.themedIcons, dark) {
        repo.configure(IconConfig(s.iconPack, s.themedIcons, dark))
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) store.update {
            if (it.dockInit) it else it.copy(dock = repo.defaultDock(apps), dockInit = true)
        }
    }
    LaunchedEffect(pagerState.currentPage) { container.currentPage = pagerState.currentPage }
    LaunchedEffect(Unit) {
        container.homeEvents.collect {
            showSettings = false
            showMenu = false
            dragSession = null
            if (drawer.isOpen) drawer.animateTo(0f) else pagerState.animateScrollToPage(0)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = drawerVisible && !showSettings) { drawer.animateTo(0f) }

    // ---- helpers ----
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun edit(block: (LauncherSettings) -> LauncherSettings) { scope.launch { store.update(block) } }
    fun details(pkg: String) = ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    fun uninstall(pkg: String) = ctx.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    fun openApp(app: AppInfo) {
        repo.launchApp(ctx, app)
    }
    fun common(app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_app_info), Icons.Default.Info) { details(app.pkg) },
        MenuAction(ctx.getString(R.string.action_uninstall), Icons.Default.Delete) { uninstall(app.pkg) },
    )
    fun drawerActions(app: AppInfo): List<MenuAction> = buildList {
        add(MenuAction(ctx.getString(R.string.action_add_to_home), Icons.Default.Add) {
            scope.launch {
                if (store.addHomeItem(ItemKind.APP, app.key, 1, 1, pagerState.currentPage)) toast(ctx.getString(R.string.toast_added_to_home))
                else toast(ctx.getString(R.string.toast_no_space_on_home))
            }
        })
        if (app.key !in s.dock && s.dock.size < DOCK_MAX) {
            add(MenuAction(ctx.getString(R.string.action_add_to_dock), Icons.Default.Add) { edit { it.copy(dock = (it.dock + app.key).take(DOCK_MAX)) } })
        }
        addAll(common(app))
    }
    fun homeActions(item: HomeItem, app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_home), Icons.Default.Close) { edit { st -> st.copy(home = st.home.filter { it.id != item.id }) } },
    ) + common(app)
    fun dockActions(app: AppInfo) = listOf(
        MenuAction(ctx.getString(R.string.action_remove_from_dock), Icons.Default.Close) { edit { st -> st.copy(dock = st.dock.filter { it != app.key }) } },
    ) + common(app)

    // Cálculo en tiempo real del destino del arrastre
    val activeDrag = dragSession
    val dragPosInRoot = if (activeDrag != null) activeDrag.startCenterInRoot + dragDelta else Offset.Zero
    val isOverDock = activeDrag != null &&
        dockBoundsInRoot.width > 0f &&
        dragPosInRoot.y >= (dockBoundsInRoot.top - with(density) { 20.dp.toPx() })

    val targetDockSlot: Int? = if (activeDrag != null && isOverDock) {
        val count = when (activeDrag.source) {
            is DragSource.Dock -> s.dock.size.coerceAtLeast(1)
            is DragSource.Home -> (s.dock.size + 1).coerceAtMost(DOCK_MAX).coerceAtLeast(1)
        }
        val relX = (dragPosInRoot.x - dockBoundsInRoot.left).coerceIn(0f, (dockBoundsInRoot.width - 1f).coerceAtLeast(1f))
        ((relX / dockBoundsInRoot.width) * count).toInt().coerceIn(0, count - 1)
    } else null

    val targetGridCell: Pair<Int, Int>? = if (activeDrag != null && !isOverDock && gridBoundsInRoot.width > 0f && gridBoundsInRoot.height > 0f) {
        val relX = (dragPosInRoot.x - gridBoundsInRoot.left).coerceIn(0f, (gridBoundsInRoot.width - 1f).coerceAtLeast(1f))
        val relY = (dragPosInRoot.y - gridBoundsInRoot.top).coerceIn(0f, (gridBoundsInRoot.height - 1f).coerceAtLeast(1f))
        val col = ((relX / gridBoundsInRoot.width) * HOME_COLS).toInt().coerceIn(0, HOME_COLS - 1)
        val row = ((relY / gridBoundsInRoot.height) * HOME_ROWS).toInt().coerceIn(0, HOME_ROWS - 1)
        col to row
    } else null

    fun finishDrag() {
        val session = dragSession ?: return
        val dockSlot = targetDockSlot
        val gridCell = targetGridCell
        val currentPage = pagerState.currentPage
        dragSession = null
        dragDelta = Offset.Zero

        edit { st ->
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
                                // Dock lleno: intercambiamos con el icono del dock en esa posición
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
                gridCell != null -> {
                    val (destCol, destRow) = gridCell
                    // Comprobamos si hay un widget ocupando la celda destino
                    val widgetBlocker = st.home.firstOrNull {
                        it.page == currentPage &&
                            it.kind == ItemKind.WIDGET &&
                            destCol in it.col until (it.col + it.spanX) &&
                            destRow in it.row until (it.row + it.spanY)
                    }
                    if (widgetBlocker != null) {
                        st
                    } else {
                        val occupantApp = st.home.firstOrNull {
                            it.page == currentPage &&
                                it.kind == ItemKind.APP &&
                                it.col == destCol &&
                                it.row == destRow
                        }
                        when (val src = session.source) {
                            is DragSource.Home -> {
                                if (occupantApp != null && occupantApp.id != src.item.id) {
                                    // Intercambiamos posiciones entre los dos iconos del escritorio
                                    st.copy(
                                        home = st.home.map {
                                            when (it.id) {
                                                src.item.id -> it.copy(page = currentPage, col = destCol, row = destRow)
                                                occupantApp.id -> it.copy(page = src.item.page, col = src.item.col, row = src.item.row)
                                                else -> it
                                            }
                                        }
                                    )
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
                                if (occupantApp != null) {
                                    // Intercambiamos el icono del dock con el icono del escritorio
                                    if (src.index in list.indices) {
                                        list[src.index] = occupantApp.data
                                    }
                                    st.copy(
                                        dock = list,
                                        home = st.home.map {
                                            if (it.id == occupantApp.id) it.copy(data = src.appKey) else it
                                        },
                                    )
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

    // ---- UI ----
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(density) { maxHeight.toPx() }

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
                    enabled = activeDrag == null,
                    state = rememberDraggableState { dy -> drawer.drag(-dy / hPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / hPx) },
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = activeDrag == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { gridBoundsInRoot = it.boundsInRoot() },
                beyondViewportPageCount = 1,
            ) { page ->
                HomePage(
                    page = page,
                    items = s.home,
                    appMap = appMap,
                    counts = counts,
                    s = s,
                    widgets = widgets,
                    draggedHomeItemId = (activeDrag?.source as? DragSource.Home)?.item?.id,
                    highlightedCell = if (page == pagerState.currentPage) targetGridCell else null,
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
                    onWidgetLong = { widgetToRemove = it },
                    onStartItemDrag = { item, app, startCenter ->
                        dragDelta = Offset.Zero
                        dragSession = DragSession(DragSource.Home(item), app, startCenter)
                    },
                    onDragItem = { delta -> dragDelta += delta },
                    onEndItemDrag = ::finishDrag,
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
                            enableDrag = true,
                            isBeingDragged = isDraggedDockItem,
                            onDragStart = { startCenter ->
                                dragDelta = Offset.Zero
                                dragSession = DragSession(DragSource.Dock(idx, appKey), app, startCenter)
                            },
                            onDrag = { delta -> dragDelta += delta },
                            onDragEnd = ::finishDrag,
                        )
                    }
                }
            }
        }

        // Capa flotante del icono mientras se arrastra por el escritorio o dock
        if (activeDrag != null) {
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
                    bitmap = activeDrag.app.homeIcon,
                    contentDescription = activeDrag.app.label,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        // Menú emergente flotante Material You al mantener pulsado un hueco del escritorio
        if (showMenu) {
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
                    onClick = { showMenu = false; showWidgets = true },
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
            SettingsScreen(s, packs, onChange = { edit(it) }, onClose = { showSettings = false })
        }
    }

    if (showWidgets) {
        WidgetPicker(
            controller = widgets,
            onPick = { info -> showWidgets = false; widgets.request(info) },
            onDismiss = { showWidgets = false },
        )
    }

    widgetToRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { widgetToRemove = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_remove_widget_title)) },
            text = { Text(stringResource(R.string.dialog_remove_widget_message)) },
            confirmButton = {
                TextButton({
                    widgetToRemove = null
                    item.data.toIntOrNull()?.let { widgets.delete(it) }
                    edit { st -> st.copy(home = st.home.filter { it.id != item.id }) }
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton({ widgetToRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
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
    widgets: WidgetController,
    draggedHomeItemId: Int?,
    highlightedCell: Pair<Int, Int>?,
    onLongPressEmpty: (Offset) -> Unit,
    actionsFor: (HomeItem, AppInfo) -> List<MenuAction>,
    onLaunch: (AppInfo) -> Unit,
    onWidgetLong: (HomeItem) -> Unit,
    onStartItemDrag: (HomeItem, AppInfo, Offset) -> Unit,
    onDragItem: (Offset) -> Unit,
    onEndItemDrag: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { offset -> onLongPressEmpty(offset) })
        }
    ) {
        val cw = maxWidth / HOME_COLS
        val ch = maxHeight / HOME_ROWS

        // Indicador visual Material You de la celda destino durante el arrastre
        if (highlightedCell != null) {
            val (hCol, hRow) = highlightedCell
            Box(
                Modifier
                    .offset(cw * hCol, ch * hRow)
                    .size(cw, ch)
                    .padding(6.dp)
                    .clip(CellHighlightShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CellHighlightShape)
            )
        }

        items.filter { it.page == page }.forEach { item ->
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
                                showLabel = s.showLabels,
                                labelColor = Color.White,
                                iconSize = 56.dp,
                                actionsProvider = { actionsFor(item, app) },
                                onClick = { onLaunch(app) },
                                modifier = Modifier.fillMaxWidth(),
                                useHomeIcon = true,
                                enableDrag = true,
                                isBeingDragged = draggedHomeItemId == item.id,
                                onDragStart = { startCenter -> onStartItemDrag(item, app, startCenter) },
                                onDrag = onDragItem,
                                onDragEnd = onEndItemDrag,
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
