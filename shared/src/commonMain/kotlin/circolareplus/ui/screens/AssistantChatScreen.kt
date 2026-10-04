package circolareplus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import circolareplus.design.AilaAssistantHalo
import circolareplus.design.AilaIconTile
import circolareplus.design.ailaPulse
import androidx.compose.ui.unit.dp
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSource
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaDuration
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaIconButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaAppear
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaGlassSurface
import circolareplus.design.ailaMorphShape
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaPressable
import circolareplus.design.AilaAssistantWave
import circolareplus.design.ailaBubbleEnter
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaRevealEnter
import circolareplus.design.ailaSelectionPop
import circolareplus.design.ailaSheetReveal
import circolareplus.design.ailaSpatialSpring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay

/**
 * La chat con l'assistente globale: una domanda in italiano, una risposta costruita su circolari,
 * calendario, bacheca, sondaggi e mappa posti.
 *
 * La schermata non sa niente di AI: riceve i messaggi e segnala quando l'utente ne manda uno.
 * Tutto il lavoro (raccolta dati, chiamata al modello, secondo giro sui PDF) sta in
 * [circolareplus.ai.assistant.AilaAssistant], chiamato da `MainAppShell` — cosi' la
 * conversazione sopravvive all'uscita dalla schermata e una risposta lunga non viene annullata
 * se si torna indietro un attimo.
 */
