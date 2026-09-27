package circolareplus.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Barra di avanzamento dei due stili.
 *
 * - Material Expressive: la barra "ondulata" di Android 16 (LinearWavyProgressIndicator): la
 *   parte completata e' un'onda che scorre, poi uno stacco e il binario piatto con il puntino
 *   finale. L'onda si appiattisce vicino al 100%, come nell'originale.
 * - Liquid Glass: capsula traslucida con il riempimento del brand e un riflesso in alto.
 *
 * Disegnata con Path e drawLine (niente archi: su Android in KMP drawArc/addArc crashano, vedi
 * AppIcons). Il valore e' animato con una molla: i download arrivano a blocchi.
 */
@Composable
fun AilaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.PrimaryBlue,
    trackColor: Color = AppTheme.TintSlate
) {
    val animated = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 200f),
        label = "ailaProgress"
    )
    if (AppTheme.isGlass) {
        GlassProgressBar(progress = { animated.value }, color = color, modifier = modifier)
    } else {
        WavyProgressBar(progress = { animated.value }, color = color, trackColor = trackColor, modifier = modifier)
    }
}

@Composable
private fun WavyProgressBar(progress: () -> Float, color: Color, trackColor: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wavyProgress")
    // L'onda scorre in avanti di una lunghezza d'onda al secondo.
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "wavePhase"
    )
    val path = remember { Path() }
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val stroke = 4.dp.toPx()
        val wavelength = 24.dp.toPx()
        val gap = 6.dp.toPx()
        val cy = size.height / 2f
        val p = progress()
        val activeEnd = size.width * p
        // Ampiezza piena, poi si spegne negli ultimi punti percentuali (a 100% e' una linea).
        val amplitude = 3.dp.toPx() * ((1f - p) / 0.1f).coerceIn(0f, 1f) * (p / 0.02f).coerceIn(0f, 1f)

        // Binario: dopo la parte completata, piatto, con il puntino finale.
        val trackStart = activeEnd + gap + stroke / 2f
        val trackEnd = size.width - stroke / 2f
        if (trackStart < trackEnd) {
            drawLine(trackColor, Offset(trackStart, cy), Offset(trackEnd, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(color, radius = stroke / 2f, center = Offset(size.width - stroke / 2f, cy))

        // Parte completata: onda sinusoidale che scorre.
        if (activeEnd > stroke) {
            path.reset()
            val startX = stroke / 2f
            val endX = (activeEnd - stroke / 2f).coerceAtLeast(startX)
            val shift = phase.value * wavelength
            var x = startX
            path.moveTo(x, cy + amplitude * sin(2f * PI.toFloat() * (x - shift) / wavelength))
            while (x < endX) {
                x = minOf(x + 2f, endX)
                path.lineTo(x, cy + amplitude * sin(2f * PI.toFloat() * (x - shift) / wavelength))
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun GlassProgressBar(progress: () -> Float, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val r = h / 2f
        // Binario di vetro: velo chiaro traslucido.
        drawLine(
            if (AppTheme.isDarkMode) Color(0x33FFFFFF) else Color(0x66FFFFFF),
            Offset(r, r), Offset(size.width - r, r), strokeWidth = h, cap = StrokeCap.Round
        )
        val end = (size.width * progress()).coerceAtLeast(h)
        if (progress() > 0f) {
            drawLine(
                Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.75f)), endX = end),
                Offset(r, r), Offset(end - r, r), strokeWidth = h, cap = StrokeCap.Round
            )
            // Riflesso in alto sulla parte piena.
            drawLine(
                Color(0x59FFFFFF),
                Offset(r, h * 0.3f), Offset((end - r).coerceAtLeast(r), h * 0.3f),
                strokeWidth = h * 0.25f, cap = StrokeCap.Round
            )
        }
    }
}
