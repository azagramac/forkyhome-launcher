package dev.azagra.forkyhome.ui

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.DrawerBg
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.resolveIconSizeDp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val DrawerTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SearchBarShape = RoundedCornerShape(28.dp)
private val DrawerCenterOrigin = TransformOrigin(0.5f, 0.35f)

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

    val view = LocalView.current
    val drawerOpen by remember(drawer) { derivedStateOf { drawer.isOpen } }
    var wasDrawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen && !wasDrawerOpen) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        wasDrawerOpen = drawerOpen
    }
    LaunchedEffect(drawerOpen, isDraggingFromDrawer) {
        if (!drawerOpen && !isDraggingFromDrawer) {
            focusManager.clearFocus()
            if (query.isNotEmpty()) query = ""
            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                gridState.scrollToItem(0)
            }
        }
    }

    val filtered = remember(apps, query, s.hiddenApps) {
        filterDrawerApps(apps, query, s.hiddenApps)
    }

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

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navBottomPadding = with(density) { navBottomPx.toDp() }
    val gridPadding = remember(navBottomPadding) {
        PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = navBottomPadding + 20.dp)
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = drawer.progress.coerceIn(0f, 1f)
                val eased = 1f - (1f - p) * (1f - p)
                if (isDraggingFromDrawer) {
                    translationY = 0f
                    alpha = (p * 1.15f).coerceIn(0f, 1f)
                } else if (p <= 0.001f) {
                    translationY = size.height
                    alpha = 0f
                } else {
                    translationY = (1f - eased) * size.height
                    alpha = 1f
                }
            }
            .background(bg)
            .nestedScroll(connection)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(
            Modifier
                .padding(top = 10.dp, bottom = 2.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(on.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )
        val searchPlaceholder = stringResource(R.string.search_apps)
        val searchTextStyle = MaterialTheme.typography.bodyLarge.copy(color = on)
        val placeholderStyle = MaterialTheme.typography.bodyLarge.copy(color = on.copy(alpha = 0.65f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(52.dp)
                .clip(SearchBarShape)
                .background(searchBg)
                .draggable(
                    enabled = !isHoldingOrDragging,
                    state = rememberDraggableState { dy -> drawer.drag(-dy / dragSpanPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / dragSpanPx) },
                )
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Search, null, tint = searchIconTint)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    BasicText(
                        text = searchPlaceholder,
                        style = placeholderStyle,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = searchTextStyle,
                    cursorBrush = SolidColor(searchIconTint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val drawerIconSize = remember(s.columns, s.drawerIconScale) {
            resolveIconSizeDp(s.columns, s.drawerIconScale).dp
        }
        val drawerLabelStyle = MaterialTheme.typography.labelSmall
        val resolvedLabelStyle = remember(drawerLabelStyle, on) { drawerLabelStyle.copy(color = on) }
        val badgeBg = MaterialTheme.colorScheme.primary
        val badgeFg = MaterialTheme.colorScheme.onPrimary

        var pressedKey by remember { mutableStateOf<String?>(null) }
        var longPressedKey by remember { mutableStateOf<String?>(null) }
        var menuTarget by remember { mutableStateOf<Pair<AppInfo, DpOffset>?>(null) }
        var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

        val currentFiltered by rememberUpdatedState(filtered)
        val currentEnableDrag by rememberUpdatedState(!s.lockDesktop)
        val currentOnLaunch by rememberUpdatedState(onLaunch)
        val currentOnItemHoldChange by rememberUpdatedState(onItemHoldChange)
        val currentOnStartAppDrag by rememberUpdatedState(onStartAppDrag)
        val currentOnDragApp by rememberUpdatedState(onDragApp)
        val currentOnEndAppDrag by rememberUpdatedState(onEndAppDrag)
        val currentDraggedKey by rememberUpdatedState(draggedDrawerAppKey)

        val pressedKeyReader = remember { { pressedKey } }
        val longPressedKeyReader = remember { { longPressedKey } }
        val draggedKeyReader = remember { { currentDraggedKey } }

        var isFastScrolling by remember { mutableStateOf(false) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(s.columns),
                userScrollEnabled = !isHoldingOrDragging && !isFastScrolling,
                modifier = Modifier
                    .fillMaxSize()
                    .onPlaced { gridCoords = it }
                    .pointerInput(gridState) {
                        val dragThresholdPx = 4.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                val ox = info.offset.x.toFloat()
                                val oy = info.offset.y.toFloat()
                                down.position.x >= ox && down.position.x <= ox + info.size.width &&
                                    down.position.y >= oy && down.position.y <= oy + info.size.height
                            } ?: return@awaitEachGesture
                            val app = currentFiltered.getOrNull(hit.index) ?: return@awaitEachGesture
                            pressedKey = app.key
                            val longPress = try {
                                awaitLongPressOrCancellation(down.id)
                            } finally {
                                pressedKey = null
                            }
                            if (longPress == null) {
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (up != null && !up.pressed && !up.isConsumed &&
                                    (up.position - down.position).getDistance() <= viewConfiguration.touchSlop
                                ) {
                                    up.consume()
                                    currentOnLaunch(app)
                                }
                            } else {
                                longPress.consume()
                                currentEvent.changes.forEach { it.consume() }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!currentEnableDrag) {
                                    val menuOff = with(density) {
                                        DpOffset(hit.offset.x.toDp(), (hit.offset.y + hit.size.height / 2).toDp())
                                    }
                                    menuTarget = app to menuOff
                                } else {
                                    longPressedKey = app.key
                                    currentOnItemHoldChange(true)
                                    var dragging = false
                                    val startPos = longPress.position
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            if (!dragging && (change.position - startPos).getDistance() > dragThresholdPx) {
                                                dragging = true
                                                longPressedKey = null
                                                focusManager.clearFocus()
                                                val c = gridCoords
                                                val rootPos = if (c != null && c.isAttached) c.localToRoot(change.position) else change.position
                                                currentOnStartAppDrag(app, rootPos)
                                                currentOnDragApp(rootPos)
                                            } else if (dragging) {
                                                val c = gridCoords
                                                val rootPos = if (c != null && c.isAttached) c.localToRoot(change.position) else change.position
                                                currentOnDragApp(rootPos)
                                            }
                                            change.consume()
                                        }
                                    } finally {
                                        longPressedKey = null
                                        currentOnItemHoldChange(false)
                                    }
                                    if (dragging) {
                                        currentOnEndAppDrag()
                                    } else {
                                        val menuOff = with(density) {
                                            DpOffset(hit.offset.x.toDp(), (hit.offset.y + hit.size.height / 2).toDp())
                                        }
                                        menuTarget = app to menuOff
                                    }
                                }
                            }
                        }
                    },
                contentPadding = gridPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = filtered,
                    key = { it.key },
                    contentType = { "drawer_app" },
                ) { app ->
                    DrawerAppIcon(
                        app = app,
                        badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                        showLabel = s.showLabels,
                        iconSize = drawerIconSize,
                        resolvedLabelStyle = resolvedLabelStyle,
                        badgeContainerColor = badgeBg,
                        badgeContentColor = badgeFg,
                        pressedKey = pressedKeyReader,
                        longPressedKey = longPressedKeyReader,
                        draggedKey = draggedKeyReader,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TrebuchetFastScroller(
                gridState = gridState,
                apps = filtered,
                columns = s.columns,
                trackColor = on.copy(alpha = 30f / 255f),
                bottomPadding = navBottomPadding + 20.dp,
                isDragging = isFastScrolling,
                onDraggingChange = { dragging ->
                    isFastScrolling = dragging
                    if (dragging) focusManager.clearFocus()
                },
                modifier = Modifier.matchParentSize(),
            )
            menuTarget?.let { (menuApp, menuOffset) ->
                val actionsProvider = remember(menuApp.key) { { actionsFor(menuApp) } }
                AppActionsMenu(
                    appKey = menuApp.key,
                    actionsProvider = actionsProvider,
                    onDismiss = { menuTarget = null },
                    offset = menuOffset,
                )
            }
        }
    }
}

