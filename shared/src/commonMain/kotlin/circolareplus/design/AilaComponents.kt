package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Componenti condivisi del linguaggio grafico AILA.
 *
 * Perché esistono: prima ogni schermata si scriveva a mano intestazione, card, riquadri icona e
 * pulsanti, ognuna con i propri esadecimali e le proprie spaziature. Il risultato era un'app
 * ordinata ma che non somigliava al mockup — mancavano proprio gli elementi che nel mockup si
 * ripetono ovunque: il riquadro icona colorato accanto a ogni riga, l'ombra morbida sotto le
 * card, il gradiente nei pulsanti e nei pannelli, il marchio in cima alle schermate.
 *
 * Scelta di fondo, confermata con Simone: nel mockup solo la Home ha il pannello a gradiente;
 * tutte le altre schermate hanno intestazione chiara.
 */

/**
 * Il marchio AILA. Ora è il segno vettoriale di [AilaLogoTile] e non più il PNG: quello è
 * un'immagine senza canale alpha, quindi si portava dietro il proprio fondo blu notte e a 24dp
 * si impastava. Vedi la nota estesa in AilaLogo.kt.
 */
@Composable
fun AilaBrandMark(size: Dp = 26.dp, modifier: Modifier = Modifier) {
    AilaLogoTile(size = size, modifier = modifier)
}

/**
 * Intestazione chiara standard: marchio grande a sinistra, titolo e sottotitolo accanto, e uno
 * slot di azione a destra. Prima il marchio stava piccolo su una riga a sé con la scritta "AILA"
 * e il titolo sotto: tre righe per dire una cosa, e il logo si perdeva. Ora il logo fa da
 * "avatar" della schermata e il titolo gli sta accanto, in un'unica fascia più compatta.
 *
 * Nessuna animazione propria: il movimento al cambio di tab lo da' la transizione in MainAppShell.
 */
@Composable
fun AilaScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showBrand: Boolean = true,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(AppTheme.BackgroundLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    top = AppTheme.Space20,
                    bottom = AppTheme.Space16
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBrand) {
                AilaBrandMark(size = 44.dp)
                Spacer(modifier = Modifier.width(AppTheme.Space12))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Titoli grandi: "large title" di iOS in Glass, "headline" di Material in Expressive.
                Text(
                    text = title,
                    fontSize = if (AppTheme.isGlass) 28.sp else 26.sp,
                    fontWeight = if (AppTheme.isGlass) FontWeight.Bold else FontWeight.Medium,
                    color = AppTheme.TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = AppTheme.TextMuted,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (action != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space12))
                action()
            }
            // Avatar: profilo e impostazioni (solo nelle schermate delle tab).
            val profileEntry = LocalProfileEntry.current
            if (profileEntry != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                AilaProfileButton(profileEntry)
            }
        }
        // Niente riga divisoria: in entrambi gli stili la barra in alto ha lo stesso fondo
        // della schermata (grandi titoli di iOS, top app bar "surface" di Material).
    }
}

/**
 * Barra superiore delle schermate aperte "sopra" le tab (dettaglio circolare, Scheda Classe,
 * Sondaggi, Notifiche): freccia indietro + titolo, stessa altezza e stesso divisore
 * dell'intestazione normale, così passare da una all'altra non fa "saltare" il layout.
 */
@Composable
fun AilaBackBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(AppTheme.BackgroundLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.Space12,
                    end = AppTheme.Space16,
                    top = AppTheme.Space20,
                    bottom = AppTheme.Space12
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppTheme.TintSlate)
                    .ailaPressable(pressedScale = 0.9f) { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                // Era il carattere "←": un glifo di testo, quindi disegnato dal sistema operativo,
                // diverso fra Android e iOS e di peso incoerente con le altre icone.
                AppIcons.ChevronLeft(modifier = Modifier.size(19.dp), color = AppTheme.TextDark)
            }
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                action()
            }
        }
        // Niente riga divisoria: in entrambi gli stili la barra in alto ha lo stesso fondo
        // della schermata (grandi titoli di iOS, top app bar "surface" di Material).
    }
}

/**
 * Selettore a segmenti (mockup: "Info / Membri / Materiali"). Usato per Circolari/Bacheca dentro
 * la tab "Classe" e per Accedi/Registrati nel login: prima erano chip identiche a quelle dei
 * filtri di contenuto, quindi non si capiva che cambiavano schermata invece di filtrare la lista.
 */
