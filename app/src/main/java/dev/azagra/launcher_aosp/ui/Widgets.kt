package dev.azagra.launcher_aosp.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.widgets.LongPressHostView
import dev.azagra.launcher_aosp.widgets.WidgetController

private val WidgetSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val WidgetItemShape = RoundedCornerShape(20.dp)

@Composable
fun WidgetView(id: Int, controller: WidgetController, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val info = controller.manager.getAppWidgetInfo(id)
                controller.host.createView(ctx, id, info) as LongPressHostView
            },
            update = { v ->
                v.onLongPress = onLongPress
                v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            },
        )
    }
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
