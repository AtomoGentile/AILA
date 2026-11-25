package circolareplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import circolareplus.ai.AiProvider
import circolareplus.ai.assistant.AssistantAttachedFile
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.design.AilaAssistantBlue
import circolareplus.design.AilaAssistantTeal
import circolareplus.design.AilaAssistantViolet
import circolareplus.design.AilaDuration
import circolareplus.design.AilaLogoTile
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaMorphShape
import circolareplus.design.ailaSpatialSpring
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palette AILA Neural Expressive ispirata a Gemini e coerente con ACA:
 * neri profondi OLED in dark mode con bagliore radiale blu/indaco del brand,
 * tema chiaro luminoso coordinato e bersagli touch grandi (48dp).
 */
internal data class AssistantSurfacePalette(
    val canvasBg: Color,
    val drawerBg: Color,
    val elevatedBg: Color,
    val composerBg: Color,
    val composerBorder: Color,
    val pillBg: Color,
    val activeRowBg: Color,
    val activeRowInk: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val secondaryAccent: Color,
    val userBubbleBg: Color,
    val userBubbleInk: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val dangerBg: Color,
    val dangerInk: Color
)

internal fun assistantSurfacePalette(): AssistantSurfacePalette {
    val dark = AppTheme.isDarkMode
    val nothing = AppTheme.isNothing
    val accent = if (nothing) AppTheme.TextDark else AppTheme.PrimaryBlue
    val onAccent = if (nothing) AppTheme.BackgroundLight else AppTheme.OnPrimaryButton
    val secondary = if (nothing) AppTheme.TextMuted else AppTheme.SecondaryIndigo

    return if (dark) {
        AssistantSurfacePalette(
            canvasBg = if (nothing) Color(0xFF060606) else Color(0xFF07080D),
            drawerBg = if (nothing) Color(0xFF101010) else Color(0xFF12141B),
            elevatedBg = if (nothing) Color(0xFF191919) else Color(0xFF191C26),
            composerBg = if (nothing) Color(0xFF181818) else Color(0xFF171A24),
            composerBorder = Color.White.copy(alpha = 0.10f),
            pillBg = if (nothing) Color(0xFF222222) else Color(0xFF222634),
            activeRowBg = if (nothing) Color(0xFF262626) else accent.copy(alpha = 0.22f),
            activeRowInk = if (nothing) Color(0xFFF5F5F3) else Color(0xFFE7EEFF),
            ink = Color(0xFFF3F5FA),
            inkMuted = Color(0xFFA6AEC2),
            inkFaint = Color(0xFF737C91),
            hairline = Color.White.copy(alpha = 0.09f),
            accent = accent,
            onAccent = onAccent,
            secondaryAccent = secondary,
            userBubbleBg = if (nothing) Color(0xFF242424) else Color(0xFF1A2A52),
            userBubbleInk = Color(0xFFF2F6FF),
            glowPrimary = if (nothing) Color.Transparent else accent.copy(alpha = 0.28f),
            glowSecondary = if (nothing) Color.Transparent else secondary.copy(alpha = 0.20f),
            dangerBg = AppTheme.TintRed,
            dangerInk = AppTheme.TintRedInk
        )
    } else {
        AssistantSurfacePalette(
            canvasBg = if (nothing) AppTheme.BackgroundLight else Color(0xFFF7F9FF),
            drawerBg = if (nothing) AppTheme.SurfaceWhite else Color(0xFFEDF1FB),
            elevatedBg = Color(0xFFFFFFFF),
            composerBg = Color(0xFFFFFFFF),
            composerBorder = accent.copy(alpha = 0.16f),
            pillBg = if (nothing) AppTheme.TrackFill else Color(0xFFE8EEFA),
            activeRowBg = AppTheme.SelectionContainer,
            activeRowInk = AppTheme.OnSelection,
            ink = AppTheme.TextDark,
            inkMuted = AppTheme.TextMuted,
            inkFaint = AppTheme.TextFaint,
            hairline = AppTheme.FieldOutline.copy(alpha = 0.35f),
            accent = accent,
            onAccent = onAccent,
            secondaryAccent = secondary,
            userBubbleBg = AppTheme.TintBlue,
            userBubbleInk = AppTheme.TintBlueInk,
            glowPrimary = if (nothing) Color.Transparent else accent.copy(alpha = 0.12f),
            glowSecondary = if (nothing) Color.Transparent else secondary.copy(alpha = 0.09f),
            dangerBg = AppTheme.TintRed,
            dangerInk = AppTheme.TintRedInk
        )
    }
}

