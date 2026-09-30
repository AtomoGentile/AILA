package circolareplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
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
    get() = if (isDarkMode) Color(0xFF020306) else Color(0xFFE9EEFA)

/**
 * Sfondo dell'app in Liquid Glass: colore di base e grandi macchie sfumate di colori diversi,
 * come gli sfondi di iOS. Il vetro, molto trasparente, prende il colore da quello che ha dietro:
 * piu' lo sfondo e' vario (zone chiare, zone sature, colori caldi e freddi), piu' l'effetto vetro
 * si vede, anche mentre si scorre.
 */
fun Modifier.ailaGlassBackdrop(): Modifier = drawWithCache {
    val backdrop = glassBackdrop()
    onDrawBehind { drawGlassBackdrop(backdrop) }
}

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
): Modifier = drawWithCache {
    val backdrop = glassBackdrop()
    onDrawBehind {
        val left = (offset() * size.width).coerceIn(0f, size.width)
        if (left >= size.width) return@onDrawBehind
        clipRect(left = left) {
            translate(left = shift() * size.width) { drawGlassBackdrop(backdrop) }
        }
    }
}

/**
 * Sfondo di una schermata che scivola sotto un'altra (la pagina coperta dal push si sposta di
 * [shift] frazioni di larghezza verso sinistra): le macchie si disegnano spostate al contrario,
 * cosi' sullo schermo restano ferme mentre il contenuto scorre.
 */
fun Modifier.ailaGlassBackdrop(shift: () -> Float): Modifier = drawWithCache {
    val backdrop = glassBackdrop()
    onDrawBehind {
        translate(left = shift() * size.width) { drawGlassBackdrop(backdrop) }
    }
}

/**
 * I pennelli dello sfondo, costruiti una volta per dimensione e tema (drawWithCache): prima si
 * ricreavano a ogni fotogramma, anche durante le transizioni che spostano lo sfondo.
 */
private class GlassBackdrop(
    val base: Brush,
    val glows: List<Triple<Brush, Float, Offset>>
)

private fun CacheDrawScope.glassBackdrop(): GlassBackdrop {
    val dark = AppTheme.isDarkMode
    val w = size.width
    val h = size.height
    val accent = AppTheme.PrimaryBlue
    // Base: un gradiente verticale lungo tutto lo schermo, ampio e morbido come gli sfondi di
    // iOS (dall'alto il colore principale, poi blu-ardesia, in fondo un azzurro ghiaccio: toni freddi, sobri). Il
    // vetro ci passa sopra e cambia tinta man mano che si scorre, senza chiazze.
    // Scuro come su iPhone: fondo quasi nero (lo "system background" di iOS e' nero puro), con
    // appena un alone del colore principale in alto. Il vetro scuro di iOS 26 non e' una lastra
    // grigia: e' quasi invisibile e si riconosce dal filo di luce sui bordi.
    val stops = if (dark) {
        arrayOf(
            0f to lerpColor(Color(0xFF000000), accent, 0.2f),
            0.4f to Color(0xFF05070D),
            0.8f to Color(0xFF03060A),
            1f to Color(0xFF010204)
        )
    } else {
        arrayOf(
            0f to lerpColor(Color(0xFFE6EDFF), accent, 0.36f),
            0.45f to Color(0xFFD3DCF3),
            0.8f to Color(0xFFCFE6EC),
            1f to Color(0xFFDEE9EF)
        )
    }
    fun glow(x: Float, y: Float, radius: Float, color: Color): Triple<Brush, Float, Offset> {
        val center = Offset(w * x, h * y)
        val r = w * radius
        return Triple(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = r), r, center)
    }
    // Due soli bagliori molto larghi e tenui, per dare profondita' senza "macchie".
    val glows = if (dark) {
        listOf(glow(0.1f, 0.12f, 1.2f, accent.copy(alpha = 0.16f)), glow(0.95f, 0.6f, 1.1f, Color(0x1A0E7490)))
    } else {
        listOf(glow(0.1f, 0.12f, 1.2f, accent.copy(alpha = 0.18f)), glow(0.95f, 0.6f, 1.1f, Color(0x3867C6D8)))
    }
    return GlassBackdrop(Brush.verticalGradient(*stops, startY = 0f, endY = h), glows)
}

private fun DrawScope.drawGlassBackdrop(backdrop: GlassBackdrop) {
    drawRect(backdrop.base)
    backdrop.glows.forEach { (brush, radius, center) -> drawCircle(brush = brush, radius = radius, center = center) }
}

/**
 * Riempimento del vetro: quasi trasparente, appena piu' chiaro in alto. Il vetro di iOS 26 non
 * "colora" quello che copre: lo schiarisce un poco e lo lascia vedere. (Tre volte piu'
 * trasparente, poi altre cinque e altre due, richiesta di Simone: la forma la danno i bordi.)
 */
val AppTheme.GlassFill: Brush
    get() = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.002f), Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.004f), Color.White.copy(alpha = 0.002f)))
    }

/**
 * Bordo "speculare" del vetro: la luce arriva dall'alto a sinistra, quindi il bordo e' brillante
 * in quell'angolo, si spegne lungo i lati e si riaccende appena in basso a destra (il riflesso
 * interno), come i controlli di iOS 26. Non piu' un filo uniforme.
 */
val AppTheme.GlassEdge: Brush
    get() = if (isDarkMode) {
        // iPhone in scuro: riflesso netto nell'angolo in alto a sinistra e il suo ritorno in basso
        // a destra, quasi spento lungo i lati. E' questo filo a disegnare il vetro sul nero.
        Brush.linearGradient(
            0f to Color(0x5AFFFFFF), 0.3f to Color(0x10FFFFFF), 0.7f to Color(0x07FFFFFF), 1f to Color(0x33FFFFFF),
            start = Offset.Zero, end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            0f to Color(0x80FFFFFF), 0.35f to Color(0x2DFFFFFF), 0.7f to Color(0x13FFFFFF), 1f to Color(0x5AFFFFFF),
            start = Offset.Zero, end = Offset.Infinite
        )
    }

/**
 * Una superficie di vetro: velo quasi trasparente e bordo speculare.
 *
 * Il bagliore interno in alto che c'era qui aveva un'opacita' dello 0,3-0,5% (meno di 1/255 dopo
 * l'arrotondamento): era una passata di disegno in piu' su ogni card di vetro senza nessun effetto
 * visibile, quindi e' stato tolto. L'aspetto non cambia.
 */
fun Modifier.ailaGlassSurface(shape: Shape, tint: Color? = null, edge: Dp = 1.dp): Modifier =
    this
        .clip(shape)
        .background(AppTheme.GlassFill)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
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
