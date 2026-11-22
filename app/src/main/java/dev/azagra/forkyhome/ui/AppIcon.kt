package dev.azagra.forkyhome.ui

import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.IconShape

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

private class AppIconRef(
    var coords: LayoutCoordinates? = null,
    var onClick: () -> Unit = {},
    var actionsProvider: () -> List<MenuAction> = { emptyList() },
    var onPressHoldChange: (Boolean) -> Unit = {},
    var onDragStart: (Offset) -> Unit = {},
    var onDrag: (Offset) -> Unit = {},
    var onDragEnd: () -> Unit = {},
)

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
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    badgeContainerColor: Color = NotificationBadgeRed,
    badgeContentColor: Color = NotificationBadgeText,
) {
    var menu by remember { mutableStateOf(false) }
    var pressingDown by remember { mutableStateOf(false) }
    var pressingLong by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val bitmap = if (useHomeIcon) app.homeIcon else app.icon

    val ref = remember { AppIconRef() }
    ref.onClick = onClick
    ref.actionsProvider = actionsProvider
    ref.onPressHoldChange = onPressHoldChange
    ref.onDragStart = onDragStart
    ref.onDrag = onDrag
    ref.onDragEnd = onDragEnd

    Box(
        modifier = modifier
            .onPlaced { ref.coords = it }
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
                            ref.onClick()
                        }
                    } else {
                        longPress.consume()
                        currentEvent.changes.forEach { it.consume() }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        if (!enableDrag) {
                            menu = true
                        } else {
                            pressingLong = true
                            ref.onPressHoldChange(true)
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
                                        val rootPos = ref.coords.toRootPos(change.position)
                                        ref.onDragStart(rootPos)
                                        ref.onDrag(rootPos)
                                    } else if (dragging) {
                                        val rootPos = ref.coords.toRootPos(change.position)
                                        ref.onDrag(rootPos)
                                    }
                                    change.consume()
                                }
                            } finally {
                                pressingLong = false
                                ref.onPressHoldChange(false)
                            }
                            if (dragging) {
                                ref.onDragEnd()
                            } else {
                                menu = true
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.18f else 1f
                val s = when {
                    isBeingDragged -> 0.85f
                    pressingLong -> 1.12f
                    pressingDown -> 0.93f
                    else -> 1f
                }
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        val resolvedLabelStyle = remember(labelStyle, labelColor) { labelStyle.copy(color = labelColor) }
        AppIconContent(
            bitmap = bitmap,
            label = app.label,
            badge = badge,
            showLabel = showLabel,
            iconSize = iconSize,
            resolvedLabelStyle = resolvedLabelStyle,
            badgeContainerColor = badgeContainerColor,
            badgeContentColor = badgeContentColor,
        )
        if (menu) {
            AppActionsMenu(
                appKey = app.key,
                actionsProvider = ref.actionsProvider,
                onDismiss = { menu = false },
            )
        }
    }
}

@Composable
fun DrawerAppIcon(
    app: AppInfo,
    badge: Int,
    showLabel: Boolean,
    iconSize: Dp,
    resolvedLabelStyle: TextStyle,
    badgeContainerColor: Color,
    badgeContentColor: Color,
    pressedKey: () -> String?,
    longPressedKey: () -> String?,
    draggedKey: () -> String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.graphicsLayer {
            val k = app.key
            val dragged = draggedKey() == k
            alpha = if (dragged) 0.18f else 1f
            val s = when {
                dragged -> 0.85f
                longPressedKey() == k -> 1.12f
                pressedKey() == k -> 0.93f
                else -> 1f
            }
            scaleX = s
            scaleY = s
        },
        contentAlignment = Alignment.Center,
    ) {
        AppIconContent(
            bitmap = app.icon,
            label = app.label,
            badge = badge,
            showLabel = showLabel,
            iconSize = iconSize,
            resolvedLabelStyle = resolvedLabelStyle,
            badgeContainerColor = badgeContainerColor,
            badgeContentColor = badgeContentColor,
        )
    }
}

internal val NotificationBadgeRed = Color(0xFFFF3B30)
internal val NotificationBadgeText = Color.White

/** Globo con el número de notificaciones: círculo rojo (cápsula si hay 2+ dígitos) con el número en blanco. */
@Composable
private fun NotificationBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val text = count.toString()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(containerColor, CircleShape)
            .padding(horizontal = if (text.length > 1) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                color = contentColor,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun AppIconContent(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    label: String,
    badge: Int,
    showLabel: Boolean,
    iconSize: Dp,
    resolvedLabelStyle: TextStyle,
    badgeContainerColor: Color,
    badgeContentColor: Color,
) {
    Column(
        Modifier.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.size(iconSize),
            )
            if (badge > 0) {
                NotificationBadge(
                    count = badge,
                    containerColor = badgeContainerColor,
                    contentColor = badgeContentColor,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                )
            }
        }
        if (showLabel) {
            BasicText(
                text = label,
                style = resolvedLabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun AppActionsMenu(
    appKey: String,
    actionsProvider: () -> List<MenuAction>,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
) {
    val actions by produceState<List<MenuAction>?>(null, appKey) {
        value = withContext(Dispatchers.IO) { actionsProvider() }
    }
    val loadedActions = actions
    if (loadedActions != null) {
        val shortcuts = remember(loadedActions) { loadedActions.filter { it.isShortcut } }
        val standardActions = remember(loadedActions) { loadedActions.filter { !it.isShortcut } }
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            offset = offset,
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
                        onDismiss()
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
                        onDismiss()
                        a.onClick()
                    },
                )
            }
        }
    }
}

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

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressHoldChange by rememberUpdatedState(onPressHoldChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val miniSize = (iconSize - 16.dp) / 2

    Box(
        modifier = modifier
            .onPlaced { coords = it }
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
                alpha = if (isBeingDragged) 0.18f else 1f
                val s = when {
                    isBeingDragged -> 0.85f
                    pressingLong -> 1.12f
                    pressingDown -> 0.93f
                    else -> 1f
                }
                scaleX = s
                scaleY = s
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
                                filterQuality = FilterQuality.Low,
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
                                    filterQuality = FilterQuality.Low,
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
