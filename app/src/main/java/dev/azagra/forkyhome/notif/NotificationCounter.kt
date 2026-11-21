package dev.azagra.forkyhome.notif

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationCounter : NotificationListenerService() {

    companion object {
        val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    }

    override fun onListenerConnected() = recompute()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recompute()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recompute()
    override fun onListenerDisconnected() {
        counts.value = emptyMap()
    }

    private fun recompute() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val nonOngoing = active.filter { !it.isOngoing }
        val groupsWithChildren = nonOngoing
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNullTo(HashSet()) { it.groupKey }

        counts.value = nonOngoing
            .filter { sbn ->
                val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                !isSummary || sbn.groupKey !in groupsWithChildren
            }
            .groupBy { it.packageName }
            .mapValues { (_, list) ->
                list.sumOf { it.notification.number.coerceAtLeast(1) }
            }
    }
}