@Composable
fun AssistantChatScreen(
    messages: List<AssistantMessage>,
    isThinking: Boolean,
    conversations: List<AssistantConversation>,
    onSend: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearChat: () -> Unit,
    onOpenConversation: (AssistantConversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSource: (AssistantSource) -> Unit,
    /** Ragionamento del modello locale (modalita' thinking): piu' ponderato ma molto piu' lento. */
    thinkingEnabled: Boolean = false,
    onThinkingChange: (Boolean) -> Unit = {},
    /** `false` quando il modello locale scelto non ha un ragionamento (Phi): niente interruttore. */
    thinkingAvailable: Boolean = true
) {
    var draft by remember { mutableStateOf("") }
    var isHistoryOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Ogni messaggio nuovo (e l'indicatore "sto pensando") porta la lista in fondo: in una chat
    // la riga che conta e' sempre l'ultima.
    // Risposta appena arrivata: quando l'attesa finisce, l'ultimo messaggio dell'assistente entra
    // con l'animazione (vedi AssistantBubble). Deciso qui, nella stessa composizione in cui sparisce
    // l'indicatore, cosi' non c'e' un fotogramma col messaggio gia' fermo.
    val wasThinking = remember { arrayOf(isThinking) }
    val arrivalHolder = remember { arrayOf<String?>(null) }
    if (wasThinking[0] && !isThinking) {
        arrivalHolder[0] = messages.lastOrNull()
            ?.takeIf { it.author != AssistantAuthor.USER && !it.isError }?.id
    }
    wasThinking[0] = isThinking

    // Domanda appena mandata: e' l'unico messaggio nuovo rispetto alla composizione precedente ed
    // e' dell'utente. Aprendo una conversazione dallo storico i messaggi nuovi sono tanti insieme,
    // quindi nessuno entra con l'animazione: si vede la conversazione gia' al suo posto.
    val seenIds = remember { messages.mapTo(HashSet()) { it.id } }
    val sentHolder = remember { arrayOf<String?>(null) }
    val newMessages = messages.filter { it.id !in seenIds }
    if (newMessages.size == 1 && newMessages[0].author == AssistantAuthor.USER) {
        sentHolder[0] = newMessages[0].id
    }
    newMessages.forEach { seenIds.add(it.id) }

    LaunchedEffect(messages.size, isThinking) {
        // Il benvenuto occupa la lista solo quando non c'e' nient'altro, quindi quando c'e'
        // qualcosa da scorrere gli elementi sono esattamente i messaggi piu' l'indicatore.
        val itemCount = messages.size + if (isThinking) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaBackBar(
            title = "AILA Assistant",
            onBackClick = onBackClick,
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    // Lo storico resta raggiungibile anche a chat vuota: e' proprio quando non
                    // c'e' niente a schermo che si va a cercare la conversazione di ieri.
                    if (conversations.isNotEmpty()) {
                        AilaIconButton(contentDescription = "Cronologia chat", onClick = { isHistoryOpen = true }) { tint ->
                            AppIcons.History(modifier = Modifier.size(20.dp), color = tint)
                        }
                    }
                    if (messages.isNotEmpty()) {
                        AilaIconButton(contentDescription = "Nuova chat", onClick = onClearChat) { tint ->
                            AppIcons.NewChat(modifier = Modifier.size(20.dp), color = tint)
                        }
                    }
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(AppTheme.Space16),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            if (messages.isEmpty() && !isThinking) {
                item { AssistantWelcome(conversations = conversations, onOpenConversation = onOpenConversation) }
            }

            items(messages, key = { it.id }) { message ->
                when {
                    message.author == AssistantAuthor.USER -> UserBubble(
                        text = message.text,
                        animateSend = message.id == sentHolder[0],
                        onShown = { if (sentHolder[0] == message.id) sentHolder[0] = null }
                    )
                    message.isError -> AssistantErrorBubble(message.text)
                    else -> AssistantBubble(
                        message = message,
                        onOpenSource = onOpenSource,
                        // La risposta appena arrivata "nasce" dall'indicatore di attesa: l'onda
                        // si calma nell'icona di AILA Assistant e il messaggio compare.
                        animateArrival = message.id == arrivalHolder[0],
                        onArrived = { if (arrivalHolder[0] == message.id) arrivalHolder[0] = null }
                    )
                }
            }

            if (isThinking) {
                item { ThinkingBubble() }
            }
        }

        // Barra per scrivere. Glass: niente fascia piena ne' riga divisoria, solo una capsula di
        // vetro che galleggia sullo sfondo e il tondo blu per mandare, come Messaggi su iOS 26.
        // Material: nessun divisore (la separazione la danno i toni), campo "pillola" pieno e
        // pulsante di invio che si deforma alla pressione, come gli altri pulsanti Expressive.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space12)
                .padding(top = AppTheme.Space8, bottom = AppTheme.Space12)
        ) {
            if (thinkingAvailable) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppTheme.Space8)
                    .chatSurface(RoundedCornerShape(AppTheme.SmallElementRadius))
                    .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // La spiegazione cambia in dissolvenza e il riquadro segue la nuova altezza con
                // una molla, invece di scattare di una riga quando si accende l'interruttore.
                Column(modifier = Modifier.weight(1f).animateContentSize(ailaSpatialSpring())) {
                    Text(
                        text = "Modalità ragionamento",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.TextDark
                    )
                    Crossfade(
                        targetState = thinkingEnabled,
                        animationSpec = ailaNavigationSpring(),
                        label = "thinkingModeHint"
                    ) { enabled ->
                        Text(
                            text = if (enabled) {
                                "Attiva: risposte più ponderate, ma più lente. Vale per l'AI sul telefono."
                            } else {
                                "Spenta: risposte rapide. Vale per l'AI sul telefono."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            color = AppTheme.TextMuted
                        )
                    }
                }
                circolareplus.design.AilaSwitch(
                    checked = thinkingEnabled,
                    onCheckedChange = onThinkingChange
                )
            }

            val canSend = draft.trim().isNotEmpty() && !isThinking
            val send = {
                val question = draft.trim()
                draft = ""
                onSend(question)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // Capsula con l'anello dell'Assistant al focus: stessa della ricerca.
                circolareplus.design.AilaPillTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Chiedi qualsiasi cosa…",
                    rotatingPlaceholders = if (messages.isEmpty()) ExamplePlaceholders else emptyList(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                SendButton(enabled = canSend, onClick = send)
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Text(
                text = "Usa i dati di AILA per la scuola e le sue conoscenze per il resto. Può " +
                    "sbagliare: per le cose importanti apri la circolare.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextFaint,
                modifier = Modifier.padding(horizontal = AppTheme.Space8)
            )
        }
    }

    if (isHistoryOpen) {
        AssistantHistorySheet(
            conversations = conversations,
            canOpen = true,
            onPick = { conversation ->
                isHistoryOpen = false
                onOpenConversation(conversation)
            },
            onDelete = onDeleteConversation,
            onDismiss = { isHistoryOpen = false }
        )
    }
}

/**
 * Fondo delle superfici della chat (fumetti dell'assistente, domande suggerite, riquadro del
 * ragionamento): vetro in Liquid Glass, superficie tonale in Material. Prima erano superfici
 * piene bianche (o grigio scuro), che in Glass sembravano un pezzo di un'altra app.
 */
private fun Modifier.chatSurface(shape: Shape): Modifier =
    if (AppTheme.isGlass) ailaGlassSurface(shape) else clip(shape).background(AppTheme.CardSurface)

/**
 * Pulsante di invio. Glass: tondo blu con la freccia in su, come Messaggi di iOS; spento e' un
 * tondo di vetro. Material: primario pieno, tondo che alla pressione si squadra, freccia a destra.
 */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = AppTheme.isGlass
    val shape = if (glass) CircleShape else ailaMorphShape(interactionSource)
    // Il blu si accende in dissolvenza appena c'e' qualcosa da mandare (prima scattava al primo
    // carattere) e la freccia fa un piccolo "pop": il pulsante dice "ora puoi" senza scritte.
    val on by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = ailaNavigationSpring(),
        label = "sendButtonOn"
    )
    val fill = AppTheme.PrimaryGradient
    Box(
        modifier = Modifier
            .padding(bottom = AppTheme.Space4)
            .size(48.dp)
            // Sotto, il pulsante spento (vetro o pillola tonale); sopra, il blu con l'opacita'
            // che segue `on`.
            .then(
                if (glass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(AppTheme.TrackFill)
            )
            .clip(shape)
            .drawBehind { drawRect(fill, alpha = on) }
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .semantics { contentDescription = "Invia" },
        contentAlignment = Alignment.Center
    ) {
        AppIcons.ArrowUp(
            modifier = Modifier
                .size(22.dp)
                .ailaSelectionPop(enabled)
                .graphicsLayer { rotationZ = if (glass) 0f else 90f },
            color = lerp(AppTheme.TextFaint, Color.White, on)
        )
    }
}

/**
 * Lo storico delle conversazioni: titolo (la prima domanda), quando, quanti messaggi, e il
 * cestino per buttarne una.
 *
 * Le conversazioni arrivano dal dispositivo, non dal server — vedi
 * [circolareplus.data.local.LocalSettingsManager.listAssistantConversations].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantHistorySheet(
    conversations: List<AssistantConversation>,
    canOpen: Boolean,
    onPick: (AssistantConversation) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    circolareplus.design.AilaBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            Text(
                text = "Le tue conversazioni",
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.TextDark
            )
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            Text(
                text = "Restano su questo telefono: non passano dal server della classe.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.TextMuted
            )
            Spacer(modifier = Modifier.height(AppTheme.Space16))

            // Il foglio non deve crescere oltre lo schermo quando le conversazioni sono venti:
            // scorre da solo, con l'altezza limitata.
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Row(
                        modifier = Modifier
                            // Eliminando una conversazione le altre scorrono al loro posto invece
                            // di saltare, e quella eliminata svanisce.
                            .animateItem(
                                fadeInSpec = null,
                                placementSpec = ailaNavigationSpring(),
                                fadeOutSpec = ailaNavigationSpring()
                            )
                            .fillMaxWidth()
                            // Glass: le righe piene dei gruppi di iOS sul foglio (di vetro quasi
                            // invisibile non si leggevano). Material: il tono "container" sopra
                            // il foglio pieno (CardSurface in scuro e' uguale al foglio).
                            // Glass: righe di vetro come il resto (dietro l'app e' sfocata, vedi
                            // AilaSheetBackdrop). Material: il tono "container" sopra il foglio.
                            .then(
                                if (AppTheme.isGlass) Modifier.ailaGlassSurface(RoundedCornerShape(AppTheme.SmallElementRadius))
                                else Modifier.clip(RoundedCornerShape(AppTheme.SmallElementRadius)).background(AppTheme.TrackFill)
                            )
                            .then(
                                if (canOpen) {
                                    Modifier.ailaPressable(pressedScale = 0.98f) { onPick(conversation) }
                                } else {
                                    Modifier.alpha(0.5f)
                                }
                            )
                            // Margini piu' stretti a destra e in verticale: il cestino da 44dp ha gia'
                            // il suo spazio intorno all'icona, e la riga resta alta come prima.
                            .padding(start = AppTheme.Space12, end = AppTheme.Space4, top = AppTheme.Space8, bottom = AppTheme.Space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AilaAssistantMark(size = 18.dp)
                        Spacer(modifier = Modifier.width(AppTheme.Space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = AppTheme.TextDark,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space4))
                            Text(
                                text = "${conversation.messages.size} messaggi \u2022 " +
                                    relativeTimeLabel(conversation.updatedAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                color = AppTheme.TextFaint
                            )
                        }
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Box(
                            modifier = Modifier
                                // 44dp di tocco (prima 28): il cestino sta accanto alla riga che
                                // apre la conversazione, mancarlo voleva dire aprirla.
                                .size(44.dp)
                                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                .ailaPressable(pressedScale = 0.9f) { onDelete(conversation.id) }
                                // 8 e non 6: sta sulla griglia e allarga un poco l'area di tocco
                                // del cestino, che era piccola.
                                .padding(AppTheme.Space8)
                                .semantics { contentDescription = "Elimina conversazione" },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcons.Trash(modifier = Modifier.size(16.dp), color = AppTheme.TextFaint)
                        }
                    }
                }
            }
        }
    }
}

/** "adesso", "X min fa", "X h fa", "X g fa": come nello storico notifiche, senza librerie data/ora. */
private fun relativeTimeLabel(atMillis: Long): String {
    val diffMillis = (circolareplus.platform.currentTimeMillis() - atMillis).coerceAtLeast(0)
    val minutes = diffMillis / (60 * 1000)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "adesso"
        minutes < 60 -> "$minutes min fa"
        hours < 24 -> "$hours h fa"
        else -> "$days g fa"
    }
}

