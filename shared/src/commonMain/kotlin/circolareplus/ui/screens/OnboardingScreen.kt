package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.ai.LocalAiModel
import circolareplus.ai.ModelDownloadState
import circolareplus.design.AilaLogoTile
import circolareplus.design.iosSafeDrawingPadding
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFieldColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tutto quello che il passo "Scegli l'AI" dell'onboarding deve poter fare sull'app vera.
 *
 * Sta in un oggetto a parte, costruito da [circolareplus.ui.MainAppShell], perché l'onboarding è
 * una schermata di presentazione e non deve conoscere `AppContainer`: è la shell a sapere dove
 * finiscono chiave, provider e modello scelti (le stesse impostazioni che poi mostra la schermata
 * Impostazioni, così le due non possono andare fuori sincrono).
 */
class OnboardingAiSetup(
    /** Frase se l'AI sul telefono non è utilizzabile qui (emulatore non ARM, iPhone senza Apple Intelligence). */
    val unavailableReason: String?,
    /** RAM totale in MB, 0 se non rilevabile. */
    val deviceRamMb: Int,
    /** Modelli selezionabili, con il consigliato per questo telefono in prima posizione. */
    val localModels: List<LocalAiModel>,
    val isModelInstalled: (LocalAiModel) -> Boolean,
    /** Prova la chiave con una chiamata minima e restituisce la risposta a parole. */
    val testApiKey: suspend (String) -> String,
    /** Salva la chiave e mette Google AI Studio come provider principale. */
    val saveApiKey: (String) -> Unit,
    val downloadModel: suspend (LocalAiModel, (Long, Long) -> Unit) -> ModelDownloadState,
    /** Sceglie il modello e mette l'AI locale come provider principale. */
    val activateModel: (LocalAiModel) -> Unit,
    /** Ferma il download in corso (Android: annulla il lavoro in background, non solo l'ascolto). */
    val cancelDownload: (LocalAiModel) -> Unit = {}
)

/**
 * Onboarding del primo avvio (prima del login): un giro veloce delle funzioni e, come ultimo
 * passo, la scelta dell'AI. Il flag sta in [circolareplus.data.local.LocalSettingsManager].
 *
 * Il tour vero sono cinque pagine con l'illustrazione animata (icona che respira dentro un anello
 * di puntini in orbita, aloni di luce, un colore d'accento per pagina) e si scorre col dito o con
 * "Avanti". Resta volutamente senza HorizontalPager: il gesto è gestito qui con
 * `detectHorizontalDragGestures` e non serve una dipendenza che il progetto non usa altrove.
 *
 * Il sesto passo ([AiSetupStep]) non è una pagina come le altre: contiene un campo di testo, un
 * download e bottoni, quindi ha una colonna scorrevole invece dell'illustrazione e lo swipe è
 * spento — un trascinamento mentre si incolla la chiave non deve far uscire dal passo. Si esce
 * col bottone in fondo, che dice onestamente cosa succede: "Inizia" se l'AI è pronta, "Salta e
 * inizia" se no (l'app funziona lo stesso, con analisi più semplici, e si configura poi da
 * Impostazioni). Con [aiSetup] nullo il passo non c'è e resta il solo tour.
 */
