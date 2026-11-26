package dev.azagra.forkyhome.notif

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationCounter : NotificationListenerService() {

    private data class Entry(
        val pkg: String,
        val groupKey: String?,
        val isSummary: Boolean,
        val count: Int,
    )

    companion object {
        val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

        private val entries = ConcurrentHashMap<String, Entry>()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val rebindExecutor = Executors.newSingleThreadExecutor()

        @Volatile
        private var uiVisible: Boolean = true

        @Volatile
        private var dirty: Boolean = false

        @Volatile
        private var badgesEnabled: Boolean = true

        @Volatile
        private var unbindPending: Boolean = false

        @Volatile
        private var instance: NotificationCounter? = null

        private var fallbackRebindRunnable: Runnable? = null

        fun setUiVisible(visible: Boolean) {
            uiVisible = visible
            if (visible && dirty) {
                dirty = false
                publishCounts()
            }
        }

        fun syncBindState(ctx: Context, enabled: Boolean) {
            val appCtx = ctx.applicationContext
            badgesEnabled = enabled
            fallbackRebindRunnable?.let { mainHandler.removeCallbacks(it) }
            fallbackRebindRunnable = null

            if (!enabled) {
                entries.clear()
                dirty = false
                if (counts.value.isNotEmpty()) {
                    counts.value = emptyMap()
                }
                val svc = instance
                if (svc != null) {
                    unbindPending = true
                    runCatching { svc.requestUnbind() }
                }
                return
            }

            // enabled == true
            val svc = instance
            if (svc != null && !unbindPending) {
                svc.seedFromActiveNotifications()
                return
            }
            if (!hasListenerAccess(appCtx)) return

            requestRebindWithFallback(appCtx)
        }

        private fun requestRebindWithFallback(appCtx: Context) {
            val cn = ComponentName(appCtx, NotificationCounter::class.java)
            rebindExecutor.execute {
                ensureComponentEnabled(appCtx, cn)
                runCatching { requestRebind(cn) }
            }
            val checkRunnable = Runnable {
                if (badgesEnabled && instance == null && hasListenerAccess(appCtx)) {
                    // Fallback para ROMs OEM / reinicios de proceso donde requestRebind por sí solo no reconecta.
                    rebindExecutor.execute {
                        forceComponentToggleRebind(appCtx, cn)
                    }
                }
            }
            fallbackRebindRunnable = checkRunnable
            mainHandler.postDelayed(checkRunnable, 800L)
        }

        private fun ensureComponentEnabled(appCtx: Context, cn: ComponentName) {
            runCatching {
                val pm = appCtx.packageManager
                val state = pm.getComponentEnabledSetting(cn)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }

        private fun forceComponentToggleRebind(appCtx: Context, cn: ComponentName) {
            runCatching {
                val pm = appCtx.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                requestRebind(cn)
            }
        }

        private fun hasListenerAccess(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(ctx, NotificationCounter::class.java)
            return flat.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expected }
        }

        private fun markChanged() {
            if (uiVisible) {
                dirty = false
                publishCounts()
            } else {
                dirty = true
            }
        }

        private fun publishCounts() {
            if (!badgesEnabled || entries.isEmpty()) {
                if (counts.value.isNotEmpty()) {
                    counts.value = emptyMap()
                }
                return
            }
            val snapshot = entries.values.toList()
            val groupsWithChildren = HashSet<String>()
            for (e in snapshot) {
                if (!e.isSummary && e.groupKey != null) {
                    groupsWithChildren.add(e.groupKey)
                }
            }
            val next = HashMap<String, Int>()
            for (e in snapshot) {
                if (!e.isSummary || e.groupKey == null || e.groupKey !in groupsWithChildren) {
                    next[e.pkg] = (next[e.pkg] ?: 0) + e.count
                }
            }
            if (counts.value != next) {
                counts.value = next
            }
        }

        private fun toEntry(sbn: StatusBarNotification): Entry {
            val flags = sbn.notification.flags
            val isSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val count = sbn.notification.number.coerceAtLeast(1)
            return Entry(
                pkg = sbn.packageName,
                groupKey = sbn.groupKey,
                isSummary = isSummary,
                count = count,
            )
        }
    }

    override fun onListenerConnected() {
        instance = this
        unbindPending = false
        fallbackRebindRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRebindRunnable = null
        if (!badgesEnabled) {
            unbindPending = true
            runCatching { requestUnbind() }
            return
        }
        seedFromActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!badgesEnabled || sbn == null) return
        if (sbn.isOngoing) {
            if (entries.remove(sbn.key) != null) {
                markChanged()
            }
            return
        }
        val next = toEntry(sbn)
        val prev = entries.put(sbn.key, next)
        if (prev != next) {
            markChanged()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (entries.remove(sbn.key) != null) {
            markChanged()
        }
    }

    override fun onListenerDisconnected() {
        val wasUnbindPending = unbindPending
        unbindPending = false
        if (instance === this) {
            instance = null
        }
        entries.clear()
        dirty = false
        counts.value = emptyMap()

        // Si el usuario reactivó los globos mientras requestUnbind() aún estaba en vuelo, reconectar al instante.
        if (badgesEnabled && wasUnbindPending && hasListenerAccess(applicationContext)) {
            requestRebindWithFallback(applicationContext)
        }
    }

    private fun seedFromActiveNotifications() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        entries.clear()
        for (sbn in active) {
            if (!sbn.isOngoing) {
                entries[sbn.key] = toEntry(sbn)
            }
        }
        markChanged()
    }
}