/**
 * Schermata vuota. Niente domande suggerite (erano frasi uguali per tutti, che nessuno toccava):
 * mostra cio' che e' vero per chi la apre — le sue ultime conversazioni, da riprendere — e da
 * dove l'Assistant prende le risposte. Gli esempi di domande stanno nel campo di testo, come
 * suggerimento che ruota, non come pulsanti.
 */
@Composable
private fun AssistantWelcome(
    conversations: List<AssistantConversation>,
    onOpenConversation: (AssistantConversation) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = AppTheme.Space12),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // L'unico elemento che si muove (cookie che ruota piano, alone che respira, barre che si
        // alzano): il resto della pagina e' gia' al suo posto, niente cascate.
        AilaAssistantHalo(size = 80.dp, intro = true)
        Text(
            text = "AILA Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = AppTheme.TextDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(
            text = "Chiedimi come lo chiederesti a un compagno: cerco io fra i dati della tua scuola.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppTheme.Space16)
        )

        val recent = conversations.sortedByDescending { it.updatedAtMillis }.take(3)
        if (recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppTheme.Space24))
            WelcomeLabel("RIPRENDI")
            recent.forEach { conversation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppTheme.Space8)
                        .chatSurface(RoundedCornerShape(AppTheme.CardCornerRadius))
                        .ailaPressable(pressedScale = 0.98f) { onOpenConversation(conversation) }
                        .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AilaAssistantMark(size = 20.dp)
                    Spacer(modifier = Modifier.width(AppTheme.Space12))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = AppTheme.TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = relativeTimeLabel(conversation.updatedAtMillis),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal,
                            color = AppTheme.TextFaint
                        )
                    }
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    AppIcons.ChevronRight(modifier = Modifier.size(16.dp), color = AppTheme.TextFaint)
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space16))
        WelcomeLabel("DA DOVE PRENDO LE RISPOSTE")
        // Inerti di proposito (niente freccia, niente pressione): dicono dove guarda l'Assistant,
        // non portano da nessuna parte.
        WelcomeSources.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space8),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
            ) {
                pair.forEach { (kind, label) ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                            .background(kind.tint())
                            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        kind.Icon(kind.ink(), 16.dp)
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = kind.ink(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Text(
            text = "Per tutto il resto risponde con le sue conoscenze.",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = AppTheme.TextFaint,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.TextFaint,
        modifier = Modifier.fillMaxWidth().padding(start = AppTheme.Space4, bottom = AppTheme.Space8)
    )
}

private val WelcomeSources = listOf(
    AssistantSourceKind.CIRCULAR to "Circolari",
    AssistantSourceKind.CALENDAR to "Calendario",
    AssistantSourceKind.BOARD to "Bacheca",
    AssistantSourceKind.POLL to "Sondaggi",
    AssistantSourceKind.SEAT_MAP to "Mappa posti",
    AssistantSourceKind.CLASS to "La tua classe"
)

/** Esempi di domande nel campo di testo, finche' la chat e' vuota. */
private val ExamplePlaceholders = listOf(
    "Quando è la prossima verifica?",
    "Cosa c'è da pagare questo mese?",
    "Riassumi l'ultima circolare",
    "Dove sono seduto in aula?",
    "Ci sono sondaggi ancora aperti?"
)

/** Angoli dei fumetti: tondi, con l'angolo verso chi parla appena accennato. */
private val BubbleRadius = 20.dp
private val BubbleTail get() = if (AppTheme.isGlass) 6.dp else 4.dp

@Composable
private fun UserBubble(text: String, animateSend: Boolean = false, onShown: () -> Unit = {}) {
    // Deciso una volta sola: se il fumetto esce dallo schermo e ci torna non deve rientrare.
    val sending = remember { animateSend }
    if (sending) LaunchedEffect(Unit) { onShown() }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                // La domanda appena mandata sale dal campo di testo e si allarga dall'angolo in
                // basso a destra, quello della "coda" del fumetto.
                .ailaBubbleEnter(enabled = sending, fromEnd = true)
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = BubbleRadius,
                        topEnd = BubbleRadius,
                        bottomStart = BubbleRadius,
                        bottomEnd = BubbleTail
                    )
                )
                // Glass: blu pieno come i fumetti di Messaggi. Material: "primary container",
                // tonale, come le chat di Android (il blu pieno e' riservato al pulsante di invio).
                .then(
                    if (AppTheme.isGlass) Modifier.background(AppTheme.PrimaryGradient)
                    else Modifier.background(AppTheme.TintBlue)
                )
                .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (AppTheme.isGlass) Color.White else AppTheme.TintBlueInk
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    message: AssistantMessage,
    onOpenSource: (AssistantSource) -> Unit,
    animateArrival: Boolean = false,
    onArrived: () -> Unit = {}
) {
    // Arrivo: l'icona parte come onda in movimento, la stessa dell'attesa (ThinkingBubble), e si
    // calma fino al marchio fermo mentre il fumetto con la risposta si apre sotto. Prima l'icona
    // sostituiva l'indicatore con uno scambio di forme; ora e' un solo segno che smette di parlare.
    val arriving = remember(message.id) { animateArrival }
    var revealed by remember(message.id) { mutableStateOf(!arriving) }
    if (arriving) {
        LaunchedEffect(message.id) {
            // Un attimo di onda ancora attiva: senza, l'indicatore spariva e la risposta compariva
            // nello stesso fotogramma e il passaggio non si vedeva.
            delay(120)
            revealed = true
            onArrived()
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AilaAssistantWave(
            active = !revealed,
            size = 26.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        androidx.compose.animation.AnimatedVisibility(
            visible = revealed,
            enter = ailaRevealEnter(),
            modifier = Modifier.weight(1f)
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .chatSurface(
                        RoundedCornerShape(
                            topStart = BubbleTail,
                            topEnd = BubbleRadius,
                            bottomStart = BubbleRadius,
                            bottomEnd = BubbleRadius
                        )
                    )
                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
            ) {
                Text(
                    text = formatAssistantText(message.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.TextDark
                )
            }

            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Row(
                    modifier = Modifier
                        // Le fonti salgono subito dopo il fumetto: prima la risposta, poi da dove
                        // viene. Solo all'arrivo, non riaprendo una conversazione.
                        .then(if (arriving) Modifier.ailaSheetReveal(4) else Modifier)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                ) {
                    message.sources.forEach { source ->
                        SourceChip(source = source, onClick = { onOpenSource(source) })
                    }
                }
            }

            message.modelLabel?.let { label ->
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = AppTheme.TextFaint,
                    modifier = if (arriving) Modifier.ailaSheetReveal(6) else Modifier
                )
            }
        }
        }
    }
}

