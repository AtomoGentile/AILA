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

/** Visible immediately on contextual invocation and throughout processing; never for ordinary chat. */
@Composable fun AilaAcaIndicator(active: Boolean) {
    val opacity by animateFloatAsState(if(active) 1f else 0f, tween(if(active || AppTheme.reduceMotion) 0 else 120), label = "aca perimeter")
    if(opacity == 0f) return
    val colors = listOf(Color(0xFF9666FF), Color(0xFF568BFF), Color(0xFF40DAD0), Color(0xFFF7DD8A), Color(0xFFFFAA76), Color(0xFFF77EB5))
    val transition = rememberInfiniteTransition(label = "aca light")
    val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "aca travel")
    val position = if(AppTheme.reduceMotion) 0f else travel
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "ACA attiva · bordo luminoso" }) {
        val shifted = List(13) { i ->
            val sample = (i / 12f + position) * colors.size
            val base = kotlin.math.floor(sample).toInt()
            androidx.compose.ui.graphics.lerp(colors[base % colors.size], colors[(base + 1) % colors.size], sample - base)
        }
        val inset = 3.dp.toPx()
        val brush = Brush.sweepGradient(shifted, Offset(size.width / 2, size.height / 2))
        for(width in if(AppTheme.liteEffects) listOf(4f) else listOf(32f, 20f, 10f, 3f)) {
            drawRoundRect(brush, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(30.dp.toPx()),
                alpha = opacity * when(width) { 3f, 4f -> .95f; 10f -> .24f; 20f -> .12f; else -> .06f }, style = Stroke(width.dp.toPx()))
        }
    }
}
