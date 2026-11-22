package circolareplus.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class GlassPageFrame(
    val progress: Float,
    val direction: Float,
    val followsPager: Boolean,
    val dragAnchorY: Float? = null
)

internal class GlassPageMotion(val frame: () -> GlassPageFrame?) {
    private val top = mutableFloatStateOf(0f)
    private val height = mutableFloatStateOf(1f)
    val width = mutableFloatStateOf(0f)

    fun updateBounds(coordinates: LayoutCoordinates) {
        top.floatValue = coordinates.positionInRoot().y
        height.floatValue = coordinates.size.height.toFloat().coerceAtLeast(1f)
        width.floatValue = coordinates.size.width.toFloat()
    }

    fun stagedProgress(frame: GlassPageFrame, centerY: Float, halfHeight: Float): Float {
        if (frame.followsPager) {
            val anchor = frame.dragAnchorY ?: return frame.progress
            // La superficie che contiene il tocco segue il pager a velocità piena.
            val distance = (abs(centerY - anchor) - halfHeight).coerceAtLeast(0f)
            val delay = 0.32f * (distance / height.floatValue).coerceAtMost(1f)
            return ((frame.progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        }
        val row = ((centerY - top.floatValue) / height.floatValue).coerceIn(0f, 1f)
        val start = 0.32f * row
        val end = 0.82f + 0.18f * row
        return ((frame.progress - start) / (end - start)).coerceIn(0f, 1f)
    }
}

internal val LocalGlassPageMotion = compositionLocalOf<GlassPageMotion?> { null }

@Composable
internal fun Modifier.glassPageMotion(): Modifier {
    val motion = LocalGlassPageMotion.current ?: return this
    val centerY = remember { mutableFloatStateOf(0f) }
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    return onGloballyPositioned {
        centerY.floatValue = it.positionInRoot().y + it.size.height / 2f
    }.graphicsLayer {
        val frame = motion.frame()
        translationX = if (frame == null) 0f else {
            val localProgress = motion.stagedProgress(frame, centerY.floatValue, size.height / 2f)
            val width = motion.width.floatValue
            // Il pager si muove già durante lo swipe. Compensiamo solo il ritardo della card.
            val offset = if (frame.followsPager) {
                (frame.progress - localProgress) * width
            } else {
                (1f - localProgress) * minOf(width * 0.30f, 112.dp.toPx())
            }
            layoutSign * frame.direction * offset
        }
    }
}
