package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import circolareplus.design.AilaDuration
import circolareplus.design.AilaLogoTile
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.GlassBase
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaSpatialSpring
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palette dell'Assistant interamente derivata dai token di [AppTheme]: rispetta tema chiaro/scuro,
 * stile grafico (Expressive, Liquid Glass, Nothing) e colore accento scelto dall'utente, mantenendo
 * la gerarchia pulita foglio-su-guscio.
 */
internal data class AssistantSurfacePalette(
    val shellBg: Color,
    val sheetBg: Color,
    val headerBtnBg: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val pillBg: Color,
    val activeRowBg: Color,
    val activeRowInk: Color,
    val userBubbleBg: Color,
    val userBubbleInk: Color,
    val composerBg: Color,
    val composerBorder: Color,
    val hairline: Color,
    val accentFill: Color,
    val onAccentFill: Color,
    val toastBg: Color,
    val dangerBg: Color,
    val dangerInk: Color
)

internal fun assistantSurfacePalette(): AssistantSurfacePalette {
    val dark = AppTheme.isDarkMode
    val glass = AppTheme.isGlass
    val nothing = AppTheme.isNothing

    val shell = when {
        nothing -> AppTheme.BackgroundLight
        glass -> AppTheme.GlassBase
        dark -> AppTheme.BackgroundLight
        else -> AppTheme.CardSurface
    }
    val sheet = when {
        nothing -> AppTheme.SurfaceWhite
        glass -> AppTheme.SurfaceWhite
        dark -> AppTheme.SurfaceWhite
        else -> AppTheme.SurfaceWhite
    }
    val pill = when {
        nothing -> AppTheme.TrackFill
        glass -> if (dark) Color(0xFF282A30) else Color(0xFFECEFF6)
        else -> AppTheme.TrackFill
    }
    val hairline = when {
        nothing -> AppTheme.Hairline
        dark -> AppTheme.FieldOutline.copy(alpha = 0.45f)
        else -> AppTheme.FieldOutline.copy(alpha = 0.35f)
    }

    return AssistantSurfacePalette(
        shellBg = shell,
        sheetBg = sheet,
        headerBtnBg = AppTheme.SurfaceWhite,
        ink = AppTheme.TextDark,
        inkMuted = AppTheme.TextMuted,
        inkFaint = AppTheme.TextFaint,
        pillBg = pill,
        activeRowBg = AppTheme.SelectionContainer,
        activeRowInk = AppTheme.OnSelection,
        userBubbleBg = AppTheme.TintBlue,
        userBubbleInk = AppTheme.TintBlueInk,
        composerBg = AppTheme.SurfaceWhite,
        composerBorder = AppTheme.FieldOutline.copy(alpha = if (dark) 0.55f else 0.45f),
        hairline = hairline,
        accentFill = if (nothing) AppTheme.TextDark else AppTheme.PrimaryBlue,
        onAccentFill = if (nothing) AppTheme.BackgroundLight else AppTheme.OnPrimaryButton,
        toastBg = if (dark) AppTheme.SurfaceContainerHighest else AppTheme.CardSurface,
        dangerBg = AppTheme.TintRed,
        dangerInk = AppTheme.TintRedInk
    )
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
            description = "Gemma 4 incluso in AILA: per lavoro quotidiano, circolari e studio senza chiavi."
        ),
        AssistantModelOption(
            providerId = AiProvider.GOOGLE_AI_STUDIO.id,
            title = "Google Gemini",
            shortLabel = "Gemini",
            description = "Gemini via API key personale di Google AI Studio, ideale per compiti complessi.",
            statusNote = if (hasGoogleKey) null else "Chiave API da configurare"
        ),
        AssistantModelOption(
            providerId = AiProvider.ON_DEVICE.id,
            title = cleanLocal,
            shortLabel = shortLocal,
            description = "Modello sul dispositivo: funziona anche offline e nessun dato esce dal telefono.",
            statusNote = if (localModelInstalled) null else "Modello da scaricare"
        ),
        AssistantModelOption(
            providerId = AiProvider.COMPATIBLE_API.id,
            title = compatTitle,
            shortLabel = "API",
            description = "Endpoint personalizzato compatibile con OpenAI o Anthropic.",
            statusNote = if (hasCompatibleConfig) null else "Endpoint da configurare"
        )
    )
}

