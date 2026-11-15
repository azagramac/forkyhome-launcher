package app.mylauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mylauncher.data.AppInfo

data class MenuAction(val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    badge: Int,
    showLabel: Boolean,
    labelColor: Color,
    iconSize: Dp,
    actions: List<MenuAction>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Column(
            Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(iconSize))
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
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            actions.forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.onClick() })
            }
        }
    }
}