/** Bagliore radiale ambientale stile Gemini / AILA Neural Expressive. */
internal fun Modifier.assistantNeuralBackdrop(palette: AssistantSurfacePalette, prominent: Boolean): Modifier =
    this
        .background(palette.canvasBg)
        .drawBehind {
            if (palette.glowPrimary == Color.Transparent) return@drawBehind
            val factor = if (prominent) 1f else 0.35f
            val mainCenter = Offset(size.width * 0.52f, size.height * 0.68f)
            val mainRadius = size.maxDimension * 0.62f
            drawRect(
                brush = Brush.radialGradient(
                    0f to palette.glowPrimary.copy(alpha = palette.glowPrimary.alpha * factor),
                    0.55f to palette.glowSecondary.copy(alpha = palette.glowSecondary.alpha * factor * 0.7f),
                    1f to Color.Transparent,
                    center = mainCenter,
                    radius = mainRadius
                )
            )
            if (prominent) {
                val topCenter = Offset(size.width * 0.18f, size.height * 0.18f)
                val topRadius = size.maxDimension * 0.42f
                drawRect(
                    brush = Brush.radialGradient(
                        0f to palette.glowSecondary.copy(alpha = palette.glowSecondary.alpha * 0.55f),
                        1f to Color.Transparent,
                        center = topCenter,
                        radius = topRadius
                    )
                )
            }
        }

internal val AssistantEffortLabels = listOf("Rapido", "Medio", "Alto", "Massimo")

internal data class AssistantModelOption(
    val providerId: String,
    val title: String,
    val shortLabel: String,
    val description: String,
    val statusNote: String? = null
)

internal fun assistantModelOptions(
    localModelLabel: String,
    hasGoogleKey: Boolean = true,
    hasCompatibleConfig: Boolean = true,
    compatibleModelName: String = "",
    localModelInstalled: Boolean = true
): List<AssistantModelOption> {
    val cleanLocal = localModelLabel.trim().ifBlank { "AI locale" }
    val shortLocal = cleanLocal.substringBefore("(").trim().take(16).ifBlank { "Locale" }
    val compatTitle = compatibleModelName.trim().takeIf { it.isNotBlank() }?.let { "API · ${it.take(18)}" } ?: "API compatibile"
    return listOf(
        AssistantModelOption(
            providerId = AiProvider.AILA_CLOUD.id,
            title = "AILA Cloud",
            shortLabel = "AILA Cloud",
            description = "Gemma 4 incluso in AILA"
        ),
        AssistantModelOption(
            providerId = AiProvider.GOOGLE_AI_STUDIO.id,
            title = "Google Gemini",
            shortLabel = "Gemini",
            description = if (hasGoogleKey) "API key personale attiva" else "Chiave API da configurare"
        ),
        AssistantModelOption(
            providerId = AiProvider.ON_DEVICE.id,
            title = cleanLocal,
            shortLabel = shortLocal,
            description = if (localModelInstalled) "Modello locale offline" else "Modello locale da scaricare"
        ),
        AssistantModelOption(
            providerId = AiProvider.COMPATIBLE_API.id,
            title = compatTitle,
            shortLabel = "API",
            description = if (hasCompatibleConfig) "Endpoint personalizzato" else "Endpoint da configurare"
        )
    )
}

// =================================================================================================
// Glifi vettoriali KMP-safe (solo Path, drawLine, drawCircle — zero drawArc / drawRoundRect)
// =================================================================================================