private enum class OnboardingIcon { DOCUMENT, CALENDAR, CHAT, CHAIR, SPARKLE }

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
        body = "Circolari con notifica, calendario, bacheca e mappa dei posti: una sola app " +
            "invece di cinque chat. Facciamo un giro veloce.",
        accent = Color(0xFF5A9BFF),
        gradient = listOf(Color(0xFF0B1330), Color(0xFF1B2E7A), Color(0xFF2F5BD8))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.CALENDAR,
        title = "Le scadenze,\nnel calendario.",
        body = "Verifiche, pagamenti e uscite vengono letti dalle circolari e proposti come " +
            "eventi. Tu confermi con un tocco.",
        accent = Color(0xFF8B5CF6),
        gradient = listOf(Color(0xFF0F1236), Color(0xFF2B2A80), Color(0xFF6D4FD8))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.CHAT,
        title = "La classe\ndecide insieme.",
        body = "Proponi idee in bacheca, vota quelle degli altri e prenota le interrogazioni " +
            "con i sondaggi.",
        accent = Color(0xFFF59E0B),
        gradient = listOf(Color(0xFF2A1408), Color(0xFF7A3B12), Color(0xFFD9822B))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.CHAIR,
        title = "Banchi giusti\nper tutti.",
        body = "Il Rappresentante propone la disposizione dei posti. Tu esprimi le tue " +
            "preferenze, in modo riservato, e l'app ne tiene conto.",
        accent = Color(0xFF10B981),
        gradient = listOf(Color(0xFF06231B), Color(0xFF0E5C47), Color(0xFF2BA07E))
    ),
    OnboardingPage(
        iconKind = OnboardingIcon.SPARKLE,
        title = "Chiedi ad\nAILA Assistant.",
        body = "Una domanda in italiano e una risposta costruita sui dati della tua classe, " +
            "con le fonti da aprire. Nel prossimo passo scegli l'AI che la alimenta.",
        accent = Color(0xFF06B6D4),
        gradient = listOf(Color(0xFF071A33), Color(0xFF0E4C6E), Color(0xFF2F7FB8))
    )
)

private val aiStepAccent = Color(0xFF06B6D4)

/** Durata e curva di ogni cambio pagina: una sola, così tutto ciò che si muove va a tempo. */
private const val PAGE_MS = 340
private val PageEasing = FastOutSlowInEasing

