package circolareplus.design

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
 * Una schermata che si sta chiudendo smette di prendere i tocchi, cosi' i pulsanti sotto
 * rispondono subito. Senza, fino a fine animazione di chiusura la pagina in uscita li copriva e
 * "apri-chiudi, apri-chiudi" perdeva i tocchi.
 *
 * Il contenuto viene spostato fuori schermo di [AILA_NO_TOUCH_SHIFT_PX] solo per il layout (e
 * quindi per i tocchi, che seguono le posizioni del layout) e ridisegnato al suo posto con una
 * traslazione del disegno. Prima la misura si dichiarava 0x0: dentro un contenitore che impone
 * tutto lo schermo come minimo, Compose porta la misura al minimo e *centra* il contenuto, e la
 * pagina in uscita si disegnava da meta' schermo (segnalato: la card del profilo "in basso a
 * destra" per un attimo aprendo le Impostazioni). In piu' i figli di un nodo 0x0 restano
 * toccabili alla loro posizione vera: il blocco funzionava solo perche' erano stati spostati.
 */
fun Modifier.ailaNoTouchWhile(active: Boolean): Modifier =
    if (!active) this else this
        .drawWithContent {
            translate(left = AILA_NO_TOUCH_SHIFT_PX.toFloat()) { this@drawWithContent.drawContent() }
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(-AILA_NO_TOUCH_SHIFT_PX, 0) }
        }

/**
 * Di quanto [ailaNoTouchWhile] sposta il layout. Molto piu' di qualunque schermo, ma abbastanza
 * piccolo da restare preciso al sottopixel in virgola mobile.
 */
const val AILA_NO_TOUCH_SHIFT_PX = 100_000

/**
 * La posizione nello schermo dove l'elemento si vede davvero: dentro una pagina in chiusura
 * (vedi [ailaNoTouchWhile]) il layout e' spostato, il disegno no.
 */
internal fun visibleXInRoot(x: Float): Float =
    if (x < -AILA_NO_TOUCH_SHIFT_PX / 2f) x + AILA_NO_TOUCH_SHIFT_PX else x

/**
 * Respiro lento: usata dal logo nella schermata di caricamento, per far capire che l'app sta
 * lavorando e non è bloccata. Non usa `rememberInfiniteTransition` di proposito — un'animazione
 * infinita continuerebbe a girare anche a caricamento finito se la schermata restasse composta;
 * qui il ciclo è esplicito e si ferma con la schermata.
 */
