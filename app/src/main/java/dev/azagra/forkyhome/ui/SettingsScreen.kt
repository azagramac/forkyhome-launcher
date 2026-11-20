package dev.azagra.forkyhome.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.DrawerBg
import dev.azagra.forkyhome.data.IconPacks
import dev.azagra.forkyhome.data.IconShape
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.SmartspaceTimeFormat
import dev.azagra.forkyhome.data.ThemeMode
import dev.azagra.forkyhome.notif.NotificationCounter
import dev.azagra.forkyhome.util.LockScreenService
import kotlin.math.roundToInt

private val SectionCardShape = RoundedCornerShape(26.dp)
private val OptionCardShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(50)

private enum class SettingsRoute {
    DASHBOARD,
    GENERAL,
    HOME_SCREEN,
    AT_A_GLANCE,
    APP_DRAWER,
    GESTURES,
    PRIVACY,
    ABOUT,
}

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
    var route by remember { mutableStateOf(SettingsRoute.DASHBOARD) }
    BackHandler(enabled = route != SettingsRoute.DASHBOARD) {
        route = SettingsRoute.DASHBOARD
    }

    val hasNotifService = remember {
        ctx.packageManager.resolveService(Intent(ctx, NotificationCounter::class.java), 0) != null
    }
    var notifEnabled by remember { mutableStateOf(notificationAccessGranted(ctx)) }
    var accessibilityEnabled by remember { mutableStateOf(LockScreenService.isEnabled(ctx)) }
    var calendarPermGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    LifecycleResumeEffect(Unit) {
        notifEnabled = notificationAccessGranted(ctx)
        accessibilityEnabled = LockScreenService.isEnabled(ctx)
        calendarPermGranted =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calendarPermGranted = granted
        if (granted) {
            onChange { it.copy(smartspaceShowCalendar = true) }
        }
    }

    var alpha by remember(s.drawerAlpha) { mutableFloatStateOf(s.drawerAlpha) }
    var homeIconScale by remember(s.homeIconScale) { mutableFloatStateOf(s.homeIconScale) }
    var drawerIconScale by remember(s.drawerIconScale) { mutableFloatStateOf(s.drawerIconScale) }
    var packDialog by remember { mutableStateOf(false) }
    var accessibilityDisclosureDialog by remember { mutableStateOf(false) }

    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val (versionName, versionCode) = remember {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (info.versionName ?: "0.1.0") to info.longVersionCode
        }.getOrDefault("0.1.0" to 1L)
    }

    val topTitle = when (route) {
        SettingsRoute.DASHBOARD -> stringResource(R.string.settings_title)
        SettingsRoute.GENERAL -> stringResource(R.string.cat_general)
        SettingsRoute.HOME_SCREEN -> stringResource(R.string.section_home_screen)
        SettingsRoute.AT_A_GLANCE -> stringResource(R.string.settings_at_a_glance)
        SettingsRoute.APP_DRAWER -> stringResource(R.string.section_app_drawer)
        SettingsRoute.GESTURES -> stringResource(R.string.cat_gestures)
        SettingsRoute.PRIVACY -> stringResource(R.string.cat_privacy)
        SettingsRoute.ABOUT -> stringResource(R.string.cat_about)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            topTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (route == SettingsRoute.DASHBOARD) onClose()
                                else route = SettingsRoute.DASHBOARD
                            },
                        ) {
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
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    when {
                        targetState == SettingsRoute.PRIVACY -> {
                            (scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) + fadeIn(tween(240))) togetherWith
                                (scaleOut(targetScale = 1.04f, animationSpec = tween(180)) + fadeOut(tween(180)))
                        }
                        initialState == SettingsRoute.PRIVACY -> {
                            (scaleIn(initialScale = 1.04f, animationSpec = tween(220)) + fadeIn(tween(220))) togetherWith
                                (scaleOut(targetScale = 0.94f, animationSpec = tween(180)) + fadeOut(tween(180)))
                        }
                        targetState != SettingsRoute.DASHBOARD -> {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 6 } + fadeOut())
                        }
                        else -> {
                            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 6 } + fadeOut())
                        }
                    }
                },
                label = "settingsRoute",
                modifier = Modifier.padding(pad),
            ) { currentRoute ->
                if (currentRoute == SettingsRoute.PRIVACY) {
                    PrivacyAppsScreen(
                        s = s,
                        allApps = allApps,
                        onChange = onChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (currentRoute) {
                        SettingsRoute.DASHBOARD -> {
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
                                            onClick = {
                                                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                            },
                                        ) {
                                            Text(stringResource(R.string.action_save))
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = SectionCardShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 2.dp,
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Star,
                                        title = stringResource(R.string.cat_general),
                                        subtitle = stringResource(R.string.cat_general_desc),
                                        onClick = { route = SettingsRoute.GENERAL },
                                    )
                                    CardDivider()
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Home,
                                        title = stringResource(R.string.section_home_screen),
                                        subtitle = stringResource(R.string.cat_home_desc),
                                        onClick = { route = SettingsRoute.HOME_SCREEN },
                                    )
                                    CardDivider()
                                    DashboardCategoryRow(
                                        icon = Icons.Default.DateRange,
                                        title = stringResource(R.string.settings_at_a_glance),
                                        subtitle = stringResource(R.string.settings_at_a_glance_desc),
                                        onClick = { route = SettingsRoute.AT_A_GLANCE },
                                    )
                                    CardDivider()
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Settings,
                                        title = stringResource(R.string.section_app_drawer),
                                        subtitle = stringResource(R.string.cat_drawer_desc),
                                        onClick = { route = SettingsRoute.APP_DRAWER },
                                    )
                                    CardDivider()
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Notifications,
                                        title = stringResource(R.string.cat_gestures),
                                        subtitle = stringResource(R.string.cat_gestures_desc),
                                        onClick = { route = SettingsRoute.GESTURES },
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = SectionCardShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 2.dp,
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Lock,
                                        title = stringResource(R.string.cat_privacy),
                                        subtitle = stringResource(
                                            R.string.settings_hidden_protected_desc,
                                            s.hiddenApps.size,
                                            s.protectedApps.size,
                                        ),
                                        onClick = {
                                            if (onAuthenticate != null) {
                                                onAuthenticate { route = SettingsRoute.PRIVACY }
                                            } else {
                                                route = SettingsRoute.PRIVACY
                                            }
                                        },
                                    )
                                    CardDivider()
                                    DashboardCategoryRow(
                                        icon = Icons.Default.Info,
                                        title = stringResource(R.string.cat_about),
                                        subtitle = stringResource(R.string.footer_version_build, versionName, versionCode),
                                        onClick = { route = SettingsRoute.ABOUT },
                                    )
                                }
                            }
                        }

                        SettingsRoute.GENERAL -> {
                            SectionGroup(
                                title = stringResource(R.string.section_appearance),
                                icon = Icons.Default.Star,
                            ) {
                                OptionPillsRow(
                                    options = listOf(
                                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                                    ),
                                    selected = s.themeMode,
                                    onSelect = { m -> onChange { it.copy(themeMode = m) } },
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
                                    subtitle = null,
                                    icon = Icons.Default.Edit,
                                    onClick = { openWallpaperPicker(ctx) },
                                )
                            }

                            SectionGroup(
                                title = stringResource(R.string.section_icons),
                                icon = Icons.Default.Star,
                            ) {
                                val packName = packs.firstOrNull { it.pkg == s.iconPack }?.label
                                    ?: stringResource(R.string.icon_pack_default)
                                ActionRow(
                                    title = stringResource(R.string.dialog_icon_pack_title),
                                    subtitle = packName,
                                    icon = Icons.Default.Star,
                                    onClick = { packDialog = true },
                                )
                                CardDivider()
                                Text(
                                    text = stringResource(R.string.settings_icon_shape),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    if (!notifEnabled) {
                                        FilledTonalButton(
                                            onClick = {
                                                ctx.startActivity(
                                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.settings_grant_notification_access))
                                        }
                                    }
                                }
                            }
                        }

                        SettingsRoute.HOME_SCREEN -> {
                            SectionGroup(
                                title = stringResource(R.string.section_home_screen),
                                icon = Icons.Default.Home,
                            ) {
                                StepperRow(
                                    title = stringResource(R.string.settings_home_columns),
                                    value = s.homeCols,
                                    range = 4..6,
                                    onValueChange = { c -> onChange { it.copy(homeCols = c) } },
                                )
                                CardDivider()
                                StepperRow(
                                    title = stringResource(R.string.settings_home_rows),
                                    value = s.homeRows,
                                    range = 4..7,
                                    onValueChange = { r -> onChange { it.copy(homeRows = r) } },
                                )
                                CardDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            stringResource(R.string.settings_home_icon_size),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${(homeIconScale * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = homeIconScale,
                                        onValueChange = { homeIconScale = it },
                                        onValueChangeFinished = { onChange { it.copy(homeIconScale = homeIconScale) } },
                                        valueRange = 0.80f..1.20f,
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
                            }
                        }

                        SettingsRoute.AT_A_GLANCE -> {
                            Surface(
                                shape = SectionCardShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChange { it.copy(showAtAGlance = !s.showAtAGlance) } }
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_at_a_glance_toggle),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = s.showAtAGlance,
                                        onCheckedChange = { v -> onChange { it.copy(showAtAGlance = v) } },
                                    )
                                }
                            }

                            if (s.showAtAGlance) {
                                SectionGroup(
                                    title = stringResource(R.string.settings_smartspace_preview),
                                    icon = Icons.Default.DateRange,
                                ) {
                                    Surface(
                                        shape = OptionCardShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        AtAGlanceHeader(s = s, previewMode = true)
                                    }
                                }

                                SectionGroup(
                                    title = stringResource(R.string.settings_smartspace_permissions),
                                    icon = Icons.Default.Lock,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(
                                                    if (calendarPermGranted) R.string.settings_calendar_permission_granted
                                                    else R.string.settings_smartspace_calendar
                                                ),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_smartspace_calendar_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (calendarPermGranted) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            FilledTonalButton(
                                                onClick = { calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                            ) {
                                                Text(stringResource(R.string.settings_grant_calendar_permission))
                                            }
                                        }
                                    }
                                }

                                SectionGroup(
                                    title = stringResource(R.string.settings_smartspace_what_to_show),
                                    icon = Icons.Default.Star,
                                ) {
                                    SwitchRow(
                                        title = stringResource(R.string.settings_smartspace_calendar),
                                        subtitle = stringResource(R.string.settings_smartspace_calendar_desc),
                                        checked = s.smartspaceShowCalendar && calendarPermGranted,
                                        onChecked = { v ->
                                            if (v && !calendarPermGranted) {
                                                calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                                            } else {
                                                onChange { it.copy(smartspaceShowCalendar = v) }
                                            }
                                        },
                                    )
                                    CardDivider()
                                    SwitchRow(
                                        title = stringResource(R.string.settings_smartspace_alarm),
                                        subtitle = null,
                                        checked = s.smartspaceShowAlarm,
                                        onChecked = { v -> onChange { it.copy(smartspaceShowAlarm = v) } },
                                    )
                                    CardDivider()
                                    SwitchRow(
                                        title = stringResource(R.string.settings_smartspace_battery),
                                        subtitle = null,
                                        checked = s.smartspaceShowBattery,
                                        onChecked = { v -> onChange { it.copy(smartspaceShowBattery = v) } },
                                    )
                                }

                                SectionGroup(
                                    title = stringResource(R.string.settings_smartspace_date_time),
                                    icon = Icons.Default.DateRange,
                                ) {
                                    val canToggleDate = !s.smartspaceShowDate || s.smartspaceShowTime
                                    val canToggleTime = !s.smartspaceShowTime || s.smartspaceShowDate
                                    SwitchRow(
                                        title = stringResource(R.string.settings_smartspace_show_date),
                                        subtitle = null,
                                        checked = s.smartspaceShowDate,
                                        onChecked = { v ->
                                            if (canToggleDate) onChange { it.copy(smartspaceShowDate = v) }
                                        },
                                    )
                                    CardDivider()
                                    SwitchRow(
                                        title = stringResource(R.string.settings_smartspace_show_time),
                                        subtitle = null,
                                        checked = s.smartspaceShowTime,
                                        onChecked = { v ->
                                            if (canToggleTime) onChange { it.copy(smartspaceShowTime = v) }
                                        },
                                    )
                                    CardDivider()
                                    Text(
                                        text = stringResource(R.string.settings_smartspace_time_format),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OptionPillsRow(
                                        options = listOf(
                                            SmartspaceTimeFormat.SYSTEM to stringResource(R.string.time_format_system),
                                            SmartspaceTimeFormat.TWELVE_HOUR to stringResource(R.string.time_format_12h),
                                            SmartspaceTimeFormat.TWENTY_FOUR_HOUR to stringResource(R.string.time_format_24h),
                                        ),
                                        selected = s.smartspaceTimeFormat,
                                        onSelect = { fmt -> onChange { it.copy(smartspaceTimeFormat = fmt) } },
                                    )
                                }
                            }
                        }

                        SettingsRoute.APP_DRAWER -> {
                            SectionGroup(
                                title = stringResource(R.string.section_app_drawer),
                                icon = Icons.Default.Settings,
                            ) {
                                StepperRow(
                                    title = stringResource(R.string.settings_columns),
                                    value = s.columns,
                                    range = 4..6,
                                    onValueChange = { c -> onChange { it.copy(columns = c) } },
                                )
                                CardDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            stringResource(R.string.settings_drawer_icon_size),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${(drawerIconScale * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = drawerIconScale,
                                        onValueChange = { drawerIconScale = it },
                                        onValueChangeFinished = { onChange { it.copy(drawerIconScale = drawerIconScale) } },
                                        valueRange = 0.80f..1.20f,
                                    )
                                }
                                CardDivider()
                                SwitchRow(
                                    title = stringResource(R.string.settings_show_labels),
                                    subtitle = null,
                                    checked = s.showLabels,
                                    onChecked = { v -> onChange { it.copy(showLabels = v) } },
                                )
                                CardDivider()
                                DrawerBgGrid(
                                    selected = s.drawerBg,
                                    onSelect = { b -> onChange { it.copy(drawerBg = b) } },
                                )
                                CardDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            stringResource(R.string.settings_bg_opacity),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${(alpha * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = alpha,
                                        onValueChange = { alpha = it },
                                        onValueChangeFinished = { onChange { it.copy(drawerAlpha = alpha) } },
                                        valueRange = 0.50f..1f,
                                    )
                                }
                            }
                        }

                        SettingsRoute.GESTURES -> {
                            SectionGroup(
                                title = stringResource(R.string.cat_gestures),
                                icon = Icons.Default.Notifications,
                            ) {
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
                                    onChecked = { v ->
                                        onChange { it.copy(doubleTapToSleep = v) }
                                        if (v && !accessibilityEnabled) {
                                            accessibilityDisclosureDialog = true
                                        }
                                    },
                                )
                                if (s.doubleTapToSleep) {
                                    if (accessibilityEnabled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_accessibility_enabled),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { accessibilityDisclosureDialog = true },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.settings_grant_accessibility_access))
                                        }
                                    }
                                }
                            }
                        }

                        SettingsRoute.ABOUT -> {
                            val repoUrl = stringResource(R.string.footer_repo_url)
                            val dm = ctx.resources.displayMetrics
                            val deviceModel = remember {
                                val mfg = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                                val mdl = Build.MODEL
                                if (mdl.startsWith(mfg, ignoreCase = true)) mdl else "$mfg $mdl"
                            }
                            val androidVersion = remember {
                                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                            }
                            val abi = remember {
                                Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
                            }
                            val displayInfo = remember(dm.widthPixels, dm.heightPixels, dm.densityDpi) {
                                "${dm.widthPixels} × ${dm.heightPixels} · ${dm.densityDpi} dpi"
                            }

                            Surface(
                                shape = SectionCardShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource(R.string.footer_version_build, versionName, versionCode),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                            }

                            SectionGroup(
                                title = stringResource(R.string.about_launcher_info),
                                icon = Icons.Default.Info,
                            ) {
                                InfoKeyValueRow(stringResource(R.string.about_version), versionName)
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_build), versionCode.toString())
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_package), ctx.packageName)
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_license), "GPL-3.0")
                            }

                            SectionGroup(
                                title = stringResource(R.string.about_device_info),
                                icon = Icons.Default.Settings,
                            ) {
                                InfoKeyValueRow(stringResource(R.string.about_model), deviceModel)
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_android), androidVersion)
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_abi), abi)
                                CardDivider()
                                InfoKeyValueRow(stringResource(R.string.about_display), displayInfo)
                            }

                            SectionGroup(
                                title = stringResource(R.string.about_source_code),
                                icon = GithubIcon,
                            ) {
                                val privacyUrl = stringResource(R.string.footer_privacy_url)
                                ActionRow(
                                    title = stringResource(R.string.footer_open_source),
                                    subtitle = repoUrl.removePrefix("https://"),
                                    icon = GithubIcon,
                                    onClick = {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                )
                                CardDivider()
                                ActionRow(
                                    title = stringResource(R.string.about_privacy_policy),
                                    subtitle = privacyUrl.removePrefix("https://"),
                                    icon = Icons.Default.Lock,
                                    onClick = {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        SettingsRoute.PRIVACY -> Unit
                    }
                    Spacer(Modifier.height(16.dp))
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

    if (accessibilityDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { accessibilityDisclosureDialog = false },
            shape = SectionCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.dialog_accessibility_title)) },
            text = { Text(stringResource(R.string.dialog_accessibility_desc)) },
            dismissButton = {
                TextButton(onClick = { accessibilityDisclosureDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        accessibilityDisclosureDialog = false
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
        )
    }
}

@Composable
private fun PrivacyAppsScreen(
    s: LauncherSettings,
    allApps: List<AppInfo>,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        revealed = true
    }

    val uniqueApps = remember(allApps, searchQuery, selectedTab, s.hiddenApps, s.protectedApps) {
        val base = allApps.distinctBy { it.pkg }
        val filtered = if (searchQuery.isBlank()) base else base.filter {
            it.label.contains(searchQuery, ignoreCase = true)
        }
        filtered.sortedByDescending { app ->
            if (selectedTab == 0) app.pkg in s.hiddenApps else app.pkg in s.protectedApps
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = SectionCardShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dialog_hidden_protected_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_hidden_protected_desc,
                            s.hiddenApps.size,
                            s.protectedApps.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                leadingIcon = if (selectedTab == 0) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                label = { Text("${stringResource(R.string.label_hidden)} (${s.hiddenApps.size})") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                leadingIcon = if (selectedTab == 1) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
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
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            } else null,
            shape = OptionCardShape,
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(260)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uniqueApps, key = { it.pkg }) { app ->
                    val isChecked = if (selectedTab == 0) app.pkg in s.hiddenApps else app.pkg in s.protectedApps
                    Surface(
                        shape = OptionCardShape,
                        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoKeyValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val GithubIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.475f, 2f, 2f, 6.475f, 2f, 12f)
            curveToRelative(0f, 4.425f, 2.8625f, 8.1625f, 6.8375f, 9.4875f)
            curveToRelative(0.5f, 0.0875f, 0.6875f, -0.2125f, 0.6875f, -0.475f)
            curveToRelative(0f, -0.2375f, -0.0125f, -1.025f, -0.0125f, -1.8625f)
            curveToRelative(-2.5125f, 0.4625f, -3.1625f, -0.6125f, -3.3625f, -1.175f)
            curveToRelative(-0.1125f, -0.2875f, -0.6f, -1.175f, -1.025f, -1.4125f)
            curveToRelative(-0.35f, -0.1875f, -0.85f, -0.65f, -0.0125f, -0.6625f)
            curveToRelative(0.7875f, -0.0125f, 1.35f, 0.725f, 1.5375f, 1.025f)
            curveToRelative(0.9f, 1.5125f, 2.3375f, 1.0875f, 2.9125f, 0.825f)
            curveToRelative(0.0875f, -0.65f, 0.35f, -1.0875f, 0.6375f, -1.3375f)
            curveToRelative(-2.225f, -0.25f, -4.55f, -1.1125f, -4.55f, -4.9375f)
            curveToRelative(0f, -1.0875f, 0.3875f, -1.9875f, 1.025f, -2.6875f)
            curveToRelative(-0.1f, -0.25f, -0.45f, -1.275f, 0.1f, -2.65f)
            curveToRelative(0f, 0f, 0.8375f, -0.2625f, 2.75f, 1.025f)
            curveToRelative(0.8f, -0.225f, 1.65f, -0.3375f, 2.5f, -0.3375f)
            curveToRelative(0.85f, 0f, 1.7f, 0.1125f, 2.5f, 0.3375f)
            curveToRelative(1.9125f, -1.3f, 2.75f, -1.025f, 2.75f, -1.025f)
            curveToRelative(0.55f, 1.375f, 0.2f, 2.4f, 0.1f, 2.65f)
            curveToRelative(0.6375f, 0.7f, 1.025f, 1.5875f, 1.025f, 2.6875f)
            curveToRelative(0f, 3.8375f, -2.3375f, 4.6875f, -4.5625f, 4.9375f)
            curveToRelative(0.3625f, 0.3125f, 0.675f, 0.9125f, 0.675f, 1.85f)
            curveToRelative(0f, 1.3375f, -0.0125f, 2.4125f, -0.0125f, 2.75f)
            curveToRelative(0f, 0.2625f, 0.1875f, 0.575f, 0.6875f, 0.475f)
            curveTo(19.1375f, 20.1625f, 22f, 16.4125f, 22f, 12f)
            curveToRelative(0f, -5.525f, -4.475f, -10f, -10f, -10f)
            close()
        }
    }.build()
}

@Composable
private fun DashboardCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
