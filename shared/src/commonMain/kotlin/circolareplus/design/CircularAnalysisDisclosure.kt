package circolareplus.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import circolareplus.domain.model.Circular

/** Live queue: accent shimmer moves right to left; the same row opens and closes the titles. */
@Composable
fun CircularAnalysisDisclosure(circulars: List<Circular>, modifier: Modifier = Modifier) {
    if (circulars.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reduced = AppTheme.reduceMotion
    val rotation by animateFloatAsState(if (expanded) 90f else 0f,
        animationSpec = tween(if (reduced) 0 else 200), label = "analysisDisclosureArrow")
    val light = if (reduced) null else rememberInfiniteTransition(label = "analysisShimmer")
        .animateFloat(1.6f, -0.6f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "analysisLight")
    val accent = AppTheme.PrimaryBlue
    val base = AppTheme.TextMuted
    val count = circulars.distinctBy { it.number }.size
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Elenco aperto" else "Elenco chiuso" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Chiudi circolari in analisi" else "Mostra circolari in analisi") { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                if (count == 1) "1 circolare in analisi" else "$count circolari in analisi",
                color = if (reduced) accent else base,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (light != null) {
                            val center = light.value * size.width
                            val band = (size.width * .4f).coerceAtLeast(55.dp.toPx())
                            drawRect(Brush.linearGradient(
                                listOf(base, accent, lerp(accent, AppTheme.TextDark, .25f), accent, base),
                                start = Offset(center - band, 0f), end = Offset(center + band, size.height)
                            ), blendMode = BlendMode.SrcAtop)
                        }
                    }
            )
            AppIcons.ChevronRight(Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }, accent)
        }
        circulars.distinctBy { it.number }.forEachIndexed { index, circular ->
            key(circular.number) {
                val delay = if (reduced) 0 else (index * 40).coerceAtMost(320)
                val outDelay = if (reduced) 0 else ((count - index - 1) * 25).coerceAtMost(200)
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(if (reduced) 100 else 200, delayMillis = delay)) +
                        expandVertically(tween(if (reduced) 0 else 220, delayMillis = delay)) +
                        slideInVertically(tween(if (reduced) 0 else 220, delayMillis = delay)) { if (reduced) 0 else -it / 3 },
                    exit = fadeOut(tween(if (reduced) 100 else 130, delayMillis = outDelay)) +
                        shrinkVertically(tween(if (reduced) 0 else 180, delayMillis = outDelay))
                ) {
                    Text(circular.title, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                }
            }
        }
    }
}