@Composable
fun Modifier.ailaBreathe(): Modifier {
    // Riduci movimento: logo fermo. Il ritorno anticipato toglie anche il ciclo, non solo la scala.
    if (AppTheme.reduceMotion) return this
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
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
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
    // Riduci movimento: niente rimpicciolimento elastico, resta solo la velatura (alpha) qui sotto,
    // che basta a dire "toccato" senza far muovere niente.
    val scale = animateFloatAsState(
        targetValue = if (isPressed && enabled && !AppTheme.reduceMotion) pressedScale else 1f,
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
        // Rapido: il vetro deve rispondere subito al dito (prima 260 ms, sembrava in ritardo).
        animationSpec = tween(durationMillis = if (isPressed) 120 else 240),
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
//
// Con "Riduci movimento" del sistema (AppTheme.reduceMotion) i due stili si comportano allo stesso
// modo: molle rapide e senza rimbalzo, schermate e tab che si cambiano con una dissolvenza breve
// invece di scorrere o crescere, effetti decorativi spenti. Le firme restano le stesse, cosi' chi
// le chiama non deve sapere niente.
// ---------------------------------------------------------------------------------------------

/** Durata delle dissolvenze che, con "Riduci movimento", prendono il posto di scorrimenti e scale. */
private const val REDUCED_FADE_MS = 150

/** Molla rapida e senza rimbalzo: con "Riduci movimento" sostituisce tutte le altre. */
private fun <T> reducedMotionSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

/** Molla del push/pop fra schermate. */
fun <T> ailaNavigationSpring(): SpringSpec<T> =
    if (AppTheme.reduceMotion) reducedMotionSpring()
    else if (AppTheme.isGlass) spring(dampingRatio = 1f, stiffness = 520f)
    else spring(dampingRatio = 0.9f, stiffness = 850f)

/** Molla "viva" per indicatori, selezioni e comparse. */
fun <T> ailaSpatialSpring(): SpringSpec<T> =
    if (AppTheme.reduceMotion) reducedMotionSpring()
    else if (AppTheme.isGlass) spring(dampingRatio = 0.78f, stiffness = 380f)
    else spring(dampingRatio = 0.6f, stiffness = 800f)

/**
 * Push/pop fra schermate.
 * Glass: come UINavigationController, la nuova entra da destra a tutta larghezza, quella sotto
 * scivola di un terzo e si scurisce. Expressive: "shared axis X" di Material, scorrimento breve
 * di pochi dp piu' dissolvenza incrociata.
 */
fun AnimatedContentTransitionScope<*>.ailaPushTransition(forward: Boolean): ContentTransform {
    val transform = if (AppTheme.reduceMotion) {
        // Riduci movimento: solo dissolvenza, niente scorrimento ne' crescita. Come nella "fade
        // through" qui sotto, la pagina che resta sotto non deve svanire mentre l'altra compare
        // (si vedrebbe la Home in mezzo): avanti la nuova si accende sopra e la vecchia sparisce a
        // cose fatte; indietro quella che torna e' gia' piena e svanisce solo quella che si chiude.
        if (forward) {
            fadeIn(tween(durationMillis = REDUCED_FADE_MS)) togetherWith
                fadeOut(tween(durationMillis = 40, delayMillis = REDUCED_FADE_MS))
        } else {
            EnterTransition.None togetherWith fadeOut(tween(durationMillis = REDUCED_FADE_MS))
        }
    } else if (AppTheme.isGlass) {
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
        // Material: "fade through", la stessa del cambio di tab (quella della Mappa posti, che
        // Simone trovava la migliore): la vecchia svanisce in fretta, la nuova emerge crescendo
        // appena. Niente scorrimento ne' pagina che si allarga da un elemento.
        // Fra due schermate sovrapposte (es. Profilo -> Impostazioni) quella che resta sotto non
        // deve sparire prima che l'altra sia comparsa: per un attimo si vedeva la Home in mezzo
        // (il "glitch"). Avanti: la vecchia resta piena finche' la nuova e' arrivata. Indietro:
        // quella sotto c'e' gia', e' la pagina che si chiude a svanire sopra.
        if (forward) {
            materialFadeThroughEnter() togetherWith fadeOut(tween(durationMillis = 60, delayMillis = 280))
        } else {
            // Indietro: fade through anche qui, la pagina che torna riemerge crescendo appena.
            materialFadeThroughEnter() togetherWith
                (fadeOut(tween(durationMillis = 90)) + scaleOut(tween(durationMillis = 90), targetScale = 0.96f))
        }
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
    // Riduci movimento: la dissolvenza rapida di Glass vale per entrambi gli stili (niente crescita).
    if (AppTheme.isGlass || AppTheme.reduceMotion) {
        fadeIn(tween(durationMillis = 140)) togetherWith fadeOut(tween(durationMillis = 90))
    } else {
        materialFadeThroughEnter() togetherWith materialFadeThroughExit()
    }

/** Material "fade through": ingresso (dissolvenza + leggera crescita, dopo l'uscita). */
fun materialFadeThroughEnter(): EnterTransition =
    if (AppTheme.reduceMotion) fadeIn(tween(durationMillis = REDUCED_FADE_MS))
    else fadeIn(tween(durationMillis = 210, delayMillis = 70)) +
        scaleIn(tween(durationMillis = 210, delayMillis = 70), initialScale = 0.94f)

/** Material "fade through": uscita rapida. */
fun materialFadeThroughExit(): ExitTransition = fadeOut(tween(durationMillis = 70))

/**
 * Container transform (la pagina che si allarga dalla card toccata, stile Pixel). Spento: la
 * "fade through" risultava piu' pulita su Android (niente sagome che si allargano, niente righe
 * a meta'). Il codice resta, basta rimettere true per riaverlo.
 */
const val AILA_CONTAINER_TRANSFORM_ENABLED = false

/** Ingresso/uscita di un livello "push" mostrato con AnimatedVisibility (dettaglio circolare). */
fun ailaPushEnter(): EnterTransition =
    if (AppTheme.reduceMotion) fadeIn(tween(durationMillis = REDUCED_FADE_MS))
    else if (AppTheme.isGlass) slideInHorizontally(ailaNavigationSpring()) { it }
    else materialFadeThroughEnter()

fun ailaPushExit(): ExitTransition =
    if (AppTheme.reduceMotion) fadeOut(tween(durationMillis = REDUCED_FADE_MS))
    else if (AppTheme.isGlass) slideOutHorizontally(ailaNavigationSpring()) { it }
    else materialFadeThroughExit()

/**
 * Quanto si sposta e si scurisce la schermata che resta sotto un push (0..1 = coperta del tutto).
 * Glass: un terzo di larghezza e velo scuro, come iOS. Expressive: quasi ferma, solo un velo
 * leggero (in "shared axis" la schermata sotto svanisce, non scivola).
 */
// Glass: le pagine sono trasparenti e scorrono sullo sfondo fermo, quindi quella vecchia esce
// del tutto (con un terzo si sarebbero viste sovrapposte).
val ailaUnderlayShift: Float get() = if (AppTheme.isGlass) 1f else 0f
// Material: velo leggero sotto al container transform (col 32% di prima l'animazione "lampeggiava"
// di scuro all'apertura e alla chiusura).
val ailaUnderlayDim: Float get() = 0f

/**
 * Se false gli elementi di un foglio ([ailaSheetReveal]) sono subito al loro posto. Il foglio
 * "Nuovo evento" lo tiene spento mentre scende: l'entrata a scaglioni di una decina di livelli
 * semitrasparenti insieme alla discesa di un pannello alto (il modulo manuale) faceva scattare
 * l'animazione. Acceso, per i passi che si aprono dopo, a foglio fermo.
 */
val LocalAilaSheetReveal = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Entrata a scaglioni degli elementi di un foglio (dettaglio evento, "Nuovo evento"): ognuno sale di
 * pochi punti e si dissolve, con un ritardo crescente per [index]. Legge l'avanzamento solo nel
 * livello grafico, quindi non sposta niente nel layout: l'altezza del foglio non cambia a ogni
 * elemento che arriva.
 */
@Composable
fun Modifier.ailaSheetReveal(index: Int): Modifier {
    // Riduci movimento: elementi subito al loro posto, come col foglio "Nuovo evento" che scende.
    val enabled = LocalAilaSheetReveal.current && !AppTheme.reduceMotion
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    if (enabled) {
        LaunchedEffect(Unit) {
            delay(15L + index * 30L)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 320f))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = (p * 1.4f).coerceIn(0f, 1f)
        translationY = (1f - p) * 18.dp.toPx()
    }
}

/**
 * Un fumetto di chat appena scritto (la domanda all'Assistant): sale di qualche punto dal campo
 * di testo e si allarga dall'angolo in basso dalla parte di chi parla ([fromEnd] = destra), come
 * in Messaggi. Solo nel livello grafico: la lista lo misura subito alla sua altezza vera, quindi lo
 * scorrimento in fondo non salta.
 *
 * [enabled] va deciso una volta sola, quando il messaggio nasce: un fumetto gia' visto non deve
 * rientrare quando torna a schermo scorrendo. Con "Riduci movimento" solo dissolvenza.
 */
@Composable
fun Modifier.ailaBubbleEnter(enabled: Boolean, fromEnd: Boolean): Modifier {
    val reduce = AppTheme.reduceMotion
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    if (enabled) LaunchedEffect(Unit) { progress.animateTo(1f, ailaSpatialSpring()) }
    if (!enabled) return this
    return graphicsLayer {
        val p = progress.value
        alpha = (p * 2f).coerceIn(0f, 1f)
        if (!reduce) {
            translationY = (1f - p) * 28.dp.toPx()
            val s = 0.88f + 0.12f * p
            scaleX = s
            scaleY = s
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (fromEnd) 1f else 0f, 1f)
        }
    }
}

/**
 * Apertura di un contenuto che arriva dopo un'attesa (la risposta dell'Assistant): si allarga
 * dall'alto con la molla "viva" mentre si dissolve. Con "Riduci movimento" solo dissolvenza breve.
 */
fun ailaRevealEnter(): EnterTransition =
    if (AppTheme.reduceMotion) fadeIn(tween(durationMillis = REDUCED_FADE_MS))
    else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
        androidx.compose.animation.expandVertically(ailaSpatialSpring(), expandFrom = androidx.compose.ui.Alignment.Top)

/** Si ricorda se il riscaldamento delle pagine e' gia' stato fatto in questo avvio dell'app. */
private object AilaWarmUpMemory {
    var done = false
}

/**
 * Riscaldamento delle pagine che si aprono dai pulsanti (ricerca, notifiche, profilo).
 *
 * La prima volta che una pagina si apre l'app deve caricare le classi di Compose e di Material
 * che non ha ancora usato (il campo di testo, le liste, i testi), e lo fa tutto nel fotogramma
 * del tocco: il "lag delle prime aperture". Qui, a app ferma da un paio di secondi, ogni pagina
 * viene composta, misurata e disegnata una volta, nascosta e senza alcun effetto (niente tocchi,
 * niente lettori di schermo, dimensione zero, ritagliata), poi tolta: il lavoro si fa quando nessuno
 * sta toccando, e all'apertura vera resta solo quello dei dati. Una pagina alla volta e una
 * volta sola per avvio.
 */
@Composable
fun AilaWarmUp(pages: List<@Composable () -> Unit>) {
    if (AilaWarmUpMemory.done) return
    var index by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        // Prima: 2,5 s di attesa e 1,2 s per pagina, quindi i primi tocchi (che arrivano subito)
        // trovavano ancora le pagine "fredde". Ora si parte quasi subito e ogni pagina resta il
        // tempo di comporsi e disegnarsi una volta.
        delay(700)
        for (i in pages.indices) {
            index = i
            delay(350)
            index = -1
            delay(120)
        }
        AilaWarmUpMemory.done = true
    }
    val page = pages.getOrNull(index) ?: return
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            // Quasi trasparente (e comunque ritagliato a 0x0 qui sotto): il contenuto si disegna
            // davvero, cosi' si scaldano anche i testi e le forme, non solo la composizione.
            .graphicsLayer { alpha = 0.01f }
            .clipToBounds()
            // Misura il contenuto con i vincoli del genitore ma occupa 0x0: fuori dai bordi
            // non riceve tocchi e non si vede.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) { placeable.place(0, 0) }
            }
            .clearAndSetSemantics { }
    ) {
        page()
    }
}

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
    // Riduci movimento: niente ingrandimento con rimbalzo, l'app e' subito ferma al suo posto. Lo
    // sblocco si segna comunque come fatto: se l'impostazione si spegnesse dopo, non deve partire a
    // meta' sessione (il contenuto sparirebbe e ricomparirebbe).
    val play = remember { !AilaUnlockMemory.played && !AppTheme.reduceMotion }
    if (!play) {
        AilaUnlockMemory.played = true
        return this
    }
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
        // Riduci movimento: l'icona cambia stato senza "pop".
        if (selected && !wasSelected[0] && !AppTheme.reduceMotion) {
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

// ---------------------------------------------------------------------------------------------
// Container transform (Material Expressive): l'elemento toccato — una card, una riga, l'avatar —
// si allarga fino a diventare la pagina intera, con gli angoli che passano da tondi a squadrati;
// tornando indietro la pagina si richiude dentro l'elemento da cui era partita. E' la transizione
// dei Pixel (Meteo, Impostazioni, Contatti). In Liquid Glass resta il push da destra di iOS.
// ---------------------------------------------------------------------------------------------

/**
 * Punto di partenza di un container transform: rettangolo nello schermo e raggio degli angoli.
 * [buttonColor] c'e' solo per i pulsanti (ricerca, notifiche, avatar della Home): la forma nasce
 * piena del colore del pulsante, esattamente sopra di lui, e ci rientra al ritorno. Per questi il
 * container transform e' sempre acceso in Material, anche se sono piccoli.
 */
class AilaTransformOrigin(
    val bounds: androidx.compose.ui.geometry.Rect,
    val cornerRadiusPx: Float,
    val buttonColor: Color? = null
)

/**
 * Memoria delle origini. Al tocco un elemento registra dove si trova ([recordTap]); quando la
 * navigazione cambia davvero schermata, l'origine appena toccata viene assegnata alla schermata
 * che si apre ([assignFreshTo]) e resta legata a lei, cosi' al ritorno si sa dove richiuderla.
 */
object AilaContainerTransform {
    private var fresh: AilaTransformOrigin? = null
    private var freshMark: kotlin.time.TimeMark? = null
    private val origins = mutableMapOf<Any, AilaTransformOrigin>()

    /** Chiave dell'ultima schermata aperta "in avanti": solo lei si espande entrando. */
    var lastForwardKey: Any? = null
        private set

    fun recordTap(origin: AilaTransformOrigin) {
        fresh = origin
        freshMark = kotlin.time.TimeSource.Monotonic.markNow()
    }

    /**
     * Prende (e consuma) l'origine toccata da poco, senza legarla a una schermata: serve a
     * "ailaExplodeOut", dove l'elemento toccato non apre una pagina ma "esplode" nella schermata
     * sotto. `null` se l'ultimo tocco registrato e' vecchio di piu' di un secondo.
     */
    fun takeFresh(): AilaTransformOrigin? {
        val mark = freshMark
        val origin = fresh
        fresh = null
        freshMark = null
        return if (origin != null && mark != null && mark.elapsedNow().inWholeMilliseconds < 1000) origin else null
    }

    /** Assegna l'origine toccata da poco (meno di un secondo fa) alla schermata [key]. */
    fun assignFreshTo(key: Any) {
        val mark = freshMark
        val origin = fresh
        fresh = null
        freshMark = null
        lastForwardKey = key
        if (origin != null && mark != null && mark.elapsedNow().inWholeMilliseconds < 1000) {
            origins[key] = origin
        } else {
            origins.remove(key)
        }
    }

    /**
     * Rinnova l'origine toccata: per le schermate che si aprono dopo un'attesa (es. il calcolo
     * delle proposte, qualche secondo) il tocco sarebbe "scaduto" prima della navigazione.
     */
    fun touchFresh() {
        if (fresh != null) freshMark = kotlin.time.TimeSource.Monotonic.markNow()
    }

    fun onBack() {
        lastForwardKey = null
    }

    fun originOf(key: Any): AilaTransformOrigin? = origins[key]

    /**
     * Posizione aggiornata degli elementi con una chiave "logica" (es. la circolare n. 12),
     * ovunque si trovino in questo momento. Serve al ritorno: se la circolare era stata aperta
     * dalla Home ma ora sotto c'e' la lista delle Circolari, la pagina si richiude sulla card
     * della lista, cioe' su quella che si vede, non sulla posizione della Home.
     */
    private val live = mutableMapOf<Any, AilaTransformOrigin>()

    fun updateLive(key: Any, origin: AilaTransformOrigin) { live[key] = origin }
    fun removeLive(key: Any, origin: AilaTransformOrigin?) { if (live[key] === origin) live.remove(key) }
    fun liveOf(key: Any): AilaTransformOrigin? = live[key]
}

/**
 * Registra questo elemento come possibile origine di un container transform: al primo contatto
 * del dito (senza consumare il tocco) ne salva posizione e angoli.
 */
@Composable
fun Modifier.ailaTransformOrigin(
    cornerRadius: androidx.compose.ui.unit.Dp,
    liveKey: Any? = null,
    /** Colore del pulsante: lo rende un'origine "da pulsante" (vedi [AilaTransformOrigin.buttonColor]). */
    buttonColor: Color? = null
): Modifier {
    val holder = remember { arrayOf<androidx.compose.ui.geometry.Rect?>(null) }
    val liveHolder = remember { arrayOf<AilaTransformOrigin?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    if (liveKey != null) {
        androidx.compose.runtime.DisposableEffect(liveKey) {
            onDispose { AilaContainerTransform.removeLive(liveKey, liveHolder[0]) }
        }
    }
    return this
        .onGloballyPositioned {
            // Dentro una pagina in chiusura il layout e' spostato fuori schermo (vedi
            // ailaNoTouchWhile) e boundsInRoot, ritagliato allo schermo, sarebbe vuoto: si tiene
            // l'ultima posizione vera, la pagina non si muove mentre si chiude.
            if (visibleXInRoot(it.positionInRoot().x) != it.positionInRoot().x) return@onGloballyPositioned
            val bounds = it.boundsInRoot()
            holder[0] = bounds
            if (liveKey != null) {
                val origin = AilaTransformOrigin(bounds, radiusPx, buttonColor)
                liveHolder[0] = origin
                AilaContainerTransform.updateLive(liveKey, origin)
            }
        }
        .pointerInput(radiusPx, buttonColor) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                holder[0]?.let { AilaContainerTransform.recordTap(AilaTransformOrigin(it, radiusPx, buttonColor)) }
            }
        }
}

