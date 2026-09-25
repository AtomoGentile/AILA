package circolareplus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.ai.assistant.AilaAssistant
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSource
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaBackBar
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaAppear
import circolareplus.design.ailaFieldColors
import circolareplus.design.ailaPressable
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
                        BarIconButton(label = "Cronologia chat", onClick = { isHistoryOpen = true }) {
                            AppIcons.History(modifier = Modifier.size(20.dp), color = AppTheme.TextMuted)
                        }
                    }
                    if (messages.isNotEmpty()) {
                        BarIconButton(label = "Nuova chat", onClick = onClearChat) {
                            AppIcons.NewChat(modifier = Modifier.size(20.dp), color = AppTheme.TextMuted)
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
                item { AssistantWelcome(onPick = { onSend(it) }) }
            }

            items(messages, key = { it.id }) { message ->
                when {
                    message.author == AssistantAuthor.USER -> UserBubble(message.text)
                    message.isError -> AssistantErrorBubble(message.text)
                    else -> AssistantBubble(message = message, onOpenSource = onOpenSource)
                }
            }

            if (isThinking) {
                item { ThinkingBubble() }
            }
        }

        HorizontalDivider(color = AppTheme.Hairline)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.SurfaceWhite)
                .padding(AppTheme.Space12)
        ) {
            if (thinkingAvailable) Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modalità ragionamento",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    Text(
                        text = if (thinkingEnabled) {
                            "Attiva: risposte più ponderate, ma più lente. Vale per l'AI sul telefono."
                        } else {
                            "Spenta: risposte rapide. Vale per l'AI sul telefono."
                        },
                        fontSize = 10.sp,
                        color = AppTheme.TextMuted,
                        lineHeight = 14.sp
                    )
                }
                circolareplus.design.AilaSwitch(
                    checked = thinkingEnabled,
                    onCheckedChange = onThinkingChange
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Chiedi qualsiasi cosa…", fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(AppTheme.CardCornerRadius),
                    colors = ailaFieldColors()
                )
                Spacer(modifier = Modifier.width(AppTheme.Space8))

                val canSend = draft.trim().isNotEmpty() && !isThinking
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AppTheme.PrimaryBlue else AppTheme.TintSlate)
                        .then(
                            if (canSend) {
                                Modifier.ailaPressable(pressedScale = 0.9f) {
                                    val question = draft.trim()
                                    draft = ""
                                    onSend(question)
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcons.ChevronRight(
                        modifier = Modifier.size(20.dp),
                        color = if (canSend) Color.White else AppTheme.TextFaint
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Usa i dati di AILA per la scuola e le sue conoscenze per il resto. Può " +
                    "sbagliare: per le cose importanti apri la circolare.",
                fontSize = 10.sp,
                color = AppTheme.TextFaint
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
 * Pulsante a icona della barra in alto (cronologia, nuova chat). Quadrato come il pulsante
 * "indietro" di [AilaBackBar], cosi' la barra ha una sola forma; [label] va ai lettori di schermo.
 */
@Composable
private fun BarIconButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintSlate)
            .ailaPressable(pressedScale = 0.92f) { onClick() }
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        icon()
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            Text(
                text = "Le tue conversazioni",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Restano su questo telefono: non passano dal server della classe.",
                fontSize = 12.sp,
                color = AppTheme.TextMuted,
                lineHeight = 17.sp
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
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(AppTheme.TintSlate)
                            .then(
                                if (canOpen) {
                                    Modifier.ailaPressable(pressedScale = 0.98f) { onPick(conversation) }
                                } else {
                                    Modifier.alpha(0.5f)
                                }
                            )
                            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AilaAssistantMark(size = 18.dp)
                        Spacer(modifier = Modifier.width(AppTheme.Space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark,
                                maxLines = 2,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${conversation.messages.size} messaggi \u2022 " +
                                    relativeTimeLabel(conversation.updatedAtMillis),
                                fontSize = 11.sp,
                                color = AppTheme.TextFaint
                            )
                        }
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                .ailaPressable(pressedScale = 0.9f) { onDelete(conversation.id) }
                                .padding(6.dp),
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

/** Schermata vuota: spiega cosa sa fare e propone le prime domande. */
@Composable
private fun AssistantWelcome(onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = AppTheme.Space24)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AilaAssistantMark(size = 34.dp)
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Column {
                Text(
                    text = "AILA Assistant",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
                Text(
                    text = "Cerca per te in circolari, calendario, bacheca, sondaggi e mappa posti.",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space24))
        Text(
            text = "PROVA A CHIEDERE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextFaint
        )
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        AilaAssistant.SUGGESTED_QUESTIONS.forEachIndexed { index, question ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppTheme.Space8)
                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                    .background(AppTheme.SurfaceWhite)
                    .ailaPressable(pressedScale = 0.98f) { onPick(question) }
                    .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space12)
                    .ailaAppear(index),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcons.Sparkle(modifier = Modifier.size(15.dp), color = AppTheme.PrimaryBlue)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(text = question, fontSize = 13.sp, color = AppTheme.TextDark)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = AppTheme.CardCornerRadius,
                        topEnd = AppTheme.CardCornerRadius,
                        bottomStart = AppTheme.CardCornerRadius,
                        bottomEnd = 6.dp
                    )
                )
                .background(AppTheme.PrimaryBlue)
                .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
        ) {
            Text(text = text, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage, onOpenSource: (AssistantSource) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AilaAssistantMark(size = 26.dp, modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = AppTheme.CardCornerRadius,
                            bottomStart = AppTheme.CardCornerRadius,
                            bottomEnd = AppTheme.CardCornerRadius
                        )
                    )
                    .background(AppTheme.SurfaceWhite)
                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
            ) {
                Text(
                    text = formatAssistantText(message.text),
                    fontSize = 14.sp,
                    color = AppTheme.TextDark,
                    lineHeight = 21.sp
                )
            }

            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                ) {
                    message.sources.forEach { source ->
                        SourceChip(source = source, onClick = { onOpenSource(source) })
                    }
                }
            }

            message.modelLabel?.let { label ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = label, fontSize = 10.sp, color = AppTheme.TextFaint)
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
                    .clip(RoundedCornerShape(AppTheme.CardCornerRadius))
                    .background(AppTheme.TintRed)
                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
            ) {
                Column {
                    Text(
                        text = "Non sono riuscito a rispondere",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintRedInk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Il motivo vero, non una frase generica: quasi sempre e' una chiave AI
                    // mancante o una quota esaurita, cioe' qualcosa che l'utente puo' sistemare
                    // dalle Impostazioni — ma solo se gli si dice quale dei due.
                    Text(text = text, fontSize = 12.sp, color = AppTheme.TintRedInk, lineHeight = 17.sp)
                }
            }
        }
    }
}

