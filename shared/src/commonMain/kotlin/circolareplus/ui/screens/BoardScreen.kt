package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaIconAction
import circolareplus.design.AilaCard
import circolareplus.design.AilaDestructiveButton
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AilaSwitch
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFieldColors
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalComment
import circolareplus.domain.model.ProposalOutcome
import circolareplus.domain.model.ProposalStatus
import circolareplus.domain.model.UnlockRequest
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(
    proposals: List<Proposal>,
    currentUserId: String = "",
    isRepresentative: Boolean = false,
    // Rappresentanti e Guardia di Sicurezza: i tre ruoli che firmano lo svelamento di un autore
    // anonimo (2 Rappresentanti + 1 Guardia). Il Rappresentante da solo non basta.
    canModerateIdentity: Boolean = isRepresentative,
    unlockRequests: List<UnlockRequest> = emptyList(),
    onVote: (String, Int) -> Unit = { _, _ -> },
    onChangeStatus: (String, ProposalStatus, ProposalOutcome?) -> Unit = { _, _, _ -> },
    onCreateProposalClick: () -> Unit = {},
    onLoadComments: suspend (String) -> List<ProposalComment> = { emptyList() },
    onAddComment: suspend (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
    onEdit: (String, String, String) -> Unit = { _, _, _ -> },
    onRequestUnlock: suspend (proposalId: String, commentId: String?, reason: String) -> Unit = { _, _, _ -> },
    onApproveUnlock: (String) -> Unit = {},
    onRejectUnlock: (String) -> Unit = {}
) {
    var selectedStatusFilter by remember { mutableStateOf<ProposalStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .padding(horizontal = AppTheme.Space16)
            .padding(top = AppTheme.Space16)
    ) {
        // Come in CircolariScreen: niente titolo grande, lo dà già il selettore
        // Circolari/Bacheca in cima alla tab "Classe". Resta la riga di contesto + l'azione.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Iniziative & idee della classe",
                fontSize = 12.sp,
                color = AppTheme.TextFaint,
                modifier = Modifier.weight(1f)
            )

            AilaPrimaryButton(text = "+ Proponi", onClick = onCreateProposalClick)
        }

        Spacer(modifier = Modifier.height(AppTheme.Space12))

        // Colonne / Filtri per Stato: stesso pill scorrevole di Sondaggio/Storico, generalizzato a
        // chip di larghezza diversa (vedi AilaSlidingChipRow). "Chiuse" raccoglie sia le accettate
        // sia le rifiutate: l'esito si legge sulla card.
        val statusFilterOptions = remember { listOf<ProposalStatus?>(null, ProposalStatus.NUOVA, ProposalStatus.IN_ANALISI, ProposalStatus.CHIUSA) }
        val statusFilterLabels = remember { listOf("Tutte", "Nuove", "In analisi", "Chiuse") }
        circolareplus.design.AilaSlidingChipRow(
            selectedIndex = statusFilterOptions.indexOf(selectedStatusFilter),
            itemCount = statusFilterOptions.size,
            scrollable = false,
            modifier = Modifier.fillMaxWidth()
        ) { chipModifier ->
            statusFilterOptions.forEachIndexed { index, status ->
                circolareplus.design.AnimatedFilterChip(
                    label = statusFilterLabels[index],
                    isSelected = selectedStatusFilter == status,
                    onClick = { selectedStatusFilter = status },
                    drawSelectionBackground = false,
                    modifier = chipModifier(index)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space16))

        val filtered = proposals.filter {
            selectedStatusFilter == null || it.status == selectedStatusFilter
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(bottom = AppTheme.Space24),
            modifier = Modifier.fillMaxSize()
        ) {
            if (canModerateIdentity && unlockRequests.isNotEmpty()) {
                item(key = "unlock-requests") {
                    UnlockRequestsPanel(
                        requests = unlockRequests,
                        onApprove = onApproveUnlock,
                        onReject = onRejectUnlock
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    AilaEmptyState(
                        title = if (proposals.isEmpty()) "Bacheca vuota" else "Nessuna proposta qui",
                        message = if (proposals.isEmpty())
                            "Hai un'idea per la classe? Proponila: i compagni possono votarla e commentarla."
                        else
                            "Nessuna proposta in questo stato. Prova a togliere il filtro.",
                        actionLabel = if (proposals.isEmpty()) "+ Proponi qualcosa" else null,
                        onAction = if (proposals.isEmpty()) onCreateProposalClick else null,
                        icon = { AppIcons.ChatBubble(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                    )
                }
            }
            // La chiave è l'id: senza, lo stato locale di ogni card (voto, commenti aperti) restava
            // legato alla POSIZIONE e passava alla proposta che le subentrava quando una si
            // spostava di filtro — da qui i voti "doppi" dopo aver cambiato stato a una proposta.
            itemsIndexed(filtered, key = { _, proposal -> proposal.id }) { index, proposal ->
                ProposalCardItem(
                    modifier = Modifier.ailaAppear(index),
                    proposal = proposal,
                    currentUserId = currentUserId,
                    canDelete = isRepresentative || proposal.authorId == currentUserId,
                    // Chi può modificare: l'autore la propria proposta, il Rappresentante
                    // qualunque. Prima era riservata al Rappresentante anche sulle proprie, e
                    // per correggere un refuso bisognava cancellare e riscrivere, perdendo voti
                    // e commenti già raccolti.
                    canEdit = isRepresentative || proposal.authorId == currentUserId,
                    isRepresentative = isRepresentative,
                    canModerateIdentity = canModerateIdentity,
                    onVote = onVote,
                    onEdit = onEdit,
                    onChangeStatus = onChangeStatus,
                    onLoadComments = onLoadComments,
                    onAddComment = onAddComment,
                    onDelete = onDelete,
                    onRequestUnlock = onRequestUnlock
                )
            }
        }
    }
}

@Composable
fun ProposalCardItem(
    proposal: Proposal,
    isRepresentative: Boolean,
    modifier: Modifier = Modifier,
    currentUserId: String = "",
    canDelete: Boolean = false,
    canEdit: Boolean = false,
    canModerateIdentity: Boolean = false,
    onVote: (String, Int) -> Unit,
    onChangeStatus: (String, ProposalStatus, ProposalOutcome?) -> Unit,
    onLoadComments: suspend (String) -> List<ProposalComment> = { emptyList() },
    onAddComment: suspend (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
    onEdit: (String, String, String) -> Unit = { _, _, _ -> },
    onRequestUnlock: suspend (String, String?, String) -> Unit = { _, _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // Il bersaglio di una richiesta di svelamento aperta da questa card: null = nessuna finestra,
    // altrimenti l'id del commento (o "" per l'autore della proposta).
    var unlockTargetCommentId by remember { mutableStateOf<String?>(null) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    // Il voto parte da quello che il server sa (myVote), non da zero. Prima partiva sempre da 0:
    // dopo un ricaricamento, o tornando sulla scheda, un voto già dato risultava "non dato" e
    // votare di nuovo lo contava un'altra volta a schermo — sembravano due voti invece di uno.
    // Le chiavi fanno ripartire lo stato quando il server manda numeri nuovi.
    var userVote by remember(proposal.id, proposal.myVote, proposal.upvotes, proposal.downvotes) {
        mutableStateOf(proposal.myVote)
    }
    // I conteggi mostrati sono quelli del server, corretti per la differenza fra il voto che il
    // server conosceva e quello scelto adesso: così si aggiornano subito, senza aspettare.
    val shownUp = proposal.upvotes - (if (proposal.myVote == 1) 1 else 0) + (if (userVote == 1) 1 else 0)
    val shownDown = proposal.downvotes - (if (proposal.myVote == -1) 1 else 0) + (if (userVote == -1) 1 else 0)
    // Una proposta chiusa (accettata o rifiutata) ha già la sua decisione: non si vota più.
    val votingOpen = proposal.status != ProposalStatus.CHIUSA
    val commentsScope = rememberCoroutineScope()

    if (showEditDialog) {
        EditProposalDialog(
            initialTitle = proposal.title,
            initialDescription = proposal.description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newTitle, newDescription ->
                showEditDialog = false
                onEdit(proposal.id, newTitle, newDescription)
            }
        )
    }

    if (showDeleteConfirm) {
        circolareplus.design.AilaConfirmDialog(
            title = "Eliminare la proposta?",
            message = "“${proposal.title}” verrà eliminata insieme ai suoi voti e ai " +
                "commenti. L'azione non può essere annullata.",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete(proposal.id)
            }
        )
    }

    if (showUnlockDialog) {
        UnlockRequestDialog(
            isComment = unlockTargetCommentId != null,
            onDismiss = { showUnlockDialog = false },
            onSubmit = { reason -> onRequestUnlock(proposal.id, unlockTargetCommentId, reason) },
            onDone = { showUnlockDialog = false }
        )
    }

    AilaCard(modifier = modifier) {
        // La card cresce e si ritira con un'animazione quando si aprono i commenti: prima
        // comparivano di colpo, facendo saltare tutta la lista sotto.
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(AppTheme.Space16)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val (statusLabel, statusColor) = when (proposal.status) {
                        ProposalStatus.NUOVA -> "NUOVA" to AppTheme.PrimaryBlue
                        ProposalStatus.IN_ANALISI -> "IN ANALISI" to AppTheme.TintAmberInk
                        ProposalStatus.CHIUSA -> when (proposal.outcome) {
                            ProposalOutcome.ACCETTATA -> "ACCETTATA" to AppTheme.TintGreenInk
                            ProposalOutcome.RIFIUTATA -> "RIFIUTATA" to AppTheme.TintRedInk
                            null -> "CHIUSA" to AppTheme.TextMuted
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = AppTheme.Space8, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = authorLabel(
                            isAnonymous = proposal.isAnonymous,
                            isMine = proposal.authorId != null && proposal.authorId == currentUserId,
                            revealed = proposal.identityRevealed,
                            name = proposal.authorName
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (proposal.isEdited) {
                        // Pastiglia invece del testo fra parentesi: si vede senza doverla
                        // leggere, e dice quello che serve sapere — il testo non è più quello
                        // pubblicato all'inizio.
                        Text(
                            text = "Modificato",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TintAmberInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AppTheme.TintAmber)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(modifier = Modifier.width(AppTheme.Space4))
                    }
                    // 44dp l'uno: erano 28dp con un'icona da 15, praticamente impossibili da
                    // centrare col pollice, e uno stava attaccato all'altro (matita e cestino).
                    if (canEdit) {
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(44.dp)) {
                            AppIcons.Pencil(modifier = Modifier.size(21.dp), color = AppTheme.TintSlateInk)
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(44.dp)) {
                            AppIcons.Trash(modifier = Modifier.size(21.dp), color = AppTheme.TintRedInk)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            Text(
                text = proposal.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )

            Spacer(modifier = Modifier.height(AppTheme.Space4))

            Text(
                text = proposal.description,
                fontSize = 14.sp,
                color = AppTheme.TextMuted
            )

            // Svelamento dell'autore anonimo: lo chiedono i tre ruoli che poi lo firmano.
            if (canModerateIdentity && proposal.isAnonymous && !proposal.identityRevealed) {
                Spacer(modifier = Modifier.height(AppTheme.Space4))
                when (proposal.unlockRequestStatus) {
                    "PENDING" -> UnlockStatusNote("Svelamento dell'autore in attesa di approvazione")
                    "APPROVED" -> UnlockStatusNote("Autore svelato ai firmatari")
                    else -> UnlockLink(
                        text = if (proposal.unlockRequestStatus == "REJECTED") "Richiesta rifiutata — riproponi" else "Svela l'autore",
                        onClick = {
                            unlockTargetCommentId = null
                            showUnlockDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            // Barra interazioni (Upvote, Downvote, Commenti)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voti e commenti come pillole, non emoji sparse: prima erano tre coppie
                // "emoji + numero" allineate nel vuoto, senza area di tocco riconoscibile.
                // Toccare di nuovo il voto già dato lo toglie, come nelle app di voto.
                AilaIconAction(
                    label = "$shownUp",
                    tint = AppTheme.TintGreen,
                    ink = AppTheme.TintGreenInk,
                    selected = userVote == 1,
                    enabled = votingOpen,
                    onClick = {
                        val next = if (userVote == 1) 0 else 1
                        userVote = next
                        onVote(proposal.id, next)
                    }
                ) { ink -> AppIcons.ThumbUp(modifier = Modifier.size(19.dp), color = ink) }
                AilaIconAction(
                    label = "$shownDown",
                    tint = AppTheme.TintRed,
                    ink = AppTheme.TintRedInk,
                    selected = userVote == -1,
                    enabled = votingOpen,
                    onClick = {
                        val next = if (userVote == -1) 0 else -1
                        userVote = next
                        onVote(proposal.id, next)
                    }
                ) { ink -> AppIcons.ThumbDown(modifier = Modifier.size(19.dp), color = ink) }
                AilaIconAction(
                    label = "${proposal.commentsCount}",
                    tint = AppTheme.TintSlate,
                    ink = AppTheme.TintSlateInk,
                    selected = isExpanded,
                    onClick = { isExpanded = !isExpanded }
                ) { ink -> AppIcons.ChatBubble(modifier = Modifier.size(19.dp), color = ink) }
                if (!votingOpen) {
                    Text(
                        text = "Votazione chiusa",
                        fontSize = 12.sp,
                        color = AppTheme.TextFaint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Gestione della proposta, solo Rappresentante. Prima c'era un unico link piccolo
            // "Metti in analisi" / "Chiudi": ora si decide direttamente. L'analisi è per le
            // proposte complesse, per questo è un'azione secondaria e non il passaggio obbligato.
            if (isRepresentative) {
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                when (proposal.status) {
                    ProposalStatus.NUOVA, ProposalStatus.IN_ANALISI -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                            AilaPrimaryButton(
                                text = "Accetta",
                                onClick = { onChangeStatus(proposal.id, ProposalStatus.CHIUSA, ProposalOutcome.ACCETTATA) },
                                large = true,
                                modifier = Modifier.weight(1f)
                            )
                            AilaDestructiveButton(
                                text = "Rifiuta",
                                onClick = { onChangeStatus(proposal.id, ProposalStatus.CHIUSA, ProposalOutcome.RIFIUTATA) },
                                large = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (proposal.status == ProposalStatus.NUOVA) {
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            AilaSecondaryButton(
                                text = "Metti in analisi (proposta complessa)",
                                onClick = { onChangeStatus(proposal.id, ProposalStatus.IN_ANALISI, null) },
                                large = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    ProposalStatus.CHIUSA -> AilaSecondaryButton(
                        text = "Riapri la proposta",
                        onClick = { onChangeStatus(proposal.id, ProposalStatus.NUOVA, null) },
                        large = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Sezione commenti: si apre/chiude con la freccetta sul contatore sopra.
            if (isExpanded) {
                var comments by remember(proposal.id) { mutableStateOf<List<ProposalComment>>(emptyList()) }
                var isLoadingComments by remember(proposal.id) { mutableStateOf(true) }
                var loadError by remember(proposal.id) { mutableStateOf(false) }
                var commentInput by remember(proposal.id) { mutableStateOf("") }
                var commentAnonymous by remember(proposal.id) { mutableStateOf(false) }
                var isSending by remember(proposal.id) { mutableStateOf(false) }

                suspend fun reloadComments() {
                    isLoadingComments = true
                    loadError = false
                    comments = try {
                        onLoadComments(proposal.id)
                    } catch (e: Exception) {
                        loadError = true
                        emptyList()
                    }
                    isLoadingComments = false
                }

                LaunchedEffect(proposal.id) { reloadComments() }

                Spacer(modifier = Modifier.height(AppTheme.Space12))
                HorizontalDivider(color = AppTheme.Hairline)
                Spacer(modifier = Modifier.height(AppTheme.Space8))

                when {
                    isLoadingComments -> Text(
                        text = "Caricamento commenti…",
                        fontSize = 12.sp,
                        color = AppTheme.TextFaint
                    )
                    loadError -> Text(
                        text = "Impossibile caricare i commenti.",
                        fontSize = 12.sp,
                        color = AppTheme.TintRedInk
                    )
                    comments.isEmpty() -> Text(
                        text = "Nessun commento. Scrivi il primo.",
                        fontSize = 12.sp,
                        color = AppTheme.TextFaint
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                        comments.forEach { comment ->
                            Column {
                                Text(
                                    text = authorLabel(
                                        isAnonymous = comment.isAnonymous,
                                        isMine = comment.isMine,
                                        revealed = comment.identityRevealed,
                                        name = comment.authorName
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.TextDark
                                )
                                Text(
                                    text = comment.content,
                                    fontSize = 13.sp,
                                    color = AppTheme.TextMuted
                                )
                                if (canModerateIdentity && comment.isAnonymous && !comment.identityRevealed && !comment.isMine) {
                                    when (comment.unlockRequestStatus) {
                                        "PENDING" -> UnlockStatusNote("Svelamento in attesa di approvazione")
                                        "APPROVED" -> UnlockStatusNote("Autore svelato ai firmatari")
                                        else -> UnlockLink(
                                            text = if (comment.unlockRequestStatus == "REJECTED") "Richiesta rifiutata — riproponi" else "Svela l'autore",
                                            onClick = {
                                                unlockTargetCommentId = comment.id
                                                showUnlockDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppTheme.Space8))

                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text("Scrivi un commento...", fontSize = 14.sp) },
                    maxLines = 4,
                    enabled = !isSending,
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                    colors = ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Anonimo per singolo commento, come per le proposte: una proposta firmata può
                    // avere anche un commento che non lo è, e viceversa.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable(enabled = !isSending) { commentAnonymous = !commentAnonymous }
                    ) {
                        AilaSwitch(
                            checked = commentAnonymous,
                            onCheckedChange = { commentAnonymous = it },
                            enabled = !isSending
                        )
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Text(
                            text = "Anonimo",
                            fontSize = 14.sp,
                            color = AppTheme.TextDark
                        )
                    }
                    AilaPrimaryButton(
                        text = "Invia",
                        enabled = commentInput.isNotBlank() && !isSending,
                        large = true,
                        onClick = {
                            val text = commentInput.trim()
                            val anonymous = commentAnonymous
                            commentsScope.launch {
                                isSending = true
                                try {
                                    onAddComment(proposal.id, text, anonymous)
                                    commentInput = ""
                                    reloadComments()
                                } catch (e: Exception) {
                                    // Il testo resta nel campo per poter riprovare l'invio.
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** "Anonimo", "Anonimo (tu)", "Anonimo · Mario Rossi" (svelato) o il nome dell'autore. */
private fun authorLabel(isAnonymous: Boolean, isMine: Boolean, revealed: Boolean, name: String): String = when {
    !isAnonymous -> name
    revealed -> "Anonimo · $name (svelato)"
    isMine -> "Anonimo (tu)"
    else -> "Anonimo"
}

/** Riga cliccabile alta almeno 44dp, per le azioni testuali dentro una card. */
@Composable
private fun UnlockLink(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .clickable(onClick = onClick)
            .padding(end = AppTheme.Space12)
    ) {
        AppIcons.Lock(modifier = Modifier.size(16.dp), color = AppTheme.PrimaryBlue)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.PrimaryBlue)
    }
}

@Composable
private fun UnlockStatusNote(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 32.dp)
    ) {
        AppIcons.Lock(modifier = Modifier.size(15.dp), color = AppTheme.TintAmberInk)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.TintAmberInk)
    }
}

/**
 * Richieste di svelamento in attesa, in cima alla bacheca dei tre firmatari. Ognuno approva dal
 * proprio account: il quorum è 2 Rappresentanti + 1 Guardia di Sicurezza, quindi una persona
 * sola non può svelare nessuno, nemmeno aprendo lei la richiesta.
 */
@Composable
private fun UnlockRequestsPanel(
    requests: List<UnlockRequest>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    AilaCard(containerColor = AppTheme.TintAmber) {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcons.Shield(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "Svelamento anonimato · ${requests.size} in attesa",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TintAmberInk
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Servono 2 Rappresentanti e 1 Guardia di Sicurezza. L'autore lo vedono solo i tre che firmano.",
                fontSize = 12.sp,
                color = AppTheme.TextMuted,
                lineHeight = 17.sp
            )

            requests.forEach { request ->
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                HorizontalDivider(color = AppTheme.Hairline)
                Spacer(modifier = Modifier.height(AppTheme.Space12))

                Text(
                    text = if (request.commentId != null) "Commento su “${request.proposalTitle}”" else request.proposalTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
                if (request.commentExcerpt != null) {
                    Text(
                        text = "“${request.commentExcerpt}”",
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Motivo (${request.requestedByName}): ${request.reason}",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rappresentanti ${request.representativeApprovals}/${request.representativesNeeded}" +
                        " · Guardia ${request.guardApprovals}/${request.guardsNeeded}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.TintAmberInk
                )

                Spacer(modifier = Modifier.height(AppTheme.Space8))
                if (request.approvedByMe) {
                    Text(
                        text = "Hai già approvato. Aspetti le altre firme.",
                        fontSize = 13.sp,
                        color = AppTheme.TextFaint
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    AilaSecondaryButton(
                        text = "Ritira la richiesta",
                        onClick = { onReject(request.id) },
                        large = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                        AilaPrimaryButton(
                            text = "Approva",
                            onClick = { onApprove(request.id) },
                            large = true,
                            modifier = Modifier.weight(1f)
                        )
                        AilaDestructiveButton(
                            text = "Rifiuta",
                            onClick = { onReject(request.id) },
                            large = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Finestra per aprire una richiesta di svelamento. La motivazione è obbligatoria (10 caratteri
 * come sul server) e finisce nell'audit: "in caso di gravi violazioni" (specifica, sez. 3.3) vuol
 * dire che la ragione deve restare scritta.
 */
@Composable
private fun UnlockRequestDialog(
    isComment: Boolean,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> Unit,
    onDone: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isValid = reason.trim().length >= 10

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        // Larga quasi quanto lo schermo sul telefono, non oltre MaxDialogWidth su tablet/iPad.
        modifier = Modifier
            .widthIn(max = circolareplus.design.MaxDialogWidth)
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space20),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = {
            Text(
                text = if (isComment) "Svelare l'autore del commento?" else "Svelare l'autore?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
        },
        text = {
            Column {
                Text(
                    text = "Serve l'approvazione di 2 Rappresentanti e di 1 Guardia di Sicurezza: la tua " +
                        "conta come la prima. Finché non firmano tutti, nessuno vede chi è. " +
                        "Da usare solo per gravi violazioni.",
                    fontSize = 14.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (almeno 10 caratteri)") },
                    minLines = 3,
                    enabled = !isSending,
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                    colors = ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    Text(text = error!!, fontSize = 13.sp, color = AppTheme.TintRedInk)
                }
            }
        },
        confirmButton = {
            AilaPrimaryButton(
                text = "Invia richiesta",
                enabled = isValid && !isSending,
                large = true,
                onClick = {
                    scope.launch {
                        isSending = true
                        error = null
                        try {
                            onSubmit(reason.trim())
                            onDone()
                        } catch (e: Exception) {
                            error = e.message ?: "Richiesta non riuscita."
                        } finally {
                            isSending = false
                        }
                    }
                }
            )
        },
        dismissButton = {
            AilaSecondaryButton(text = "Annulla", onClick = onDismiss, large = true)
        }
    )
}

/**
 * Finestra di modifica di una proposta.
 *
 * Prima non c'era: chi si accorgeva di un errore nel testo poteva solo cancellare la proposta e
 * riscriverla, perdendo i voti e i commenti che aveva già raccolto. La categoria non si tocca
 * qui apposta — cambiarla sposterebbe la proposta sotto un altro filtro senza che chi l'ha
 * votata se ne accorga.
 */
@Composable
private fun EditProposalDialog(
    initialTitle: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    val isChanged = title.trim() != initialTitle || description.trim() != initialDescription
    val isValid = title.isNotBlank() && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica la proposta") },
        text = {
            Column {
                Text(
                    text = "Ai compagni comparirà la dicitura “Modificato”: i voti e i " +
                        "commenti già raccolti restano.",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo") },
                    singleLine = true,
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                    colors = ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrizione") },
                    minLines = 3,
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                    colors = ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isChanged && isValid,
                onClick = { onConfirm(title.trim(), description.trim()) }
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
