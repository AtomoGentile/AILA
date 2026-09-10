package circolareplus.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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

    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        // Durata ridotta a 200ms per rendere la comparsa meno pesante.
        animationSpec = if (isInitialBatch) tween(durationMillis = 200) else snap(),
        label = "ailaAppearAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (shown) 0.dp else 12.dp,
        animationSpec = if (isInitialBatch) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else snap(),
        label = "ailaAppearOffset"
    )

    return this.offset(y = offsetY).alpha(alpha)
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "ailaPressScale"
    )

    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ailaPressAlpha"
    )

    return this
        .scale(scale)
        .alpha(pressAlpha)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) { onClick() }
}
