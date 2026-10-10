package circolareplus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import circolareplus.design.AilaAssistantTeal
import circolareplus.design.AilaAssistantViolet
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import circolareplus.design.ailaTopicSurface
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
import androidx.compose.animation.togetherWith
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

    val showWelcome = messages.isEmpty() && !isThinking
    // Mentre la conversazione svanisce (indietro alla schermata iniziale) i messaggi sono gia'
    // stati svuotati: la lista che esce deve poter mostrare ancora gli ultimi che c'erano.
    val lastShown = remember { arrayOf(emptyList<AssistantMessage>()) }
    if (messages.isNotEmpty()) lastShown[0] = messages.toList()

    LaunchedEffect(messages.size, isThinking, showWelcome) {
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
        AssistantHeader(
            isThinking = isThinking,
            hasMessages = messages.isNotEmpty(),
            statusLabel = messages.lastOrNull { it.author != AssistantAuthor.USER && !it.isError }?.modelLabel,
            onBackClick = onBackClick,
            // Lo storico resta raggiungibile anche a chat vuota: e' proprio quando non c'e'
            // niente a schermo che si va a cercare la conversazione di ieri.
            onHistoryClick = if (conversations.isNotEmpty()) ({ isHistoryOpen = true }) else null,
            onNewChatClick = if (messages.isNotEmpty()) onClearChat else null
        )

        // Schermata iniziale e conversazione sono due "pagine" della stessa schermata. Tornando
        // dalla conversazione alla schermata iniziale la conversazione si rimpicciolisce e svanisce
        // e quella iniziale arriva da un poco piu' grande (come un indietro fra pagine, ma breve);
        // dall'iniziale alla conversazione solo una dissolvenza rapida: lo sposta il fumetto che
        // sale dal campo di testo.
        androidx.compose.animation.AnimatedContent(
            targetState = showWelcome,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState) {
                    (androidx.compose.animation.fadeIn(ailaFadeSpec(AilaDuration.Standard, delayMillis = 40)) +
                        androidx.compose.animation.scaleIn(ailaMoveSpec(AilaDuration.Standard), initialScale = 1.05f)) togetherWith
                        (androidx.compose.animation.fadeOut(ailaFadeSpec(AilaDuration.Quick)) +
                            androidx.compose.animation.scaleOut(ailaMoveSpec(AilaDuration.Quick), targetScale = 0.95f))
                } else {
                    androidx.compose.animation.fadeIn(ailaFadeSpec(AilaDuration.Quick)) togetherWith
                        androidx.compose.animation.fadeOut(ailaFadeSpec(AilaDuration.Quick))
                }
            },
            label = "assistantContent"
        ) { welcome ->
        if (welcome) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppTheme.Space16)
            ) {
                AssistantWelcome(conversations = conversations, onOpenConversation = onOpenConversation)
            }
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(AppTheme.Space16),
            // Material: le risposte sono testo libero, serve piu' aria fra un turno e l'altro.
            verticalArrangement = Arrangement.spacedBy(if (AppTheme.isGlass) AppTheme.Space12 else AppTheme.Space20)
        ) {
            items(if (messages.isEmpty()) lastShown[0] else messages, key = { it.id }) { message ->
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = null,
                        fadeOutSpec = ailaFadeSpec(AilaDuration.Quick)
                    )
                ) {
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
            }

            if (isThinking) {
                item { ThinkingBubble() }
            }
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
            onNewChat = if (messages.isNotEmpty()) ({ isHistoryOpen = false; onClearChat() }) else null,
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
 * Lo storico delle conversazioni: raggruppate per quanto sono recenti, con la prima domanda come
 * titolo, un'anteprima dell'ultima risposta e il cestino per buttarne una. Da sei in su si puo'
 * cercare.
 *
 * Material: righe "a gruppo" come le liste di Material 3 Expressive (angoli grandi solo in cima e
 * in fondo al gruppo, quasi squadrati in mezzo). Glass: righe di vetro separate e tonde.
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
    onNewChat: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Scelta fatta nel foglio (apri una conversazione, nuova chat): si esegue a foglio chiuso, cosi'
    // il foglio rientra nel pulsante e poi cambia la chat, invece di sparire di colpo.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val trimmed = query.trim()
    val groups = remember(conversations, trimmed) {
        val now = circolareplus.platform.currentTimeMillis()
        conversations
            .filter { c ->
                trimmed.isEmpty() || c.title.contains(trimmed, ignoreCase = true) ||
                    c.messages.any { it.text.contains(trimmed, ignoreCase = true) }
            }
            .sortedByDescending { it.updatedAtMillis }
            .groupBy { c ->
                val hours = (now - c.updatedAtMillis).coerceAtLeast(0) / (60L * 60 * 1000)
                when {
                    hours < 24 -> "Ultime 24 ore"
                    hours < 24 * 7 -> "Questa settimana"
                    else -> "Prima"
                }
            }
            .toList()
    }

    circolareplus.design.AilaOriginSheet(
        originKey = "assistantHistory",
        onDismiss = {
            onDismiss()
            pending?.invoke()
        }
    ) { close ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Conversazioni",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.TextDark
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space4))
                    Text(
                        text = "${conversations.size} su questo telefono, non passano dal server della classe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.TextMuted
                    )
                }
                if (onNewChat != null) {
                    Spacer(modifier = Modifier.width(AppTheme.Space12))
                    circolareplus.design.AilaPrimaryButton(
                        text = "Nuova",
                        onClick = { pending = onNewChat; close() },
                        compact = true,
                        icon = { color -> AppIcons.NewChat(modifier = Modifier.size(16.dp), color = color) }
                    )
                }
            }
            if (conversations.size >= 6) {
                Spacer(modifier = Modifier.height(AppTheme.Space16))
                circolareplus.design.AilaPillTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Cerca nelle conversazioni",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint) }
                )
            }
            Spacer(modifier = Modifier.height(AppTheme.Space8))

            if (groups.isEmpty()) {
                Text(
                    text = "Nessuna conversazione contiene \"$trimmed\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.padding(vertical = AppTheme.Space24)
                )
            }

            // Il foglio non deve crescere oltre lo schermo quando le conversazioni sono venti:
            // scorre da solo, con l'altezza limitata.
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                groups.forEach { (label, group) ->
                    item(key = "header-$label") {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.TextFaint,
                            modifier = Modifier.padding(top = AppTheme.Space12, bottom = AppTheme.Space8, start = AppTheme.Space4)
                        )
                    }
                    itemsIndexed(group, key = { _, c -> c.id }) { index, conversation ->
                        ConversationRow(
                            conversation = conversation,
                            position = when {
                                group.size == 1 -> RowPosition.Single
                                index == 0 -> RowPosition.First
                                index == group.lastIndex -> RowPosition.Last
                                else -> RowPosition.Middle
                            },
                            canOpen = canOpen,
                            onPick = { picked -> pending = { onPick(picked) }; close() },
                            onDelete = onDelete,
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = ailaNavigationSpring(),
                                fadeOutSpec = ailaNavigationSpring()
                            )
                        )
                    }
                }
            }
        }
    }
}

