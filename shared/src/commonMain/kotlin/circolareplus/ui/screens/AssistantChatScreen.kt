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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
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
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import circolareplus.ai.AssistantProviderModel
import circolareplus.ai.assistant.AssistantActionStatus
import circolareplus.ai.assistant.AssistantAgentPhase
import circolareplus.ai.assistant.AssistantAttachedFile
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSource
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.design.AilaAssistantWave
import circolareplus.design.AilaDuration
import circolareplus.design.AilaLogoTile
import circolareplus.design.AilaResponseText
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaBubbleEnter
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaPressable
import circolareplus.design.ailaPulse
import circolareplus.design.ailaRevealEnter
import circolareplus.design.ailaSheetReveal
import circolareplus.design.ailaTopicSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AILA Assistant in stile Neural Expressive / Gemini: neri profondi OLED in tema scuro con
 * bagliore radiale del brand, colori AILA vivi, tasti grandi (48dp), sidebar laterale pulita
 * (Nuova chat, Fissate, Tutte, Ricerca, Impostazioni AI) e selettore Modello/Provider +
 * Ragionamento in 1 tap.
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
    activeConversationId: String? = null,
    isIncognito: Boolean = false,
    onIncognitoChange: (Boolean) -> Unit = {},
    onTogglePinConversation: (String) -> Unit = {},
    selectedAiProvider: String = AiProvider.AILA_CLOUD.id,
    onSelectAiProvider: (String) -> Unit = {},
    localModelLabel: String = "AI locale",
    hasGoogleKey: Boolean = true,
    hasCompatibleConfig: Boolean = false,
    compatibleProviderName: String = "API personale",
    showLocalAi: Boolean = true,
    localModelInstalled: Boolean = true,
    selectedModelId: String = "",
    selectedReasoning: String = "",
    binaryThinkingAvailable: Boolean = false,
    providerConfigRevision: Int = 0,
    loadModels: (suspend (AiProvider) -> List<AssistantProviderModel>)? = null,
    onSelectModel: (String) -> Unit = {},
    onSelectReasoning: (String) -> Unit = {},
    userInitials: String = "AI",
    userDisplayName: String = "",
    finishedToastConversation: AssistantConversation? = null,
    onDismissFinishedToast: () -> Unit = {}
) {
    val palette = assistantSurfacePalette()
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

    val drawerProgress = remember { Animatable(0f) }
    fun setDrawerOpen(open: Boolean) {
        scope.launch {
            if (AppTheme.reduceMotion) {
                drawerProgress.snapTo(if (open) 1f else 0f)
            } else {
                drawerProgress.animateTo(
                    targetValue = if (open) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                )
            }
        }
    }

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
    val feedbackMap = remember { mutableStateMapOf<String, Int>() }
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
    val modelOptions = assistantModelOptions(
        localModelLabel, hasGoogleKey, hasCompatibleConfig, compatibleProviderName, showLocalAi, localModelInstalled
    )
    val provider = AiProvider.fromId(selectedAiProvider)
    val google = provider == AiProvider.GOOGLE_AI_STUDIO
    val external = google || provider == AiProvider.COMPATIBLE_API
    val available = when (provider) {
        AiProvider.GOOGLE_AI_STUDIO -> hasGoogleKey
        AiProvider.COMPATIBLE_API -> hasCompatibleConfig
        AiProvider.ON_DEVICE -> showLocalAi
        AiProvider.AILA_CLOUD -> true
    }
    var modelRetry by remember { mutableStateOf(0) }
    // Keep the resolved names and capabilities when the menu closes or the model changes.
    val catalog = androidx.compose.runtime.key(provider, providerConfigRevision, modelRetry) {
        produceState<Result<List<AssistantProviderModel>>?>(null, isModelSheetOpen) {
            if (isModelSheetOpen && value == null && available && loadModels != null && provider != AiProvider.AILA_CLOUD) {
                value = try {
                    Result.success(loadModels(provider))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.value
    }
    val catalogModels = catalog?.getOrNull().orEmpty()
    val activeModel = catalogModels.firstOrNull { it.id == selectedModelId }
    val modelLabel = activeModel?.name ?: when (provider) {
        AiProvider.AILA_CLOUD -> "Gemma 4 26B A4B"
        AiProvider.ON_DEVICE -> localModelLabel
        AiProvider.GOOGLE_AI_STUDIO -> selectedModelId.ifBlank { "Automatico" }
        AiProvider.COMPATIBLE_API -> selectedModelId.ifBlank { "Seleziona un modello" }
    }
    val headerModelLabel = if (google && selectedModelId.isBlank()) "Gemini · Auto" else modelLabel
    val supportedReasoning = if (external) activeModel?.reasoning.orEmpty()
        else if (binaryThinkingAvailable) listOf("off", "on") else emptyList()
    val reasoningOptions = if (external && supportedReasoning.isNotEmpty()) listOf("") + supportedReasoning
        else supportedReasoning
    val visibleReasoning = selectedReasoning.takeIf { it in reasoningOptions } ?: reasoningOptions.firstOrNull()
    val modelStatus = when {
        !available && external -> "Aggiungi una chiave nelle Impostazioni AI."
        catalog?.isFailure == true -> catalog.exceptionOrNull()?.message ?: "Catalogo non disponibile"
        catalog?.isSuccess == true && external && catalogModels.isEmpty() -> "Nessun modello disponibile"
        catalog == null && external && loadModels != null -> "Caricamento modelli…"
        else -> null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasBg)
    ) {
        val drawerWidth = (maxWidth * 0.82f).coerceIn(272.dp, 336.dp)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val progress = drawerProgress.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerWidthPx, isSearchOpen, isModelSheetOpen) {
                    if (isSearchOpen || isModelSheetOpen) return@pointerInput
                    var activeDrag = false
                    var lastVelocityDir = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            activeDrag = true
                            lastVelocityDir = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!activeDrag) return@detectHorizontalDragGestures
                            // Se il cassetto è chiuso e l'utente trascina verso sinistra, non fare nulla
                            if (drawerProgress.value <= 0.001f && dragAmount < 0f) return@detectHorizontalDragGestures
                            lastVelocityDir = dragAmount
                            val next = (drawerProgress.value + dragAmount / drawerWidthPx).coerceIn(0f, 1f)
                            scope.launch { drawerProgress.snapTo(next) }
                        },
                        onDragEnd = {
                            if (activeDrag) {
                                val shouldOpen = when {
                                    lastVelocityDir > 6f -> true
                                    lastVelocityDir < -6f -> false
                                    else -> drawerProgress.value >= 0.30f
                                }
                                setDrawerOpen(shouldOpen)
                            }
                            activeDrag = false
                        },
                        onDragCancel = {
                            if (activeDrag) setDrawerOpen(drawerProgress.value >= 0.30f)
                            activeDrag = false
                        }
                    )
                }
        ) {
        // Schermata principale della chat: resta SEMPRE ferma immobile mentre il pannello scorre sopra.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .assistantNeuralBackdrop(palette, prominent = showWelcome)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AssistantHeaderBar(
                    palette = palette,
                    hasMessages = messages.isNotEmpty() || isThinking,
                    isThinking = isThinking,
                    modelShortLabel = headerModelLabel,
                    effortLabel = (visibleReasoning ?: selectedReasoning.takeIf { external && it.isNotBlank() })
                        ?.let(::assistantReasoningLabel),
                    isModelPickerOpen = isModelSheetOpen,
                    isIncognito = isIncognito,
                    isPinned = activeConversation?.pinned == true,
                    onOpenDrawer = { setDrawerOpen(drawerProgress.value < 0.5f) },
                    onToggleModelPicker = { isModelSheetOpen = !isModelSheetOpen },
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
                    onTogglePin = { activeConversationId?.let(onTogglePinConversation) },
                    onDeleteCurrent = { activeConversationId?.let(onDeleteConversation) ?: onClearChat() }
                )

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
                        label = "assistantMainContent"
                    ) { welcome ->
                        if (welcome) {
                            AssistantWelcomeCanvas(
                                palette = palette,
                                isIncognito = isIncognito
                            )
                        } else {
                            val displayed = if (messages.isEmpty()) lastShown[0] else messages
                            val lastAssistantIndex = displayed.indexOfLast { it.author != AssistantAuthor.USER && !it.isError }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                                                message.author == AssistantAuthor.USER -> AssistantUserBubble(
                                                    text = message.text,
                                                    palette = palette,
                                                    animateSend = message.id == sentHolder[0],
                                                    onShown = { if (sentHolder[0] == message.id) sentHolder[0] = null }
                                                )
                                                message.isError -> AssistantErrorCard(message.text, palette)
                                                else -> {
                                                    val previousPrompt = remember(displayed, index) {
                                                        displayed.take(index).lastOrNull { it.author == AssistantAuthor.USER }?.text
                                                    }
                                                    AssistantResponseBlock(
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
                                                val isAudio = file.service.equals("AUDIO", ignoreCase = true) ||
                                                    file.name.lowercase().let { n ->
                                                        n.endsWith(".m4a") || n.endsWith(".mp3") || n.endsWith(".wav") ||
                                                            n.endsWith(".ogg") || n.endsWith(".webm")
                                                    }
                                                if (isAudio) {
                                                    AssistantVoiceBubble(
                                                        file = file,
                                                        isUser = message.author == AssistantAuthor.USER,
                                                        palette = palette,
                                                        onOpenDetails = { onOpenAttachedFile(file) },
                                                        modifier = Modifier
                                                            .padding(top = 8.dp)
                                                            .align(if (message.author == AssistantAuthor.USER) Alignment.End else Alignment.Start)
                                                    )
                                                } else {
                                                    Row(
                                                        modifier = Modifier
                                                            .padding(top = 8.dp)
                                                            .align(if (message.author == AssistantAuthor.USER) Alignment.End else Alignment.Start)
                                                            .widthIn(max = 296.dp)
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .background(palette.elevatedBg)
                                                            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                                                            .clickable { onOpenAttachedFile(file) }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Box(
                                                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.TintViolet),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            AppIcons.Document(Modifier.size(18.dp), AppTheme.TintVioletInk)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                text = file.name,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                                                                color = palette.ink
                                                            )
                                                            Text(
                                                                text = if (file.generated) file.processingLabel ?: "Testo elaborato da AILA" else "Apri l’originale",
                                                                style = TextStyle(fontSize = 11.5.sp),
                                                                color = palette.inkMuted
                                                            )
                                                        }
                                                        AppIcons.ChevronRight(Modifier.size(16.dp), palette.inkFaint)
                                                    }
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
                                    item(key = "assistant-thinking") {
                                        AssistantThinkingRow(agentPhase, palette)
                                    }
                                }
                            }
                        }
                    }

                    AssistantFinishedToast(
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

                val send = {
                    val question = draft.trim().ifBlank { "Riassumi gli allegati di questo messaggio." }
                    draft = ""
                    onSend(question)
                }
                AnimatedContent(
                    targetState = recording,
                    transitionSpec = {
                        (scaleIn(ailaExpressiveBounceSpring(damping = 0.68f, stiffness = 440f), initialScale = 0.94f) +
                            fadeIn(tween(150))) togetherWith
                            (scaleOut(tween(130), targetScale = 0.96f) + fadeOut(tween(110)))
                    },
                    label = "assistantVoiceBarSwitch"
                ) { isRec ->
                    if (isRec) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                                .padding(horizontal = 14.dp)
                                .padding(top = 6.dp, bottom = 12.dp)
                        ) {
                            ChatVoiceRecordingBar(
                                onSend = { recorder?.stop() },
                                onCancel = { recorder?.cancel() }
                            )
                        }
                    } else {
                        AssistantComposer(
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
                            onOpenAttachments = if (onAttachContent != null) ({ attachmentsMenu = true }) else null,
                            onStartVoice = if (recorder != null) ({
                                attachmentError = null
                                recorder.start()
                            }) else null,
                            onSend = send
                        )
                    }
                }
            }

        }

        // Scrim scuro + pannello cronologia che scorre da sinistra SOPRA la chat immobile
        if (progress > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(9f)
                    .background(Color.Black.copy(alpha = 0.50f * progress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { setDrawerOpen(false) }
                    )
                    .semantics { contentDescription = "Chiudi menu chat" }
            )

            val drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .zIndex(10f)
                    .graphicsLayer {
                        translationX = -drawerWidthPx * (1f - progress)
                    }
                    .shadow(24.dp * progress, drawerShape, ambientColor = Color.Black.copy(alpha = 0.5f))
                    .clip(drawerShape)
                    .border(1.dp, palette.hairline, drawerShape)
            ) {
                AssistantLeftDrawer(
                    palette = palette,
                    drawerWidth = drawerWidth,
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    userInitials = userInitials,
                    userDisplayName = userDisplayName,
                    activeModelLabel = headerModelLabel,
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
                    onOpenAiSettings = {
                        setDrawerOpen(false)
                        onOpenAiSettings()
                    },
                    onCloseDrawer = { setDrawerOpen(false) }
                )
            }
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn(ailaFadeSpec(AilaDuration.Quick)),
            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)),
            modifier = Modifier.fillMaxSize().zIndex(15f)
        ) {
            AssistantSearchOverlay(
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

        // Picker modelli/ragionamento animato ancorato in alto come Gemini
        AssistantModelPickerPopup(
            visible = isModelSheetOpen,
            palette = palette,
            options = modelOptions,
            selectedProviderId = provider.id,
            modelLabel = modelLabel,
            models = if (google && (catalogModels.isNotEmpty() || selectedModelId.isNotBlank())) {
                listOf(AssistantProviderModel("", "Automatico")) + catalogModels
            } else catalogModels,
            selectedModelId = selectedModelId,
            modelStatus = modelStatus,
            onSelectModel = onSelectModel.takeIf { available && provider != AiProvider.AILA_CLOUD },
            onRetryModels = { modelRetry++ },
            reasoningOptions = reasoningOptions,
            selectedReasoning = visibleReasoning.orEmpty(),
            onSelectProvider = onSelectAiProvider,
            onSelectReasoning = onSelectReasoning,
            onOpenAiSettings = onOpenAiSettings,
            onDismiss = { isModelSheetOpen = false }
        )
        }
    }

    // Foglio allegati (mantiene Fotocamera / Foto / File su una sola riga e i colori AILA vivi)
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
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = palette.ink
                    )
                    IconButton(
                        onClick = { attachmentsMenu = false },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Chiudi allegati" }
                    ) {
                        AppIcons.Close(Modifier.size(20.dp), palette.inkMuted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistantAttachmentTile(
                        title = "Fotocamera",
                        subtitle = "Scatta foto",
                        fill = AppTheme.TintGreen,
                        ink = AppTheme.TintGreenInk,
                        enabled = !isThinking && !recording,
                        onClick = {
                            attachmentError = null
                            attachmentsMenu = false
                            cameraPicker?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        CameraGlyph(Modifier.size(24.dp), it)
                    }
                    AssistantAttachmentTile(
                        title = "Foto",
                        subtitle = "Galleria",
                        fill = AppTheme.TintBlue,
                        ink = AppTheme.TintBlueInk,
                        enabled = !isThinking && !recording,
                        onClick = {
                            attachmentError = null
                            photoPicker?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcons.Photo(Modifier.size(24.dp), it)
                    }
                    AssistantAttachmentTile(
                        title = "File",
                        subtitle = "Documenti",
                        fill = AppTheme.TintViolet,
                        ink = AppTheme.TintVioletInk,
                        enabled = !isThinking && !recording,
                        onClick = { fileSources = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcons.Document(Modifier.size(24.dp), it)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcons.Shield(Modifier.size(16.dp), palette.inkMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Le azioni richiedono la tua conferma.",
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 12.5.sp),
                        color = palette.inkMuted
                    )
                    TextButton(onClick = { attachmentsMenu = false; onOpenAiSettings() }) {
                        Text("Impostazioni AI", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = palette.accent)
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
                    ) { AppIcons.Document(Modifier.size(22.dp), AppTheme.TintVioletInk) }
                    AttachmentMenuRow(
                        title = "Copie già salvate",
                        subtitle = "Riapri un file conservato da AILA",
                        onClick = { fileSources = false; attachmentsMenu = false; onOpenFiles() }
                    ) { AppIcons.Download(Modifier.size(22.dp), palette.accent) }
                }
            },
            confirmButton = { TextButton(onClick = { fileSources = false }) { Text("Chiudi") } }
        )
    }
}

