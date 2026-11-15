package dev.azagra.launcher_aosp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

private val DrawerTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SearchBarShape = RoundedCornerShape(28.dp)

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
    onLaunch: (AppInfo) -> Unit,
    actionsFor: (AppInfo) -> List<MenuAction>,
) {
    var query by remember { mutableStateOf("") }
    val (bg, on, searchBg) = drawerColors(s)
    val searchIconTint = if (s.drawerBg == DrawerBg.SYSTEM) MaterialTheme.colorScheme.primary else on
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(drawer.isOpen) {
        if (!drawer.isOpen) {
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

    // Arrastrar el cajón desde dentro de la lista sin interferir con el fling a 120fps de la cuadrícula
    val connection = remember(drawer, heightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0 && drawer.progress < 1f) {
                    drawer.drag(-available.y / heightPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    focusManager.clearFocus()
                    drawer.drag(-available.y / heightPx)
                    Offset(0f, available.y)
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (drawer.progress in 0.001f..0.999f) {
                    drawer.settle(-available.y / heightPx)
                    available
                } else Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (drawer.progress < 1f && available.y > 0) {
                    drawer.settle(-available.y / heightPx)
                    available
                } else Velocity.Zero
        }
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridPadding = remember(navBottomPadding) {
        PaddingValues(start = 8.dp, end = 8.dp, bottom = navBottomPadding + 16.dp)
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = drawer.progress
                translationY = (1f - p) * size.height
                alpha = if (p > 0.001f) 1f else 0f
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
                    state = rememberDraggableState { dy -> drawer.drag(-dy / heightPx) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> drawer.settle(-v / heightPx) },
                ),
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(s.columns),
            modifier = Modifier.weight(1f),
            contentPadding = gridPadding,
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
                    iconSize = 56.dp,
                    actionsProvider = { actionsFor(app) },
                    onClick = { onLaunch(app) },
                    modifier = Modifier.fillMaxWidth(),
                    useHomeIcon = false,
                    iconShape = s.iconShape,
                )
            }
        }
    }
}
