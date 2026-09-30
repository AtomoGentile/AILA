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
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            ClassCodeRow()
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
            circolareplus.design.AilaAdaptiveCardList(
                items = entries,
                key = { it.studentId },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
                header = {
                    DisciplinePairsCard(
                        entries = entries,
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

/**
 * Codice per entrare nella classe: senza, chiunque poteva registrarsi in "4 CSA" e vedere nomi,
 * bacheca e calendario. Si gira ai compagni (es. nel gruppo della classe); se finisce dove non
 * deve si rigenera e quello vecchio smette di valere. Diventa obbligatorio per registrarsi dal
 * momento in cui esiste, cioe' dalla prima volta che si apre questa schermata.
 */
@Composable
private fun ClassCodeRow() {
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            code = circolareplus.data.AppContainer.usersRepository.classCode()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? circolareplus.data.remote.ApiException)?.message ?: "Codice classe non disponibile offline."
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintBlue)
            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Codice classe", fontSize = 12.sp, color = AppTheme.TintBlueInk)
            Text(
                text = code ?: error ?: "…",
                fontSize = if (code != null) 20.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (code != null) 3.sp else 0.sp,
                color = AppTheme.TintBlueInk
            )
            if (code != null) {
                Text(
                    text = "Serve ai compagni per registrarsi in questa classe.",
                    fontSize = 11.sp,
                    color = AppTheme.TintBlueInk.copy(alpha = 0.8f)
                )
            }
        }
        if (code != null) {
            TextButton(enabled = !busy, onClick = { confirmRegenerate = true }) {
                Text(if (busy) "…" else "Rigenera", color = AppTheme.TintBlueInk)
            }
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("Nuovo codice classe?") },
            text = { Text("Il codice attuale smette di valere: chi non si è ancora registrato dovrà usare quello nuovo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    busy = true
                    scope.launch {
                        try {
                            code = circolareplus.data.AppContainer.usersRepository.regenerateClassCode()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = (e as? circolareplus.data.remote.ApiException)?.message
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Rigenera") }
            },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("Annulla") } }
        )
    }
}

/**
 * Password dimenticata di un compagno: nell'app non ci sono email, quindi il Rappresentante genera
 * un codice monouso (24 ore) e glielo dice; lui lo usa in "Password dimenticata?" al login.
 */
@Composable
private fun ResetCodeButton(entry: RatingEntryDto) {
    var result by remember { mutableStateOf<circolareplus.data.remote.dto.ResetCodeResponseDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TextButton(enabled = !busy, onClick = { confirm = true }) {
        Text(if (busy) "Genero il codice…" else "Password dimenticata? Genera un codice", fontSize = 13.sp)
    }
    error?.let { Text(text = it, fontSize = 12.sp, color = AppTheme.TintRedInk) }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Codice di reset") },
            text = {
                Text(
                    "Generi un codice con cui ${entry.firstName} può scegliere una password nuova. " +
                        "Dallo solo a lei/lui, di persona o in privato."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            result = circolareplus.data.AppContainer.usersRepository.createResetCode(entry.studentId)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = (e as? circolareplus.data.remote.ApiException)?.message ?: "Impossibile generare il codice."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Genera") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } }
        )
    }

    result?.let { reset ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Codice per ${entry.firstName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    Text(text = reset.code, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = AppTheme.TextDark)
                    Text(
                        text = "Username: ${reset.username ?: "—"}. Al login tocca \"Password dimenticata?\" e inserisce " +
                            "username, questo codice e la password nuova. Vale 24 ore e una volta sola.",
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("Fatto") } }
        )
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

            if (!isSelf) {
                Spacer(modifier = Modifier.height(4.dp))
                ResetCodeButton(entry)
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
                    AilaIconButton(
                        contentDescription = "Rimuovi la coppia",
                        onClick = { onRemove(pair.id) },
                        size = 36.dp
                    ) { tint -> AppIcons.Trash(modifier = Modifier.size(18.dp), color = tint) }
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
                                AppIcons.Check(modifier = Modifier.size(16.dp), color = AppTheme.TintBlueInk)
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
