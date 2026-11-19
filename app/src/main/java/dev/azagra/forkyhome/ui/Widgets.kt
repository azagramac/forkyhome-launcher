package dev.azagra.forkyhome.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SizeF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.HomeItem
import dev.azagra.forkyhome.widgets.LongPressHostView
import dev.azagra.forkyhome.widgets.WidgetController
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WidgetSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val WidgetItemShape = RoundedCornerShape(20.dp)
private val WidgetFrameShape = RoundedCornerShape(24.dp)

@Composable
fun WidgetView(
    id: Int,
    controller: WidgetController,
    isBeingDragged: Boolean = false,
    onPressHoldChange: (Boolean) -> Unit = {},
    onActivateResizeFrame: () -> Unit = {},
    onStartDrag: (startRootPos: Offset, touchOffsetInWidget: Offset) -> Unit = { _, _ -> },
    onDrag: (currentRootPos: Offset) -> Unit = {},
    onEndDrag: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dragStartRootPos by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var startedDragging by remember { mutableStateOf(false) }

    val currentOnPressHoldChange by rememberUpdatedState(onPressHoldChange)
    val currentOnActivateResizeFrame by rememberUpdatedState(onActivateResizeFrame)
    val currentOnStartDrag by rememberUpdatedState(onStartDrag)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEndDrag by rememberUpdatedState(onEndDrag)

    val animatedScale by animateFloatAsState(
        targetValue = if (isBeingDragged) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "widgetScale",
    )

    BoxWithConstraints(
        modifier = modifier
            .onGloballyPositioned { boundsInRoot = it.boundsInRoot() }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isBeingDragged) 0.35f else 1f
            }
            .clip(WidgetFrameShape),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                controller.getOrCreateView(ctx, id)
            },
            update = { v ->
                v.onLongPressStart = { touchOffset, _ ->
                    startedDragging = false
                    dragTouchOffset = touchOffset
                    dragStartRootPos = Offset(
                        x = boundsInRoot.left + touchOffset.x,
                        y = boundsInRoot.top + touchOffset.y,
                    )
                    currentOnPressHoldChange(true)
                    currentOnActivateResizeFrame()
                }
                v.onDragMove = { rawDelta ->
                    val currentRootPos = dragStartRootPos + rawDelta
                    if (!startedDragging) {
                        startedDragging = true
                        currentOnStartDrag(currentRootPos, dragTouchOffset)
                    } else {
                        currentOnDrag(currentRootPos)
                    }
                }
                v.onDragFinish = {
                    if (startedDragging) {
                        startedDragging = false
                        currentOnEndDrag()
                    }
                    currentOnPressHoldChange(false)
                }
                v.onLongPressRelease = {
                    startedDragging = false
                    currentOnPressHoldChange(false)
                }
                val sizeKey = w to h
                if (v.lastReportedSize != sizeKey) {
                    v.lastReportedSize = sizeKey
                    v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                }
            },
        )
    }
}