// =================================================================================================
// Schermata iniziale stile Gemini / AILA Neural Expressive: logo AILA luminoso + "Da dove iniziamo?"
// =================================================================================================

@Composable
private fun AssistantWelcomeCanvas(
    palette: AssistantSurfacePalette,
    isIncognito: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isIncognito,
            transitionSpec = {
                (fadeIn(ailaFadeSpec(AilaDuration.Standard)) +
                    scaleIn(spring(dampingRatio = 0.72f, stiffness = 360f), initialScale = 0.88f)) togetherWith
                    (fadeOut(ailaFadeSpec(AilaDuration.Quick)) +
                        scaleOut(ailaFadeSpec(AilaDuration.Quick), targetScale = 0.92f))
            },
            label = "assistantWelcomeMode"
        ) { incognito ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (incognito) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(palette.pillBg),
                        contentAlignment = Alignment.Center
                    ) {
                        AssistantGhostGlyph(
                            modifier = Modifier.size(34.dp),
                            color = palette.ink,
                            filled = true,
                            eyeCutoutColor = palette.pillBg
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Chat in incognito",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 27.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = palette.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Questa conversazione non viene salvata nello storico del telefono.",
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                } else {
                    AilaLogoTile(size = 60.dp, glow = !AppTheme.isNothing)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Da dove iniziamo?",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 29.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
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
// Bolla utente (tonale AILA) e risposta assistente (onda AILA + testo nitido + fonti colorate + azioni 44dp)
// =================================================================================================

@Composable
private fun AssistantUserBubble(
    text: String,
    palette: AssistantSurfacePalette,
    animateSend: Boolean = false,
    onShown: () -> Unit = {}
) {
    val sending = remember { animateSend }
    if (sending) LaunchedEffect(Unit) { onShown() }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .ailaBubbleEnter(enabled = sending, fromEnd = true)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp))
                .background(palette.userBubbleBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = palette.userBubbleInk
            )
        }
    }
}

