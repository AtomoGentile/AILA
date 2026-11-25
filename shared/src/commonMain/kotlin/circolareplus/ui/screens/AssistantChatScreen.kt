package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import circolareplus.ai.AiProvider
import circolareplus.ai.assistant.AssistantActionStatus
import circolareplus.ai.assistant.AssistantAgentPhase
import circolareplus.ai.assistant.AssistantAttachedFile
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSource
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaAssistantWave
import circolareplus.design.AilaDuration
import circolareplus.design.AilaResponseText
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaBubbleEnter
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaGlassSurface
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaPressable
import circolareplus.design.ailaPulse
import circolareplus.design.ailaRevealEnter
import circolareplus.design.ailaSheetReveal
import circolareplus.design.ailaSpatialSpring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La chat con AILA Assistant, ridisegnata con estetica editoriale minimale fedele al nuovo Claude:
 * foglio bianco arrotondato su guscio pietra caldo, cassetto laterale scorrevole (Fissate, Recenti,
 * Scorciatoie, footer Impostazioni/Cerca/Nuova chat), ricerca dedicata con match in grassetto,
 * modalità incognito, composer espandibile con selettore Modello + Ragionamento e barra inferiore
 * con pillola scorrevole e tooltip fluttuante sulle tab di AILA.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AssistantChatScreen(
    messages: List<AssistantMessage>,
    isThinking: Boolean,
    conversations: List<AssistantConversation>,
    onSend: (String) -> Unit,
    initialDraft: String = "",
    onBackClick: () -> Unit,
    onClearChat: () -> Unit,
    onOpenConversation: (AssistantConversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSource: (AssistantSource) -> Unit,
    /** Ragionamento del modello locale (modalita' thinking): piu' ponderato ma molto piu' lento. */
    thinkingEnabled: Boolean = false,
    onThinkingChange: (Boolean) -> Unit = {},
    /** `false` quando il modello locale scelto non ha un ragionamento (Phi): niente interruttore. */
    thinkingAvailable: Boolean = true,
    onConfirmAction: (String) -> Unit = {},
    onCancelAction: (String) -> Unit = {},
    agentPhase: AssistantAgentPhase? = null,
    onRecoverAction: (String) -> Unit = {},
    onAttachContent: ((circolareplus.platform.IncomingContent) -> Unit)? = null,
    onVoiceContent: ((circolareplus.platform.IncomingContent) -> Unit)? = null,
    onOpenFiles: () -> Unit = {},
    onStudyPlan: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    attachedFiles: List<AssistantAttachedFile> = emptyList(),
    onRemoveAttachedFile: (String) -> Unit = {},
    onOpenAttachedFile: (AssistantAttachedFile) -> Unit = {},
    // --- Estensioni interfaccia Claude beta ---
    activeConversationId: String? = null,
    isIncognito: Boolean = false,
    onIncognitoChange: (Boolean) -> Unit = {},
    onTogglePinConversation: (String) -> Unit = {},
    selectedAiProvider: String = AiProvider.AILA_CLOUD.id,
    onSelectAiProvider: (String) -> Unit = {},
    localModelLabel: String = "AI locale",
    effortLevel: Int = if (thinkingEnabled) 2 else 1,
    onEffortLevelChange: (Int) -> Unit = { onThinkingChange(it >= 2) },
    unreadTabs: Set<MainTab> = emptySet(),
    onSelectMainTab: ((MainTab) -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = onOpenAiSettings,
    onOpenBureaucracy: () -> Unit = {},
    onOpenGita: () -> Unit = {},
    finishedToastConversation: AssistantConversation? = null,
    onDismissFinishedToast: () -> Unit = {}
) {
    val palette = claudePalette(AppTheme.isDarkMode)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current

    var draft by remember { mutableStateOf(initialDraft) }
    LaunchedEffect(initialDraft) { if (initialDraft.isNotBlank()) draft = initialDraft }

    var isSearchOpen by remember { mutableStateOf(false) }
    var isModelSheetOpen by remember { mutableStateOf(false) }
    var attachmentsMenu by remember { mutableStateOf(false) }
    var fileSources by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }

    // Stato cassetto laterale scorrevole (0f = chiuso, 1f = aperto)
    val drawerProgress = remember { Animatable(0f) }
    fun setDrawerOpen(open: Boolean) {
        scope.launch {
            if (AppTheme.reduceMotion) {
                drawerProgress.snapTo(if (open) 1f else 0f)
            } else {
                drawerProgress.animateTo(
                    targetValue = if (open) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

    // Back interno: chiude fogli/ricerca/cassetto prima di uscire dalla schermata
    circolareplus.platform.PlatformBackHandler(
        enabled = isModelSheetOpen || isSearchOpen || drawerProgress.value > 0.05f
    ) {
        when {
            isModelSheetOpen -> isModelSheetOpen = false
            isSearchOpen -> isSearchOpen = false
            drawerProgress.value > 0.05f -> setDrawerOpen(false)
        }
    }

    val receiveFile = onAttachContent?.let { attach ->
        { file: circolareplus.platform.IncomingContent ->
            attachmentError = null
            attachmentsMenu = false
            attach(file)
        }
    }
    val picker = receiveFile?.let { circolareplus.platform.rememberContentPicker({ attachmentError = it }, it) }
    val photoPicker = receiveFile?.let {
        circolareplus.platform.rememberContentPicker(
            onError = { message -> attachmentsMenu = false; attachmentError = message },
            onPicked = it,
            photosOnly = true
        )
    }
    val cameraPicker = receiveFile?.let {
        circolareplus.platform.rememberCameraPhotoPicker(
            onError = { message -> attachmentsMenu = false; attachmentError = message },
            onPicked = it
        )
    }
    val recorder = if (onAttachContent != null) {
        circolareplus.platform.rememberVoiceRecorder({ recording = it }, { attachmentError = it }) { file ->
            attachmentsMenu = false
            (onVoiceContent ?: onAttachContent)(file)
        }
    } else null

    val listState = rememberLazyListState()
    val feedbackMap = remember { mutableStateMapOf<String, Int>() } // +1 = up, -1 = down
    var copiedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedMessageId) {
        if (copiedMessageId != null) {
            delay(1800)
            copiedMessageId = null
        }
    }

    val wasThinking = remember { arrayOf(isThinking) }
    val arrivalHolder = remember { arrayOf<String?>(null) }
    if (wasThinking[0] && !isThinking) {
        arrivalHolder[0] = messages.lastOrNull()
            ?.takeIf { it.author != AssistantAuthor.USER && !it.isError }?.id
    }
    wasThinking[0] = isThinking

    val seenIds = remember { messages.mapTo(HashSet()) { it.id } }
    val sentHolder = remember { arrayOf<String?>(null) }
    val newMessages = messages.filter { it.id !in seenIds }
    if (newMessages.size == 1 && newMessages[0].author == AssistantAuthor.USER) {
        sentHolder[0] = newMessages[0].id
    }
    newMessages.forEach { seenIds.add(it.id) }

    val showWelcome = messages.isEmpty() && !isThinking
    val lastShown = remember { arrayOf(emptyList<AssistantMessage>()) }
    if (messages.isNotEmpty()) lastShown[0] = messages.toList()

    LaunchedEffect(messages.size, isThinking, showWelcome) {
        val itemCount = messages.size + if (isThinking) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    val activeConversation = remember(conversations, activeConversationId) {
        activeConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
    }
    val headerTitle = remember(activeConversation, messages) {
        activeConversation?.let(::conversationDisplayTitle)
            ?: AssistantConversation.titleFrom(messages).takeIf { messages.isNotEmpty() }
    }
    val modelOptions = remember(localModelLabel) { claudeModelOptions(localModelLabel) }
    val currentModel = remember(modelOptions, selectedAiProvider) {
        modelOptions.firstOrNull { it.providerId == selectedAiProvider } ?: modelOptions.first()
    }
    val currentEffortLabel = ClaudeEffortLabels[effortLevel.coerceIn(0, ClaudeEffortLabels.lastIndex)]
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.shellBg)
    ) {
        val drawerWidth = (maxWidth * 0.82f).coerceIn(264.dp, 326.dp)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }

        // Strato sottostante: Cassetto laterale sinistro (rivelato quando il foglio scorre a destra)
        if (drawerProgress.value > 0.005f) {
            ClaudeLeftDrawer(
                palette = palette,
                drawerWidth = drawerWidth,
                conversations = conversations,
                activeConversationId = activeConversationId,
                onSelectConversation = { conv ->
                    setDrawerOpen(false)
                    onIncognitoChange(false)
                    onOpenConversation(conv)
                },
                onTogglePin = onTogglePinConversation,
                onOpenSearch = {
                    setDrawerOpen(false)
                    isSearchOpen = true
                },
                onNewChat = {
                    setDrawerOpen(false)
                    onIncognitoChange(false)
                    onClearChat()
                },
                onOpenSettings = {
                    setDrawerOpen(false)
                    onOpenSettings()
                },
                onOpenProfile = {
                    setDrawerOpen(false)
                    onOpenProfile()
                },
                onOpenFiles = {
                    setDrawerOpen(false)
                    onOpenFiles()
                },
                onStudyPlan = {
                    setDrawerOpen(false)
                    onIncognitoChange(false)
                    onStudyPlan()
                },
                onOpenBureaucracy = {
                    setDrawerOpen(false)
                    onOpenBureaucracy()
                },
                onOpenGita = {
                    setDrawerOpen(false)
                    onOpenGita()
                }
            )
        }

        // Strato principale: Shell Chat che scorre a destra con angoli arrotondati quando il cassetto si apre
        val progress = drawerProgress.value
        val sheetCorner = (28f * progress).dp
        val mainClipShape = RoundedCornerShape(topStart = sheetCorner, bottomStart = sheetCorner)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = drawerWidthPx * progress
                }
                .then(
                    if (progress > 0.01f) {
                        Modifier
                            .shadow(16.dp * progress, mainClipShape)
                            .clip(mainClipShape)
                            .border(0.75.dp, palette.hairline, mainClipShape)
                    } else Modifier
                )
                .background(palette.shellBg)
                .pointerInput(drawerWidthPx) {
                    var draggingDrawer = false
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            // Apre scorrendo dal bordo sinistro, oppure chiude da ovunque se aperto
                            draggingDrawer = drawerProgress.value > 0.05f || start.x <= 48.dp.toPx()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!draggingDrawer) return@detectHorizontalDragGestures
                            val next = (drawerProgress.value + dragAmount / drawerWidthPx).coerceIn(0f, 1f)
                            scope.launch { drawerProgress.snapTo(next) }
                        },
                        onDragEnd = {
                            if (draggingDrawer) {
                                setDrawerOpen(drawerProgress.value >= 0.35f)
                            }
                            draggingDrawer = false
                        },
                        onDragCancel = {
                            if (draggingDrawer) setDrawerOpen(drawerProgress.value >= 0.35f)
                            draggingDrawer = false
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header superiore sul guscio pietra
                ClaudeHeaderBar(
                    palette = palette,
                    hasMessages = messages.isNotEmpty() || isThinking,
                    conversationTitle = headerTitle,
                    isIncognito = isIncognito,
                    isPinned = activeConversation?.pinned == true,
                    onOpenDrawer = { setDrawerOpen(drawerProgress.value < 0.5f) },
                    onOpenSearch = { isSearchOpen = true },
                    onToggleIncognito = {
                        val next = !isIncognito
                        onIncognitoChange(next)
                        if (next && messages.isNotEmpty()) onClearChat()
                    },
                    onNewChat = {
                        onIncognitoChange(false)
                        onClearChat()
                    },
                    onTogglePin = {
                        activeConversationId?.let(onTogglePinConversation)
                    },
                    onDeleteCurrent = {
                        activeConversationId?.let(onDeleteConversation) ?: onClearChat()
                    }
                )

                // Foglio bianco arrotondato con contenuto, composer e barra inferiore
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(palette.sheetBg)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = showWelcome,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (targetState) {
                                    (fadeIn(ailaFadeSpec(AilaDuration.Standard, delayMillis = 30)) +
                                        scaleIn(ailaMoveSpec(AilaDuration.Standard), initialScale = 1.03f)) togetherWith
                                        (fadeOut(ailaFadeSpec(AilaDuration.Quick)) +
                                            scaleOut(ailaMoveSpec(AilaDuration.Quick), targetScale = 0.97f))
                                } else {
                                    fadeIn(ailaFadeSpec(AilaDuration.Quick)) togetherWith
                                        fadeOut(ailaFadeSpec(AilaDuration.Quick))
                                }
                            },
                            label = "claudeAssistantContent"
                        ) { welcome ->
                            if (welcome) {
                                ClaudeWelcomeCanvas(
                                    palette = palette,
                                    isIncognito = isIncognito
                                )
                            } else {
                                val displayed = if (messages.isEmpty()) lastShown[0] else messages
                                val lastAssistantIndex = displayed.indexOfLast { it.author != AssistantAuthor.USER && !it.isError }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    itemsIndexed(displayed, key = { _, msg -> msg.id }) { index, message ->
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = null,
                                                fadeOutSpec = ailaFadeSpec(AilaDuration.Quick)
                                            )
                                        ) {
                                            Column {
                                                when {
                                                    message.author == AssistantAuthor.USER -> ClaudeUserBubble(
                                                        text = message.text,
                                                        palette = palette,
                                                        animateSend = message.id == sentHolder[0],
                                                        onShown = { if (sentHolder[0] == message.id) sentHolder[0] = null }
                                                    )
                                                    message.isError -> ClaudeErrorBubble(message.text, palette)
                                                    else -> {
                                                        val previousPrompt = remember(displayed, index) {
                                                            displayed.take(index).lastOrNull { it.author == AssistantAuthor.USER }?.text
                                                        }
                                                        ClaudeAssistantResponse(
                                                            message = message,
                                                            palette = palette,
                                                            showDisclaimer = index == lastAssistantIndex && !isThinking,
                                                            feedback = feedbackMap[message.id] ?: 0,
                                                            copied = copiedMessageId == message.id,
                                                            onThumbUp = {
                                                                feedbackMap[message.id] = if (feedbackMap[message.id] == 1) 0 else 1
                                                            },
                                                            onThumbDown = {
                                                                feedbackMap[message.id] = if (feedbackMap[message.id] == -1) 0 else -1
                                                            },
                                                            onCopy = {
                                                                clipboard.setText(AnnotatedString(message.text))
                                                                copiedMessageId = message.id
                                                            },
                                                            onRegenerate = previousPrompt?.let { prompt ->
                                                                { if (!isThinking) onSend(prompt) }
                                                            },
                                                            onOpenSource = onOpenSource,
                                                            animateArrival = message.id == arrivalHolder[0],
                                                            onArrived = {
                                                                if (arrivalHolder[0] == message.id) arrivalHolder[0] = null
                                                            }
                                                        )
                                                    }
                                                }
                                                message.attachedFiles.forEach { file ->
                                                    Row(
                                                        modifier = Modifier
                                                            .padding(top = 6.dp)
                                                            .align(if (message.author == AssistantAuthor.USER) Alignment.End else Alignment.Start)
                                                            .widthIn(max = 290.dp)
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(palette.pillBg)
                                                            .border(0.75.dp, palette.hairline, RoundedCornerShape(16.dp))
                                                            .clickable { onOpenAttachedFile(file) }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        AppIcons.Document(Modifier.size(18.dp), palette.inkMuted)
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                text = file.name,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                                                                color = palette.ink
                                                            )
                                                            Text(
                                                                text = if (file.generated) file.processingLabel ?: "Testo elaborato da AILA" else "Apri l’originale",
                                                                style = TextStyle(fontSize = 11.5.sp),
                                                                color = palette.inkMuted
                                                            )
                                                        }
                                                        AppIcons.ChevronRight(Modifier.size(15.dp), palette.inkFaint)
                                                    }
                                                }
                                                if (message.actionPlan != null) {
                                                    AssistantActionPreview(
                                                        message = message,
                                                        isBusy = isThinking,
                                                        onConfirm = { onConfirmAction(message.id) },
                                                        onCancel = { onCancelAction(message.id) },
                                                        onRecover = { onRecoverAction(message.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isThinking) {
                                        item(key = "claude-thinking") {
                                            ClaudeThinkingRow(agentPhase, palette)
                                        }
                                    }
                                }
                            }
                        }

                        // Toast fluttuante "Risposta pronta" per conversazioni terminate in background
                        ClaudeFinishedToast(
                            conversation = finishedToastConversation,
                            palette = palette,
                            onOpen = { conv ->
                                onIncognitoChange(false)
                                onOpenConversation(conv)
                            },
                            onDismiss = onDismissFinishedToast,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 10.dp)
                        )
                    }

                    // Composer (o pannello registrazione vocale)
                    val send = {
                        val question = draft.trim().ifBlank { "Riassumi gli allegati di questo messaggio." }
                        draft = ""
                        onSend(question)
                    }
                    if (recording) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            VoiceRecordingPanel(
                                onStop = { recorder?.stop() },
                                onCancel = { recorder?.cancel() }
                            )
                        }
                    } else {
                        ClaudeComposer(
                            palette = palette,
                            draft = draft,
                            onDraftChange = { draft = it },
                            isThinking = isThinking,
                            isIncognito = isIncognito,
                            hasMessages = messages.isNotEmpty(),
                            rotatingPlaceholders = if (messages.isEmpty()) ExamplePlaceholders else emptyList(),
                            attachedFiles = attachedFiles,
                            onRemoveAttachedFile = onRemoveAttachedFile,
                            onOpenAttachedFile = onOpenAttachedFile,
                            attachmentError = attachmentError,
                            modelShortLabel = currentModel.shortLabel,
                            effortLabel = currentEffortLabel,
                            onOpenModelSheet = { isModelSheetOpen = true },
                            onOpenAttachments = if (onAttachContent != null) ({ attachmentsMenu = true }) else null,
                            onStartVoice = if (recorder != null) ({
                                attachmentError = null
                                recorder.start()
                            }) else null,
                            onSend = send,
                            onFocusedChange = { composerFocused = it }
                        )
                    }

                    // Barra inferiore stile Claude con tutte le tab di AILA + tooltip nero trascinabile
                    val showBottomBar = onSelectMainTab != null && !imeVisible && !recording
                    if (showBottomBar) {
                        ClaudeBottomNavBar(
                            palette = palette,
                            unreadTabs = unreadTabs,
                            onSelectMainTab = { tab ->
                                onSelectMainTab?.invoke(tab)
                            }
                        )
                    } else {
                        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(4.dp))
                    }
                }
            }

            // Velo cliccabile sul bordo destro del foglio quando il cassetto è aperto
            if (progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f * progress))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { setDrawerOpen(false) }
                        )
                        .semantics { contentDescription = "Chiudi menu chat" }
                )
            }
        }

        // Schermata di ricerca dedicata a tutto schermo (Video 1 00:06–00:11)
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn(ailaFadeSpec(AilaDuration.Quick)),
            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)),
            modifier = Modifier.fillMaxSize().zIndex(15f)
        ) {
            ClaudeSearchOverlay(
                palette = palette,
                conversations = conversations,
                onClose = { isSearchOpen = false },
                onSelectConversation = { conv ->
                    isSearchOpen = false
                    onIncognitoChange(false)
                    onOpenConversation(conv)
                }
            )
        }

        // Foglio Modello + Ragionamento (Video 1 00:16–00:23)
        AnimatedVisibility(
            visible = isModelSheetOpen,
            enter = fadeIn(ailaFadeSpec(AilaDuration.Quick)),
            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)),
            modifier = Modifier.fillMaxSize().zIndex(20f)
        ) {
            ClaudeModelAndEffortSheet(
                palette = palette,
                options = modelOptions,
                selectedProviderId = currentModel.providerId,
                effortLevel = effortLevel,
                onSelectProvider = onSelectAiProvider,
                onSelectEffort = { level ->
                    onEffortLevelChange(level)
                    onThinkingChange(level >= 2)
                },
                onOpenAiSettings = onOpenAiSettings,
                onDismiss = { isModelSheetOpen = false }
            )
        }
    }

    // Foglio allegati (mantiene Fotocamera / Foto / File su una sola riga e tutte le semantics esistenti)
    if (onAttachContent != null && attachmentsMenu) {
        circolareplus.design.AilaBottomSheet(onDismissRequest = { attachmentsMenu = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Aggiungi alla chat",
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = palette.ink
                    )
                    IconButton(
                        onClick = { attachmentsMenu = false },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Chiudi allegati" }
                    ) {
                        AppIcons.Close(Modifier.size(18.dp), palette.inkMuted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClaudeAttachmentTile(
                        title = "Fotocamera",
                        subtitle = "Scatta foto",
                        palette = palette,
                        enabled = !isThinking && !recording,
                        onClick = {
                            attachmentError = null
                            attachmentsMenu = false
                            cameraPicker?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        CameraGlyph(Modifier.size(22.dp), it)
                    }
                    ClaudeAttachmentTile(
                        title = "Foto",
                        subtitle = "Galleria",
                        palette = palette,
                        enabled = !isThinking && !recording,
                        onClick = {
                            attachmentError = null
                            photoPicker?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcons.Photo(Modifier.size(22.dp), it)
                    }
                    ClaudeAttachmentTile(
                        title = "File",
                        subtitle = "Documenti",
                        palette = palette,
                        enabled = !isThinking && !recording,
                        onClick = { fileSources = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcons.Document(Modifier.size(22.dp), it)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcons.Shield(Modifier.size(15.dp), palette.inkMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Le azioni richiedono la tua conferma.",
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.inkMuted
                    )
                    TextButton(onClick = { attachmentsMenu = false; onOpenAiSettings() }) {
                        Text("Impostazioni AI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = palette.ink)
                    }
                }
            }
        }
    }

    if (fileSources) {
        circolareplus.design.AilaAlertDialog(
            onDismissRequest = { fileSources = false },
            title = { Text("File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentMenuRow(
                        title = "Importa dal dispositivo",
                        subtitle = "Scegli un file dal dispositivo",
                        onClick = { fileSources = false; attachmentsMenu = false; picker?.invoke() }
                    ) { AppIcons.Document(Modifier.size(22.dp), palette.ink) }
                    AttachmentMenuRow(
                        title = "Copie già salvate",
                        subtitle = "Riapri un file conservato da AILA",
                        onClick = { fileSources = false; attachmentsMenu = false; onOpenFiles() }
                    ) { AppIcons.Download(Modifier.size(22.dp), palette.ink) }
                }
            },
            confirmButton = { TextButton(onClick = { fileSources = false }) { Text("Chiudi") } }
        )
    }
}

// =================================================================================================
// Schermata vuota centrata: marchio terracotta + titolo editoriale Serif (o fantasmino incognito)
// =================================================================================================

@Composable
private fun ClaudeWelcomeCanvas(
    palette: ClaudePalette,
    isIncognito: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isIncognito,
            transitionSpec = {
                (fadeIn(ailaFadeSpec(AilaDuration.Standard)) +
                    scaleIn(spring(dampingRatio = 0.72f, stiffness = 360f), initialScale = 0.85f)) togetherWith
                    (fadeOut(ailaFadeSpec(AilaDuration.Quick)) +
                        scaleOut(ailaFadeSpec(AilaDuration.Quick), targetScale = 0.9f))
            },
            label = "claudeWelcomeMode"
        ) { incognito ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (incognito) {
                    ClaudeGhostGlyph(
                        modifier = Modifier.size(44.dp),
                        color = palette.ink,
                        filled = true,
                        eyeCutoutColor = palette.sheetBg
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Chat in incognito",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.3).sp
                        ),
                        color = palette.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Questa conversazione non viene salvata nello storico del telefono.",
                        style = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                } else {
                    AilaAssistantWave(
                        active = false,
                        size = 42.dp,
                        intro = true
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Come posso aiutarti oggi?",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.3).sp
                        ),
                        color = palette.ink,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =================================================================================================
// Bolla utente (pillola azzurro-ghiaccio) e risposta assistente (testo libero + barra azioni)
// =================================================================================================

@Composable
private fun ClaudeUserBubble(
    text: String,
    palette: ClaudePalette,
    animateSend: Boolean = false,
    onShown: () -> Unit = {}
) {
    val sending = remember { animateSend }
    if (sending) LaunchedEffect(Unit) { onShown() }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .ailaBubbleEnter(enabled = sending, fromEnd = true)
                .widthIn(max = 292.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.userBubbleBg)
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = palette.ink
            )
        }
    }
}

@Composable
private fun ClaudeAssistantResponse(
    message: AssistantMessage,
    palette: ClaudePalette,
    showDisclaimer: Boolean,
    feedback: Int,
    copied: Boolean,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onOpenSource: (AssistantSource) -> Unit,
    animateArrival: Boolean = false,
    onArrived: () -> Unit = {}
) {
    val arriving = remember(message.id) { animateArrival }
    var revealed by remember(message.id) { mutableStateOf(!arriving || AppTheme.reduceMotion) }
    var textComplete by remember(message.id) { mutableStateOf(!arriving || AppTheme.reduceMotion) }
    val formattedText = remember(message.text) { formatAssistantText(message.text) }

    if (arriving) {
        LaunchedEffect(message.id) {
            if (!AppTheme.reduceMotion) delay(90)
            revealed = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = revealed,
            enter = ailaRevealEnter(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AilaResponseText(
                    text = formattedText,
                    responseId = message.id,
                    animate = arriving,
                    color = palette.ink,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 15.5.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { textComplete = true; onArrived() }
                )

                if (textComplete) {
                    if (message.sources.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .then(if (arriving) Modifier.ailaSheetReveal(3) else Modifier)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.sources.forEach { source ->
                                ClaudeSourcePill(source = source, palette = palette, onClick = { onOpenSource(source) })
                            }
                        }
                    }

                    // Riga azioni monocromatiche identica a Video 1 00:32–00:33 (pollice su/giù, copia, rigenera)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (arriving) Modifier.ailaSheetReveal(5) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ClaudeActionIconButton(
                            contentDescription = "Risposta utile",
                            onClick = onThumbUp
                        ) {
                            AppIcons.ThumbUp(
                                modifier = Modifier.size(16.dp),
                                color = if (feedback == 1) palette.ink else palette.inkFaint,
                                filled = feedback == 1
                            )
                        }
                        ClaudeActionIconButton(
                            contentDescription = "Risposta non utile",
                            onClick = onThumbDown
                        ) {
                            AppIcons.ThumbDown(
                                modifier = Modifier.size(16.dp),
                                color = if (feedback == -1) palette.ink else palette.inkFaint,
                                filled = feedback == -1
                            )
                        }
                        ClaudeActionIconButton(
                            contentDescription = if (copied) "Testo copiato" else "Copia risposta",
                            onClick = onCopy
                        ) {
                            if (copied) {
                                AppIcons.Check(Modifier.size(16.dp), color = palette.ink)
                            } else {
                                ClaudeCopyGlyph(Modifier.size(16.dp), color = palette.inkFaint)
                            }
                        }
                        if (onRegenerate != null) {
                            ClaudeActionIconButton(
                                contentDescription = "Rigenera risposta",
                                onClick = onRegenerate
                            ) {
                                AppIcons.Refresh(Modifier.size(16.dp), color = palette.inkFaint)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        message.modelLabel?.let { label ->
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 11.5.sp),
                                color = palette.inkFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showDisclaimer) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "AILA può commettere errori. Per le cose importanti apri la circolare.",
                            style = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
                            color = palette.inkFaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClaudeActionIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun ClaudeSourcePill(
    source: AssistantSource,
    palette: ClaudePalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(palette.pillBg)
            .border(0.75.dp, palette.hairline, RoundedCornerShape(100.dp))
            .ailaPressable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        source.kind.Icon(palette.inkMuted, 13.dp)
        Text(
            text = source.label,
            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        AppIcons.ChevronRight(Modifier.size(11.dp), color = palette.inkFaint)
    }
}

@Composable
private fun ClaudeThinkingRow(phase: AssistantAgentPhase?, palette: ClaudePalette) {
    val phaseLabel = when (phase) {
        AssistantAgentPhase.READING -> "Leggo il contesto degli eventi…"
        AssistantAgentPhase.PLANNING -> "Preparo l'anteprima…"
        AssistantAgentPhase.EXECUTING -> "Salvo e verifico nel calendario…"
        AssistantAgentPhase.WRITING -> "Elaboro la risposta…"
        AssistantAgentPhase.TRANSCRIBING -> "Trascrivo l’audio…"
        null -> "Sto cercando in AILA…"
    }
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .semantics { contentDescription = phaseLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AilaAssistantWave(active = true, size = 22.dp)
        Text(
            text = phaseLabel,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
            color = palette.inkMuted,
            modifier = Modifier.ailaPulse().clearAndSetSemantics { }
        )
    }
}

@Composable
private fun ClaudeErrorBubble(text: String, palette: ClaudePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.dangerBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcons.Warning(Modifier.size(18.dp).padding(top = 2.dp), color = palette.dangerInk)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Non sono riuscito a rispondere",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = palette.dangerInk
            )
            Text(
                text = text,
                style = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
                color = palette.dangerInk
            )
        }
    }
}

@Composable
private fun ClaudeAttachmentTile(
    title: String,
    subtitle: String,
    palette: ClaudePalette,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.pillBg)
            .border(0.75.dp, palette.hairline, shape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.sheetBg),
            contentAlignment = Alignment.Center
        ) {
            icon(palette.ink)
        }
        Text(title, style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = palette.ink)
        Text(subtitle, minLines = 2, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = palette.inkMuted)
    }
}

