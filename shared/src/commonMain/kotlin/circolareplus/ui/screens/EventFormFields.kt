package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.TimetableLesson
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaFieldColors

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
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(AppTheme.FieldSurface)
            .border(1.dp, AppTheme.FieldOutline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) AppTheme.TextDark else AppTheme.TextFaint,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        AppIcons.ChevronRight(Modifier.size(16.dp), color = AppTheme.TextMuted)
    }
}

@Composable
internal fun EventFormDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        EventFormValueRow(label, options.getOrElse(selectedIndex) { "Scegli" }, { expanded = true }, icon = icon)
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, color = if (index == selectedIndex) AppTheme.PrimaryBlue else AppTheme.TextDark) },
                    onClick = { expanded = false; onSelect(index) },
                    trailingIcon = { if (index == selectedIndex) AppIcons.Check(Modifier.size(16.dp), color = AppTheme.PrimaryBlue) }
                )
            }
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
        OutlinedTextField(
            value, onValueChange, label = { Text("Materia") }, placeholder = { Text("Facoltativa") },
            singleLine = true, shape = RoundedCornerShape(16.dp), colors = ailaFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            supportingText = {
                Text(when {
                    !fillHours -> "Gli orari si impostano per ciascun giorno"
                    selectedLesson != null -> "Orario compilato · ${selectedLesson.timeLabel}"
                    !hasDate -> "Scegli la data per vedere le lezioni del giorno"
                    lessons.isNotEmpty() -> "Scegli una lezione per compilare anche le ore"
                    else -> "Puoi inserire liberamente la materia"
                }, color = AppTheme.TextMuted)
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
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
