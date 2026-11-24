package circolareplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    canSignModeration: Boolean = isModerator
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
            threadId != null -> {
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
            AilaBackBar(
                title = when {
                    focusPostId != null -> "Conversazione"
                    threadId != null -> snapshot?.thread?.title ?: "Revisione tra pere"
                    else -> "Revisione tra pere"
                },
                onBackClick = goBack,
                action = {
                    if (threadId != null && rulesAccepted) {
                        AilaIconButton("Preferenze della discussione", { notificationDialog = true }, size = 40.dp) { tint ->
                            AppIcons.Bell(Modifier.size(19.dp), tint)
                        }
                    }
                }
            )
        } else if (threadId != null && rulesAccepted) {
            // Intestazione compatta dentro Classe > Pere: evita la doppia barra alta.
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
                    size = 40.dp
                ) { tint -> AppIcons.ChevronLeft(Modifier.size(18.dp), tint) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            text = if (th.resolved) "Risolta · aperta alle risposte" else "Aperta · Revisione tra pere",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (th.resolved) AppTheme.TintGreenInk else AppTheme.TextMuted
                        )
                    }
                }
                AilaIconButton("Preferenze notifiche", { notificationDialog = true }, size = 40.dp) { tint ->
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
                            AilaCard(onClick = { threadId = t.id }) {
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

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "thread_header") {
                        AilaCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ForumStatusPill(
                                        text = if (snap.thread.resolved) "Risolta" else "Aperta",
                                        tint = if (snap.thread.resolved) AppTheme.TintGreen else AppTheme.TintBlue,
                                        ink = if (snap.thread.resolved) AppTheme.TintGreenInk else AppTheme.TintBlueInk
                                    )
                                    Text(
                                        text = if (snap.thread.resolved) "Puoi continuare a rispondere" else "Discussione aperta alla classe",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.TextMuted
                                    )
                                }
                                Text(snap.thread.title, style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                                if (snap.thread.needsResolution) {
                                    Text(
                                        "Sei ancora qui? Se hai trovato la risposta, puoi segnare la discussione come risolta.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.PrimaryBlue
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ForumActionPill(
                                        label = "Riassumi",
                                        tint = AppTheme.TintViolet,
                                        ink = AppTheme.TintVioletInk,
                                        enabled = !busy,
                                        onClick = { askAi(ForumAiRequestDto()) }
                                    ) { AilaAssistantMark(size = 14.dp, brush = androidx.compose.ui.graphics.SolidColor(it)) }

                                    if (snap.thread.isAuthor || isModerator) {
                                        ForumActionPill(
                                            label = if (snap.thread.resolved) "Riapri" else "Segna risolta",
                                            tint = if (snap.thread.resolved) AppTheme.TintSlate else AppTheme.TintGreen,
                                            ink = if (snap.thread.resolved) AppTheme.TintSlateInk else AppTheme.TintGreenInk,
                                            enabled = !busy,
                                            onClick = { launchWork { repo.state(snap.thread.id, !snap.thread.resolved, snap.thread.featuredPostId); refresh() } }
                                        ) { AppIcons.Check(Modifier.size(14.dp), it) }
                                    }

                                    if (canSignModeration) {
                                        ForumActionPill(
                                            label = "Segnalazioni",
                                            tint = AppTheme.TintAmber,
                                            ink = AppTheme.TintAmberInk,
                                            enabled = !busy,
                                            onClick = { launchWork { reports = repo.reports(snap.thread.id) } }
                                        ) { AppIcons.Warning(Modifier.size(14.dp), it) }

                                        ForumActionPill(
                                            label = "Elimina (firme)",
                                            tint = AppTheme.TintRed,
                                            ink = AppTheme.TintRedInk,
                                            enabled = !busy,
                                            onClick = { deleteRequestTarget = Triple("FORUM_THREAD", snap.thread.id, snap.thread.title) }
                                        ) { AppIcons.Trash(Modifier.size(14.dp), it) }
                                    }
                                }
                                if (focusPostId != null) {
                                    ForumActionPill(
                                        label = "Torna a tutta la discussione",
                                        tint = AppTheme.TintBlue,
                                        ink = AppTheme.TintBlueInk,
                                        enabled = true,
                                        onClick = { focusPostId = null }
                                    ) { AppIcons.ChevronLeft(Modifier.size(14.dp), it) }
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
                        AilaCard {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { if (post.id != snap.thread.id) focusPostId = post.id },
                                        onLongClick = { menuPost = post }
                                    )
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PostAuthorAvatar(post.author, post.isAi)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = post.author.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (post.isAi) AppTheme.TintVioletInk else AppTheme.TextDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (post.author.isMe) {
                                                ForumStatusPill("Tu", AppTheme.TintBlue, AppTheme.TintBlueInk)
                                            } else if (post.author.isOp) {
                                                ForumStatusPill("OP", AppTheme.TintSlate, AppTheme.TintSlateInk)
                                            } else if (post.isAi) {
                                                ForumStatusPill("AI", AppTheme.TintViolet, AppTheme.TintVioletInk)
                                            }
                                        }
                                        if (post.isAi) {
                                            Text(
                                                "Risposta assistita · ${post.provider.orEmpty()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.TextMuted
                                            )
                                        }
                                    }
                                    AilaIconControl(
                                        onClick = { menuPost = post },
                                        modifier = Modifier.size(40.dp).semantics { contentDescription = "Azioni messaggio" }
                                    ) {
                                        DotsMoreGlyph(Modifier.size(18.dp), AppTheme.TextMuted)
                                    }
                                }

                                if (isFeatured) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(AppTheme.TintBlue)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        AppIcons.Star(Modifier.size(12.dp), AppTheme.TintBlueInk, filled = true)
                                        Text("Risposta in evidenza", color = AppTheme.TintBlueInk, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                var shown = post.body
                                post.mentions.filter { it.isMe }.forEach { shown = shown.replace("@${it.label}", "@Tu") }
                                if (shown.isNotBlank()) {
                                    Text(
                                        text = shown,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (post.hidden) AppTheme.TextMuted else AppTheme.TextDark
                                    )
                                }

                                post.attachments.forEach { a ->
                                    LocalForumAttachment(a, !busy) { onOpenAttachment(a) }
                                }

                                val replies = snap.posts.count { it.parentId == post.id }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ForumActionPill(
                                        label = "Rispondi",
                                        tint = AppTheme.TintSlate,
                                        ink = AppTheme.TextDark,
                                        enabled = !busy && !post.hidden,
                                        onClick = {
                                            replyTo = post
                                            focusPostId = if (post.id == snap.thread.id) null else post.id
                                        }
                                    ) { AppIcons.ChatBubble(Modifier.size(13.dp), it) }

                                    if (post.id != snap.thread.id && replies > 0) {
                                        ForumActionPill(
                                            label = "$replies ${if (replies == 1) "risposta" else "risposte"}",
                                            tint = AppTheme.TintBlue,
                                            ink = AppTheme.TintBlueInk,
                                            enabled = true,
                                            onClick = { focusPostId = post.id }
                                        ) { AppIcons.ChevronRight(Modifier.size(13.dp), it) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Composer ancorato in basso in stile AILA Assistant (niente più OutlinedTextField grezzo o overlap).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.BackgroundLight)
                ) {
                    HorizontalDivider(color = AppTheme.Hairline)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        replyTo?.let { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppTheme.TintBlue)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Rispondi a ${p.author.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppTheme.TintBlueInk,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { replyTo = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcons.Close(Modifier.size(14.dp), AppTheme.TintBlueInk)
                                }
                            }
                        }

                        if (pendingAttachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                pendingAttachments.forEach { a ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(AppTheme.CardSurface)
                                            .border(1.dp, AppTheme.Hairline, RoundedCornerShape(14.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isImg = a.mime.startsWith("image/")
                                        if (isImg) AppIcons.Photo(Modifier.size(16.dp), AppTheme.PrimaryBlue)
                                        else AppIcons.Document(Modifier.size(16.dp), AppTheme.TintVioletInk)
                                        Text(
                                            a.fileName,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AppTheme.TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    pendingAttachments = pendingAttachments.filterNot { it.id == a.id }
                                                    scope.launch {
                                                        try { AppContainer.acaRepository.removeAttachment(a.id) } catch (_: Exception) {}
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppIcons.Close(Modifier.size(14.dp), AppTheme.TextMuted)
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
                        } else {
                            Text(
                                "Partecipi come ${snap.myIdentity.label} in questa discussione.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        if (recording) {
                            VoiceRecordingPanel(
                                onStop = recorder.stop,
                                onCancel = recorder.cancel,
                                subtitle = "Il vocale verrà allegato alla risposta."
                            )
                        } else {
                            val canSend = !busy && !uploadingAttachment && rulesAccepted && (body.isNotBlank() || pendingAttachments.isNotEmpty())
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(AppTheme.CardSurface)
                                    .border(1.dp, AppTheme.FieldOutline.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AilaPillTextField(
                                    value = body,
                                    onValueChange = { body = it },
                                    placeholder = "Rispondi… (@ai per chiedere ad AILA)",
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = Color.Transparent,
                                    maxLines = 4,
                                    integrated = true,
                                    enabled = !busy
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AilaIconButton(
                                        contentDescription = "Allega file",
                                        onClick = pickFile,
                                        enabled = !busy && !uploadingAttachment && pendingAttachments.size < 5,
                                        size = 38.dp
                                    ) { tint -> AppIcons.Plus(Modifier.size(18.dp), tint) }

                                    AilaIconButton(
                                        contentDescription = "Allega foto",
                                        onClick = pickPhoto,
                                        enabled = !busy && !uploadingAttachment && pendingAttachments.size < 5,
                                        size = 38.dp
                                    ) { tint -> AppIcons.Photo(Modifier.size(18.dp), tint) }

                                    AilaIconButton(
                                        contentDescription = "Registra vocale",
                                        onClick = recorder.start,
                                        enabled = !busy && !uploadingAttachment && pendingAttachments.size < 5,
                                        size = 38.dp
                                    ) { tint -> ForumMicGlyph(tint) }

                                    AilaIconButton(
                                        contentDescription = "Menziona @ai o un compagno",
                                        onClick = { mentionsDialog = true },
                                        enabled = !busy,
                                        size = 38.dp
                                    ) { tint ->
                                        Text("@", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
                                    }

                                    Spacer(Modifier.weight(1f))

                                    ForumSendButton(
                                        enabled = canSend,
                                        onClick = {
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
                                    )
                                }
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
                    threadId = created.thread.id
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
private fun PostAuthorAvatar(author: ForumParticipantDto, isAi: Boolean) {
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
            .size(34.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        when {
            isAi -> AilaAssistantMark(size = 17.dp, brush = androidx.compose.ui.graphics.SolidColor(ink))
            author.anonymous -> AppIcons.Shield(Modifier.size(16.dp), ink)
            else -> {
                val initials = remember(author.label) {
                    author.label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        .take(2).joinToString("") { it.first().uppercaseChar().toString() }
                        .ifEmpty { "?" }
                }
                Text(initials, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
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
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) AppTheme.PrimaryBlue else AppTheme.TrackFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Invia risposta" },
        contentAlignment = Alignment.Center
    ) {
        AppIcons.ArrowUp(
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer { rotationZ = if (AppTheme.isGlass || AppTheme.isNothing) 0f else 90f },
            color = if (enabled) (if (AppTheme.isNothing) AppTheme.BackgroundLight else Color.White) else AppTheme.TextFaint
        )
    }
}

@Composable
internal fun LocalForumAttachment(item: ContentAttachmentDto, enabled: Boolean, onOpen: () -> Unit) {
    InlineAttachmentView(item = item, enabled = enabled, onOpen = onOpen)
}