@Composable
internal fun AssistantMenuGlyph(modifier: Modifier = Modifier.size(21.dp), color: Color) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.8.dp.toPx())
        drawLine(color, Offset(size.width * 0.14f, size.height * 0.36f), Offset(size.width * 0.86f, size.height * 0.36f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.14f, size.height * 0.64f), Offset(size.width * 0.86f, size.height * 0.64f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun AssistantGhostGlyph(
    modifier: Modifier = Modifier.size(22.dp),
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
            quadraticTo(w * 0.70f, h * 0.88f, w * 0.62f, h * 0.79f)
            quadraticTo(w * 0.50f, h * 0.88f, w * 0.38f, h * 0.79f)
            quadraticTo(w * 0.30f, h * 0.88f, w * 0.20f, h * 0.80f)
            close()
        }
        if (filled) drawPath(body, color)
        else drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val eyeColor = if (filled) eyeCutoutColor else color
        val eyeR = w * 0.055f
        drawCircle(eyeColor, radius = eyeR, center = Offset(w * 0.40f, h * 0.48f))
        drawCircle(eyeColor, radius = eyeR, center = Offset(w * 0.60f, h * 0.48f))
    }
}

@Composable
internal fun AssistantPinGlyph(modifier: Modifier = Modifier.size(16.dp), color: Color, filled: Boolean = false) {
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
        drawLine(color, Offset(w * 0.38f, h * 0.62f), Offset(w * 0.16f, h * 0.84f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun AssistantCopyGlyph(modifier: Modifier = Modifier.size(19.dp), color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.095f).coerceAtLeast(1.5.dp.toPx())
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

@Composable
internal fun AssistantOverflowGlyph(modifier: Modifier = Modifier.size(20.dp), color: Color) {
    Canvas(modifier) {
        val r = (size.minDimension * 0.085f).coerceAtLeast(1.6.dp.toPx())
        val cx = size.width / 2f
        drawCircle(color, r, Offset(cx, size.height * 0.24f))
        drawCircle(color, r, Offset(cx, size.height * 0.50f))
        drawCircle(color, r, Offset(cx, size.height * 0.76f))
    }
}

@Composable
internal fun AssistantSettingsGearGlyph(modifier: Modifier = Modifier.size(20.dp), color: Color) {
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

@Composable
internal fun AssistantChevronGlyph(
    modifier: Modifier = Modifier.size(14.dp),
    color: Color,
    pointUp: Boolean = false
) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.13f).coerceAtLeast(1.5.dp.toPx())
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

@Composable
internal fun AssistantArrowLeftGlyph(modifier: Modifier = Modifier.size(21.dp), color: Color) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.8.dp.toPx())
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

@Composable
internal fun AssistantMicGlyph(modifier: Modifier = Modifier.size(22.dp), color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.088f).coerceAtLeast(1.7.dp.toPx())
        val cap = Path().apply {
            moveTo(w * 0.36f, h * 0.24f)
            cubicTo(w * 0.36f, h * 0.08f, w * 0.64f, h * 0.08f, w * 0.64f, h * 0.24f)
            lineTo(w * 0.64f, h * 0.48f)
            cubicTo(w * 0.64f, h * 0.64f, w * 0.36f, h * 0.64f, w * 0.36f, h * 0.48f)
            close()
        }
        drawPath(cap, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
// Top bar stile Gemini mobile: Menu lista chat + Selettore modello subito accanto, NIENTE tasto
// indietro (ci sono le gesture), NIENTE scritta "Incognito" in alto
// =================================================================================================

@Composable
internal fun AssistantHeaderBar(
    palette: AssistantSurfacePalette,
    hasMessages: Boolean,
    isThinking: Boolean,
    modelShortLabel: String,
    effortLabel: String,
    isModelPickerOpen: Boolean,
    isIncognito: Boolean,
    isPinned: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleModelPicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleIncognito: () -> Unit,
    onNewChat: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteCurrent: () -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isModelPickerOpen) 180f else 0f,
        animationSpec = ailaSpatialSpring(),
        label = "modelPickerChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sinistra: Menu cronologia chat (48dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenDrawer)
                .semantics { contentDescription = "Apri menu chat" },
            contentAlignment = Alignment.Center
        ) {
            AssistantMenuGlyph(color = palette.ink)
        }

        Spacer(Modifier.width(4.dp))

        // Subito vicino al menu: Selettore Modello + Ragionamento come "Hydrogen Media ⌄" di Gemini!
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(if (isModelPickerOpen) palette.pillBg else Color.Transparent)
                .clickable(role = Role.Button, onClick = onToggleModelPicker)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = "Seleziona modello e ragionamento" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = modelShortLabel,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                ),
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isThinking) "…" else effortLabel,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (isThinking) palette.accent else palette.inkMuted,
                maxLines = 1
            )
            Box(modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }) {
                AssistantChevronGlyph(Modifier.size(14.dp), color = palette.inkMuted)
            }
        }

        Spacer(Modifier.weight(1f))

        // Destra: a chat vuota Incognito (48dp); in conversazione Nuova chat + ⋮ (48dp)
        Box {
            if (hasMessages) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onNewChat)
                            .semantics { contentDescription = "Nuova chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.NewChat(Modifier.size(21.dp), color = palette.ink)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { overflowOpen = true })
                            .semantics { contentDescription = "Altre opzioni chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        AssistantOverflowGlyph(color = palette.ink)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isIncognito) palette.accent else Color.Transparent)
                        .clickable(role = Role.Button, onClick = onToggleIncognito)
                        .semantics {
                            contentDescription = if (isIncognito) "Esci dalla chat in incognito" else "Chat in incognito"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = isIncognito, animationSpec = ailaFadeSpec(AilaDuration.Quick), label = "incognitoBtn") { inc ->
                        if (inc) {
                            AppIcons.Close(Modifier.size(20.dp), color = palette.onAccent)
                        } else {
                            AssistantGhostGlyph(Modifier.size(22.dp), color = palette.ink)
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { overflowOpen = false },
                containerColor = palette.elevatedBg
            ) {
                if (!isIncognito) {
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "Rimuovi dai fissati" else "Fissa conversazione", color = palette.ink) },
                        leadingIcon = { AssistantPinGlyph(Modifier.size(16.dp), color = palette.accent, filled = isPinned) },
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
                    leadingIcon = { AssistantGhostGlyph(Modifier.size(16.dp), color = palette.inkMuted) },
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
// Menu fluttuante animato Modello + Effort con pillola che aumenta (ancorato in alto come Gemini!)
// =================================================================================================

@Composable
internal fun AssistantModelPickerPopup(
    visible: Boolean,
    palette: AssistantSurfacePalette,
    options: List<AssistantModelOption>,
    selectedProviderId: String,
    effortLevel: Int,
    onSelectProvider: (String) -> Unit,
    onSelectEffort: (Int) -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var effortExpanded by remember(visible) { mutableStateOf(false) }
    val effortChevronRot by animateFloatAsState(
        targetValue = if (effortExpanded) 180f else 0f,
        animationSpec = ailaSpatialSpring(),
        label = "effortChevron"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize().zIndex(20f)
    ) {
        val popupMotion = Modifier.animateEnterExit(
            enter = scaleIn(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                initialScale = 0.84f,
                transformOrigin = TransformOrigin(0.26f, 0f)
            ) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f)
            ) { -36 } + fadeIn(tween(150)),
            exit = scaleOut(
                animationSpec = tween(160),
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.26f, 0f)
            ) + slideOutVertically(tween(160)) { -24 } + fadeOut(tween(140))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Box(
                modifier = popupMotion
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 62.dp, start = 16.dp, end = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 360.dp)
                        .fillMaxWidth()
                        .shadow(22.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.45f))
                        .clip(RoundedCornerShape(28.dp))
                        .background(palette.elevatedBg)
                        .border(1.dp, palette.hairline, RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .animateContentSize(ailaSpatialSpring())
                        .padding(vertical = 10.dp)
                ) {
                    // Lista modelli/provider stile Gemini (con spunta e selezione animate)
                    options.forEach { option ->
                        val selected = option.providerId == selectedProviderId
                        val rowBg by animateColorAsState(
                            targetValue = if (selected) palette.activeRowBg else Color.Transparent,
                            animationSpec = tween(180),
                            label = "modelRowBg"
                        )
                        val titleInk by animateColorAsState(
                            targetValue = if (selected) palette.activeRowInk else palette.ink,
                            animationSpec = tween(180),
                            label = "modelRowInk"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(rowBg)
                                .clickable(role = Role.Button) { onSelectProvider(option.providerId) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selected,
                                    enter = scaleIn(ailaSpatialSpring()) + fadeIn(ailaFadeSpec(AilaDuration.Quick)),
                                    exit = scaleOut(tween(120)) + fadeOut(tween(120))
                                ) {
                                    AppIcons.Check(Modifier.size(18.dp), color = palette.accent)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = option.title,
                                    style = TextStyle(
                                        fontSize = 15.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = titleInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = option.description,
                                    style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
                                    color = if (selected) palette.activeRowInk.copy(alpha = 0.78f) else palette.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Divisore + riga "Livello di ragionamento" che espande la pillola che aumenta!
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(palette.hairline))
                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { effortExpanded = !effortExpanded }
                            .padding(start = 46.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                            .semantics { contentDescription = "Livello di ragionamento" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Livello di ragionamento",
                                style = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Medium),
                                color = palette.ink
                            )
                            Text(
                                text = AssistantEffortLabels[effortLevel.coerceIn(0, AssistantEffortLabels.lastIndex)],
                                style = TextStyle(fontSize = 13.sp),
                                color = palette.accent
                            )
                        }
                        Box(modifier = Modifier.graphicsLayer { rotationZ = effortChevronRot }) {
                            AssistantChevronGlyph(Modifier.size(16.dp), color = palette.inkMuted)
                        }
                    }

                    // Slider tattile "pillola che aumenta" con animazione molleggiata
                    AnimatedVisibility(
                        visible = effortExpanded,
                        enter = expandVertically(ailaSpatialSpring()) + fadeIn(ailaFadeSpec(AilaDuration.Quick)),
                        exit = shrinkVertically(ailaSpatialSpring()) + fadeOut(tween(120))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(top = 6.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistantEffortPillSlider(
                                level = effortLevel,
                                steps = AssistantEffortLabels.size,
                                palette = palette,
                                onLevelChange = onSelectEffort
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AssistantEffortLabels.forEachIndexed { idx, label ->
                                    val active = idx == effortLevel.coerceIn(0, AssistantEffortLabels.lastIndex)
                                    Text(
                                        text = label,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (active) palette.accent else palette.inkFaint,
                                        modifier = Modifier.clickable { onSelectEffort(idx) }
                                    )
                                }
                            }
                        }
                    }

                    // Pulsante standard "Impostazioni AI" (coerente con i tasti AILA)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.hairline, RoundedCornerShape(100.dp))
                            .clickable(role = Role.Button) {
                                onDismiss()
                                onOpenAiSettings()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AssistantSettingsGearGlyph(Modifier.size(17.dp), color = palette.inkMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Impostazioni AI",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.ink
                        )
                    }
                }
            }
        }
    }
}

