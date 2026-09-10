package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaIconAction
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFieldColors
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalComment
import circolareplus.domain.model.ProposalStatus
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(
    proposals: List<Proposal>,
    currentUserId: String = "",
    isRepresentative: Boolean = false,
    onVote: (String, Int) -> Unit = { _, _ -> },
    onChangeStatus: (String, ProposalStatus) -> Unit = { _, _ -> },
    onCreateProposalClick: () -> Unit = {},
    onLoadComments: suspend (String) -> List<ProposalComment> = { emptyList() },
    onAddComment: suspend (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onEdit: (String, String, String) -> Unit = { _, _, _ -> }
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

        // Colonne / Filtri per Stato con transizione animata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            circolareplus.design.AnimatedFilterChip(
                label = "Tutte",
                isSelected = selectedStatusFilter == null,
                onClick = { selectedStatusFilter = null }
            )
            circolareplus.design.AnimatedFilterChip(
                label = "Nuove",
                isSelected = selectedStatusFilter == ProposalStatus.NUOVA,
                onClick = { selectedStatusFilter = ProposalStatus.NUOVA }
            )
            circolareplus.design.AnimatedFilterChip(
                label = "In analisi",
                isSelected = selectedStatusFilter == ProposalStatus.IN_ANALISI,
                onClick = { selectedStatusFilter = ProposalStatus.IN_ANALISI }
            )
            circolareplus.design.AnimatedFilterChip(
                label = "Chiuse",
                isSelected = selectedStatusFilter == ProposalStatus.CHIUSA,
                onClick = { selectedStatusFilter = ProposalStatus.CHIUSA }
            )
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
            if (filtered.isEmpty()) {
                item {
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
            itemsIndexed(filtered) { index, proposal ->
                ProposalCardItem(
                    modifier = Modifier.ailaAppear(index),
                    proposal = proposal,
                    canDelete = isRepresentative || proposal.authorId == currentUserId,
                    // Chi può modificare: l'autore la propria proposta, il Rappresentante
                    // qualunque. Prima era riservata al Rappresentante anche sulle proprie, e
                    // per correggere un refuso bisognava cancellare e riscrivere, perdendo voti
                    // e commenti già raccolti.
                    canEdit = isRepresentative || proposal.authorId == currentUserId,
                    isRepresentative = isRepresentative,
                    onVote = onVote,
                    onEdit = onEdit,
                    onChangeStatus = onChangeStatus,
                    onLoadComments = onLoadComments,
                    onAddComment = onAddComment,
                    onDelete = onDelete
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
    canDelete: Boolean = false,
    canEdit: Boolean = false,
    onVote: (String, Int) -> Unit,
    onChangeStatus: (String, ProposalStatus) -> Unit,
    onLoadComments: suspend (String) -> List<ProposalComment> = { emptyList() },
    onAddComment: suspend (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onEdit: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // Aggiornamento ottimistico dei voti: l'UI si aggiorna subito, la richiesta va in background
    var optimisticUpvotes by remember { mutableStateOf(proposal.upvotes) }
    var optimisticDownvotes by remember { mutableStateOf(proposal.downvotes) }
    var userVote by remember { mutableStateOf(0) } // -1 downvote, 0 niente, 1 upvote
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
            message = "L'azione non può essere annullata.",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete(proposal.id)
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (proposal.status) {
                        ProposalStatus.NUOVA -> AppTheme.PrimaryBlue
                        ProposalStatus.IN_ANALISI -> AppTheme.TintAmberInk
                        ProposalStatus.CHIUSA -> AppTheme.TextMuted
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = AppTheme.Space8, vertical = 2.dp)
                    ) {
                        Text(
                            text = proposal.status.name.replace("_", " "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = if (proposal.isAnonymous) "Anonimo" else proposal.authorName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (proposal.isEdited) {
                        // Pastiglia invece del testo fra parentesi: si vede senza doverla
                        // leggere, e dice quello che serve sapere — il testo non è più quello
                        // pubblicato all'inizio.
                        Text(
                            text = "Modificato",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TintAmberInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AppTheme.TintAmber)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(modifier = Modifier.width(AppTheme.Space4))
                    }
                    if (canEdit) {
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                            AppIcons.Pencil(modifier = Modifier.size(15.dp), color = AppTheme.TintSlateInk)
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                            AppIcons.Trash(modifier = Modifier.size(15.dp), color = AppTheme.TintRedInk)
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

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            // Barra interazioni (Upvote, Downvote, Commenti)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voti e commenti come pillole, non emoji sparse: prima erano tre coppie
                // "emoji + numero" allineate nel vuoto, senza area di tocco riconoscibile.
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    AilaIconAction(
                        label = "$optimisticUpvotes",
                        tint = AppTheme.TintGreen,
                        ink = AppTheme.TintGreenInk,
                        onClick = {
                            // Aggiornamento ottimistico: incrementa subito, poi sincronizza col server
                            if (userVote != 1) {
                                optimisticUpvotes++
                                if (userVote == -1) optimisticDownvotes--
                                userVote = 1
                                onVote(proposal.id, 1)
                            }
                        }
                    ) { ink -> AppIcons.ThumbUp(modifier = Modifier.size(15.dp), color = ink) }
                    AilaIconAction(
                        label = "$optimisticDownvotes",
                        tint = AppTheme.TintRed,
                        ink = AppTheme.TintRedInk,
                        onClick = {
                            // Aggiornamento ottimistico: decrementa subito, poi sincronizza col server
                            if (userVote != -1) {
                                optimisticDownvotes++
                                if (userVote == 1) optimisticUpvotes--
                                userVote = -1
                                onVote(proposal.id, -1)
                            }
                        }
                    ) { ink -> AppIcons.ThumbDown(modifier = Modifier.size(15.dp), color = ink) }
                    AilaIconAction(
                        label = "${proposal.commentsCount}",
                        tint = AppTheme.TintSlate,
                        ink = AppTheme.TintSlateInk,
                        selected = isExpanded,
                        onClick = { isExpanded = !isExpanded }
                    ) { ink -> AppIcons.ChatBubble(modifier = Modifier.size(15.dp), color = ink) }
                }

                // Tasto azione rapida per Rappresentante di Classe
                if (isRepresentative && proposal.status == ProposalStatus.NUOVA) {
                    TextButton(
                        onClick = { onChangeStatus(proposal.id, ProposalStatus.IN_ANALISI) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "Metti in analisi \u2192", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isRepresentative && proposal.status == ProposalStatus.IN_ANALISI) {
                    TextButton(
                        onClick = { onChangeStatus(proposal.id, ProposalStatus.CHIUSA) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "Chiudi proposta \u2192", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sezione commenti: si apre/chiude con la freccetta sul contatore sopra.
            if (isExpanded) {
                var comments by remember(proposal.id) { mutableStateOf<List<ProposalComment>>(emptyList()) }
                var isLoadingComments by remember(proposal.id) { mutableStateOf(true) }
                var loadError by remember(proposal.id) { mutableStateOf(false) }
                var commentInput by remember(proposal.id) { mutableStateOf("") }
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
                        text = "Caricamento commenti\u2026",
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
                                    text = comment.authorName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.TextDark
                                )
                                Text(
                                    text = comment.content,
                                    fontSize = 13.sp,
                                    color = AppTheme.TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppTheme.Space8))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Scrivi un commento...", fontSize = 13.sp) },
                        singleLine = true,
                        enabled = !isSending,
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                        colors = ailaFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    TextButton(
                        enabled = commentInput.isNotBlank() && !isSending,
                        onClick = {
                            val text = commentInput.trim()
                            commentsScope.launch {
                                isSending = true
                                try {
                                    onAddComment(proposal.id, text)
                                    commentInput = ""
                                    reloadComments()
                                } catch (e: Exception) {
                                    // Il testo resta nel campo per poter riprovare l'invio.
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    ) {
                        Text("Invia", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
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
                    text = "Ai compagni comparirà la dicitura \u201cModificato\u201d: i voti e i " +
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