@Composable
private fun AssistantErrorBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AppIcons.Warning(
            modifier = Modifier.size(20.dp).padding(top = 6.dp),
            color = AppTheme.TintRedInk
        )
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(BubbleRadius))
                    .background(AppTheme.TintRed)
                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
            ) {
                Column {
                    Text(
                        text = "Non sono riuscito a rispondere",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.TintRedInk
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space4))
                    // Il motivo vero, non una frase generica: quasi sempre e' una chiave AI
                    // mancante o una quota esaurita, cioe' qualcosa che l'utente puo' sistemare
                    // dalle Impostazioni — ma solo se gli si dice quale dei due.
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TintRedInk)
                }
            }
        }
    }
}

/**
 * Mentre il modello lavora: il marchio di AILA Assistant che "parla" (l'onda in movimento), al posto
 * dell'icona e senza fumetto. Quando arriva la risposta la stessa onda si calma nella sua icona (vedi
 * AssistantBubble). Prima erano tre puntini in Glass e la forma che cambia in Material: due segni
 * diversi per la stessa cosa, e nessuno dei due era l'Assistant.
 */
@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "AILA Assistant sta cercando" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaAssistantWave(active = true, size = 26.dp)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        // Una sola frase, onesta: non finge i passaggi (leggo le circolari, controllo il
        // calendario…) che il modello fa tutti insieme. Solo respira, come l'onda.
        Text(
            text = "Sto cercando in AILA…",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            color = AppTheme.TextMuted,
            modifier = Modifier.ailaPulse().clearAndSetSemantics { }
        )
    }
}

