package app.mylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.mylauncher.data.AppInfo
import app.mylauncher.data.DrawerBg
import app.mylauncher.data.LauncherSettings

/** Colores del fondo del cajón: sistema (Material You), blanco, negro u oscuro. */
@Composable
fun drawerColors(s: LauncherSettings): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val base = when (s.drawerBg) {
        DrawerBg.SYSTEM -> cs.surface
        DrawerBg.WHITE -> Color.White
        DrawerBg.BLACK -> Color.Black
        DrawerBg.DARK -> Color(0xFF1B1B1F)
    }
    val on = when (s.drawerBg) {
        DrawerBg.SYSTEM -> cs.onSurface
        DrawerBg.WHITE -> Color.Black
        else -> Color.White
    }
    return base.copy(alpha = s.drawerAlpha) to on
}

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    counts: Map<String, Int>,
    s: LauncherSettings,
    drawer: DrawerState,
    heightPx: Float,
    onLaunch: (AppInfo) -> Unit,
    actionsFor: (AppInfo) -> List<MenuAction>,
) {
    var query by remember { mutableStateOf("") }
    val (bg, on) = drawerColors(s)
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    // Arrastrar el cajón desde dentro de la lista: subir lo abre del todo, bajar desde arriba lo cierra.
    val connection = remember(drawer, heightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0 && drawer.progress < 1f) {
                    drawer.drag(-available.y / heightPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0) {
                    drawer.drag(-available.y / heightPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (drawer.progress in 0.001f..0.999f) {
                    drawer.settle(-available.y / heightPx)
                    available
                } else Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y > 0) {
                    drawer.settle(-available.y / heightPx)
                    available
                } else Velocity.Zero
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - drawer.progress) * size.height }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(bg)
            .nestedScroll(connection)
            .statusBarsPadding()
            .imePadding()
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Buscar aplicaciones", color = on.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = on) },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = on.copy(alpha = 0.08f),
                unfocusedContainerColor = on.copy(alpha = 0.08f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = on,
                unfocusedTextColor = on,
                cursorColor = on,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .draggable(
                    state = rememberDraggableState { dy -> drawer.drag(-dy / heightPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / heightPx) },
                ),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(filtered, key = { it.key }) { app ->
                AppIcon(
                    app = app,
                    badge = if (s.badgeNumbers) counts[app.pkg] ?: 0 else 0,
                    showLabel = s.showLabels,
                    labelColor = on,
                    iconSize = 56.dp,
                    actions = actionsFor(app),
                    onClick = { onLaunch(app) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
