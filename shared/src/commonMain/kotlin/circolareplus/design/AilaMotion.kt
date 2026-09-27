package circolareplus.design

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
import androidx.compose.runtime.currentCompositeKeyHash
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
 * Elementi gia' "presentati" in questa sessione, per posizione nell'albero di composizione.
 * Serve a [ailaAppear]: l'entrata si vede la prima volta che si apre una schermata, poi non piu'.
 */
private object AilaAppearMemory {
    val seen = HashSet<Int>()
}

/**
 * Entrata di un elemento: sfuma dentro e si "posa" (scala da 0.96 a 1, pochi dp di salita) con
 * una molla, e un leggero sfalsamento fra i primi elementi di una lista.
 *
 * **Solo la prima volta per sessione.** Prima la cascata ripartiva a ogni cambio di tab: carina
 * all'inizio, alla lunga stancava (feedback di Simone) e rallentava la lettura di schermate gia'
 * note. Ora al ritorno su una schermata gli elementi ci sono gia': il movimento lo da' la
 * transizione fra le tab (vedi MainAppShell), che e' breve e uguale per tutta la schermata.
 *
 * La chiave e' la posizione nella composizione ([currentCompositeKeyHash]), che resta la stessa
 * quando si rientra nella stessa schermata; nelle liste include la chiave dell'elemento, quindi un
 * elemento nuovo (una proposta appena pubblicata) entra comunque con l'animazione.
 */
@Composable
fun Modifier.ailaAppear(index: Int = 0, enabled: Boolean = true): Modifier {
    if (!enabled) return this

    val key = currentCompositeKeyHash
    // Deciso una volta sola per questa comparsa: se l'elemento era gia' stato presentato, o e'
    // oltre i primi della lista (arriva scorrendo, e li' un'animazione sarebbe solo ritardo),
    // resta fermo.
    val animate = remember { index < 6 && AilaAppearMemory.seen.add(key) }
    if (!animate) {
        if (index >= 6) AilaAppearMemory.seen.add(key)
        return this
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(4) * 35L)
        progress.animateTo(
            1f,
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    // Valori letti nel graphicsLayer: nessuna ricomposizione a ogni fotogramma.
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.96f + 0.04f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 8.dp.toPx()
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
