package circolareplus.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaGlyphBuilding
import circolareplus.design.AilaMarkBuildSeconds
import kotlin.math.PI
import kotlin.math.sin

/**
 * L'intro del logo si vede una volta per processo, cioè all'avvio a freddo. Se la schermata
 * ricompare dopo (nuovo tentativo senza rete, rotazione, ripristino della sessione ripetuto) il
 * logo è già intero e non si rifà tutta l'animazione.
 */
private var ailaIntroPlayed = false

/** Vero finché l'intro del logo di questo avvio non è stata vista: MainAppShell aspetta la fine. */
fun isAilaIntroPending(): Boolean = !ailaIntroPlayed

/** Fine dell'intro: il segno è costruito e la scritta "AILA" è entrata tutta. */
private const val IntroSeconds = 1.7f

/** Lo stesso blu notte dello splash di sistema (themes.xml su Android, LaunchBackground su iOS). */
private val SplashNavy = Color(0xFF0A1330)

/**
 * Fa avanzare [clock] fotogramma per fotogramma fino a [until] secondi. Ogni passo vale al massimo
 * 1/30 di secondo: all'avvio a freddo il telefono è carico (classi da caricare, codice non ancora
 * ottimizzato) e salta fotogrammi; con un'animazione legata all'orologio si vedeva il primo banco e
 * poi il logo già intero. Così, se il telefono resta indietro, l'intro rallenta ma si vede tutta.
 */
private suspend fun advanceClock(clock: MutableFloatState, until: Float) {
    var last = withFrameNanos { it }
    while (clock.floatValue < until) {
        val now = withFrameNanos { it }
        val step = ((now - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
        last = now
        clock.floatValue = (clock.floatValue + step).coerceAtMost(until)
    }
}

/**
 * Schermata di avvio e di caricamento: sfondo blu scuro sfumato, il marchio AILA e la scritta.
 *
 * **L'intro.** Al primo avvio il marchio si costruisce pezzo per pezzo, come nel video di
 * presentazione: banchi, collegamenti, scintilla, poi "AILA" lettera per lettera e il motto. Lo
 * splash di sistema mostra solo il blu notte, senza logo, proprio per non far vedere il marchio
 * intero un istante prima che si ricostruisca. Per lo stesso motivo lo sfondo sfumato compare
 * partendo da quel blu pieno, così il passaggio dallo splash a qui non si nota.
 *
 * L'animazione gira mentre l'app ripristina la sessione e non lo rallenta. Se il ripristino finisce
 * prima, si aspetta la fine dell'intro ([onIntroFinished]); se dura di più, il logo continua a
 * respirare e compaiono la rotellina e [message].
 *
 * Il tempo dell'animazione viene letto solo in fase di disegno (Canvas e graphicsLayer): la
 * schermata non si ricompone a ogni fotogramma.
 *
 * **Riduci movimento.** Con l'impostazione di sistema accesa il logo compare gia' costruito e
 * fermo: niente intro, niente respiro (il ciclo infinito non viene proprio creato). Resta solo la
 * rotellina, che e' un indicatore di attesa e non una decorazione.
 */
@Composable
fun AilaLoadingScreen(
    message: String = "Caricamento...",
    onIntroFinished: () -> Unit = {}
) {
    // Letto qui dal sistema e non da AppTheme.reduceMotion: questa e' la prima schermata e
    // AilaTheme lo copia in AppTheme solo dopo il primo fotogramma, quando l'intro sarebbe gia'
    // partita.
    val reduceMotion = circolareplus.platform.isReduceMotionEnabled()
    // Secondi dall'inizio dell'intro. Se l'intro è già stata vista (o il movimento è ridotto)
    // parte dalla fine.
    val clock = remember {
        mutableFloatStateOf(if (ailaIntroPlayed || reduceMotion) IntroSeconds + 1f else 0f)
    }
    // Chiave reduceMotion: se l'impostazione si accende durante l'intro, il ciclo dei fotogrammi
    // viene annullato e il logo salta subito alla fine. onIntroFinished si puo' chiamare piu' volte
    // (abbassa solo un flag).
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            ailaIntroPlayed = true
            clock.floatValue = IntroSeconds + 1f
            onIntroFinished()
            return@LaunchedEffect
        }
        if (!ailaIntroPlayed) {
            advanceClock(clock, IntroSeconds)
            ailaIntroPlayed = true
        }
        onIntroFinished()
        // Se il caricamento non è ancora finito, il tempo va avanti ancora un poco per far
        // comparire rotellina e messaggio.
        advanceClock(clock, IntroSeconds + 1f)
    }
    // Respiro lento del logo, che si innesta piano a costruzione finita: fa capire che l'app sta
    // lavorando e non è piantata.
    // Con il movimento ridotto la transizione infinita non si crea affatto (fase ferma a 0, cioè
    // scala 1): un'animazione infinita con durata cambiata girerebbe comunque a ogni fotogramma.
    val breathPhase: State<Float> = if (reduceMotion) {
        rememberUpdatedState(0f)
    } else {
        val breath = rememberInfiniteTransition(label = "respiro")
        breath.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
            label = "faseRespiro"
        )
    }
    fun reveal(start: Float, duration: Float) = ((clock.floatValue - start) / duration).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Stesso gradiente dell'onboarding: prima erano due blu notte scritti a mano qui.
            .background(circolareplus.design.AppTheme.HeroGradientDeep),
        contentAlignment = Alignment.Center
    ) {
        // Il blu pieno dello splash che si scioglie nel gradiente.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - reveal(0f, 0.6f) }
                .background(SplashNavy)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AilaGlyphBuilding(
                size = 132.dp,
                elapsed = { clock.floatValue },
                modifier = Modifier.graphicsLayer {
                    val settled = reveal(AilaMarkBuildSeconds, 0.5f)
                    val scale = 1f + 0.035f * sin(breathPhase.value) * settled
                    scaleX = scale
                    scaleY = scale
                }
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                "AILA".forEachIndexed { i, letter ->
                    Text(
                        text = letter.toString(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            val p = 1f - (1f - reveal(0.9f + i * 0.09f, 0.7f)).let { q -> q * q * q * q * q }
                            alpha = p
                            translationY = (1f - p) * 24.dp.toPx()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La vita di classe, in un'app.",
                fontSize = 15.sp,
                color = Color(0xCCFFFFFF),
                modifier = Modifier.graphicsLayer {
                    val p = reveal(1.25f, 0.45f)
                    alpha = p
                    translationY = (1f - p) * 10.dp.toPx()
                }
            )
            // Rotellina e messaggio occupano il loro posto da subito (niente salti di layout) ma si
            // vedono solo se il caricamento dura più dell'intro.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = reveal(IntroSeconds + 0.1f, 0.3f) }
            ) {
                Spacer(modifier = Modifier.height(28.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = Color(0xCCFFFFFF)
                )
            }
        }
    }
}
