package dev.azagra.launcher_aosp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.data.AppInfo
import dev.azagra.launcher_aosp.data.DrawerBg
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.data.resolveIconSizeDp

private val DrawerTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SearchBarShape = RoundedCornerShape(28.dp)
private val DrawerCenterOrigin = TransformOrigin(0.5f, 0.35f)

/** Colores del fondo del cajón: sistema (Material You), blanco, negro u oscuro. */
@Composable
fun drawerColors(s: LauncherSettings): Triple<Color, Color, Color> {
    val cs = MaterialTheme.colorScheme
    val base = when (s.drawerBg) {
        DrawerBg.SYSTEM -> cs.surfaceContainerLow
        DrawerBg.WHITE -> Color.White
        DrawerBg.BLACK -> Color.Black
        DrawerBg.DARK -> Color(0xFF1B1B1F)
    }
    val on = when (s.drawerBg) {
        DrawerBg.SYSTEM -> cs.onSurface
        DrawerBg.WHITE -> Color.Black
        else -> Color.White
    }
    val searchBg = when (s.drawerBg) {
        DrawerBg.SYSTEM -> cs.surfaceContainerHighest
        else -> on.copy(alpha = 0.10f)
    }
    return Triple(base.copy(alpha = s.drawerAlpha), on, searchBg)
}

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    counts: Map<String, Int>,
    s: LauncherSettings,
    drawer: DrawerState,
    heightPx: Float,
    isHoldingOrDragging: Boolean,
    draggedDrawerAppKey: String?,
    onLaunch: (AppInfo) -> Unit,
    actionsFor: (AppInfo) -> List<MenuAction>,
    onItemHoldChange: (Boolean) -> Unit,
    onStartAppDrag: (AppInfo, Offset) -> Unit,
    onDragApp: (Offset) -> Unit,
    onEndAppDrag: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val (bg, on, searchBg) = drawerColors(s)
    val searchIconTint = if (s.drawerBg == DrawerBg.SYSTEM) MaterialTheme.colorScheme.primary else on
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    val isDraggingFromDrawer = draggedDrawerAppKey != null
    val currentHoldingOrDragging by rememberUpdatedState(isHoldingOrDragging)
    val dragSpanPx = remember(heightPx) { (heightPx * 0.68f).coerceAtLeast(1f) }

    // Solo reiniciamos la búsqueda y el scroll cuando el cajón está completamente cerrado y sin arrastre activo
    LaunchedEffect(drawer.isOpen, isDraggingFromDrawer) {
        if (!drawer.isOpen && !isDraggingFromDrawer) {
            focusManager.clearFocus()
            if (query.isNotEmpty()) query = ""
            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                gridState.scrollToItem(0)
            }
        }
    }

    val filtered = remember(apps, query, s.hiddenApps) {
        val visibleApps = if (s.hiddenApps.isEmpty()) apps else apps.filter { it.pkg !in s.hiddenApps && it.key !in s.hiddenApps }
        if (query.isBlank()) visibleApps else visibleApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    // Conexión de scroll anidado suave para deslizar el cajón al llegar al tope superior de la cuadrícula
    val connection = remember(drawer, dragSpanPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (!currentHoldingOrDragging && source == NestedScrollSource.UserInput && available.y < 0 && drawer.progress < 1f) {
                    drawer.drag(-available.y / dragSpanPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (!currentHoldingOrDragging && source == NestedScrollSource.UserInput && available.y > 0) {
                    focusManager.clearFocus()
                    drawer.drag(-available.y / dragSpanPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (!currentHoldingOrDragging && drawer.progress in 0.001f..0.999f) {
                    drawer.settle(-available.y / dragSpanPx)
                    available
                } else Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (!currentHoldingOrDragging && drawer.progress < 1f && available.y > 0) {
                    drawer.settle(-available.y / dragSpanPx)
                    available
                } else Velocity.Zero
        }
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridPadding = remember(navBottomPadding) {
        PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = navBottomPadding + 20.dp)
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = drawer.progress.coerceIn(0f, 1f)
                val eased = 1f - (1f - p) * (1f - p)
                // Durante el arrastre de una app fuera del cajón, mantenemos translationY = 0f y scale = 1f
                // para que las coordenadas del puntero no se desplacen mientras el cajón se desvanece con suavidad
                if (isDraggingFromDrawer) {
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    alpha = (p * 1.15f).coerceIn(0f, 1f)
                } else {
                    translationY = if (p <= 0.001f) size.height else (1f - eased) * size.height * 0.58f
                    val sScale = 0.94f + 0.06f * eased
                    scaleX = sScale
                    scaleY = sScale
                    transformOrigin = DrawerCenterOrigin
                    alpha = (p * 1.35f).coerceIn(0f, 1f)
                }
                shape = DrawerTopShape
                clip = true
            }
            .background(bg)
            .nestedScroll(connection)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Tirador superior estilo Material You
        Box(
            Modifier
                .padding(top = 10.dp, bottom = 2.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(on.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps), color = on.copy(alpha = 0.65f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = searchIconTint) },
            shape = SearchBarShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = searchBg,
                unfocusedContainerColor = searchBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = on,
                unfocusedTextColor = on,
                cursorColor = searchIconTint,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .draggable(
                    enabled = !isHoldingOrDragging,
                    state = rememberDraggableState { dy -> drawer.drag(-dy / dragSpanPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / dragSpanPx) },
                ),
        )
        val drawerIconSize = remember(s.columns, s.drawerIconScale) {
            resolveIconSizeDp(s.columns, s.drawerIconScale).dp
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(s.columns),
            userScrollEnabled = !isHoldingOrDragging,
            modifier = Modifier.weight(1f),
            contentPadding = gridPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = filtered,
                key = { it.key },
                contentType = { "drawer_app" },
            ) { app ->
                AppIcon(
                    app = app,
                    badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                    showLabel = s.showLabels,
                    labelColor = on,
                    iconSize = drawerIconSize,
                    actionsProvider = { actionsFor(app) },
                    onClick = { onLaunch(app) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ),
                    useHomeIcon = false,
                    iconShape = s.iconShape,
                    enableDrag = !s.lockDesktop,
                    isBeingDragged = draggedDrawerAppKey == app.key,
                    onPressHoldChange = onItemHoldChange,
                    onDragStart = { startRootPos ->
                        focusManager.clearFocus()
                        onStartAppDrag(app, startRootPos)
                    },
                    onDrag = onDragApp,
                    onDragEnd = onEndAppDrag,
                )
            }
        }
    }
}
