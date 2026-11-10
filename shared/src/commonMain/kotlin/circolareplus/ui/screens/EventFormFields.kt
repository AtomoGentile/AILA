package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.TimetableLesson
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFieldColors

/** Shared metrics keep text inputs and selectors aligned, including their labels. */
internal val EventFormShape = RoundedCornerShape(16.dp)

@Composable
internal fun EventFormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
        content()
    }
}

@Composable
internal fun EventFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    inputModifier: Modifier = Modifier
) {
    EventFormField(label, modifier) {
        OutlinedTextField(
            value, onValueChange,
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = singleLine, minLines = if (singleLine) 1 else 2, maxLines = if (singleLine) 1 else 4,
            shape = EventFormShape, colors = ailaFieldColors(),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label }.then(inputModifier)
        )
    }
}

/** A compact, accessible selector: one label/value row instead of a grid of large chips. */
@Composable
internal fun EventFormValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    EventFormField(label, modifier) {
        Row(
            Modifier.fillMaxWidth().clip(EventFormShape)
                .background(AppTheme.FieldSurface)
                .border(1.dp, AppTheme.FieldOutline, EventFormShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) AppTheme.TextDark else AppTheme.TextFaint,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (icon != null) icon() else AppIcons.ChevronRight(Modifier.size(18.dp), color = AppTheme.TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventFormDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    EventFormValueRow(label, options.getOrElse(selectedIndex) { "Scegli" }, { focusManager.clearFocus(); expanded = true }, modifier = modifier, icon = {
        AppIcons.ChevronRight(Modifier.size(18.dp).rotate(90f), color = AppTheme.TextMuted)
    })
    if (expanded) circolareplus.design.AilaBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark, modifier = Modifier.weight(1f))
                circolareplus.design.AilaIconButton(contentDescription = "Chiudi $label", onClick = { expanded = false }) { tint ->
                    AppIcons.Close(Modifier.size(18.dp), color = tint)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier.fillMaxWidth().clip(EventFormShape)
                            .background(if (index == selectedIndex) AppTheme.TintBlue else AppTheme.FieldSurface)
                            .selectable(selected = index == selectedIndex, role = Role.RadioButton, onClick = { expanded = false; onSelect(index) })
                            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(option, style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark, modifier = Modifier.weight(1f))
                        if (index == selectedIndex) AppIcons.Check(Modifier.size(18.dp), color = AppTheme.PrimaryBlue)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Inline suggestions keep the keyboard active and leave the subject editable without a timetable. */
@Composable
internal fun EventSubjectAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    lessons: List<TimetableLesson>,
    selectedLesson: TimetableLesson?,
    hasDate: Boolean,
    onSelect: (TimetableLesson) -> Unit,
    modifier: Modifier = Modifier,
    fillHours: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val matches = lessons.filter { value.isBlank() || it.subject.contains(value.trim(), ignoreCase = true) }
    Column(modifier) {
        EventFormTextField(
            value, onValueChange, label = "Materia (facoltativa)", placeholder = "Es. Inglese",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            inputModifier = Modifier.onFocusChanged { focused = it.isFocused }
        )
        if (focused || selectedLesson != null) {
            Text(when {
                    !fillHours -> "Gli orari si impostano per ciascun giorno"
                    selectedLesson != null -> "Orario compilato · ${selectedLesson.timeLabel}"
                    !hasDate -> "Scegli prima la data per vedere le lezioni"
                    lessons.isNotEmpty() -> "Scegli una lezione per compilare anche le ore"
                    else -> "Puoi inserire liberamente la materia"
                }, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (focused && matches.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.FieldSurface).border(1.dp, AppTheme.FieldOutline, RoundedCornerShape(16.dp))
                    .heightIn(max = 192.dp).verticalScroll(rememberScrollState())
            ) {
                Text("LEZIONI DEL GIORNO", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp))
                matches.forEach { lesson ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            onSelect(lesson)
                            focusManager.clearFocus()
                        }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lesson.subject.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark,
                            modifier = Modifier.weight(1f))
                        Text(lesson.timeLabel, style = MaterialTheme.typography.labelMedium, color = AppTheme.PrimaryBlue,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
