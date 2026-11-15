package dev.azagra.launcher_aosp.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.data.AppInfo
import dev.azagra.launcher_aosp.data.DrawerBg
import dev.azagra.launcher_aosp.data.IconPacks
import dev.azagra.launcher_aosp.data.IconShape
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.data.ThemeMode
import dev.azagra.launcher_aosp.notif.NotificationCounter

private val SectionCardShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    s: LauncherSettings,
    packs: List<IconPacks.Pack>,
    allApps: List<AppInfo> = emptyList(),
    onAuthenticate: ((() -> Unit) -> Unit)? = null,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val hasNotifService = remember {
        ctx.packageManager.resolveService(Intent(ctx, NotificationCounter::class.java), 0) != null
    }
    var notifEnabled by remember { mutableStateOf(notificationAccessGranted(ctx)) }
    LifecycleResumeEffect(Unit) {
        notifEnabled = notificationAccessGranted(ctx)
        onPauseOrDispose { }
    }
    var alpha by remember(s.drawerAlpha) { mutableFloatStateOf(s.drawerAlpha) }
    var packDialog by remember { mutableStateOf(false) }
    var privacyDialog by remember { mutableStateOf(false) }
    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val (versionName, versionCode) = remember {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (info.versionName ?: "0.1.0") to info.longVersionCode
        }.getOrDefault("0.1.0" to 1L)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(stringResource(R.string.section_appearance)) {
                    Segmented(
                        listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ),
                        s.themeMode,
                    ) { v -> onChange { it.copy(themeMode = v) } }
                    SwitchRow(
                        stringResource(R.string.settings_material_you),
                        stringResource(R.string.settings_material_you_desc),
                        s.dynamicColor,
                    ) { v ->
                        onChange { it.copy(dynamicColor = v) }
                    }
                    FilledTonalButton({ openWallpaperPicker(ctx) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_change_wallpaper))
                    }
                }

                SectionCard(stringResource(R.string.section_home_screen)) {
                    Text(stringResource(R.string.settings_pages), style = MaterialTheme.typography.bodyMedium)
                    Segmented((1..5).map { it to it.toString() }, s.homePages) { v -> onChange { it.copy(homePages = v) } }

                    Text(stringResource(R.string.settings_home_columns), style = MaterialTheme.typography.bodyMedium)
                    Segmented(listOf(4 to "4", 5 to "5", 6 to "6"), s.homeCols) { v -> onChange { it.copy(homeCols = v) } }

                    Text(stringResource(R.string.settings_home_rows), style = MaterialTheme.typography.bodyMedium)
                    Segmented(listOf(4 to "4", 5 to "5", 6 to "6", 7 to "7"), s.homeRows) { v -> onChange { it.copy(homeRows = v) } }

                    SwitchRow(stringResource(R.string.settings_show_home_labels), null, s.showHomeLabels) { v ->
                        onChange { it.copy(showHomeLabels = v) }
                    }
                    SwitchRow(
                        stringResource(R.string.settings_lock_desktop),
                        stringResource(R.string.settings_lock_desktop_desc),
                        s.lockDesktop,
                    ) { v ->
                        onChange { it.copy(lockDesktop = v) }
                    }
                    SwitchRow(stringResource(R.string.settings_auto_add_apps), null, s.autoAddNewApps) { v ->
                        onChange { it.copy(autoAddNewApps = v) }
                    }
                    SwitchRow(
                        stringResource(R.string.settings_swipe_down_notif),
                        stringResource(R.string.settings_swipe_down_notif_desc),
                        s.swipeDownNotifications,
                    ) { v ->
                        onChange { it.copy(swipeDownNotifications = v) }
                    }

                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Button(
                            { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) },
                            Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_set_default_launcher)) }
                    }
                }

                SectionCard(stringResource(R.string.section_app_drawer)) {
                    Segmented(
                        listOf(
                            DrawerBg.SYSTEM to stringResource(R.string.drawer_bg_system),
                            DrawerBg.WHITE to stringResource(R.string.drawer_bg_white),
                            DrawerBg.BLACK to stringResource(R.string.drawer_bg_black),
                            DrawerBg.DARK to stringResource(R.string.drawer_bg_dark),
                        ),
                        s.drawerBg,
                    ) { v -> onChange { it.copy(drawerBg = v) } }
                    Text(stringResource(R.string.settings_bg_opacity), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        onValueChangeFinished = { onChange { it.copy(drawerAlpha = alpha) } },
                        valueRange = 0.5f..1f,
                    )
                    Text(stringResource(R.string.settings_columns), style = MaterialTheme.typography.bodyMedium)
                    Segmented(listOf(4 to "4", 5 to "5", 6 to "6"), s.columns) { v -> onChange { it.copy(columns = v) } }
                    SwitchRow(stringResource(R.string.settings_show_labels), null, s.showLabels) { v ->
                        onChange { it.copy(showLabels = v) }
                    }
                }

                SectionCard(stringResource(R.string.section_icons)) {
                    FilledTonalButton({ packDialog = true }, Modifier.fillMaxWidth()) {
                        val packName = packs.firstOrNull { it.pkg == s.iconPack }?.label ?: stringResource(R.string.icon_pack_default)
                        Text(stringResource(R.string.settings_icon_pack_value, packName))
                    }
                    Text(stringResource(R.string.settings_icon_shape), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val shapes = listOf(
                            IconShape.DEFAULT to stringResource(R.string.shape_default),
                            IconShape.CIRCLE to stringResource(R.string.shape_circle),
                            IconShape.SQUIRCLE to stringResource(R.string.shape_squircle),
                            IconShape.ROUNDED to stringResource(R.string.shape_rounded),
                            IconShape.TEARDROP to stringResource(R.string.shape_teardrop),
                        )
                        shapes.forEach { (sh, label) ->
                            FilterChip(
                                selected = s.iconShape == sh,
                                onClick = { onChange { it.copy(iconShape = sh) } },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    SwitchRow(
                        stringResource(R.string.settings_themed_icons),
                        stringResource(R.string.settings_themed_icons_desc),
                        s.themedIcons,
                    ) { v ->
                        onChange { it.copy(themedIcons = v) }
                    }
                    if (hasNotifService) {
                        SwitchRow(
                            stringResource(R.string.settings_badge_numbers),
                            stringResource(R.string.settings_badge_numbers_desc),
                            s.badgeNumbers,
                        ) { v ->
                            onChange { it.copy(badgeNumbers = v) }
                        }
                        if (s.badgeNumbers && !notifEnabled) {
                            Button(
                                { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                                Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.settings_grant_notification_access)) }
                        }
                    }
                }

                // Sección de Privacidad estilo LineageOS Trebuchet (Apps ocultas y protegidas)
                SectionCard(stringResource(R.string.section_privacy)) {
                    Text(
                        stringResource(R.string.settings_hidden_protected_desc, s.hiddenApps.size, s.protectedApps.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = {
                            if (onAuthenticate != null) {
                                onAuthenticate { privacyDialog = true }
                            } else {
                                privacyDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_hidden_protected_apps))
                    }
                }

                // Pie de información: Versión, Build y enlace al repositorio Open Source
                val repoUrl = stringResource(R.string.footer_repo_url)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.footer_version_build, versionName, versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.footer_open_source),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = repoUrl.removePrefix("https://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (packDialog) {
        AlertDialog(
            onDismissRequest = { packDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_icon_pack_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf<IconPacks.Pack?>(null) + packs).forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChange { it.copy(iconPack = p?.pkg) }; packDialog = false }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.iconPack == p?.pkg, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(p?.label ?: stringResource(R.string.icon_pack_default_system))
                        }
                    }
                    if (packs.isEmpty()) Text(
                        stringResource(R.string.dialog_no_icon_packs),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton({ packDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    if (privacyDialog) {
        val uniqueApps = remember(allApps) { allApps.distinctBy { it.pkg } }
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_hidden_protected_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uniqueApps, key = { it.pkg }) { app ->
                        val isHidden = app.pkg in s.hiddenApps
                        val isProtected = app.pkg in s.protectedApps
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.label_hidden), style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = isHidden,
                                    onCheckedChange = { checked ->
                                        onChange { st ->
                                            val next = if (checked) st.hiddenApps + app.pkg else st.hiddenApps - app.pkg
                                            st.copy(hiddenApps = next)
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.label_protected), style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = isProtected,
                                    onCheckedChange = { checked ->
                                        onChange { st ->
                                            val next = if (checked) st.protectedApps + app.pkg else st.protectedApps - app.pkg
                                            st.copy(protectedApps = next)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ privacyDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}