@Composable
fun AilaSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: Any? = null
) {
    // Il pill non compare/scompare più a scatti su un segmento o sull'altro: è un unico riquadro
    // condiviso che scivola e cambia larghezza dall'uno all'altro. Niente scala/pressione di tocco
    // qui (quella resta ai pulsanti veri) — questa è un'animazione di posizione, non di feedback.
    val interactionSources = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
    // Altezza reale della riga di tab, misurata via onSizeChanged: `fillMaxHeight()` sull'indicatore
    // prendeva per buono il vincolo verticale ereditato dal contenitore esterno (spesso molto più
    // alto della riga stessa), quindi il pill si gonfiava a riempire tutto lo spazio disponibile
    // invece di restare alto quanto i tab.
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // La pillola si muove subito (stato locale); la schermata cambia contenuto un paio di
    // fotogrammi dopo. Prima le due cose partivano nello stesso fotogramma: costruire la sezione
    // nuova (es. Storico dei sondaggi) rubava il primo fotogramma all'animazione e la pillola
    // "saltava" — il microscatto alla selezione.
    var visualIndex by remember { mutableStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) { visualIndex = selectedIndex }
    val selectScope = androidx.compose.runtime.rememberCoroutineScope()

    // Capsula in entrambi gli stili. Glass: binario grigio traslucido e selezione bianca in
    // rilievo con testo scuro (segmented control di iOS). Expressive: binario tonale e selezione
    // "secondary container", con la molla piu' vivace di Material.
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(AppTheme.TrackFill)
            .then(if (AppTheme.isGlass) Modifier.border(1.dp, AppTheme.GlassEdge, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / labels.size
        val indicatorOffset = animateDpAsState(
            targetValue = segmentWidth * visualIndex,
            animationSpec = ailaSpatialSpring(),
            label = "segmentedIndicatorOffset"
        )

        if (rowHeightPx > 0) {
            val rowHeight = with(density) { rowHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.value.roundToPx(), 0) }
                    .width(segmentWidth)
                    .height(rowHeight)
                    .then(
                        if (AppTheme.isGlass) Modifier.shadow(3.dp, RoundedCornerShape(percent = 50))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(percent = 50))
                    .background(AppTheme.SelectionFill)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeightPx = it.height }
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == visualIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSources[index],
                            indication = null
                        ) {
                            if (index != visualIndex) {
                                visualIndex = index
                                selectScope.launch {
                                    androidx.compose.runtime.withFrameNanos { }
                                    androidx.compose.runtime.withFrameNanos { }
                                    onSelect(index)
                                }
                            }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AppTheme.OnSelection else AppTheme.TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Contenitore di chip filtro con l'indicatore condiviso che scivola: stessa idea di
 * [AilaSegmentedTabs] ma per chip di larghezza diversa (ogni etichetta è lunga a modo suo, es.
 * "Tutte" / "In analisi" / "Non rilevanti") e, quando serve, dentro una riga che scorre
 * orizzontalmente (bacheca, circolari, calendario, ricerca). Le chip restano le
 * [AnimatedFilterChip] di sempre, passate con `drawSelectionBackground = false`: il colore non lo
 * disegna più la singola chip al proprio posto quando si seleziona, lo fa questo unico riquadro
 * che scivola dall'una all'altra — la "goccia" già usata per Sondaggio/Storico, generalizzata a
 * misure diverse e allo scorrimento.
 *
 * `content` riceve `chipModifier(index)`: va aggiunto al modifier di ogni chip perché il
 * riquadro sappia dove e quanto è larga.
 */
@Composable
fun AilaSlidingChipRow(
    selectedIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable (chipModifier: (Int) -> Modifier) -> Unit
) {
    var chipBounds by remember(itemCount) { mutableStateOf(List(itemCount) { Rect.Zero }) }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val target = chipBounds.getOrNull(selectedIndex)?.takeIf { it != Rect.Zero }
    val indicatorSpec = ailaSpatialSpring<Dp>()
    val animatedLeft by animateDpAsState(
        targetValue = target?.let { with(density) { it.left.toDp() } } ?: 0.dp,
        animationSpec = indicatorSpec,
        label = "chipRowIndicatorX"
    )
    val animatedWidth by animateDpAsState(
        targetValue = target?.let { with(density) { it.width.toDp() } } ?: 0.dp,
        animationSpec = indicatorSpec,
        label = "chipRowIndicatorWidth"
    )

    Box(
        modifier = modifier
            // Un'unica barra, come le tab segmentate — non più tante chip separate: prima ogni
            // chip da inattiva restava una sua piccola card bianca bordata, fluttuante nello
            // spazio invece che dentro un contenitore comune.
            .clip(RoundedCornerShape(percent = 50))
            .background(AppTheme.TrackFill)
            .then(if (AppTheme.isGlass) Modifier.border(1.dp, AppTheme.GlassEdge, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(4.dp)
            .then(
                if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier
            )
    ) {
        if (target != null) {
            Box(
                modifier = Modifier
                    .ailaAnimatedBar(left = { animatedLeft }, width = { animatedWidth }, top = with(density) { target.top.toDp() })
                    .height(with(density) { target.height.toDp() })
                    .then(
                        if (AppTheme.isGlass) Modifier.shadow(3.dp, RoundedCornerShape(percent = 50))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(percent = 50))
                    .background(AppTheme.SelectionFill)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            content { index ->
                Modifier.onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInParent()
                    val newRect = Rect(
                        pos.x,
                        pos.y,
                        pos.x + coordinates.size.width,
                        pos.y + coordinates.size.height
                    )
                    if (chipBounds.getOrNull(index) != newRect) {
                        chipBounds = chipBounds.toMutableList().also { list ->
                            while (list.size <= index) list.add(Rect.Zero)
                            list[index] = newRect
                        }
                    }
                }
            }
        }
    }
}

/** La superficie base di tutte le liste; l'aspetto dipende dallo stile (Glass/Expressive). */
@Composable
fun AilaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = AppTheme.CardSurface,
    /** Chiave "logica" dell'elemento per il ritorno del container transform (vedi ailaTransformOrigin). */
    transformKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Glass: vetro traslucido (riflesso in alto, filo di luce sul bordo) sopra lo sfondo a
    // macchie di colore; le card colorate diventano vetro tinto. Expressive: superficie tonale
    // piena, senza bordo ne' ombra (vedi AppTheme.CardSurface).
    val glass = AppTheme.isGlass
    val shape = RoundedCornerShape(AppTheme.CardCornerRadius)
    val glassTint = if (glass && containerColor != AppTheme.CardSurface) {
        containerColor.copy(alpha = minOf(containerColor.alpha, 0.018f))
    } else null
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (glass) Color.Transparent else containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
        modifier = modifier
            .fillMaxWidth()
            // Scala più contenuta delle righe: su una superficie grande il 3% si nota già.
            .then(
                if (onClick != null) {
                    // Origine del container transform: toccata, la card si allarga nella pagina.
                    Modifier
                        .ailaTransformOrigin(AppTheme.CardCornerRadius, liveKey = transformKey)
                        .ailaPressable(pressedScale = 0.985f) { onClick() }
                } else Modifier
            )
            .then(if (glass) Modifier.ailaGlassSurface(shape, tint = glassTint) else Modifier),
        content = content
    )
}

/** Riquadro icona colorato: l'elemento che nel mockup accompagna ogni riga di ogni lista. */
@Composable
fun AilaIconTile(
    tint: Color = AppTheme.TintBlue,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    // Material Expressive: il contenitore dell'icona e' una forma "cookie" a 9 lobi, come nelle
    // liste di Android 16. Glass: quadrato arrotondato (le icone delle Impostazioni di iOS).
    Box(
        modifier = modifier
            .size(size)
            .clip(if (AppTheme.isGlass) RoundedCornerShape(size / 3) else ailaCookieShape(9, 0.09f))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Riga standard "icona + titolo + sottotitolo + freccia" dentro una card. Copre la Home, il
 * Profilo, le notifiche e le liste di accesso rapido, che prima erano tutte scritte a mano.
 */
@Composable
fun AilaListRow(
    title: String,
    subtitle: String? = null,
    tint: Color = AppTheme.TintBlue,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .ailaTransformOrigin(AppTheme.SmallElementRadius)
                        .ailaPressable(pressedScale = 0.98f) { onClick() }
                } else Modifier
            )
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AilaIconTile(tint = tint, icon = icon)
            Spacer(modifier = Modifier.width(AppTheme.Space12))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted,
                    maxLines = 2
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            trailing()
        } else if (showChevron) {
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            AppIcons.ChevronRight(modifier = Modifier.size(16.dp), color = AppTheme.TextFaint)
        }
    }
}