private enum class RowPosition { Single, First, Middle, Last }

@Composable
private fun ConversationRow(
    conversation: AssistantConversation,
    position: RowPosition,
    canOpen: Boolean,
    onPick: (AssistantConversation) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = AppTheme.isGlass
    val big = AppTheme.CardCornerRadius
    val small = 6.dp
    val shape = if (glass) RoundedCornerShape(AppTheme.CardCornerRadius) else when (position) {
        RowPosition.Single -> RoundedCornerShape(big)
        RowPosition.First -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        RowPosition.Middle -> RoundedCornerShape(small)
        RowPosition.Last -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
    }
    val preview = remember(conversation) { conversationPreview(conversation) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Glass: righe di vetro separate (dietro l'app e' sfocata, vedi AilaSheetBackdrop).
            // Material: gruppo di righe tonali, separate da un filo di fondo.
            .padding(bottom = if (glass) AppTheme.Space8 else 2.dp)
            .then(
                if (glass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(AppTheme.TrackFill)
            )
            .then(
                if (canOpen) Modifier.ailaPressable(pressedScale = 0.98f) { onPick(conversation) }
                else Modifier.alpha(0.5f)
            )
            .padding(start = AppTheme.Space12, end = AppTheme.Space4, top = AppTheme.Space12, bottom = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        circolareplus.design.AilaIconTile(tint = AppTheme.TintBlue, size = 44.dp) {
            AilaAssistantMark(size = 22.dp)
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            Text(
                text = "${relativeTimeLabel(conversation.updatedAtMillis)} \u2022 ${conversation.messages.size} messaggi",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextFaint
            )
        }
        Box(
            modifier = Modifier
                // 44dp di tocco: il cestino sta accanto alla riga che apre la conversazione,
                // mancarlo voleva dire aprirla.
                .size(44.dp)
                .clip(CircleShape)
                .ailaPressable(pressedScale = 0.9f) { onDelete(conversation.id) }
                .padding(AppTheme.Space12)
                .semantics { contentDescription = "Elimina conversazione" },
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Trash(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint)
        }
    }
}

/** La prima riga dell'ultima risposta, senza i simboli del markdown: e' l'anteprima della riga. */
private fun conversationPreview(conversation: AssistantConversation): String {
    val answer = conversation.messages.lastOrNull { it.author != AssistantAuthor.USER && !it.isError } ?: return ""
    return answer.text.lines()
        .map { it.trim().removePrefix("- ").removePrefix("* ").replace("**", "") }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
        .take(90)
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
                            .ailaTopicSurface(RoundedCornerShape(AppTheme.ButtonCornerRadius), kind.tint(), kind.ink())
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
    AssistantSourceKind.CLASS to "La tua classe",
    AssistantSourceKind.GITA to "Gita"
)

/** Esempi di domande nel campo di testo, finche' la chat e' vuota. */
private val ExamplePlaceholders = listOf(
    "Quando è la prossima verifica?",
    "Cosa c'è da pagare questo mese?",
    "Riassumi l'ultima circolare",
    "Dove sono seduto in aula?",
    "Ci sono sondaggi ancora aperti?"
)

/**
 * Intestazione della chat: indietro, il marchio (che "parla" mentre l'Assistant lavora), nome e una
 * riga di stato — "Sto cercando…" in attesa, altrimenti il modello che ha risposto — e le azioni.
 * Il marchio compare con la prima domanda: a chat vuota c'e' gia' quello grande del benvenuto.
 */
@Composable
private fun AssistantHeader(
    isThinking: Boolean,
    hasMessages: Boolean,
    statusLabel: String?,
    onBackClick: () -> Unit,
    onHistoryClick: (() -> Unit)?,
    onNewChatClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.Space12, end = AppTheme.Space16, top = AppTheme.Space20, bottom = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaIconButton(contentDescription = "Indietro", onClick = onBackClick) { tint ->
            AppIcons.ChevronLeft(modifier = Modifier.size(19.dp), color = tint)
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        androidx.compose.animation.AnimatedVisibility(
            visible = hasMessages || isThinking,
            enter = androidx.compose.animation.fadeIn(ailaFadeSpec()) +
                androidx.compose.animation.expandHorizontally(ailaSpatialSpring())
        ) {
            Row {
                AilaAssistantWave(active = isThinking, size = 30.dp)
                Spacer(modifier = Modifier.width(AppTheme.Space12))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AILA Assistant",
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Crossfade(
                targetState = when {
                    isThinking -> "Sto cercando…"
                    hasMessages && statusLabel != null -> statusLabel
                    else -> "Circolari, calendario, bacheca e altro"
                },
                animationSpec = ailaFadeSpec(),
                label = "assistantStatus"
            ) { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = if (isThinking) AppTheme.PrimaryBlue else AppTheme.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            if (onHistoryClick != null) {
                AilaIconButton(contentDescription = "Cronologia chat", onClick = onHistoryClick, opensPage = true) { tint ->
                    AppIcons.History(modifier = Modifier.size(20.dp), color = tint)
                }
            }
            if (onNewChatClick != null) {
                AilaIconButton(contentDescription = "Nuova chat", onClick = onNewChatClick) { tint ->
                    AppIcons.NewChat(modifier = Modifier.size(20.dp), color = tint)
                }
            }
        }
    }
}

/** Angoli dei fumetti: tondi, con l'angolo verso chi parla appena accennato. */
private val BubbleRadius get() = if (AppTheme.isGlass) 24.dp else 28.dp
private val BubbleTail get() = if (AppTheme.isGlass) 6.dp else 6.dp

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
                // Glass: blu col gradiente dei pulsanti e un riflesso chiaro in alto (vetro
                // colorato). Material: "primary container", tonale; il blu pieno e' del pulsante
                // di invio.
                .then(
                    if (AppTheme.isGlass) {
                        Modifier
                            .background(AppTheme.PrimaryGradient)
                            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent))
                    } else Modifier.background(AppTheme.TintBlue)
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
    // calma fino al marchio fermo mentre la risposta si apre sotto. E' un solo segno che smette
    // di parlare, in entrambi gli stili.
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
    if (AppTheme.isGlass) {
        // Liquid Glass: marchio accanto e risposta in una lastra di vetro, col filo di luce del
        // marchio (viola in alto a sinistra, verde acqua in basso a destra) sul bordo.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AilaAssistantWave(active = !revealed, size = 26.dp, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            androidx.compose.animation.AnimatedVisibility(
                visible = revealed,
                enter = ailaRevealEnter(),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val shape = RoundedCornerShape(
                        topStart = BubbleTail,
                        topEnd = BubbleRadius,
                        bottomStart = BubbleRadius,
                        bottomEnd = BubbleRadius
                    )
                    Box(
                        modifier = Modifier
                            .chatSurface(shape)
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    0f to AilaAssistantViolet.copy(alpha = 0.55f),
                                    0.45f to Color.Transparent,
                                    0.75f to Color.Transparent,
                                    1f to AilaAssistantTeal.copy(alpha = 0.45f)
                                ),
                                shape
                            )
                            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
                    ) {
                        Text(
                            text = formatAssistantText(message.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.TextDark
                        )
                    }
                    AnswerExtras(message, arriving, onOpenSource)
                }
            }
        }
    } else {
        // Material Expressive: la risposta non sta in un fumetto ma e' testo libero a tutta
        // larghezza, come un documento (piu' leggibile quando e' lunga); solo la domanda
        // dell'utente e' in un contenitore tonale. Il marchio sta sopra, con il nome.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(top = AppTheme.Space4), verticalAlignment = Alignment.CenterVertically) {
                AilaAssistantWave(active = !revealed, size = 22.dp)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "AILA Assistant",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.TextMuted
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = revealed,
                enter = ailaRevealEnter(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = AppTheme.Space8)) {
                    Text(
                        text = formatAssistantText(message.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.TextDark,
                        modifier = Modifier.padding(horizontal = AppTheme.Space4)
                    )
                    AnswerExtras(message, arriving, onOpenSource)
                }
            }
        }
    }
}

