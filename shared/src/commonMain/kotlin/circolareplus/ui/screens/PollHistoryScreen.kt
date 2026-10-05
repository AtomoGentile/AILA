package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.dto.PollAssignmentDto
import circolareplus.data.remote.dto.PollSummaryDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconTile
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaAppear
import circolareplus.design.ailaPressable
import circolareplus.util.formatDayMonth
import circolareplus.util.weekdayName

/**
 * Storico dei sondaggi interrogazioni (visibile a tutta la classe; elimina e calendario solo al Rappresentante): prima non esisteva alcun modo di
 * vedere i sondaggi passati, i loro risultati (assegnazioni calcolate) o di eliminarne uno.
 * `PollsRepository.getAssignments()`/`runAssignments()`/`deletePoll()` esistevano lato client e
 * backend ma non erano richiamati da nessuna schermata.
 *
 * Riordinata per somigliare al resto del linguaggio grafico AILA (comparsa a cascata, chip di
 * riepilogo, righe risultato con iniziali invece di semplice testo affiancato) e per ospitare
 * l'azione "Aggiungi al calendario", che prima non esisteva: i risultati calcolati restavano
 * visibili solo qui, senza alcun modo di riportarli sul calendario dello studente.
 */
@Composable
fun PollHistoryScreen(
    polls: List<PollSummaryDto>,
    /** Solo il Rappresentante elimina i sondaggi e porta le date sul calendario; il resto della classe legge. */
    canManage: Boolean = true,
    expandedPollId: String?,
    isLoadingResults: Boolean,
    resultsError: String?,
    assignments: List<PollAssignmentDto>,
    onToggleResults: (String) -> Unit,
    onDelete: (String) -> Unit,
    isAddingToCalendar: Boolean = false,
    calendarAddMessage: String? = null,
    calendarAddIsError: Boolean = false,
    onAddToCalendar: (PollSummaryDto, List<PollAssignmentDto>) -> Unit = { _, _ -> }
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    if (pendingDeleteId != null) {
        circolareplus.design.AilaConfirmDialog(
            title = "Eliminare il sondaggio?",
            message = "Voti e risultati collegati andranno persi. L'azione non può essere annullata.",
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                onDelete(pendingDeleteId!!)
                pendingDeleteId = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        // isCalculated implica già che il sondaggio è stato chiuso/calcolato (automaticamente
        // o tramite pulsante "Calcola risultati" con force=true), indipendentemente da quanti
        // studenti avessero effettivamente inviato: usare submittedCount == totalStudents qui
        // escludeva i sondaggi chiusi in anticipo, che restavano bloccati (né attivi né in storico).
        val completedPolls = polls.filter { it.isCalculated && it.totalStudents > 0 }
        if (completedPolls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                circolareplus.design.AilaEmptyState(
                    title = "Nessuno storico",
                    message = "I sondaggi con tutte le risposte raccolte e calcolati restano qui.",
                    icon = { AppIcons.Check(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        } else {
            circolareplus.design.AilaAdaptiveCardList(
                items = completedPolls,
                key = { poll -> poll.id },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    top = AppTheme.Space16,
                    bottom = AppTheme.Space16 + circolareplus.design.LocalBottomBarPadding.current
                )
            ) { index, poll ->
                val isExpanded = expandedPollId == poll.id
                AilaCard(modifier = Modifier.ailaAppear(index)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AilaIconTile(tint = AppTheme.TintBlue, size = 44.dp) {
                                    AppIcons.Check(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk)
                                }
                                Spacer(modifier = Modifier.width(AppTheme.Space12))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        poll.subject,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AppTheme.TextDark,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(AppTheme.Space4))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusPill(
                                            text = if (poll.isPublished) "Pubblicato" else "Non pubblicato",
                                            tint = if (poll.isPublished) AppTheme.TintGreen else AppTheme.TintSlate,
                                            ink = if (poll.isPublished) AppTheme.TintGreenInk else AppTheme.TextFaint
                                        )
                                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                                        StatusPill(
                                            text = "${poll.totalStudents} student${if (poll.totalStudents == 1) "e" else "i"}",
                                            tint = AppTheme.TintSlate,
                                            ink = AppTheme.TintSlateInk
                                        )
                                    }
                                }
                            }
                            // 44dp e un nome: a 36dp senza descrizione era un cestino muto e
                            // difficile da centrare. Il nome dice anche quale sondaggio si elimina.
                            if (canManage) IconButton(
                                onClick = { pendingDeleteId = poll.id },
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics { contentDescription = "Elimina sondaggio ${poll.subject}" }
                            ) {
                                AppIcons.Trash(modifier = Modifier.size(16.dp), color = AppTheme.TintRedInk)
                            }
                        }

                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                        TextButton(
                            onClick = { onToggleResults(poll.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isExpanded) "Chiudi risultati" else "Vedi risultati")
                            Spacer(modifier = Modifier.width(AppTheme.Space4))
                            AppIcons.ChevronRight(
                                modifier = Modifier.size(14.dp).rotate(if (isExpanded) 90f else 0f),
                                color = AppTheme.PrimaryBlue
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(AppTheme.Space4))
                            HorizontalDivider(color = AppTheme.Hairline)
                            Spacer(modifier = Modifier.height(AppTheme.Space12))
                            when {
                                isLoadingResults -> Text(
                                    "Calcolo risultati…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.TextFaint
                                )
                                resultsError != null -> Text(
                                    resultsError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.TintRedInk
                                )
                                assignments.isEmpty() -> Text(
                                    "Nessuna assegnazione (nessun voto ricevuto ancora).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.TextFaint
                                )
                                else -> Column {
                                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                        assignments.forEach { a ->
                                            AssignmentRow(a)
                                        }
                                    }

                                    if (canManage) {
                                        Spacer(modifier = Modifier.height(AppTheme.Space16))
                                        HorizontalDivider(color = AppTheme.Hairline)
                                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                                        CalendarSyncAction(
                                            isLoading = isAddingToCalendar,
                                            message = calendarAddMessage,
                                            isError = calendarAddIsError,
                                            onClick = { onAddToCalendar(poll, assignments) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

/** Pillola di stato compatta ("Pubblicato", "12 studenti"): sostituisce il singolo badge fisso. */
@Composable
private fun StatusPill(text: String, tint: Color, ink: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        modifier = Modifier
            // ButtonCornerRadius e' gia' una capsula (100dp): stessa forma dei pulsanti.
            .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
            .background(tint)
            .padding(horizontal = AppTheme.Space8, vertical = AppTheme.Space4)
    )
}

/** Riga di un'assegnazione: iniziali dello studente, nome e data leggibile invece dell'ISO grezzo. */
@Composable
private fun AssignmentRow(assignment: PollAssignmentDto) {
    val name = assignment.studentName ?: assignment.studentId
    val initials = name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    val dateLabel = assignment.slotDate?.let { iso ->
        val weekday = weekdayName(iso).take(3)
        val dayMonth = formatDayMonth(iso)
        if (weekday.isNotEmpty() && dayMonth.isNotEmpty()) "$weekday $dayMonth" else iso
    } ?: ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(AppTheme.TintViolet),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifEmpty { "?" },
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.TintVioletInk
            )
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.TextDark,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (dateLabel.isNotEmpty()) {
            StatusPill(text = dateLabel, tint = AppTheme.TintSlate, ink = AppTheme.TintSlateInk)
        }
    }
}

/**
 * Azione "Aggiungi al calendario": prende le date già calcolate dal sondaggio (data, studente,
 * materia) e le trasforma in eventi calendario visibili solo allo studente coinvolto, senza dover
 * ricopiarle a mano una per una. Il badge "AI" segue la stessa convenzione usata per gli eventi
 * generati dal parsing delle circolari: qui non c'è un modello da interrogare (i dati sono già
 * strutturati), ma il risultato finito è comunque generato automaticamente e non da una persona.
 */
@Composable
private fun CalendarSyncAction(
    isLoading: Boolean,
    message: String?,
    isError: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .background(AppTheme.TintViolet)
                .ailaPressable(enabled = !isLoading, pressedScale = 0.98f) { onClick() }
                .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcons.Sparkle(modifier = Modifier.size(16.dp), color = AppTheme.TintVioletInk)
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            Text(
                text = if (isLoading) "Aggiunta in corso…" else "Aggiungi tutte le date al calendario",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.TintVioletInk,
                modifier = Modifier.weight(1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AppTheme.TintVioletInk
                )
            }
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) AppTheme.TintRedInk else AppTheme.TintGreenInk
            )
        }
    }
}
