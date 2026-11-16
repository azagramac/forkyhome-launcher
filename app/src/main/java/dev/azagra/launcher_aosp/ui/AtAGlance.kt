package dev.azagra.launcher_aosp.ui

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AtAGlanceHeader(
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nextAlarmMillis by remember { mutableLongStateOf(readNextAlarmMillis(ctx)) }
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(ctx) {
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

        refreshState()

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
        ctx.registerReceiver(receiver, filter)
        onDispose {
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }

    val locale = Locale.getDefault()
    val dateText = remember(nowMillis, locale) {
        val raw = SimpleDateFormat("EEEE, d MMM", locale).format(Date(nowMillis))
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    val alarmText = remember(nextAlarmMillis, locale) {
        if (nextAlarmMillis > 0L) {
            SimpleDateFormat("EEE HH:mm", locale).format(Date(nextAlarmMillis))
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { openCalendar(ctx, nowMillis) }
                .padding(vertical = 2.dp, horizontal = 4.dp),
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = textShadow,
                ),
                color = Color.White,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (alarmText != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { openAlarms(ctx) },
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

            Surface(
                shape = CircleShape,
                color = if (isCharging) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { openBatterySettings(ctx) },
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
