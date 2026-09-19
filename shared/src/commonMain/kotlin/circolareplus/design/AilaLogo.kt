package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Il marchio AILA disegnato a vettori.
 *
 * **Perché non si usa più il PNG.** `aila_logo.png` è un'immagine 512x512 in RGB, cioè *senza
 * canale alpha*: lo sfondo blu notte fa parte dell'immagine e non si può togliere. Ovunque venisse
 * mostrata senza ritaglio (nel login era esattamente così, a 72dp) compariva quindi un quadrato
 * scuro appiccicato sopra la card bianca — il difetto segnalato da Simone. Ritagliarla ad angoli
 * tondi risolveva a metà: restava un riquadro scuro con dentro un bordo squadrato, e a 24dp il
 * disegno si impastava perché il PNG veniva ridotto di venti volte.
 *
 * Qui il segno è ricostruito come geometria: due gambe dritte che si chiudono in una punta
 * arrotondata più il pallino centrale. Essendo vettoriale è nitido a ogni misura, ha lo sfondo
 * trasparente quando serve, e prende i colori da [AppTheme] (quindi segue anche il tema scuro).
 *
 * Vincolo tecnico rispettato, lo stesso annotato in [AppIcons]: niente `drawArc`/`addArc`/
 * `drawRoundRect`, che su Android in KMP crashano con ClassNotFoundException SkiaBackedPath.
 * Il pallino è un cerchio costruito con quattro bezier quadratiche; gli angoli tondi del riquadro
 * si ottengono con `clip(RoundedCornerShape)`, che è una Shape e non un disegno di Path.
 */

/** Cerchio come Path: sostituto sicuro di addArc/drawArc. */
private fun logoCirclePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val k = r * 0.5523f
    moveTo(cx, cy - r)
    cubicTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    cubicTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    cubicTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    cubicTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    close()
}

/**
 * Solo il segno "A", senza riquadro: sfondo trasparente, si appoggia su qualunque superficie.
 * Usato dentro [AilaLogoTile] e da solo dove serve il marchio in linea con del testo.
 */
@Composable
fun AilaGlyph(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    brush: Brush = Brush.linearGradient(
        listOf(Color(0xFF6FA8FF), Color(0xFFA78BFA))
    ),
    dotColor: Color = Color(0xFFEDF2FF)
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.155f

        // Due gambe dritte e una punta arrotondata: la bezier quadratica fa il vertice.
        val arch = Path().apply {
            moveTo(w * 0.205f, h * 0.855f)
            lineTo(w * 0.415f, h * 0.255f)
            quadraticBezierTo(w * 0.5f, h * 0.055f, w * 0.585f, h * 0.255f)
            lineTo(w * 0.795f, h * 0.855f)
        }
        drawPath(
            path = arch,
            brush = brush,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Il pallino al centro, dove in una "A" normale ci sarebbe la traversa.
        drawPath(logoCirclePath(w * 0.5f, h * 0.615f, w * 0.088f), color = dotColor)
    }
}

/**
 * I colori del marchio AILA Assistant, nell'ordine in cui si susseguono sulle quattro barre:
 * viola, blu, blu, verde acqua (#8B5CF6, #3B82F6, #14B8A6 della palette di brand).
 *
 * Non passano da [AppTheme] di proposito: un marchio che cambia colore col tema non e' piu' un
 * marchio. Dove il fondo non lo permette (badge minuscoli, superfici gia' colorate, header con
 * sfumatura) si passa un `SolidColor` al parametro `brush`, che e' la versione monocromatica
 * prevista dalle linee guida del logo.
 */
val AilaAssistantBrush: Brush = Brush.linearGradient(
    listOf(
        Color(0xFF8B5CF6),
        Color(0xFF3B82F6),
        Color(0xFF3B82F6),
        Color(0xFF14B8A6)
    )
)

/** Altezze delle quattro barre in frazione del lato: e' questa sequenza a fare l'onda. */
private val AssistantBarHeights = floatArrayOf(0.50f, 0.92f, 0.34f, 0.64f)

/**
 * Marchio "AILA Assistant": l'onda vocale a quattro barre, ridisegnata a vettori dal logo di
 * brand (il foglio "AILA Assistant — sempre al tuo fianco").
 *
 * **Perche' non e' piu' la "A" con l'avatar.** L'assistente ha un marchio suo, distinto dal segno
 * dell'app: quattro barre arrotondate di altezza diversa, un'onda vocale stilizzata che dice
 * "ti ascolta e ti risponde" invece di ripetere il logo dell'app con un pallino sopra. Serve
 * proprio a distinguere a colpo d'occhio "questo l'ha fatto AILA Assistant" da "questa e' AILA".
 *
 * Come [AilaGlyph] e' geometria e non un'immagine: nitido a ogni misura, fondo trasparente, e
 * senza `drawRoundRect`/`drawArc` — che su Android in KMP crashano con ClassNotFoundException
 * SkiaBackedPath. Ogni barra e' una linea con i capi arrotondati, che da' esattamente la stessa
 * pillola del logo originale.
 *
 * @param brush il riempimento delle barre. Il default e' [AilaAssistantBrush] (la variante a
 *   colori); su fondi colorati si passa `SolidColor(...)` per la monocromatica.
 */
@Composable
fun AilaAssistantMark(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    brush: Brush = AilaAssistantBrush
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barWidth = w * 0.15f
        val gap = w * 0.093f
        val bars = AssistantBarHeights.size
        // Il gruppo di barre sta al centro del riquadro: quello che resta diventa margine ai lati,
        // cosi' il marchio resta centrato qualunque sia la misura richiesta.
        val firstCenter = (w - (bars * barWidth + (bars - 1) * gap)) / 2f + barWidth / 2f
        val cy = h * 0.5f

        AssistantBarHeights.forEachIndexed { index, heightFactor ->
            val cx = firstCenter + index * (barWidth + gap)
            // La linea e' piu' corta della barra di mezzo spessore per capo: con StrokeCap.Round
            // sono i capi tondi a completare l'altezza voluta.
            val half = (h * heightFactor - barWidth) / 2f
            drawLine(
                brush = brush,
                start = Offset(cx, cy - half),
                end = Offset(cx, cy + half),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Il marchio dentro il riquadro ad angoli tondi, cioè l'aspetto dell'icona dell'app.
 *
 * @param glow alone morbido dietro al riquadro: si usa dove il marchio è protagonista
 *   (caricamento, login), non nelle intestazioni, dove distrarrebbe.
 */
@Composable
fun AilaLogoTile(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    glow: Boolean = false
) {
    val tile: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 27))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF16204A),
                            Color(0xFF1E2E6B),
                            Color(0xFF3B2E86)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Riflesso in alto a sinistra: dà volume al riquadro, come nell'icona vera.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)),
                            center = Offset(0f, 0f),
                            radius = 220f
                        )
                    )
            )
            AilaGlyph(size = size * 0.62f)
        }
    }

    if (!glow) {
        Box(modifier = modifier) { tile() }
        return
    }

    Box(modifier = modifier.size(size * 1.9f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * 1.9f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AppTheme.PrimaryBlue.copy(alpha = 0.22f),
                            AppTheme.SecondaryIndigo.copy(alpha = 0.10f),
                            Color(0x00000000)
                        )
                    )
                )
        )
        tile()
    }
}
