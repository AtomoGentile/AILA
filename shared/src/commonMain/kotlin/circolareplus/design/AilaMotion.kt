package circolareplus.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Entrata morbida di un elemento: sale di pochi dp e sfuma dentro alla prima comparsa.
 * `index` sfalsa l'ingresso degli elementi di una lista, così arrivano a cascata invece che tutti
 * insieme — ritardo piccolo e con un tetto, altrimenti in fondo a una lista lunga si aspetterebbe.
 */
@Composable
fun Modifier.ailaAppear(index: Int = 0, enabled: Boolean = true): Modifier {
    if (!enabled) return this

    // 1. Usiamo rememberSaveable: una volta che l'elemento è apparso, non deve più rigenerare
    // l'animazione se l'utente scorrere su e giù nella lista.
    // 2. Riduciamo i tempi per evitare "momenti di vuoto" durante lo scrolling veloce.
    val isInitialBatch = index < 6
    var shown by rememberSaveable { mutableStateOf(!isInitialBatch) }

    if (isInitialBatch) {
        LaunchedEffect(Unit) {
            if (!shown) {
                // Ritardo dimezzato (20ms) e tetto più basso per una cascata più scattante.
                delay((index * 20L))
                shown = true
            }
        }
    }

    val appearAlpha = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        // Durata ridotta a 200ms per rendere la comparsa meno pesante.
        animationSpec = if (isInitialBatch) tween(durationMillis = 200) else snap(),
        label = "ailaAppearAlpha"
    )
    val offsetY = animateDpAsState(
        targetValue = if (shown) 0.dp else 12.dp,
        animationSpec = if (isInitialBatch) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else snap(),
        label = "ailaAppearOffset"
    )

    // I valori animati si leggono dentro graphicsLayer (fase di disegno), non nella composizione:
    // prima `offset(y)` + `alpha()` ricomponevano ogni card a ogni fotogramma dell'animazione, e
    // con una decina di card in cascata all'apertura di una tab si sentiva il lag.
    return this.graphicsLayer {
        alpha = appearAlpha.value
        translationY = offsetY.value.toPx()
    }
}

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
