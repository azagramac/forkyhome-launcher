package dev.azagra.launcher_aosp.ui

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.azagra.launcher_aosp.R
import dev.azagra.launcher_aosp.data.DrawerBg
import dev.azagra.launcher_aosp.data.IconPacks
import dev.azagra.launcher_aosp.data.LauncherSettings
import dev.azagra.launcher_aosp.data.ThemeMode
import dev.azagra.launcher_aosp.notif.NotificationCounter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    s: LauncherSettings,
    packs: List<IconPacks.Pack>,
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
    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    },
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section(stringResource(R.string.section_appearance))
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
                OutlinedButton({ openWallpaperPicker(ctx) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_change_wallpaper)) }

                Section(stringResource(R.string.section_app_drawer))
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
                SwitchRow(stringResource(R.string.settings_show_labels), null, s.showLabels) { v -> onChange { it.copy(showLabels = v) } }

                Section(stringResource(R.string.section_icons))
                OutlinedButton({ packDialog = true }, Modifier.fillMaxWidth()) {
                    val packName = packs.firstOrNull { it.pkg == s.iconPack }?.label ?: stringResource(R.string.icon_pack_default)
                    Text(stringResource(R.string.settings_icon_pack_value, packName))
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

                Section(stringResource(R.string.section_home_screen))
                Text(stringResource(R.string.settings_pages), style = MaterialTheme.typography.bodyMedium)
                Segmented((1..5).map { it to it.toString() }, s.homePages) { v -> onChange { it.copy(homePages = v) } }

                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Button(
                        { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) },
                        Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_set_default_launcher)) }
                }
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }

    if (packDialog) {
        AlertDialog(
            onDismissRequest = { packDialog = false },
            title = { Text(stringResource(R.string.dialog_icon_pack_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf<IconPacks.Pack?>(null) + packs).forEach { p ->
                        Row(
                            Modifier.fillMaxWidth()
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
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
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
