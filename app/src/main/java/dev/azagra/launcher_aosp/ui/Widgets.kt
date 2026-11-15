package dev.azagra.launcher_aosp.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.data.HomeItem
import dev.azagra.launcher_aosp.widgets.LongPressHostView
import dev.azagra.launcher_aosp.widgets.WidgetController
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

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
                val info = controller.manager.getAppWidgetInfo(id)
                (controller.host.createView(ctx, id, info) as LongPressHostView).apply {
                    clipToPadding = false
                }
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
                    currentOnActivateResizeFrame()
                }
                v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            },
        )
    }
}

/**
 * Marco interactivo de redimensionado y movimiento de widgets estilo LineageOS Trebuchet
 * (`com.android.launcher3.AppWidgetResizeFrame` + `app_widget_resize_frame.xml`):
 * - 4 tiradores en los bordes (izquierda, arriba, derecha, abajo) y soporte de esquinas.
 * - Arrastrar cualquier borde redimensiona el widget celda a celda en tiempo real (`visualizeResizeForDelta`).
 * - Arrastrar desde el interior del marco mueve el widget por el escritorio o entre páginas.
 * - Incluye botón de reconfiguración (si el widget tiene actividad de configuración), eliminar y cerrar.
 */
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

    // Deltas visuales en píxeles mientras se arrastra un tirador (como mDeltaX / mDeltaY en AppWidgetResizeFrame)
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

                    // Comprobar si el toque cae sobre alguno de los 4 bordes/tiradores (igual que beginResizeIfPointInRegion en Trebuchet)
                    val inExpandedBounds =
                        downPos.x in (curLeft - handleHitRadiusPx)..(curRight + handleHitRadiusPx) &&
                            downPos.y in (curTop - handleHitRadiusPx)..(curBottom + handleHitRadiusPx)

                    if (!inExpandedBounds) {
                        // Toque fuera del widget: cerrar el marco de redimensionado
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
                        // Modo Redimensionar (AppWidgetResizeFrame)
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

                                    // Calcular nuevas celdas igual que resizeWidgetIfNeeded en AppWidgetResizeFrame.java
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
                        // Toque en el interior del widget con el marco activo:
                        // - Si arrastra -> mueve el widget por el escritorio o entre páginas
                        // - Si solo toca y suelta -> cierra el marco de edición
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
        // Marco visual con los 4 tiradores de redimensionado (Left, Top, Right, Bottom) al estilo Trebuchet
        Box(
            modifier = Modifier
                .offset { IntOffset(frameLeftPx.roundToInt(), frameTopPx.roundToInt()) }
                .size(frameWidthDp, frameHeightDp),
        ) {
            // Borde del marco (widget_resize_frame)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(WidgetFrameShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(2.5.dp, MaterialTheme.colorScheme.primary, WidgetFrameShape),
            )

            // Tirador Izquierdo (widget_resize_left_handle)
            ResizeHandlePill(
                isVertical = true,
                isActive = !isResizingGesture || leftActive,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            // Tirador Derecho (widget_resize_right_handle)
            ResizeHandlePill(
                isVertical = true,
                isActive = !isResizingGesture || rightActive,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            // Tirador Superior (widget_resize_top_handle)
            ResizeHandlePill(
                isVertical = false,
                isActive = !isResizingGesture || topActive,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Tirador Inferior (widget_resize_bottom_handle)
            ResizeHandlePill(
                isVertical = false,
                isActive = !isResizingGesture || bottomActive,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Barra flotante de acciones del widget (tamaño actual, botón Reconfigurar de Trebuchet, Eliminar y Listo)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPicker(
    controller: WidgetController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val entries = remember {
        val pm = ctx.packageManager
        controller.manager.installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .map { it to it.loadLabel(pm) }
            .sortedBy { it.second.lowercase() }
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
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.first.provider.flattenToString() }) { (info, label) ->
                val icon = remember(info) {
                    runCatching {
                        info.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi)
                            ?.toBitmap(96, 96)?.asImageBitmap()
                    }.getOrNull()
                }
                Surface(
                    shape = WidgetItemShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clip(WidgetItemShape).clickable { onPick(info) },
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            headlineColor = MaterialTheme.colorScheme.onSurface,
                            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            val d = ctx.resources.displayMetrics.density
                            Text("${((info.minWidth / d + 30) / 70).toInt().coerceAtLeast(1)} × ${((info.minHeight / d + 30) / 70).toInt().coerceAtLeast(1)}")
                        },
                        leadingContent = {
                            icon?.let { Image(it, null, Modifier.size(44.dp)) }
                        },
                    )
                }
            }
        }
    }
}