// =================================================================================================
// Glifi vettoriali KMP-safe (solo Path, drawLine, drawCircle — nessun drawArc / drawRoundRect)
// =================================================================================================

/** Due linee orizzontali asimmetriche del pulsante menu del cassetto. */
@Composable
internal fun AssistantMenuGlyph(modifier: Modifier = Modifier.size(19.dp), color: Color) {
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
internal fun AssistantGhostGlyph(
    modifier: Modifier = Modifier.size(21.dp),
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

/** Puntina minimale per le chat fissate. */
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
internal fun AssistantCopyGlyph(modifier: Modifier = Modifier.size(18.dp), color: Color) {
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
internal fun AssistantOverflowGlyph(modifier: Modifier = Modifier.size(19.dp), color: Color) {
    Canvas(modifier) {
        val r = (size.minDimension * 0.085f).coerceAtLeast(1.5.dp.toPx())
        val cx = size.width / 2f
        drawCircle(color, r, Offset(cx, size.height * 0.24f))
        drawCircle(color, r, Offset(cx, size.height * 0.50f))
        drawCircle(color, r, Offset(cx, size.height * 0.76f))
    }
}

/** Ingranaggio minimale per il pulsante Impostazioni AI nel cassetto. */
@Composable
internal fun AssistantSettingsGearGlyph(modifier: Modifier = Modifier.size(19.dp), color: Color) {
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

/** Piccola chevron verso il basso o verso l'alto. */
@Composable
internal fun AssistantChevronGlyph(
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

/** Freccia sinistra pulita (←). */
@Composable
internal fun AssistantArrowLeftGlyph(modifier: Modifier = Modifier.size(19.dp), color: Color) {
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

/** Microfono KMP-safe senza drawRoundRect/drawArc. */
@Composable
internal fun AssistantMicGlyph(modifier: Modifier = Modifier.size(20.dp), color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1.6.dp.toPx())
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

/** Indicatore compatto a 4 tacche per il livello di ragionamento nel selettore Modello. */
@Composable
internal fun AssistantEffortDots(
    level: Int,
    palette: AssistantSurfacePalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until AssistantEffortLabels.size) {
            val selected = i == level.coerceIn(0, AssistantEffortLabels.lastIndex)
            Box(
                modifier = Modifier
                    .width(if (selected) 2.5.dp else 3.dp)
                    .height(if (selected) 12.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.accentFill else palette.inkFaint.copy(alpha = 0.65f))
            )
        }
    }
}

// =================================================================================================
// Header bar: Indietro + Menu cassetto a sinistra, Titolo + Modello al centro, Incognito / Azioni a destra
// =================================================================================================

@Composable
internal fun AssistantHeaderBar(
    palette: AssistantSurfacePalette,
    hasMessages: Boolean,
    isThinking: Boolean,
    conversationTitle: String?,
    modelSubtitle: String?,
    isIncognito: Boolean,
    isPinned: Boolean,
    onBackClick: () -> Unit,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sinistra: Indietro (44dp) + Menu cassetto (44dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onBackClick)
                    .semantics { contentDescription = "Indietro" },
                contentAlignment = Alignment.Center
            ) {
                AssistantArrowLeftGlyph(color = palette.ink)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenDrawer)
                    .semantics { contentDescription = "Apri menu chat" },
                contentAlignment = Alignment.Center
            ) {
                AssistantMenuGlyph(color = palette.ink)
            }
        }

        Spacer(Modifier.width(12.dp))

        // Centro: titolo chat + nome del modello sotto (al posto giusto!) oppure "Chat in incognito"
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (hasMessages) Alignment.CenterStart else Alignment.Center
        ) {
            when {
                hasMessages -> {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isIncognito) {
                                AssistantGhostGlyph(Modifier.size(15.dp), color = palette.inkMuted)
                            }
                            Text(
                                text = if (isIncognito) "Chat in incognito" else (conversationTitle ?: "AILA Assistant"),
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 15.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val sub = when {
                            isThinking -> "Sto elaborando…"
                            !modelSubtitle.isNullOrBlank() -> modelSubtitle
                            else -> null
                        }
                        if (sub != null) {
                            Text(
                                text = sub,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (isThinking) palette.accentFill else palette.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                isIncognito -> {
                    Text(
                        text = "Chat in incognito",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.inkMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Destra: a chat vuota solo Incognito (44dp); in conversazione pillola Nuova chat + ⋮ (44dp)
        Box {
            if (hasMessages) {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(palette.headerBtnBg)
                        .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 44.dp)
                            .clickable(role = Role.Button, onClick = onNewChat)
                            .semantics { contentDescription = "Nuova chat" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.NewChat(Modifier.size(19.dp), color = palette.ink)
                    }
                    Box(Modifier.width(0.75.dp).height(20.dp).background(palette.hairline))
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 44.dp)
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.headerBtnBg)
                        .border(0.75.dp, palette.hairline, CircleShape)
                        .clickable(role = Role.Button, onClick = onToggleIncognito)
                        .semantics {
                            contentDescription = if (isIncognito) "Esci dalla chat in incognito" else "Chat in incognito"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = isIncognito, animationSpec = ailaFadeSpec(AilaDuration.Quick), label = "incognitoBtn") { inc ->
                        if (inc) {
                            AppIcons.Close(Modifier.size(18.dp), color = palette.ink)
                        } else {
                            AssistantGhostGlyph(Modifier.size(20.dp), color = palette.ink)
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
                        leadingIcon = { AssistantPinGlyph(Modifier.size(16.dp), color = palette.inkMuted, filled = isPinned) },
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
// Cassetto laterale sinistro: solo Fissate, Recenti e footer Impostazioni AI / Cerca / Nuova chat
// =================================================================================================

@Composable
internal fun AssistantLeftDrawer(
    palette: AssistantSurfacePalette,
    drawerWidth: Dp,
    conversations: List<AssistantConversation>,
    activeConversationId: String?,
    onSelectConversation: (AssistantConversation) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onNewChat: () -> Unit,
    onOpenAiSettings: () -> Unit
) {
    var pinnedExpanded by remember { mutableStateOf(true) }
    var showAllRecents by remember { mutableStateOf(false) }

    val pinned = remember(conversations) {
        conversations.filter { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val recents = remember(conversations) {
        conversations.filterNot { it.pinned }.sortedByDescending { it.updatedAtMillis }
    }
    val visibleRecents = if (showAllRecents) recents else recents.take(10)

    Column(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight()
            .background(palette.shellBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // Intestazione con vero marchio AILA
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AilaLogoTile(size = 30.dp)
            Text(
                text = "AILA",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp
                ),
                color = palette.ink
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (pinned.isNotEmpty()) {
                item(key = "header-pinned") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pinnedExpanded = !pinnedExpanded }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fissate",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
                    text = "Recenti",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
                if (!showAllRecents && recents.size > 10) {
                    item(key = "show-more") {
                        Text(
                            text = "Mostra altre",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            color = palette.accentFill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAllRecents = true }
                                .padding(horizontal = 10.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        }

        // Footer comodo (46dp): Impostazioni AI | Cerca | Nuova chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenAiSettings)
                    .semantics { contentDescription = "Impostazioni AI" },
                contentAlignment = Alignment.Center
            ) {
                AssistantSettingsGearGlyph(color = palette.ink)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp))
                    .clickable(role = Role.Button, onClick = onOpenSearch)
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Cerca nelle conversazioni" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcons.Search(Modifier.size(17.dp), color = palette.inkMuted)
                Text(
                    text = "Cerca",
                    style = TextStyle(fontSize = 15.sp),
                    color = palette.inkFaint,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(palette.accentFill)
                    .clickable(role = Role.Button, onClick = onNewChat)
                    .semantics { contentDescription = "Nuova chat dal menu" },
                contentAlignment = Alignment.Center
            ) {
                AppIcons.NewChat(Modifier.size(19.dp), color = palette.onAccentFill)
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
    val hasAttachmentsOrSources = remember(conversation) {
        conversation.messages.any { it.attachedFiles.isNotEmpty() || it.sources.isNotEmpty() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.activeRowBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = "Rimuovi dai fissati" },
                contentAlignment = Alignment.Center
            ) {
                AssistantPinGlyph(
                    Modifier.size(15.dp),
                    color = if (active) palette.activeRowInk else palette.accentFill,
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
        if (hasAttachmentsOrSources) {
            AppIcons.Document(Modifier.size(14.dp), color = if (active) palette.activeRowInk.copy(alpha = 0.7f) else palette.inkFaint)
        }
        if (!showLeadingPin) {
            Box(
                modifier = Modifier
                    .size(34.dp)
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
// Schermata di ricerca dedicata (accessibile dal cassetto laterale e dal menu ⋮)
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
            .background(palette.shellBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Chiudi ricerca chat" },
                contentAlignment = Alignment.Center
            ) {
                AssistantArrowLeftGlyph(color = palette.ink)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.headerBtnBg)
                    .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcons.Search(Modifier.size(18.dp), color = palette.inkMuted)
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
                        cursorBrush = SolidColor(palette.accentFill),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Campo ricerca chat" }
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { query = "" })
                            .semantics { contentDescription = "Svuota ricerca" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.Close(Modifier.size(16.dp), color = palette.inkMuted)
                    }
                }
            }
        }

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
                                    AssistantPinGlyph(Modifier.size(13.dp), color = palette.accentFill, filled = true)
                                }
                                if (hasDocs) {
                                    AppIcons.Document(Modifier.size(13.dp), color = palette.inkFaint)
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
// Composer unificato: UN SOLO BasicTextField sempre montato (zero perdita focus),
// pillola Modello/Provider sempre visibile, UN SOLO microfono e bersagli touch >= 44dp
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
    modelShortLabel: String,
    effortLabel: String,
    onOpenModelSheet: () -> Unit,
    onOpenAttachments: (() -> Unit)?,
    onStartVoice: (() -> Unit)?,
    onSend: () -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.sheetBg)
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
                            .widthIn(min = 176.dp, max = 250.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.pillBg)
                            .border(0.75.dp, palette.hairline, RoundedCornerShape(16.dp))
                            .clickable { onOpenAttachedFile(file) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcons.Document(Modifier.size(16.dp), color = palette.accentFill)
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isThinking) { onRemoveAttachedFile(file.localId) }
                                .semantics { contentDescription = "Rimuovi allegato ${file.name}" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Close(Modifier.size(14.dp), color = palette.inkMuted)
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

        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (AppTheme.isDarkMode || AppTheme.isNothing) 0.dp else 2.dp,
                    shape = shape,
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x14000000)
                )
                .clip(shape)
                .background(palette.composerBg)
                .border(1.dp, palette.composerBorder, shape)
                .animateContentSize(ailaSpatialSpring())
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            // Riga 1: UN SOLO BasicTextField sempre montato -> focus e tastiera non cadono mai
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .padding(end = 6.dp, bottom = 8.dp),
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
                    cursorBrush = SolidColor(palette.accentFill),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Riga 2: + (44dp) | Pillola Modello/Provider (44dp, sempre visibile) | 1 solo Mic (44dp) | Invia (44dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenAttachments != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(palette.pillBg)
                            .clickable(role = Role.Button, onClick = onOpenAttachments)
                            .semantics { contentDescription = "Menu allegati" },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcons.Plus(Modifier.size(20.dp), color = palette.ink)
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Selettore Modello / Provider sempre raggiungibile in 1 tap
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(palette.pillBg)
                        .clickable(role = Role.Button, onClick = onOpenModelSheet)
                        .padding(horizontal = 14.dp)
                        .semantics { contentDescription = "Seleziona modello e ragionamento" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = modelShortLabel,
                        style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.width(1.dp).height(14.dp).background(palette.hairline))
                    Text(
                        text = effortLabel,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                        color = palette.inkMuted,
                        maxLines = 1
                    )
                    AssistantChevronGlyph(Modifier.size(12.dp), color = palette.inkMuted)
                }

                Spacer(Modifier.weight(1f))

                // Un solo microfono (nessun secondo tasto onda duplicato)
                if (onStartVoice != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isThinking, role = Role.Button, onClick = onStartVoice)
                            .semantics { contentDescription = "Registra vocale" },
                        contentAlignment = Alignment.Center
                    ) {
                        AssistantMicGlyph(Modifier.size(20.dp), color = palette.inkMuted)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // Un solo pulsante Invia (44dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) palette.accentFill else palette.pillBg)
                        .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                        .semantics { contentDescription = "Invia" },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcons.ArrowUp(
                        Modifier.size(20.dp),
                        color = if (canSend) palette.onAccentFill else palette.inkFaint
                    )
                }
            }
        }
    }
}

// =================================================================================================
// Foglio Modello / Provider + Ragionamento (con colori AILA e bersagli touch >= 44dp)
// =================================================================================================

@Composable
internal fun AssistantModelAndEffortSheet(
    palette: AssistantSurfacePalette,
    options: List<AssistantModelOption>,
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
            .background(Color.Black.copy(alpha = 0.36f))
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
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
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
                label = "assistantModelEffortPage"
            ) { isEffort ->
                if (!isEffort) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(palette.pillBg)
                                    .clickable(role = Role.Button, onClick = onDismiss)
                                    .semantics { contentDescription = "Chiudi selettore modello" },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Close(Modifier.size(18.dp), color = palette.ink)
                            }
                            Text(
                                text = "Modello e provider",
                                style = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.ink,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        val selectedIdx = options.indexOfFirst { it.providerId == selectedOption.providerId }.coerceAtLeast(0)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEachIndexed { index, option ->
                                val isSelected = index == selectedIdx
                                if (isSelected) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 58.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(palette.activeRowBg)
                                            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = option.title,
                                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                                color = palette.activeRowInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            option.statusNote?.let { note ->
                                                Text(
                                                    text = note,
                                                    style = TextStyle(fontSize = 11.5.sp),
                                                    color = palette.activeRowInk.copy(alpha = 0.75f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(100.dp))
                                                .background(palette.sheetBg.copy(alpha = 0.72f))
                                                .clickable(role = Role.Button) { showEffortPage = true }
                                                .padding(horizontal = 12.dp)
                                                .semantics { contentDescription = "Modifica livello di ragionamento" },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AssistantEffortDots(level = effortLevel, palette = palette)
                                            Text(
                                                text = AssistantEffortLabels[effortLevel.coerceIn(0, AssistantEffortLabels.lastIndex)],
                                                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                                                color = palette.ink
                                            )
                                            AppIcons.ChevronRight(Modifier.size(13.dp), color = palette.inkMuted)
                                        }
                                    }
                                } else {
                                    val distance = kotlin.math.abs(index - selectedIdx)
                                    val rowAlpha by animateFloatAsState(
                                        targetValue = if (distance == 1) 0.78f else 0.52f,
                                        animationSpec = ailaFadeSpec(AilaDuration.Quick),
                                        label = "modelRowAlpha"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .clickable(role = Role.Button) { onSelectProvider(option.providerId) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                            .alpha(rowAlpha),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                                            color = palette.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        option.statusNote?.let { note ->
                                            Text(
                                                text = note,
                                                style = TextStyle(fontSize = 12.sp),
                                                color = palette.inkMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = selectedOption.description,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            color = palette.inkMuted,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Configura in Impostazioni AI",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.accentFill,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .clickable(role = Role.Button) {
                                        onDismiss()
                                        onOpenAiSettings()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                            Text(
                                text = "Fatto",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.onAccentFill,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(palette.accentFill)
                                    .clickable(role = Role.Button, onClick = onDismiss)
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(palette.pillBg)
                                    .clickable(role = Role.Button) { showEffortPage = false }
                                    .semantics { contentDescription = "Torna ai modelli" },
                                contentAlignment = Alignment.Center
                            ) {
                                AssistantArrowLeftGlyph(Modifier.size(18.dp), color = palette.ink)
                            }
                            Text(
                                text = "Ragionamento",
                                style = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.ink,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Crossfade(
                            targetState = AssistantEffortLabels[effortLevel.coerceIn(0, AssistantEffortLabels.lastIndex)],
                            animationSpec = ailaFadeSpec(AilaDuration.Quick),
                            label = "effortTitle"
                        ) { label ->
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        AssistantEffortSlider(
                            level = effortLevel,
                            steps = AssistantEffortLabels.size,
                            palette = palette,
                            onLevelChange = onSelectEffort
                        )

                        Spacer(Modifier.height(30.dp))

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

/** Slider tattile a pillola nel colore primario di AILA, trascinabile e cliccabile. */
@Composable
private fun AssistantEffortSlider(
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
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
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
                contentDescription = "Livello di ragionamento: ${AssistantEffortLabels[clamped]}"
            }
    ) {
        val fillWidth = maxWidth * animatedFraction
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(29.dp))
                .background(palette.accentFill)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 13.dp)
                    .width(3.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(palette.onAccentFill.copy(alpha = 0.92f))
            )
        }
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
                                    if (insideFill) palette.onAccentFill.copy(alpha = 0.48f)
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
// Toast fluttuante "Risposta pronta" per conversazioni terminate in background
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
                    .widthIn(min = 184.dp, max = 264.dp)
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
                    AilaLogoTile(size = 16.dp)
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

/** Formato compatto: "ora", "12m", "3h", "1g". */
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