@Composable
fun TrebuchetWidgetResizeFrame(
    item: HomeItem,
    cw: Dp,
    ch: Dp,
    homeCols: Int,
    homeRows: Int,
    gridOriginInRoot: Offset,
    canReconfigure: Boolean,
    onReconfigure: () -> Unit,
    onRemove: () -> Unit,
    onResize: (newCol: Int, newRow: Int, newSpanX: Int, newSpanY: Int) -> Boolean,
    onPressHoldChange: (Boolean) -> Unit,
    onStartMoveDrag: (startRootPos: Offset, touchOffsetInWidget: Offset) -> Unit,
    onMoveDrag: (currentRootPos: Offset) -> Unit,
    onEndMoveDrag: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val cwPx = with(density) { cw.toPx() }.coerceAtLeast(1f)
    val chPx = with(density) { ch.toPx() }.coerceAtLeast(1f)
    val handleHitRadiusPx = with(density) { 28.dp.toPx() }
    val dragSlopPx = with(density) { 8.dp.toPx() }

    val latestItem by rememberUpdatedState(item)
    val latestGridOrigin by rememberUpdatedState(gridOriginInRoot)
    val latestOnResize by rememberUpdatedState(onResize)
    val latestOnPressHoldChange by rememberUpdatedState(onPressHoldChange)
    val latestOnStartMoveDrag by rememberUpdatedState(onStartMoveDrag)
    val latestOnMoveDrag by rememberUpdatedState(onMoveDrag)
    val latestOnEndMoveDrag by rememberUpdatedState(onEndMoveDrag)
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    var leftActive by remember { mutableStateOf(false) }
    var rightActive by remember { mutableStateOf(false) }
    var topActive by remember { mutableStateOf(false) }
    var bottomActive by remember { mutableStateOf(false) }
    var visualLeftPx by remember(item.col, item.spanX, cwPx) { mutableFloatStateOf(item.col * cwPx) }
    var visualTopPx by remember(item.row, item.spanY, chPx) { mutableFloatStateOf(item.row * chPx) }
    var visualRightPx by remember(item.col, item.spanX, cwPx) { mutableFloatStateOf((item.col + item.spanX) * cwPx) }
    var visualBottomPx by remember(item.row, item.spanY, chPx) { mutableFloatStateOf((item.row + item.spanY) * chPx) }
    var isResizingGesture by remember { mutableStateOf(false) }

    val frameLeftPx = if (isResizingGesture) visualLeftPx else item.col * cwPx
    val frameTopPx = if (isResizingGesture) visualTopPx else item.row * chPx
    val frameRightPx = if (isResizingGesture) visualRightPx else (item.col + item.spanX) * cwPx
    val frameBottomPx = if (isResizingGesture) visualBottomPx else (item.row + item.spanY) * chPx
    val frameWidthDp = with(density) { (frameRightPx - frameLeftPx).coerceAtLeast(cwPx * 0.6f).toDp() }
    val frameHeightDp = with(density) { (frameBottomPx - frameTopPx).coerceAtLeast(chPx * 0.6f).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id, homeCols, homeRows, cwPx, chPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position

                    val curItem = latestItem
                    val curLeft = curItem.col * cwPx
                    val curRight = (curItem.col + curItem.spanX) * cwPx
                    val curTop = curItem.row * chPx
                    val curBottom = (curItem.row + curItem.spanY) * chPx

                    val inExpandedBounds =
                        downPos.x in (curLeft - handleHitRadiusPx)..(curRight + handleHitRadiusPx) &&
                            downPos.y in (curTop - handleHitRadiusPx)..(curBottom + handleHitRadiusPx)

                    if (!inExpandedBounds) {
                        latestOnDismiss()
                        return@awaitEachGesture
                    }

                    val hitLeft = abs(downPos.x - curLeft) <= handleHitRadiusPx
                    val hitRight = !hitLeft && abs(downPos.x - curRight) <= handleHitRadiusPx
                    val hitTop = abs(downPos.y - curTop) <= handleHitRadiusPx
                    val hitBottom = !hitTop && abs(downPos.y - curBottom) <= handleHitRadiusPx
                    val anyBorder = hitLeft || hitRight || hitTop || hitBottom

                    down.consume()
                    latestOnPressHoldChange(true)

                    if (anyBorder) {
                        leftActive = hitLeft
                        rightActive = hitRight
                        topActive = hitTop
                        bottomActive = hitBottom
                        isResizingGesture = true

                        val baseLeft = curLeft
                        val baseRight = curRight
                        val baseTop = curTop
                        val baseBottom = curBottom
                        val origCol = curItem.col
                        val origRow = curItem.row
                        val origRightCol = curItem.col + curItem.spanX
                        val origBottomRow = curItem.row + curItem.spanY

                        visualLeftPx = baseLeft
                        visualRightPx = baseRight
                        visualTopPx = baseTop
                        visualBottomPx = baseBottom

                        var totalDx = 0f
                        var totalDy = 0f

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) break

                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    change.consume()
                                    totalDx += delta.x
                                    totalDy += delta.y

                                    if (hitLeft) {
                                        visualLeftPx = (baseLeft + totalDx).coerceIn(0f, baseRight - cwPx * 0.7f)
                                    } else if (hitRight) {
                                        visualRightPx = (baseRight + totalDx).coerceIn(baseLeft + cwPx * 0.7f, homeCols * cwPx)
                                    }
                                    if (hitTop) {
                                        visualTopPx = (baseTop + totalDy).coerceIn(0f, baseBottom - chPx * 0.7f)
                                    } else if (hitBottom) {
                                        visualBottomPx = (baseBottom + totalDy).coerceIn(baseTop + chPx * 0.7f, homeRows * chPx)
                                    }

                                    val newCol = if (hitLeft) {
                                        (visualLeftPx / cwPx).roundToInt().coerceIn(0, origRightCol - 1)
                                    } else latestItem.col

                                    val newRightCol = if (hitRight) {
                                        (visualRightPx / cwPx).roundToInt().coerceIn(origCol + 1, homeCols)
                                    } else if (hitLeft) {
                                        origRightCol
                                    } else {
                                        latestItem.col + latestItem.spanX
                                    }

                                    val newRow = if (hitTop) {
                                        (visualTopPx / chPx).roundToInt().coerceIn(0, origBottomRow - 1)
                                    } else latestItem.row

                                    val newBottomRow = if (hitBottom) {
                                        (visualBottomPx / chPx).roundToInt().coerceIn(origRow + 1, homeRows)
                                    } else if (hitTop) {
                                        origBottomRow
                                    } else {
                                        latestItem.row + latestItem.spanY
                                    }

                                    val newSpanX = (newRightCol - newCol).coerceIn(1, homeCols - newCol)
                                    val newSpanY = (newBottomRow - newRow).coerceIn(1, homeRows - newRow)

                                    val live = latestItem
                                    if (newCol != live.col || newRow != live.row || newSpanX != live.spanX || newSpanY != live.spanY) {
                                        val resized = latestOnResize(newCol, newRow, newSpanX, newSpanY)
                                        if (resized) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            }
                        } finally {
                            isResizingGesture = false
                            leftActive = false
                            rightActive = false
                            topActive = false
                            bottomActive = false
                            latestOnPressHoldChange(false)
                        }
                    } else {
                        val touchOffsetInWidget = Offset(downPos.x - curLeft, downPos.y - curTop)
                        var totalMove = Offset.Zero
                        var moveStarted = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) break

                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    change.consume()
                                    totalMove += delta
                                    val rootPos = latestGridOrigin + change.position
                                    if (!moveStarted && hypot(totalMove.x, totalMove.y) > dragSlopPx) {
                                        moveStarted = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestOnStartMoveDrag(rootPos, touchOffsetInWidget)
                                    } else if (moveStarted) {
                                        latestOnMoveDrag(rootPos)
                                    }
                                }
                            }
                        } finally {
                            latestOnPressHoldChange(false)
                            if (moveStarted) {
                                latestOnEndMoveDrag()
                            } else {
                                latestOnDismiss()
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(frameLeftPx.roundToInt(), frameTopPx.roundToInt()) }
                .size(frameWidthDp, frameHeightDp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(WidgetFrameShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(2.5.dp, MaterialTheme.colorScheme.primary, WidgetFrameShape),
            )

            ResizeHandlePill(
                isVertical = true,
                isActive = !isResizingGesture || leftActive,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            ResizeHandlePill(
                isVertical = true,
                isActive = !isResizingGesture || rightActive,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            ResizeHandlePill(
                isVertical = false,
                isActive = !isResizingGesture || topActive,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            ResizeHandlePill(
                isVertical = false,
                isActive = !isResizingGesture || bottomActive,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        val toolbarHeightPx = with(density) { 48.dp.toPx() }
        val toolbarY = if (frameTopPx >= toolbarHeightPx + 8f) {
            (frameTopPx - toolbarHeightPx - 6f).roundToInt()
        } else {
            (frameTopPx + 10f).roundToInt()
        }
        val toolbarCenterX = ((frameLeftPx + frameRightPx) / 2f).roundToInt()

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (toolbarCenterX - with(density) { 84.dp.toPx() }.roundToInt()).coerceAtLeast(8),
                        y = toolbarY,
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${item.spanX} × ${item.spanY}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                if (canReconfigure) {
                    IconButton(
                        onClick = onReconfigure,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_launcher_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_save),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizeHandlePill(
    isVertical: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (isActive) 1f else 0.25f, label = "handleAlpha")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .let {
                if (isVertical) it.width(10.dp).height(34.dp)
                else it.width(34.dp).height(10.dp)
            },
    ) {}
}

private data class WidgetAppGroup(
    val pkg: String,
    val appLabel: String,
    val widgets: List<Pair<AppWidgetProviderInfo, String>>,
)

private fun Drawable.toAspectFitBitmap(maxSidePx: Int = 280): ImageBitmap {
    val iw = intrinsicWidth
    val ih = intrinsicHeight
    if (iw <= 0 || ih <= 0) {
        return toBitmap(128, 128).asImageBitmap()
    }
    val scale = (maxSidePx.toFloat() / maxOf(iw, ih)).coerceAtMost(2f)
    val w = (iw * scale).roundToInt().coerceAtLeast(1)
    val h = (ih * scale).roundToInt().coerceAtLeast(1)
    return toBitmap(w, h).asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPicker(
    controller: WidgetController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val groups by produceState<List<WidgetAppGroup>>(emptyList(), controller) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            controller.manager.installedProviders
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val appLabel = runCatching {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai).toString()
                    }.getOrDefault(pkg)
                    val sortedWidgets = list
                        .map { it to it.loadLabel(pm) }
                        .sortedBy { it.second.lowercase() }
                    WidgetAppGroup(pkg, appLabel, sortedWidgets)
                }
                .sortedBy { it.appLabel.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    val filteredGroups = remember(groups, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { g ->
                if (g.appLabel.contains(q, ignoreCase = true)) {
                    g
                } else {
                    val matching = g.widgets.filter { it.second.contains(q, ignoreCase = true) }
                    if (matching.isNotEmpty()) g.copy(widgets = matching) else null
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = WidgetSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = stringResource(R.string.menu_widgets),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filteredGroups, key = { it.pkg }) { group ->
                val isExpanded = query.isNotBlank() || expandedPkg == group.pkg
                val appIcon by produceState<ImageBitmap?>(null, group.pkg) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.packageManager.getApplicationIcon(group.pkg).toBitmap(96, 96).asImageBitmap()
                        }.getOrNull()
                    }
                }
                Surface(
                    shape = WidgetItemShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clip(WidgetItemShape),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedPkg = if (expandedPkg == group.pkg) null else group.pkg
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            appIcon?.let {
                                Image(it, contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = group.appLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${group.widgets.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.widgets.forEach { (info, label) ->
                                    val densityDpi = ctx.resources.displayMetrics.densityDpi
                                    val preview by produceState<ImageBitmap?>(null, info.provider) {
                                        value = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val dr = info.loadPreviewImage(ctx, densityDpi)
                                                    ?: info.loadIcon(ctx, densityDpi)
                                                    ?: ctx.packageManager.getApplicationIcon(group.pkg)
                                                dr.toAspectFitBitmap(280)
                                            }.getOrNull()
                                        }
                                    }
                                    val spanX = if (info.targetCellWidth > 0) info.targetCellWidth else ((info.minWidth / ctx.resources.displayMetrics.density + 30) / 70).toInt().coerceAtLeast(1)
                                    val spanY = if (info.targetCellHeight > 0) info.targetCellHeight else ((info.minHeight / ctx.resources.displayMetrics.density + 30) / 70).toInt().coerceAtLeast(1)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onPick(info) }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            )
                                            Text(
                                                text = "$spanX × $spanY",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        preview?.let { bmp ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 56.dp, max = 140.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = label,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 132.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