/**
 * Pulsante primario col riempimento sfumato del brand. Non è un Button di Material perché quello
 * accetta solo un colore pieno, e nel mockup i pulsanti sono sfumati.
 */
@Composable
fun AilaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
    compact: Boolean = false,
    // Versione "da pollice": altezza minima 48dp e testo 15sp, per i punti in cui il pulsante è
    // l'azione principale di una finestra e non deve mancare il tocco.
    large: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = ailaMorphShape(interactionSource)
    Box(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            // Anche un pulsante puo' essere l'origine di un container transform (es. "Calcola
            // 3 proposte": la pagina delle proposte si apre dal pulsante).
            .ailaTransformOrigin(24.dp)
            .then(if (large) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(shape)
            .then(
                if (enabled) Modifier.background(AppTheme.PrimaryGradient)
                else Modifier.background(AppTheme.TintSlate)
            )
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .then(
                // Glass: la velatura di vetro che si accende dal punto del tocco. Expressive: il
                // feedback e' la forma che si deforma (ailaMorphShape), niente velo.
                if (AppTheme.isGlass) Modifier.ailaGlassOverlay(interactionSource, enabled = enabled, tint = Color.White)
                else Modifier
            )
            .padding(
                horizontal = if (large) AppTheme.Space20 else if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (enabled) Color.White else AppTheme.TextFaint
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon(contentColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = if (large) 15.sp else if (compact) 12.sp else 13.sp,
                fontWeight = if (AppTheme.isGlass) FontWeight.SemiBold else FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Forma dei pulsanti: capsula a riposo. In Material Expressive, mentre lo si preme, il pulsante si
 * "schiaccia" in un quadrato arrotondato e torna capsula al rilascio con una molla — il feedback
 * tipico di M3 Expressive. In Glass resta capsula.
 */
@Composable
fun ailaMorphShape(interactionSource: MutableInteractionSource, pressedPercent: Int = 22): RoundedCornerShape {
    val pressed by interactionSource.collectIsPressedAsState()
    val percent by animateFloatAsState(
        targetValue = if (pressed && !AppTheme.isGlass) pressedPercent.toFloat() else 50f,
        animationSpec = if (pressed) spring(stiffness = 1400f) else spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "ailaMorphShape"
    )
    return RoundedCornerShape(percent = percent.toInt().coerceIn(0, 50))
}

/**
 * Come [ailaMorphShape] ma ritaglia nel livello grafico: la forma che si schiaccia e ritorna
 * tonda si legge solo nella fase di disegno, quindi il pulsante non si ricompone a ogni
 * fotogramma dell'animazione (era uno dei micro-scatti al tocco dei pulsanti della Home). A
 * riposo e' sempre un cerchio pieno: la molla che rimbalza viene tagliata al 50%.
 */
@Composable
fun Modifier.ailaMorphClip(interactionSource: MutableInteractionSource, pressedPercent: Int = 22): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val percent = remember { Animatable(50f) }
    val squash = pressed && !AppTheme.isGlass
    LaunchedEffect(squash) {
        if (squash) percent.animateTo(pressedPercent.toFloat(), spring(stiffness = 1400f))
        else percent.animateTo(50f, spring(dampingRatio = 0.5f, stiffness = 600f))
    }
    return graphicsLayer {
        val corner = size.minDimension * percent.value.coerceIn(0f, 50f) / 100f
        shape = RoundedCornerShape(androidx.compose.foundation.shape.CornerSize(corner))
        clip = true
    }
}

/** Scala alla pressione (Glass), letta nel graphicsLayer. */
@Composable
fun Modifier.ailaPressScale(interactionSource: MutableInteractionSource, pressedScale: Float): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "ailaPressScale"
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Pulsante secondario: stesso ingombro del primario ma solo contorno. */
@Composable
fun AilaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    large: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppTheme.ButtonCornerRadius)
    Box(
        modifier = modifier
            .then(if (large) Modifier.heightIn(min = 48.dp) else Modifier)
            .then(
                if (AppTheme.isGlass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(AppTheme.SurfaceWhite).border(1.dp, AppTheme.FieldOutline, shape)
            )
            .ailaGlassPressable(tint = AppTheme.PrimaryBlue) { onClick() }
            .padding(
                horizontal = if (large) AppTheme.Space20 else if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon(AppTheme.TextDark)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = if (large) 15.sp else if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1
            )
        }
    }
}

/** Pulsante pieno per azioni distruttive ("Elimina"): stesso ingombro di [AilaPrimaryButton], ma
 * in tinta rossa invece che nel gradiente del brand — quello resta riservato alle azioni positive. */
@Composable
fun AilaDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    large: Boolean = false
) {
    val shape = RoundedCornerShape(AppTheme.ButtonCornerRadius)
    Box(
        modifier = modifier
            .then(if (large) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(shape)
            .background(AppTheme.TintRed)
            .ailaGlassPressable(tint = Color.White) { onClick() }
            .padding(
                horizontal = if (large) AppTheme.Space20 else if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (large) 15.sp else if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TintRedInk,
            maxLines = 1
        )
    }
}

/**
 * Finestra di conferma standard (es. "Eliminare la proposta?"): stessi raggi, colori e pulsanti
 * del resto dell'app invece dell'`AlertDialog` grezzo di Material, che risaltava come l'unico
 * elemento "di sistema" in mezzo a schermate tutte disegnate con questo linguaggio grafico.
 */
@Composable
fun AilaConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Elimina",
    dismissLabel: String = "Annulla",
    isDestructive: Boolean = true
) {
    // Larga quasi quanto lo schermo e con testo e pulsanti "da pollice": la versione precedente
    // (280dp, titolo 17sp, pulsanti da 12sp) sembrava un'etichetta e i pulsanti si mancavano.
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        // Larga quasi quanto lo schermo sul telefono, non oltre MaxDialogWidth su tablet/iPad.
        modifier = Modifier
            .widthIn(max = circolareplus.design.MaxDialogWidth)
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space20),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
        },
        text = {
            Text(text = message, fontSize = 15.sp, color = AppTheme.TextMuted, lineHeight = 22.sp)
        },
        confirmButton = {
            if (isDestructive) {
                AilaDestructiveButton(text = confirmLabel, onClick = onConfirm, large = true)
            } else {
                AilaPrimaryButton(text = confirmLabel, onClick = onConfirm, large = true)
            }
        },
        dismissButton = {
            AilaSecondaryButton(text = dismissLabel, onClick = onDismiss, large = true)
        }
    )
}

