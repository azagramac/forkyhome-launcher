package dev.azagra.launcher_aosp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.azagra.launcher_aosp.data.AppInfo

private val PopupMenuShape = RoundedCornerShape(22.dp)

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
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
    enableDrag: Boolean = false,
    isBeingDragged: Boolean = false,
    onDragStart: (centerInRoot: Offset) -> Unit = {},
    onDrag: (delta: Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    val bitmap = if (useHomeIcon) app.homeIcon else app.icon

    Box(
        modifier = modifier
            .onGloballyPositioned { coords = it }
            .graphicsLayer { alpha = if (isBeingDragged) 0.25f else 1f }
    ) {
        Column(
            Modifier
                .pointerInput(app.key, enableDrag) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                up.consume()
                                onClick()
                            }
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                            if (enableDrag) {
                                var dragging = false
                                var totalMove = Offset.Zero
                                val slop = viewConfiguration.touchSlop
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        totalMove += delta
                                        if (!dragging && totalMove.getDistance() > slop) {
                                            dragging = true
                                            menu = false
                                            val c = coords
                                            val center = if (c != null && c.isAttached) {
                                                c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)
                                            } else {
                                                longPress.position
                                            }
                                            onDragStart(center)
                                            onDrag(totalMove)
                                        } else if (dragging) {
                                            onDrag(delta)
                                        }
                                        change.consume()
                                    }
                                }
                                if (dragging) {
                                    onDragEnd()
                                }
                            }
                        }
                    }
                }
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(iconSize),
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
            val actions = remember(app.key) { actionsProvider() }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menu = false },
                shape = PopupMenuShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                actions.forEach { a ->
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
