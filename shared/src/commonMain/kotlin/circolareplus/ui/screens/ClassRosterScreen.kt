package circolareplus.ui.screens

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.remote.dto.DisciplinePairDto
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AilaSwitch
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import kotlinx.coroutines.launch

/**
 * Scheda Classe del Rappresentante: unica schermata dove chi ha il ruolo REPRESENTATIVE può
 * impostare, per ogni compagno, le valutazioni didattica/comportamento (1-5, usate dall'algoritmo
 * della Mappa Posti) e il Priority Pass (diritto a stare in una delle prime 3 file). Prima di
 * questa schermata, `RatingsRepository`/`/api/ratings` esistevano già lato client e backend ma non
 * erano collegati a nessuna interfaccia: era impossibile assegnare qualunque cosa dall'app.
 */
@Composable
fun ClassRosterScreen(
    entries: List<RatingEntryDto>,
    currentUserId: String = "",
    onDidacticChange: (studentId: String, value: Int) -> Unit,
    onBehaviorChange: (studentId: String, value: Int) -> Unit,
    onPriorityPassChange: (studentId: String, enabled: Boolean) -> Unit,
    onSecurityGuardChange: (studentId: String, enabled: Boolean) -> Unit = { _, _ -> },
    /** Coppie che insieme fanno caos: la Mappa Posti le penalizza (non le vieta). */
    disciplinePairs: List<DisciplinePairDto> = emptyList(),
    /** `duration`: MONTH, QUARTER o SCHOOL_YEAR. */
    onAddDisciplinePair: (studentA: String, studentB: String, duration: String) -> Unit = { _, _, _ -> },
    onRemoveDisciplinePair: (id: String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        Column(modifier = Modifier.padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)) {
            Text(
                text = "Valutazioni didattica/comportamento (1-5) e Priority Pass: usati dall'algoritmo " +
                    "quando generi le proposte di Mappa Posti.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.TextMuted
            )
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            // La Guardia la sceglie il Rappresentante: è la terza firma, con i due Rappresentanti,
            // per svelare l'autore di una proposta o di un commento anonimi.
            Text(
                text = if (entries.any { it.isSecurityGuard })
                    "Guardia di Sicurezza: ${entries.first { it.isSecurityGuard }.let { "${it.firstName} ${it.lastName}" }}."
                else
                    "Scegli la Guardia di Sicurezza di un compagno: senza di lei nessuno può chiedere " +
                        "di svelare un autore anonimo.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.PrimaryBlue
            )
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            ClassCodeRow()
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            LessonHoursRow()
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                circolareplus.design.AilaEmptyState(
                    title = "Nessun compagno di classe",
                    message = "Appena i tuoi compagni si registrano con il codice della classe, compaiono qui.",
                    icon = { circolareplus.design.AppIcons.Profile(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        } else {
            // Sort entries case-insensitively without Java-only Locale so the Wasm build remains portable.
            val displayEntries = remember(entries) {
                entries.sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() }))
            }

            circolareplus.design.AilaAdaptiveCardList(
                items = displayEntries,
                key = { it.studentId },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
                header = {
                    DisciplinePairsCard(
                        entries = displayEntries,
                        pairs = disciplinePairs,
                        onAdd = onAddDisciplinePair,
                        onRemove = onRemoveDisciplinePair
                    )
                }
            ) { _, entry ->
                ClassRosterRow(
                    entry = entry,
                    isSelf = entry.studentId == currentUserId,
                    onDidacticChange = { onDidacticChange(entry.studentId, it) },
                    onBehaviorChange = { onBehaviorChange(entry.studentId, it) },
                    onPriorityPassChange = { onPriorityPassChange(entry.studentId, it) },
                    onSecurityGuardChange = { onSecurityGuardChange(entry.studentId, it) }
                )
            }
        }
    }
}
