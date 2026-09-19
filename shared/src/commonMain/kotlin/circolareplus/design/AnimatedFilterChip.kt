package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pulsantino / Chip filtro con animazioni fluide di selezione:
 * - Transizione fluida del colore di sfondo e bordo (diventa blu sfumando dolcemente)
 * - Transizione del colore del testo
 * - Effetto scala / bounce al tocco (feedback tattile morbido)
 */
@Composable
fun AnimatedFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeBackgroundColor: Color = AppTheme.PrimaryBlue,
    inactiveBackgroundColor: Color = AppTheme.SurfaceWhite,
    activeTextColor: Color = Color.White,
    inactiveTextColor: Color = AppTheme.TextMuted,
    /**
     * Quando la chip vive dentro un [AilaSlidingChipRow], il colore di selezione lo disegna il
     * riquadro condiviso che scivola da una chip all'altra: qui va messo a `false` perché la chip
     * non disegni anche il proprio sfondo pieno, restando solo testo/icona che cambiano colore.
     */
    drawSelectionBackground: Boolean = true,
    /**
     * Icona facoltativa a sinistra dell'etichetta. Riceve il colore corrente del testo, così
     * segue l'animazione della chip. Serve a togliere le emoji dalle etichette (erano "📝
     * Verifiche", "🟢 Ti riguarda"): le emoji le disegna il sistema, cambiano tra Android e iOS
     * e non prendono il colore del tema.
     */
    icon: (@Composable (Color) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animazione colore di sfondo. Se il colore di selezione lo disegna il riquadro condiviso
    // (dentro un AilaSlidingChipRow), la chip da inattiva resta trasparente invece che una card
    // bianca a sé — altrimenti sembravano tante schede separate invece di un'unica barra.
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && drawSelectionBackground -> activeBackgroundColor
            !drawSelectionBackground -> Color.Transparent
            else -> inactiveBackgroundColor
        },
        animationSpec = tween(durationMillis = 220),
        label = "chipBgColor"
    )

    // Animazione colore del bordo
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected && drawSelectionBackground -> activeBackgroundColor
            !drawSelectionBackground -> Color.Transparent
            else -> AppTheme.Hairline
        },
        animationSpec = tween(durationMillis = 220),
        label = "chipBorderColor"
    )

    // Animazione colore del testo
    val textColor by animateColorAsState(
        targetValue = if (isSelected) activeTextColor else inactiveTextColor,
        animationSpec = tween(durationMillis = 200),
        label = "chipTextColor"
    )

    // Animazione scale al click per un effetto feedback vivo
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chipScale"
    )

    // Elevazione / spessore bordo fluido
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 180),
        label = "chipBorderWidth"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            // Selezionata: riempimento sfumato del brand, come le tab segmentate. Non
            // selezionata: colore pieno animato (il gradiente non è animabile allo stesso modo).
            .then(
                if (isSelected && drawSelectionBackground) Modifier.background(AppTheme.PrimaryGradient)
                else Modifier.background(backgroundColor)
            )
            .border(borderWidth, borderColor, RoundedCornerShape(AppTheme.SmallElementRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Pulito, senza ripple squadrato
                onClick = onClick
            )
            .padding(horizontal = AppTheme.Space16, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon(textColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}
