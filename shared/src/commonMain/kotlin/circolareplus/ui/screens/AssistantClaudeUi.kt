package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import circolareplus.ai.AiProvider
import circolareplus.ai.assistant.AssistantAttachedFile
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaDuration
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaSpatialSpring
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Token visivi fedeli al nuovo design editoriale di Claude (chiaro e scuro).
 *
 * Tutti i colori sono caldi e neutri: foglio bianco arrotondato su guscio pietra chiaro in tema
 * chiaro, antracite caldo in tema scuro, con accento terracotta solo sul marchio vivo.
 */
internal data class ClaudePalette(
    val shellBg: Color,
    val sheetBg: Color,
    val headerBtnBg: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val pillBg: Color,
    val activeRowBg: Color,
    val userBubbleBg: Color,
    val composerBg: Color,
    val composerBorder: Color,
    val hairline: Color,
    val coral: Color,
    val tooltipBg: Color,
    val tooltipInk: Color,
    val notifBlue: Color,
    val toastBg: Color,
    val dangerBg: Color,
    val dangerInk: Color
)

internal fun claudePalette(dark: Boolean = AppTheme.isDarkMode): ClaudePalette =
    if (dark) {
        ClaudePalette(
            shellBg = Color(0xFF191917),
            sheetBg = Color(0xFF21211F),
            headerBtnBg = Color(0xFF2C2C29),
            ink = Color(0xFFF3F2EC),
            inkMuted = Color(0xFFA4A39D),
            inkFaint = Color(0xFF73726C),
            pillBg = Color(0xFF2E2E2B),
            activeRowBg = Color(0xFF353531),
            userBubbleBg = Color(0xFF28323D),
            composerBg = Color(0xFF292926),
            composerBorder = Color(0xFF3A3A35),
            hairline = Color(0xFF33332E),
            coral = Color(0xFFE38869),
            tooltipBg = Color(0xFFF3F2EC),
            tooltipInk = Color(0xFF141413),
            notifBlue = AppTheme.PrimaryBlue,
            toastBg = Color(0xFF2B2B28),
            dangerBg = Color(0xFF3A2022),
            dangerInk = Color(0xFFFCA5A5)
        )
    } else {
        ClaudePalette(
            shellBg = Color(0xFFF3F3F1),
            sheetBg = Color(0xFFFFFFFF),
            headerBtnBg = Color(0xFFFFFFFF),
            ink = Color(0xFF141413),
            inkMuted = Color(0xFF6B6B67),
            inkFaint = Color(0xFF9B9A94),
            pillBg = Color(0xFFF0F0ED),
            activeRowBg = Color(0xFFE7E7E3),
            userBubbleBg = Color(0xFFEDF2F7),
            composerBg = Color(0xFFFFFFFF),
            composerBorder = Color(0xFFE5E5E1),
            hairline = Color(0xFFE8E7E3),
            coral = Color(0xFFD97757),
            tooltipBg = Color(0xFF141413),
            tooltipInk = Color(0xFFFFFFFF),
            notifBlue = AppTheme.PrimaryBlue,
            toastBg = Color(0xFFF7F7F5),
            dangerBg = Color(0xFFFEF2F2),
            dangerInk = Color(0xFF991B1B)
        )
    }

internal val ClaudeEffortLabels = listOf("Rapido", "Medio", "Alto", "Massimo")

internal data class ClaudeModelOption(
    val providerId: String,
    val title: String,
    val shortLabel: String,
    val description: String
)

internal fun claudeModelOptions(localModelLabel: String): List<ClaudeModelOption> {
    val cleanLocal = localModelLabel.trim().ifBlank { "AI locale" }
    val shortLocal = cleanLocal.substringBefore("(").trim().take(16).ifBlank { "Locale" }
    return listOf(
        ClaudeModelOption(
            providerId = AiProvider.AILA_CLOUD.id,
            title = "AILA Cloud",
            shortLabel = "AILA Cloud",
            description = "Gemma 4 incluso in AILA: per lavoro quotidiano, circolari e studio senza chiavi."
        ),
        ClaudeModelOption(
            providerId = AiProvider.GOOGLE_AI_STUDIO.id,
            title = "Google Gemini",
            shortLabel = "Gemini",
            description = "Gemini via API key personale di Google AI Studio, ideale per compiti complessi."
        ),
        ClaudeModelOption(
            providerId = AiProvider.ON_DEVICE.id,
            title = cleanLocal,
            shortLabel = shortLocal,
            description = "Modello sul dispositivo: funziona anche offline e nessun dato esce dal telefono."
        ),
        ClaudeModelOption(
            providerId = AiProvider.COMPATIBLE_API.id,
            title = "API compatibile",
            shortLabel = "API",
            description = "Collega un endpoint personalizzato compatibile con OpenAI o Anthropic."
        )
    )
}

// =================================================================================================
// Glifi vettoriali KMP-safe (nessun drawArc / addArc / drawRoundRect: solo Path, drawLine, drawCircle)
// =================================================================================================

/** Marchio starburst terracotta: 11 raggi affusolati, ruota e respira dolcemente quando attivo. */
@Composable
internal fun ClaudeSparkMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    color: Color = claudePalette().coral,
    active: Boolean = false
) {
    val animate = active && !AppTheme.reduceMotion && !AppTheme.liteEffects
    val transition = if (animate) rememberInfiniteTransition(label = "claudeSpark") else null
    val spin by (transition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sparkSpin"
    ) ?: remember { mutableFloatStateOf(0f) })
    val pulse by (transition?.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sparkPulse"
    ) ?: remember { mutableFloatStateOf(1f) })

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (animate) {
                    rotationZ = spin
                    scaleX = pulse
                    scaleY = pulse
                }
            }
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val rMax = this.size.minDimension * 0.48f
        val rInner = rMax * 0.22f
        val rays = 11
        val lengths = floatArrayOf(1.0f, 0.88f, 0.98f, 0.85f, 1.0f, 0.89f, 0.97f, 0.86f, 1.0f, 0.88f, 0.95f)
        for (i in 0 until rays) {
            val angle = (i * (2.0 * PI / rays) - PI / 2.0).toFloat()
            val len = rMax * lengths[i % lengths.size]
            val perp = angle + (PI / 2.0).toFloat()
            val baseHalf = rMax * 0.088f
            val tipHalf = rMax * 0.048f
            val sx = cx + cos(angle) * rInner
            val sy = cy + sin(angle) * rInner
            val ex = cx + cos(angle) * len
            val ey = cy + sin(angle) * len
            val p = Path().apply {
                moveTo(sx + cos(perp) * baseHalf, sy + sin(perp) * baseHalf)
                lineTo(ex + cos(perp) * tipHalf, ey + sin(perp) * tipHalf)
                quadraticBezierTo(
                    ex + cos(angle) * tipHalf * 1.4f,
                    ey + sin(angle) * tipHalf * 1.4f,
                    ex - cos(perp) * tipHalf,
                    ey - sin(perp) * tipHalf
                )
                lineTo(sx - cos(perp) * baseHalf, sy - sin(perp) * baseHalf)
                close()
            }
            drawPath(p, color)
        }
        drawCircle(color = color, radius = rInner * 1.15f, center = Offset(cx, cy))
    }
}

