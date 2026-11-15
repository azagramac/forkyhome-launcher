package dev.azagra.launcher_aosp.ui

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

/** progress: 0 = cerrado, 1 = abierto. Se arrastra con el dedo y se asienta con un muelle rápido sin rebote. */
@Stable
class DrawerState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    val isOpen: Boolean get() = progress > 0.001f
    private var job: Job? = null

    fun drag(delta: Float) {
        job?.cancel()
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    fun snapTo(target: Float) {
        job?.cancel()
        progress = target.coerceIn(0f, 1f)
    }

    /** velocity en "progreso por segundo"; positivo = abriendo. */
    fun settle(velocity: Float) {
        val target = when {
            velocity > 0.45f -> 1f
            velocity < -0.45f -> 0f
            progress > 0.45f -> 1f
            else -> 0f
        }
        animateTo(target, velocity)
    }

    fun animateTo(target: Float, initialVelocity: Float = 0f) {
        job?.cancel()
        if (progress == target) return
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = initialVelocity.coerceIn(-8f, 8f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { v, _ -> progress = v }
        }
    }
}
