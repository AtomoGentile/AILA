package circolareplus.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import circolareplus.ai.assistant.AssistantAgentPhase
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** A private, non-blocking signal: perimeter → official symbol → restrained activity. */
@Composable
fun AilaAcaIndicator(
    phase: AssistantAgentPhase?, awaitingConfirmation: Boolean, showPopup: Boolean,
    operationKey: String?, onOpenAssistant: () -> Unit
) {
    // Confirmation ends activity. No border or dots remain after end/error/cancellation.
    if (phase == null) return
    val reduced = AppTheme.reduceMotion
    val convergence = remember(operationKey) { Animatable(0f) }
    val pulse = remember(operationKey) { Animatable(0f) }
    var popupVisible by remember(operationKey) { mutableStateOf(reduced) }
    var iconCenter by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rainbow = remember { listOf(Color(0xFF9C6CFF), Color(0xFF568BFF), Color(0xFF40DAD0),
        Color(0xFFF7DD8A), Color(0xFFFFAA76), Color(0xFFF77EB5), Color(0xFF9C6CFF)) }
    LaunchedEffect(operationKey, reduced) {
        if (reduced) { popupVisible = true; convergence.snapTo(1f); return@LaunchedEffect }
        convergence.snapTo(0f)
        delay(160)
        popupVisible = true
        delay(540)
        convergence.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(operationKey, reduced) {
        if (!reduced) while (true) { pulse.snapTo(0f); pulse.animateTo(1f, tween(1800, easing = LinearEasing)) }
    }
    val label = when (phase) {
        AssistantAgentPhase.READING -> "Legge il contesto"
        AssistantAgentPhase.PLANNING -> "Prepara le azioni"
        AssistantAgentPhase.EXECUTING -> "Salva e verifica"
        AssistantAgentPhase.WRITING -> "Elabora la risposta"
        AssistantAgentPhase.TRANSCRIBING -> "Trascrive l’audio"
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        val p = convergence.value
        if (!reduced && p < 1f) {
            val target = if (iconCenter == Offset.Zero) Offset(size.width - 48.dp.toPx(), 48.dp.toPx()) else iconCenter
            val inset = 3.dp.toPx(); val radius = 23.dp.toPx()
            val left = inset + (target.x - radius - inset) * p
            val top = inset + (target.y - radius - inset) * p
            val right = size.width - inset + (target.x + radius - size.width + inset) * p
            val bottom = size.height - inset + (target.y + radius - size.height + inset) * p
            val brush = Brush.sweepGradient(rainbow, target)
            val start = (1f - p) * .9f
            // Soft halo and crisp core share the same geometry; no blur layers on the whole app.
            for (width in if (AppTheme.liteEffects) listOf(3f) else listOf(16f, 8f, 3f)) drawRoundRect(brush,
                Offset(left, top), Size((right-left).coerceAtLeast(1f), (bottom-top).coerceAtLeast(1f)),
                CornerRadius((26f - 3f*p).dp.toPx()),
                alpha = start * if (width == 3f) 1f else if (width == 8f) .22f else .08f,
                style = Stroke(width.dp.toPx()))
        }
    }) {
        if (showPopup && popupVisible) Row(
            Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 12.dp, end = 12.dp, bottom = 112.dp)
                .background(AppTheme.CardSurface, RoundedCornerShape(28.dp))
                .border(1.dp, AppTheme.Hairline, RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClickLabel = "Apri AILA Assistant", onClick = onOpenAssistant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "AILA ACA. $label" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(42.dp).onGloballyPositioned {
                iconCenter = it.positionInRoot() - origin + Offset(it.size.width/2f, it.size.height/2f)
            }.drawBehind {
                drawCircle(Brush.sweepGradient(rainbow), style = Stroke(1.5.dp.toPx()),
                    alpha = if (reduced) .65f else .15f + convergence.value * (.55f + .12f*sin(pulse.value*2f*PI.toFloat())))
            }, contentAlignment = Alignment.Center) {
                AilaAssistantWave(active = !reduced, size = 26.dp)
            }
            Column {
                Text("AILA ACA", style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
                Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            Canvas(Modifier.size(width = 24.dp, height = 16.dp).clearAndSetSemantics {}) {
                repeat(3) { i ->
                    val wave = if (reduced) .6f else .35f + .65f*((sin((pulse.value*2f*PI-i*.7).toFloat())+1f)/2f)
                    drawCircle(AppTheme.PrimaryBlue.copy(alpha = wave), 1.9.dp.toPx(),
                        Offset((4f+i*8f).dp.toPx(), size.height/2f))
                }
            }
        }
    }
}
