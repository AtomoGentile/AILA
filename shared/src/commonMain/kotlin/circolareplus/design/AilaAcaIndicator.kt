package circolareplus.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A soft contextual perimeter; ordinary chat never activates it. */
@Composable fun AilaAcaIndicator(active: Boolean) {
    val reduced = AppTheme.reduceMotion
    val opacity = animateFloatAsState(
        if(active) 1f else 0f,
        tween(if(reduced) 80 else if(active) 280 else 120, easing = LinearOutSlowInEasing),
        label = "aca perimeter"
    )
    if(!active && opacity.value == 0f) return
    val colors = listOf(Color(0xFF865BE0), Color(0xFF4D77E8), Color(0xFF36B6B4), Color(0xFFD1B16D), Color(0xFFDA9476), Color(0xFFD26C9E))
    val travel = if(reduced) null else rememberInfiniteTransition(label = "aca light").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6800, easing = LinearEasing)), label = "aca travel"
    )
    val radius = maxOf(circolareplus.platform.displayCornerRadius(), 28.dp)
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "ACA attiva · bordo luminoso" }) {
        // Read animated state in the draw phase, keeping the popup out of frame-by-frame recomposition.
        val position = travel?.value ?: 0f
        val shifted = List(13) { i ->
            val sample = (i / 12f + position) * colors.size
            val base = kotlin.math.floor(sample).toInt()
            androidx.compose.ui.graphics.lerp(colors[base % colors.size], colors[(base + 1) % colors.size], sample - base)
        }
        val inset = 2.dp.toPx()
        val brush = Brush.sweepGradient(shifted, Offset(size.width / 2, size.height / 2))
        val glow = opacity.value
        for(width in if(AppTheme.liteEffects) listOf(2.2f) else listOf(28f, 16f, 8f, 2.2f)) {
            drawRoundRect(brush, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(radius.toPx()),
                alpha = glow * when(width) { 2.2f -> .82f; 8f -> .18f; 16f -> .09f; else -> .045f }, style = Stroke(width.dp.toPx()))
        }
    }
}
