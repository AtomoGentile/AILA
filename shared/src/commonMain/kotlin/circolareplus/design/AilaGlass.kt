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
 * Sfondo dell'app in Liquid Glass: colore di base piu' tre grandi macchie sfumate (blu in alto a
 * sinistra, viola a destra, rosa/acqua in basso), come uno sfondo di iOS visto attraverso il vetro.
 */
fun Modifier.ailaGlassBackdrop(): Modifier = drawBehind {
    val dark = AppTheme.isDarkMode
    drawRect(AppTheme.GlassBase)
    val w = size.width
    val h = size.height
    fun blob(center: Offset, radius: Float, color: Color) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
            radius = radius,
            center = center
        )
    }
    if (dark) {
        blob(Offset(w * 0.1f, h * 0.08f), w * 0.95f, Color(0x8C1E3A8A))
        blob(Offset(w * 0.95f, h * 0.45f), w * 0.85f, Color(0x734C1D95))
        blob(Offset(w * 0.2f, h * 0.92f), w * 0.9f, Color(0x590E7490))
    } else {
        blob(Offset(w * 0.1f, h * 0.08f), w * 0.95f, Color(0x999EC1FF))
        blob(Offset(w * 0.95f, h * 0.45f), w * 0.85f, Color(0x80C9B5FF))
        blob(Offset(w * 0.2f, h * 0.92f), w * 0.9f, Color(0x66FFC7E0))
    }
}

/** Riempimento del vetro: piu' chiaro in alto (il riflesso), piu' trasparente in basso. */
val AppTheme.GlassFill: Brush
    get() = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xA6FFFFFF), Color(0x61FFFFFF)))
    }

/** Filo di luce sul bordo del vetro: forte in alto, quasi sparito in basso. */
val AppTheme.GlassEdge: Brush
    get() = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x0FFFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0x40FFFFFF)))
    }

/** Una superficie di vetro: riempimento traslucido, riflesso in alto e filo di luce sul bordo. */
fun Modifier.ailaGlassSurface(shape: Shape, tint: Color? = null, edge: Dp = 1.dp): Modifier =
    this
        .clip(shape)
        .background(AppTheme.GlassFill)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
        .border(edge, AppTheme.GlassEdge, shape)

/** Riflesso "bagnato" sopra un riempimento pieno (pulsanti primari in vetro colorato). */
val AppTheme.GlassGloss: Brush
    get() = Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x00FFFFFF), Color(0x14FFFFFF)))

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