@Composable
private fun AttachmentMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        }
        AppIcons.ChevronRight(Modifier.size(18.dp), AppTheme.TextMuted)
    }
}

// =================================================================================================
// Componenti condivisi con altri file (ForumScreen, AcaContextOverlay) — firme mantenute invariate
// =================================================================================================

@Composable
internal fun CameraGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.085f).coerceAtLeast(1.5.dp.toPx())
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.28f),
            size = Size(w * 0.76f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.12f),
            style = Stroke(stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.16f),
            size = Size(w * 0.32f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = Stroke(stroke)
        )
        drawCircle(
            color = tint,
            radius = w * 0.16f,
            center = Offset(w * 0.50f, h * 0.55f),
            style = Stroke(stroke)
        )
    }
}

private fun Modifier.chatSurface(shape: Shape): Modifier =
    if (AppTheme.isGlass) ailaGlassSurface(shape) else clip(shape).background(AppTheme.CardSurface)

/** Reviewable, persisted plan. Only this explicit confirmation can start a calendar write. */
@Composable
internal fun AssistantActionPreview(
    message: AssistantMessage,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRecover: () -> Unit
) {
    val plan = message.actionPlan ?: return
    val palette = claudePalette(AppTheme.isDarkMode)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.Space12)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.pillBg)
            .border(0.75.dp, palette.hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Azioni sul calendario · ${plan.actions.size}",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = palette.ink
        )
        plan.actions.forEach { action ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${when (action.tool) { "calendar.delete" -> "Elimina"; "calendar.update" -> "Modifica"; else -> "Aggiungi" }}: ${action.title}",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.ink
                )
                if (action.tool == "calendar.update" && action.previousTitle != null) {
                    Text(
                        text = "Prima: ${action.previousTitle} · ${action.previousDate.orEmpty()} ${action.previousTime.orEmpty()}",
                        style = TextStyle(fontSize = 12.5.sp),
                        color = palette.inkMuted
                    )
                }
                Text(
                    text = "${circolareplus.util.formatItalianDateWithWeekday(action.date)} ${action.date.take(4)} · ${action.time ?: "Orario non specificato"}",
                    style = TextStyle(fontSize = 13.5.sp),
                    color = palette.ink
                )
                Text(
                    text = "${action.audienceLabel ?: if (action.visibility == "CLASS") "Tutta la classe" else "Solo tu"} · ${action.category.name.lowercase().replace('_', ' ')}" +
                        action.circularNumber?.let { " · Circolare n. $it" }.orEmpty(),
                    style = TextStyle(fontSize = 12.5.sp),
                    color = palette.inkMuted
                )
                action.notes?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = TextStyle(fontSize = 13.sp), color = palette.inkMuted)
                }
            }
        }
        when (message.actionStatus) {
            AssistantActionStatus.AWAITING_CONFIRMATION -> {
                Text(
                    text = if (plan.operationId == null) "Anteprima precedente: invia una nuova richiesta per confermare."
                    else "Nessuna modifica è stata ancora effettuata.",
                    style = TextStyle(fontSize = 12.5.sp),
                    color = palette.inkMuted
                )
                Button(
                    onClick = onConfirm,
                    enabled = !isBusy && plan.operationId != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.ink,
                        contentColor = palette.sheetBg
                    )
                ) {
                    Text("Conferma le azioni")
                }
                TextButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annulla", color = palette.inkMuted)
                }
            }
            else -> Text(
                text = when (message.actionStatus) {
                    AssistantActionStatus.RUNNING -> if (isBusy) "Inserimento in corso…" else "Operazione interrotta: verifica il calendario."
                    AssistantActionStatus.COMPLETED -> "Elaborazione terminata: leggi l'esito qui sotto."
                    AssistantActionStatus.PARTIAL -> "Operazione completata in parte: leggi gli esiti qui sotto."
                    AssistantActionStatus.EXPIRED -> "Anteprima scaduta: prepara una nuova richiesta."
                    AssistantActionStatus.ERROR -> "Operazione interrotta: verifica il calendario."
                    else -> "Anteprima annullata. Nessuna nuova azione da questa anteprima."
                },
                style = TextStyle(fontSize = 12.5.sp),
                color = palette.inkMuted
            )
        }
        if (message.actionStatus in setOf(AssistantActionStatus.ERROR, AssistantActionStatus.RUNNING) && !isBusy && plan.operationId != null) {
            OutlinedButton(
                onClick = onRecover,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Verifica l’esito sul server", color = palette.ink)
            }
        }
    }
}

