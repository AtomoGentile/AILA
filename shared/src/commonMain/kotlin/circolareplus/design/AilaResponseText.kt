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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation of a completed response; provider/network streaming stays independent. */
@Composable
fun AilaResponseText(
    text: AnnotatedString,
    responseId: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    color: androidx.compose.ui.graphics.Color = AppTheme.TextDark,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
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
        style = style,
        color = color,
        modifier = modifier.graphicsLayer {
            alpha = entrance
            translationY = -descent * (1f - entrance)
        }.clearAndSetSemantics { this.text = text }
    )
}

/**
 * Converte il markdown essenziale prodotto dai modelli AI (`**grassetto**`, `*corsivo*`,
 * `` `codice` ``, titoli `#`, elenchi puntati `-` / `*`) in un [AnnotatedString] pulito
 * e ben leggibile senza asterischi grezzi.
 */
fun formatAilaMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
    val bulletRegex = Regex("^\\s*[-*•]\\s+(.*)$")
    val numberedRegex = Regex("^\\s*(\\d+\\.)\\s+(.*)$")
    val headingRegex = Regex("^\\s*#{1,3}\\s+(.*)$")

    fun isListOrHeading(line: String) =
        bulletRegex.matches(line) || numberedRegex.matches(line) || headingRegex.matches(line)

    fun AnnotatedString.Builder.appendInline(text: String) {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInline(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('`')
                        i++
                    }
                }
                text[i] == '*' && (i + 1 < text.length && text[i + 1] != ' ' && text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1 && text[end - 1] != ' ') {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('*')
                        i++
                    }
                }
                else -> {
                    val nextSpecial = listOf(
                        text.indexOf("**", i),
                        text.indexOf('`', i),
                        text.indexOf('*', i)
                    ).filter { it > i }.minOrNull() ?: text.length
                    append(text.substring(i, nextSpecial))
                    i = nextSpecial
                }
            }
        }
    }

    lines.forEachIndexed { idx, line ->
        val heading = headingRegex.matchEntire(line)
        val bullet = bulletRegex.matchEntire(line)
        val numbered = numberedRegex.matchEntire(line)
        when {
            heading != null -> {
                if (idx > 0) append("\n")
                withStyle(ParagraphStyle(lineHeight = 26.sp)) {
                    withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) {
                        appendInline(heading.groupValues[1].trim())
                    }
                }
            }
            bullet != null -> {
                withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp))) {
                    append("•  ")
                    appendInline(bullet.groupValues[1].trim())
                }
            }
            numbered != null -> {
                withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 18.sp))) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append("${numbered.groupValues[1]} ")
                    }
                    appendInline(numbered.groupValues[2].trim())
                }
            }
            else -> {
                appendInline(line)
                val next = lines.getOrNull(idx + 1)
                if (next != null && !isListOrHeading(next)) append("\n")
            }
        }
    }
}