/** Due linee orizzontali asimmetriche del pulsante menu in alto a sinistra. */
@Composable
internal fun ClaudeMenuGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.11f).coerceAtLeast(1.8.dp.toPx())
        drawLine(
            color = color,
            start = Offset(size.width * 0.16f, size.height * 0.36f),
            end = Offset(size.width * 0.84f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.16f, size.height * 0.65f),
            end = Offset(size.width * 0.60f, size.height * 0.65f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Fantasmino minimale per la modalità incognito (contorno o pieno). */
@Composable
internal fun ClaudeGhostGlyph(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color,
    filled: Boolean = false,
    eyeCutoutColor: Color = Color.White
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1.6.dp.toPx())
        val body = Path().apply {
            moveTo(w * 0.20f, h * 0.80f)
            lineTo(w * 0.20f, h * 0.45f)
            cubicTo(w * 0.20f, h * 0.14f, w * 0.80f, h * 0.14f, w * 0.80f, h * 0.45f)
            lineTo(w * 0.80f, h * 0.80f)
            quadraticBezierTo(w * 0.70f, h * 0.88f, w * 0.62f, h * 0.79f)
            quadraticBezierTo(w * 0.50f, h * 0.88f, w * 0.38f, h * 0.79f)
            quadraticBezierTo(w * 0.30f, h * 0.88f, w * 0.20f, h * 0.80f)
            close()
        }
        if (filled) {
            drawPath(body, color)
        } else {
            drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val eyeColor = if (filled) eyeCutoutColor else color
        val eyeR = w * 0.055f
        drawCircle(eyeColor, radius = eyeR, center = Offset(w * 0.40f, h * 0.48f))
        drawCircle(eyeColor, radius = eyeR, center = Offset(w * 0.60f, h * 0.48f))
    }
}

/** Quattro barre verticali arrotondate per il tasto vocale / onda nel composer. */
@Composable
internal fun ClaudeWaveformGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
    Canvas(modifier) {
        val stroke = (size.width * 0.115f).coerceAtLeast(1.8.dp.toPx())
        val bars = listOf(0.24f to 0.30f, 0.42f to 0.68f, 0.58f to 0.46f, 0.76f to 0.26f)
        bars.forEach { (xFrac, hFrac) ->
            val half = size.height * hFrac / 2f
            val cy = size.height / 2f
            drawLine(
                color = color,
                start = Offset(size.width * xFrac, cy - half),
                end = Offset(size.width * xFrac, cy + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Puntina minimale per le chat fissate. */
@Composable
internal fun ClaudePinGlyph(modifier: Modifier = Modifier.size(15.dp), color: Color, filled: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.10f).coerceAtLeast(1.4.dp.toPx())
        val head = Path().apply {
            moveTo(w * 0.46f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.54f)
            lineTo(w * 0.68f, h * 0.62f)
            lineTo(w * 0.54f, h * 0.76f)
            lineTo(w * 0.24f, h * 0.46f)
            lineTo(w * 0.38f, h * 0.32f)
            close()
        }
        if (filled) drawPath(head, color)
        else drawPath(head, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            color = color,
            start = Offset(w * 0.38f, h * 0.62f),
            end = Offset(w * 0.16f, h * 0.84f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Doppia pagina minimale per copiare il testo. */
@Composable
internal fun ClaudeCopyGlyph(modifier: Modifier = Modifier.size(17.dp), color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.095f).coerceAtLeast(1.4.dp.toPx())
        val front = Path().apply {
            moveTo(w * 0.34f, h * 0.34f)
            lineTo(w * 0.82f, h * 0.34f)
            lineTo(w * 0.82f, h * 0.86f)
            lineTo(w * 0.34f, h * 0.86f)
            close()
        }
        drawPath(front, color, style = Stroke(stroke, join = StrokeJoin.Round))
        val back = Path().apply {
            moveTo(w * 0.24f, h * 0.66f)
            lineTo(w * 0.18f, h * 0.66f)
            lineTo(w * 0.18f, h * 0.16f)
            lineTo(w * 0.66f, h * 0.16f)
            lineTo(w * 0.66f, h * 0.24f)
        }
        drawPath(back, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Tre puntini verticali (⋮). */
@Composable
internal fun ClaudeOverflowGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
    Canvas(modifier) {
        val r = (size.minDimension * 0.085f).coerceAtLeast(1.5.dp.toPx())
        val cx = size.width / 2f
        drawCircle(color, r, Offset(cx, size.height * 0.24f))
        drawCircle(color, r, Offset(cx, size.height * 0.50f))
        drawCircle(color, r, Offset(cx, size.height * 0.76f))
    }
}

/** Ingranaggio minimale per il pulsante Impostazioni nel cassetto. */
@Composable
internal fun ClaudeSettingsGearGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rOuter = size.minDimension * 0.36f
        val rInner = size.minDimension * 0.15f
        val stroke = (size.minDimension * 0.095f).coerceAtLeast(1.5.dp.toPx())
        drawCircle(color, radius = rOuter * 0.72f, center = Offset(cx, cy), style = Stroke(stroke))
        drawCircle(color, radius = rInner, center = Offset(cx, cy), style = Stroke(stroke))
        for (i in 0 until 8) {
            val a = (i * PI / 4.0).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + cos(a) * rOuter * 0.78f, cy + sin(a) * rOuter * 0.78f),
                end = Offset(cx + cos(a) * rOuter * 1.12f, cy + sin(a) * rOuter * 1.12f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Doppia nuvoletta chat stile tab Chat del nuovo Claude. */
@Composable
internal fun ClaudeChatTabGlyph(modifier: Modifier = Modifier.size(21.dp), color: Color, filled: Boolean) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1.6.dp.toPx())
        val main = Path().apply {
            moveTo(w * 0.14f, h * 0.44f)
            cubicTo(w * 0.14f, h * 0.20f, w * 0.70f, h * 0.20f, w * 0.70f, h * 0.44f)
            cubicTo(w * 0.70f, h * 0.66f, w * 0.44f, h * 0.68f, w * 0.30f, h * 0.66f)
            lineTo(w * 0.16f, h * 0.74f)
            lineTo(w * 0.20f, h * 0.59f)
            cubicTo(w * 0.16f, h * 0.55f, w * 0.14f, h * 0.50f, w * 0.14f, h * 0.44f)
            close()
        }
        val secondary = Path().apply {
            moveTo(w * 0.50f, h * 0.68f)
            cubicTo(w * 0.58f, h * 0.80f, w * 0.82f, h * 0.78f, w * 0.88f, h * 0.82f)
            lineTo(w * 0.85f, h * 0.69f)
            cubicTo(w * 0.90f, h * 0.62f, w * 0.88f, h * 0.44f, w * 0.74f, h * 0.40f)
        }
        if (filled) {
            drawPath(main, color)
            drawPath(secondary, color.copy(alpha = 0.75f), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawPath(main, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(secondary, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Piccola chevron verso il basso o verso l'alto. */
@Composable
internal fun ClaudeChevronGlyph(
    modifier: Modifier = Modifier.size(14.dp),
    color: Color,
    pointUp: Boolean = false
) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.13f).coerceAtLeast(1.4.dp.toPx())
        val topY = if (pointUp) size.height * 0.64f else size.height * 0.36f
        val tipY = if (pointUp) size.height * 0.34f else size.height * 0.66f
        val path = Path().apply {
            moveTo(size.width * 0.22f, topY)
            lineTo(size.width * 0.50f, tipY)
            lineTo(size.width * 0.78f, topY)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Freccia sinistra pulita (←) per Ricerca e foglio Ragionamento. */
@Composable
internal fun ClaudeArrowLeftGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.105f).coerceAtLeast(1.7.dp.toPx())
        val cy = size.height / 2f
        drawLine(color, Offset(size.width * 0.22f, cy), Offset(size.width * 0.80f, cy), stroke, StrokeCap.Round)
        val head = Path().apply {
            moveTo(size.width * 0.46f, size.height * 0.24f)
            lineTo(size.width * 0.20f, cy)
            lineTo(size.width * 0.46f, size.height * 0.76f)
        }
        drawPath(head, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Indicatore compatto a 4 tacche per il livello di ragionamento nel selettore Modello. */
@Composable
internal fun ClaudeEffortDots(
    level: Int,
    palette: ClaudePalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until ClaudeEffortLabels.size) {
            val selected = i == level.coerceIn(0, ClaudeEffortLabels.lastIndex)
            Box(
                modifier = Modifier
                    .width(if (selected) 2.5.dp else 3.dp)
                    .height(if (selected) 12.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.ink else palette.inkFaint.copy(alpha = 0.65f))
            )
        }
    }
}

// =================================================================================================
// Header bar su guscio pietra chiaro con bottoni circolari bianchi e pillola raggruppata a destra
// =================================================================================================

@Composable
internal fun ClaudeHeaderBar(
    palette: ClaudePalette,
    hasMessages: Boolean,
    conversationTitle: String?,
    isIncognito: Boolean,
    isPinned: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleIncognito: () -> Unit,
    onNewChat: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteCurrent: () -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.shellBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sinistra: bottone circolare bianco con icona menu a due linee
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.headerBtnBg)
                .border(0.75.dp, palette.hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenDrawer)
                .semantics { contentDescription = "Apri menu chat" },
            contentAlignment = Alignment.Center
        ) {
            ClaudeMenuGlyph(color = palette.ink)
        }

        Spacer(Modifier.width(12.dp))

        // Centro: titolo conversazione oppure etichetta discreta "Chat in incognito"
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (hasMessages) Alignment.CenterStart else Alignment.Center
        ) {
            when {
                hasMessages -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isIncognito) {
                            ClaudeGhostGlyph(Modifier.size(16.dp), color = palette.inkMuted)
                        }
                        Text(
                            text = if (isIncognito) "Chat in incognito" else (conversationTitle ?: "AILA Assistant"),
                            style = TextStyle(
                                fontFamily = FontFamily.Default,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                isIncognito -> {
                    Text(
                        text = "Chat in incognito",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.inkMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Destra: pillola bianca con due azioni (Ricerca + Incognito a chat vuota; Nuova chat + ⋮ in conversazione)
        Box {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasMessages) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 40.dp)
                            .clickable(role = Role.Button, onClick = onNewChat)
                            .semantics { contentDescription = "Nuova chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.NewChat(Modifier.size(18.dp), color = palette.ink)
                    }
                    Box(Modifier.width(0.75.dp).height(18.dp).background(palette.hairline))
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 40.dp)
                            .clickable(role = Role.Button, onClick = { overflowOpen = true })
                            .semantics { contentDescription = "Altre opzioni chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        ClaudeOverflowGlyph(color = palette.ink)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 40.dp)
                            .clickable(role = Role.Button, onClick = onOpenSearch)
                            .semantics { contentDescription = "Cerca nelle chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.Search(Modifier.size(18.dp), color = palette.ink)
                    }
                    Box(Modifier.width(0.75.dp).height(18.dp).background(palette.hairline))
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 40.dp)
                            .clickable(role = Role.Button, onClick = onToggleIncognito)
                            .semantics {
                                contentDescription = if (isIncognito) "Esci dalla chat in incognito" else "Chat in incognito"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = isIncognito, animationSpec = ailaFadeSpec(AilaDuration.Quick), label = "incognitoBtn") { inc ->
                            if (inc) {
                                AppIcons.Close(Modifier.size(17.dp), color = palette.ink)
                            } else {
                                ClaudeGhostGlyph(Modifier.size(19.dp), color = palette.ink)
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { overflowOpen = false },
                containerColor = palette.sheetBg
            ) {
                if (!isIncognito) {
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "Rimuovi dai fissati" else "Fissa conversazione", color = palette.ink) },
                        leadingIcon = { ClaudePinGlyph(Modifier.size(16.dp), color = palette.inkMuted, filled = isPinned) },
                        onClick = { overflowOpen = false; onTogglePin() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Cerca nelle chat", color = palette.ink) },
                    leadingIcon = { AppIcons.Search(Modifier.size(16.dp), color = palette.inkMuted) },
                    onClick = { overflowOpen = false; onOpenSearch() }
                )
                DropdownMenuItem(
                    text = { Text(if (isIncognito) "Esci da incognito" else "Nuova chat in incognito", color = palette.ink) },
                    leadingIcon = { ClaudeGhostGlyph(Modifier.size(16.dp), color = palette.inkMuted) },
                    onClick = { overflowOpen = false; onToggleIncognito() }
                )
                if (!isIncognito) {
                    DropdownMenuItem(
                        text = { Text("Elimina conversazione", color = palette.dangerInk) },
                        leadingIcon = { AppIcons.Trash(Modifier.size(16.dp), color = palette.dangerInk) },
                        onClick = { overflowOpen = false; onDeleteCurrent() }
                    )
                }
            }
        }
    }
}

// =================================================================================================
// Cassetto laterale sinistro (Slide-Over Drawer fedele a Claude)
// =================================================================================================

@Composable
internal fun ClaudeLeftDrawer(
    palette: ClaudePalette,
    drawerWidth: Dp,
    conversations: List<AssistantConversation>,
    activeConversationId: String?,
    onSelectConversation: (AssistantConversation) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFiles: () -> Unit,
    onStudyPlan: () -> Unit,
    onOpenBureaucracy: () -> Unit,
    onOpenGita: () -> Unit
) {
    var pinnedExpanded by remember { mutableStateOf(true) }
    var showAllRecents by remember { mutableStateOf(false) }

    val pinned = remember(conversations) {
        conversations.filter { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val recents = remember(conversations) {
        conversations.filterNot { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val visibleRecents = if (showAllRecents) recents else recents.take(8)

    Column(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight()
            .background(palette.shellBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // Wordmark editoriale Serif "AILA"
        Text(
            text = "AILA",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp
            ),
            color = palette.ink,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (pinned.isNotEmpty()) {
                item(key = "header-pinned") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { pinnedExpanded = !pinnedExpanded }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fissate",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = palette.inkMuted,
                            modifier = Modifier.weight(1f)
                        )
                        ClaudeChevronGlyph(color = palette.inkMuted, pointUp = pinnedExpanded)
                    }
                }
                if (pinnedExpanded) {
                    items(pinned, key = { "pin-${it.id}" }) { conv ->
                        ClaudeDrawerConversationRow(
                            conversation = conv,
                            active = conv.id == activeConversationId,
                            showLeadingPin = true,
                            palette = palette,
                            onClick = { onSelectConversation(conv) },
                            onTogglePin = { onTogglePin(conv.id) }
                        )
                    }
                }
                item(key = "spacer-pinned") { Spacer(Modifier.height(10.dp)) }
            }

            item(key = "header-recents") {
                Text(
                    text = "Recenti",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = palette.inkMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            if (recents.isEmpty() && pinned.isEmpty()) {
                item(key = "empty-recents") {
                    Text(
                        text = "Nessuna conversazione salvata",
                        style = TextStyle(fontSize = 14.sp),
                        color = palette.inkFaint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            } else {
                items(visibleRecents, key = { "rec-${it.id}" }) { conv ->
                    ClaudeDrawerConversationRow(
                        conversation = conv,
                        active = conv.id == activeConversationId,
                        showLeadingPin = false,
                        palette = palette,
                        onClick = { onSelectConversation(conv) },
                        onTogglePin = { onTogglePin(conv.id) }
                    )
                }
                if (!showAllRecents && recents.size > 8) {
                    item(key = "show-more") {
                        Text(
                            text = "Mostra altre",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            color = palette.inkMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAllRecents = true }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Scorciatoie AILA (profilo, file, piano di studio, burocrazia, gita)
            item(key = "header-shortcuts") {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(0.75.dp).background(palette.hairline))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Scorciatoie",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = palette.inkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            item(key = "shortcut-profile") {
                ClaudeDrawerShortcutRow("Profilo", palette, onOpenProfile) {
                    AppIcons.Profile(Modifier.size(16.dp), color = palette.inkMuted)
                }
            }
            item(key = "shortcut-study") {
                ClaudeDrawerShortcutRow("Piano di studio", palette, onStudyPlan) {
                    AppIcons.Sparkle(Modifier.size(16.dp), color = palette.inkMuted)
                }
            }
            item(key = "shortcut-files") {
                ClaudeDrawerShortcutRow("File salvati", palette, onOpenFiles) {
                    AppIcons.Document(Modifier.size(16.dp), color = palette.inkMuted)
                }
            }
            item(key = "shortcut-bureaucracy") {
                ClaudeDrawerShortcutRow("Burocrazia", palette, onOpenBureaucracy) {
                    AppIcons.Check(Modifier.size(16.dp), color = palette.inkMuted)
                }
            }
            item(key = "shortcut-gita") {
                ClaudeDrawerShortcutRow("Gita", palette, onOpenGita) {
                    AppIcons.Bus(Modifier.size(16.dp), color = palette.inkMuted)
                }
            }
        }

        // Footer esatto di Claude: Impostazioni (cerchio) | Cerca (pillola) | Nuova chat (cerchio pieno)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics { contentDescription = "Impostazioni" },
                contentAlignment = Alignment.Center
            ) {
                ClaudeSettingsGearGlyph(color = palette.ink)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp))
                    .clickable(role = Role.Button, onClick = onOpenSearch)
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = "Cerca nelle conversazioni" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcons.Search(Modifier.size(16.dp), color = palette.inkFaint)
                Text(
                    text = "Cerca",
                    style = TextStyle(fontSize = 14.5.sp),
                    color = palette.inkFaint,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.ink)
                    .clickable(role = Role.Button, onClick = onNewChat)
                    .semantics { contentDescription = "Nuova chat dal menu" },
                contentAlignment = Alignment.Center
            ) {
                AppIcons.NewChat(Modifier.size(18.dp), color = palette.sheetBg)
            }
        }
    }
}

@Composable
private fun ClaudeDrawerConversationRow(
    conversation: AssistantConversation,
    active: Boolean,
    showLeadingPin: Boolean,
    palette: ClaudePalette,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val hasAttachmentsOrSources = remember(conversation) {
        conversation.messages.any { it.attachedFiles.isNotEmpty() || it.sources.isNotEmpty() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.activeRowBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = "Rimuovi dai fissati" },
                contentAlignment = Alignment.Center
            ) {
                ClaudePinGlyph(Modifier.size(14.dp), color = palette.inkMuted, filled = true)
            }
        }
        Text(
            text = conversationDisplayTitle(conversation),
            style = TextStyle(
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
            ),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (hasAttachmentsOrSources) {
            AppIcons.Document(Modifier.size(13.dp), color = palette.inkFaint)
        }
        if (!showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = "Fissa" },
                contentAlignment = Alignment.Center
            ) {
                ClaudePinGlyph(
                    Modifier.size(13.dp),
                    color = palette.inkFaint.copy(alpha = 0.45f),
                    filled = false
                )
            }
        }
    }
}

@Composable
private fun ClaudeDrawerShortcutRow(
    label: String,
    palette: ClaudePalette,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Text(
            text = label,
            style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Normal),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =================================================================================================
// Schermata di ricerca dedicata (fedele a Video 1 00:06–00:11)
// =================================================================================================

@Composable
internal fun ClaudeSearchOverlay(
    palette: ClaudePalette,
    conversations: List<AssistantConversation>,
    onClose: () -> Unit,
    onSelectConversation: (AssistantConversation) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val trimmed = query.trim()
    val filtered = remember(conversations, trimmed) {
        conversations
            .sortedWith(compareByDescending<AssistantConversation> { it.pinned }.thenByDescending { it.updatedAtMillis })
            .filter { conv ->
                trimmed.isEmpty() ||
                    conversationDisplayTitle(conv).contains(trimmed, ignoreCase = true) ||
                    conv.messages.any { it.text.contains(trimmed, ignoreCase = true) }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.shellBg)
    ) {
        // Barra superiore ricerca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Chiudi ricerca chat" },
                contentAlignment = Alignment.Center
            ) {
                ClaudeArrowLeftGlyph(color = palette.ink)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcons.Search(Modifier.size(17.dp), color = palette.inkMuted)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Cerca nelle chat",
                            style = TextStyle(fontSize = 15.sp),
                            color = palette.inkFaint
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = palette.ink,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(palette.ink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Campo ricerca chat" }
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { query = "" })
                            .semantics { contentDescription = "Svuota ricerca" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.Close(Modifier.size(15.dp), color = palette.inkMuted)
                    }
                }
            }
        }

        // Foglio bianco arrotondato con lista piatta e match in grassetto
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(palette.sheetBg)
        ) {
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (trimmed.isEmpty()) "Nessuna chat salvata" else "Nessun risultato per \"$trimmed\"",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val title = conversationDisplayTitle(conv)
                        val matchingSnippet = remember(conv, trimmed) {
                            if (trimmed.isEmpty() || title.contains(trimmed, ignoreCase = true)) null
                            else conv.messages.firstOrNull { it.text.contains(trimmed, ignoreCase = true) }
                                ?.text?.replace(Regex("\\s+"), " ")?.trim()?.take(88)
                        }
                        val hasDocs = remember(conv) {
                            conv.messages.any { it.attachedFiles.isNotEmpty() || it.sources.isNotEmpty() }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectConversation(conv) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = highlightSearchMatch(title, trimmed, palette.ink),
                                    style = TextStyle(fontSize = 15.5.sp, lineHeight = 21.sp),
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (matchingSnippet != null) {
                                    Text(
                                        text = highlightSearchMatch(matchingSnippet, trimmed, palette.inkMuted),
                                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                                        color = palette.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (conv.pinned) {
                                    ClaudePinGlyph(Modifier.size(13.dp), color = palette.inkFaint, filled = true)
                                }
                                if (hasDocs) {
                                    AppIcons.Document(Modifier.size(13.dp), color = palette.inkFaint)
                                }
                                Text(
                                    text = claudeCompactTimeLabel(conv.updatedAtMillis),
                                    style = TextStyle(fontSize = 13.sp),
                                    color = palette.inkFaint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun highlightSearchMatch(text: String, query: String, baseColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val match = lowerText.indexOf(lowerQuery, cursor)
            if (match < 0) {
                append(text.substring(cursor))
                break
            }
            if (match > cursor) append(text.substring(cursor, match))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                append(text.substring(match, match + query.length))
            }
            cursor = match + query.length
        }
    }
}

// =================================================================================================
// Composer espandibile (1 riga a riposo → 2 righe con selettore Modello/Ragionamento al focus)
// =================================================================================================

@Composable
internal fun ClaudeComposer(
    palette: ClaudePalette,
    draft: String,
    onDraftChange: (String) -> Unit,
    isThinking: Boolean,
    isIncognito: Boolean,
    hasMessages: Boolean,
    rotatingPlaceholders: List<String>,
    attachedFiles: List<AssistantAttachedFile>,
    onRemoveAttachedFile: (String) -> Unit,
    onOpenAttachedFile: (AssistantAttachedFile) -> Unit,
    attachmentError: String?,
    modelShortLabel: String,
    effortLabel: String,
    onOpenModelSheet: () -> Unit,
    onOpenAttachments: (() -> Unit)?,
    onStartVoice: (() -> Unit)?,
    onSend: () -> Unit,
    onFocusedChange: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val hasDraft = draft.isNotBlank() || attachedFiles.isNotEmpty()
    val canSend = hasDraft && !isThinking
    val expanded = focused || draft.isNotEmpty() || attachedFiles.isNotEmpty()
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 24.dp else 28.dp,
        animationSpec = ailaSpatialSpring(),
        label = "composerRadius"
    )
    val placeholderText = if (isIncognito) "Chiedi in incognito" else "Chiedi ad AILA"
    var placeholderIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(hasMessages, expanded, AppTheme.reduceMotion, AppTheme.liteEffects) {
        if (hasMessages || expanded || AppTheme.reduceMotion || AppTheme.liteEffects || rotatingPlaceholders.isEmpty()) {
            placeholderIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            delay(3600)
            placeholderIndex = (placeholderIndex + 1) % rotatingPlaceholders.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (attachedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachedFiles.forEach { file ->
                    Row(
                        modifier = Modifier
                            .widthIn(min = 170.dp, max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.pillBg)
                            .border(0.75.dp, palette.hairline, RoundedCornerShape(16.dp))
                            .clickable { onOpenAttachedFile(file) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcons.Document(Modifier.size(16.dp), color = palette.inkMuted)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pronto per l'invio",
                                style = TextStyle(fontSize = 11.sp),
                                color = palette.inkFaint
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isThinking) { onRemoveAttachedFile(file.localId) }
                                .semantics { contentDescription = "Rimuovi allegato ${file.name}" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Close(Modifier.size(13.dp), color = palette.inkMuted)
                        }
                    }
                }
            }
        }

        attachmentError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = palette.dangerInk,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        val shape = RoundedCornerShape(cornerRadius)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (AppTheme.isDarkMode) 0.dp else 2.dp,
                    shape = shape,
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x14000000)
                )
                .clip(shape)
                .background(palette.composerBg)
                .border(1.dp, palette.composerBorder, shape)
                .animateContentSize(ailaSpatialSpring())
                .padding(
                    start = if (expanded) 14.dp else 8.dp,
                    end = 8.dp,
                    top = if (expanded) 12.dp else 7.dp,
                    bottom = 7.dp
                )
        ) {
            if (expanded) {
                // Riga 1: campo testo multilinea
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 6.dp, bottom = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                            color = palette.inkFaint
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = true,
                        maxLines = 5,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = palette.ink,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(palette.ink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                focused = it.isFocused
                                onFocusedChange(it.isFocused)
                            }
                    )
                }

                // Riga 2: + | pillola Modello & Ragionamento | Mic | Invia / Onda
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenAttachments != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.pillBg)
                                .clickable(role = Role.Button, onClick = onOpenAttachments)
                                .semantics { contentDescription = "Menu allegati" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Plus(Modifier.size(18.dp), color = palette.ink)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Pillola selettore Modello + Ragionamento (come "Opus 5.5 | High ⌄")
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(palette.pillBg)
                            .clickable(role = Role.Button, onClick = onOpenModelSheet)
                            .padding(horizontal = 12.dp)
                            .semantics { contentDescription = "Seleziona modello e ragionamento" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = modelShortLabel,
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(Modifier.width(1.dp).height(12.dp).background(palette.hairline))
                        Text(
                            text = effortLabel,
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                            color = palette.inkMuted,
                            maxLines = 1
                        )
                        ClaudeChevronGlyph(Modifier.size(11.dp), color = palette.inkMuted)
                    }

                    Spacer(Modifier.weight(1f))

                    if (onStartVoice != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isThinking, role = Role.Button, onClick = onStartVoice)
                                .semantics { contentDescription = "Registra vocale" },
                            contentAlignment = Alignment.Center
                        ) {
                            ClaudeMicGlyph(Modifier.size(19.dp), color = palette.inkMuted)
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    ClaudeSendOrWaveButton(
                        palette = palette,
                        showSendArrow = hasDraft || onStartVoice == null,
                        canSend = canSend,
                        isThinking = isThinking,
                        onSend = onSend,
                        onVoiceFallback = { onStartVoice?.invoke() }
                    )
                }
            } else {
                // Stato compatto a 1 riga (identico a Video 1 00:00 e 00:14)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenAttachments != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.pillBg)
                                .clickable(role = Role.Button, onClick = onOpenAttachments)
                                .semantics { contentDescription = "Menu allegati" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Plus(Modifier.size(18.dp), color = palette.ink)
                        }
                        Spacer(Modifier.width(10.dp))
                    }

                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draft.isEmpty()) {
                            Crossfade(
                                targetState = placeholderIndex,
                                animationSpec = ailaFadeSpec(AilaDuration.Slow),
                                label = "claudePlaceholder"
                            ) { idx ->
                                Text(
                                    text = if (idx < 0 || rotatingPlaceholders.isEmpty()) placeholderText
                                    else rotatingPlaceholders[idx],
                                    style = TextStyle(fontSize = 15.5.sp, lineHeight = 20.sp),
                                    color = palette.inkFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.5.sp,
                                lineHeight = 20.sp,
                                color = palette.ink,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(palette.ink),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    focused = it.isFocused
                                    onFocusedChange(it.isFocused)
                                }
                        )
                    }

                    if (onStartVoice != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isThinking, role = Role.Button, onClick = onStartVoice)
                                .semantics { contentDescription = "Registra vocale" },
                            contentAlignment = Alignment.Center
                        ) {
                            ClaudeMicGlyph(Modifier.size(19.dp), color = palette.inkMuted)
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    ClaudeSendOrWaveButton(
                        palette = palette,
                        showSendArrow = hasDraft || onStartVoice == null,
                        canSend = canSend,
                        isThinking = isThinking,
                        onSend = onSend,
                        onVoiceFallback = { onStartVoice?.invoke() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeSendOrWaveButton(
    palette: ClaudePalette,
    showSendArrow: Boolean,
    canSend: Boolean,
    isThinking: Boolean,
    onSend: () -> Unit,
    onVoiceFallback: () -> Unit
) {
    val active = if (showSendArrow) canSend else !isThinking
    val bg = if (showSendArrow) {
        if (canSend) palette.ink else palette.pillBg
    } else {
        palette.ink
    }
    val fg = if (showSendArrow) {
        if (canSend) palette.sheetBg else palette.inkFaint
    } else {
        palette.sheetBg
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = active,
                role = Role.Button,
                onClick = { if (showSendArrow) onSend() else onVoiceFallback() }
            )
            .semantics { contentDescription = if (showSendArrow) "Invia" else "Avvia voce" },
        contentAlignment = Alignment.Center
    ) {
        if (showSendArrow) {
            AppIcons.ArrowUp(Modifier.size(18.dp), color = fg)
        } else {
            ClaudeWaveformGlyph(Modifier.size(17.dp), color = fg)
        }
    }
}

/** Microfono KMP-safe senza drawRoundRect/drawArc. */
@Composable
internal fun ClaudeMicGlyph(modifier: Modifier = Modifier.size(20.dp), color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1.6.dp.toPx())
        // Capsula microfono con bezier
        val cap = Path().apply {
            moveTo(w * 0.36f, h * 0.24f)
            cubicTo(w * 0.36f, h * 0.08f, w * 0.64f, h * 0.08f, w * 0.64f, h * 0.24f)
            lineTo(w * 0.64f, h * 0.48f)
            cubicTo(w * 0.64f, h * 0.64f, w * 0.36f, h * 0.64f, w * 0.36f, h * 0.48f)
            close()
        }
        drawPath(cap, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Culla inferiore curva
        val cradle = Path().apply {
            moveTo(w * 0.22f, h * 0.46f)
            cubicTo(w * 0.22f, h * 0.78f, w * 0.78f, h * 0.78f, w * 0.78f, h * 0.46f)
        }
        drawPath(cradle, color, style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.50f, h * 0.71f), Offset(w * 0.50f, h * 0.88f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.88f), Offset(w * 0.64f, h * 0.88f), stroke, StrokeCap.Round)
    }
}

// =================================================================================================
// Foglio Modello + Ragionamento (fedele a Video 1 00:16–00:23)
// =================================================================================================

@Composable
internal fun ClaudeModelAndEffortSheet(
    palette: ClaudePalette,
    options: List<ClaudeModelOption>,
    selectedProviderId: String,
    effortLevel: Int,
    onSelectProvider: (String) -> Unit,
    onSelectEffort: (Int) -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var showEffortPage by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.providerId == selectedProviderId } ?: options.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(palette.sheetBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Piccola maniglia superiore
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.hairline)
            )
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = showEffortPage,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(ailaNavigationSpring()) { it / 3 } + fadeIn(ailaFadeSpec(AilaDuration.Quick))) togetherWith
                            (slideOutHorizontally(ailaNavigationSpring()) { -it / 4 } + fadeOut(ailaFadeSpec(AilaDuration.Quick)))
                    } else {
                        (slideInHorizontally(ailaNavigationSpring()) { -it / 3 } + fadeIn(ailaFadeSpec(AilaDuration.Quick))) togetherWith
                            (slideOutHorizontally(ailaNavigationSpring()) { it / 4 } + fadeOut(ailaFadeSpec(AilaDuration.Quick)))
                    }
                },
                label = "claudeModelEffortPage"
            ) { isEffort ->
                if (!isEffort) {
                    // Pagina 1: Selettore Modello a ruota/pillola centrata
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(palette.pillBg)
                                    .clickable(role = Role.Button, onClick = onDismiss)
                                    .semantics { contentDescription = "Chiudi selettore modello" },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Close(Modifier.size(16.dp), color = palette.ink)
                            }
                            Text(
                                text = "Modello",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.ink,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        val selectedIdx = options.indexOfFirst { it.providerId == selectedOption.providerId }.coerceAtLeast(0)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            options.forEachIndexed { index, option ->
                                val isSelected = index == selectedIdx
                                val distance = kotlin.math.abs(index - selectedIdx)
                                val rowAlpha by animateFloatAsState(
                                    targetValue = when {
                                        isSelected -> 1f
                                        distance == 1 -> 0.56f
                                        else -> 0.28f
                                    },
                                    animationSpec = ailaFadeSpec(AilaDuration.Quick),
                                    label = "modelRowAlpha"
                                )
                                if (isSelected) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(palette.pillBg)
                                            .padding(start = 20.dp, end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                                            color = palette.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(100.dp))
                                                .clickable(role = Role.Button) { showEffortPage = true }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .semantics { contentDescription = "Modifica livello di ragionamento" },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ClaudeEffortDots(level = effortLevel, palette = palette)
                                            Text(
                                                text = ClaudeEffortLabels[effortLevel.coerceIn(0, ClaudeEffortLabels.lastIndex)],
                                                style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Normal),
                                                color = palette.inkMuted
                                            )
                                            AppIcons.ChevronRight(Modifier.size(13.dp), color = palette.inkMuted)
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(100.dp))
                                            .clickable(role = Role.Button) { onSelectProvider(option.providerId) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                            .alpha(rowAlpha)
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.Normal),
                                            color = palette.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = selectedOption.description,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            color = palette.inkMuted,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Impostazioni AI",
                                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                                color = palette.ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .clickable(role = Role.Button) {
                                        onDismiss()
                                        onOpenAiSettings()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    // Pagina 2: Slider tattile a pillola nera "Ragionamento" (Video 1 00:20–00:22)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(palette.pillBg)
                                    .clickable(role = Role.Button) { showEffortPage = false }
                                    .semantics { contentDescription = "Torna ai modelli" },
                                contentAlignment = Alignment.Center
                            ) {
                                ClaudeArrowLeftGlyph(Modifier.size(16.dp), color = palette.ink)
                            }
                            Text(
                                text = "Ragionamento",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.ink,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Spacer(Modifier.height(26.dp))

                        Crossfade(
                            targetState = ClaudeEffortLabels[effortLevel.coerceIn(0, ClaudeEffortLabels.lastIndex)],
                            animationSpec = ailaFadeSpec(AilaDuration.Quick),
                            label = "effortTitle"
                        ) { label ->
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium),
                                color = palette.ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        ClaudeEffortSlider(
                            level = effortLevel,
                            steps = ClaudeEffortLabels.size,
                            palette = palette,
                            onLevelChange = onSelectEffort
                        )

                        Spacer(Modifier.height(34.dp))

                        Text(
                            text = "Un ragionamento più alto produce risposte più approfondite e ponderate, ma può richiedere più tempo.",
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            color = palette.inkMuted,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/** Slider a pillola piena scura con pallini discreti e stanghetta bianca, trascinabile e cliccabile. */
@Composable
private fun ClaudeEffortSlider(
    level: Int,
    steps: Int,
    palette: ClaudePalette,
    onLevelChange: (Int) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val clamped = level.coerceIn(0, steps - 1)
    val targetFraction = (clamped + 1f) / steps.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0.24f, 1f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 460f),
        label = "effortFill"
    )

    fun stepFromX(x: Float): Int {
        val ratio = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 0.999f)
        return (ratio * steps).toInt().coerceIn(0, steps - 1)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.pillBg)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(steps) {
                detectTapGestures { offset ->
                    onLevelChange(stepFromX(offset.x))
                }
            }
            .pointerInput(steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onLevelChange(stepFromX(change.position.x))
                }
            }
            .semantics {
                contentDescription = "Livello di ragionamento: ${ClaudeEffortLabels[clamped]}"
            }
    ) {
        val fillWidth = maxWidth * animatedFraction
        // Pillola nera che cresce fino allo step scelto
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.ink)
        ) {
            // Stanghetta bianca verticale vicino al bordo destro del cursore
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .width(3.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(palette.sheetBg.copy(alpha = 0.9f))
            )
        }
        // Pallini discreti dei livelli
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until steps) {
                val insideFill = i <= clamped
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (i != clamped) {
                        Box(
                            modifier = Modifier
                                .size(if (insideFill) 5.dp else 4.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (insideFill) palette.sheetBg.copy(alpha = 0.42f)
                                    else palette.inkFaint.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// =================================================================================================
// Barra inferiore minimalista con pillola scorrevole e tooltip nero fluttuante al trascinamento
// (Fedele a Video 1 00:00 e Video 2 00:09–00:18 + tutte le tab di AILA)
// =================================================================================================

internal data class ClaudeNavTabItem(
    val key: String,
    val label: String,
    val mainTab: MainTab?,
    val hasUnread: Boolean
)

@Composable
internal fun ClaudeBottomNavBar(
    palette: ClaudePalette,
    unreadTabs: Set<MainTab>,
    onSelectMainTab: (MainTab) -> Unit
) {
    val items = remember(unreadTabs) {
        listOf(
            ClaudeNavTabItem("aila", "AILA", null, false),
            ClaudeNavTabItem("calendar", MainTab.CALENDAR.title, MainTab.CALENDAR, MainTab.CALENDAR in unreadTabs),
            ClaudeNavTabItem("class", MainTab.CLASS.title, MainTab.CLASS, MainTab.CLASS in unreadTabs),
            ClaudeNavTabItem("home", MainTab.HOME.title, MainTab.HOME, MainTab.HOME in unreadTabs),
            ClaudeNavTabItem("polls", MainTab.POLLS.title, MainTab.POLLS, MainTab.POLLS in unreadTabs),
            ClaudeNavTabItem("seatmap", MainTab.SEATMAP.title, MainTab.SEATMAP, MainTab.SEATMAP in unreadTabs)
        )
    }

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    var barWidthPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current

    val activeIndex = hoveredIndex ?: 0
    val animatedIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 500f),
        label = "claudeTabHighlight"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.sheetBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Tooltip nero fluttuante sopra la tab mentre si tiene premuto o si trascina (Video 2 00:10–00:13)
        val tipIndex = hoveredIndex
        if (tipIndex != null && tipIndex in items.indices) {
            val slotW = barWidthPx.toFloat() / items.size.toFloat()
            val centerPx = slotW * (tipIndex + 0.5f)
            val estHalfPx = with(density) { 46.dp.toPx() }
            val xPx = (centerPx - estHalfPx).coerceIn(4f, (barWidthPx - estHalfPx * 2f).coerceAtLeast(4f))
            Box(
                modifier = Modifier
                    .zIndex(10f)
                    .offset { IntOffset(xPx.roundToInt(), (-44.dp).roundToPx()) }
                    .shadow(4.dp, RoundedCornerShape(100.dp))
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.tooltipBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = items[tipIndex].label,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = palette.tooltipInk,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onSizeChanged { barWidthPx = it.width }
                .pointerInput(items) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val count = items.size
                        fun indexAt(x: Float): Int =
                            ((x / size.width.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)
                        var current = indexAt(down.position.x)
                        hoveredIndex = current
                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                val target = items[current].mainTab
                                hoveredIndex = null
                                if (target != null) onSelectMainTab(target)
                                break
                            }
                            current = indexAt(change.position.x)
                            hoveredIndex = current
                        }
                    }
                }
        ) {
            // Pillola morbida scorrevole dietro l'icona attiva / puntata
            val slotWidthDp = with(density) { (barWidthPx.toFloat() / items.size.toFloat()).toDp() }
            val pillWidth = 48.dp.coerceAtMost(slotWidthDp - 4.dp).coerceAtLeast(36.dp)
            val pillOffsetX = slotWidthDp * animatedIndex + (slotWidthDp - pillWidth) / 2f
            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .align(Alignment.CenterStart)
                    .width(pillWidth)
                    .height(36.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.pillBg)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == activeIndex
                    val iconColor = if (selected) palette.ink else palette.inkMuted
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = item.label
                                role = Role.Tab
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (item.mainTab) {
                                null -> ClaudeChatTabGlyph(Modifier.size(20.dp), color = iconColor, filled = selected)
                                MainTab.CALENDAR -> AppIcons.Calendar(Modifier.size(19.dp), color = iconColor)
                                MainTab.CLASS -> AppIcons.Document(Modifier.size(19.dp), color = iconColor)
                                MainTab.HOME -> AppIcons.Home(Modifier.size(19.dp), color = iconColor)
                                MainTab.POLLS -> AppIcons.Poll(Modifier.size(19.dp), color = iconColor)
                                MainTab.SEATMAP -> AppIcons.Chair(Modifier.size(19.dp), color = iconColor)
                            }
                            if (item.hasUnread) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-3).dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(palette.notifBlue)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// Toast fluttuante "Risposta pronta" (Video 2 00:16–00:17)
// =================================================================================================

@Composable
internal fun ClaudeFinishedToast(
    conversation: AssistantConversation?,
    palette: ClaudePalette,
    onOpen: (AssistantConversation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(conversation?.id) {
        if (conversation != null) {
            delay(5000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = conversation != null,
        enter = fadeIn(ailaFadeSpec(AilaDuration.Quick)) + slideInVertically(ailaSpatialSpring()) { it / 2 },
        exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)) + slideOutVertically(ailaSpatialSpring()) { it / 2 },
        modifier = modifier
    ) {
        conversation?.let { conv ->
            Column(
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 260.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x18000000), spotColor = Color(0x18000000))
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.toastBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) {
                        onOpen(conv)
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AilaAssistantMark(size = 13.dp)
                    Text(
                        text = conversationDisplayTitle(conv),
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Risposta pronta",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = palette.ink
                )
            }
        }
    }
}

internal fun conversationDisplayTitle(conversation: AssistantConversation): String =
    conversation.messages.firstOrNull { it.author == AssistantAuthor.USER }?.text
        ?.takeIf { it.startsWith("Prepara gli eventi da inserire nel calendario:") }
        ?.removePrefix("Prepara gli eventi da inserire nel calendario:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: conversation.title

/** Formato compatto stile Claude: "ora", "12m", "3h", "1g". */
internal fun claudeCompactTimeLabel(atMillis: Long): String {
    val diffMillis = (circolareplus.platform.currentTimeMillis() - atMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1 -> "ora"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}g"
    }
}
