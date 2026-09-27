package circolareplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass: i mattoni comuni.
 *
 * Il vetro si vede solo se dietro c'e' qualcosa: su uno sfondo grigio piatto una card
 * traslucida e' solo una card un po' piu' grigia (il "Liquid Glass appena accennato" della prima
 * versione). Per questo in Glass tutta l'app poggia su [ailaGlassBackdrop], uno sfondo con grandi
 * macchie di colore sfumate come gli sfondi di iOS, e le superfici sono veli traslucidi
 * ([ailaGlassSurface]) con un riflesso chiaro in alto e un filo di luce sul bordo.
 */

/** Colore pieno di base dello sfondo Glass (serve dove occorre un colore opaco, es. Haze). */
val AppTheme.GlassBase: Color
    get() = if (isDarkMode) Color(0xFF05070D) else Color(0xFFE9EEFA)

/**
 * Sfondo dell'app in Liquid Glass: colore di base e grandi macchie sfumate di colori diversi,
 * come gli sfondi di iOS. Il vetro, molto trasparente, prende il colore da quello che ha dietro:
 * piu' lo sfondo e' vario (zone chiare, zone sature, colori caldi e freddi), piu' l'effetto vetro
 * si vede, anche mentre si scorre.
 */
fun Modifier.ailaGlassBackdrop(): Modifier = drawBehind { drawAilaGlassBackdrop() }

/**
 * Sfondo di una schermata che entra "alla iOS" sopra un'altra: le macchie restano ferme
 * (allineate a quelle della schermata sotto) e si dipingono solo dalla posizione in cui e' gia'
 * arrivata la pagina ([offset] in frazioni di larghezza). Se scorresse con la pagina, per un
 * attimo si vedrebbero due sfondi diversi accostati.
 */
fun Modifier.ailaGlassPushBackdrop(
    offset: () -> Float,
    /** Spostamento del livello intero (es. scivola sotto il dettaglio circolare): si compensa. */
    shift: () -> Float = { 0f }
): Modifier = drawBehind {
    val left = (offset() * size.width).coerceIn(0f, size.width)
    if (left >= size.width) return@drawBehind
    clipRect(left = left) {
        translate(left = shift() * size.width) { drawAilaGlassBackdrop() }
    }
}

/**
 * Sfondo di una schermata che scivola sotto un'altra (la pagina coperta dal push si sposta di
 * [shift] frazioni di larghezza verso sinistra): le macchie si disegnano spostate al contrario,
 * cosi' sullo schermo restano ferme mentre il contenuto scorre.
 */
fun Modifier.ailaGlassBackdrop(shift: () -> Float): Modifier = drawBehind {
    val dx = shift() * size.width
    translate(left = dx) { drawAilaGlassBackdrop() }
}

fun DrawScope.drawAilaGlassBackdrop() {
    val dark = AppTheme.isDarkMode
    val w = size.width
    val h = size.height
    val accent = AppTheme.PrimaryBlue
    // Base: un gradiente verticale lungo tutto lo schermo, ampio e morbido come gli sfondi di
    // iOS (dall'alto il colore principale, al centro lilla, in fondo un rosa/pesca tenue). Il
    // vetro ci passa sopra e cambia tinta man mano che si scorre, senza chiazze.
    val stops = if (dark) {
        arrayOf(
            0f to lerpColor(Color(0xFF070A14), accent, 0.38f),
            0.45f to Color(0xFF1A1438),
            0.8f to Color(0xFF221431),
            1f to Color(0xFF1A0F1E)
        )
    } else {
        arrayOf(
            0f to lerpColor(Color(0xFFEAF0FF), accent, 0.30f),
            0.45f to Color(0xFFE4DDFB),
            0.8f to Color(0xFFF6E1EE),
            1f to Color(0xFFFBEADF)
        )
    }
    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = h))
    fun glow(x: Float, y: Float, radius: Float, color: Color) {
        val center = Offset(w * x, h * y)
        drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = w * radius),
            radius = w * radius,
            center = center
        )
    }
    // Due soli bagliori molto larghi e tenui, per dare profondita' senza "macchie".
    if (dark) {
        glow(0.1f, 0.12f, 1.2f, accent.copy(alpha = 0.22f))
        glow(0.95f, 0.6f, 1.1f, Color(0x264C1D95))
    } else {
        glow(0.1f, 0.12f, 1.2f, accent.copy(alpha = 0.14f))
        glow(0.95f, 0.6f, 1.1f, Color(0x33C4B5FD))
    }
}

/**
 * Riempimento del vetro: quasi trasparente, appena piu' chiaro in alto. Il vetro di iOS 26 non
 * "colora" quello che copre: lo schiarisce un poco e lo lascia vedere.
 */
val AppTheme.GlassFill: Brush
    get() = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x2EFFFFFF)))
    }

/**
 * Bordo "speculare" del vetro: la luce arriva dall'alto a sinistra, quindi il bordo e' brillante
 * in quell'angolo, si spegne lungo i lati e si riaccende appena in basso a destra (il riflesso
 * interno), come i controlli di iOS 26. Non piu' un filo uniforme.
 */
val AppTheme.GlassEdge: Brush
    get() = if (isDarkMode) {
        Brush.linearGradient(
            0f to Color(0x8CFFFFFF), 0.35f to Color(0x14FFFFFF), 0.7f to Color(0x0AFFFFFF), 1f to Color(0x40FFFFFF),
            start = Offset.Zero, end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            0f to Color(0xFFFFFFFF), 0.35f to Color(0x59FFFFFF), 0.7f to Color(0x26FFFFFF), 1f to Color(0xB3FFFFFF),
            start = Offset.Zero, end = Offset.Infinite
        )
    }

/**
 * Una superficie di vetro: velo quasi trasparente, bagliore morbido lungo il bordo alto (la luce
 * che entra nel vetro) e bordo speculare.
 */
fun Modifier.ailaGlassSurface(shape: Shape, tint: Color? = null, edge: Dp = 1.dp): Modifier =
    this
        .clip(shape)
        .background(AppTheme.GlassFill)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
        .drawBehind {
            // Bagliore interno in alto: la luce che attraversa lo spessore del vetro.
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (AppTheme.isDarkMode) 0.10f else 0.35f), Color.Transparent),
                    endY = size.height * 0.35f
                )
            )
        }
        .border(edge, AppTheme.GlassEdge, shape)

/** Riflesso "bagnato" sopra un riempimento colorato (pulsanti in vetro tinto). */
val AppTheme.GlassGloss: Brush
    get() = Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x0DFFFFFF), Color(0x00FFFFFF), Color(0x1FFFFFFF)))

/**
 * Forme "ondulate" di Material 3 Expressive (cookie, sunny, clover...): un cerchio il cui raggio
 * varia con l'angolo, r(θ) = R·(1 − d·(1 − cos kθ)/2), con k lobi di profondita' d. In M3E sono
 * i contenitori delle icone nelle liste, degli stati vuoti e degli avatar.
 * Path di segmenti, niente archi (su Android in KMP gli archi possono crashare, vedi AppIcons).
 */
fun ailaCookieShape(lobes: Int = 9, depth: Float = 0.08f): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy)
        val steps = 144
        for (i in 0..steps) {
            val theta = 2.0 * kotlin.math.PI * i / steps
            val r = radius * (1f - depth * (1f - kotlin.math.cos(lobes * theta).toFloat()) / 2f)
            val x = cx + r * kotlin.math.cos(theta - kotlin.math.PI / 2).toFloat()
            val y = cy + r * kotlin.math.sin(theta - kotlin.math.PI / 2).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