/** Sotto la risposta: da dove viene (le fonti) e quale modello l'ha scritta. */
@Composable
private fun AnswerExtras(
    message: AssistantMessage,
    arriving: Boolean,
    onOpenSource: (AssistantSource) -> Unit
) {
    if (message.sources.isNotEmpty()) {
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        Row(
            modifier = Modifier
                // Le fonti salgono subito dopo la risposta: prima la risposta, poi da dove viene.
                // Solo all'arrivo, non riaprendo una conversazione.
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
        Row(
            modifier = (if (arriving) Modifier.ailaSheetReveal(6) else Modifier)
                .padding(horizontal = AppTheme.Space4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcons.Sparkle(modifier = Modifier.size(11.dp), color = AppTheme.TextFaint)
            Spacer(modifier = Modifier.width(AppTheme.Space4))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
 * Mentre il modello lavora: il marchio di AILA Assistant che "parla" (l'onda in movimento). Quando
 * arriva la risposta la stessa onda si calma nella sua icona (vedi AssistantBubble), alla stessa
 * posizione: per questo qui la riga e' costruita come quella dell'intestazione della risposta.
 */
@Composable
private fun ThinkingBubble() {
    val glass = AppTheme.isGlass
    Row(
        modifier = Modifier
            .padding(top = if (glass) 4.dp else AppTheme.Space4)
            .semantics { contentDescription = "AILA Assistant sta cercando" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaAssistantWave(active = true, size = if (glass) 26.dp else 22.dp)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        // Una sola frase, onesta: non finge i passaggi (leggo le circolari, controllo il
        // calendario…) che il modello fa tutti insieme. Solo respira, come l'onda. Glass: in una
        // capsula di vetro, come un avviso di iOS; Material: solo testo accanto all'onda.
        Text(
            text = "Sto cercando in AILA…",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            color = AppTheme.TextMuted,
            modifier = Modifier
                .then(
                    if (glass) {
                        Modifier
                            .ailaGlassSurface(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space4)
                    } else Modifier
                )
                .ailaPulse()
                .clearAndSetSemantics { }
        )
    }
}

@Composable
private fun SourceChip(source: AssistantSource, onClick: () -> Unit) {
    val tint = source.kind.tint()
    val ink = source.kind.ink()
    Row(
        modifier = Modifier
            .ailaTopicSurface(RoundedCornerShape(AppTheme.ButtonCornerRadius), tint, ink)
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
