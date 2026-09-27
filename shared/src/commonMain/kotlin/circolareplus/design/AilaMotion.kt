package circolareplus.design

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Animazioni condivise.
 *
 * Nel mockup non si vedono (è statico), ma la differenza tra un'app che "compare" e una che
 * "sbatte in faccia i contenuti" è quasi tutta qui. Finora l'unica animazione dell'app era quella
 * delle chip filtro: liste, card e schermate apparivano di colpo.
 */

/**
 * Entrata di un elemento: **ora non fa nulla**, resta per non toccare le decine di chiamate.
 *
 * Prima gli elementi arrivavano a cascata, poi solo alla prima apertura; ma sommata allo
 * "sblocco" (tutto che si posa con un rimbalzo) la cascata faceva due animazioni diverse una
 * dopo l'altra. Scelta di Simone: all'apertura rimbalza tutto insieme ([ailaUnlock]), e il
 * movimento fra le schermate lo danno le transizioni, non i singoli elementi.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.ailaAppear(index: Int = 0, enabled: Boolean = true): Modifier = this

/**
 * Respiro lento: usata dal logo nella schermata di caricamento, per far capire che l'app sta
 * lavorando e non è bloccata. Non usa `rememberInfiniteTransition` di proposito — un'animazione
 * infinita continuerebbe a girare anche a caricamento finito se la schermata restasse composta;
 * qui il ciclo è esplicito e si ferma con la schermata.
 */
@Composable
fun Modifier.ailaBreathe(): Modifier {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            expanded = !expanded
            delay(1100)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1.06f else 0.97f,
        animationSpec = tween(durationMillis = 1100),
        label = "ailaBreathe"
    )
    return this.scale(scale)
}

/**
 * Feedback al tocco: effetto multi-layer per un feedback tattile evidente.
 * - Scala: l'elemento si rimpicciolisce durante il press
 * - Ombra: si riduce durante il press (effetto "affonda"), rimbalza al rilascio
 * - Alpha: leggermente più scuro durante il press
 *
 * Nasce dal punto 6 di Simone ("le animazioni al tocco mancano"). Prima card, righe di lista e
 * pulsanti reagivano solo con l'increspatura di Material — che su iOS non c'è, e che comunque
 * arriva dopo il tocco invece che durante. La scala parte all'istante, quindi l'app sembra
 * rispondere prima ancora di aver fatto qualcosa.
 *
 * Sostituisce `Modifier.clickable`, non si aggiunge: gestisce il clic e l'increspatura viene tolta
 * di proposito (`indication = null`), perché sommata alla scala il risultato è confuso.
 *
 * `pressedScale` va tarato sulla dimensione: un elemento grande che rimpicciolisce del 3% si nota
 * quanto uno piccolo che rimpicciolisce del 6%, quindi le card usano un valore più vicino a 1.
 */
@Composable
fun Modifier.ailaPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "ailaPressScale"
    )

    val pressAlpha = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ailaPressAlpha"
    )

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = pressAlpha.value
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) { onClick() }
}

/**
 * Feedback al tocco per i pulsanti: niente scala né traslazione (Simone li vuole fermi), solo una
 * velatura "di vetro" che si accende dentro il pulsante mentre lo si preme — un velo colorato
 * diffuso più un riflesso più chiaro in alto a sinistra, come un pannello smerigliato illuminato
 * da un lato. Sparisce un po' più lentamente di quanto non compaia, altrimenti il rilascio sembra
 * uno scatto.
 *
 * `tint` è il colore del vetro: bianco sui pulsanti pieni (gradiente, rosso), un colore del brand
 * sui pulsanti chiari a contorno — su sfondo bianco un velo bianco non si vedrebbe.
 */
@Composable
fun Modifier.ailaGlassPressable(
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) { onClick() }
        .ailaGlassOverlay(interactionSource, enabled = enabled, tint = tint)
}