/** Esempi di domande nel campo di testo, finché la chat è vuota. */
private val ExamplePlaceholders = listOf(
    "Quando è la prossima verifica?",
    "Cosa c'è da pagare questo mese?",
    "Riassumi l'ultima circolare",
    "Dove sono seduto in aula?",
    "Ci sono sondaggi ancora aperti?"
)

@Composable
private fun AssistantSourceKind.Icon(color: Color, iconSize: Dp = 14.dp) {
    val size = Modifier.size(iconSize)
    when (this) {
        AssistantSourceKind.CIRCULAR -> AppIcons.Document(modifier = size, color = color)
        AssistantSourceKind.CALENDAR -> AppIcons.Calendar(modifier = size, color = color)
        AssistantSourceKind.BOARD -> AppIcons.ChatBubble(modifier = size, color = color)
        AssistantSourceKind.POLL -> AppIcons.Check(modifier = size, color = color)
        AssistantSourceKind.SEAT_MAP -> AppIcons.Chair(modifier = size, color = color)
        AssistantSourceKind.CLASS -> AppIcons.Profile(modifier = size, color = color)
        AssistantSourceKind.GITA -> AppIcons.Document(modifier = size, color = color)
    }
}

private fun formatAssistantText(raw: String): AnnotatedString = circolareplus.design.formatAilaMarkdown(raw)
