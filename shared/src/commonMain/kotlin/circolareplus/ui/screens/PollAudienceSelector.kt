package circolareplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.AppContainer
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.AppTheme
import circolareplus.domain.model.User
import kotlinx.coroutines.CancellationException

/**
 * "A chi è rivolto?" nei fogli di creazione dei sondaggi: tutta la classe (default) oppure solo
 * alcune persone scelte. [selectedIds] null = tutta la classe.
 *
 * L'elenco della classe si carica qui, alla prima apertura di "Solo alcune persone", come nel
 * foglio "Nuovo evento": `classmates` di MainAppShell è popolato solo dopo una visita alla Mappa
 * Posti.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollAudienceSelector(
    selectedIds: List<String>?,
    onSelectionChange: (List<String>?) -> Unit,
    modifier: Modifier = Modifier
) {
    var candidates by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isEveryone = selectedIds == null

    LaunchedEffect(isEveryone) {
        if (!isEveryone && candidates.isEmpty() && !isLoading) {
            isLoading = true
            error = null
            try {
                candidates = AppContainer.usersRepository.listStudents()
                    .sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Impossibile caricare l'elenco della classe."
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "A chi è rivolto",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextMuted,
            modifier = Modifier.padding(bottom = AppTheme.Space8)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            AnimatedFilterChip(
                label = "Tutta la classe",
                isSelected = isEveryone,
                onClick = { onSelectionChange(null) },
                modifier = Modifier.weight(1f)
            )
            AnimatedFilterChip(
                label = "Solo alcune persone",
                isSelected = !isEveryone,
                onClick = { if (isEveryone) onSelectionChange(emptyList()) },
                modifier = Modifier.weight(1f)
            )
        }
        if (!isEveryone) {
            val selected = selectedIds.orEmpty()
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selected.size) {
                        0 -> "Tocca i nomi da includere"
                        1 -> "1 persona selezionata"
                        else -> "${selected.size} persone selezionate"
                    },
                    fontSize = 11.sp,
                    color = if (selected.isEmpty()) AppTheme.TintRedInk else AppTheme.TextFaint,
                    modifier = Modifier.weight(1f)
                )
                if (candidates.isNotEmpty()) {
                    val allSelected = selected.size >= candidates.size
                    Text(
                        text = if (allSelected) "Nessuno" else "Tutti",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.PrimaryBlue,
                        modifier = Modifier
                            .clickable {
                                onSelectionChange(if (allSelected) emptyList() else candidates.map { it.id })
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            when {
                isLoading -> Text("Carico l'elenco della classe...", fontSize = 12.sp, color = AppTheme.TextMuted)
                error != null -> Text(error ?: "", fontSize = 12.sp, color = AppTheme.TintRedInk)
                candidates.isEmpty() -> Text(
                    "Nessun compagno registrato ancora.",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                ) {
                    candidates.forEach { candidate ->
                        val isSelected = candidate.id in selected
                        AnimatedFilterChip(
                            label = "${candidate.firstName} ${candidate.lastName}".trim(),
                            isSelected = isSelected,
                            onClick = {
                                onSelectionChange(if (isSelected) selected - candidate.id else selected + candidate.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