/**
 * Solo la velatura di vetro di [ailaGlassPressable], senza gestire il tocco: serve per i casi in
 * cui l'area toccabile (es. tutta la colonna icona+etichetta di una quick action) è più grande
 * del riquadro che deve effettivamente illuminarsi — si passa lo stesso `interactionSource` usato
 * dal `clickable`/`ailaGlassPressable` esterno, così il vetro reagisce alla stessa pressione ma
 * resta ritagliato sul riquadro giusto.
 */
@Composable
fun Modifier.ailaGlassOverlay(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    tint: Color = Color.White
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()

    // Punto del tocco: il colore parte da lì, non dal centro — così sembra davvero "arrivare"
    // da dove hai messo il dito invece di comparire uniforme su tutto il pulsante.
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                pressPosition = interaction.pressPosition
            }
        }
    }

    // Il raggio cresce finché resta premuto (il colore "riempie" il pulsante) e si ritira un po'
    // più lentamente al rilascio, invece di un velo fisso che compare e basta.
    val fillProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 260 else 320),
        label = "ailaGlassFill"
    )

    return this.drawWithContent {
        drawContent()
        if (fillProgress > 0f) {
            // Raggio che a progress=1 copre l'angolo più lontano dal punto di tocco, quindi il
            // pulsante risulta interamente colorato quando il riempimento è completo.
            val dx = maxOf(pressPosition.x, size.width - pressPosition.x)
            val dy = maxOf(pressPosition.y, size.height - pressPosition.y)
            val maxRadius = kotlin.math.sqrt(dx * dx + dy * dy)
            drawCircle(
                color = tint.copy(alpha = 0.4f * fillProgress),
                radius = (maxRadius * fillProgress).coerceAtLeast(1f),
                center = pressPosition
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.55f * fillProgress), Color.Transparent),
                    center = pressPosition,
                    radius = (maxRadius * fillProgress).coerceAtLeast(1f)
                ),
                radius = (maxRadius * fillProgress).coerceAtLeast(1f),
                center = pressPosition
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Movimento dei due stili (vedi UiStyle). Quasi tutto e' una molla, che parte veloce e rallenta da
// sola; cambia il carattere:
// - Liquid Glass: molle morbide, rimbalzo appena accennato; push da destra come iOS.
// - Material Expressive: le "spatial spring" di M3 Expressive, piu' rigide e piu' rimbalzanti;
//   navigazione "shared axis" (scorrimento breve + dissolvenza) e cambio tab "fade through".
// ---------------------------------------------------------------------------------------------

/** Molla del push/pop fra schermate. */
fun <T> ailaNavigationSpring(): SpringSpec<T> =
    if (AppTheme.isGlass) spring(dampingRatio = 1f, stiffness = 420f)
    else spring(dampingRatio = 0.9f, stiffness = 700f)

/** Molla "viva" per indicatori, selezioni e comparse. */
fun <T> ailaSpatialSpring(): SpringSpec<T> =
    if (AppTheme.isGlass) spring(dampingRatio = 0.78f, stiffness = 380f)
    else spring(dampingRatio = 0.6f, stiffness = 800f)

/**
 * Push/pop fra schermate.
 * Glass: come UINavigationController, la nuova entra da destra a tutta larghezza, quella sotto
 * scivola di un terzo e si scurisce. Expressive: "shared axis X" di Material, scorrimento breve
 * di pochi dp piu' dissolvenza incrociata.
 */
fun AnimatedContentTransitionScope<*>.ailaPushTransition(forward: Boolean): ContentTransform {
    val transform = if (AppTheme.isGlass) {
        if (forward) {
            slideInHorizontally(ailaNavigationSpring()) { it } togetherWith
                (slideOutHorizontally(ailaNavigationSpring()) { -it / 3 } +
                    fadeOut(tween(durationMillis = 320), targetAlpha = 0.55f))
        } else {
            (slideInHorizontally(ailaNavigationSpring()) { -it / 3 } +
                fadeIn(tween(durationMillis = 320), initialAlpha = 0.55f)) togetherWith
                slideOutHorizontally(ailaNavigationSpring()) { it }
        }
    } else {
        val dir = if (forward) 1 else -1
        (slideInHorizontally(ailaNavigationSpring()) { dir * it / 10 } +
            fadeIn(tween(durationMillis = 210, delayMillis = 60))) togetherWith
            (slideOutHorizontally(ailaNavigationSpring()) { -dir * it / 10 } +
                fadeOut(tween(durationMillis = 90)))
    }
    return transform.apply {
        targetContentZIndex = if (forward) 1f else -1f
    } using SizeTransform(clip = false)
}

