@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.domain.model.GitaTrip
import circolareplus.domain.model.GitaUpdate

/** Il titolo e' il selettore; il gesto resta separato dallo scroll dei materiali. */
@Composable
internal fun GitaTripHeader(
    trips: List<GitaTrip>, selectedId: String?, enabled: Boolean, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val index = trips.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val latestSelect by rememberUpdatedState(onSelect)
    val selectable = trips.size > 1
    val ink = AppTheme.TextMuted
    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .clickable(enabled = enabled && selectable, role = Role.Button) { expanded = true }
                .semantics {
                    contentDescription = if (selectable)
                        "${trips.getOrNull(index)?.name}. Gita ${index + 1} di ${trips.size}. Tocca per scegliere o scorri in verticale per cambiare."
                    else trips.firstOrNull()?.name ?: "Gita"
                }
                .pointerInput(trips, index, enabled, threshold) {
                    if (!enabled || !selectable) return@pointerInput
                    var distance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
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
                }
                .padding(vertical = AppTheme.Space12, horizontal = AppTheme.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(AppTheme.SelectionContainer), contentAlignment = Alignment.Center) {
                AppIcons.Bus(Modifier.size(24.dp), color = AppTheme.OnSelection)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trips.getOrNull(index)?.name ?: "Gita", style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (selectable) "Gita ${index + 1} di ${trips.size} · Scorri ↑ ↓" else "Materiali e novità della classe",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            if (selectable) Canvas(Modifier.size(16.dp)) {
                drawLine(ink, Offset(size.width * .2f, size.height * .4f), Offset(size.width * .5f, size.height * .7f), strokeWidth = 2.dp.toPx())
                drawLine(ink, Offset(size.width * .5f, size.height * .7f), Offset(size.width * .8f, size.height * .4f), strokeWidth = 2.dp.toPx())
            }
        }
        if (expanded && enabled) GitaTripPickerSheet(
            trips = trips, selectedId = selectedId,
            onDismiss = { expanded = false }, onSelect = onSelect
        )
    }
}

/** Stessa gerarchia per documenti, circolari e novita'; il colore e' riservato alle azioni. */
@Composable
internal fun GitaSectionHeading(title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space4), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
        Text(count, style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.TintSlate).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
internal fun GitaTextSheet(
    kind: String, initialName: String, busy: Boolean, error: String?, closeRequested: Boolean,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit
) {
    var title by rememberSaveable(kind) { mutableStateOf(initialName) }
    var body by rememberSaveable(kind) { mutableStateOf("") }
    val isUpdate = kind == "update"
    val maxTitle = if (isUpdate) 200 else 100
    val latestBusy by rememberUpdatedState(busy)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !latestBusy })
    LaunchedEffect(closeRequested, busy) {
        if (closeRequested && !busy) {
            keyboard?.hide()
            sheetState.hide()
            latestDismiss()
        }
    }
    AilaBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().appImePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.Space24).padding(bottom = AppTheme.Space24),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (kind) { "create" -> "Nuova gita"; "rename" -> "Rinomina gita"; else -> "Scrivi aggiornamento" },
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                AilaIconButton(contentDescription = "Chiudi", onClick = onDismiss, enabled = !busy) { tint ->
                    AppIcons.Close(Modifier.size(18.dp), color = tint)
                }
            }
            Text(if (isUpdate) "Le novità saranno visibili a tutta la classe, in questa gita."
                else "Scegli un nome che la classe riconosca, per esempio «Viaggio a Firenze».",
                style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            OutlinedTextField(value = title, onValueChange = { if (it.length <= maxTitle) title = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(if (isUpdate) "Titolo" else "Nome della gita") },
                shape = RoundedCornerShape(AppTheme.SmallElementRadius), singleLine = true, enabled = !busy)
            if (isUpdate) OutlinedTextField(value = body, onValueChange = { if (it.length <= 5000) body = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Cosa c’è di nuovo?") }, minLines = 5, maxLines = 10,
                shape = RoundedCornerShape(AppTheme.SmallElementRadius), enabled = !busy)
            if (error != null) Text(error, color = AppTheme.TintRedInk, style = MaterialTheme.typography.bodySmall)
            AilaPrimaryButton(text = if (busy) "Salvataggio…" else if (isUpdate) "Pubblica aggiornamento" else "Salva gita",
                onClick = { keyboard?.hide(); onSave(title.trim(), body.trim()) }, fillMaxWidth = true, large = true,
                enabled = !busy && title.isNotBlank() && (!isUpdate || body.isNotBlank()))
        }
    }
}

@Composable
internal fun GitaUpdateCard(update: GitaUpdate, canEdit: Boolean, busy: Boolean, onDelete: (GitaUpdate) -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(update.id) { mutableStateOf(false) }
    var overflowing by remember(update.body) { mutableStateOf(false) }
    AilaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(update.authorName.ifBlank { "Rappresentante" }, style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
                    Text(update.createdAt.take(16), style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                }
                if (canEdit) Box {
                    val ink = AppTheme.TextMuted
                    IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Gestisci aggiornamento" }) {
                        Canvas(Modifier.size(20.dp)) {
                            repeat(3) { drawCircle(ink, 1.8.dp.toPx(), Offset(size.width * (.2f + .3f * it), size.height * .5f)) }
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Elimina aggiornamento", color = AppTheme.TintRedInk) },
                            onClick = { menu = false; deleting = true })
                    }
                }
            }
            Text(update.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
            Text(update.body, style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextMuted,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow })
            if (overflowing || expanded) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Mostra meno" else "Leggi tutto")
            }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Eliminare l’aggiornamento?") },
        text = { Text(update.title) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete(update) }, enabled = !busy) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annulla") } })
}
