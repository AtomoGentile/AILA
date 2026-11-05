package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private const val AcaHoldMillis = 650L

/** Observes unclaimed touches without stealing scrolling, controls or text selection. */
@Composable
fun Modifier.acaHoldToOpen(enabled: Boolean, surfaceKey: Any?, onOpen: () -> Unit): Modifier {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val progress = remember { Animatable(0f) }
    val open by rememberUpdatedState(onOpen)
    val haptics = LocalHapticFeedback.current
    val accent = AppTheme.PrimaryBlue
    val reduced = AppTheme.reduceMotion
    LaunchedEffect(origin) {
        progress.snapTo(0f)
        if (origin != null) progress.animateTo(1f, tween(AcaHoldMillis.toInt(), easing = LinearEasing))
    }
    return this.pointerInput(enabled, surfaceKey) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            // Clickable and editable children own their press from the first event.
            if (down.isConsumed || currentEvent.changes.count { it.pressed } != 1) return@awaitEachGesture
            origin = down.position
            try {
                val cancelled = withTimeoutOrNull(AcaHoldMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed ||
                            event.changes.count { it.pressed } != 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            return@withTimeoutOrNull true
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (cancelled == null) {
                    origin = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    open()
                    // Suppress a trailing tap if opening ACA leaves the source composed.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            } finally {
                origin = null
            }
        }
    }.drawWithContent {
        drawContent()
        origin?.let { point ->
            val radius = 25.dp.toPx()
            drawCircle(accent.copy(alpha = .18f), radius, point, style = Stroke(3.dp.toPx()))
            if (reduced) {
                drawCircle(accent, 5.dp.toPx(), point)
            } else {
                drawArc(accent, -90f, 360f * progress.value, false,
                    point - Offset(radius, radius), Size(radius * 2, radius * 2),
                    style = Stroke(3.dp.toPx()))
            }
        }
    }
}
