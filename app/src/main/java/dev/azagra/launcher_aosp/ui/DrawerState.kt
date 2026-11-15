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

/** progress: 0 = cerrado, 1 = abierto. Se arrastra con el dedo y se asienta con un muelle. */
@Stable
class DrawerState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    val isOpen: Boolean get() = progress > 0f
    private var job: Job? = null

    fun drag(delta: Float) {
        job?.cancel()
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    /** velocity en "progreso por segundo"; positivo = abriendo. */
    fun settle(velocity: Float) {
        animateTo(
            when {
                velocity > 0.6f -> 1f
                velocity < -0.6f -> 0f
                progress > 0.5f -> 1f
                else -> 0f
            }
        )
    }

    fun animateTo(target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                progress, target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            ) { v, _ -> progress = v }
        }
    }
}