@Composable
private fun AssistantResponseBlock(
    message: AssistantMessage,
    palette: AssistantSurfacePalette,
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
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AilaAssistantWave(active = !revealed, size = 20.dp)
            Text(
                text = "AILA",
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = palette.inkMuted
            )
            message.modelLabel?.takeIf { it.isNotBlank() }?.let { model ->
                Text(model, color = palette.inkMuted, fontSize = 12.sp)
            }
        }

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
                        fontSize = 16.sp,
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
                                AssistantSourcePill(source = source, onClick = { onOpenSource(source) })
                            }
                        }
                    }

                    // Azioni grandi (44dp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (arriving) Modifier.ailaSheetReveal(5) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistantActionIconButton(
                            contentDescription = "Risposta utile",
                            onClick = onThumbUp
                        ) {
                            AppIcons.ThumbUp(
                                modifier = Modifier.size(19.dp),
                                color = if (feedback == 1) palette.accent else palette.inkFaint,
                                filled = feedback == 1
                            )
                        }
                        AssistantActionIconButton(
                            contentDescription = "Risposta non utile",
                            onClick = onThumbDown
                        ) {
                            AppIcons.ThumbDown(
                                modifier = Modifier.size(19.dp),
                                color = if (feedback == -1) palette.accent else palette.inkFaint,
                                filled = feedback == -1
                            )
                        }
                        AssistantActionIconButton(
                            contentDescription = if (copied) "Testo copiato" else "Copia risposta",
                            onClick = onCopy
                        ) {
                            if (copied) {
                                AppIcons.Check(Modifier.size(19.dp), color = palette.accent)
                            } else {
                                AssistantCopyGlyph(Modifier.size(19.dp), color = palette.inkFaint)
                            }
                        }
                        if (onRegenerate != null) {
                            AssistantActionIconButton(
                                contentDescription = "Rigenera risposta",
                                onClick = onRegenerate
                            ) {
                                AppIcons.Refresh(Modifier.size(19.dp), color = palette.inkFaint)
                            }
                        }
                    }

                    if (showDisclaimer) {
                        Spacer(Modifier.height(6.dp))
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
private fun AssistantActionIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun AssistantSourcePill(
    source: AssistantSource,
    onClick: () -> Unit
) {
    val tint = source.kind.tint()
    val ink = source.kind.ink()
    Row(
        modifier = Modifier
            .ailaTopicSurface(RoundedCornerShape(100.dp), tint, ink)
            .ailaPressable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        source.kind.Icon(ink, 14.dp)
        Text(
            text = source.label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        AppIcons.ChevronRight(Modifier.size(12.dp), color = ink)
    }
}

@Composable
private fun AssistantThinkingRow(phase: AssistantAgentPhase?, palette: AssistantSurfacePalette) {
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
            .padding(vertical = 8.dp)
            .semantics { contentDescription = phaseLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AilaAssistantWave(active = true, size = 24.dp)
        Text(
            text = phaseLabel,
            style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
            color = palette.inkMuted,
            modifier = Modifier.ailaPulse().clearAndSetSemantics { }
        )
    }
}

@Composable
private fun AssistantErrorCard(text: String, palette: AssistantSurfacePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.dangerBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcons.Warning(Modifier.size(20.dp).padding(top = 2.dp), color = palette.dangerInk)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Non sono riuscito a rispondere",
                style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
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
private fun AssistantAttachmentTile(
    title: String,
    subtitle: String,
    fill: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            icon(ink)
        }
        Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = ink)
        Text(subtitle, minLines = 2, style = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp), color = ink.copy(alpha = 0.82f))
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
    val palette = assistantSurfacePalette()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.Space12)
            .clip(RoundedCornerShape(22.dp))
            .background(palette.elevatedBg)
            .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Azioni sul calendario · ${plan.actions.size}",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent
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
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Verifica l’esito sul server", color = palette.ink)
            }
        }
    }
}