@Composable
private fun TrebuchetFastScroller(
    gridState: LazyGridState,
    apps: List<AppInfo>,
    columns: Int,
    trackColor: Color,
    bottomPadding: Dp,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canScroll by remember(gridState) {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }
    if (apps.isEmpty() || !canScroll) return

    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thumbColor = MaterialTheme.colorScheme.primary
    val popupTextColor = MaterialTheme.colorScheme.onPrimary

    val trackMinWidth = 6.dp
    val trackMaxWidth = 8.dp
    val thumbPadding = 1.dp
    val thumbHeight = 52.dp
    val popupWidth = 75.dp
    val popupHeight = 62.dp
    val topPadding = 8.dp

    val animatedTrackWidth by animateDpAsState(
        targetValue = if (isDragging) trackMaxWidth else trackMinWidth,
        animationSpec = tween(durationMillis = 150),
        label = "fastScrollTrackWidth",
    )
    val popupAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = if (isDragging) 200 else 150),
        label = "fastScrollPopupAlpha",
    )

    var dragProgress by remember { mutableFloatStateOf(0f) }
    var currentLetter by remember { mutableStateOf("") }

    val scrollProgress by remember(gridState, apps.size, columns) {
        derivedStateOf {
            val rowCount = ((apps.size + columns - 1) / columns).coerceAtLeast(1)
            val firstRow = gridState.firstVisibleItemIndex / columns
            val firstItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            val rowHeightPx = firstItem?.size?.height?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val extraPaddingPx = gridState.layoutInfo.beforeContentPadding + gridState.layoutInfo.afterContentPadding
            val totalContentPx = rowCount * rowHeightPx + extraPaddingPx
            val viewportPx = gridState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
            val maxScrollPx = (totalContentPx - viewportPx).coerceAtLeast(1f)
            val currentScrollPx = firstRow * rowHeightPx + gridState.firstVisibleItemScrollOffset
            (currentScrollPx / maxScrollPx).coerceIn(0f, 1f)
        }
    }

    val effectiveProgress = if (isDragging) dragProgress else scrollProgress

    val currentApps by rememberUpdatedState(apps)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)

    BoxWithConstraints(modifier = modifier) {
        val trackTopPx = with(density) { topPadding.toPx() }
        val trackBottomPx = with(density) { bottomPadding.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val popupHeightPx = with(density) { popupHeight.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val trackHeightPx = (containerHeightPx - trackTopPx - trackBottomPx).coerceAtLeast(thumbHeightPx)
        val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetY = trackTopPx + effectiveProgress * travelPx

        fun scrollToFraction(fraction: Float) {
            val clamped = fraction.coerceIn(0f, 1f)
            dragProgress = clamped
            val list = currentApps
            if (list.isEmpty()) return
            val cols = currentColumns.coerceAtLeast(1)
            val rowCount = ((list.size + cols - 1) / cols).coerceAtLeast(1)
            val firstItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            val rowHeightPx = firstItem?.size?.height?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val extraPaddingPx = gridState.layoutInfo.beforeContentPadding + gridState.layoutInfo.afterContentPadding
            val viewportPx = gridState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
            val maxScrollPx = (rowCount * rowHeightPx + extraPaddingPx - viewportPx).coerceAtLeast(0f)
            val targetScrollPx = clamped * maxScrollPx
            val targetRow = (targetScrollPx / rowHeightPx).toInt().coerceIn(0, rowCount - 1)
            val targetRowOffset = (targetScrollPx - targetRow * rowHeightPx).roundToInt().coerceAtLeast(0)
            val targetIndex = (targetRow * cols).coerceIn(0, list.lastIndex)

            val letterIndex = (clamped * list.lastIndex).roundToInt().coerceIn(0, list.lastIndex)
            val ch = list[letterIndex].label.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val nextLetter = if (ch.isLetter()) ch.toString() else "#"
            if (nextLetter != currentLetter) {
                currentLetter = nextLetter
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            scope.launch {
                gridState.scrollToItem(targetIndex, targetRowOffset)
            }
        }

        if (popupAlpha > 0.01f && currentLetter.isNotEmpty()) {
            val popupCenterOffset = (thumbOffsetY + thumbHeightPx * 0.25f - popupHeightPx * 0.5f)
                .coerceIn(0f, (containerHeightPx - popupHeightPx).coerceAtLeast(0f))
            val teardropPath = remember { Path() }
            val teardropMatrix = remember { Matrix() }
            val teardropPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = 0, y = popupCenterOffset.roundToInt()) }
                    .padding(end = 36.dp)
                    .size(width = popupWidth, height = popupHeight)
                    .graphicsLayer { alpha = popupAlpha },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.height * 0.5f
                    val diameter = 2f * r
                    val r2 = r / 5f
                    teardropPath.reset()
                    teardropPath.addRoundRect(
                        0f,
                        0f,
                        diameter,
                        diameter,
                        floatArrayOf(r, r, r, r, r2, r2, r, r),
                        Path.Direction.CCW,
                    )
                    teardropMatrix.reset()
                    teardropMatrix.setRotate(-45f, r, r)
                    if (isRtl) {
                        teardropMatrix.postTranslate(size.width, 0f)
                        teardropMatrix.postScale(-1f, 1f, size.width, 0f)
                    }
                    teardropPath.transform(teardropMatrix)
                    teardropPaint.color = thumbColor.toArgb()
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(teardropPath, teardropPaint)
                    }
                }
                Text(
                    text = currentLetter,
                    color = popupTextColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 13.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(34.dp)
                .pointerInput(trackTopPx, travelPx, thumbHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnDraggingChange(true)
                        scrollToFraction((down.position.y - trackTopPx - thumbHeightPx / 2f) / travelPx)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                scrollToFraction((change.position.y - trackTopPx - thumbHeightPx / 2f) / travelPx)
                            }
                        } finally {
                            currentOnDraggingChange(false)
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPadding, bottom = bottomPadding, end = 6.dp)
                    .fillMaxHeight()
                    .width(animatedTrackWidth)
                    .clip(CircleShape)
                    .background(trackColor),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = 0, y = thumbOffsetY.roundToInt()) }
                    .padding(end = 5.dp)
                    .width(animatedTrackWidth + thumbPadding * 2)
                    .height(thumbHeight)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

internal fun filterDrawerApps(
    apps: List<AppInfo>,
    query: String,
    hiddenApps: Set<String>,
): List<AppInfo> {
    val visibleApps = if (hiddenApps.isEmpty()) {
        apps
    } else {
        apps.filter { it.pkg !in hiddenApps && it.key !in hiddenApps }
    }
    val trimmed = query.trim()
    return if (trimmed.isEmpty()) {
        visibleApps
    } else {
        visibleApps.filter { it.label.contains(trimmed, ignoreCase = true) }
    }
}

internal fun buildAlphabetIndexMap(labels: List<String>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    labels.forEachIndexed { index, label ->
        val first = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
        val key = if (first in 'A'..'Z') first else '#'
        map.putIfAbsent(key, index)
    }
    return map
}
