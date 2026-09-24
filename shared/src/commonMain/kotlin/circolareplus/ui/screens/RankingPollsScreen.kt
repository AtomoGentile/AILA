package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.remote.dto.RankingPollDto
import circolareplus.data.remote.dto.RankingPollResultDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaConfirmDialog
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaAppear

/**
 * Sondaggi a ordinamento: invece di votare un'opzione sola, ognuno mette in ordine tutte le
 * opzioni (es. "dove andiamo in gita?"). La classe ottiene una classifica a punti: con N opzioni
 * la prima di ogni classifica vale N-1 punti, l'ultima 0 (metodo Borda).
 *
 * I risultati si vedono solo dopo aver inviato la propria classifica o a sondaggio chiuso:
 * vederli prima spingerebbe a mettersi dietro alla maggioranza. Non c'è nessun calcolo da far
 * partire: il server li ricalcola a ogni lettura.
 *
 * L'ordine si cambia con le frecce su/giù invece del trascinamento: dentro una lista che scorre
 * il trascinamento si confonde facilmente con lo scorrimento, e le frecce funzionano uguali su
 * Android e iOS.
 */
@Composable
fun RankingPollsScreen(
    polls: List<RankingPollDto>,
    totalStudents: Int,
    isRepresentative: Boolean,
    submittingPollId: String?,
    onSubmitRanking: (pollId: String, optionIds: List<String>) -> Unit,
    onClosePoll: (String) -> Unit,
    onDeletePoll: (String) -> Unit,
    onCreatePoll: () -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingCloseId by remember { mutableStateOf<String?>(null) }

    pendingDeleteId?.let { id ->
        AilaConfirmDialog(
            title = "Eliminare il sondaggio?",
            message = "Le classifiche inviate andranno perse. L'azione non può essere annullata.",
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                pendingDeleteId = null
                onDeletePoll(id)
            }
        )
    }
    pendingCloseId?.let { id ->
        AilaConfirmDialog(
            title = "Chiudere il sondaggio?",
            message = "Nessuno potrà più inviare o cambiare la propria classifica, e i risultati diventano visibili a tutti.",
            onDismiss = { pendingCloseId = null },
            onConfirm = {
                pendingCloseId = null
                onClosePoll(id)
            },
            confirmLabel = "Chiudi",
            isDestructive = false
        )
    }

    if (polls.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AilaEmptyState(
                title = "Nessun sondaggio a ordinamento",
                message = if (isRepresentative)
                    "Proponi delle opzioni e fai mettere in ordine alla classe quelle che preferisce."
                else
                    "Quando il Rappresentante ne apre uno, lo trovi qui.",
                actionLabel = if (isRepresentative) "+ Nuovo sondaggio" else null,
                onAction = if (isRepresentative) onCreatePoll else null,
                icon = { AppIcons.Sliders(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight),
        contentPadding = PaddingValues(AppTheme.Space16),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
    ) {
        itemsIndexed(polls, key = { _, poll -> poll.id }) { index, poll ->
            RankingPollCard(
                poll = poll,
                totalStudents = totalStudents,
                isRepresentative = isRepresentative,
                isSubmitting = submittingPollId == poll.id,
                onSubmit = { order -> onSubmitRanking(poll.id, order) },
                onClose = { pendingCloseId = poll.id },
                onDelete = { pendingDeleteId = poll.id },
                modifier = Modifier.ailaAppear(index)
            )
        }
    }
}

@Composable
private fun RankingPollCard(
    poll: RankingPollDto,
    totalStudents: Int,
    isRepresentative: Boolean,
    isSubmitting: Boolean,
    onSubmit: (List<String>) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = remember(poll.options) { poll.options.associate { it.id to it.label } }
    // Ordine di partenza: la propria classifica se c'è, altrimenti l'ordine del Rappresentante.
    // Ripartono da capo quando dal server arriva una classifica nuova (dopo l'invio).
    var draft by remember(poll.id, poll.myRanking) {
        mutableStateOf(poll.myRanking ?: poll.options.map { it.id })
    }
    var isEditing by remember(poll.id, poll.myRanking) { mutableStateOf(poll.myRanking == null) }
    val canEdit = !poll.isClosed && isEditing

    AilaCard(modifier = modifier) {
        Column(modifier = Modifier.padding(AppTheme.Space16).animateContentSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = poll.question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                StatusChip(isClosed = poll.isClosed)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    totalStudents > 0 && poll.voterCount >= totalStudents -> "Hanno risposto tutti"
                    totalStudents > 0 -> "${poll.voterCount} di $totalStudents hanno risposto"
                    poll.voterCount == 1 -> "1 risposta"
                    else -> "${poll.voterCount} risposte"
                },
                fontSize = 12.sp,
                color = AppTheme.TextMuted
            )

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            if (canEdit) {
                Text(
                    text = "Metti in cima quella che preferisci.",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.forEachIndexed { index, optionId ->
                        key(optionId) {
                            RankingDraftRow(
                                position = index + 1,
                                label = labels[optionId] ?: "",
                                canMoveUp = index > 0 && !isSubmitting,
                                canMoveDown = index < draft.lastIndex && !isSubmitting,
                                onMoveUp = { draft = draft.swapped(index, index - 1) },
                                onMoveDown = { draft = draft.swapped(index, index + 1) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    if (poll.myRanking != null) {
                        AilaSecondaryButton(
                            text = "Annulla",
                            onClick = {
                                draft = poll.myRanking ?: draft
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AilaPrimaryButton(
                        text = if (isSubmitting) "Invio…" else "Invia la mia classifica",
                        onClick = { onSubmit(draft) },
                        enabled = !isSubmitting,
                        fillMaxWidth = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                val results = poll.results
                if (results != null) {
                    RankingResults(results = results, maxPoints = poll.maxPoints, myFirstId = poll.myRanking?.firstOrNull())
                } else {
                    // Il server manda sempre i risultati a chi ha risposto o a sondaggio chiuso:
                    // qui si arriva solo in casi limite, e lo si dice invece di lasciare la card vuota.
                    Text(text = "Risultati non disponibili.", fontSize = 12.sp, color = AppTheme.TextMuted)
                }
                if (!poll.isClosed && poll.myRanking != null) {
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                    AilaSecondaryButton(
                        text = "Cambia la mia classifica",
                        onClick = { isEditing = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isRepresentative) {
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8, Alignment.End)
                ) {
                    if (!poll.isClosed) {
                        AilaSecondaryButton(
                            text = "Chiudi",
                            onClick = onClose,
                            compact = true,
                            icon = { tint -> AppIcons.Lock(modifier = Modifier.size(13.dp), color = tint) }
                        )
                    }
                    AilaSecondaryButton(
                        text = "Elimina",
                        onClick = onDelete,
                        compact = true,
                        icon = { _ -> AppIcons.Trash(modifier = Modifier.size(13.dp), color = AppTheme.TintRedInk) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(isClosed: Boolean) {
    Text(
        text = if (isClosed) "Chiuso" else "Aperto",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (isClosed) AppTheme.TintSlateInk else AppTheme.TintGreenInk,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isClosed) AppTheme.TintSlate else AppTheme.TintGreen)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Una riga della propria classifica in compilazione: posizione, opzione, frecce su/giù. */
@Composable
private fun RankingDraftRow(
    position: Int,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintSlate)
            .padding(start = AppTheme.Space12, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PositionBadge(position = position, highlighted = position == 1)
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.TextDark,
            modifier = Modifier.weight(1f)
        )
        MoveButton(up = true, enabled = canMoveUp, onClick = onMoveUp)
        MoveButton(up = false, enabled = canMoveDown, onClick = onMoveDown)
    }
}

@Composable
private fun MoveButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcons.ChevronRight(
            modifier = Modifier.size(20.dp).rotate(if (up) -90f else 90f),
            color = if (enabled) AppTheme.TextDark else AppTheme.Hairline
        )
    }
}

@Composable
private fun PositionBadge(position: Int, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (highlighted) AppTheme.PrimaryBlue else AppTheme.SurfaceWhite),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$position",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Color.White else AppTheme.TextDark
        )
    }
}

/** Classifica della classe: posizione, opzione, barra proporzionale ai punti. */
@Composable
private fun RankingResults(results: List<RankingPollResultDto>, maxPoints: Int, myFirstId: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        results.forEachIndexed { index, result ->
            // A pari punti (e pari primi posti) la posizione è la stessa: niente vincitori a caso.
            val position = results.indexOfFirst {
                it.points == result.points && it.firstPlaces == result.firstPlaces
            } + 1
            val fraction = if (maxPoints > 0) result.points.toFloat() / maxPoints else 0f
            val animated by animateFloatAsState(
                targetValue = fraction.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 480),
                label = "rankingResultBar"
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PositionBadge(position = position, highlighted = position == 1 && result.points > 0)
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = result.label,
                        fontSize = 14.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color = AppTheme.TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (result.optionId == myFirstId) {
                        AppIcons.Star(modifier = Modifier.size(13.dp), color = AppTheme.TintAmberInk)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (result.points == 1) "1 pt" else "${result.points} pt",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .padding(start = 34.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(AppTheme.TintSlate)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animated)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (position == 1) AppTheme.PrimaryBlue else AppTheme.TextFaint)
                    )
                }
            }
        }
        if (myFirstId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcons.Star(modifier = Modifier.size(11.dp), color = AppTheme.TintAmberInk)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "il tuo primo posto", fontSize = 11.sp, color = AppTheme.TextFaint)
            }
        }
    }
}

private fun List<String>.swapped(i: Int, j: Int): List<String> =
    toMutableList().also { list ->
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
