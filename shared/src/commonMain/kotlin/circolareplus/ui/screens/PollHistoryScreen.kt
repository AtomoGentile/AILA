package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.remote.dto.PollAssignmentDto
import circolareplus.data.remote.dto.PollSummaryDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconTile
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme

/**
 * Storico dei sondaggi interrogazioni (solo Rappresentante): prima non esisteva alcun modo di
 * vedere i sondaggi passati, i loro risultati (assegnazioni calcolate) o di eliminarne uno.
 * `PollsRepository.getAssignments()`/`runAssignments()`/`deletePoll()` esistevano lato client e
 * backend ma non erano richiamati da nessuna schermata.
 */
@Composable
fun PollHistoryScreen(
    polls: List<PollSummaryDto>,
    expandedPollId: String?,
    isLoadingResults: Boolean,
    resultsError: String?,
    assignments: List<PollAssignmentDto>,
    onToggleResults: (String) -> Unit,
    onDelete: (String) -> Unit
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
        val completedPolls = polls.filter { it.isCalculated && it.submittedCount == it.totalStudents && it.totalStudents > 0 }
        if (completedPolls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                circolareplus.design.AilaEmptyState(
                    title = "Nessuno storico",
                    message = "I sondaggi con tutte le risposte raccolte e calcolati restano qui.",
                    icon = { circolareplus.design.AppIcons.Check(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
                contentPadding = PaddingValues(AppTheme.Space16),
                modifier = Modifier.fillMaxSize()
            ) {
                items(completedPolls, key = { it.id }) { poll ->
                    AilaCard {
                        Column(modifier = Modifier.padding(AppTheme.Space16)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AilaIconTile(tint = AppTheme.TintBlue, size = 40.dp) {
                                        AppIcons.Check(modifier = Modifier.size(18.dp), color = AppTheme.TintBlueInk)
                                    }
                                    Spacer(modifier = Modifier.width(AppTheme.Space12))
                                    Column {
                                        Text(poll.subject, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (poll.isPublished) "Pubblicato" else "Non pubblicato",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (poll.isPublished) AppTheme.TintGreenInk else AppTheme.TextFaint,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (poll.isPublished) AppTheme.TintGreen else AppTheme.TintSlate)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Row {
                                    TextButton(onClick = { onToggleResults(poll.id) }) {
                                        Text(if (expandedPollId == poll.id) "Chiudi" else "Risultati")
                                    }
                                    IconButton(onClick = { pendingDeleteId = poll.id }, modifier = Modifier.size(36.dp)) {
                                        AppIcons.Trash(modifier = Modifier.size(16.dp), color = AppTheme.TintRedInk)
                                    }
                                }
                            }

                            if (expandedPollId == poll.id) {
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                                HorizontalDivider(color = AppTheme.Hairline)
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                                when {
                                    isLoadingResults -> Text(
                                        "Calcolo risultati…",
                                        fontSize = 12.sp,
                                        color = AppTheme.TextFaint
                                    )
                                    resultsError != null -> Text(
                                        resultsError,
                                        fontSize = 12.sp,
                                        color = AppTheme.TintRedInk
                                    )
                                    assignments.isEmpty() -> Text(
                                        "Nessuna assegnazione (nessun voto ricevuto ancora).",
                                        fontSize = 12.sp,
                                        color = AppTheme.TextFaint
                                    )
                                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        assignments.forEach { a ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(a.studentName ?: a.studentId, fontSize = 13.sp, color = AppTheme.TextDark)
                                                Text(a.slotDate ?: "", fontSize = 12.sp, color = AppTheme.TextMuted)
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
    }
}
