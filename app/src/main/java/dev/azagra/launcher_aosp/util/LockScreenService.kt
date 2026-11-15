package dev.azagra.launcher_aosp.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class LockScreenService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: LockScreenService? = null

        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(ctx, LockScreenService::class.java)
            val enabledServices = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabledServices
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expected }
        }

        fun lockScreen(ctx: Context): Boolean {
            val svc = instance
            if (svc != null && svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
                return true
            }
            val pmSuccess = runCatching {
                val pm = ctx.getSystemService(PowerManager::class.java) ?: return@runCatching false
                val method = pm.javaClass.getMethod("goToSleep", Long::class.javaPrimitiveType)
                method.invoke(pm, SystemClock.uptimeMillis())
                true
            }.getOrDefault(false)
            return pmSuccess
        }
    }
}
