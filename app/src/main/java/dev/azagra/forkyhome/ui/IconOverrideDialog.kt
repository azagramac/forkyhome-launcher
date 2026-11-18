package dev.azagra.forkyhome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.IconPacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun IconOverrideDialog(
    app: AppInfo,
    activePack: String?,
    currentOverride: String?,
    packs: IconPacks,
    onSelectOverride: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val installedPacks by produceState<List<IconPacks.Pack>>(emptyList()) {
        value = withContext(Dispatchers.IO) { packs.installed() }
    }
    var selectedPack by remember(activePack, installedPacks) {
        mutableStateOf(
            when {
                currentOverride != null && '|' in currentOverride -> currentOverride.substringBefore('|')
                activePack != null -> activePack
                else -> installedPacks.firstOrNull()?.pkg
            }
        )
    }
    val drawables by produceState<List<String>>(emptyList(), selectedPack) {
        val pkg = selectedPack
        value = if (pkg != null) withContext(Dispatchers.IO) { packs.listDrawables(pkg) } else emptyList()
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(drawables, query) {
        val q = query.trim()
        if (q.isEmpty()) drawables else drawables.filter { it.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.dialog_edit_icon_title, app.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = currentOverride == null,
                        onClick = { onSelectOverride(null) },
                        label = { Text(stringResource(R.string.edit_icon_default)) },
                    )
                    FilterChip(
                        selected = currentOverride == "SYSTEM",
                        onClick = { onSelectOverride("SYSTEM") },
                        label = { Text(stringResource(R.string.edit_icon_system)) },
                    )
                }

                if (installedPacks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        installedPacks.forEach { p ->
                            FilterChip(
                                selected = selectedPack == p.pkg,
                                onClick = { selectedPack = p.pkg },
                                label = { Text(p.label) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.edit_icon_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val pkg = selectedPack
                    if (pkg != null) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(56.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it }) { drName ->
                                val bmp by produceState<ImageBitmap?>(null, pkg, drName) {
                                    value = withContext(Dispatchers.IO) {
                                        packs.loadDrawableBitmap(pkg, drName, 128)?.asImageBitmap()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable { onSelectOverride("$pkg|$drName") }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    bmp?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = drName,
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.dialog_no_icon_packs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
