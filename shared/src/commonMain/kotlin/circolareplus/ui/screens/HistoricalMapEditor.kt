package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import circolareplus.algorithms.DeskAssignment
import circolareplus.design.*
import circolareplus.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HistoricalMapEditor(initial: List<DeskAssignment>, studentsMap: Map<String, User>, isNew: Boolean,
    onDismiss: () -> Unit, onSave: suspend (List<DeskAssignment>, String) -> Unit) {
    var desks by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun occupant(d: DeskAssignment, seat: Int) = when (seat) { 0 -> d.studentAId; 1 -> d.studentBId; else -> d.studentCId }
    fun assign(d: DeskAssignment, seat: Int, id: String?) = when (seat) {
        0 -> d.copy(studentAId = id); 1 -> d.copy(studentBId = id); else -> d.copy(studentCId = id)
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().appSafeDrawingPadding().padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                Text(if (isNew) "Aggiungi mappa passata" else "Correggi mappa", style = MaterialTheme.typography.titleLarge)
                Text("Le correzioni conteranno nelle prossime generazioni. Tocca un posto per cambiare studente; se già presente, i posti vengono scambiati.", color = AppTheme.TextMuted)
                if (isNew) OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data effettiva (AAAA-MM-GG)") }, singleLine = true)
                error?.let { Text(it, color = AppTheme.TintRedInk) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    itemsIndexed(desks) { index, desk ->
                        AilaCard {
                            Column(Modifier.padding(AppTheme.Space12)) {
                                Text("Fila ${desk.row + 1} · banco ${desk.column + 1}")
                                repeat(desk.seats) { seat ->
                                    val id = occupant(desk, seat)
                                    val student = studentsMap[id]
                                    TextButton(enabled = !busy, onClick = { selected = index to seat }) {
                                        Text(student?.let { "${it.firstName} ${it.lastName}" } ?: id?.let { "Studente non più registrato" } ?: "Posto vuoto")
                                    }
                                }
                                Row {
                                    TextButton(enabled = !busy, onClick = { desks = desks.mapIndexed { i, d -> if (i == index) d.copy(seats = 3) else d } }) { Text("Terzo posto") }
                                    TextButton(enabled = !busy, onClick = { desks = desks.filterIndexed { i, _ -> i != index } }) { Text("Rimuovi banco") }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(enabled = !busy, onClick = {
                            val next = (desks.maxOfOrNull { it.row * 3 + it.column } ?: -1) + 1
                            desks = desks + DeskAssignment(next / 3, next % 3, null, null)
                        }) { Text("Aggiungi banco") }
                    }
                }
                Row {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Annulla") }
                    Button(enabled = !busy && desks.isNotEmpty() && (!isNew || Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)), onClick = {
                        busy = true
                        scope.launch {
                            try { onSave(desks, if (isNew) "${date}T12:00:00Z" else "")
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { error = "Salvataggio non riuscito: ${e.message}"
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Salvataggio…" else "Salva nello storico") }
                }
            }
        }
        selected?.let { (index, seat) ->
            AlertDialog(onDismissRequest = { selected = null }, title = { Text("Scegli studente") }, text = {
                LazyColumn {
                    item { TextButton(onClick = { desks = desks.mapIndexed { i, d -> if (i == index) assign(d, seat, null) else d }; selected = null }) { Text("Posto vuoto") } }
                    val students = studentsMap.values.sortedWith(compareBy({ it.lastName }, { it.firstName }))
                    items(students.size) { studentIndex ->
                        val student = students[studentIndex]
                        TextButton(onClick = {
                            val previous = occupant(desks[index], seat)
                            desks = desks.mapIndexed { i, d ->
                                var changed = d
                                repeat(d.seats) { s -> if (occupant(d, s) == student.id) changed = assign(changed, s, previous) }
                                if (i == index) changed = assign(changed, seat, student.id)
                                changed
                            }
                            selected = null
                        }) { Text("${student.firstName} ${student.lastName}") }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Chiudi") } })
        }
    }
}
