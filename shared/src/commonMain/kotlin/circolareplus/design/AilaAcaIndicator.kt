package circolareplus.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Diffuse light follows the edges, with broader clouds around the lower corners. */
@Composable fun AilaAcaIndicator(active: Boolean) {
    val reduced = AppTheme.reduceMotion
    val opacity = animateFloatAsState(
        if(active) 1f else 0f,
        tween(if(reduced) 80 else if(active) 360 else 120, easing = LinearOutSlowInEasing),
        label = "aca ambient light"
    )
    if(!active && opacity.value == 0f) return
    val travel = if(reduced) null else rememberInfiniteTransition(label = "aca light").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "aca drift"
    )
    val radius = maxOf(circolareplus.platform.displayCornerRadius(), 28.dp)
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "ACA attiva · bordo luminoso" }) {
        val phase = (travel?.value ?: 0f) * 6.2831855f
        fun wave(offset:Float) = (sin(phase + offset) + 1f) * .5f
        val gain = opacity.value * if(reduced) 1f else .90f + .10f * wave(1f)
        val blue = Color(0xFF668BFF)
        val violet = Color(0xFFA27AF7)
        val teal = Color(0xFF49CFA7)
        val rose = Color(0xFFDE8BAF)
        val peach = Color(0xFFF2AD83)
        val width = size.width
        val height = size.height
        // Scaled radial gradients create a soft falloff without blur filters or stacked outlines.
        fun cloud(x:Float,y:Float,rx:Float,ry:Float,color:Color,strength:Float) {
            withTransform({ translate(x,y); scale(rx,ry,pivot=Offset.Zero) }) {
                drawCircle(brush = Brush.radialGradient(
                    0f to color.copy(alpha = strength * gain),
                    .23f to color.copy(alpha = strength * gain * .74f),
                    .52f to color.copy(alpha = strength * gain * .28f),
                    .78f to color.copy(alpha = strength * gain * .055f),
                    1f to color.copy(alpha = 0f),
                    center = Offset.Zero, radius = 1f
                ), radius = 1f, center = Offset.Zero)
            }
        }
        val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero,size), CornerRadius(radius.toPx()))) }
        clipPath(outline) {
            val side = minOf(70.dp.toPx(), width * .18f)
            cloud(-side * .16f, height * (.12f + .70f * wave(0f)), side, height * .36f, lerp(blue,violet,wave(2f)), .68f)
            cloud(width + side * .13f, height * (.12f + .70f * wave(2.5f)), side, height * .36f, lerp(blue,teal,wave(1f)), .74f)
            val lower = minOf(160.dp.toPx(), width * .40f)
            cloud(-lower * .12f, height - 30.dp.toPx() - 85.dp.toPx() * wave(1f), lower, lower * 1.65f, violet, .82f)
            cloud(width + lower * .10f, height - 34.dp.toPx() - 90.dp.toPx() * wave(3f), lower, lower * 1.55f, lerp(blue,violet,wave(3f)), .76f)
            cloud(-10.dp.toPx(), height + 8.dp.toPx(), lower * .63f, lower * .95f, lerp(peach,rose,wave(4f)), .72f)
            cloud(width * (.15f + .70f * wave(4f)), height + 22.dp.toPx(), width * .70f, 95.dp.toPx(), lerp(violet,blue,wave(5f)), .54f)
            cloud(width * (.08f + .84f * wave(3f)), -15.dp.toPx(), width * .50f, 52.dp.toPx(), blue, .32f)
            cloud(width * (.08f + .84f * wave(0f)), -12.dp.toPx(), width * .38f, 46.dp.toPx(), teal, .30f)
        }
    }
}