/** Titolo di sezione con link opzionale a destra ("Prossimi eventi — Vedi tutti"). */
@Composable
fun AilaSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Material: etichetta di gruppo come nelle Impostazioni di Android (piccola, colore
        // primario). Glass: titolo di sezione in grassetto, come iOS.
        Text(
            text = text,
            fontSize = if (AppTheme.isGlass) 17.sp else 14.sp,
            fontWeight = if (AppTheme.isGlass) FontWeight.Bold else FontWeight.SemiBold,
            color = if (AppTheme.isGlass) AppTheme.TextDark else AppTheme.PrimaryBlue,
            modifier = if (AppTheme.isGlass) Modifier else Modifier.padding(start = 4.dp)
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.PrimaryBlue,
                modifier = Modifier.ailaPressable(pressedScale = 0.95f) { onActionClick() }
            )
        }
    }
}

/** Cerchio sfumato con l'icona dentro, usato dagli stati vuoti e d'errore. */
@Composable
private fun StateBadge(brush: Brush, icon: @Composable () -> Unit) {
    Box(
        // Material: forma "sunny" a 12 lobi piena; Glass: cerchio sfumato.
        modifier = Modifier
            .size(if (AppTheme.isGlass) 84.dp else 96.dp)
            .clip(if (AppTheme.isGlass) CircleShape else ailaCookieShape(12, 0.1f))
            .background(if (AppTheme.isGlass) brush else SolidColor(AppTheme.TintBlue)),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Stato vuoto curato (mockup "Vuoto"): cerchio sfumato con l'icona, titolo, spiegazione e —
 * quando ha senso — il pulsante che porta all'azione risolutiva. Sostituisce le righe di testo
 * grigio centrate usate finora, che sembravano un errore di caricamento.
 */
@Composable
fun AilaEmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: @Composable () -> Unit = {
        AppIcons.Document(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue)
    }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space32, vertical = AppTheme.Space48),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateBadge(
            brush = Brush.linearGradient(listOf(AppTheme.TintBlue, AppTheme.TintViolet)),
            icon = icon
        )
        Spacer(modifier = Modifier.height(AppTheme.Space16))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = AppTheme.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space20))
            AilaPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Stato di errore (mockup "Errore di rete"): stessa struttura dello stato vuoto ma in tinta calda
 * e con "Riprova". Prima l'errore era una riga di testo rosso al centro dello schermo, senza
 * alcun modo di ritentare se non uscire e rientrare dalla scheda.
 */
