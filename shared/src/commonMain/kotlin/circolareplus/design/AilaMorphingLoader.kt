package circolareplus.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Indicatore di caricamento di Material 3 Expressive: una forma che ruota e si trasforma
 * continuamente da una sagoma all'altra (fiore, biscotto, stella morbida, trifoglio...), al posto
 * dei soliti puntini o della rotella.
 *
 * Come funziona: ogni sagoma e' un cerchio "ondulato", cioe' un raggio che varia con l'angolo
 * r(θ) = R·(1 + a·cos(kθ)) — k lobi, profondita' a. Passare da una sagoma alla successiva vuol
 * dire sfumare i due raggi, quindi la trasformazione e' continua e senza scatti. Disegnato come
 * Path di segmenti (niente archi: su Android in KMP drawArc/addArc crashano, vedi AppIcons).
 */
@Composable
fun AilaMorphingLoader(
    size: Dp = 24.dp,
    color: Color = AppTheme.PrimaryBlue,
    modifier: Modifier = Modifier
) {
    // (lobi, profondita') delle sagome, nell'ordine in cui si susseguono.
    val shapes = remember {
        listOf(
            8 to 0.10f,  // fiore a 8 petali
            5 to 0.16f,  // stella morbida
            12 to 0.06f, // biscotto (cookie)
            3 to 0.20f,  // trifoglio arrotondato
            4 to 0.18f,  // quadrifoglio
            6 to 0.12f   // esagono morbido
        )
    }
    val transition = rememberInfiniteTransition(label = "morphingLoader")
    // Avanzamento lungo la sequenza: la parte intera e' la sagoma, la frazione il passaggio.
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 650 * shapes.size, easing = LinearEasing)),
        label = "morphPhase"
    )
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "morphRotation"
    )
    val path = remember { Path() }

    Canvas(modifier = modifier.size(size)) {
        val p = phase.value
        val index = p.toInt() % shapes.size
        // Ogni passaggio accelera e poi si posa sulla sagoma (easing), come le molle di M3E.
        val t = FastOutSlowInEasing.transform((p - p.toInt()).coerceIn(0f, 1f))
        val (k1, a1) = shapes[index]
        val (k2, a2) = shapes[(index + 1) % shapes.size]
        val maxAmp = maxOf(a1, a2)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val base = minOf(cx, cy) / (1f + maxAmp)
        val rot = rotation.value * (PI.toFloat() / 180f)
        val steps = 120
        path.reset()
        for (i in 0..steps) {
            val theta = 2f * PI.toFloat() * i / steps
            val r1 = 1f + a1 * cos(k1 * theta)
            val r2 = 1f + a2 * cos(k2 * theta)
            val r = base * ((1f - t) * r1 + t * r2)
            val x = cx + r * cos(theta + rot)
            val y = cy + r * sin(theta + rot)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}