/** Le due strade del passo AI. Nessuna preselezionata: la scelta deve essere consapevole. */
private enum class AiChoice { GOOGLE, LOCAL }

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    aiSetup: OnboardingAiSetup? = null
) {
    val tourSize = onboardingPages.size
    val totalSteps = tourSize + if (aiSetup != null) 1 else 0
    var stepIndex by remember { mutableStateOf(0) }
    val isAiStep = aiSetup != null && stepIndex == tourSize
    val isLast = stepIndex == totalSteps - 1
    val accent = if (isAiStep) aiStepAccent else onboardingPages[stepIndex].accent

    var aiConfigured by remember { mutableStateOf(false) }
    // Download o prova della chiave in corso: finché dura non si cambia passo, perché uscire dal
    // passo AI ne butterebbe via lo stato a metà.
    var aiBusy by remember { mutableStateOf(false) }

    // "Indietro" di sistema: prima chiudeva l'app anche a metà onboarding.
    circolareplus.platform.PlatformBackHandler(enabled = stepIndex > 0 && !aiBusy) { stepIndex-- }

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
                        if (!isAiStep && !aiBusy) {
                            // Dal tour non si scivola sul passo AI col dito: ci si arriva con
                            // "Avanti", così non ci si finisce per sbaglio.
                            if (dragAccumulated < -90f && stepIndex < tourSize - 1) {
                                stepIndex++
                            } else if (dragAccumulated > 90f && stepIndex > 0) {
                                stepIndex--
                            }
                        }
                        dragAccumulated = 0f
                    },
                    onDragCancel = { dragAccumulated = 0f }
                ) { _, delta -> dragAccumulated += delta }
            }
            .iosSafeDrawingPadding()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
            // "Indietro" visibile: prima si tornava al passo precedente solo col gesto/tasto di
            // sistema o trascinando, e dal passo AI il trascinamento e' disattivato di proposito
            // (su iOS il gesto di sistema non c'e', quindi non si poteva tornare al tour).
            if (stepIndex > 0) {
                Text(
                    text = "Indietro",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !aiBusy) { stepIndex-- }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (!isLast) {
                Text(
                    text = "Salta",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !aiBusy) { onFinish() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            }
        }

        // Un solo contenitore per il tour e per il passo AI. Nel vecchio onboarding illustrazione
        // e testo stavano in due Crossfade con durate diverse, in zone diverse dello schermo: si
        // muovevano fuori tempo e la pagina "scattava". Ora ogni cambio di pagina è un unico
        // movimento con la stessa durata e la stessa curva ([PAGE_MS], [PageEasing]), e la
        // direzione segue quella della navigazione (avanti da destra, indietro da sinistra).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = isAiStep,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    ((fadeIn(tween(PAGE_MS, delayMillis = 90, easing = PageEasing)) +
                        slideInHorizontally(tween(PAGE_MS, easing = PageEasing)) { dir * (it / 8) }) togetherWith
                        (fadeOut(tween(PAGE_MS / 2, easing = PageEasing)) +
                            slideOutHorizontally(tween(PAGE_MS, easing = PageEasing)) { -dir * (it / 8) }))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "onboardingArea"
            ) { showAiStep ->
                if (showAiStep && aiSetup != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = AppTheme.Space16, bottom = AppTheme.Space16)
                    ) {
                        AiSetupStep(
                            setup = aiSetup,
                            onConfigured = { aiConfigured = true },
                            onBusyChange = { aiBusy = it }
                        )
                    }
                } else {
                    // Sul passo AI questo ramo esce con l'ultima pagina del tour ancora
                    // selezionata (coerce): così non cambia nulla mentre svanisce.
                    val tourIndex = stepIndex.coerceAtMost(tourSize - 1)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.weight(1f))
                        // Una sola illustrazione per tutto il tour: cambia colore ma non viene
                        // ricreata, quindi orbita e "respiro" non ripartono da zero a ogni pagina.
                        OnboardingIllustration(onboardingPages[tourIndex])
                        Spacer(modifier = Modifier.height(AppTheme.Space32))
                        OnboardingCopy(tourIndex)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        AilaPrimaryButton(
            text = when {
                aiBusy -> "Attendi…"
                isAiStep && !aiConfigured -> "Salta e inizia"
                isLast -> "Inizia"
                else -> "Avanti"
            },
            enabled = !aiBusy,
            onClick = { if (isLast) onFinish() else stepIndex++ },
            fillMaxWidth = true
        )

        Spacer(modifier = Modifier.height(AppTheme.Space20))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (0 until totalSteps).forEach { index ->
                val isActive = index == stepIndex
                val dotWidth by animateDpAsState(
                    targetValue = if (isActive) 24.dp else 8.dp,
                    animationSpec = tween(durationMillis = 240),
                    label = "onboardingDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isActive) accent else AppTheme.Hairline,
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
                        .clickable(enabled = !aiBusy) { stepIndex = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space8))

        Text(
            text = "${stepIndex + 1} di $totalSteps",
            fontSize = 11.sp,
            color = AppTheme.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Titolo e testo della pagina del tour, con il cambio pagina che scorre e sfuma.
 *
 * L'altezza è riservata una volta per tutte: sotto l'`AnimatedContent` ci sono tutte le pagine
 * sovrapposte e invisibili, e il riquadro prende quella della più alta. Senza, ogni pagina aveva
 * un testo di altezza diversa e — con l'illustrazione centrata da due spacer — questa saliva e
 * scendeva a ogni cambio: era il "salto" di zone dello schermo che si vedeva prima. Le copie
 * invisibili sono nascoste anche ai lettori di schermo.
 */
@Composable
private fun OnboardingCopy(pageIndex: Int) {
    Box(modifier = Modifier.fillMaxWidth()) {
        onboardingPages.forEach { page ->
            OnboardingCopyText(page, Modifier.alpha(0f).clearAndSetSemantics { })
        }
        AnimatedContent(
            targetState = pageIndex,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                ((fadeIn(tween(PAGE_MS, delayMillis = 90, easing = PageEasing)) +
                    slideInHorizontally(tween(PAGE_MS, easing = PageEasing)) { dir * (it / 6) }) togetherWith
                    (fadeOut(tween(PAGE_MS / 2, easing = PageEasing)) +
                        slideOutHorizontally(tween(PAGE_MS, easing = PageEasing)) { -dir * (it / 6) }))
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "onboardingCopy"
        ) { index ->
            OnboardingCopyText(onboardingPages[index], Modifier)
        }
    }
}

@Composable
private fun OnboardingCopyText(page: OnboardingPage, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = page.title,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            lineHeight = 33.sp
        )
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        Text(
            text = page.body,
            fontSize = 15.sp,
            color = AppTheme.TextMuted,
            lineHeight = 22.sp
        )
    }
}