@Composable
fun AilaErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Qualcosa non ha funzionato",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space32, vertical = AppTheme.Space48),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateBadge(
            brush = Brush.linearGradient(listOf(AppTheme.TintRed, AppTheme.TintAmber)),
            icon = { Text(text = "!", fontSize = 34.sp, fontWeight = FontWeight.Black, color = AppTheme.TintRedInk) }
        )
        Spacer(modifier = Modifier.height(AppTheme.Space16))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = AppTheme.TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space20))
            AilaPrimaryButton(
                text = "Riprova",
                onClick = onRetry,
                icon = { tint -> AppIcons.Refresh(modifier = Modifier.size(15.dp), color = tint) }
            )
        }
    }
}

/**
 * Pulsante quadrato con sola icona, della stessa misura del pulsante "indietro" di [AilaBackBar]:
 * per le azioni in una barra (aggiungi, apri fuori, esporta) dove una parola ruba spazio al
 * titolo o alle schede accanto. [contentDescription] non si vede ma lo leggono TalkBack e
 * VoiceOver, che altrimenti annuncerebbero solo "pulsante".
 *
 * [primary] usa il gradiente dei pulsanti principali: per l'azione che la schermata offre per
 * prima (aggiungere un evento, proporre un'idea); le altre restano sul grigio.
 */
