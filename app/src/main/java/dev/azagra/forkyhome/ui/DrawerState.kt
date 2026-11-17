package dev.azagra.forkyhome.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class DrawerState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    private var startProgress = 0f
    val isOpen: Boolean get() = progress > 0.01f
    private var job: Job? = null

    fun drag(delta: Float) {
        if (job?.isActive == true) {
            job?.cancel()
            startProgress = progress
        }
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    fun snapTo(target: Float) {
        job?.cancel()
        val clamped = target.coerceIn(0f, 1f)
        progress = clamped
        startProgress = clamped
    }

    fun settle(velocity: Float) {
        val threshold = if (startProgress < 0.5f) 0.25f else 0.75f
        val target = when {
            velocity > 0.22f -> 1f
            velocity < -0.22f -> 0f
            progress > threshold -> 1f
            else -> 0f
        }
        animateTo(target, velocity)
    }

    fun animateTo(target: Float, initialVelocity: Float = 0f) {
        job?.cancel()
        val clampedTarget = target.coerceIn(0f, 1f)
        if (progress == clampedTarget) {
            startProgress = clampedTarget
            return
        }
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = clampedTarget,
                initialVelocity = initialVelocity.coerceIn(-6f, 6f),
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 380f,
                    visibilityThreshold = 0.001f,
                ),
            ) { v, _ ->
                progress = v
            }
            startProgress = clampedTarget
        }
    }
}