/**
 * Molla del container transform: rapida e senza rimbalzo (smorzamento critico), con una soglia
 * di arrivo minuscola, cosi' in chiusura la pagina arriva davvero sui bordi dell'elemento invece
 * di sparire poco prima.
 */
fun <T> ailaContainerSpring(): SpringSpec<T> =
    if (AppTheme.reduceMotion) reducedMotionSpring() else spring(dampingRatio = 1f, stiffness = 520f)

/**
 * Come [ailaContainerSpring], per i Float, con la soglia di arrivo stretta. Un filo piu' lenta di
 * prima (620): a quella velocita' la forma che si allargava risultava troppo brusca.
 */
fun ailaContainerFloatSpring(): SpringSpec<Float> = spring(
    dampingRatio = 1f,
    // Riduci movimento: la forma che si allarga dura il meno possibile.
    stiffness = if (AppTheme.reduceMotion) Spring.StiffnessHigh else 450f,
    visibilityThreshold = 0.0005f
)

/**
 * Chiusura del container transform (la pagina che rientra nel pulsante). Non la molla
 * dell'apertura: la molla parte subito veloce, e al tocco "indietro" il primo fotogramma e' lungo
 * (si ricompone mezza app), quindi la forma saltava avanti ("troppo veloce, lagga"). Qui una curva
 * a durata fissa che aspetta un paio di fotogrammi prima di partire, si stringe senza strappi e
 * rallenta a lungo prima di posarsi sul pulsante (l'"emphasized" di Material).
 */
