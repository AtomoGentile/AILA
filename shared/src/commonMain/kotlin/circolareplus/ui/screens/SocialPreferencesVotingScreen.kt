package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaAdaptiveCardList
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaProgressBar
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.LocalBottomBarPadding
import circolareplus.domain.model.PublishedSeatMap
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User
import circolareplus.util.formatDayMonth
import kotlinx.coroutines.CancellationException

private const val MAX_EXTREME_VOTES = 2

@Composable
fun SocialPreferencesVotingScreen(
    classmates: List<User>,
    currentVotes: Map<String, SocialPreferenceScore>,
    onVoteChanged: (targetStudentId: String, score: SocialPreferenceScore) -> Unit,
    loadHistory: suspend () -> List<PublishedSeatMap> = { emptyList() },
    studentsMap: Map<String, User> = classmates.associateBy { it.id },
    currentUserId: String? = null,
    onBack: () -> Unit,
    onSubmitVotes: () -> Unit
) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        SeatMapHistoryDialog(
            studentsMap = studentsMap,
            loadHistory = loadHistory,
            canManage = false,
            onDeleteMap = {},
            onClearHistory = {},
            onHistoryChanged = {},
            onDismiss = { showHistory = false }
        )
    }
    var publishedMaps by remember { mutableStateOf<List<PublishedSeatMap>>(emptyList()) }
    var historyUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            publishedMaps = loadHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            historyUnavailable = true
        }
    }

    val remainingPlusTwo = MAX_EXTREME_VOTES -
        currentVotes.values.count { it == SocialPreferenceScore.STRONG_AFFINITY }
    val remainingMinusTwo = MAX_EXTREME_VOTES -
        currentVotes.values.count { it == SocialPreferenceScore.STRONG_REJECTION }
    val votedCount = classmates.count { it.id in currentVotes }
    val isComplete = votedCount == classmates.size

    Column(
        modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)
    ) {
        AilaBackBar(title = "Preferenze di posto", onBackClick = onBack) {
            AilaIconButton(contentDescription = "Storico dei posti", onClick = { showHistory = true }) { tint ->
                AppIcons.History(modifier = Modifier.size(19.dp), color = tint)
            }
        }
        AilaAdaptiveCardList(
            items = classmates,
            key = { it.id },
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space4,
                bottom = AppTheme.Space12
            ),
            header = {
                VotingSummary(
                    votedCount = votedCount,
                    total = classmates.size,
                    remainingPlusTwo = remainingPlusTwo.coerceAtLeast(0),
                    remainingMinusTwo = remainingMinusTwo.coerceAtLeast(0),
                    lastSeat = currentUserId?.let { publishedMaps.lastSeatLabel(it) },
                    historyUnavailable = historyUnavailable
                )
            }
        ) { _, classmate ->
            val currentScore = currentVotes[classmate.id]
            AilaCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AppTheme.Space12),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${classmate.firstName} ${classmate.lastName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = AppTheme.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentScore == null) {
                                Text("Da votare", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                            }
                        }
                        publishedMaps.lastSeatLabel(classmate.id)?.let { lastSeat ->
                            Text(lastSeat, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space4)
                    ) {
                        for (score in SocialPreferenceScore.entries) {
                            val selected = score == currentScore
                            val eligible = when (score) {
                                SocialPreferenceScore.STRONG_AFFINITY -> remainingPlusTwo > 0
                                SocialPreferenceScore.STRONG_REJECTION -> remainingMinusTwo > 0
                                else -> true
                            }
                            SocialScoreButton(
                                score = score,
                                isSelected = selected,
                                enabled = selected || eligible,
                                onClick = { onVoteChanged(classmate.id, score) }
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = AppTheme.FieldOutline)
        Box(
            modifier = Modifier.fillMaxWidth().padding(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space12,
                bottom = AppTheme.Space12 + LocalBottomBarPadding.current
            )
        ) {
            AilaPrimaryButton(
                text = "Salva preferenze",
                onClick = onSubmitVotes,
                enabled = isComplete,
                fillMaxWidth = true,
                large = true
            )
        }
    }
}

@Composable
private fun VotingSummary(
    votedCount: Int,
    total: Int,
    remainingPlusTwo: Int,
    remainingMinusTwo: Int,
    lastSeat: String?,
    historyUnavailable: Boolean
) {
    AilaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (votedCount == total) "Votazione completa" else "Vota ogni compagno",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.TextDark,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$votedCount / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.TextDark
                )
            }
            AilaProgressBar(progress = if (total == 0) 0f else votedCount.toFloat() / total)
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                VoteBudget("+2", remainingPlusTwo, AppTheme.TintGreen, AppTheme.TintGreenInk, Modifier.weight(1f))
                VoteBudget("−2", remainingMinusTwo, AppTheme.TintRed, AppTheme.TintRedInk, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcons.Lock(modifier = Modifier.size(14.dp), color = AppTheme.TextMuted)
                Text("Voti riservati", style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
            }
            when {
                historyUnavailable -> Text(
                    "Storico non disponibile. Riprova dall’icona in alto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
                lastSeat != null -> Text(
                    "Il tuo ultimo posto · $lastSeat",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
            }
        }
    }
}

@Composable
private fun VoteBudget(label: String, remaining: Int, background: Color, textColor: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier.background(background, shape).padding(horizontal = AppTheme.Space8, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
        Text("$remaining di $MAX_EXTREME_VOTES rimasti", style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun RowScope.SocialScoreButton(
    score: SocialPreferenceScore,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val (label, description, selectedColor) = when (score) {
        SocialPreferenceScore.STRONG_AFFINITY -> Triple("+2", "Preferenza massima", AppTheme.PollGreen)
        SocialPreferenceScore.MEDIUM_AFFINITY -> Triple("+1", "Gradimento", AppTheme.PrefPlusOne)
        SocialPreferenceScore.NEUTRAL -> Triple("0", "Indifferente", AppTheme.PrefNeutral)
        SocialPreferenceScore.MILD_REJECTION -> Triple("−1", "Preferirei di no", AppTheme.PrefMinusOne)
        SocialPreferenceScore.STRONG_REJECTION -> Triple("−2", "Rifiuto assoluto", AppTheme.PrefMinusTwo)
    }
    val shape = RoundedCornerShape(if (AppTheme.isNothing) 8.dp else 14.dp)
    val borderColor = when {
        isSelected -> selectedColor
        enabled -> AppTheme.FieldOutline
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (isSelected) selectedColor else AppTheme.TrackFill)
            .border(1.dp, borderColor, shape)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$label, $description" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isSelected -> AppTheme.OnPollColor
                enabled -> AppTheme.TextDark
                else -> AppTheme.TextFaint
            }
        )
    }
}

private fun List<PublishedSeatMap>.lastSeatLabel(studentId: String): String? = firstNotNullOfOrNull { map ->
    val desk = map.assignments.firstOrNull {
        studentId == it.studentAId || studentId == it.studentBId || studentId == it.studentCId
    } ?: return@firstNotNullOfOrNull null
    val date = formatDayMonth(map.publishedAt).ifBlank { map.publishedAt.take(10) }
    "Fila ${desk.row + 1}, banco ${desk.column + 1} · $date"
}
