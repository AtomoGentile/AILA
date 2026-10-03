package circolareplus.design

import kotlinx.coroutines.launch
import androidx.compose.ui.node.invalidateDraw

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Tema Material 3 dell'app.
 *
 * Perché serviva: finora l'app non impostava nessuno schema di colori, quindi tutti i componenti
 * standard di Material (interruttori, slider, campi di testo, indicatori di caricamento, pulsanti
 * dei dialoghi) uscivano nel **viola di default di Material 3** — si vede negli screenshot:
 * l'interruttore delle notifiche e lo slider dei pesi della mappa posti erano viola, mentre tutto
 * il resto dell'app è blu AILA. Le schermate erano state colorate a mano una per una, ma i
 * componenti di sistema no, perché il loro colore non lo decide la schermata: lo decide il tema.
 *
 * Da qui in avanti si imposta una volta sola qui e vale ovunque.
 */
private fun ailaColorScheme() = if (AppTheme.isDarkMode) darkColorScheme(
    primary = AppTheme.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppTheme.TintBlue,
    onPrimaryContainer = AppTheme.TintBlueInk,
    secondary = AppTheme.SecondaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = AppTheme.TintViolet,
    onSecondaryContainer = AppTheme.TintVioletInk,
    tertiary = AppTheme.AccentCyan,
    onTertiary = Color.White,
    background = AppTheme.BackgroundLight,
    onBackground = AppTheme.TextDark,
    surface = AppTheme.SurfaceWhite,
    onSurface = AppTheme.TextDark,
    surfaceVariant = AppTheme.TintSlate,
    onSurfaceVariant = AppTheme.TextMuted,
    outline = AppTheme.FieldOutline,
    outlineVariant = AppTheme.FieldOutline,
    error = AppTheme.TintRedInk,
    onError = Color.White,
    errorContainer = AppTheme.TintRed,
    onErrorContainer = AppTheme.TintRedInk,
    surfaceTint = Color.Transparent
) else lightColorScheme(
    primary = AppTheme.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppTheme.TintBlue,
    onPrimaryContainer = AppTheme.TintBlueInk,

    secondary = AppTheme.SecondaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = AppTheme.TintViolet,
    onSecondaryContainer = AppTheme.TintVioletInk,

    tertiary = AppTheme.AccentCyan,
    onTertiary = Color.White,

    background = AppTheme.BackgroundLight,
    onBackground = AppTheme.TextDark,
    surface = AppTheme.SurfaceWhite,
    onSurface = AppTheme.TextDark,
    surfaceVariant = AppTheme.TintSlate,
    onSurfaceVariant = AppTheme.TextMuted,

    outline = AppTheme.FieldOutline,
    outlineVariant = AppTheme.FieldOutline,

    error = AppTheme.TintRedInk,
    onError = Color.White,
    errorContainer = AppTheme.TintRed,
    onErrorContainer = AppTheme.TintRedInk,

    // Material tinge di blu le superfici in base all'elevazione. Le card AILA sono bianche con
    // un'ombra morbida, non bianche-azzurrate: qui l'effetto viene disattivato.
    surfaceTint = Color.Transparent
)

private fun ailaShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(AppTheme.SmallElementRadius),
    medium = RoundedCornerShape(AppTheme.ButtonCornerRadius),
    large = RoundedCornerShape(AppTheme.CardCornerRadius),
    extraLarge = RoundedCornerShape(AppTheme.CardCornerRadius + 8.dp)
)

/**
 * Avvolge l'intera app. Va applicato nei punti d'ingresso di piattaforma (MainActivity su
 * Android, MainViewController su iOS) e non dentro le singole schermate, così vale anche per
 * login, caricamento e onboarding.
 */
@Composable
fun AilaTheme(content: @Composable () -> Unit) {
    // "Riduci movimento" del sistema, copiato nello stato globale che leggono le molle e le
    // transizioni di AilaMotion (non sono @Composable). In un SideEffect e non durante la
    // composizione: scrivere uno stato mentre si compone fa ricomporre di nuovo chi l'ha appena
    // letto. Le schermate che partono subito con un'animazione (caricamento) leggono il sistema
    // da sole per non perdere il primo fotogramma.
    val reduceMotion = circolareplus.platform.isReduceMotionEnabled()
    androidx.compose.runtime.SideEffect { AppTheme.reduceMotion = reduceMotion }
    MaterialTheme(
        // Ricalcolati a ogni ricomposizione: dipendono da AppTheme.isDarkMode, che è stato di
        // Compose e cambia dalle Impostazioni.
        colorScheme = ailaColorScheme(),
        shapes = ailaShapes()
    ) {
        // Liquid Glass: lo sfondo a macchie di colore dietro a tutta l'app (vedi AilaGlass.kt).
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .then(if (AppTheme.isGlass) androidx.compose.ui.Modifier.ailaGlassBackdrop() else androidx.compose.ui.Modifier)
        ) {
            if (AppTheme.isGlass) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.LocalIndication provides AilaGlassHighlight,
                    content = content
                )
            } else {
                content()
            }
        }
    }
}

/**
 * Colori condivisi dei campi di testo: sfondo bianco pieno e bordo appena percettibile, come nel
 * mockup. Senza questo ogni campo usava lo stile di default di Material (bordo grigio scuro,
 * sfondo trasparente), che stonava con le card intorno.
 */
@Composable
fun ailaFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppTheme.FieldSurface,
    unfocusedContainerColor = AppTheme.FieldSurface,
    disabledContainerColor = AppTheme.TintSlate,
    focusedBorderColor = AppTheme.PrimaryBlue,
    unfocusedBorderColor = AppTheme.FieldOutline,
    focusedLabelColor = AppTheme.PrimaryBlue,
    unfocusedLabelColor = AppTheme.TextMuted,
    cursorColor = AppTheme.PrimaryBlue,
    focusedPlaceholderColor = AppTheme.TextFaint,
    unfocusedPlaceholderColor = AppTheme.TextFaint
)

/**
 * Liquid Glass: al tocco niente "onda" di Material (il ripple che si allarga dal dito, tipico di
 * Android), ma l'evidenziazione di iOS: un velo che si accende subito alla pressione e si spegne
 * con calma al rilascio. Vale per tutti i `clickable` che usano l'indicazione di default (righe
 * degli eventi, liste, card).
 */
internal object AilaGlassHighlight : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(
        interactionSource: androidx.compose.foundation.interaction.InteractionSource
    ): androidx.compose.ui.node.DelegatableNode = GlassHighlightNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class GlassHighlightNode(
    private val interactionSource: androidx.compose.foundation.interaction.InteractionSource
) : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
    private val level = androidx.compose.animation.core.Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed++
                    is androidx.compose.foundation.interaction.PressInteraction.Release -> pressed--
                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed--
                }
                val target = if (pressed > 0) 1f else 0f
                launch {
                    level.animateTo(
                        target,
                        androidx.compose.animation.core.tween(if (target > 0f) 60 else 280)
                    ) { invalidateDraw() }
                }
            }
        }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        drawContent()
        val value = level.value
        if (value > 0f) {
            val base = if (AppTheme.isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
            drawRect(base.copy(alpha = base.alpha * value))
        }
    }
}