/**
 * Il passo "Scegli l'AI". Due strade in schede selezionabili, ciascuna con i propri passaggi
 * spiegati in fila (niente gergo: "chiave" è spiegata come "una password gratuita che ti dà
 * Google"), e la possibilità di rimandare senza perdere nulla.
 *
 * Non parte alcun download da solo e nessuna scheda è preselezionata: il modello locale pesa da
 * qualche centinaio di MB a un paio di GB, e scaricarlo sulla rete mobile di nascosto sarebbe un
 * brutto primo incontro con l'app.
 */
@Composable
private fun AiSetupStep(
    setup: OnboardingAiSetup,
    onConfigured: () -> Unit,
    onBusyChange: (Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val localAvailable = setup.unavailableReason == null && setup.localModels.isNotEmpty()

    var choice by remember { mutableStateOf<AiChoice?>(null) }

    // Chiave Google
    var keyInput by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var keyOk by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }

    // Modello sul telefono
    var selectedModelId by remember { mutableStateOf(setup.localModels.firstOrNull()?.id) }
    var showAllModels by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var modelOk by remember { mutableStateOf(false) }
    // "Il modello è installato" è un file su disco, non stato di Compose: senza il contatore la
    // scheda mostrerebbe ancora "Scarica" a download finito.
    var modelsRevision by remember { mutableStateOf(0) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val busy = isTestingKey || isDownloading
    LaunchedEffect(busy) { onBusyChange(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(aiStepAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Sparkle(modifier = Modifier.size(22.dp), color = aiStepAccent)
        }
    }
    Spacer(modifier = Modifier.height(AppTheme.Space16))
    Text(
        text = "Scegli come AILA\nlegge le circolari",
        fontSize = 25.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.TextDark,
        lineHeight = 31.sp
    )
    Spacer(modifier = Modifier.height(AppTheme.Space8))
    Text(
        text = "Per riassumere le circolari e rispondere alle tue domande AILA usa un'AI. " +
            "Scegli una strada: potrai cambiarla quando vuoi da Impostazioni.",
        fontSize = 14.sp,
        color = AppTheme.TextMuted,
        lineHeight = 20.sp
    )
    Spacer(modifier = Modifier.height(AppTheme.Space16))

    // --- Strada 1: chiave Google ---------------------------------------------------------------
    AiChoiceCard(
        title = "Chiave Google gratuita",
        subtitle = "La più semplice da avviare. Serve la connessione a ogni circolare.",
        badge = "Consigliata per iniziare",
        selected = choice == AiChoice.GOOGLE,
        enabled = !busy,
        onClick = { choice = AiChoice.GOOGLE }
    ) {
        StepLine(number = "1", text = "Apri Google AI Studio ed entra con il tuo account Google.")
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        AilaSecondaryButton(
            text = "Apri Google AI Studio",
            onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }
        )
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        StepLine(
            number = "2",
            text = "Premi \"Create API key\" e copia il codice che compare: è gratuito, " +
                "senza carta di credito."
        )
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        StepLine(number = "3", text = "Torna qui e incolla il codice.")
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
                keyStatus = null
                keyOk = false
            },
            placeholder = { Text("AIzaSy…", fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false
            ),
            shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
            colors = ailaFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (keyStatus != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Text(
                text = keyStatus!!,
                fontSize = 12.sp,
                fontWeight = if (keyOk) FontWeight.Bold else FontWeight.Normal,
                color = if (keyOk) AppTheme.TintGreenInk else AppTheme.TintRedInk,
                lineHeight = 17.sp
            )
            if (!keyOk && keyInput.isNotBlank()) {
                // Senza rete la prova fallisce per forza: chi è offline a scuola deve poter
                // salvare la chiave lo stesso e verificarla dopo.
                Text(
                    text = "Salva comunque, la verifico più tardi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.PrimaryBlue,
                    modifier = Modifier
                        .clickable {
                            setup.saveApiKey(keyInput.trim())
                            keyOk = true
                            keyStatus = "✓ Chiave salvata. Non l'ho potuta verificare: puoi farlo da " +
                                "Impostazioni con \"Prova la chiave\"."
                            onConfigured()
                        }
                        .padding(vertical = AppTheme.Space8)
                )
            }
        }
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        AilaPrimaryButton(
            text = if (isTestingKey) "Verifico…" else "Verifica e salva",
            enabled = keyInput.isNotBlank() && !isTestingKey,
            fillMaxWidth = true,
            onClick = {
                val trimmed = keyInput.trim()
                isTestingKey = true
                keyStatus = null
                scope.launch {
                    val answer = setup.testApiKey(trimmed)
                    // testKey() racconta l'esito a parole: "Chiave valida…" è l'unico successo.
                    if (answer.startsWith("Chiave valida")) {
                        setup.saveApiKey(trimmed)
                        keyOk = true
                        keyStatus = "✓ Chiave verificata e salvata. AILA è pronta."
                        onConfigured()
                    } else {
                        keyOk = false
                        keyStatus = answer
                    }
                    isTestingKey = false
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(AppTheme.Space12))

    // --- Strada 2: modello sul telefono --------------------------------------------------------
    val model = setup.localModels.firstOrNull { it.id == selectedModelId }
    AiChoiceCard(
        title = "Modello sul telefono",
        subtitle = if (localAvailable) {
            "Funziona anche offline e nessun dato esce dal dispositivo."
        } else {
            setup.unavailableReason ?: "Non disponibile su questo dispositivo."
        },
        badge = if (localAvailable) "Privato e offline" else null,
        selected = choice == AiChoice.LOCAL && localAvailable,
        enabled = localAvailable && !busy,
        onClick = { choice = AiChoice.LOCAL }
    ) {
        // Legge modelsRevision perché è l'unico modo di rifare la composizione dopo il download.
        val installed = modelsRevision.let { model != null && setup.isModelInstalled(model) }
        val isSystemModel = model != null && model.approxSizeBytes == 0L
        val shown = if (showAllModels) setup.localModels else listOfNotNull(model)

        shown.forEach { candidate ->
            OnboardingModelRow(
                model = candidate,
                isSelected = candidate.id == model?.id,
                isInstalled = setup.isModelInstalled(candidate),
                isRecommended = candidate.id == setup.localModels.firstOrNull()?.id,
                fits = candidate.fitsComfortablyIn(setup.deviceRamMb),
                onClick = {
                    if (!isDownloading) {
                        selectedModelId = candidate.id
                        modelStatus = null
                        modelOk = false
                        showAllModels = false
                    }
                }
            )
            Spacer(modifier = Modifier.height(AppTheme.Space8))
        }

        if (setup.localModels.size > 1 && !isDownloading) {
            Text(
                text = if (showAllModels) "Chiudi l'elenco" else "Scegli un altro modello",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.PrimaryBlue,
                modifier = Modifier
                    .clickable { showAllModels = !showAllModels }
                    .padding(vertical = AppTheme.Space4)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space4))
        }

        if (isDownloading) {
            OnboardingProgressBar(downloadedBytes = downloadedBytes, totalBytes = totalBytes)
            Spacer(modifier = Modifier.height(AppTheme.Space8))
        }

        if (modelStatus != null) {
            Text(
                text = modelStatus!!,
                fontSize = 12.sp,
                fontWeight = if (modelOk) FontWeight.Bold else FontWeight.Normal,
                color = if (modelOk) AppTheme.TintGreenInk else AppTheme.TintRedInk,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(AppTheme.Space8))
        }

        if (model != null && !modelOk) {
            if (isDownloading) {
                AilaSecondaryButton(
                    text = "Annulla download",
                    onClick = {
                        downloadJob?.cancel()
                        setup.cancelDownload(model)
                        isDownloading = false
                        modelStatus = "Download annullato. Quello che era già arrivato resta, " +
                            "e riparte da lì se ci riprovi."
                    }
                )
            } else {
                AilaPrimaryButton(
                    text = when {
                        installed -> "Usa questo modello"
                        isSystemModel -> "Attiva"
                        else -> "Scarica (${model.readableSize})"
                    },
                    fillMaxWidth = true,
                    onClick = {
                        if (installed) {
                            setup.activateModel(model)
                            modelOk = true
                            modelStatus = "✓ ${model.displayName} è attivo. AILA è pronta."
                            onConfigured()
                        } else {
                            isDownloading = true
                            modelStatus = null
                            downloadedBytes = 0L
                            totalBytes = model.approxSizeBytes
                            downloadJob = scope.launch {
                                try {
                                    val result = setup.downloadModel(model) { done, total ->
                                        downloadedBytes = done
                                        totalBytes = total
                                    }
                                    when (result) {
                                        is ModelDownloadState.Installed -> {
                                            setup.activateModel(model)
                                            modelOk = true
                                            modelStatus = "✓ ${model.displayName} è pronto. Le circolari " +
                                                "verranno analizzate sul telefono, anche offline."
                                            onConfigured()
                                        }
                                        is ModelDownloadState.Failed -> modelStatus = result.reason
                                        else -> modelStatus = "Download non completato."
                                    }
                                } finally {
                                    isDownloading = false
                                    modelsRevision++
                                }
                            }
                        }
                    }
                )
            }
            if (!installed && !isSystemModel && !isDownloading) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Text(
                    text = "Meglio con il Wi-Fi. Resta su questa schermata finché il download non " +
                        "finisce: se si interrompe riparte da dove era arrivato, non da capo.",
                    fontSize = 11.sp,
                    color = AppTheme.TextFaint,
                    lineHeight = 15.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(AppTheme.Space16))
    Text(
        text = "Non vuoi scegliere adesso? Vai avanti: AILA funziona lo stesso, con analisi più " +
            "semplici, e puoi configurare l'AI in ogni momento da Impostazioni.",
        fontSize = 12.sp,
        color = AppTheme.TextFaint,
        lineHeight = 17.sp
    )
}

/**
 * Una scheda selezionabile del passo AI. Il contenuto si apre solo quando è scelta, così le due
 * strade si confrontano a colpo d'occhio invece di stare una sopra l'altra con tutti i passaggi.
 */
@Composable
private fun AiChoiceCard(
    title: String,
    subtitle: String,
    badge: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppTheme.CardCornerRadius)
    // Colori che sfumano invece di scattare, e altezza che cresce con `animateContentSize`: la
    // scheda scelta si apre e l'altra si richiude nello stesso istante, e anche le righe di esito
    // ("Chiave salvata", avanzamento) entrano allargando la scheda invece di spingere a scatti.
    // `animateContentSize` sta dopo sfondo e bordo e prima del padding, così anche loro seguono.
    val background by animateColorAsState(
        targetValue = if (selected) AppTheme.TintBlue else AppTheme.SurfaceWhite,
        animationSpec = tween(220, easing = PageEasing),
        label = "aiCardBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) AppTheme.PrimaryBlue else AppTheme.Hairline,
        animationSpec = tween(220, easing = PageEasing),
        label = "aiCardBorder"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .animateContentSize(tween(260, easing = PageEasing))
            .padding(AppTheme.Space16)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pallino da radio: dice "scegli una" senza bisogno di leggere.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) AppTheme.PrimaryBlue else AppTheme.Hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AppTheme.PrimaryBlue)
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled || selected) AppTheme.TextDark else AppTheme.TextFaint
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 17.sp
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintGreenInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(AppTheme.TintGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.height(AppTheme.Space16))
            content()
        }
    }
}