/**
 * Cambio di tab dalla barra in basso. Glass: dissolvenza rapida, quasi un taglio, come le tab
 * bar di iOS. Expressive: "fade through" di Material, la vecchia svanisce e la nuova emerge
 * crescendo appena.
 */
fun AnimatedContentTransitionScope<*>.ailaTabTransition(): ContentTransform =
    if (AppTheme.isGlass) {
        fadeIn(tween(durationMillis = 140)) togetherWith fadeOut(tween(durationMillis = 90))
    } else {
        (fadeIn(tween(durationMillis = 210, delayMillis = 70)) +
            scaleIn(tween(durationMillis = 210, delayMillis = 70), initialScale = 0.94f)) togetherWith
            fadeOut(tween(durationMillis = 70))
    }

/** Ingresso/uscita di un livello "push" mostrato con AnimatedVisibility (dettaglio circolare). */
fun ailaPushEnter(): EnterTransition =
    if (AppTheme.isGlass) slideInHorizontally(ailaNavigationSpring()) { it }
    else slideInHorizontally(ailaNavigationSpring()) { it / 10 } + fadeIn(tween(210, delayMillis = 60))

fun ailaPushExit(): ExitTransition =
    if (AppTheme.isGlass) slideOutHorizontally(ailaNavigationSpring()) { it }
    else slideOutHorizontally(ailaNavigationSpring()) { it / 10 } + fadeOut(tween(90))

/**
 * Quanto si sposta e si scurisce la schermata che resta sotto un push (0..1 = coperta del tutto).
 * Glass: un terzo di larghezza e velo scuro, come iOS. Expressive: quasi ferma, solo un velo
 * leggero (in "shared axis" la schermata sotto svanisce, non scivola).
 */
val ailaUnderlayShift: Float get() = if (AppTheme.isGlass) 1f / 3f else 0.06f
val ailaUnderlayDim: Float get() = if (AppTheme.isGlass) 0.18f else 0.08f

/** Si ricorda se lo "sblocco" e' gia' stato fatto in questo avvio dell'app. */
private object AilaUnlockMemory {
    var played = false
}

/**
 * L'animazione d'ingresso nell'app (dopo caricamento o login): tutto insieme, senza cascate.
 * Glass: come lo sblocco di iOS, arriva leggermente ingrandito e si posa con un piccolo rimbalzo.
 * Expressive: emerge da piu' piccolo con la molla vivace di Material. Una volta sola per avvio.
 */
@Composable
fun Modifier.ailaUnlock(): Modifier {
    val play = remember { !AilaUnlockMemory.played }
    if (!play) return this
    val glass = AppTheme.isGlass
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        AilaUnlockMemory.played = true
        progress.animateTo(
            1f,
            // Glass: si posa dall'alto verso il basso (1.14 -> 1) come lo sblocco di iOS.
            // Expressive: emerge dal basso verso l'alto (0.9 -> 1) con la molla piu' vivace.
            if (glass) spring(dampingRatio = 0.62f, stiffness = 170f)
            else spring(dampingRatio = 0.55f, stiffness = 260f)
        )
    }
    val from = if (glass) 1.14f else 0.9f
    return this.graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        val scale = from + (1f - from) * p
        scaleX = scale
        scaleY = scale
    }
}

/**
 * "Pop" di un'icona quando diventa selezionata (barra in basso): si comprime e torna con una
 * molla elastica.
 */
@Composable
fun Modifier.ailaSelectionPop(selected: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val wasSelected = remember { arrayOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected[0]) {
            scale.animateTo(0.82f, spring(stiffness = 1400f))
            scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        }
        wasSelected[0] = selected
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
