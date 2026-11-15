package dev.azagra.launcher_aosp.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.hypot

const val WIDGET_HOST_ID = 1024
private const val REQ_CONFIGURE = 4242

/** Vista de widget con detección de pulsación larga (para poder quitarlo). */
class LongPressHostView(ctx: Context) : AppWidgetHostView(ctx) {
    var onLongPress: (() -> Unit)? = null
    private var fired = false
    private var downX = 0f
    private var downY = 0f
    private val longPress = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (hypot(ev.x - downX, ev.y - downY) > ViewConfiguration.get(context).scaledTouchSlop)
                    removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = fired || super.onTouchEvent(ev)
}

class LauncherWidgetHost(ctx: Context) : AppWidgetHost(ctx, WIDGET_HOST_ID) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LongPressHostView(context)
}

/**
 * Flujo completo de añadir un widget: reservar id → pedir permiso de bind →
 * pantalla de configuración (si la tiene) → avisar a la UI.
 */
class WidgetController(
    private val activity: ComponentActivity,
    private val onAdded: (id: Int, info: AppWidgetProviderInfo) -> Unit,
) {
    val host = LauncherWidgetHost(activity.applicationContext)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    private val bindLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) configureOrFinish() else cancel()
        }

    fun request(info: AppWidgetProviderInfo) {
        cancel()
        pendingInfo = info
        pendingId = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(pendingId, info.provider)) {
            configureOrFinish()
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            )
        }
    }

    private fun configureOrFinish() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
        } else {
            finish()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_CONFIGURE) return
        if (resultCode == Activity.RESULT_OK) finish() else cancel()
    }

    private fun finish() {
        val info = pendingInfo ?: return
        val id = pendingId
        pendingId = -1
        pendingInfo = null
        onAdded(id, info)
    }

    private fun cancel() {
        if (pendingId != -1) host.deleteAppWidgetId(pendingId)
        pendingId = -1
        pendingInfo = null
    }

    fun delete(id: Int) = host.deleteAppWidgetId(id)
}