fun ailaContainerCloseSpec(): androidx.compose.animation.core.FiniteAnimationSpec<Float> = tween(
    // Riduci movimento: chiusura breve, la forma che si stringe si vede appena.
    durationMillis = if (AppTheme.reduceMotion) 200 else 520,
    delayMillis = 40,
    easing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0f, 1f)
)

// Durate e curve del container transform. Corte e decise: la forma deve arrivare, non trascinarsi.
// L'apertura parte subito e rallenta solo nell'ultimo tratto; la chiusura e' un po' piu' rapida
// (si torna indietro, non si scopre niente) e simmetrica, senza coda lunga.
private const val OPEN_MS = 260L
private const val CLOSE_MS = 220L
private val ContainerOpenEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.1f, 0.05f, 1f)
private val ContainerCloseEasing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)


/**
 * Aspetta che la pagina appena composta si "calmi" prima di far partire l'apertura: due
 * fotogrammi di fila entro [calmMs] (a 60 Hz ne bastano ~17), ma non oltre [maxWaitMs]. Prima si
 * aspettavano due fotogrammi alla cieca: se la pagina nuova aveva ancora lavoro da fare (liste,
 * campo di testo, immagini) l'animazione partiva proprio sul fotogramma lungo e la forma saltava.
 * Il lavoro lo si fa con la forma ancora ferma sul pulsante.
 */