/** Una riga numerata delle istruzioni: cerchietto col numero e il testo accanto. */
@Composable
private fun StepLine(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(AppTheme.PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Text(
            text = text,
            fontSize = 13.sp,
            color = AppTheme.TextDark,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Riga di un modello nel passo AI: nome, peso, cosa sa fare e — quando serve — l'avviso che su
 * questo telefono potrebbe non partire. Stesse informazioni della scheda in Impostazioni, ma più
 * corte: qui si sceglie una volta e si va avanti.
 */
@Composable
private fun OnboardingModelRow(
    model: LocalAiModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    isRecommended: Boolean,
    fits: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) AppTheme.TintBlue else AppTheme.SurfaceWhite)
            .border(1.dp, if (isSelected) AppTheme.PrimaryBlue else AppTheme.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(AppTheme.Space12)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = model.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
            if (model.approxSizeBytes > 0L) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(text = model.readableSize, fontSize = 11.sp, color = AppTheme.TextMuted)
            }
            Spacer(modifier = Modifier.weight(1f))
            val tag = when {
                isInstalled -> "Installato"
                isRecommended -> "Consigliato"
                else -> null
            }
            if (tag != null) {
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isInstalled) AppTheme.TintGreenInk else AppTheme.TintSlateInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                        .background(if (isInstalled) AppTheme.TintGreen else AppTheme.TintSlate)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(
            text = model.description,
            fontSize = 11.sp,
            color = AppTheme.TextMuted,
            lineHeight = 15.sp
        )
        if (!fits) {
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            Text(
                text = "Ha bisogno di circa ${model.recommendedRamMb / 1000} GB di memoria: su " +
                    "questo telefono potrebbe non partire.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintRedInk,
                lineHeight = 15.sp
            )
        }
    }
}