@Composable
private fun SourceChip(source: AssistantSource, onClick: () -> Unit) {
    val tint = source.kind.tint()
    val ink = source.kind.ink()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
            .background(tint)
            .ailaPressable(pressedScale = 0.95f) { onClick() }
            .padding(start = AppTheme.Space12, end = AppTheme.Space12, top = AppTheme.Space8, bottom = AppTheme.Space8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        source.kind.Icon(ink)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        // Il titolo lungo non esce piu' dallo schermo con il fondo tagliato: sta in un massimo
        // di larghezza e finisce con i puntini.
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        Spacer(modifier = Modifier.width(AppTheme.Space4))
        AppIcons.ChevronRight(modifier = Modifier.size(12.dp), color = ink)
    }
}

private fun AssistantSourceKind.tint(): Color = when (this) {
    AssistantSourceKind.CIRCULAR -> AppTheme.TintBlue
    AssistantSourceKind.CALENDAR -> AppTheme.TintAmber
    AssistantSourceKind.BOARD -> AppTheme.TintViolet
    AssistantSourceKind.POLL -> AppTheme.TintGreen
    AssistantSourceKind.SEAT_MAP -> AppTheme.TintSlate
    AssistantSourceKind.CLASS -> AppTheme.TintSlate
}

private fun AssistantSourceKind.ink(): Color = when (this) {
    AssistantSourceKind.CIRCULAR -> AppTheme.TintBlueInk
    AssistantSourceKind.CALENDAR -> AppTheme.TintAmberInk
    AssistantSourceKind.BOARD -> AppTheme.TintVioletInk
    AssistantSourceKind.POLL -> AppTheme.TintGreenInk
    AssistantSourceKind.SEAT_MAP -> AppTheme.TintSlateInk
    AssistantSourceKind.CLASS -> AppTheme.TintSlateInk
}

