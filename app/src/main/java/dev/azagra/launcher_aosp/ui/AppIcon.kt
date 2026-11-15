package dev.azagra.launcher_aosp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.azagra.launcher_aosp.data.AppInfo
import dev.azagra.launcher_aosp.data.IconShape

private val PopupMenuShape = RoundedCornerShape(22.dp)
private val SquircleShape = RoundedCornerShape(percent = 30)
private val RoundedRectIconShape = RoundedCornerShape(percent = 20)
private val TeardropIconShape = RoundedCornerShape(
    topStartPercent = 50,
    topEndPercent = 50,
    bottomStartPercent = 50,
    bottomEndPercent = 14,
)

fun iconMaskShape(shape: IconShape): Shape = when (shape) {
    IconShape.DEFAULT -> RectangleShape
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.ROUNDED -> RoundedRectIconShape
    IconShape.TEARDROP -> TeardropIconShape
}

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val isShortcut: Boolean = false,
    val onClick: () -> Unit,
)

private fun LayoutCoordinates?.toRootPos(localPos: Offset): Offset {
    val c = this
    return if (c != null && c.isAttached) {
        c.localToRoot(localPos)
    } else {
        localPos
    }
}

@Composable
fun AppIcon(
    app: AppInfo,
    badge: Int,
    showLabel: Boolean,
    labelColor: Color,
    iconSize: Dp,
    actionsProvider: () -> List<MenuAction>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useHomeIcon: Boolean = false,
    iconShape: IconShape = IconShape.DEFAULT,
    enableDrag: Boolean = false,
    isBeingDragged: Boolean = false,
    onPressHoldChange: (Boolean) -> Unit = {},
    onDragStart: (posInRoot: Offset) -> Unit = {},
    onDrag: (posInRoot: Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var pressingDown by remember { mutableStateOf(false) }
    var pressingLong by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    val bitmap = if (useHomeIcon) app.homeIcon else app.icon
    val clipShape = remember(iconShape) { iconMaskShape(iconShape) }

    val targetScale = when {
        isBeingDragged -> 0.85f
        pressingLong -> 1.12f
        pressingDown -> 0.93f
        else -> 1f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label = "iconScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isBeingDragged) 0.18f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "iconAlpha",
    )

    // Garantiza que pointerInput siempre invoque los callbacks con el estado más reciente de la pantalla
    val currentOnClick by rememberUpdatedState(onClick)
    val currentActionsProvider by rememberUpdatedState(actionsProvider)
    val currentOnPressHoldChange by rememberUpdatedState(onPressHoldChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .onGloballyPositioned { coords = it }
            .pointerInput(app.key, enableDrag) {
                val dragThresholdPx = 4.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressingDown = true
                    val longPress = try {
                        awaitLongPressOrCancellation(down.id)
                    } finally {
                        pressingDown = false
                    }
                    if (longPress == null) {
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && !up.pressed && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                            up.consume()
                            currentOnClick()
                        }
                    } else {
                        // Consumimos el evento para que HomePage NO abra un segundo menú flotante de fondo
                        longPress.consume()
                        currentEvent.changes.forEach { it.consume() }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        if (!enableDrag) {
                            menu = true
                        } else {
                            pressingLong = true
                            currentOnPressHoldChange(true)
                            var dragging = false
                            val startLocalPos = longPress.position
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    val movedDist = (change.position - startLocalPos).getDistance()
                                    if (!dragging && movedDist > dragThresholdPx) {
                                        dragging = true
                                        pressingLong = false
                                        val rootPos = coords.toRootPos(change.position)
                                        currentOnDragStart(rootPos)
                                        currentOnDrag(rootPos)
                                    } else if (dragging) {
                                        val rootPos = coords.toRootPos(change.position)
                                        currentOnDrag(rootPos)
                                    }
                                    change.consume()
                                }
                            } finally {
                                pressingLong = false
                                currentOnPressHoldChange(false)
                            }
                            if (dragging) {
                                currentOnDragEnd()
                            } else {
                                menu = true
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .size(iconSize)
                        .let { if (iconShape != IconShape.DEFAULT) it.clip(clipShape) else it },
                )
                if (badge > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(if (badge > 99) "99+" else badge.toString()) }
                }
            }
            if (showLabel) {
                Text(
                    app.label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (menu) {
            val actions = remember(app.key) { currentActionsProvider() }
            val shortcuts = actions.filter { it.isShortcut }
            val standardActions = actions.filter { !it.isShortcut }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menu = false },
                shape = PopupMenuShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                shortcuts.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                a.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = a.icon?.let { ic ->
                            {
                                Icon(
                                    imageVector = ic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.secondary,
                        ),
                        onClick = {
                            menu = false
                            a.onClick()
                        },
                    )
                }
                if (shortcuts.isNotEmpty() && standardActions.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                standardActions.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                a.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = a.icon?.let { ic ->
                            {
                                Icon(
                                    imageVector = ic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = {
                            menu = false
                            a.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** Icono de Carpeta en el escritorio con vista previa 2×2 estilo Trebuchet / Material You. */
@Composable
fun FolderIcon(
    title: String,
    previewApps: List<AppInfo>,
    showLabel: Boolean,
    labelColor: Color,
    iconSize: Dp,
    useHomeIcon: Boolean,
    enableDrag: Boolean,
    isBeingDragged: Boolean,
    onClick: () -> Unit,
    onPressHoldChange: (Boolean) -> Unit = {},
    onDragStart: (posInRoot: Offset) -> Unit,
    onDrag: (posInRoot: Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressingDown by remember { mutableStateOf(false) }
    var pressingLong by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val targetScale = when {
        isBeingDragged -> 0.85f
        pressingLong -> 1.12f
        pressingDown -> 0.93f
        else -> 1f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label = "folderScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isBeingDragged) 0.18f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "folderAlpha",
    )

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressHoldChange by rememberUpdatedState(onPressHoldChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val miniSize = (iconSize - 16.dp) / 2

    Box(
        modifier = modifier
            .onGloballyPositioned { coords = it }
            .pointerInput(enableDrag) {
                val dragThresholdPx = 4.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressingDown = true
                    val longPress = try {
                        awaitLongPressOrCancellation(down.id)
                    } finally {
                        pressingDown = false
                    }
                    if (longPress == null) {
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && !up.pressed && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                            up.consume()
                            currentOnClick()
                        }
                    } else if (!enableDrag) {
                        longPress.consume()
                        currentOnClick()
                    } else {
                        longPress.consume()
                        currentEvent.changes.forEach { it.consume() }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pressingLong = true
                        currentOnPressHoldChange(true)
                        var dragging = false
                        val startLocalPos = longPress.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val movedDist = (change.position - startLocalPos).getDistance()
                                if (!dragging && movedDist > dragThresholdPx) {
                                    dragging = true
                                    pressingLong = false
                                    val rootPos = coords.toRootPos(change.position)
                                    currentOnDragStart(rootPos)
                                    currentOnDrag(rootPos)
                                } else if (dragging) {
                                    val rootPos = coords.toRootPos(change.position)
                                    currentOnDrag(rootPos)
                                }
                                change.consume()
                            }
                        } finally {
                            pressingLong = false
                            currentOnPressHoldChange(false)
                        }
                        if (dragging) {
                            currentOnDragEnd()
                        } else {
                            currentOnClick()
                        }
                    }
                }
            }
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(SquircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), SquircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val topRow = previewApps.take(2)
                val bottomRow = previewApps.drop(2).take(2)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        topRow.forEach { a ->
                            Image(
                                bitmap = if (useHomeIcon) a.homeIcon else a.icon,
                                contentDescription = null,
                                filterQuality = FilterQuality.Medium,
                                modifier = Modifier.size(miniSize),
                            )
                        }
                    }
                    if (bottomRow.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            bottomRow.forEach { a ->
                                Image(
                                    bitmap = if (useHomeIcon) a.homeIcon else a.icon,
                                    contentDescription = null,
                                    filterQuality = FilterQuality.Medium,
                                    modifier = Modifier.size(miniSize),
                                )
                            }
                        }
                    }
                }
            }
            if (showLabel) {
                Text(
                    title,
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
