package circolareplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.ai.AcaPolicyState
import circolareplus.ai.allows
import circolareplus.ai.assistant.AssistantAgentPhase
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.*
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForumScreen(
    isModerator: Boolean,
    onBack: () -> Unit,
    onOpenAttachment: (ContentAttachmentDto) -> Unit,
    onPrepareCalendar: (String) -> Unit,
    onActivity: (AssistantAgentPhase?) -> Unit,
    initialThreadId: String? = null,
    inline: Boolean = false,
    isActive: Boolean = true,
    onCalendarChanged: () -> Unit = {},
    onContextChange: (String) -> Unit = {},
    canSignModeration: Boolean = isModerator,
    onOpenThread: ((String) -> Unit)? = null,
    exitOnBackFromThread: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val repo = AppContainer.forumRepository
    val currentUserId = AppContainer.settings.currentUserId
    var manualPhase by remember { mutableStateOf<AssistantAgentPhase?>(null) }
    var automaticPhase by remember { mutableStateOf<AssistantAgentPhase?>(null) }
    fun activity(phase: AssistantAgentPhase?) { manualPhase = phase }
    LaunchedEffect(manualPhase, automaticPhase) { onActivity(manualPhase ?: automaticPhase) }
    val policy = AcaPolicyState.preferences
    var calendarProposals by remember { mutableStateOf<List<ForumCalendarProposalDto>>(emptyList()) }
    var confirmingProposal by remember { mutableStateOf<ForumCalendarProposalDto?>(null) }
    var proposalError by remember { mutableStateOf<String?>(null) }

    var threadFilter by rememberSaveable { mutableStateOf("ALL") }
    var threads by remember { mutableStateOf<List<ForumThreadDto>>(emptyList()) }
    var moreThreads by remember { mutableStateOf(false) }
    var threadId by rememberSaveable(initialThreadId) { mutableStateOf(initialThreadId) }
    var snapshot by remember { mutableStateOf<ForumSnapshotDto?>(null) }
    var focusPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var replyTo by remember { mutableStateOf<ForumPostDto?>(null) }
    var body by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(true) }
    var rulesAccepted by remember(currentUserId) { mutableStateOf(AppContainer.settings.hasAcceptedForumRules(currentUserId)) }
    var showRules by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var createBody by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var uploadingAttachment by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var deleteRequests by remember { mutableStateOf<List<ContentDeleteRequestDto>>(emptyList()) }
    var deleteRequestTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var notificationDialog by remember { mutableStateOf(false) }
    var mentionsDialog by remember { mutableStateOf(false) }
    var attachmentsMenu by remember { mutableStateOf(false) }
    var confirmToggleResolved by remember { mutableStateOf(false) }
    var menuPost by remember { mutableStateOf<ForumPostDto?>(null) }
    var reportPost by remember { mutableStateOf<ForumPostDto?>(null) }
    var reports by remember { mutableStateOf<List<ForumReportDto>?>(null) }
    var summary by remember { mutableStateOf<AcaAiResultDto?>(null) }
    var consentRequest by remember { mutableStateOf<ForumAiRequestDto?>(null) }
    var pendingAttachments by remember { mutableStateOf<List<ContentAttachmentDto>>(emptyList()) }
    var recording by remember { mutableStateOf(false) }
    var createAttachments by remember { mutableStateOf<List<ContentAttachmentDto>>(emptyList()) }

    suspend fun reloadDeleteRequests() {
        if (!canSignModeration) return
        deleteRequests = runCatching { repo.listDeleteRequests() }.getOrDefault(deleteRequests)
    }

    suspend fun refresh(older: Boolean = false) {
        val id = threadId
        if (id == null) {
            val result = repo.list(filter = threadFilter)
            if (result.threads != threads) threads = result.threads
            moreThreads = result.hasMore
            return
        }
        val next = repo.thread(id, if (older) snapshot?.nextBefore else null)
        try {
            val proposals = repo.calendarProposals(id)
            if (threadId == id && calendarProposals != proposals) calendarProposals = proposals
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        if (threadId != id) return
        val previous = snapshot?.takeIf { it.thread.id == id }
        val refreshed = if (previous == null) next else next.copy(
            posts = (previous.posts + next.posts).associateBy { it.id }.values
                .sortedWith(compareBy<ForumPostDto> { it.id != id }.thenBy { it.createdAt }.thenBy { it.id }),
            hasMore = if (older) next.hasMore else previous.hasMore || next.hasMore,
            nextBefore = if (older) next.nextBefore else previous.nextBefore ?: next.nextBefore
        )
        if (snapshot != refreshed) snapshot = refreshed
    }

    fun launchWork(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Operazione non completata"
            } finally {
                busy = false
                activity(null)
            }
        }
    }

    LaunchedEffect(threadId, threadFilter, canSignModeration) {
        calendarProposals = emptyList()
        proposalError = null
        confirmingProposal = null
        snapshot = null
        focusPostId = null
        replyTo = null
        body = ""
        pendingAttachments = emptyList()
        error = null
        try {
            refresh()
            reloadDeleteRequests()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
        while (isActive) {
            delay(15_000)
            if (AppForeground.isForeground) {
                try {
                    refresh()
                    reloadDeleteRequests()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(Unit) { onDispose { onActivity(null) } }

    val automaticCloudAllowed = policy.cloudConsent && AppContainer.settings.aiProvider != circolareplus.ai.AiProvider.ON_DEVICE.id
    LaunchedEffect(threadId, snapshot?.posts?.map { it.id to it.hidden }, policy.aiEnabled, policy.acaEnabled, policy.services, automaticCloudAllowed) {
        val id = threadId
        if (id != null && snapshot != null && policy.allows("FORUM_SUGGESTIONS") && automaticCloudAllowed) {
            delay(1500)
            try {
                automaticPhase = AssistantAgentPhase.PLANNING
                proposalError = null
                val result = repo.analyzeCalendarProposals(id)
                if (threadId == id) calendarProposals = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                proposalError = "Proposte automatiche non disponibili: ${e.message.orEmpty()}"
            } finally {
                automaticPhase = null
            }
        }
    }

    val addFile: (IncomingContent) -> Unit = { file ->
        when {
            creating && createAttachments.size < 5 -> {
                uploadingAttachment = true
                error = null
                scope.launch {
                    try {
                        activity(AssistantAgentPhase.READING)
                        createAttachments = createAttachments + AppContainer.acaRepository.upload(file, persistLocal = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Impossibile caricare l'allegato."
                    } finally {
                        uploadingAttachment = false
                        activity(null)
                    }
                }
            }
            creating -> error = "Puoi allegare al massimo 5 file alla domanda."
            threadId != null && pendingAttachments.size < 5 -> {
                val targetThread = threadId!!
                uploadingAttachment = true
                error = null
                scope.launch {
                    try {
                        activity(AssistantAgentPhase.READING)
                        pendingAttachments = pendingAttachments + AppContainer.acaRepository.upload(file, targetThread, persistLocal = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Impossibile caricare l'allegato."
                    } finally {
                        uploadingAttachment = false
                        activity(null)
                    }
                }
            }
            else -> error = "Apri una discussione e allega al massimo 5 file per risposta."
        }
    }

    val closeCreation: () -> Unit = {
        if (!busy) {
            creating = false
            val abandoned = createAttachments
            createAttachments = emptyList()
            scope.launch {
                abandoned.forEach { item ->
                    try { AppContainer.acaRepository.removeAttachment(item.id) } catch (_: Exception) {}
                }
            }
        }
    }

    val pickFile = rememberContentPicker({ error = it }, addFile)
    val pickPhoto = rememberContentPicker({ error = it }, addFile, photosOnly = true)
    val takePhoto = rememberCameraPhotoPicker({ error = it }, addFile)
    val recorder = rememberVoiceRecorder({ recording = it }, { error = it }, addFile)

    fun askAi(request: ForumAiRequestDto) {
        launchWork {
            val p = AppContainer.acaRepository.preferences().preferences
            if (!p.aiEnabled || !p.acaEnabled || "FORUM" !in p.services) {
                error = "Attiva AI, ACA e il servizio Forum in Impostazioni → AI."
                return@launchWork
            }
            if (!p.cloudConsent) {
                consentRequest = request
                return@launchWork
            }
            activity(AssistantAgentPhase.WRITING)
            val result = repo.ai(threadId ?: return@launchWork, request.question, request.publish, request.replyToId)
            if (request.publish) refresh() else summary = result
        }
    }

    confirmingProposal?.let { proposal ->
        AilaAlertDialog(
            onDismissRequest = { confirmingProposal = null },
            title = { Text("Aggiungere al calendario della classe?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    proposal.plan.actions.forEach { event ->
                        Text("${event.title}\n${event.date}${event.time?.let { " · $it" }.orEmpty()} · ${event.category}\n${event.notes.orEmpty()}")
                    }
                    Text("Visibile a tutta la classe. La prima conferma salva gli eventi e aggiorna la proposta per tutti.")
                }
            },
            confirmButton = {
                AilaTextButton(enabled = !busy, onClick = {
                    confirmingProposal = null
                    launchWork {
                        activity(AssistantAgentPhase.EXECUTING)
                        try {
                            val operation = AppContainer.acaRepository.previewShared(proposal)
                            AppContainer.acaRepository.confirm(operation.id, operation.digest)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val current = repo.calendarProposals(proposal.threadId)
                            calendarProposals = current
                            if (current.none { it.id == proposal.id && it.status == "ACCEPTED" }) throw e
                        }
                        onCalendarChanged()
                        refresh()
                    }
                }) { Text("Conferma per la classe") }
            },
            dismissButton = {
                AilaTextButton(onClick = { confirmingProposal = null }) { Text("Annulla") }
            }
        )
    }

    LaunchedEffect(snapshot?.posts, focusPostId) {
        onContextChange(
            snapshot?.let { snap ->
                "Discussione: ${snap.thread.title}\n" +
                    snap.posts.filter { !it.hidden }.takeLast(30)
                        .joinToString("\n") { "${it.author.label}: ${it.body}" }
                        .take(6000)
            }.orEmpty()
        )
    }
    DisposableEffect(Unit) { onDispose { onContextChange("") } }

    val goBack: () -> Unit = {
        when {
            focusPostId != null -> focusPostId = null
            threadId != null && !exitOnBackFromThread -> {
                threadId = null
                snapshot = null
            }
            else -> onBack()
        }
    }
    PlatformBackHandler(enabled = isActive && (!inline || threadId != null || focusPostId != null)) { goBack() }

    val gutter = if (AppTheme.isNothing) NothingSpacing.Edge else AppTheme.Space16

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .then(
                if (inline) Modifier.padding(bottom = LocalBottomBarPadding.current)
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
            .imePadding()
    ) {
        if (!inline) {
            if (threadId != null && rulesAccepted) {
                // Intestazione compatta in stile chat (WhatsApp / Telegram), senza barra Classe né tab.
                val th = snapshot?.thread
                Column(modifier = Modifier.fillMaxWidth().background(AppTheme.BackgroundLight)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AilaIconButton(
                            contentDescription = if (focusPostId != null) "Tutta la discussione" else "Indietro",
                            onClick = goBack,
                            size = 38.dp
                        ) { tint -> AppIcons.ChevronLeft(Modifier.size(18.dp), tint) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = if (focusPostId != null) "Ramo conversazione" else (th?.title ?: "Discussione"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            th?.let { current ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (current.resolved) AppTheme.TintGreenInk else AppTheme.PrimaryBlue)
                                    )
                                    Text(
                                        text = if (current.resolved) "Risolta" else "Aperta",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (current.resolved) AppTheme.TintGreenInk else AppTheme.TextMuted
                                    )
                                }
                            }
                        }
                        if (th != null) {
                            AilaIconButton(
                                contentDescription = "Riassumi con AILA",
                                onClick = { askAi(ForumAiRequestDto()) },
                                enabled = !busy,
                                size = 36.dp
                            ) { _ ->
                                AilaAssistantMark(size = 17.dp, brush = androidx.compose.ui.graphics.SolidColor(AppTheme.TintVioletInk))
                            }
                        }
                        AilaIconButton("Notifiche discussione", { notificationDialog = true }, size = 36.dp) { tint ->
                            AppIcons.Bell(Modifier.size(18.dp), tint)
                        }
                    }
                    HorizontalDivider(color = AppTheme.Hairline)
                }
            } else {
                AilaBackBar(
                    title = "Revisione tra pere",
                    onBackClick = goBack
                )
            }
        } else if (threadId != null && rulesAccepted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AilaIconButton(
                    contentDescription = if (focusPostId != null) "Tutta la discussione" else "Elenco discussioni",
                    onClick = goBack,
                    size = 38.dp
                ) { tint -> AppIcons.ChevronLeft(Modifier.size(18.dp), tint) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (focusPostId != null) "Ramo conversazione" else (snapshot?.thread?.title ?: "Discussione"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    snapshot?.thread?.let { th ->
                        Text(
                            text = if (th.resolved) "Risolta" else "Aperta",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (th.resolved) AppTheme.TintGreenInk else AppTheme.TextMuted
                        )
                    }
                }
                AilaIconButton("Preferenze notifiche", { notificationDialog = true }, size = 36.dp) { tint ->
                    AppIcons.Bell(Modifier.size(18.dp), tint)
                }
            }
        }

        if (!rulesAccepted) {
            var checked by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AilaCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AilaIconTile(tint = AppTheme.TintBlue, size = 40.dp) {
                                AppIcons.Shield(Modifier.size(20.dp), AppTheme.TintBlueInk)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Regole di Revisione tra pere", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                                Text("Accettazione obbligatoria per accedere", style = MaterialTheme.typography.labelMedium, color = AppTheme.PrimaryBlue)
                            }
                        }
                        Text(
                            "Le discussioni sono visibili alla tua classe e restano disponibili nel tempo. Scegli nome reale o pseudonimo al primo intervento: l’identità resta fissa in quella discussione. Non pubblicare dati personali o contenuti offensivi. Puoi segnalare gli abusi; i Rappresentanti e la Guardia di Sicurezza possono rimuovere i contenuti con firma congiunta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.TextDark
                        )
                        Text(
                            "Puoi richiamare @ai (o @ia) o chiedere un riassunto esplicitamente. I messaggi condivisi possono essere analizzati anche da altri lettori autorizzati che attivano le proposte automatiche: le proposte sono visibili alla classe e nessun evento viene aggiunto senza conferma. Le risposte AI sono assistenza e possono contenere errori. L’elaborazione cloud richiede il consenso in Impostazioni → AI. Sul server: 90 giorni fino a 3 MB; oltre: 7 giorni. Gli audio durano al massimo 10 minuti.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.TextMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { checked = !checked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AilaCheckbox(checked = checked, onCheckedChange = { checked = it })
                            Text("Ho letto e accetto le regole e l’informativa", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark, modifier = Modifier.weight(1f))
                        }
                        AilaPrimaryButton(
                            text = "Accetta e continua",
                            enabled = checked,
                            fillMaxWidth = true,
                            onClick = {
                                AppContainer.settings.setAcceptedForumRules(currentUserId, true)
                                rulesAccepted = true
                            }
                        )
                        if (!inline) {
                            AilaTextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("Torna indietro")
                            }
                        }
                    }
                }
            }
            return@Column
        }

        error?.let { err ->
            Box(Modifier.padding(horizontal = gutter, vertical = 6.dp)) {
                AilaCard(containerColor = AppTheme.TintRed) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcons.Warning(Modifier.size(16.dp), AppTheme.TintRedInk)
                        Text(err, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk, modifier = Modifier.weight(1f))
                        AilaTextButton(onClick = { error = null }) { Text("Chiudi") }
                    }
                }
            }
        }

        feedbackMessage?.let { msg ->
            Box(Modifier.padding(horizontal = gutter, vertical = 6.dp)) {
                AilaCard(containerColor = AppTheme.TintGreen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintGreenInk, modifier = Modifier.weight(1f))
                        AilaTextButton(onClick = { feedbackMessage = null }) { Text("OK") }
                    }
                }
            }
        }

        if (threadId == null) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = gutter)
                    .padding(top = 12.dp)
            ) {
                ForumIntroduction(busy, onCreate = { creating = true }, onRules = { showRules = true })
                Spacer(Modifier.height(12.dp))
                val filters = listOf("ALL", "OPEN", "RESOLVED", "MINE")
                AilaFilterBar(
                    labels = listOf("Tutte", "Aperte", "Risolte", "Le mie"),
                    selectedIndex = filters.indexOf(threadFilter),
                    onSelect = {
                        if (threadFilter != filters[it]) {
                            threadFilter = filters[it]
                            threads = emptyList()
                            moreThreads = false
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (canSignModeration && deleteRequests.isNotEmpty()) {
                    DeleteRequestsPanel(
                        requests = deleteRequests,
                        onApprove = { reqId ->
                            val executed = repo.approveDelete(reqId)
                            reloadDeleteRequests()
                            refresh()
                            feedbackMessage = if (executed) "Contenuto eliminato con tutte le firme." else "Firma registrata."
                        },
                        onReject = { reqId ->
                            repo.rejectDelete(reqId)
                            reloadDeleteRequests()
                            feedbackMessage = "Richiesta di eliminazione rifiutata."
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (threads.isEmpty() && !busy) {
                    if (threadFilter == "ALL") ForumEmptyState()
                    else ForumEmptyState(title = "Nessuna discussione qui", description = "Non ci sono discussioni con questo filtro. Prova un’altra categoria.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                    ) {
                        items(threads, key = { it.id }) { t ->
                            AilaCard(onClick = { if (onOpenThread != null) onOpenThread(t.id) else threadId = t.id }) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ForumStatusPill(
                                            text = if (t.resolved) "Risolta" else "Aperta",
                                            tint = if (t.resolved) AppTheme.TintGreen else AppTheme.TintBlue,
                                            ink = if (t.resolved) AppTheme.TintGreenInk else AppTheme.TintBlueInk
                                        )
                                        if (t.featuredPostId != null) {
                                            ForumStatusPill(
                                                text = "In evidenza",
                                                tint = AppTheme.TintViolet,
                                                ink = AppTheme.TintVioletInk
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${t.replies} ${if (t.replies == 1) "risposta" else "risposte"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.TextMuted
                                        )
                                        AppIcons.ChevronRight(Modifier.size(14.dp), AppTheme.TextFaint)
                                    }
                                    Text(t.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                                    if (t.needsResolution) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AppTheme.TintBlue)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AppIcons.Bulb(Modifier.size(14.dp), AppTheme.TintBlueInk)
                                            Text(
                                                "Sei ancora qui? Puoi indicare se la domanda è risolta.",
                                                color = AppTheme.TintBlueInk,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (moreThreads) {
                            item {
                                AilaTextButton(
                                    onClick = {
                                        launchWork {
                                            val next = repo.list(threads.size, filter = threadFilter)
                                            threads = (threads + next.threads).distinctBy { it.id }
                                            moreThreads = next.hasMore
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Carica altre discussioni") }
                            }
                        }
                    }
                }
            }
        } else {
            val snap = snapshot
            if (snap != null) {
                val descendants = remember(snap.posts, focusPostId) {
                    val ids = mutableSetOf<String>()
                    focusPostId?.let { ids += it }
                    var changed = true
                    while (changed) {
                        changed = false
                        snap.posts.forEach { if (it.parentId in ids && ids.add(it.id)) changed = true }
                    }
                    ids
                }
                val visible = snap.posts.filter {
                    if (focusPostId != null) it.id in descendants else it.id == snap.thread.id || it.parentId == snap.thread.id
                }

                val chatListState = rememberLazyListState()
                var lastMessageCount by remember(snap.thread.id, focusPostId) { mutableStateOf(0) }
                LaunchedEffect(visible.size) {
                    if (visible.size > lastMessageCount && visible.isNotEmpty()) {
                        val target = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        if (lastMessageCount > 0) chatListState.animateScrollToItem(target)
                    }
                    lastMessageCount = visible.size
                }

                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "thread_header") {
                        // Card compatta della discussione con pulsanti icona invece di testi lunghi.
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = AppTheme.CardSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.Hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ForumStatusPill(
                                        text = if (snap.thread.resolved) "Risolta" else "Aperta",
                                        tint = if (snap.thread.resolved) AppTheme.TintGreen else AppTheme.TintBlue,
                                        ink = if (snap.thread.resolved) AppTheme.TintGreenInk else AppTheme.TintBlueInk
                                    )
                                    Text(
                                        text = snap.thread.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.TextDark,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (snap.thread.needsResolution) {
                                    Text(
                                        "Se hai trovato la risposta, puoi segnarla come risolta.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.PrimaryBlue
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ForumCompactIconButton(
                                        contentDescription = "Riassumi discussione con AILA",
                                        tint = AppTheme.TintViolet,
                                        ink = AppTheme.TintVioletInk,
                                        enabled = !busy,
                                        onClick = { askAi(ForumAiRequestDto()) }
                                    ) { AilaAssistantMark(size = 16.dp, brush = androidx.compose.ui.graphics.SolidColor(it)) }

                                    if (snap.thread.isAuthor || isModerator) {
                                        ForumCompactIconButton(
                                            contentDescription = if (snap.thread.resolved) "Riapri discussione" else "Segna come risolta",
                                            tint = if (snap.thread.resolved) AppTheme.TintSlate else AppTheme.TintGreen,
                                            ink = if (snap.thread.resolved) AppTheme.TintSlateInk else AppTheme.TintGreenInk,
                                            enabled = !busy,
                                            onClick = { confirmToggleResolved = true }
                                        ) { ink ->
                                            if (snap.thread.resolved) AppIcons.Refresh(Modifier.size(16.dp), ink)
                                            else AppIcons.Check(Modifier.size(16.dp), ink)
                                        }
                                    }

                                    if (canSignModeration) {
                                        ForumCompactIconButton(
                                            contentDescription = "Segnalazioni",
                                            tint = AppTheme.TintAmber,
                                            ink = AppTheme.TintAmberInk,
                                            enabled = !busy,
                                            onClick = { launchWork { reports = repo.reports(snap.thread.id) } }
                                        ) { AppIcons.Warning(Modifier.size(16.dp), it) }

                                        ForumCompactIconButton(
                                            contentDescription = "Richiedi eliminazione discussione",
                                            tint = AppTheme.TintRed,
                                            ink = AppTheme.TintRedInk,
                                            enabled = !busy,
                                            onClick = { deleteRequestTarget = Triple("FORUM_THREAD", snap.thread.id, snap.thread.title) }
                                        ) { AppIcons.Trash(Modifier.size(16.dp), it) }
                                    }

                                    Spacer(Modifier.weight(1f))

                                    if (focusPostId != null) {
                                        ForumActionPill(
                                            label = "Tutta la chat",
                                            tint = AppTheme.TintBlue,
                                            ink = AppTheme.TintBlueInk,
                                            enabled = true,
                                            onClick = { focusPostId = null }
                                        ) { AppIcons.ChevronLeft(Modifier.size(13.dp), it) }
                                    }
                                }
                            }
                        }
                    }

                    if (canSignModeration && deleteRequests.isNotEmpty()) {
                        item(key = "thread_delete_requests") {
                            DeleteRequestsPanel(
                                requests = deleteRequests,
                                onApprove = { reqId ->
                                    val executed = repo.approveDelete(reqId)
                                    reloadDeleteRequests()
                                    if (executed) {
                                        threadId = null
                                        snapshot = null
                                    }
                                    refresh()
                                    feedbackMessage = if (executed) "Contenuto eliminato con tutte le firme." else "Firma registrata."
                                },
                                onReject = { reqId ->
                                    repo.rejectDelete(reqId)
                                    reloadDeleteRequests()
                                    feedbackMessage = "Richiesta di eliminazione rifiutata."
                                }
                            )
                        }
                    }

                    if (focusPostId == null) {
                        if (automaticPhase != null) {
                            item(key = "aca_scanning") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AilaAssistantWave(active = true, size = 20.dp)
                                    Text("AILA cerca impegni nella conversazione…", style = MaterialTheme.typography.bodySmall, color = AppTheme.PrimaryBlue)
                                }
                            }
                        }
                        calendarProposals.filter { it.status != "STALE" }.forEach { proposal ->
                            item(key = "calendar-${proposal.id}") {
                                AilaCard(containerColor = AppTheme.TintBlue) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            if (proposal.status == "ACCEPTED") "Già aggiunto al calendario" else "Proposta per il calendario di classe",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AppTheme.TintBlueInk
                                        )
                                        proposal.plan.actions.forEach { event ->
                                            Text("${event.date} · ${event.title}${event.time?.let { " · $it" }.orEmpty()}", color = AppTheme.TextDark)
                                            event.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted) }
                                        }
                                        Text(
                                            "Proposta AI (${proposal.provider}) · verifica date e dettagli nelle fonti.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.TextMuted
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            AilaTextButton(onClick = { focusPostId = proposal.sourcePostIds.firstOrNull { it != snap.thread.id } ?: snap.thread.id }) {
                                                Text("Vedi origine")
                                            }
                                            if (proposal.status == "PENDING") {
                                                AilaPrimaryButton(
                                                    text = "Aggiungi",
                                                    compact = true,
                                                    enabled = !busy && policy.allows("CALENDAR"),
                                                    onClick = { confirmingProposal = proposal }
                                                )
                                            }
                                        }
                                        if (proposal.status == "PENDING" && !policy.allows("CALENDAR")) {
                                            Text("Attiva il servizio Calendario in Impostazioni → AI per confermare.", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                        proposalError?.let { message ->
                            item(key = "proposal_err") { Text(message, color = AppTheme.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    if (snap.hasMore) {
                        item(key = "load_more_posts") {
                            AilaTextButton(onClick = { launchWork { refresh(true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Carica messaggi precedenti")
                            }
                        }
                    }

                    items(visible, key = { it.id }) { post ->
                        val isFeatured = post.id == snap.thread.featuredPostId
                        val isMine = post.author.isMe && !post.isAi
                        val replies = snap.posts.count { it.parentId == post.id }
                        val bubbleShape = if (isMine) {
                            RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                        } else {
                            RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                        }
                        val bubbleBg = when {
                            post.isAi -> AppTheme.TintViolet.copy(alpha = 0.42f)
                            isMine -> AppTheme.TintBlue.copy(alpha = 0.70f)
                            else -> AppTheme.CardSurface
                        }
                        val bubbleBorder = when {
                            post.isAi -> AppTheme.TintVioletInk.copy(alpha = 0.25f)
                            isMine -> AppTheme.PrimaryBlue.copy(alpha = 0.28f)
                            else -> AppTheme.Hairline
                        }
                        val timeLabel = remember(post.createdAt) { formatForumChatTime(post.createdAt) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMine) {
                                PostAuthorAvatar(post.author, post.isAi, size = 28.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .clip(bubbleShape)
                                    .background(bubbleBg)
                                    .border(1.dp, bubbleBorder, bubbleShape)
                                    .combinedClickable(
                                        onClick = { if (post.id != snap.thread.id && replies > 0) focusPostId = post.id },
                                        onLongClick = { menuPost = post }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isMine) "Tu" else post.author.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            post.isAi -> AppTheme.TintVioletInk
                                            isMine -> AppTheme.TintBlueInk
                                            else -> AppTheme.PrimaryBlue
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (post.author.isOp) {
                                        ForumStatusPill("OP", AppTheme.TintSlate, AppTheme.TintSlateInk)
                                    } else if (post.isAi) {
                                        ForumStatusPill("AI", AppTheme.TintViolet, AppTheme.TintVioletInk)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable(role = Role.Button) { menuPost = post }
                                            .semantics { contentDescription = "Azioni messaggio" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DotsMoreGlyph(Modifier.size(15.dp), AppTheme.TextMuted)
                                    }
                                }

                                if (isFeatured) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(AppTheme.TintBlue)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        AppIcons.Star(Modifier.size(11.dp), AppTheme.TintBlueInk, filled = true)
                                        Text("In evidenza", color = AppTheme.TintBlueInk, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                var shown = post.body
                                post.mentions.filter { it.isMe }.forEach { shown = shown.replace("@${it.label}", "@Tu") }
                                if (shown.isNotBlank()) {
                                    Text(
                                        text = shown,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (post.hidden) AppTheme.TextMuted else AppTheme.TextDark
                                    )
                                }

                                post.attachments.forEach { a ->
                                    LocalForumAttachment(a, !busy) { onOpenAttachment(a) }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (post.id != snap.thread.id && replies > 0) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(AppTheme.TintBlue)
                                                .clickable { focusPostId = post.id }
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "$replies ${if (replies == 1) "risposta" else "risposte"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppTheme.TintBlueInk
                                            )
                                            AppIcons.ChevronRight(Modifier.size(11.dp), AppTheme.TintBlueInk)
                                        }
                                    }
                                    if (!post.hidden) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = !busy, role = Role.Button) {
                                                    replyTo = post
                                                    focusPostId = if (post.id == snap.thread.id) null else post.id
                                                }
                                                .semantics { contentDescription = "Rispondi al messaggio" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppIcons.ChatBubble(Modifier.size(13.dp), AppTheme.TextMuted)
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (timeLabel.isNotEmpty()) {
                                        Text(
                                            text = timeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Barra inferiore identica a quella di AILA Assistant (+ menu allegati, campo pillola, microfono, invio).
                val hasDraft = body.isNotBlank() || pendingAttachments.isNotEmpty()
                val canSend = !busy && !uploadingAttachment && rulesAccepted && hasDraft
                val sendReply: () -> Unit = {
                    val text = body
                    val parent = replyTo?.id ?: focusPostId ?: snap.thread.id
                    val mentions = snap.participants.filter { text.contains("@${it.label}") }.map { it.key }
                    launchWork {
                        val sent = repo.reply(snap.thread.id, text, snap.myIdentity?.anonymous ?: anonymous, parent, mentions, pendingAttachments.map { it.id })
                        body = ""
                        pendingAttachments = emptyList()
                        replyTo = null
                        refresh()
                        if (sent.invokePerabot) {
                            val p = AppContainer.acaRepository.preferences().preferences
                            val request = ForumAiRequestDto(question = text, publish = true, replyToId = sent.id)
                            if (!p.aiEnabled || !p.acaEnabled || "FORUM" !in p.services) {
                                error = "Attiva AI, ACA e il servizio Forum in Impostazioni → AI."
                                return@launchWork
                            }
                            if (!p.cloudConsent) {
                                consentRequest = request
                            } else {
                                activity(AssistantAgentPhase.WRITING)
                                repo.ai(snap.thread.id, text, true, sent.id)
                                refresh()
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTheme.Space12)
                        .padding(top = AppTheme.Space8, bottom = AppTheme.Space12),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    replyTo?.let { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.CardSurface)
                                .border(1.dp, AppTheme.PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(AppTheme.PrimaryBlue))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    p.author.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.PrimaryBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (p.body.isNotBlank()) {
                                    Text(
                                        p.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { replyTo = null },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Close(Modifier.size(14.dp), AppTheme.TextMuted)
                            }
                        }
                    }

                    if (pendingAttachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pendingAttachments.forEach { a ->
                                val isImg = a.mime.startsWith("image/", ignoreCase = true)
                                val isAudio = a.mime.startsWith("audio/", ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .widthIn(min = 170.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AppTheme.CardSurface)
                                        .border(1.dp, AppTheme.PrimaryBlue.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .background(
                                                when {
                                                    isImg -> AppTheme.TintBlue
                                                    isAudio -> AppTheme.TintGreen
                                                    else -> AppTheme.TintViolet
                                                },
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            isImg -> AppIcons.Photo(Modifier.size(18.dp), AppTheme.TintBlueInk)
                                            isAudio -> ForumMicGlyph(AppTheme.TintGreenInk)
                                            else -> AppIcons.Document(Modifier.size(18.dp), AppTheme.TintVioletInk)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (isAudio) "Messaggio vocale" else a.fileName,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = AppTheme.TextDark
                                        )
                                        Text("Pronto per l’invio", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                                    }
                                    IconButton(
                                        onClick = {
                                            pendingAttachments = pendingAttachments.filterNot { it.id == a.id }
                                            scope.launch {
                                                try { AppContainer.acaRepository.removeAttachment(a.id) } catch (_: Exception) {}
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.size(30.dp).semantics { contentDescription = "Rimuovi allegato ${a.fileName}" }
                                    ) {
                                        AppIcons.Close(Modifier.size(15.dp), AppTheme.TextMuted)
                                    }
                                }
                            }
                        }
                    }

                    if (uploadingAttachment) {
                        Text("Caricamento allegato…", style = MaterialTheme.typography.labelSmall, color = AppTheme.PrimaryBlue)
                    }

                    if (snap.myIdentity == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IdentityChip(
                                label = "Anonimo",
                                selected = anonymous,
                                onClick = { anonymous = true }
                            )
                            IdentityChip(
                                label = "Nome reale",
                                selected = !anonymous,
                                onClick = { anonymous = false }
                            )
                        }
                    }

                    if (recording) {
                        VoiceRecordingPanel(
                            onStop = recorder.stop,
                            onCancel = recorder.cancel,
                            subtitle = "Il vocale verrà allegato al messaggio."
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppTheme.CardSurface, RoundedCornerShape(28.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AilaIconButton(
                                contentDescription = "Menu allegati",
                                onClick = { attachmentsMenu = true },
                                enabled = !busy && !uploadingAttachment
                            ) { tint -> AppIcons.Plus(Modifier.size(24.dp), tint) }
                            Spacer(Modifier.width(4.dp))
                            AilaPillTextField(
                                value = body,
                                onValueChange = { body = it },
                                placeholder = "Messaggio… (@ai)",
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                containerColor = Color.Transparent,
                                maxLines = 4,
                                integrated = true,
                                enabled = !busy
                            )
                            Spacer(Modifier.width(AppTheme.Space8))
                            AilaIconButton(
                                contentDescription = "Registra vocale",
                                enabled = !busy && !uploadingAttachment && pendingAttachments.size < 5,
                                onClick = {
                                    error = null
                                    recorder.start()
                                }
                            ) { tint -> ForumMicGlyph(tint) }
                            if (hasDraft) {
                                ForumSendButton(enabled = canSend, onClick = sendReply)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ForumNewQuestionSheet(
            title = createTitle,
            question = createBody,
            anonymous = anonymous,
            accepted = rulesAccepted,
            busy = busy || uploadingAttachment,
            error = error,
            attachments = createAttachments,
            onPickAttachment = pickFile,
            onTakePhoto = takePhoto,
            recording = recording,
            onStartVoice = recorder.start,
            onStopVoice = recorder.stop,
            onCancelVoice = recorder.cancel,
            onRemoveAttachment = { item ->
                createAttachments = createAttachments.filterNot { it.id == item.id }
                scope.launch {
                    try { AppContainer.acaRepository.removeAttachment(item.id) } catch (_: Exception) {}
                }
            },
            onTitle = { createTitle = it },
            onQuestion = { createBody = it },
            onAnonymous = { anonymous = it },
            onAccepted = {
                if (it) {
                    AppContainer.settings.setAcceptedForumRules(currentUserId, true)
                    rulesAccepted = true
                }
            },
            onRules = { showRules = true },
            onClose = closeCreation,
            onPublish = {
                launchWork {
                    val created = repo.create(createTitle, createBody, anonymous, createAttachments.map { it.id })
                    creating = false
                    createTitle = ""
                    createBody = ""
                    createAttachments = emptyList()
                    if (onOpenThread != null) onOpenThread(created.thread.id) else threadId = created.thread.id
                }
            }
        )
    }

    if (showRules) {
        AilaAlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("Revisione tra pere") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Le discussioni sono visibili alla tua classe e restano disponibili nel tempo. Scegli nome reale o pseudonimo al primo intervento: l’identità resta fissa in quella discussione. Non pubblicare dati personali o contenuti offensivi. Puoi segnalare gli abusi; i Rappresentanti e la Guardia di Sicurezza possono rimuovere i contenuti con firma congiunta.")
                    Spacer(Modifier.height(12.dp))
                    Text("Puoi richiamare @ai (o @ia) o chiedere un riassunto esplicitamente. I messaggi condivisi possono essere analizzati anche da altri lettori autorizzati che attivano le proposte automatiche: le proposte sono visibili alla classe e nessun evento viene aggiunto senza conferma. Disattivare la tua AI non impedisce l’analisi dei messaggi già condivisi da parte degli altri lettori. Le risposte AI sono assistenza e possono contenere errori. L’elaborazione cloud richiede il consenso in Impostazioni → AI. Sul server: 90 giorni fino a 3 MB; oltre: 7 giorni. Le copie sul dispositivo restano fino alla cancellazione manuale. Gli audio durano al massimo 10 minuti. La scadenza degli allegati non elimina i messaggi.")
                }
            },
            confirmButton = {
                AilaTextButton(onClick = {
                    AppContainer.settings.setAcceptedForumRules(currentUserId, true)
                    rulesAccepted = true
                    showRules = false
                }) { Text("Ho letto e accetto") }
            }
        )
    }

    if (notificationDialog) {
        AilaBottomSheet(onDismissRequest = { notificationDialog = false }) {
            ForumSheetContainer(title = "Notifiche discussione", onClose = { notificationDialog = false }) {
                listOf(
                    "REPLIES" to "Solo risposte a me",
                    "ALL" to "Tutte le risposte",
                    "NONE" to "Nessuna notifica"
                ).forEach { (value, label) ->
                    AilaCard {
                        AilaListRow(
                            title = label,
                            tint = AppTheme.TintBlue,
                            icon = { AppIcons.Bell(Modifier.size(18.dp), AppTheme.TintBlueInk) },
                            onClick = {
                                launchWork {
                                    repo.subscribe(threadId!!, value)
                                    refresh()
                                    notificationDialog = false
                                }
                            },
                            trailing = {
                                AilaRadioButton(selected = snapshot?.notificationMode == value, onClick = null)
                            }
                        )
                    }
                }
            }
        }
    }

    if (mentionsDialog) {
        AilaBottomSheet(onDismissRequest = { mentionsDialog = false }) {
            ForumSheetContainer(title = "Menziona nella risposta", onClose = { mentionsDialog = false }) {
                AilaCard {
                    AilaListRow(
                        title = "@ai · Chiedi ad AILA",
                        subtitle = "Risponde pubblicamente nella discussione",
                        tint = AppTheme.TintViolet,
                        icon = { AilaAssistantMark(size = 18.dp, brush = androidx.compose.ui.graphics.SolidColor(AppTheme.TintVioletInk)) },
                        onClick = {
                            body += if (body.endsWith(" ") || body.isEmpty()) "@ai " else " @ai "
                            mentionsDialog = false
                        }
                    )
                }
                snapshot?.participants.orEmpty().forEach { p ->
                    AilaCard {
                        AilaListRow(
                            title = if (p.isMe) "@Tu (${p.label})" else "@${p.label}",
                            tint = AppTheme.TintBlue,
                            icon = { AppIcons.Profile(Modifier.size(18.dp), AppTheme.TintBlueInk) },
                            onClick = {
                                body += if (body.endsWith(" ") || body.isEmpty()) "@${p.label} " else " @${p.label} "
                                mentionsDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    menuPost?.let { p ->
        AilaBottomSheet(onDismissRequest = { menuPost = null }) {
            ForumSheetContainer(
                title = p.author.label,
                subtitle = if (p.body.isNotBlank()) p.body.take(80) else null,
                onClose = { menuPost = null }
            ) {
                AilaCard {
                    AilaListRow(
                        title = "Riprendi messaggio",
                        subtitle = "Rispondi citando questo intervento",
                        tint = AppTheme.TintBlue,
                        icon = { AppIcons.ChatBubble(Modifier.size(18.dp), AppTheme.TintBlueInk) },
                        onClick = {
                            replyTo = p
                            focusPostId = if (p.id == threadId) null else p.id
                            menuPost = null
                        }
                    )
                }
                AilaCard {
                    AilaListRow(
                        title = "Prepara eventi nel calendario",
                        subtitle = "Estrai date e impegni con AILA",
                        tint = AppTheme.TintAmber,
                        icon = { AppIcons.Calendar(Modifier.size(18.dp), AppTheme.TintAmberInk) },
                        onClick = {
                            menuPost = null
                            onPrepareCalendar(p.body)
                        }
                    )
                }
                if (snapshot?.thread?.isAuthor == true || isModerator) {
                    val isPinned = snapshot?.thread?.featuredPostId == p.id
                    AilaCard {
                        AilaListRow(
                            title = if (isPinned) "Togli dall’evidenza" else "Metti in evidenza",
                            subtitle = if (isPinned) "Rimuovi la risposta evidenziata" else "Segnala come risposta utile in cima",
                            tint = AppTheme.TintViolet,
                            icon = { AppIcons.Star(Modifier.size(18.dp), AppTheme.TintVioletInk, filled = true) },
                            onClick = {
                                menuPost = null
                                launchWork {
                                    val s = snapshot!!
                                    repo.state(s.thread.id, s.thread.resolved, if (isPinned) null else p.id)
                                    refresh()
                                }
                            }
                        )
                    }
                }
                AilaCard {
                    AilaListRow(
                        title = "Segnala contenuto",
                        subtitle = "Avvisa i Rappresentanti e la Guardia",
                        tint = AppTheme.TintAmber,
                        icon = { AppIcons.Warning(Modifier.size(18.dp), AppTheme.TintAmberInk) },
                        onClick = {
                            reportPost = p
                            menuPost = null
                        }
                    )
                }
                if (canSignModeration) {
                    AilaCard {
                        AilaListRow(
                            title = "Elimina (firme)",
                            subtitle = "Richiede 2 Rappresentanti + 1 Guardia di Sicurezza",
                            tint = AppTheme.TintRed,
                            icon = { AppIcons.Trash(Modifier.size(18.dp), AppTheme.TintRedInk) },
                            onClick = {
                                deleteRequestTarget = Triple(
                                    if (p.id == threadId) "FORUM_THREAD" else "FORUM_POST",
                                    p.id,
                                    p.body.ifBlank { snapshot?.thread?.title.orEmpty() }
                                )
                                menuPost = null
                            }
                        )
                    }
                }
                if (isModerator) {
                    AilaCard {
                        AilaListRow(
                            title = if (p.hidden) "Ripristina messaggio" else "Nascondi messaggio",
                            subtitle = if (p.hidden) "Rendi di nuovo visibile alla classe" else "Oscura temporaneamente il testo",
                            tint = AppTheme.TintSlate,
                            icon = {
                                if (p.hidden) AppIcons.Eye(Modifier.size(18.dp), AppTheme.TintSlateInk)
                                else AppIcons.EyeOff(Modifier.size(18.dp), AppTheme.TintSlateInk)
                            },
                            onClick = {
                                menuPost = null
                                launchWork {
                                    repo.moderate(threadId!!, p.id, !p.hidden)
                                    refresh()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    reportPost?.let { p ->
        ReportContentDialog(
            title = "Segnala un contenuto",
            onDismiss = { reportPost = null },
            onSubmit = { reason ->
                repo.report(threadId!!, p.id, reason)
                feedbackMessage = "Segnalazione inviata ai moderatori."
            }
        )
    }

    deleteRequestTarget?.let { (scopeType, targetId, preview) ->
        RequestDeleteDialog(
            title = if (scopeType == "FORUM_THREAD") "Richiedi eliminazione domanda" else "Richiedi eliminazione messaggio",
            preview = preview,
            onDismiss = { deleteRequestTarget = null },
            onSubmit = { reason ->
                repo.requestDelete(scopeType, targetId, reason)
                reloadDeleteRequests()
                feedbackMessage = "Richiesta di eliminazione inviata (in attesa delle firme)."
            }
        )
    }

    reports?.let { items ->
        AilaBottomSheet(onDismissRequest = { reports = null }) {
            ForumSheetContainer(title = "Segnalazioni della discussione", onClose = { reports = null }) {
                if (items.isEmpty()) {
                    Text("Nessuna segnalazione ricevuta.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                } else {
                    items.forEach { r ->
                        AilaCard {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ForumStatusPill(
                                    text = if (r.status == "OPEN") "Aperta" else "Chiusa",
                                    tint = if (r.status == "OPEN") AppTheme.TintAmber else AppTheme.TintGreen,
                                    ink = if (r.status == "OPEN") AppTheme.TintAmberInk else AppTheme.TintGreenInk
                                )
                                Text(r.reason, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark)
                                if (r.status == "OPEN") {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (canSignModeration) {
                                            ForumActionPill("Elimina (firme)", AppTheme.TintRed, AppTheme.TintRedInk, true, onClick = {
                                                reports = null
                                                deleteRequestTarget = Triple(if (r.postId != null) "FORUM_POST" else "FORUM_THREAD", r.postId ?: threadId!!, r.reason)
                                            }) { AppIcons.Trash(Modifier.size(13.dp), it) }
                                        }
                                        if (isModerator) {
                                            ForumActionPill("Nascondi", AppTheme.TintSlate, AppTheme.TintSlateInk, true, onClick = {
                                                reports = null
                                                launchWork { repo.moderate(threadId!!, r.postId, true, r.id); refresh() }
                                            }) { AppIcons.EyeOff(Modifier.size(13.dp), it) }
                                        }
                                        ForumActionPill("Chiudi segnalazione", AppTheme.TintGreen, AppTheme.TintGreenInk, true, onClick = {
                                            reports = null
                                            launchWork { repo.closeReport(threadId!!, r.id); feedbackMessage = "Segnalazione chiusa." }
                                        }) { AppIcons.Check(Modifier.size(13.dp), it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    summary?.let { r ->
        AilaBottomSheet(onDismissRequest = { summary = null }) {
            ForumSheetContainer(
                title = "Riassunto della discussione",
                subtitle = "${r.provider} · ${r.generatedAt}",
                onClose = { summary = null }
            ) {
                AilaAssistantBadge("Generato da AILA")
                SelectionContainer {
                    Text(r.text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark)
                }
                if (r.sources.isNotEmpty()) {
                    Text("Messaggi di origine:", style = MaterialTheme.typography.labelLarge, color = AppTheme.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.sources.take(20).forEach { s ->
                            ForumActionPill(s.label, AppTheme.TintBlue, AppTheme.TintBlueInk, true, onClick = {
                                focusPostId = s.postId
                                summary = null
                            }) { AppIcons.ChevronRight(Modifier.size(13.dp), it) }
                        }
                    }
                }
            }
        }
    }

    if (attachmentsMenu) {
        AilaBottomSheet(onDismissRequest = { attachmentsMenu = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aggiungi alla chat",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.TextDark
                    )
                    AilaIconControl(
                        onClick = { attachmentsMenu = false },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Chiudi allegati" }
                    ) { AppIcons.Close(Modifier.size(19.dp), AppTheme.TextMuted) }
                }
                val canAttach = !busy && !uploadingAttachment && !recording && pendingAttachments.size < 5
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ForumAttachmentTile(
                        title = "Fotocamera",
                        subtitle = "Scatta foto",
                        fill = AppTheme.TintGreen,
                        ink = AppTheme.TintGreenInk,
                        enabled = canAttach,
                        onClick = {
                            attachmentsMenu = false
                            error = null
                            takePhoto()
                        },
                        modifier = Modifier.weight(1f)
                    ) { CameraGlyph(Modifier.size(24.dp), it) }
                    ForumAttachmentTile(
                        title = "Foto",
                        subtitle = "Galleria",
                        fill = AppTheme.TintBlue,
                        ink = AppTheme.TintBlueInk,
                        enabled = canAttach,
                        onClick = {
                            attachmentsMenu = false
                            error = null
                            pickPhoto()
                        },
                        modifier = Modifier.weight(1f)
                    ) { AppIcons.Photo(Modifier.size(24.dp), it) }
                    ForumAttachmentTile(
                        title = "File",
                        subtitle = "Documenti",
                        fill = AppTheme.TintViolet,
                        ink = AppTheme.TintVioletInk,
                        enabled = canAttach,
                        onClick = {
                            attachmentsMenu = false
                            error = null
                            pickFile()
                        },
                        modifier = Modifier.weight(1f)
                    ) { AppIcons.Document(Modifier.size(24.dp), it) }
                }
                AilaCard {
                    AilaListRow(
                        title = "Menziona @ai o un compagno",
                        subtitle = "Richiama AILA o un partecipante nel messaggio",
                        tint = AppTheme.TintViolet,
                        icon = { AilaAssistantMark(size = 18.dp, brush = androidx.compose.ui.graphics.SolidColor(AppTheme.TintVioletInk)) },
                        onClick = {
                            attachmentsMenu = false
                            mentionsDialog = true
                        }
                    )
                }
            }
        }
    }

    if (confirmToggleResolved) {
        snapshot?.thread?.let { th ->
            AilaAlertDialog(
                onDismissRequest = { confirmToggleResolved = false },
                title = { Text(if (th.resolved) "Riaprire la discussione?" else "Segnare come risolta?") },
                text = {
                    Text(
                        if (th.resolved) "La discussione tornerà nello stato «Aperta»."
                        else "La discussione verrà contrassegnata come «Risolta». I compagni potranno comunque continuare a rispondere."
                    )
                },
                confirmButton = {
                    AilaTextButton(enabled = !busy, onClick = {
                        confirmToggleResolved = false
                        launchWork {
                            repo.state(th.id, !th.resolved, th.featuredPostId)
                            refresh()
                        }
                    }) { Text(if (th.resolved) "Riapri" else "Segna risolta") }
                },
                dismissButton = {
                    AilaTextButton(onClick = { confirmToggleResolved = false }) { Text("Annulla") }
                }
            )
        }
    }

    consentRequest?.let { request ->
        AilaAlertDialog(
            onDismissRequest = { consentRequest = null },
            title = { Text("Attivare AILA per questa discussione?") },
            text = {
                Text("Il servizio cloud elaborerà i messaggi visibili alla classe e le fonti AILA autorizzate. ${if (request.publish) "La risposta di AILA (@ai) sarà pubblicata nella discussione." else "Il riassunto sarà visibile solo a te."} Nessun evento verrà modificato automaticamente.")
            },
            confirmButton = {
                AilaTextButton(onClick = {
                    consentRequest = null
                    launchWork {
                        val p = AppContainer.acaRepository.preferences().preferences
                        AppContainer.acaRepository.savePreferences(p.copy(cloudConsent = true))
                        activity(AssistantAgentPhase.WRITING)
                        val result = repo.ai(threadId!!, request.question, request.publish, request.replyToId)
                        if (request.publish) refresh() else summary = result
                    }
                }) { Text("Consenti e continua") }
            },
            dismissButton = {
                AilaTextButton(onClick = { consentRequest = null }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun ForumSheetContainer(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AilaIconControl(onClick = onClose, modifier = Modifier.size(36.dp).semantics { contentDescription = "Chiudi" }) {
                AppIcons.Close(Modifier.size(18.dp), AppTheme.TextMuted)
            }
        }
        content()
    }
}

@Composable
private fun ForumStatusPill(text: String, tint: Color, ink: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun ForumActionPill(
    label: String,
    tint: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) tint else AppTheme.TrackFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val c = if (enabled) ink else AppTheme.TextFaint
        icon(c)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c)
    }
}

@Composable
private fun IdentityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AppTheme.TintBlue else AppTheme.TrackFill)
            .border(1.dp, if (selected) AppTheme.PrimaryBlue.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) AppIcons.Check(Modifier.size(13.dp), AppTheme.TintBlueInk)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AppTheme.TintBlueInk else AppTheme.TextMuted
        )
    }
}

@Composable
private fun ForumCompactIconButton(
    contentDescription: String,
    tint: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) tint else AppTheme.TrackFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        icon(if (enabled) ink else AppTheme.TextFaint)
    }
}

@Composable
private fun ForumAttachmentTile(
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
    val textColor = if (AppTheme.isGlass) AppTheme.TextDark else ink
    val iconColor = if (AppTheme.isGlass) AppTheme.PrimaryBlue else ink
    Column(
        modifier
            .then(
                if (AppTheme.isGlass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(fill)
            )
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(38.dp).background(iconColor.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) { icon(iconColor) }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor)
        Text(
            subtitle,
            minLines = 2,
            style = MaterialTheme.typography.labelMedium,
            color = if (AppTheme.isGlass) AppTheme.TextMuted else ink.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun PostAuthorAvatar(author: ForumParticipantDto, isAi: Boolean, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val bg = when {
        isAi -> AppTheme.TintViolet
        author.anonymous -> AppTheme.TintSlate
        else -> AppTheme.TintBlue
    }
    val ink = when {
        isAi -> AppTheme.TintVioletInk
        author.anonymous -> AppTheme.TintSlateInk
        else -> AppTheme.TintBlueInk
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        when {
            isAi -> AilaAssistantMark(size = size * 0.52f, brush = androidx.compose.ui.graphics.SolidColor(ink))
            author.anonymous -> AppIcons.Shield(Modifier.size(size * 0.48f), ink)
            else -> {
                val initials = remember(author.label) {
                    author.label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        .take(2).joinToString("") { it.first().uppercaseChar().toString() }
                        .ifEmpty { "?" }
                }
                Text(
                    initials,
                    style = if (size <= 30.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ink
                )
            }
        }
    }
}

@Composable
private fun DotsMoreGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val r = size.minDimension * 0.11f
        val cy = size.height / 2f
        drawCircle(color, r, Offset(size.width * 0.24f, cy))
        drawCircle(color, r, Offset(size.width * 0.50f, cy))
        drawCircle(color, r, Offset(size.width * 0.76f, cy))
    }
}

@Composable
private fun ForumMicGlyph(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        // Capsula microfono
        drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.48f), strokeWidth = w * 0.28f, cap = StrokeCap.Round)
        // Arco inferiore con curva quadratica (evita drawArc su Android Skiko)
        val cup = Path().apply {
            moveTo(w * 0.22f, h * 0.46f)
            quadraticBezierTo(w * 0.24f, h * 0.72f, w * 0.50f, h * 0.72f)
            quadraticBezierTo(w * 0.76f, h * 0.72f, w * 0.78f, h * 0.46f)
        }
        drawPath(cup, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.88f), Offset(w * 0.66f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun ForumSendButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = AppTheme.isGlass
    val shape = if (glass) CircleShape else ailaMorphShape(interactionSource)
    val on by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = ailaNavigationSpring(),
        label = "forumSendButtonOn"
    )
    val fill = AppTheme.PrimaryGradient
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(
                if (glass) Modifier.ailaGlassSurface(shape)
                else Modifier.clip(shape).background(AppTheme.TrackFill)
            )
            .clip(shape)
            .drawBehind { drawRect(fill, alpha = on) }
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button) { onClick() }
            .semantics { contentDescription = "Invia risposta" },
        contentAlignment = Alignment.Center
    ) {
        AppIcons.ArrowUp(
            modifier = Modifier
                .size(20.dp)
                .ailaSelectionPop(enabled)
                .graphicsLayer { rotationZ = if (glass || AppTheme.isNothing) 0f else 90f },
            color = lerp(AppTheme.TextFaint, if (AppTheme.isNothing) AppTheme.BackgroundLight else Color.White, on)
        )
    }
}

private fun formatForumChatTime(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length < 16) return ""
    // Formati attesi: "YYYY-MM-DD HH:MM:SS" o ISO "YYYY-MM-DDTHH:MM:SSZ"
    val sepIdx = trimmed.indexOf('T').takeIf { it >= 0 } ?: trimmed.indexOf(' ')
    if (sepIdx < 0 || sepIdx + 6 > trimmed.length) return ""
    return trimmed.substring(sepIdx + 1, sepIdx + 6)
}

@Composable
internal fun LocalForumAttachment(item: ContentAttachmentDto, enabled: Boolean, onOpen: () -> Unit) {
    InlineAttachmentView(item = item, enabled = enabled, onOpen = onOpen)
}
