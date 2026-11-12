package circolareplus.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One interactive coordinate. Drag writes synchronously; only settling owns an animation. */
internal class CalendarMorphState(initialTimetable: Boolean = false) {
    var progress by mutableFloatStateOf(if (initialTimetable) 1f else 0f)
        private set
    var timetable by mutableStateOf(initialTimetable)
        private set
    private var settling: Job? = null
    private var dragOrigin = initialTimetable

    fun beginDrag() {
        settling?.cancel()
        dragOrigin = timetable
    }

    fun drag(delta: Float) { progress = (progress + delta).coerceIn(0f, 1f) }

    fun finish(scope: CoroutineScope, velocity: Float, reduceMotion: Boolean, cancelled: Boolean = false) {
        settle(scope, if (cancelled) dragOrigin else morphDestination(progress, velocity), reduceMotion)
    }

    fun settle(scope: CoroutineScope, toTimetable: Boolean, reduceMotion: Boolean) {
        settling?.cancel()
        timetable = toTimetable
        val destination = if (toTimetable) 1f else 0f
        if (reduceMotion) { progress = destination; return }
        settling = scope.launch {
            Animatable(progress).animateTo(destination, spring(dampingRatio = 1f, stiffness = 550f)) {
                progress = value.coerceIn(0f, 1f)
            }
        }
    }

    companion object {
        // Restore a stable destination, never a half-drag after recreation.
        val saver = Saver<CalendarMorphState, Boolean>(save = { it.timetable }, restore = { CalendarMorphState(it) })
    }
}

/** Velocity is in progress units / second, making the fling threshold density independent. */
internal fun morphDestination(progress: Float, velocity: Float): Boolean = when {
    velocity >= 2.5f -> true
    velocity <= -2.5f -> false
    else -> progress >= 0.5f
}
