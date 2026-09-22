package circolareplus.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.remote.dto.DisciplinePairDto
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AilaSwitch
import circolareplus.design.AppTheme

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
                fontSize = 12.sp,
                color = AppTheme.TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            // La Guardia la sceglie il Rappresentante: è la terza firma, con i due Rappresentanti,
            // per svelare l'autore di una proposta o di un commento anonimi.
            Text(
                text = if (entries.any { it.isSecurityGuard })
                    "Guardia di Sicurezza: ${entries.first { it.isSecurityGuard }.let { "${it.firstName} ${it.lastName}" }}."
                else
                    "Scegli la Guardia di Sicurezza di un compagno: senza di lei nessuno può chiedere " +
                        "di svelare un autore anonimo.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.PrimaryBlue
            )
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)
            ) {
                item(key = "discipline-pairs") {
                    DisciplinePairsCard(
                        entries = entries,
                        pairs = disciplinePairs,
                        onAdd = onAddDisciplinePair,
                        onRemove = onRemoveDisciplinePair
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                }
                items(entries, key = { it.studentId }) { entry ->
                    ClassRosterRow(
                        entry = entry,
                        isSelf = entry.studentId == currentUserId,
                        onDidacticChange = { onDidacticChange(entry.studentId, it) },
                        onBehaviorChange = { onBehaviorChange(entry.studentId, it) },
                        onPriorityPassChange = { onPriorityPassChange(entry.studentId, it) },
                        onSecurityGuardChange = { onSecurityGuardChange(entry.studentId, it) }
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                }
            }
        }
    }
}

@Composable
private fun ClassRosterRow(
    entry: RatingEntryDto,
    isSelf: Boolean = false,
    onDidacticChange: (Int) -> Unit,
    onBehaviorChange: (Int) -> Unit,
    onPriorityPassChange: (Boolean) -> Unit,
    onSecurityGuardChange: (Boolean) -> Unit
) {
    AilaCard {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppTheme.TintBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${entry.firstName.take(1)}${entry.lastName.take(1)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintBlueInk
                    )
                }
                Spacer(modifier = Modifier.width(AppTheme.Space12))
                Text(
                    text = "${entry.firstName} ${entry.lastName}" + if (isSelf) " (Tu)" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            RatingStepper(
                label = "Didattica",
                value = entry.didactic ?: 3,
                onValueChange = onDidacticChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            RatingStepper(
                label = "Comportamento",
                value = entry.behavior ?: 3,
                onValueChange = onBehaviorChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Priority Pass (prime 3 file)", fontSize = 14.sp, color = AppTheme.TextMuted)
                AilaSwitch(
                    checked = entry.priorityPass,
                    onCheckedChange = onPriorityPassChange
                )
            }

            // I Rappresentanti non possono essere anche la Guardia: le tre firme sono di tre
            // persone diverse.
            if (entry.role != "REPRESENTATIVE") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Guardia di Sicurezza (3ª firma)",
                        fontSize = 14.sp,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    AilaSwitch(
                        checked = entry.isSecurityGuard,
                        onCheckedChange = onSecurityGuardChange
                    )
                }
            }
        }
    }
}

/** Selettore 1-5 con frecce +/-, senza dipendenze da picker più complessi. */
@Composable
private fun RatingStepper(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = AppTheme.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > 1) onValueChange(value - 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Text(text = "−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTheme.PrimaryBlue)
            }
            Text(
                text = "$value",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                modifier = Modifier.padding(horizontal = AppTheme.Space8)
            )
            IconButton(
                onClick = { if (value < 5) onValueChange(value + 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Text(text = "+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTheme.PrimaryBlue)
            }
        }
    }
}

/** Durate proposte per una coppia da separare, con il valore che capisce il server. */
private val PAIR_DURATIONS = listOf("1 mese" to "MONTH", "3 mesi" to "QUARTER", "Fine anno" to "SCHOOL_YEAR")

/**
 * Coppie da separare per disciplina: chi insieme fa caos ma con altri e' tranquillo. Il voto di
 * comportamento e' per persona e non sa dirlo. L'ottimizzatore penalizza solo quel banco, senza
 * vietarlo, cosi' la rotazione mensile continua a variare. Visibile solo ai Rappresentanti; ogni
 * segnalazione scade da sola.
 */
@Composable
private fun DisciplinePairsCard(
    entries: List<RatingEntryDto>,
    pairs: List<DisciplinePairDto>,
    onAdd: (String, String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val names = remember(entries) { entries.associate { it.studentId to "${it.firstName} ${it.lastName}" } }

    AilaCard {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Text(
                text = "Coppie da separare",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Per chi insieme fa caos ma con altri e' tranquillo. La Mappa Posti evita di " +
                    "metterli nello stesso banco, senza bloccare la rotazione. Lo vedono solo i " +
                    "Rappresentanti e scade da solo.",
                fontSize = 12.sp,
                color = AppTheme.TextMuted,
                lineHeight = 16.sp
            )
            pairs.forEach { pair ->
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${names[pair.studentA] ?: "?"} + ${names[pair.studentB] ?: "?"}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.TextDark
                        )
                        Text(
                            text = "Fino al ${italianDate(pair.expiresAt)}",
                            fontSize = 12.sp,
                            color = AppTheme.TextMuted
                        )
                    }
                    Text(
                        text = "Rimuovi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.PrimaryBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRemove(pair.id) }
                            .padding(horizontal = AppTheme.Space8, vertical = 4.dp)
                    )
                }
            }
            if (entries.size >= 2) {
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                AilaSecondaryButton(
                    text = "Aggiungi coppia",
                    onClick = { showDialog = true },
                    compact = true
                )
            }
        }
    }

    if (showDialog) {
        AddDisciplinePairDialog(
            entries = entries,
            onDismiss = { showDialog = false },
            onConfirm = { a, b, duration ->
                showDialog = false
                onAdd(a, b, duration)
            }
        )
    }
}

@Composable
private fun AddDisciplinePairDialog(
    entries: List<RatingEntryDto>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    var durationIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coppia da separare") },
        text = {
            Column {
                Text(
                    text = "Scegli due compagni.",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEach { entry ->
                        val isSelected = entry.studentId in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AppTheme.TintBlue else Color.Transparent)
                                .clickable {
                                    if (isSelected) {
                                        selected -= entry.studentId
                                    } else if (selected.size < 2) {
                                        selected += entry.studentId
                                    }
                                }
                                .padding(horizontal = AppTheme.Space8, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${entry.firstName} ${entry.lastName}",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppTheme.TintBlueInk else AppTheme.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Text(text = "✓", fontSize = 14.sp, color = AppTheme.TintBlueInk)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                Text(text = "Per quanto tempo", fontSize = 13.sp, color = AppTheme.TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                AilaSegmentedTabs(
                    labels = PAIR_DURATIONS.map { it.first },
                    selectedIndex = durationIndex,
                    onSelect = { durationIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.size == 2,
                onClick = { onConfirm(selected[0], selected[1], PAIR_DURATIONS[durationIndex].second) }
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

/** "2026-10-22" -> "22/10/2026"; se il formato non e' quello atteso, la stringa com'e'. */
private fun italianDate(iso: String): String {
    val parts = iso.take(10).split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}