suspend fun awaitCalmFrames(minCalmFrames: Int = 1, maxWaitMs: Long = 50, calmMs: Long = 24) {
    val start = androidx.compose.runtime.withFrameNanos { it }
    var last = start
    var calm = 0
    while (calm < minCalmFrames && last - start < maxWaitMs * 1_000_000L) {
        val now = androidx.compose.runtime.withFrameNanos { it }
        if (now - last <= calmMs * 1_000_000L) calm++ else calm = 0
        last = now
    }
}

/**
 * Apertura del container transform a durata fissa, con un orologio "a passo limitato": ogni
 * fotogramma fa avanzare il tempo di al massimo 32 ms, anche se in realta' e' durato di piu'.
 * La molla di prima calcolava la posizione dal tempo vero, quindi un fotogramma lungo a meta'
 * corsa (la pagina che si disegna, un dato che arriva) faceva saltare la forma in avanti: era lo
 * "scatto". Cosi' un fotogramma lungo si vede come una breve pausa, non come uno strappo, e
 * l'animazione dura al massimo un poco di piu'. La curva parte morbida (come la chiusura), cosi'
 * i primi fotogrammi, che sono i piu' fragili, si muovono di poco.
 */
suspend fun androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.animateContainerOpen(
    target: Float = 1f
) {
    animateSteppedTo(target, (if (AppTheme.reduceMotion) 150L else OPEN_MS) * 1_000_000L, ContainerOpenEasing)
}

/**
 * Chiusura del container transform, con lo stesso orologio a passo limitato dell'apertura (vedi
 * [animateContainerOpen]). Prima la chiusura era una `tween` della transizione, il cui orologio e'
 * quello vero: al tocco "indietro" il primo fotogramma e' lungo (si ricompone mezza app) e la
 * forma saltava avanti. Aspetta un fotogramma calmo (al massimo 80 ms, al posto dei 40 ms fissi di
 * ritardo di prima) e poi si stringe senza strappi. Se si torna indietro a meta' apertura la corsa
 * e' proporzionale a quanto resta da fare.
 */
suspend fun androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.animateContainerClose(
    target: Float = 0f
) {
    // Niente attesa: al tocco "indietro" la forma parte subito; il primo fotogramma lungo lo
    // assorbe il passo limitato dell'orologio.
    val travel = kotlin.math.abs(value - target).coerceIn(0.5f, 1f)
    val base = if (AppTheme.reduceMotion) 150L else CLOSE_MS
    animateSteppedTo(target, (base * travel).toLong() * 1_000_000L, ContainerCloseEasing)
}

