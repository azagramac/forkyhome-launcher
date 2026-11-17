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
        counts.value = active
            .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .groupingBy { it.packageName }
            .eachCount()
    }
}
