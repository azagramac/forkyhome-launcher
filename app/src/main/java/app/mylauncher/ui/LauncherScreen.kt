package app.mylauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mylauncher.Container
import app.mylauncher.data.AppInfo
import app.mylauncher.data.DOCK_MAX
import app.mylauncher.data.HOME_COLS
import app.mylauncher.data.HOME_ROWS
import app.mylauncher.data.HomeItem
import app.mylauncher.data.IconConfig
import app.mylauncher.data.ItemKind
import app.mylauncher.data.LauncherSettings
import app.mylauncher.notif.NotificationCounter
import app.mylauncher.widgets.WidgetController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(s: LauncherSettings, widgets: WidgetController, container: Container) {
    val ctx = LocalContext.current
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
    var showWidgets by remember { mutableStateOf(false) }
    var widgetToRemove by remember { mutableStateOf<HomeItem?>(null) }

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
        MenuAction("Información") { details(app.pkg) },
        MenuAction("Desinstalar") { uninstall(app.pkg) },
    )
    fun drawerActions(app: AppInfo): List<MenuAction> = buildList {
        add(MenuAction("Añadir al escritorio") {
            scope.launch {
                if (store.addHomeItem(ItemKind.APP, app.key, 1, 1, pagerState.currentPage)) toast("Añadido al escritorio")
                else toast("No hay sitio en el escritorio")
            }
        })
        if (app.key !in s.dock && s.dock.size < DOCK_MAX) {
            add(MenuAction("Añadir al dock") { edit { it.copy(dock = (it.dock + app.key).take(DOCK_MAX)) } })
        }
        addAll(common(app))
    }
    fun homeActions(item: HomeItem, app: AppInfo) = listOf(
        MenuAction("Quitar del escritorio") { edit { st -> st.copy(home = st.home.filter { it.id != item.id }) } },
    ) + common(app)
    fun dockActions(app: AppInfo) = listOf(
        MenuAction("Quitar del dock") { edit { st -> st.copy(dock = st.dock.filter { it != app.key }) } },
    ) + common(app)

    // ---- UI ----
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }

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
                    state = rememberDraggableState { dy -> drawer.drag(-dy / hPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / hPx) },
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                HomePage(
                    page = page,
                    items = s.home,
                    appMap = appMap,
                    counts = counts,
                    s = s,
                    widgets = widgets,
                    onLongPressEmpty = { showMenu = true },
                    actionsFor = ::homeActions,
                    onLaunch = ::openApp,
                    onWidgetLong = { widgetToRemove = it },
                )
            }
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(s.homePages) { i ->
                    val sel = i == pagerState.currentPage
                    Box(
                        Modifier.size(if (sel) 8.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (sel) 0.95f else 0.45f))
                    )
                }
            }
            // Dock (máx. 5)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .heightIn(min = 76.dp)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                s.dock.mapNotNull { appMap[it] }.take(DOCK_MAX).forEach { app ->
                    AppIcon(
                        app = app,
                        badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                        showLabel = false,
                        labelColor = Color.Unspecified,
                        iconSize = 52.dp,
                        actions = dockActions(app),
                        onClick = { openApp(app) },
                    )
                }
            }
        }

        if (drawerVisible) {
            AppDrawer(
                apps = apps,
                counts = counts,
                s = s,
                drawer = drawer,
                heightPx = hPx,
                onLaunch = ::openApp,
                actionsFor = ::drawerActions,
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            val packs = remember { repo.packs.installed() }
            SettingsScreen(s, packs, onChange = { edit(it) }, onClose = { showSettings = false })
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }) {
            ListItem(
                headlineContent = { Text("Widgets") },
                modifier = Modifier.clickable { showMenu = false; showWidgets = true },
            )
            ListItem(
                headlineContent = { Text("Fondo de pantalla") },
                modifier = Modifier.clickable { showMenu = false; openWallpaperPicker(ctx) },
            )
            ListItem(
                headlineContent = { Text("Ajustes del launcher") },
                modifier = Modifier.clickable { showMenu = false; showSettings = true },
            )
            Spacer(Modifier.height(24.dp))
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
            title = { Text("Quitar widget") },
            text = { Text("¿Quitar este widget del escritorio?") },
            confirmButton = {
                TextButton({
                    widgetToRemove = null
                    item.data.toIntOrNull()?.let { widgets.delete(it) }
                    edit { st -> st.copy(home = st.home.filter { it.id != item.id }) }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton({ widgetToRemove = null }) { Text("Cancelar") } },
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
    onLongPressEmpty: () -> Unit,
    actionsFor: (HomeItem, AppInfo) -> List<MenuAction>,
    onLaunch: (AppInfo) -> Unit,
    onWidgetLong: (HomeItem) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { onLongPressEmpty() })
        }
    ) {
        val cw = maxWidth / HOME_COLS
        val ch = maxHeight / HOME_ROWS
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
                                actions = actionsFor(item, app),
                                onClick = { onLaunch(app) },
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