/** Barra del download, nelle forme di AppTheme (come quella in Impostazioni). */
@Composable
private fun OnboardingProgressBar(downloadedBytes: Long, totalBytes: Long) {
    val target = if (totalBytes > 0) {
        (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    // I byte arrivano a blocchi: senza interpolazione la barra avanzerebbe a scatti.
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(300, easing = LinearEasing),
        label = "downloadFraction"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.TintSlate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppTheme.PrimaryBlue)
            )
        }
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(
            text = "${formatMegabytes(downloadedBytes)} di ${formatMegabytes(totalBytes)} " +
                "(${(target * 100).toInt()}%)",
            fontSize = 11.sp,
            color = AppTheme.TextFaint
        )
    }
}

/** "1,4 GB" / "820 MB". */
private fun formatMegabytes(bytes: Long): String {
    val mb = bytes / 1_000_000
    return if (mb >= 1000) {
        val tenthsOfGb = mb / 100
        "${tenthsOfGb / 10},${tenthsOfGb % 10} GB"
    } else {
        "$mb MB"
    }
}

/**
 * Il riquadro illustrativo. Tutto il movimento continuo sta qui: aloni che scorrono, anello di
 * puntini che ruota, icona che respira. Vive solo finché l'onboarding è a schermo, quindi non
 * consuma nulla dopo il primo avvio.
 *
 * Tre scelte contro gli scatti della versione precedente:
 *
 * - **Una sola istanza per tutto il tour.** Prima ogni pagina ne creava una nuova dentro un
 *   `Crossfade`: le animazioni infinite ripartivano da zero a ogni cambio (l'orbita "saltava") e
 *   per 300 ms c'erano due riquadri a metà opacità sovrapposti, che sul fondo chiaro lo
 *   schiarivano per un attimo. Ora il riquadro resta e ne sfumano i colori.
 * - **Il movimento si legge in fase di disegno.** Le animazioni infinite restano `State` e si
 *   leggono dentro `drawBehind`/`graphicsLayer`: a ogni frame si ridisegna, ma non si ricompone
 *   niente e non si rifà il layout. Prima `breath` cambiava la dimensione del disco (relayout a
 *   60 fps) e otto `Box` venivano ricomposti a ogni frame.
 * - **Posizioni in frazioni del riquadro** e non in pixel, così il moto ha la stessa ampiezza su
 *   qualunque densità di schermo.
 */
