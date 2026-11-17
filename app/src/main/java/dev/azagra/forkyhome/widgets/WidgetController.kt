package dev.azagra.forkyhome.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

const val WIDGET_HOST_ID = 1024
private const val REQ_CONFIGURE = 4242
private const val REQ_RECONFIGURE = 4243

class LongPressHostView(ctx: Context) : AppWidgetHostView(ctx) {
    var lastReportedSize: Pair<Float, Float>? = null
    var onLongPressStart: ((touchOffsetInWidget: Offset, rawPos: Offset) -> Unit)? = null
    var onDragMove: ((rawDelta: Offset) -> Unit)? = null
    var onDragFinish: (() -> Unit)? = null
    var onLongPressRelease: (() -> Unit)? = null

    private var fired = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f

    private val longPressRunnable = Runnable {
        if (!isAttachedToWindow) return@Runnable
        fired = true
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()

        onLongPressStart?.invoke(Offset(downX, downY), Offset(downRawX, downRawY))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPressRunnable)
                fired = false
                dragging = false
                downX = ev.x
                downY = ev.y
                downRawX = ev.rawX
                downRawY = ev.rawY
                val timeout = (ViewConfiguration.getLongPressTimeout() * 0.75f).toLong()
                postDelayed(longPressRunnable, timeout)
                super.dispatchTouchEvent(ev)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                val dist = hypot(dx, dy)
                if (!fired) {
                    if (dist > slop) {
                        removeCallbacks(longPressRunnable)
                    }
                    return super.dispatchTouchEvent(ev)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!dragging && dist > slop * 0.4f) {
                    dragging = true
                }
                if (dragging) {
                    onDragMove?.invoke(Offset(dx, dy))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (fired) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val wasDragging = dragging
                    fired = false
                    dragging = false
                    if (wasDragging) {
                        onDragFinish?.invoke()
                    } else {
                        onLongPressRelease?.invoke()
                    }
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

class LauncherWidgetHost(ctx: Context) : AppWidgetHost(ctx, WIDGET_HOST_ID) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LongPressHostView(context)
}

class WidgetController(
    private val activity: ComponentActivity,
    private val onAdded: (id: Int, info: AppWidgetProviderInfo) -> Unit,
) {
    val host = LauncherWidgetHost(activity.applicationContext)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val viewCache = HashMap<Int, LongPressHostView>()

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    fun getOrCreateView(ctx: Context, appWidgetId: Int): LongPressHostView {
        val cached = viewCache[appWidgetId]
        if (cached != null) {
            (cached.parent as? android.view.ViewGroup)?.removeView(cached)
            return cached
        }
        val info = manager.getAppWidgetInfo(appWidgetId)
        val created = (host.createView(ctx, appWidgetId, info) as LongPressHostView).apply {
            clipToPadding = false
        }
        viewCache[appWidgetId] = created
        return created
    }

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

    fun canReconfigure(appWidgetId: Int): Boolean {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return false
        return info.configure != null &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    fun reconfigure(appWidgetId: Int) {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure != null) {
            runCatching {
                host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQ_RECONFIGURE, null)
            }
        }
    }

    private fun configureOrFinish() {
        val info = pendingInfo ?: return
        val isOptional = (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !isOptional) {
            runCatching {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
            }.onFailure {
                finish()
            }
        } else {
            finish()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQ_RECONFIGURE) return
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

    fun delete(id: Int) {
        viewCache.remove(id)?.let { v ->
            (v.parent as? android.view.ViewGroup)?.removeView(v)
        }
        host.deleteAppWidgetId(id)
    }
}