/** Pillola tattile che cresce fluidamente da sinistra verso destra al tocco o al trascinamento. */
@Composable
internal fun AssistantEffortPillSlider(
    level: Int,
    steps: Int,
    palette: AssistantSurfacePalette,
    onLevelChange: (Int) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val clamped = level.coerceIn(0, steps - 1)
    val targetFraction = (clamped + 1f) / steps.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0.24f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "effortPillGrow"
    )

    fun stepFromX(x: Float): Int {
        val ratio = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 0.999f)
        return (ratio * steps).toInt().coerceIn(0, steps - 1)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(palette.pillBg)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(steps) {
                detectTapGestures { offset -> onLevelChange(stepFromX(offset.x)) }
            }
            .pointerInput(steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onLevelChange(stepFromX(change.position.x))
                }
            }
            .semantics {
                contentDescription = "Livello di ragionamento: ${AssistantEffortLabels[clamped]}"
            }
    ) {
        val fillWidth = maxWidth * animatedFraction
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(26.dp))
                .background(palette.accent)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 11.dp)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(palette.onAccent.copy(alpha = 0.92f))
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until steps) {
                val insideFill = i <= clamped
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (i != clamped) {
                        Box(
                            modifier = Modifier
                                .size(if (insideFill) 5.dp else 4.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (insideFill) palette.onAccent.copy(alpha = 0.45f)
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
// Sidebar laterale stile Gemini con avatar INIZIALI del nome in basso a sinistra + Impostazioni AI
// =================================================================================================

@Composable
internal fun AssistantLeftDrawer(
    palette: AssistantSurfacePalette,
    drawerWidth: Dp,
    conversations: List<AssistantConversation>,
    activeConversationId: String?,
    userInitials: String,
    userDisplayName: String,
    activeModelLabel: String,
    onSelectConversation: (AssistantConversation) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onNewChat: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var pinnedExpanded by remember { mutableStateOf(true) }
    var showAllRecents by remember { mutableStateOf(false) }

    val pinned = remember(conversations) {
        conversations.filter { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val recents = remember(conversations) {
        conversations.filterNot { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val visibleRecents = if (showAllRecents) recents else recents.take(12)

    Column(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight()
            .background(palette.drawerBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // Intestazione sidebar: logo AILA + nome, Cerca + Chiudi (44dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                AilaLogoTile(size = 30.dp)
                Text(
                    text = "AILA",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = palette.ink
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSearch)
                    .semantics { contentDescription = "Cerca nelle conversazioni" },
                contentAlignment = Alignment.Center
            ) {
                AppIcons.Search(Modifier.size(20.dp), color = palette.inkMuted)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onCloseDrawer)
                    .semantics { contentDescription = "Chiudi menu laterale" },
                contentAlignment = Alignment.Center
            ) {
                AppIcons.Close(Modifier.size(19.dp), color = palette.inkMuted)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Pulsante grande "Nuova chat" (50dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(palette.activeRowBg)
                .clickable(role = Role.Button, onClick = onNewChat)
                .padding(horizontal = 18.dp)
                .semantics { contentDescription = "Nuova chat dal menu" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcons.NewChat(Modifier.size(20.dp), color = palette.activeRowInk)
            Text(
                text = "Nuova chat",
                style = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
                color = palette.activeRowInk
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (pinned.isNotEmpty()) {
                item(key = "header-pinned") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pinnedExpanded = !pinnedExpanded }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fissate",
                            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.inkMuted,
                            modifier = Modifier.weight(1f)
                        )
                        AssistantChevronGlyph(color = palette.inkMuted, pointUp = pinnedExpanded)
                    }
                }
                if (pinnedExpanded) {
                    items(pinned, key = { "pin-${it.id}" }) { conv ->
                        AssistantDrawerConversationRow(
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
                    text = "Tutte",
                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.inkMuted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            if (recents.isEmpty() && pinned.isEmpty()) {
                item(key = "empty-recents") {
                    Text(
                        text = "Nessuna conversazione salvata",
                        style = TextStyle(fontSize = 14.sp),
                        color = palette.inkFaint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(visibleRecents, key = { "rec-${it.id}" }) { conv ->
                    AssistantDrawerConversationRow(
                        conversation = conv,
                        active = conv.id == activeConversationId,
                        showLeadingPin = false,
                        palette = palette,
                        onClick = { onSelectConversation(conv) },
                        onTogglePin = { onTogglePin(conv.id) }
                    )
                }
                if (!showAllRecents && recents.size > 12) {
                    item(key = "show-more") {
                        Text(
                            text = "Mostra altre",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showAllRecents = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // Footer sidebar: iniziali del nome dell'utente in basso a sinistra + nome/modello + ingranaggio AI a destra
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onOpenAiSettings)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .semantics { contentDescription = "Impostazioni AI" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitials.ifBlank { "AI" },
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = palette.onAccent,
                    maxLines = 1
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userDisplayName.ifBlank { "Impostazioni AI" },
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeModelLabel,
                    style = TextStyle(fontSize = 12.sp),
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AssistantSettingsGearGlyph(Modifier.size(20.dp), color = palette.inkMuted)
            }
        }
    }
}

@Composable
private fun AssistantDrawerConversationRow(
    conversation: AssistantConversation,
    active: Boolean,
    showLeadingPin: Boolean,
    palette: AssistantSurfacePalette,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) palette.activeRowBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = "Rimuovi dai fissati" },
                contentAlignment = Alignment.Center
            ) {
                AssistantPinGlyph(
                    Modifier.size(15.dp),
                    color = if (active) palette.activeRowInk else palette.accent,
                    filled = true
                )
            }
        }
        Text(
            text = conversationDisplayTitle(conversation),
            style = TextStyle(
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (active) palette.activeRowInk else palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = "Fissa" },
                contentAlignment = Alignment.Center
            ) {
                AssistantPinGlyph(
                    Modifier.size(14.dp),
                    color = palette.inkFaint.copy(alpha = 0.55f),
                    filled = false
                )
            }
        }
    }
}

// =================================================================================================
// Schermata di ricerca dedicata nelle chat
// =================================================================================================

@Composable
internal fun AssistantSearchOverlay(
    palette: AssistantSurfacePalette,
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
            .background(palette.canvasBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.elevatedBg)
                    .border(1.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Chiudi ricerca chat" },
                contentAlignment = Alignment.Center
            ) {
                AssistantArrowLeftGlyph(color = palette.ink)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.elevatedBg)
                    .border(1.dp, palette.composerBorder, RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcons.Search(Modifier.size(19.dp), color = palette.inkMuted)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Cerca nelle chat",
                            style = TextStyle(fontSize = 15.5.sp),
                            color = palette.inkFaint
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.5.sp,
                            color = palette.ink,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Campo ricerca chat" }
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { query = "" })
                            .semantics { contentDescription = "Svuota ricerca" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.Close(Modifier.size(17.dp), color = palette.inkMuted)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (trimmed.isEmpty()) "Nessuna chat salvata" else "Nessun risultato per \"$trimmed\"",
                    style = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Medium),
                    color = palette.inkMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { conv ->
                    val title = conversationDisplayTitle(conv)
                    val matchingSnippet = remember(conv, trimmed) {
                        if (trimmed.isEmpty() || title.contains(trimmed, ignoreCase = true)) null
                        else conv.messages.firstOrNull { it.text.contains(trimmed, ignoreCase = true) }
                            ?.text?.replace(Regex("\\s+"), " ")?.trim()?.take(88)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.elevatedBg)
                            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                            .clickable { onSelectConversation(conv) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                                AssistantPinGlyph(Modifier.size(14.dp), color = palette.accent, filled = true)
                            }
                            Text(
                                text = assistantCompactTimeLabel(conv.updatedAtMillis),
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
// Composer pulito stile Gemini mobile (1 pillola ampia: + | Chiedi ad AILA | Mic | Invia 48dp)
// =================================================================================================

@Composable
internal fun AssistantComposer(
    palette: AssistantSurfacePalette,
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
    onOpenAttachments: (() -> Unit)?,
    onStartVoice: (() -> Unit)?,
    onSend: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val hasDraft = draft.isNotBlank() || attachedFiles.isNotEmpty()
    val canSend = hasDraft && !isThinking
    val placeholderText = if (isIncognito) "Chiedi in incognito" else "Chiedi ad AILA"
    var placeholderIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(hasMessages, draft.isEmpty(), AppTheme.reduceMotion, AppTheme.liteEffects) {
        if (hasMessages || draft.isNotEmpty() || AppTheme.reduceMotion || AppTheme.liteEffects || rotatingPlaceholders.isEmpty()) {
            placeholderIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            delay(3600)
            placeholderIndex = (placeholderIndex + 1) % rotatingPlaceholders.size
        }
    }

    val sendInteraction = remember { MutableInteractionSource() }
    val sendShape = ailaMorphShape(sendInteraction)
    val sendBg by animateColorAsState(
        targetValue = if (canSend) palette.accent else palette.pillBg,
        animationSpec = tween(if (AppTheme.reduceMotion) 60 else 160),
        label = "assistantSendBg"
    )
    val sendFg by animateColorAsState(
        targetValue = if (canSend) palette.onAccent else palette.inkFaint,
        animationSpec = tween(if (AppTheme.reduceMotion) 60 else 160),
        label = "assistantSendFg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (attachedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachedFiles.forEach { file ->
                    Row(
                        modifier = Modifier
                            .widthIn(min = 180.dp, max = 256.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.elevatedBg)
                            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                            .clickable { onOpenAttachedFile(file) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIcons.Document(Modifier.size(18.dp), color = palette.accent)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pronto per l'invio",
                                style = TextStyle(fontSize = 11.5.sp),
                                color = palette.inkFaint
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isThinking) { onRemoveAttachedFile(file.localId) }
                                .semantics { contentDescription = "Rimuovi allegato ${file.name}" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Close(Modifier.size(15.dp), color = palette.inkMuted)
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
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        val shape = RoundedCornerShape(30.dp)
        val borderBrush = if (focused && !AppTheme.isNothing) {
            Brush.linearGradient(listOf(AilaAssistantViolet, AilaAssistantBlue, AilaAssistantTeal))
        } else {
            SolidColor(palette.composerBorder)
        }

        // Pillola unica elegante stile Gemini mobile (1 BasicTextField fisso, tasti grandi 48dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (AppTheme.isDarkMode || AppTheme.isNothing) 8.dp else 4.dp,
                    shape = shape,
                    ambientColor = palette.accent.copy(alpha = 0.22f),
                    spotColor = palette.accent.copy(alpha = 0.22f)
                )
                .clip(shape)
                .background(palette.composerBg)
                .border(if (focused) 1.5.dp else 1.dp, borderBrush, shape)
                .animateContentSize(ailaSpatialSpring())
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenAttachments != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onOpenAttachments)
                        .semantics { contentDescription = "Menu allegati" },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcons.Plus(Modifier.size(23.dp), color = palette.ink)
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (draft.isEmpty()) {
                    Crossfade(
                        targetState = placeholderIndex,
                        animationSpec = ailaFadeSpec(AilaDuration.Slow),
                        label = "assistantPlaceholder"
                    ) { idx ->
                        Text(
                            text = if (idx < 0 || rotatingPlaceholders.isEmpty()) placeholderText
                            else rotatingPlaceholders[idx],
                            style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                            color = palette.inkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    maxLines = 5,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = palette.ink,
                        fontFamily = FontFamily.Default
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
            }

            if (onStartVoice != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isThinking, role = Role.Button, onClick = onStartVoice)
                        .semantics { contentDescription = "Registra vocale" },
                    contentAlignment = Alignment.Center
                ) {
                    AssistantMicGlyph(Modifier.size(22.dp), color = palette.inkMuted)
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(sendShape)
                    .background(sendBg)
                    .clickable(
                        interactionSource = sendInteraction,
                        indication = null,
                        enabled = canSend,
                        role = Role.Button,
                        onClick = onSend
                    )
                    .semantics { contentDescription = "Invia" },
                contentAlignment = Alignment.Center
            ) {
                AppIcons.ArrowUp(Modifier.size(21.dp), color = sendFg)
            }
        }
    }
}

// =================================================================================================
// Toast fluttuante "Risposta pronta"
// =================================================================================================

@Composable
internal fun AssistantFinishedToast(
    conversation: AssistantConversation?,
    palette: AssistantSurfacePalette,
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
                    .widthIn(min = 190.dp, max = 270.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = palette.accent.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.elevatedBg)
                    .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) {
                        onOpen(conv)
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AilaLogoTile(size = 17.dp)
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
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
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

internal fun assistantCompactTimeLabel(atMillis: Long): String {
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