@Composable
fun AilaIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    // 44dp come i pulsanti della Home e il minimo di tocco consigliato: a 38dp, accanto al logo
    // da 48 delle intestazioni, sembravano piu' piccoli e fuori asse rispetto al titolo.
    size: Dp = 44.dp,
    /**
     * true se il pulsante apre una schermata intera: in Material la pagina nasce dal pulsante e ci
     * rientra (stesso container transform di ricerca, notifiche e profilo sulla Home).
     */
    opensPage: Boolean = false,
    icon: @Composable (Color) -> Unit
) {
    // Glass: cerchio (i pulsanti tondi di iOS). Expressive: cerchio che alla pressione diventa un
    // quadrato arrotondato, come gli icon button di M3 Expressive.
    val interactionSource = remember { MutableInteractionSource() }
    val shape = CircleShape
    val background = when {
        !enabled -> Modifier.background(AppTheme.TintSlate)
        primary -> Modifier.background(AppTheme.PrimaryGradient)
        else -> Modifier.background(AppTheme.TintSlate)
    }
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (opensPage && enabled && !AppTheme.isGlass)
                    Modifier.ailaTransformOrigin(
                        size / 2,
                        buttonColor = if (primary) AppTheme.PrimaryBlue else AppTheme.TintSlate
                    )
                else Modifier
            )
            .then(if (AppTheme.isGlass) Modifier.ailaPressScale(interactionSource, 0.9f) else Modifier)
            .then(
                if (AppTheme.isGlass) Modifier.clip(shape)
                else Modifier.ailaMorphClip(interactionSource, pressedPercent = 28)
            )
            .then(background)
            .then(
                if (AppTheme.isGlass) Modifier
                    // Primario: tinta piatta, senza riflesso ne' bordo (vedi PrimaryGradient).
                    .then(if (primary) Modifier else Modifier.background(AppTheme.GlassFill).border(1.dp, AppTheme.GlassEdge, shape))
                else Modifier
            )
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        icon(
            when {
                !enabled -> AppTheme.TextFaint
                primary -> Color.White
                else -> AppTheme.TextDark
            }
        )
    }
}

/**
 * Azione con icona e contatore (voti, commenti). Sostituisce le coppie "emoji + numero" della
 * bacheca: ha un'area di tocco vera, il colore del design system e due animazioni — rimpicciolisce
 * mentre la si preme e "rimbalza" quando il valore cambia, così si vede che il tocco è arrivato
 * anche prima che la risposta del server aggiorni il numero.
 */