@Composable
private fun OnboardingIllustration(page: OnboardingPage) {
    val transition = rememberInfiniteTransition(label = "onboardingLoop")

    val orbit = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val breath = transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = PageEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = PageEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    // Cambio pagina: i colori del fondo scorrono dall'uno all'altro nello stesso tempo del testo.
    // Sono `State` non delegati apposta: si leggono solo nel disegno, vedi sopra.
    val colorSpec = tween<Color>(durationMillis = PAGE_MS + 120, easing = PageEasing)
    val gradient0 = animateColorAsState(page.gradient[0], colorSpec, label = "gradient0")
    val gradient1 = animateColorAsState(page.gradient[1], colorSpec, label = "gradient1")
    val gradient2 = animateColorAsState(page.gradient[2], colorSpec, label = "gradient2")
    val accent = animateColorAsState(page.accent, colorSpec, label = "accent")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(AppTheme.CardCornerRadius + 8.dp))
            .drawBehind {
                val w = size.width
                val h = size.height

                drawRect(
                    Brush.linearGradient(
                        colors = listOf(gradient0.value, gradient1.value, gradient2.value),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )
                // Due aloni che si spostano lentamente: danno profondità al fondo, che altrimenti
                // è una sfumatura ferma.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.value.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(w * (0.2f + drift.value * 0.5f), h * 0.4f),
                        radius = w * 0.75f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2AFFFFFF), Color(0x00FFFFFF)),
                        center = Offset(w * (0.95f - drift.value * 0.6f), h * 0.95f),
                        radius = w * 0.65f
                    )
                )

                // Anello di otto puntini in orbita attorno al disco centrale.
                val ringRadius = 76.dp.toPx()
                repeat(8) { i ->
                    val angle = (orbit.value + i * 45f) * PI.toFloat() / 180f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f + 0.45f * ((i % 3) / 2f)),
                        radius = (if (i % 2 == 0) 3.5.dp else 2.dp).toPx(),
                        center = Offset(
                            w / 2f + cos(angle) * ringRadius,
                            h / 2f + sin(angle) * ringRadius
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Disco centrale: respira con `graphicsLayer` (nessun relayout), così l'occhio ci torna
        // sopra senza che nulla intorno si sposti.
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = breath.value
                    scaleY = breath.value
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x3DFFFFFF), Color(0x14FFFFFF)))),
            contentAlignment = Alignment.Center
        ) {
            // L'icona cambia con scala e dissolvenza, non di colpo: è l'unica cosa che cambia
            // "forma" fra una pagina e l'altra, quindi è quella che l'occhio segue.
            AnimatedContent(
                targetState = page.iconKind,
                transitionSpec = {
                    (fadeIn(tween(PAGE_MS, delayMillis = 90, easing = PageEasing)) +
                        scaleIn(tween(PAGE_MS, delayMillis = 90, easing = PageEasing), initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(PAGE_MS / 2, easing = PageEasing)) +
                            scaleOut(tween(PAGE_MS / 2, easing = PageEasing), targetScale = 0.7f))
                },
                label = "onboardingIcon"
            ) { kind ->
                val iconModifier = Modifier.size(42.dp)
                when (kind) {
                    OnboardingIcon.DOCUMENT -> AppIcons.Document(modifier = iconModifier, color = Color.White)
                    OnboardingIcon.CALENDAR -> AppIcons.Calendar(modifier = iconModifier, color = Color.White)
                    OnboardingIcon.CHAT -> AppIcons.ChatBubble(modifier = iconModifier, color = Color.White)
                    OnboardingIcon.CHAIR -> AppIcons.Chair(modifier = iconModifier, color = Color.White)
                    OnboardingIcon.SPARKLE -> AppIcons.Sparkle(modifier = iconModifier, color = Color.White)
                }
            }
        }
    }
}
