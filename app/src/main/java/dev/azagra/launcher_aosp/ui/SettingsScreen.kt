package dev.azagra.launcher_aosp.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.azagra.launcher_aosp.util.LockScreenService
import kotlin.math.roundToInt

private val SectionCardShape = RoundedCornerShape(26.dp)
private val OptionCardShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(50)

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
    var accessibilityEnabled by remember { mutableStateOf(LockScreenService.isEnabled(ctx)) }
    LifecycleResumeEffect(Unit) {
        notifEnabled = notificationAccessGranted(ctx)
        accessibilityEnabled = LockScreenService.isEnabled(ctx)
        onPauseOrDispose { }
    }
    var alpha by remember(s.drawerAlpha) { mutableFloatStateOf(s.drawerAlpha) }
    var homeIconScale by remember(s.homeIconScale) { mutableFloatStateOf(s.homeIconScale) }
    var drawerIconScale by remember(s.drawerIconScale) { mutableFloatStateOf(s.drawerIconScale) }
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
                    title = {
                        Text(
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Surface(
                        shape = SectionCardShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_set_default_launcher),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Button(
                                onClick = { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) },
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }

                SectionGroup(
                    title = stringResource(R.string.section_appearance),
                    icon = Icons.Default.Star,
                ) {
                    Text(
                        stringResource(R.string.section_appearance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OptionPillsRow(
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ),
                        selected = s.themeMode,
                        onSelect = { v -> onChange { it.copy(themeMode = v) } },
                    )

                    CardDivider()

                    SwitchRow(
                        title = stringResource(R.string.settings_material_you),
                        subtitle = stringResource(R.string.settings_material_you_desc),
                        checked = s.dynamicColor,
                        onChecked = { v -> onChange { it.copy(dynamicColor = v) } },
                    )

                    CardDivider()

                    ActionRow(
                        title = stringResource(R.string.settings_change_wallpaper),
                        subtitle = stringResource(R.string.menu_wallpaper),
                        icon = Icons.Default.Edit,
                        onClick = { openWallpaperPicker(ctx) },
                    )
                }

                SectionGroup(
                    title = stringResource(R.string.section_icons),
                    icon = Icons.Default.Star,
                ) {
                    val activePack = packs.firstOrNull { it.pkg == s.iconPack }
                    val packLabel = activePack?.label ?: stringResource(R.string.icon_pack_default_system)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OptionCardShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { packDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (activePack?.icon != null) {
                            Image(
                                bitmap = activePack.icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dialog_icon_pack_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = packLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    CardDivider()

                    Text(
                        text = stringResource(R.string.settings_icon_shape),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconShapeGrid(
                        selected = s.iconShape,
                        onSelect = { sh -> onChange { it.copy(iconShape = sh) } },
                    )

                    CardDivider()

                    SwitchRow(
                        title = stringResource(R.string.settings_themed_icons),
                        subtitle = stringResource(R.string.settings_themed_icons_desc),
                        checked = s.themedIcons,
                        onChecked = { v -> onChange { it.copy(themedIcons = v) } },
                    )

                    if (hasNotifService) {
                        CardDivider()
                        SwitchRow(
                            title = stringResource(R.string.settings_badge_numbers),
                            subtitle = stringResource(R.string.settings_badge_numbers_desc),
                            checked = s.badgeNumbers,
                            onChecked = { v -> onChange { it.copy(badgeNumbers = v) } },
                        )
                        if (s.badgeNumbers && !notifEnabled) {
                            FilledTonalButton(
                                onClick = {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_grant_notification_access))
                            }
                        }
                    }
                }

                SectionGroup(
                    title = stringResource(R.string.section_home_screen),
                    icon = Icons.Default.Home,
                ) {
                    SwitchRow(
                        title = stringResource(R.string.settings_at_a_glance),
                        subtitle = stringResource(R.string.settings_at_a_glance_desc),
                        checked = s.showAtAGlance,
                        onChecked = { v -> onChange { it.copy(showAtAGlance = v) } },
                    )
                    CardDivider()
                    StepperRow(
                        title = stringResource(R.string.settings_home_columns),
                        value = s.homeCols,
                        range = 4..6,
                        onValueChange = { v -> onChange { it.copy(homeCols = v) } },
                    )
                    CardDivider()
                    StepperRow(
                        title = stringResource(R.string.settings_home_rows),
                        value = s.homeRows,
                        range = 4..7,
                        onValueChange = { v -> onChange { it.copy(homeRows = v) } },
                    )
                    CardDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_home_icon_size),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${(homeIconScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = homeIconScale,
                            onValueChange = { homeIconScale = (it * 20).roundToInt() / 20f },
                            onValueChangeFinished = { onChange { it.copy(homeIconScale = homeIconScale) } },
                            valueRange = 0.80f..1.20f,
                            steps = 7,
                        )
                    }
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_show_home_labels),
                        subtitle = null,
                        checked = s.showHomeLabels,
                        onChecked = { v -> onChange { it.copy(showHomeLabels = v) } },
                    )
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_lock_desktop),
                        subtitle = stringResource(R.string.settings_lock_desktop_desc),
                        checked = s.lockDesktop,
                        onChecked = { v -> onChange { it.copy(lockDesktop = v) } },
                    )
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_add_apps),
                        subtitle = null,
                        checked = s.autoAddNewApps,
                        onChecked = { v -> onChange { it.copy(autoAddNewApps = v) } },
                    )
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_swipe_down_notif),
                        subtitle = stringResource(R.string.settings_swipe_down_notif_desc),
                        checked = s.swipeDownNotifications,
                        onChecked = { v -> onChange { it.copy(swipeDownNotifications = v) } },
                    )
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_double_tap_sleep),
                        subtitle = stringResource(R.string.settings_double_tap_sleep_desc),
                        checked = s.doubleTapToSleep,
                        onChecked = { v -> onChange { it.copy(doubleTapToSleep = v) } },
                    )
                    if (s.doubleTapToSleep && !accessibilityEnabled) {
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_grant_accessibility_access))
                        }
                    }
                }

                SectionGroup(
                    title = stringResource(R.string.section_app_drawer),
                    icon = Icons.Default.Settings,
                ) {
                    Text(
                        text = stringResource(R.string.section_app_drawer),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    DrawerBgGrid(
                        selected = s.drawerBg,
                        onSelect = { bg -> onChange { it.copy(drawerBg = bg) } },
                    )

                    CardDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_bg_opacity),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${(alpha * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = alpha,
                            onValueChange = { alpha = it },
                            onValueChangeFinished = { onChange { it.copy(drawerAlpha = alpha) } },
                            valueRange = 0.5f..1f,
                        )
                    }

                    CardDivider()

                    StepperRow(
                        title = stringResource(R.string.settings_columns),
                        value = s.columns,
                        range = 4..6,
                        onValueChange = { v -> onChange { it.copy(columns = v) } },
                    )

                    CardDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_drawer_icon_size),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${(drawerIconScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = drawerIconScale,
                            onValueChange = { drawerIconScale = (it * 20).roundToInt() / 20f },
                            onValueChangeFinished = { onChange { it.copy(drawerIconScale = drawerIconScale) } },
                            valueRange = 0.80f..1.20f,
                            steps = 7,
                        )
                    }

                    CardDivider()

                    SwitchRow(
                        title = stringResource(R.string.settings_show_labels),
                        subtitle = null,
                        checked = s.showLabels,
                        onChecked = { v -> onChange { it.copy(showLabels = v) } },
                    )
                }

                SectionGroup(
                    title = stringResource(R.string.section_privacy),
                    icon = Icons.Default.Lock,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OptionCardShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                if (onAuthenticate != null) {
                                    onAuthenticate { privacyDialog = true }
                                } else {
                                    privacyDialog = true
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_hidden_protected_apps),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.settings_hidden_protected_desc, s.hiddenApps.size, s.protectedApps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val repoUrl = stringResource(R.string.footer_repo_url)
                Surface(
                    shape = SectionCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.footer_version_build, versionName, versionCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.footer_open_source))
                        }
                        Text(
                            text = repoUrl.removePrefix("https://"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }

    if (packDialog) {
        AlertDialog(
            onDismissRequest = { packDialog = false },
            shape = SectionCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_icon_pack_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (listOf<IconPacks.Pack?>(null) + packs).forEach { p ->
                        val isSelected = s.iconPack == p?.pkg
                        Surface(
                            shape = OptionCardShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(OptionCardShape)
                                .clickable {
                                    onChange { it.copy(iconPack = p?.pkg) }
                                    packDialog = false
                                },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (p?.icon != null) {
                                    Image(
                                        bitmap = p.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp).clip(CircleShape),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = p?.label ?: stringResource(R.string.icon_pack_default_system),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (p != null) {
                                        Text(
                                            text = p.pkg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                RadioButton(selected = isSelected, onClick = null)
                            }
                        }
                    }
                    if (packs.isEmpty()) {
                        Text(
                            stringResource(R.string.dialog_no_icon_packs),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({ packDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (privacyDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedTab by remember { mutableIntStateOf(0) }
        val uniqueApps = remember(allApps, searchQuery) {
            val base = allApps.distinctBy { it.pkg }
            if (searchQuery.isBlank()) base
            else base.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            shape = SectionCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_hidden_protected_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text("${stringResource(R.string.label_hidden)} (${s.hiddenApps.size})") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text("${stringResource(R.string.label_protected)} (${s.protectedApps.size})") },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = PillShape,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(uniqueApps, key = { it.pkg }) { app ->
                            val isChecked = if (selectedTab == 0) app.pkg in s.hiddenApps else app.pkg in s.protectedApps
                            Surface(
                                shape = OptionCardShape,
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(OptionCardShape)
                                    .clickable {
                                        onChange { st ->
                                            if (selectedTab == 0) {
                                                val next = if (isChecked) st.hiddenApps - app.pkg else st.hiddenApps + app.pkg
                                                st.copy(hiddenApps = next)
                                            } else {
                                                val next = if (isChecked) st.protectedApps - app.pkg else st.protectedApps + app.pkg
                                                st.copy(protectedApps = next)
                                            }
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = null,
                                    )
                                }
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
private fun IconShapeGrid(
    selected: IconShape,
    onSelect: (IconShape) -> Unit,
) {
    val shapes = listOf(
        IconShape.DEFAULT to stringResource(R.string.shape_default),
        IconShape.CIRCLE to stringResource(R.string.shape_circle),
        IconShape.SQUIRCLE to stringResource(R.string.shape_squircle),
        IconShape.ROUNDED to stringResource(R.string.shape_rounded),
        IconShape.TEARDROP to stringResource(R.string.shape_teardrop),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shapes.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (shape, label) ->
                    val isSelected = selected == shape
                    val previewShape = if (shape == IconShape.DEFAULT) RoundedCornerShape(8.dp) else iconMaskShape(shape)
                    Surface(
                        shape = OptionCardShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(OptionCardShape)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = OptionCardShape,
                            )
                            .clickable { onSelect(shape) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(previewShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerBgGrid(
    selected: DrawerBg,
    onSelect: (DrawerBg) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val options = listOf(
        Triple(DrawerBg.SYSTEM, stringResource(R.string.drawer_bg_system), cs.surfaceContainerLow),
        Triple(DrawerBg.WHITE, stringResource(R.string.drawer_bg_white), Color.White),
        Triple(DrawerBg.DARK, stringResource(R.string.drawer_bg_dark), Color(0xFF1B1B1F)),
        Triple(DrawerBg.BLACK, stringResource(R.string.drawer_bg_black), Color.Black),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (bg, label, swatchColor) ->
                    val isSelected = selected == bg
                    Surface(
                        shape = OptionCardShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(OptionCardShape)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = OptionCardShape,
                            )
                            .clickable { onSelect(bg) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionPillsRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                shape = OptionCardShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .clip(OptionCardShape)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = OptionCardShape,
                    )
                    .clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilledTonalIconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.size(36.dp),
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.width(44.dp),
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            FilledTonalIconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SectionCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .clickable { onChecked(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
