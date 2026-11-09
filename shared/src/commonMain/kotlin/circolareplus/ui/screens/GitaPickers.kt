@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.domain.model.GitaTrip
import kotlinx.coroutines.launch

internal enum class GitaQuickAction { UPDATE, MATERIAL, CREATE, RENAME }

/** Scelte della gita in un foglio: nomi su più righe e selezione esplicita. */
@Composable
internal fun GitaTripPickerSheet(
    trips: List<GitaTrip>, selectedId: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestSelect by rememberUpdatedState(onSelect)
    var closing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val activeId = selectedId ?: trips.firstOrNull()?.id
    val selectedIndex = trips.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val needle = query.trim().lowercase()
    val visible = trips.withIndex().filter { needle.isEmpty() || needle in it.value.name.lowercase() }
    fun close(select: String? = null) {
        if (closing) return
        closing = true
        keyboard?.hide()
        scope.launch {
            state.hide()
            latestDismiss()
            if (select != null) latestSelect(select)
        }
    }
    AilaBottomSheet(sheetState = state, onDismissRequest = { latestDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).appImePadding()
            .padding(horizontal = AppTheme.Space20).padding(bottom = AppTheme.Space24),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space16)) {
            GitaPickerHeading("Scegli la gita", "${trips.size} gite della classe", !closing) { close() }
            if (trips.size > 6) AilaPillTextField(
                value = query, onValueChange = { query = it }, placeholder = "Cerca una gita…",
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { AppIcons.Search(Modifier.size(18.dp), color = AppTheme.TextMuted) }
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                if (visible.isEmpty()) item(key = "empty") {
                    Text("Nessuna gita trovata.", style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.TextMuted, modifier = Modifier.padding(AppTheme.Space16))
                }
                items(visible, key = { it.value.id }) { indexed ->
                    val trip = indexed.value
                    val selected = trip.id == activeId
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(if (selected) AppTheme.SelectionContainer else AppTheme.CardSurface)
                            .selectable(selected = selected, enabled = !closing, role = Role.RadioButton,
                                onClick = { close(trip.id) })
                            .heightIn(min = 80.dp).padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppTheme.SelectionContainer else AppTheme.TintSlate),
                            contentAlignment = Alignment.Center) {
                            AppIcons.Bus(Modifier.size(22.dp), color = if (selected) AppTheme.OnSelection else AppTheme.TextMuted)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)) {
                            Text(trip.name, style = MaterialTheme.typography.titleMedium,
                                color = if (selected) AppTheme.OnSelection else AppTheme.TextDark)
                            Text(if (selected) "Gita ${indexed.index + 1} · Selezionata" else "Gita ${indexed.index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) AppTheme.OnSelection else AppTheme.TextMuted)
                        }
                        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(
                            selectedColor = AppTheme.OnSelection, unselectedColor = AppTheme.TextFaint))
                    }
                }
            }
        }
    }
}

/** Un solo ingresso alle azioni; il foglio si chiude prima di aprire quello di scrittura. */
@Composable
internal fun GitaActionsSheet(tripName: String, onDismiss: () -> Unit, onAction: (GitaQuickAction) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestAction by rememberUpdatedState(onAction)
    var closing by remember { mutableStateOf(false) }
    fun close(action: GitaQuickAction? = null) {
        if (closing) return
        closing = true
        scope.launch {
            state.hide()
            latestDismiss()
            if (action != null) latestAction(action)
        }
    }
    AilaBottomSheet(sheetState = state, onDismissRequest = { latestDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.Space20).padding(bottom = AppTheme.Space24),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            GitaPickerHeading("Organizza la gita", tripName, !closing) { close() }
            Text("Condividi con la classe", style = MaterialTheme.typography.labelMedium,
                color = AppTheme.TextMuted, modifier = Modifier.padding(top = AppTheme.Space4, start = AppTheme.Space4))
            GitaActionRow("Scrivi aggiornamento", "Pubblica una novità per questa gita", !closing, true,
                icon = { AppIcons.ChatBubble(Modifier.size(22.dp), color = it) }) { close(GitaQuickAction.UPDATE) }
            GitaActionRow("Aggiungi materiale", "Documenti, link e circolari", !closing,
                icon = { AppIcons.Document(Modifier.size(22.dp), color = it) }) { close(GitaQuickAction.MATERIAL) }
            Text("Gestisci le gite", style = MaterialTheme.typography.labelMedium,
                color = AppTheme.TextMuted, modifier = Modifier.padding(top = AppTheme.Space8, start = AppTheme.Space4))
            GitaActionRow("Nuova gita", "Crea un altro viaggio per la classe", !closing,
                icon = { AppIcons.Plus(Modifier.size(22.dp), color = it) }) { close(GitaQuickAction.CREATE) }
            GitaActionRow("Rinomina gita", "Modifica il nome di questa gita", !closing,
                icon = { AppIcons.Pencil(Modifier.size(22.dp), color = it) }) { close(GitaQuickAction.RENAME) }
        }
    }
}

@Composable
private fun GitaPickerHeading(title: String, subtitle: String, enabled: Boolean, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
        }
        AilaIconButton(contentDescription = "Chiudi", onClick = onClose, enabled = enabled, size = 44.dp) { tint ->
            AppIcons.Close(Modifier.size(18.dp), color = tint)
        }
    }
}

@Composable
private fun GitaActionRow(
    title: String, subtitle: String, enabled: Boolean, primary: Boolean = false,
    icon: @Composable (Color) -> Unit, onClick: () -> Unit
) {
    val ink = if (primary) AppTheme.OnSelection else AppTheme.TextDark
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AppTheme.SmallElementRadius))
        .background(if (primary) AppTheme.SelectionContainer else AppTheme.CardSurface)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .heightIn(min = 76.dp).padding(AppTheme.Space16),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
            if (primary) AppTheme.SelectionContainer else AppTheme.TintSlate), contentAlignment = Alignment.Center) {
            icon(if (primary) AppTheme.OnSelection else AppTheme.PrimaryBlue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (primary) ink else AppTheme.TextMuted)
        }
        AppIcons.ChevronRight(Modifier.size(18.dp), color = if (primary) ink else AppTheme.TextFaint)
    }
}