/** Tre puntini che respirano mentre il modello lavora. */
@Composable
private fun ThinkingBubble() {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            step = (step + 1) % 3
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AilaAssistantMark(size = 26.dp)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppTheme.CardCornerRadius))
                .background(AppTheme.SurfaceWhite)
                .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val alpha by animateFloatAsState(
                    targetValue = if (index == step) 1f else 0.25f,
                    animationSpec = tween(400),
                    label = "assistantDot$index"
                )
                Box(
                    modifier = Modifier
                        .padding(end = if (index < 2) 5.dp else 0.dp)
                        .size(7.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(AppTheme.PrimaryBlue)
                )
            }
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Text(text = "Sto cercando in AILA…", fontSize = 12.sp, color = AppTheme.TextMuted)
        }
    }
}

@Composable
private fun SourceChip(source: AssistantSource, onClick: () -> Unit) {
    val tint = source.kind.tint()
    val ink = source.kind.ink()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(tint)
            .ailaPressable(pressedScale = 0.95f) { onClick() }
            .padding(horizontal = AppTheme.Space12, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        source.kind.Icon(ink)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = source.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1
        )
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
private fun AssistantSourceKind.Icon(color: Color) {
    val size = Modifier.size(13.dp)
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
    lines.forEachIndexed { lineIndex, rawLine ->
        var line = rawLine
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val indent = line.length - trimmed.length
            line = " ".repeat(indent) + "•" + trimmed.substring(1)
        }

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
        if (lineIndex < lines.lastIndex) append("\n")
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