@Composable
fun AilaIconAction(
    label: String,
    tint: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    // Spenta: resta visibile con il suo numero ma non risponde al tocco (es. voti su una proposta chiusa).
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Rimbalzo alla variazione del contatore — solo quando il numero cambia davvero. Prima
    // l'effetto partiva anche alla prima composizione: ogni card della bacheca che entrava nello
    // schermo faceva rimbalzare le sue tre pillole, e durante lo scorrimento erano decine di
    // animazioni insieme (una delle cause del lag della Bacheca).
    val bounce = remember { Animatable(1f) }
    val lastLabel = remember { arrayOf(label) }
    LaunchedEffect(label) {
        if (label != lastLabel[0]) {
            lastLabel[0] = label
            bounce.animateTo(1.10f, spring(stiffness = Spring.StiffnessHigh))
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    val pressScale = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconActionScale"
    )
    val background by animateColorAsState(
        targetValue = if (selected) ink.copy(alpha = 0.16f) else tint,
        animationSpec = tween(200),
        label = "iconActionBg"
    )

    // Almeno 44dp di altezza e 48 di larghezza: con il padding di prima (7dp) la pillola era alta
    // poco più di 30dp e sulle proposte si sbagliava spesso pulsante.
    Row(
        modifier = modifier
            // Scala letta nel graphicsLayer: niente ricomposizione a ogni fotogramma.
            .graphicsLayer {
                val k = pressScale.value * bounce.value
                scaleX = k
                scaleY = k
                alpha = if (enabled) 1f else 0.55f
            }
            .heightIn(min = 44.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(background)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon(ink)
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
        }
    }
}

/**
 * Marchio "fatto da AILA Assistant": l'onda di [AilaAssistantMark] in monocromatico, che pulsa
 * piano, più l'etichetta. Sostituisce l'emoji 🤖/✨ e dice da sola che quel contenuto è lavoro
 * dell'assistente e non di una persona.
 *
 * Monocromatico e non a colori: a 13dp le quattro barre sfumate si impastano contro il tinto del
 * badge, mentre in tinta unita il segno resta leggibile.
 */
@Composable
fun AilaAssistantBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.TintViolet,
    ink: Color = AppTheme.TintVioletInk
) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            delay(1400)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (pulse) 1f else 0.55f,
        animationSpec = tween(1400),
        label = "aiBadgePulse"
    )

    Row(
        modifier = modifier
            // Capsula: con gli angoli a 6dp era un rettangolo dentro il riquadro molto
            // arrotondato del riassunto, e stonava.
            .clip(RoundedCornerShape(percent = 50))
            .background(tint)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaAssistantMark(
            size = 13.dp,
            modifier = Modifier.alpha(alpha),
            brush = SolidColor(ink)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1
        )
    }
}

/** Pallino colorato: stato di pertinenza, giorni con eventi, indicatori vari. */
@Composable
fun AilaDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

private val SwitchWidth = 46.dp
private val SwitchHeight = 26.dp
private val SwitchThumbSize = 22.dp
private val SwitchPadding = 2.dp

/**
 * Interruttore in stile iOS, in tinta col brand invece del verde di sistema: pillola blu quando è
 * "on", grigia quando è "off", e la goccia bianca che scorre da un lato all'altro.
 *
 * Il bordo della goccia che guida il movimento arriva a destinazione più svelto di quello che
 * segue (due molle con rigidità diverse sui due bordi, non un'unica animazione di posizione):
 * per questo, mentre scorre, si allunga come un elastico invece di restare un cerchio rigido, e
 * torna rotonda solo quando si ferma — è la stessa illusione dei toggle di iOS.
 */
/**
 * Posizione e larghezza animate lette in fase di layout, non di composizione: mentre la pillola
 * scorre (selettore a chip, interruttore Glass) si rifa' solo il layout di questo elemento, invece
 * di ricomporre a ogni fotogramma tutto il componente che la contiene.
 */
internal fun Modifier.ailaAnimatedBar(left: () -> Dp, width: () -> Dp, top: Dp = 0.dp): Modifier =
    this.layout { measurable, constraints ->
        val w = width().roundToPx().coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(placeable.width, placeable.height) {
            placeable.place(left().roundToPx(), top.roundToPx())
        }
    }