private val ExamplePlaceholders = listOf(
    "Quando è la prossima verifica?",
    "Cosa c'è da pagare questo mese?",
    "Riassumi l'ultima circolare",
    "Dove sono seduto in aula?",
    "Ci sono sondaggi ancora aperti?"
)

private fun AssistantSourceKind.tint(): Color = when (this) {
    AssistantSourceKind.CIRCULAR -> AppTheme.TintBlue
    AssistantSourceKind.CALENDAR -> AppTheme.TintAmber
    AssistantSourceKind.BOARD -> AppTheme.TintViolet
    AssistantSourceKind.POLL -> AppTheme.TintGreen
    AssistantSourceKind.SEAT_MAP -> AppTheme.TintSlate
    AssistantSourceKind.CLASS -> AppTheme.TintSlate
    AssistantSourceKind.GITA -> AppTheme.TintAmber
}

private fun AssistantSourceKind.ink(): Color = when (this) {
    AssistantSourceKind.CIRCULAR -> AppTheme.TintBlueInk
    AssistantSourceKind.CALENDAR -> AppTheme.TintAmberInk
    AssistantSourceKind.BOARD -> AppTheme.TintVioletInk
    AssistantSourceKind.POLL -> AppTheme.TintGreenInk
    AssistantSourceKind.SEAT_MAP -> AppTheme.TintSlateInk
    AssistantSourceKind.CLASS -> AppTheme.TintSlateInk
    AssistantSourceKind.GITA -> AppTheme.TintAmberInk
}

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