/**
 * Quanto la transizione tiene viva la pagina che si chiude con [animateContainerClose]: la sua
 * animazione serve solo a questo (il disegno lo guida l'orologio a passo limitato, che con un
 * fotogramma lungo dura un poco di piu' dei 520 ms). Alla fine la forma e' gia' sul pulsante, la
 * pagina e' invisibile e non prende i tocchi.
 */
fun ailaContainerKeepAliveSpec(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
    tween(durationMillis = if (AppTheme.reduceMotion) 300 else 500)

private suspend fun androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.animateSteppedTo(
    target: Float,
    totalNanos: Long,
    easing: androidx.compose.animation.core.Easing
) {
    val from = value
    var elapsed = 0L
    var last = androidx.compose.runtime.withFrameNanos { it }
    while (elapsed < totalNanos) {
        val now = androidx.compose.runtime.withFrameNanos { it }
        elapsed += minOf(now - last, 32_000_000L)
        last = now
        val fraction = easing.transform((elapsed.toFloat() / totalNanos).coerceIn(0f, 1f))
        snapTo(from + (target - from) * fraction)
    }
    snapTo(target)
}

/**
 * "Esplosione" verso la schermata che sta sotto (Material): l'elemento toccato (una tessera) cresce
 * dal suo rettangolo fino a coprire lo schermo, poi si dissolve e lascia vedere la destinazione.
 * E' il container transform al contrario: non una pagina che nasce dall'elemento, ma l'elemento
 * che diventa la schermata. Si applica alla pagina che si sta chiudendo (la Ricerca), sopra il
 * suo contenuto; [color] e' quello dell'elemento. Il contenuto della pagina svanisce nel primo
 * 40% della corsa. Disegna con i Path a curve (niente drawRoundRect, vedi [ailaRoundRectPath]).
 */
@Composable
fun Modifier.ailaExplodeOut(
    progress: () -> Float,
    origin: AilaTransformOrigin,
    color: Color
): Modifier {
    val selfOffset = remember { arrayOf(Offset.Zero) }
    val path = remember { Path() }
    return this
        .onGloballyPositioned {
            val position = it.positionInRoot()
            // Pagina in chiusura (vedi ailaNoTouchWhile): conta dove si vede, non il layout.
            selfOffset[0] = Offset(visibleXInRoot(position.x), position.y)
        }
        .drawWithContent {
            drawContent()
            val p = progress().coerceIn(0f, 1f)
            if (p <= 0f) return@drawWithContent
            val o = origin.bounds.translate(-selfOffset[0])
            fun lerp(a: Float, b: Float) = a + (b - a) * p
            val left = lerp(o.left, 0f)
            val top = lerp(o.top, 0f)
            val right = lerp(o.right, size.width)
            val bottom = lerp(o.bottom, size.height)
            val radius = lerp(origin.cornerRadiusPx, 0f).coerceIn(0f, minOf(right - left, bottom - top) / 2f)
            ailaRoundRectPathInto(path, left, top, right - left, bottom - top, radius)
            // Pieno finche' cresce, poi si dissolve sulla schermata che c'e' sotto.
            val fadeIn = (p / 0.12f).coerceIn(0f, 1f)
            val fadeOut = (1f - ((p - 0.55f) / 0.45f)).coerceIn(0f, 1f)
            drawPath(path, color = color, alpha = fadeIn * fadeOut)
        }
        .graphicsLayer { alpha = (1f - progress() / 0.4f).coerceIn(0f, 1f) }
}

/**
 * Disegna il contenuto dentro un "contenitore" che cresce dall'origine fino a tutto lo schermo
 * mentre [progress] va da 0 a 1: prima si vede il fondo del contenitore (come se la card si
 * allungasse), poi il contenuto della pagina compare in dissolvenza.
 */
fun Modifier.ailaContainerReveal(
    progress: () -> Float,
    origin: AilaTransformOrigin,
    containerColor: Color,
    /** Colore della pagina a schermo intero: il contenitore passa dal colore della card a questo. */
    pageColor: Color = containerColor,
    /** true mentre la pagina si richiude: da un pulsante la forma si stringe a cerchio in anticipo. */
    closing: () -> Boolean = { false },
    /**
     * true se [origin] e' gia' nelle coordinate di questo livello (un elemento dentro lo stesso
     * contenitore, es. una card del menu di un foglio) invece che nella radice dello schermo.
     */
    relative: Boolean = false,
    /** Filo intorno alla forma che cresce, per origini chiare sul chiaro (card bianca su foglio bianco). */
    outlineColor: Color = Color.Unspecified,
    /**
     * Dove arriva la forma a fine corsa, nelle coordinate di questo livello. Di norma tutto il
     * livello; per un foglio dal basso (livello a schermo intero, foglio in fondo) e' il suo
     * rettangolo, cosi' la card o il pulsante diventano il foglio e non tutto lo schermo.
     */
    targetRect: (() -> androidx.compose.ui.geometry.Rect?)? = null,
    /** Raggio degli angoli a fine corsa (es. quelli alti del foglio); 0 = squadrati. */
    targetRadiusPx: Float = 0f
): Modifier {
    val selfOffset = arrayOf(Offset.Zero)
    return this
        .onGloballyPositioned {
            val position = it.positionInRoot()
            // Pagina in chiusura (vedi ailaNoTouchWhile): conta dove si vede, non il layout.
            selfOffset[0] = Offset(visibleXInRoot(position.x), position.y)
        }
        // La forma che cresce e' il contorno del livello grafico, non un clipPath: il contorno a
        // rettangolo arrotondato lo ritaglia la GPU quasi gratis, mentre il clipPath di un
        // tracciato a ogni fotogramma su Android passa da una maschera ed era la causa degli
        // scatti. Cambiare contorno non ridisegna il contenuto: si aggiorna solo la proprieta'.
        .graphicsLayer {
            val p = progress().coerceIn(0f, 1f)
            if (p >= 0.999f) {
                clip = false
                shape = androidx.compose.ui.graphics.RectangleShape
                return@graphicsLayer
            }
            val o = if (relative) origin.bounds else origin.bounds.translate(-selfOffset[0])
            // Chiudendosi su un pulsante tondo la curva della chiusura passa quasi tutto il tempo
            // negli ultimi punti percentuali: con la geometria lineare la forma restava un
            // rettangolo alto per centinaia di millisecondi, e solo alla fine diventava il cerchio
            // del pulsante. Sotto meta' corsa la geometria (non il colore) rallenta, cosi' la forma
            // e' gia' un cerchio quando comincia a dissolversi. Oltre meta' e' identica a prima.
            val g = if (origin.buttonColor != null && closing()) {
                val t = (p / 0.5f).coerceIn(0f, 1f)
                p * t * t * (3f - 2f * t)
            } else p
            fun lerp(a: Float, b: Float) = a + (b - a) * g
            val t = targetRect?.invoke() ?: androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
            val rect = androidx.compose.ui.geometry.Rect(
                lerp(o.left, t.left), lerp(o.top, t.top), lerp(o.right, t.right), lerp(o.bottom, t.bottom)
            )
            val radius = lerp(origin.cornerRadiusPx, targetRadiusPx).coerceIn(0f, minOf(rect.width, rect.height) / 2f)
            shape = AilaRevealShape(rect, radius)
            clip = true
        }
        .drawWithContent {
            val p = progress().coerceIn(0f, 1f)
            if (p >= 0.999f) {
                drawContent()
                return@drawWithContent
            }
            // Il contenitore prende subito il colore della pagina (niente "flash" colorato) e il
            // contenuto compare solo alla fine, quando il contenitore e' quasi a tutto schermo:
            // durante il movimento si vede una forma pulita, non righe di testo tagliate.
            // Agli estremi la forma e' trasparente: la card vera, sotto, resta visibile e piena.
            // Aprendo la forma "nasce" sopra la card; chiudendo ci si dissolve sopra, e quando
            // sparisce si vede la card gia' completa invece di una sagoma vuota che si riempie.
            // Da un pulsante (stile Meteo dei Pixel): la forma parte piena del colore del pulsante,
            // esattamente sopra di lui; il colore passa a quello della pagina lungo quasi tutta la
            // corsa (niente lampo chiaro all'inizio), l'icona si dissolve con calma e il contenuto
            // arriva in una dissolvenza lunga. Al ritorno lo stesso al contrario.
            val fromButton = origin.buttonColor != null
            val contentAlpha = if (fromButton) ((p - 0.5f) / 0.42f).coerceIn(0f, 1f)
                else ((p - 0.72f) / 0.23f).coerceIn(0f, 1f)
            val colorT = (p / (if (fromButton) 0.75f else 0.3f)).coerceIn(0f, 1f)
            val fill = androidx.compose.ui.graphics.lerp(
                origin.buttonColor ?: containerColor, pageColor, colorT * colorT * (3f - 2f * colorT)
            )
            val edgeT = (p / (if (fromButton) 0.12f else 0.22f)).coerceIn(0f, 1f)
            val shapeAlpha = edgeT * edgeT * (3f - 2f * edgeT)
            // Il ritaglio lo fa il contorno del livello (sopra): qui solo cosa c'e' dentro. Finche'
            // il contenuto e' invisibile non lo si disegna affatto, basta un rettangolo.
            if (contentAlpha > 0f) {
                drawContent()
                if (contentAlpha < 1f) drawRect(fill, alpha = 1f - contentAlpha)
            } else {
                // Il contenuto viene "disegnato" subito ma con un ritaglio vuoto: non si vede nulla,
                // pero' la pagina si registra adesso, nei primi fotogrammi in cui la forma e' ancora
                // piccola. Prima si registrava la prima volta che compariva, a meta' corsa, e proprio
                // li' c'era lo scatto (a ogni apertura di ricerca, notifiche e profilo).
                clipRect(0f, 0f, 0f, 0f) { this@drawWithContent.drawContent() }
                drawRect(fill, alpha = shapeAlpha)
            }
            if (outlineColor != Color.Unspecified) {
                val o = if (relative) origin.bounds else origin.bounds.translate(-selfOffset[0])
                val t = targetRect?.invoke() ?: androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                val rect = androidx.compose.ui.geometry.Rect(
                    o.left + (t.left - o.left) * p, o.top + (t.top - o.top) * p,
                    o.right + (t.right - o.right) * p, o.bottom + (t.bottom - o.bottom) * p
                )
                val radius = (origin.cornerRadiusPx + (targetRadiusPx - origin.cornerRadiusPx) * p)
                    .coerceIn(0f, minOf(rect.width, rect.height) / 2f)
                // Il filo si dissolve mentre la forma prende il colore della pagina.
                val lineAlpha = (1f - p * 1.6f).coerceIn(0f, 1f) * shapeAlpha
                if (lineAlpha > 0f) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = outlineColor.copy(alpha = outlineColor.alpha * lineAlpha),
                        topLeft = androidx.compose.ui.geometry.Offset(rect.left + stroke / 2, rect.top + stroke / 2),
                        size = androidx.compose.ui.geometry.Size(rect.width - stroke, rect.height - stroke),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(maxOf(radius - stroke / 2, 0f)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                }
            }
        }
}

/** Contorno del container transform: un rettangolo arrotondato dentro al livello, dove sta ora. */
private class AilaRevealShape(
    private val rect: androidx.compose.ui.geometry.Rect,
    private val radius: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline = androidx.compose.ui.graphics.Outline.Rounded(
        androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(radius))
    )
}


