package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Rear-light silhouette: camera arc, broken coil, diagonal strip and charging stem. */
@Composable
fun NothingGlyphMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    active: Boolean = true,
    accent: Color = AppTheme.PrimaryBlue,
    progress: Float = 1f
) {
    Canvas(modifier.size(size).semantics { contentDescription = "Glyph AILA" }) {
        val s = this.size.minDimension
        val ox = (this.size.width - s) / 2f
        val oy = (this.size.height - s) / 2f
        fun point(x: Float, y: Float) = Offset(ox + s*x, oy + s*y)
        val off = Color(0xFF414141)
        val on = if (active) Color(0xFFF2F2EC) else off
        val stroke = (s * .037f).coerceAtLeast(1.2.dp.toPx())
        fun segment(a: Offset, b: Offset, lit: Boolean = true) {
            if (active && lit) drawLine(Color.White.copy(alpha = .08f), a, b, stroke*2.8f, StrokeCap.Round)
            drawLine(if (lit) on else off, a, b, stroke, StrokeCap.Round)
        }
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, steps: Int) {
            repeat(steps) { i ->
                val a = (start + sweep*i/steps)*PI.toFloat()/180f
                val b = (start + sweep*(i+1)/steps)*PI.toFloat()/180f
                segment(point(cx+r*cos(a),cy+r*sin(a)),point(cx+r*cos(b),cy+r*sin(b)), i.toFloat()/steps < progress)
            }
        }
        // Camera halo and rear charging coil; separated tracks rather than a generic cross.
        arc(.25f,.22f,.12f,45f,270f,24)
        arc(.50f,.49f,.25f,48f,264f,44)
        segment(point(.74f,.17f), point(.85f,.28f), progress > .5f)
        segment(point(.50f,.78f), point(.50f,.93f), progress > .15f)
        drawCircle(on, s*.023f, point(.50f,.99f))
        drawCircle(accent, s*.018f, point(.77f,.085f))
    }
}