@Composable
fun AilaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Material Expressive: l'interruttore di Material 3, con il pallino che cresce da acceso e la
    // spunta dentro. La "goccia" elastica qui sotto resta per Liquid Glass.
    if (!AppTheme.isGlass) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            thumbContent = if (checked) {
                { AppIcons.Check(modifier = Modifier.size(12.dp), color = AppTheme.PrimaryBlue) }
            } else null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppTheme.PrimaryBlue,
                uncheckedThumbColor = AppTheme.TextMuted,
                uncheckedTrackColor = AppTheme.TrackFill,
                uncheckedBorderColor = AppTheme.TextMuted
            )
        )
        return
    }
    val leftBound = SwitchPadding
    val rightBound = SwitchWidth - SwitchPadding - SwitchThumbSize
    val targetLeft = if (checked) rightBound else leftBound
    val targetRight = targetLeft + SwitchThumbSize

    // Bordo guida: molla rigida e senza rimbalzo, arriva a destinazione svelto. Bordo che segue:
    // molla molto più morbida e con un rimbalzo pronunciato, quindi resta indietro mentre l'altro
    // bordo è già arrivato — la goccia si allunga parecchio — e poi supera un poco il traguardo
    // prima di "ricomprimersi" sulla misura normale. A riposo i due bordi combaciano sempre a
    // esattamente SwitchThumbSize di distanza.
    val leadingSpec = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)
    val trailingSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 35f)

    val leftEdge by animateDpAsState(
        targetValue = targetLeft,
        animationSpec = if (checked) trailingSpec else leadingSpec,
        label = "ailaSwitchLeftEdge"
    )
    val rightEdge by animateDpAsState(
        targetValue = targetRight,
        animationSpec = if (checked) leadingSpec else trailingSpec,
        label = "ailaSwitchRightEdge"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> AppTheme.TintSlate
            checked -> AppTheme.PrimaryBlue
            else -> AppTheme.FieldOutline
        },
        animationSpec = tween(180),
        label = "ailaSwitchTrack"
    )

    Box(
        modifier = modifier
            .size(width = SwitchWidth, height = SwitchHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .ailaAnimatedBar(left = { leftEdge }, width = { (rightEdge - leftEdge).coerceAtLeast(4.dp) }, top = SwitchPadding)
                .height(SwitchThumbSize)
                .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}


/**
 * Pulsante d'azione flottante (es. "Nuova proposta" in Bacheca), in basso a destra sopra la
 * barra delle tab. Prima il "+" stava nell'intestazione della tab Classe e, comparendo solo su
 * Bacheca, faceva cambiare larghezza al selettore Circolari/Bacheca.
 *
 * Glass: cerchio col gradiente del brand e un'ombra morbida. Expressive: il FAB di Material,
 * quadrato arrotondato "primary container" che si schiaccia alla pressione.
 */
@Composable
fun AilaFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glass = AppTheme.isGlass
    val corner by animateFloatAsState(
        targetValue = when {
            glass -> 50f
            pressed -> 20f
            else -> 30f
        },
        animationSpec = if (pressed) spring(stiffness = 1400f) else spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "fabCorner"
    )
    val shape = RoundedCornerShape(percent = corner.toInt())
    val ink = if (glass) Color.White else AppTheme.TintBlueInk
    Box(
        modifier = modifier
            .size(56.dp)
            .then(if (glass) Modifier.ailaPressScale(interactionSource, 0.9f) else Modifier)
            .shadow(if (glass) 8.dp else 6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(if (glass) AppTheme.PrimaryGradient else SolidColor(AppTheme.TintBlue))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        icon(ink)
    }
}

/** Accesso a profilo e impostazioni dall'avatar: iniziali dell'utente e cosa fare al tocco. */
class AilaProfileEntry(val initials: String, val onClick: () -> Unit)

/**
 * Fornito da MainAppShell alle schermate delle tab: se c'e', le intestazioni mostrano l'avatar a
 * destra. Da quando "Altro" non e' piu' una tab, profilo e impostazioni si aprono da qui.
 */
val LocalProfileEntry = androidx.compose.runtime.compositionLocalOf<AilaProfileEntry?> { null }

/**
 * Avatar tondo con le iniziali: apre profilo e impostazioni. [onHero] per la Home, dove sta sul
 * pannello colorato e usa il vetro chiaro invece del blu tenue.
 */
@Composable
fun AilaProfileButton(entry: AilaProfileEntry, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val avatarFill = when {
        onHero -> AppTheme.AccentDeep
        AppTheme.accent != AilaAccent.BLUE -> AppTheme.AccentContainer
        AppTheme.isDarkMode -> Color(0xFF34457A)
        else -> Color(0xFFBFD0FF)
    }
    Box(
        modifier = modifier
            .size(44.dp)
            // Material: il profilo si apre dall'avatar alla Pixel e ci rientra, dalla Home come
            // dalle altre schermate (prima solo dalla Home).
            .ailaTransformOrigin(22.dp, buttonColor = if (AppTheme.isGlass) null else avatarFill)
            .ailaPressScale(interactionSource, 0.9f)
            .clip(CircleShape)
            // Un tono piu' scuro di prima: sul pannello era un velo bianco che quasi spariva,
            // fuori un azzurro chiarissimo. Ora si stacca come un avatar vero.
            .then(
                // Liquid Glass: avatar di vetro neutro, identico ai pulsanti tondi accanto (niente
                // tinta colorata, che lo staccava dal resto della barra), iniziali in inchiostro.
                if (AppTheme.isGlass) Modifier.ailaGlassSurface(CircleShape)
                else Modifier.background(avatarFill)
            )
            .clickable(interactionSource = interactionSource, indication = null) { entry.onClick() }
            .semantics {
                contentDescription = "Profilo e impostazioni"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = entry.initials,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                AppTheme.isGlass -> AppTheme.TextDark
                onHero || AppTheme.isDarkMode -> Color.White
                else -> AppTheme.OnAccentContainer
            }
        )
    }
}