/**
 * Rettangolo ad angoli tondi fatto di curve di Bezier: niente addRoundRect/archi, che su Android
 * in KMP possono crashare (vedi la nota in AppIcons).
 */
fun ailaRoundRectPath(left: Float, top: Float, w: Float, h: Float, radius: Float): Path =
    Path().also { ailaRoundRectPathInto(it, left, top, w, h, radius) }

/** Come [ailaRoundRectPath], ma riempie un Path esistente (per le animazioni). */
fun ailaRoundRectPathInto(path: Path, left: Float, top: Float, w: Float, h: Float, radius: Float): Unit = with(path) {
    reset()
    val r = radius.coerceIn(0f, minOf(w, h) / 2f)
    val k = r * 0.5523f
    val right = left + w
    val bottom = top + h
    moveTo(left + r, top)
    lineTo(right - r, top)
    cubicTo(right - r + k, top, right, top + r - k, right, top + r)
    lineTo(right, bottom - r)
    cubicTo(right, bottom - r + k, right - r + k, bottom, right - r, bottom)
    lineTo(left + r, bottom)
    cubicTo(left + r - k, bottom, left, bottom - r + k, left, bottom - r)
    lineTo(left, top + r)
    cubicTo(left, top + r - k, left + r - k, top, left + r, top)
    close()
}

