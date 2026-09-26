package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
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
 * L'ordine si cambia trascinando la maniglia a sinistra di ogni opzione (o tenendo premuta la
 * riga), oppure con le frecce su/giù, che restano per chi preferisce i tocchi singoli. La
 * maniglia prende il trascinamento subito, quindi non si confonde con lo scorrimento della lista.
 *
 * [showingHistory]: la stessa schermata mostra i sondaggi aperti o, nello Storico, quelli chiusi.
 */
@Composable
fun RankingPollsScreen(
    polls: List<RankingPollDto>,
    showingHistory: Boolean = false,
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

    if (polls.isEmpty() && showingHistory) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AilaEmptyState(
                title = "Nessun sondaggio chiuso",
                message = "Quando un sondaggio a ordinamento viene chiuso, la classifica finale resta qui.",
                icon = { AppIcons.History(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
            )
        }
        return
    }
    if (polls.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AilaEmptyState(
                title = "Nessun sondaggio aperto",
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
                totalStudents = poll.totalStudents ?: totalStudents,
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
    val canEdit = !poll.isClosed && isEditing && poll.isTarget

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
            if (poll.audienceUserIds != null) {
                Text(
                    text = if (poll.isTarget) "Rivolto solo ad alcune persone, tra cui tu"
                    else "Rivolto solo ad alcune persone: non puoi rispondere",
                    fontSize = 11.sp,
                    color = AppTheme.TextFaint
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            if (canEdit) {
                Text(
                    text = "Trascina le opzioni (o usa le frecce): in cima quella che preferisci.",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                DraggableRankingList(
                    order = draft,
                    labels = labels,
                    enabled = !isSubmitting,
                    onOrderChange = { draft = it }
                )
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
                Text(
                    text = if (poll.isClosed) "Elimina: cancella il sondaggio e la sua classifica."
                    else "Chiudi: nessuno può più rispondere, la classifica diventa definitiva e passa nello Storico. " +
                        "Elimina: cancella il sondaggio con tutte le risposte.",
                    fontSize = 11.sp,
                    color = AppTheme.TextFaint,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
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

/**
 * La propria classifica in compilazione, riordinabile col trascinamento.
 *
 * Mentre si trascina, la riga segue il dito (translationY) e scambia posto con la vicina appena
 * la supera per metà: l'ordine si aggiorna subito, quindi le posizioni 1, 2, 3 restano sempre
 * vere. Le frecce restano per chi preferisce i tocchi singoli (e per l'accessibilità).
 */
@Composable
private fun DraggableRankingList(
    order: List<String>,
    labels: Map<String, String>,
    enabled: Boolean,
    onOrderChange: (List<String>) -> Unit
) {
    val spacing = 6.dp
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    // Il callback del gesto è creato una volta: legge sempre l'ordine più recente da qui.
    val currentOrder by rememberUpdatedState(order)
    val currentOnChange by rememberUpdatedState(onOrderChange)

    fun onDrag(id: String, delta: Float) {
        dragOffset += delta
        val list = currentOrder
        val index = list.indexOf(id)
        if (index < 0) return
        if (dragOffset > 0 && index < list.lastIndex) {
            val next = list[index + 1]
            val threshold = ((rowHeights[next] ?: 0) + spacingPx) / 2f
            if (dragOffset > threshold) {
                currentOnChange(list.swapped(index, index + 1))
                dragOffset -= (rowHeights[next] ?: 0) + spacingPx
            }
        } else if (dragOffset < 0 && index > 0) {
            val prev = list[index - 1]
            val threshold = ((rowHeights[prev] ?: 0) + spacingPx) / 2f
            if (-dragOffset > threshold) {
                currentOnChange(list.swapped(index, index - 1))
                dragOffset += (rowHeights[prev] ?: 0) + spacingPx
            }
        }
    }

    fun endDrag() {
        draggedId = null
        dragOffset = 0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, optionId ->
            key(optionId) {
                val isDragged = draggedId == optionId
                val dragModifier = if (enabled) {
                    Modifier.pointerInput(optionId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedId = optionId; dragOffset = 0f },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                            onDrag = { change, amount -> change.consume(); onDrag(optionId, amount.y) }
                        )
                    }
                } else Modifier
                val handleModifier = if (enabled) {
                    Modifier.pointerInput(optionId) {
                        detectDragGestures(
                            onDragStart = { draggedId = optionId; dragOffset = 0f },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                            onDrag = { change, amount -> change.consume(); onDrag(optionId, amount.y) }
                        )
                    }
                } else Modifier
                RankingDraftRow(
                    position = index + 1,
                    label = labels[optionId] ?: "",
                    canMoveUp = index > 0 && enabled,
                    canMoveDown = index < order.lastIndex && enabled,
                    onMoveUp = { onOrderChange(order.swapped(index, index - 1)) },
                    onMoveDown = { onOrderChange(order.swapped(index, index + 1)) },
                    isDragged = isDragged,
                    handleModifier = handleModifier,
                    modifier = Modifier
                        .onSizeChanged { rowHeights[optionId] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffset
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 8.dp.toPx()
                                shape = RoundedCornerShape(AppTheme.SmallElementRadius)
                                clip = true
                            }
                        }
                        .then(dragModifier)
                )
            }
        }
    }
}

/** Una riga della propria classifica in compilazione: maniglia, posizione, opzione, frecce su/giù. */
@Composable
private fun RankingDraftRow(
    position: Int,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isDragged: Boolean = false,
    handleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(if (isDragged) AppTheme.TintBlue else AppTheme.TintSlate)
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Maniglia: tre righe orizzontali, area di tocco generosa.
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 40.dp)
                .then(handleModifier),
            contentAlignment = Alignment.Center
        ) {
            DragHandleIcon(color = if (isDragged) AppTheme.PrimaryBlue else AppTheme.TextFaint)
        }
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

@Composable
private fun DragHandleIcon(color: Color) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
        val stroke = 2.dp.toPx()
        for (i in 0..2) {
            val y = stroke / 2 + i * (size.height - stroke) / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun List<String>.swapped(i: Int, j: Int): List<String> =
    toMutableList().also { list ->
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
