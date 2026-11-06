package circolareplus.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Presentation of a completed response; provider/network streaming stays independent. */
@Composable
fun AilaResponseText(
    text: AnnotatedString,
    responseId: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onComplete: () -> Unit = {}
) {
    val reduceMotion = AppTheme.reduceMotion
    var visibleCharacters by rememberSaveable(responseId, text) {
        mutableIntStateOf(if(animate && !reduceMotion) 0 else text.length)
    }
    var entrance by rememberSaveable(responseId, text) {
        mutableFloatStateOf(if(animate && !reduceMotion) 0f else 1f)
    }
    val completed by rememberUpdatedState(onComplete)
    LaunchedEffect(responseId, text, animate, reduceMotion) {
        if(!animate || reduceMotion || text.isEmpty()) {
            visibleCharacters = text.length
            entrance = 1f
        } else if(visibleCharacters < text.length || entrance < 1f) {
            val initialCharacters = visibleCharacters
            val initialEntrance = entrance
            val durationNanos = ((text.length - initialCharacters).toLong() * 8L).coerceIn(360L, 2400L) * 1_000_000L
            val start = withFrameNanos { it }
            do {
                val elapsed = withFrameNanos { it } - start
                val progress = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
                var end = initialCharacters + ((text.length - initialCharacters) * progress).toInt()
                // Never split a surrogate pair (emoji and other supplementary characters).
                if(end in 1 until text.length && text.text[end - 1].isHighSurrogate() && text.text[end].isLowSurrogate())end++
                visibleCharacters = end
                entrance = initialEntrance + (1f - initialEntrance) * FastOutSlowInEasing.transform((elapsed / 320_000_000f).coerceIn(0f, 1f))
            } while(visibleCharacters < text.length || entrance < 1f)
        }
        completed()
    }
    val descent = with(LocalDensity.current) { 14.dp.toPx() }
    Text(
        text = text.subSequence(0, visibleCharacters.coerceAtMost(text.length)),
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.TextDark,
        modifier = modifier.graphicsLayer {
            alpha = entrance
            translationY = -descent * (1f - entrance)
        }.clearAndSetSemantics { this.text = text }
    )
}
