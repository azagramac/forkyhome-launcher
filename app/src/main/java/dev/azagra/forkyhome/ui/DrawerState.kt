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
        val target = if (startProgress < 0.5f) {
            when {
                velocity < -0.25f -> 0f
                velocity > 0.08f -> 1f
                progress > 0.10f -> 1f
                else -> 0f
            }
        } else {
            when {
                velocity > 0.25f -> 1f
                velocity < -0.08f -> 0f
                progress < 0.88f -> 0f
                else -> 1f
            }
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
