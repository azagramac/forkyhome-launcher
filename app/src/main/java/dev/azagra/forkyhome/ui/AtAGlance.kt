package dev.azagra.forkyhome.ui

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.azagra.forkyhome.data.LauncherSettings
import dev.azagra.forkyhome.data.SmartspaceTimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NextCalendarEvent(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtAGlanceHeader(
    s: LauncherSettings = LauncherSettings(),
    previewMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nextAlarmMillis by remember { mutableLongStateOf(readNextAlarmMillis(ctx)) }
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(ctx, lifecycleOwner) {
        fun refreshState(action: String? = null, batteryIntent: Intent? = null) {
            if (action == null || action != Intent.ACTION_BATTERY_CHANGED) {
                nowMillis = System.currentTimeMillis()
            }
            if (action == null ||
                action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED
            ) {
                nextAlarmMillis = readNextAlarmMillis(ctx)
            }
            if (action == null || action == Intent.ACTION_BATTERY_CHANGED) {
                val bIntent = batteryIntent ?: ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (bIntent != null) {
                    val level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                    }
                    val status = bIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshState(
                    action = intent?.action,
                    batteryIntent = intent?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED },
                )
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        var registered = false
        fun register() {
            if (!registered) {
                refreshState()
                ctx.registerReceiver(receiver, filter)
                registered = true
            }
        }
        fun unregister() {
            if (registered) {
                runCatching { ctx.unregisterReceiver(receiver) }
                registered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            register()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    val hasCalendarPerm = remember(ctx, nowMillis) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val nextEvent by produceState<NextCalendarEvent?>(null, s.smartspaceShowCalendar, hasCalendarPerm, nowMillis / 60_000L) {
        value = if (s.smartspaceShowCalendar && hasCalendarPerm) {
            withContext(Dispatchers.IO) { readNextCalendarEvent(ctx, nowMillis) }
        } else {
            null
        }
    }

    val locale = Locale.getDefault()
    val use24Hour = when (s.smartspaceTimeFormat) {
        SmartspaceTimeFormat.SYSTEM -> DateFormat.is24HourFormat(ctx)
        SmartspaceTimeFormat.TWELVE_HOUR -> false
        SmartspaceTimeFormat.TWENTY_FOUR_HOUR -> true
    }
    val dateText = remember(nowMillis, locale) {
        val raw = SimpleDateFormat("EEEE, d MMM", locale).format(Date(nowMillis))
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    val timeText = remember(nowMillis, locale, use24Hour) {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        SimpleDateFormat(pattern, locale).format(Date(nowMillis))
    }
    val headerText = remember(s.smartspaceShowDate, s.smartspaceShowTime, dateText, timeText) {
        buildList {
            if (s.smartspaceShowTime) add(timeText)
            if (s.smartspaceShowDate) add(dateText)
        }.joinToString(" · ")
    }
    val alarmText = remember(nextAlarmMillis, locale, use24Hour) {
        if (nextAlarmMillis > 0L) {
            val pattern = if (use24Hour) "EEE HH:mm" else "EEE h:mm a"
            SimpleDateFormat(pattern, locale).format(Date(nextAlarmMillis))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        } else null
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.55f),
            offset = Offset(0f, 2f),
            blurRadius = 6f,
        )
    }
    val primaryTextColor = if (previewMode) MaterialTheme.colorScheme.onSurface else Color.White
    val primaryTextStyle = if (previewMode) {
        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, shadow = textShadow)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (previewMode) 12.dp else 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (headerText.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !previewMode) { openCalendar(ctx, nowMillis) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = headerText,
                    style = primaryTextStyle,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val showAnyChip = (s.smartspaceShowCalendar && nextEvent != null) ||
            (s.smartspaceShowAlarm && alarmText != null) ||
            s.smartspaceShowBattery

        if (showAnyChip) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (s.smartspaceShowCalendar && nextEvent != null) {
                    val ev = nextEvent!!
                    val evTime = remember(ev.beginMillis, locale, use24Hour) {
                        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
                        SimpleDateFormat(pattern, locale).format(Date(ev.beginMillis))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.88f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !previewMode) { openCalendarEvent(ctx, ev.eventId, ev.beginMillis) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${ev.title} · $evTime",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (s.smartspaceShowAlarm && alarmText != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !previewMode) { openAlarms(ctx) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = alarmText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                if (s.smartspaceShowBattery) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCharging) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !previewMode) { openBatterySettings(ctx) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val prefix = if (isCharging) "⚡ " else ""
                            Text(
                                text = "$prefix$batteryPct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isCharging) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readNextCalendarEvent(ctx: Context, nowMillis: Long): NextCalendarEvent? = runCatching {
    val endMillis = nowMillis + 12 * 60 * 60 * 1000L
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, nowMillis)
    ContentUris.appendId(builder, endMillis)
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY,
    )
    ctx.contentResolver.query(
        builder.build(),
        projection,
        "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.END} >= ?",
        arrayOf(nowMillis.toString()),
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val title = cursor.getString(1)?.trim()
            if (!title.isNullOrEmpty()) {
                return@runCatching NextCalendarEvent(
                    eventId = cursor.getLong(0),
                    title = title,
                    beginMillis = cursor.getLong(2),
                )
            }
        }
        null
    }
}.getOrNull()

private fun readNextAlarmMillis(ctx: Context): Long = runCatching {
    val am = ctx.getSystemService(AlarmManager::class.java)
    am?.nextAlarmClock?.triggerTime ?: 0L
}.getOrDefault(0L)

private fun openCalendar(ctx: Context, timeMillis: Long) {
    runCatching {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, timeMillis)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }.onFailure {
        runCatching {
            val fallback = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(fallback)
        }
    }
}

private fun openCalendarEvent(ctx: Context, eventId: Long, beginMillis: Long) {
    runCatching {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }.onFailure {
        openCalendar(ctx, beginMillis)
    }
}

private fun openAlarms(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openBatterySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
