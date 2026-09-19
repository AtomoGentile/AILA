package circolareplus.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaLogoTile
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Onboarding a 3 schermate, mostrato una sola volta al primo avvio (prima del login).
 * Il flag sta in [circolareplus.data.local.LocalSettingsManager].
 *
 * Rifatto dopo "ste schermate puoi farle meglio". Prima era corretto ma inerte: un riquadro
 * sfumato con dentro un cerchio e un'icona ferma, cambio pagina solo col pulsante, e l'unica
 * animazione erano i pallini in fondo. Ora:
 *
 * - l'illustrazione è viva — l'icona respira dentro un anello di puntini che ruota lentamente,
 *   e due aloni di luce si spostano nel riquadro, così ogni pagina ha un movimento proprio;
 * - ogni pagina ha un colore d'accento diverso, quindi si capisce a colpo d'occhio di aver
 *   cambiato schermata;
 * - si può scorrere col dito (prima si poteva solo premere "Avanti", cosa che in un onboarding
 *   sorprende: è il gesto che chiunque prova per primo) e si può tornare indietro;
 * - il passaggio tra una pagina e l'altra è in dissolvenza invece che istantaneo.
 *
 * Resta volutamente senza HorizontalPager: il gesto è gestito qui con `detectHorizontalDragGestures`
 * e non serve introdurre una dipendenza che il progetto non ha mai usato altrove.
 */
private enum class OnboardingIcon { DOCUMENT, BELL, SPARKLE }

private data class OnboardingPage(
    val iconKind: OnboardingIcon,
    val title: String,
    val body: String,
    val accent: Color,
    val gradient: List<Color>
)

private val onboardingPages = listOf(
    OnboardingPage(
        iconKind = OnboardingIcon.DOCUMENT,
        title = "Tutto ciò che conta,\nin un unico posto.",
        body = "Circolari, calendario, bacheca e mappa dei posti: una sola app invece di cinque chat.",
        accent = Color(0xFF5A9BFF),
        gradient = listOf(Color(0xFF0B1330), Color(0xFF1B2E7A), Color(0xFF2F5BD8))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.BELL,
        title = "Sempre aggiornato.",
        body = "Una notifica quando esce qualcosa che ti riguarda. Scegli tu quali ricevere.",
        accent = Color(0xFF8B5CF6),
        gradient = listOf(Color(0xFF0F1236), Color(0xFF2B2A80), Color(0xFF6D4FD8))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.SPARKLE,
        title = "La tua classe,\npiù intelligente.",
        body = "AILA Assistant legge le circolari al posto tuo e ti dice in una riga se ti riguardano.",
        accent = Color(0xFF06B6D4),
        gradient = listOf(Color(0xFF071A33), Color(0xFF0E4C6E), Color(0xFF2F7FB8))
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var pageIndex by remember { mutableStateOf(0) }
    val page = onboardingPages[pageIndex]
    val isLast = pageIndex == onboardingPages.lastIndex

    // Accumula lo scorrimento del dito e decide a fine gesto: una soglia sola, così un tocco
    // storto non fa cambiare pagina per sbaglio.
    var dragAccumulated by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccumulated < -90f && pageIndex < onboardingPages.lastIndex) {
                            pageIndex++
                        } else if (dragAccumulated > 90f && pageIndex > 0) {
                            pageIndex--
                        }
                        dragAccumulated = 0f
                    },
                    onDragCancel = { dragAccumulated = 0f }
                ) { _, delta -> dragAccumulated += delta }
            }
            .padding(AppTheme.Space24)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AilaLogoTile(size = 26.dp)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "AILA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = AppTheme.PrimaryBlue
                )
            }
            Text(
                text = "Salta",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onFinish() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Crossfade(
            targetState = pageIndex,
            animationSpec = tween(durationMillis = 320),
            label = "onboardingIllustration"
        ) { index ->
            OnboardingIllustration(onboardingPages[index])
        }

        Spacer(modifier = Modifier.height(AppTheme.Space32))

        Crossfade(
            targetState = pageIndex,
            animationSpec = tween(durationMillis = 260),
            label = "onboardingCopy"
        ) { index ->
            val shown = onboardingPages[index]
            Column {
                Text(
                    text = shown.title,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark,
                    lineHeight = 33.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                Text(
                    text = shown.body,
                    fontSize = 15.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AilaPrimaryButton(
            text = if (isLast) "Inizia" else "Avanti",
            onClick = { if (isLast) onFinish() else pageIndex++ },
            fillMaxWidth = true
        )

        Spacer(modifier = Modifier.height(AppTheme.Space20))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            onboardingPages.indices.forEach { index ->
                val isActive = index == pageIndex
                val dotWidth by animateDpAsState(
                    targetValue = if (isActive) 24.dp else 8.dp,
                    animationSpec = tween(durationMillis = 240),
                    label = "onboardingDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isActive) page.accent else AppTheme.Hairline,
                    animationSpec = tween(durationMillis = 240),
                    label = "onboardingDotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(dotWidth)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(dotColor)
                        .clickable { pageIndex = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space8))

        Text(
            text = "${pageIndex + 1} di ${onboardingPages.size}",
            fontSize = 11.sp,
            color = AppTheme.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Il riquadro illustrativo. Tutto il movimento sta qui: aloni che scorrono, anello di puntini
 * che ruota, icona che respira. È l'unica parte animata in continuo dell'app e vive solo finché
 * l'onboarding è a schermo, quindi non consuma nulla dopo il primo avvio.
 */
@Composable
private fun OnboardingIllustration(page: OnboardingPage) {
    val transition = rememberInfiniteTransition(label = "onboardingLoop")

    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(AppTheme.CardCornerRadius + 8.dp))
            .background(Brush.linearGradient(page.gradient)),
        contentAlignment = Alignment.Center
    ) {
        // Due aloni che si spostano lentamente: danno profondità al fondo, che altrimenti è una
        // sfumatura ferma.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(page.accent.copy(alpha = 0.34f), Color(0x00000000)),
                        center = Offset(160f + drift * 260f, 120f),
                        radius = 340f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2AFFFFFF), Color(0x00FFFFFF)),
                        center = Offset(620f - drift * 300f, 520f),
                        radius = 300f
                    )
                )
        )

        // Anello di puntini in orbita: sostituisce il cerchio statico di prima.
        repeat(8) { i ->
            val angle = (orbit + i * 45f) * PI.toFloat() / 180f
            val alpha = 0.25f + 0.45f * ((i % 3) / 2f)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cos(angle) * 76.dp.toPx()).roundToInt(),
                            (sin(angle) * 76.dp.toPx()).roundToInt()
                        )
                    }
                    .size(if (i % 2 == 0) 7.dp else 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
        }

        // Disco centrale con l'icona: respira, così l'occhio ci torna sopra.
        Box(
            modifier = Modifier
                .size((96 * breath).dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x3DFFFFFF), Color(0x14FFFFFF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when (page.iconKind) {
                OnboardingIcon.DOCUMENT ->
                    AppIcons.Document(modifier = Modifier.size(42.dp), color = Color.White)
                OnboardingIcon.BELL ->
                    AppIcons.Bell(modifier = Modifier.size(42.dp), color = Color.White)
                OnboardingIcon.SPARKLE ->
                    AppIcons.Sparkle(modifier = Modifier.size(42.dp), color = Color.White)
            }
        }
    }
}