// =============================================================================================
// Vocabolario per le animazioni delle schermate
// =============================================================================================
//
// Le schermate scrivevano a mano ~70 tween e molle con durate sparse (90, 100, 120, 160, 180,
// 190, 200, 220, 240, 250, 260...), e nessuna di quelle sentiva "Riduci movimento". Da qui in
// poi una schermata sceglie *che cosa* anima (una dissolvenza, un movimento, un colore, una
// molla viva) e la durata da una scala corta; il modo lo decide AilaMotion, uguale ovunque.

/** Scala delle durate (ms). Breve per le uscite, media per entrate e colori, lunga per gli effetti. */
object AilaDuration {
    /** Uscite rapide, risposte al tocco. */
    const val Quick = 120
    /** Entrate, colori, comparse. */
    const val Standard = 200
    /** Riempimenti e barre che crescono, evidenziazioni. */
    const val Slow = 400
}

/**
 * Dissolvenza (alpha). Resta anche con "Riduci movimento": cambiare opacita' non e' movimento,
 * solo un po' piu' breve.
 */
fun <T> ailaFadeSpec(durationMillis: Int = AilaDuration.Standard, delayMillis: Int = 0): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (AppTheme.reduceMotion) tween(durationMillis = minOf(durationMillis, REDUCED_FADE_MS))
    else tween(durationMillis = durationMillis, delayMillis = delayMillis)

/** Colore che cambia (sfondi, bordi, testo selezionato). Come la dissolvenza: resta, piu' breve. */
fun <T> ailaColorSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> = ailaFadeSpec(AilaDuration.Standard)

/**
 * Movimento a durata fissa (scorrimenti brevi, espansioni, barre che crescono). Con "Riduci
 * movimento" arriva subito alla fine.
 */
fun <T> ailaMoveSpec(
    durationMillis: Int = AilaDuration.Standard,
    delayMillis: Int = 0,
    easing: androidx.compose.animation.core.Easing = androidx.compose.animation.core.FastOutSlowInEasing
): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (AppTheme.reduceMotion) androidx.compose.animation.core.snap()
    else tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)

/**
 * Molla "giocosa" con un po' di rimbalzo, per ciò che risponde al dito (pallini, chip, banchi
 * selezionati). Con "Riduci movimento" niente rimbalzo.
 */
fun <T> ailaBouncySpring(): SpringSpec<T> =
    if (AppTheme.reduceMotion) reducedMotionSpring()
    else spring(dampingRatio = 0.75f, stiffness = 450f)

/** Comparsa di un blocco che si apre sotto (campi facoltativi, dettagli): dissolvenza + apertura. */
fun ailaExpandEnter(): EnterTransition =
    fadeIn(ailaFadeSpec(AilaDuration.Standard)) +
        androidx.compose.animation.expandVertically(ailaMoveSpec(AilaDuration.Standard))

/** Chiusura del blocco aperto con [ailaExpandEnter]. */
fun ailaCollapseExit(): ExitTransition =
    fadeOut(ailaFadeSpec(AilaDuration.Quick)) +
        androidx.compose.animation.shrinkVertically(ailaMoveSpec(AilaDuration.Standard))
