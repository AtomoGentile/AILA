package circolareplus.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pezzi grafici di AILA Assistant condivisi fra la chat e la ricerca: l'icona "viva" del
 * benvenuto, l'aurora del pulsante che porta all'Assistant, il campo di testo a capsula con
 * l'anello sfumato e la pulsazione dei testi d'attesa.
 *
 * Il filo comune e' l'onda a quattro barre ([AilaAssistantWave]): i colori del marchio (viola,
 * blu, verde acqua) compaiono sempre e solo dove parla l'Assistant, cosi' si riconosce a colpo
 * d'occhio cosa e' suo. Tutto cio' che si muove lo fa nel livello grafico o di disegno (niente
 * ricomposizioni) e con "Riduci movimento" resta fermo.
 */

/**
 * L'icona del benvenuto: l'onda dentro una forma "cookie" di Material Expressive che ruota
 * pianissimo (un giro ogni 40 s), con un alone che respira dietro. Il marchio resta fermo e
 * leggibile; si muove solo il contorno.
 */
@Composable
fun AilaAssistantHalo(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    active: Boolean = false,
    intro: Boolean = false
) {
    val reduce = AppTheme.reduceMotion
    val transition = rememberInfiniteTransition(label = "assistantHalo")
    val spin = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "haloSpin"
    )
    val breathe = transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloBreathe"
    )
    val outer = size * 1.7f
    Box(modifier = modifier.size(outer), contentAlignment = Alignment.Center) {
        // Alone: sfumatura radiale che esce dal bordo della forma e si perde nello sfondo.
        Box(
            modifier = Modifier
                .size(outer)
                .graphicsLayer {
                    if (!reduce) {
                        scaleX = breathe.value
                        scaleY = breathe.value
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.35f to AilaAssistantViolet.copy(alpha = 0.26f),
                        0.65f to AilaAssistantBlue.copy(alpha = 0.12f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { if (!reduce) rotationZ = spin.value }
                .clip(ailaCookieShape(lobes = 9, depth = 0.09f))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AilaAssistantViolet.copy(alpha = 0.22f),
                            AilaAssistantBlue.copy(alpha = 0.20f),
                            AilaAssistantTeal.copy(alpha = 0.22f)
                        )
                    )
                )
        )
        AilaAssistantWave(active = active, size = size * 0.5f, intro = intro)
    }
}

/**
 * Sfondo "aurora" per l'elemento che porta all'Assistant (il pulsante "Chiedi ad AILA
 * Assistant"): il blu primario dei pulsanti con due macchie, una viola e una blu-verde, che
 * scivolano lente da un lato all'altro. Le macchie usano i toni profondi dell'onboarding, non
 * quelli del marchio, cosi' il testo bianco sopra resta leggibile (oltre 4.5:1) ovunque passino.
 *
 * Va messo dopo il `clip` della forma: le macchie escono dal riquadro e le ritaglia il chiamante.
 */
@Composable
fun Modifier.ailaAssistantAurora(): Modifier {
    val reduce = AppTheme.reduceMotion
    val transition = rememberInfiniteTransition(label = "assistantAurora")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing)),
        label = "auroraPhase"
    )
    val violet = AppTheme.OnboardingCalendarGradient.last()
    val deepTeal = AppTheme.OnboardingAssistantGradient[1]
    return this
        .background(AppTheme.PrimaryGradient)
        .drawBehind {
            val angle = (if (reduce) 0.15f else phase.value) * 2f * PI.toFloat()
            val radius = size.maxDimension * 0.55f
            val a = Offset(size.width * (0.5f + 0.38f * sin(angle)), size.height * 0.15f)
            val b = Offset(size.width * (0.5f - 0.38f * sin(angle + 0.8f)), size.height * 0.95f)
            drawCircle(
                brush = Brush.radialGradient(listOf(violet.copy(alpha = 0.85f), Color.Transparent), center = a, radius = radius),
                radius = radius,
                center = a
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(deepTeal.copy(alpha = 0.9f), Color.Transparent), center = b, radius = radius),
                radius = radius,
                center = b
            )
        }
}

/**
 * Anello sfumato (viola → blu → verde acqua) che si accende in dissolvenza quando un campo ha il
 * focus: il segnale "qui stai scrivendo" nel colore dell'Assistant, al posto del solo cursore.
 * Non cambia il layout (e' un bordo disegnato sopra) e, spento, non disegna niente.
 */
@Composable
fun Modifier.ailaFocusRing(focused: Boolean, shape: Shape): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = ailaFadeSpec(AilaDuration.Standard),
        label = "focusRing"
    )
    if (progress < 0.01f) return this
    val ring = Brush.linearGradient(
        listOf(
            AilaAssistantViolet.copy(alpha = progress),
            AilaAssistantBlue.copy(alpha = progress),
            AilaAssistantTeal.copy(alpha = progress)
        )
    )
    return this.border(2.dp, ring, shape)
}

/**
 * Campo di testo a capsula: vetro in Liquid Glass, pillola tonale in Material, con l'anello
 * dell'Assistant al focus. E' lo stesso della chat e della ricerca, che prima avevano due campi
 * diversi (uno trasparente senza contorno, l'altro con il bordo blu di Material).
 */
@Composable
fun AilaPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(100.dp),
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .then(
                if (AppTheme.isGlass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(AppTheme.TrackFill)
            )
            .ailaFocusRing(focused, shape)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            maxLines = maxLines,
            shape = shape,
            interactionSource = interactionSource,
            // Il fondo lo da' il contenitore (vetro o pillola tonale): il campo e' trasparente e
            // senza contorno; il contorno al focus e' l'anello.
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = AppTheme.PrimaryBlue,
                focusedTextColor = AppTheme.TextDark,
                unfocusedTextColor = AppTheme.TextDark,
                focusedPlaceholderColor = AppTheme.TextFaint,
                unfocusedPlaceholderColor = AppTheme.TextFaint
            )
        )
    }
}

/**
 * Pulsazione lenta dell'opacita' per un testo d'attesa ("Sto cercando…"). Con "Riduci movimento"
 * il testo resta fisso.
 */
@Composable
fun Modifier.ailaPulse(): Modifier {
    if (AppTheme.reduceMotion) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    return this.graphicsLayer { this.alpha = alpha.value }
}
