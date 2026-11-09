package circolareplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.domain.model.GitaTrip
import circolareplus.domain.model.GitaUpdate

/** Il gesto appartiene al selettore: la lista sottostante mantiene lo scroll verticale. */
@Composable
internal fun GitaTripHeader(
    trips: List<GitaTrip>, selectedId: String?, canEdit: Boolean, enabled: Boolean,
    onSelect: (String) -> Unit, onCreate: () -> Unit, onRename: () -> Unit, onUpdate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val index = trips.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val latestSelect by rememberUpdatedState(onSelect)
    Column(Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)) {
        Box {
            AilaCard(modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "Selettore gita. Scorri in alto per la successiva, in basso per la precedente, o tocca per scegliere." }
                .clickable(enabled = enabled) { expanded = true }
                .pointerInput(trips, index, enabled, threshold) {
                    if (!enabled || trips.size < 2) return@pointerInput
                    var distance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { distance = 0f },
                        onDragCancel = { distance = 0f },
                        onDragEnd = {
                            val next = when {
                                distance < -threshold -> index + 1
                                distance > threshold -> index - 1
                                else -> index
                            }
                            if (next != index && next in trips.indices) latestSelect(trips[next].id)
                        },
                        onVerticalDrag = { change, amount -> change.consume(); distance += amount }
                    )
                }) {
                Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trips.getOrNull(index)?.name ?: "Gita", style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                    Text(if (trips.size > 1) "${index + 1} di ${trips.size} · Scorri qui ↑ ↓ o tocca per scegliere"
                        else "Tocca per scegliere la gita", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                }
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                trips.forEach { trip ->
                    DropdownMenuItem(text = { Text(trip.name) }, onClick = { expanded = false; onSelect(trip.id) })
                }
            }
        }
        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCreate, enabled = enabled) { Text("Nuova gita") }
                TextButton(onClick = onRename, enabled = enabled && trips.isNotEmpty()) { Text("Rinomina") }
            }
            AilaPrimaryButton(text = "Scrivi aggiornamento", onClick = onUpdate, enabled = enabled && trips.isNotEmpty(), fillMaxWidth = true)
        }
    }
}

@Composable
internal fun GitaTextDialog(
    kind: String, initialName: String, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit
) {
    var title by remember(kind) { mutableStateOf(initialName) }
    var body by remember(kind) { mutableStateOf("") }
    val isUpdate = kind == "update"
    val maxTitle = if (isUpdate) 200 else 100
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AppTheme.SurfaceWhite,
        title = { Text(when (kind) { "create" -> "Nuova gita"; "rename" -> "Rinomina gita"; else -> "Nuovo aggiornamento" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { if (it.length <= maxTitle) title = it },
                    label = { Text(if (isUpdate) "Titolo" else "Nome della gita") }, singleLine = true, enabled = !busy)
                if (isUpdate) OutlinedTextField(value = body, onValueChange = { if (it.length <= 5000) body = it },
                    label = { Text("Cosa c’è di nuovo?") }, minLines = 4, maxLines = 9, enabled = !busy)
                if (error != null) Text(error, color = AppTheme.TintRedInk, style = MaterialTheme.typography.bodySmall)
                if (busy) Text("Salvataggio…", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim(), body.trim()) },
            enabled = !busy && title.isNotBlank() && (!isUpdate || body.isNotBlank())) {
            Text(if (isUpdate) "Pubblica" else "Salva")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annulla") } }
    )
}

@Composable
internal fun GitaUpdatesSection(updates: List<GitaUpdate>, canEdit: Boolean, busy: Boolean, onDelete: (GitaUpdate) -> Unit) {
    var deleting by remember { mutableStateOf<GitaUpdate?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        updates.forEach { update ->
            key(update.id) {
                AilaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                        Text(update.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text("${update.authorName} · ${update.createdAt.take(16)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                        Text(update.body, style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark)
                        if (canEdit) TextButton(onClick = { deleting = update }, enabled = !busy) { Text("Elimina", color = AppTheme.TintRedInk) }
                    }
                }
            }
        }
    }
    deleting?.let { update ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminare l’aggiornamento?") },
            text = { Text(update.title) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(update) }, enabled = !busy) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } })
    }
}