@Composable
private fun AssistantSourceKind.Icon(color: Color, iconSize: androidx.compose.ui.unit.Dp = 14.dp) {
    val size = Modifier.size(iconSize)
    when (this) {
        AssistantSourceKind.CIRCULAR -> AppIcons.Document(modifier = size, color = color)
        AssistantSourceKind.CALENDAR -> AppIcons.Calendar(modifier = size, color = color)
        AssistantSourceKind.BOARD -> AppIcons.ChatBubble(modifier = size, color = color)
        AssistantSourceKind.POLL -> AppIcons.Check(modifier = size, color = color)
        AssistantSourceKind.SEAT_MAP -> AppIcons.Chair(modifier = size, color = color)
        AssistantSourceKind.CLASS -> AppIcons.Profile(modifier = size, color = color)
    }
}

/**
 * Rende leggibile quel poco di markdown che i modelli infilano comunque nella risposta.
 *
 * Non e' un renderer markdown: gestisce solo il grassetto `**cosi'**` e trasforma i trattini a
 * inizio riga in punti elenco. Serve perche' senza, una risposta ben formattata dal modello
 * arriva a video piena di asterischi — che l'utente legge come un difetto dell'app, non come
 * una convenzione di formattazione.
 */
private fun formatAssistantText(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
    fun isBullet(line: String) = line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") }

    fun appendLine(line: String) {
        var index = 0
        var bold = false
        while (index < line.length) {
            val marker = line.indexOf("**", index)
            if (marker < 0) {
                appendStyled(line.substring(index), bold)
                break
            }
            appendStyled(line.substring(index, marker), bold)
            bold = !bold
            index = marker + 2
        }
    }

    lines.forEachIndexed { lineIndex, line ->
        if (isBullet(line)) {
            // Il punto elenco e' un paragrafo a se' con l'andata a capo "appesa": la seconda riga
            // parte sotto il testo e non sotto il pallino. Un paragrafo va a capo da solo, quindi
            // niente "\n" dopo (ne' prima di un altro paragrafo).
            val body = line.trimStart().substring(2)
            withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp))) {
                append("•  ")
                appendLine(body)
            }
        } else {
            appendLine(line)
            val next = lines.getOrNull(lineIndex + 1)
            if (next != null && !isBullet(next)) append("\n")
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyled(text: String, bold: Boolean) {
    if (text.isEmpty()) return
    if (bold) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    } else {
        append(text)
    }
}
