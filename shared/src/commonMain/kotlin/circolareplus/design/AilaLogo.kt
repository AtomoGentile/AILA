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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Il marchio AILA disegnato a vettori: **AI + AULA**.
 *
 * In alto la scintilla, cioè l'AI, messa dove in classe sta la cattedra. Sotto ci sono due file di
 * banchi collegati come i nodi di una rete neurale: si legge sia come aula vista dall'alto sia come
 * rete. Ha preso il posto della vecchia "A" con il pallino, che non diceva niente di cosa fa l'app.
 *
 * **Due varianti.** Sotto [SmallMarkThreshold] la rete completa diventa un grumo di puntini (a 24dp
 * nell'intestazione delle schermate era illeggibile), quindi si passa alla variante piccola: la
 * scintilla e una sola fila di banchi, più grandi. Il cambio è automatico in base alla misura.
 *
 * **Una sola geometria.** Le stesse proporzioni, nel riquadro unitario 0..1, sono in
 * `design/logo/genera_icone.py`, che genera l'icona Android (adattiva e monocromatica), le icone iOS,
 * il logo della schermata di avvio e `aila_logo.png`. Se cambi i numeri qui, cambiali anche lì.
 *
 * **Perché non si usa il PNG.** `aila_logo.png` è un'immagine senza canale alpha: si porta dietro
 * il proprio fondo blu notte e a 24dp si impasta. Il segno vettoriale è nitido a ogni misura e ha lo
 * sfondo trasparente.
 *
 * Vincolo tecnico rispettato, lo stesso annotato in [AppIcons]: niente `drawArc`/`addArc`/
 * `drawRoundRect`, che su Android in KMP crashano con ClassNotFoundException SkiaBackedPath.
 * I banchi arrotondati sono Path fatti di bezier cubiche, l'alone della scintilla è un `drawRect`
 * con gradiente radiale, gli angoli tondi del riquadro vengono da `clip(RoundedCornerShape)`.
 */

/** Proporzioni del marchio nel riquadro unitario (0..1). */
private class AilaMarkGeometry(
    val sparkX: Float,
    val sparkY: Float,
    val sparkRx: Float,
    val sparkRy: Float,
    val halo: Float,
    /** Per ogni fila di banchi: y del centro e opacità. */
    val rows: List<Pair<Float, Float>>,
    val deskXs: List<Float>,
    val deskW: Float,
    val deskH: Float,
    val deskR: Float,
    val lineW: Float,
    val lineAlpha: Float
)

private val FullMark = AilaMarkGeometry(
    sparkX = 0.5f, sparkY = 0.1515f, sparkRx = 0.1212f, sparkRy = 0.1515f, halo = 0.2576f,
    rows = listOf(0.5455f to 1f, 0.9091f to 0.78f),
    deskXs = listOf(0.1818f, 0.5f, 0.8182f), deskW = 0.2424f, deskH = 0.1818f, deskR = 0.0606f,
    lineW = 0.0273f, lineAlpha = 0.42f
)

private val SmallMark = AilaMarkGeometry(
    sparkX = 0.5f, sparkY = 0.2f, sparkRx = 0.14f, sparkRy = 0.19f, halo = 0.30f,
    rows = listOf(0.78f to 1f),
    deskXs = listOf(0.17f, 0.5f, 0.83f), deskW = 0.26f, deskH = 0.2f, deskR = 0.065f,
    lineW = 0.04f, lineAlpha = 0.5f
)

/** Sotto questa misura del segno si usa la variante piccola. */
val SmallMarkThreshold: Dp = 32.dp

private val MarkDeskStart = Color(0xFF6FA8FF)
private val MarkDeskEnd = Color(0xFFA78BFA)
private val MarkLine = Color(0xFFB4C6FF)
private val MarkSparkBottom = Color(0xFFDCE5FF)
private val MarkHalo = Color(0xFFC9B8FF)

/** Rettangolo ad angoli tondi come Path di bezier cubiche: sostituto sicuro di drawRoundRect. */
private fun markRoundRectPath(left: Float, top: Float, w: Float, h: Float, r: Float): Path =
    Path().apply {
        val k = r * 0.5523f
        val right = left + w
        val bottom = top + h
        moveTo(left + r, top)
        lineTo(right - r, top)
        cubicTo(right - r + k, top, right, top + r - k, right, top + r)
        lineTo(right, bottom - r)
        cubicTo(right, bottom - r + k, right - r + k, bottom, right - r, bottom)
        lineTo(left + r, bottom)
        cubicTo(left + r - k, bottom, left, bottom - r + k, left, bottom - r)
        lineTo(left, top + r)
        cubicTo(left, top + r - k, left + r - k, top, left + r, top)
        close()
    }

/** Scintilla a quattro punte: quattro bezier quadratiche con i fianchi rientranti. */
private fun markSparklePath(cx: Float, cy: Float, rx: Float, ry: Float): Path = Path().apply {
    val ix = rx * 0.17f
    val iy = ry * 0.17f
    moveTo(cx, cy - ry)
    quadraticBezierTo(cx + ix, cy - iy, cx + rx, cy)
    quadraticBezierTo(cx + ix, cy + iy, cx, cy + ry)
    quadraticBezierTo(cx - ix, cy + iy, cx - rx, cy)
    quadraticBezierTo(cx - ix, cy - iy, cx, cy - ry)
    close()
}

/**
 * Solo il segno, senza riquadro: sfondo trasparente, si appoggia su qualunque superficie.
 * Usato dentro [AilaLogoTile] e da solo dove serve il marchio in linea con del testo.
 *
 * @param brush riempimento dei banchi. Se `null` è il gradiente del brand, blu in basso a sinistra
 *   e viola in alto a destra; su fondi colorati si passa un `SolidColor(...)`.
 * @param simplified variante piccola (scintilla + una fila di banchi). Di default si attiva da sola
 *   sotto [SmallMarkThreshold].
 */
@Composable
fun AilaGlyph(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    sparkleColor: Color = Color.White,
    simplified: Boolean = size < SmallMarkThreshold
) {
    val g = if (simplified) SmallMark else FullMark
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        fun px(u: Float) = u * s
        val deskBrush = brush ?: Brush.linearGradient(
            listOf(MarkDeskStart, MarkDeskEnd),
            start = Offset(0f, s),
            end = Offset(s, 0f)
        )
        val spark = Offset(px(g.sparkX), px(g.sparkY))
        val rows = g.rows.map { (y, _) -> g.deskXs.map { x -> Offset(px(x), px(y)) } }

        // 1. I collegamenti: dalla scintilla alla prima fila, poi da ogni banco a tutti quelli
        //    della fila successiva. Stanno sotto ai banchi, che ne coprono le estremità.
        val lineColor = MarkLine.copy(alpha = g.lineAlpha)
        val levels = listOf(listOf(spark)) + rows
        for (i in 0 until levels.size - 1) {
            for (a in levels[i]) for (b in levels[i + 1]) {
                drawLine(
                    color = lineColor,
                    start = a,
                    end = b,
                    strokeWidth = px(g.lineW),
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. I banchi: le file più lontane dalla cattedra sfumano un poco.
        g.rows.forEachIndexed { r, (_, alpha) ->
            for (c in rows[r]) {
                drawPath(
                    path = markRoundRectPath(
                        c.x - px(g.deskW) / 2f, c.y - px(g.deskH) / 2f,
                        px(g.deskW), px(g.deskH), px(g.deskR)
                    ),
                    brush = deskBrush,
                    alpha = alpha
                )
            }
        }

        // 3. L'alone e la scintilla. L'alone è un drawRect con gradiente radiale che si spegne
        //    entro il raggio: niente drawCircle/drawArc, per lo stesso vincolo di sopra.
        val haloR = px(g.halo)
        drawRect(
            brush = Brush.radialGradient(
                listOf(MarkHalo.copy(alpha = 0.55f), MarkHalo.copy(alpha = 0f)),
                center = spark,
                radius = haloR
            ),
            topLeft = Offset(spark.x - haloR, spark.y - haloR),
            size = Size(haloR * 2f, haloR * 2f)
        )
        drawPath(
            path = markSparklePath(spark.x, spark.y, px(g.sparkRx), px(g.sparkRy)),
            brush = Brush.verticalGradient(
                listOf(sparkleColor, MarkSparkBottom),
                startY = spark.y - px(g.sparkRy),
                endY = spark.y + px(g.sparkRy)
            )
        )
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