/**
 * Bolla vocale inline con la stessa estetica dei vocali in Pere (`InlineAudioAttachment`):
 * pulsante Play/Pause circolare, waveform a 28 barre con cursore che avanza, durata e link Apri.
 */
@Composable
private fun AssistantVoiceBubble(
    file: AssistantAttachedFile,
    isUser: Boolean,
    palette: AssistantSurfacePalette,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var audioBytes by remember(file.localId) { mutableStateOf<ByteArray?>(null) }
    var audioMime by remember(file.localId) { mutableStateOf("audio/mp4") }
    var loading by remember(file.localId) { mutableStateOf(false) }
    var pendingAutoPlay by remember(file.localId) { mutableStateOf(false) }

    val player = circolareplus.platform.rememberAudioPlayer(audioBytes, audioMime)

    LaunchedEffect(audioBytes, pendingAutoPlay) {
        if (pendingAutoPlay && audioBytes != null) {
            pendingAutoPlay = false
            player.togglePlayPause()
        }
    }

    val totalMs = player.durationMs
    val progress = if (totalMs > 0) (player.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val waveform = remember(file.localId) {
        val seed = file.localId.hashCode()
        List(28) { idx ->
            val mixed = ((seed * 31 + idx * 17) xor (idx * 13 + 7)) and 0x7fffffff
            0.22f + (mixed % 78) / 100f
        }
    }
    val activeColor = palette.accent
    val inactiveColor = palette.inkFaint.copy(alpha = 0.40f)
    val durationLabel = when {
        player.isPlaying || player.positionMs > 0 ->
            if (totalMs > 0) "${formatVoiceMs(player.positionMs)} / ${formatVoiceMs(totalMs)}"
            else formatVoiceMs(player.positionMs)
        totalMs > 0 -> formatVoiceMs(totalMs)
        else -> "Vocale"
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (isUser) 22.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 22.dp
    )

    Row(
        modifier = modifier
            .widthIn(min = 224.dp, max = 290.dp)
            .clip(bubbleShape)
            .background(if (isUser) palette.userBubbleBg else palette.elevatedBg)
            .border(1.dp, palette.hairline, bubbleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(activeColor)
                .clickable(enabled = !loading, role = Role.Button) {
                    if (audioBytes != null) {
                        player.togglePlayPause()
                    } else {
                        loading = true
                        scope.launch {
                            val loaded = withContext(Dispatchers.Default) {
                                runCatching {
                                    val archive = circolareplus.data.AppContainer.localContents
                                    val entry = archive.entry(file.localId)
                                    entry?.let { archive.read(it) }
                                }.getOrNull()
                            }
                            loading = false
                            if (loaded != null && loaded.bytes.isNotEmpty()) {
                                audioMime = loaded.mime.ifBlank { "audio/mp4" }
                                audioBytes = loaded.bytes
                                pendingAutoPlay = true
                            } else {
                                onOpenDetails()
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = if (player.isPlaying) "Metti in pausa vocale" else "Riproduci vocale"
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> circolareplus.design.AilaCircularProgress(Modifier.size(18.dp), color = palette.onAccent, strokeWidth = 2.dp)
                player.isPlaying -> AssistantPauseGlyph(Modifier.size(16.dp), palette.onAccent)
                else -> AssistantPlayGlyph(Modifier.size(16.dp), palette.onAccent)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                val count = waveform.size
                val step = size.width / count.toFloat()
                val barWidth = (step * 0.52f).coerceIn(2.dp.toPx(), 3.4.dp.toPx())
                val centerY = size.height / 2f
                waveform.forEachIndexed { index, amp ->
                    val barProgress = (index + 0.5f) / count.toFloat()
                    val x = index * step + step / 2f
                    val halfH = (size.height * amp * 0.44f).coerceAtLeast(2.dp.toPx())
                    drawLine(
                        color = if (barProgress <= progress) activeColor else inactiveColor,
                        start = Offset(x, centerY - halfH),
                        end = Offset(x, centerY + halfH),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
                val dotX = (size.width * progress).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
                drawCircle(
                    color = activeColor,
                    radius = 4.2.dp.toPx(),
                    center = Offset(dotX, centerY)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = durationLabel,
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                    color = palette.inkMuted
                )
                Text(
                    text = "Apri",
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = activeColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onOpenDetails)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun formatVoiceMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

@Composable
private fun AssistantPlayGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.16f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            lineTo(size.width * 0.24f, size.height * 0.84f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun AssistantPauseGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = (size.width * 0.22f).coerceAtLeast(2.dp.toPx())
        drawLine(color, Offset(size.width * 0.30f, size.height * 0.18f), Offset(size.width * 0.30f, size.height * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.70f, size.height * 0.18f), Offset(size.width * 0.70f, size.height * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
