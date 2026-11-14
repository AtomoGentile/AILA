package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaCard
import circolareplus.design.AilaSwitch
import circolareplus.design.AppTheme
import circolareplus.design.SubjectColors

@Composable
internal fun SubjectColorSettingsSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    overrides: Map<String, String>,
    onColorChange: (String, String?) -> Unit
) {
    var choosingSubject by remember { mutableStateOf<SubjectColors.Subject?>(null) }
    AilaCard {
        Column(Modifier.padding(AppTheme.Space16)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = AppTheme.Space12)) {
                    Text("Colori delle materie", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Applica i colori nel calendario e nel Brief. Le scelte restano su questo dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextMuted
                    )
                }
                AilaSwitch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Spacer(Modifier.height(AppTheme.Space8))
                HorizontalDivider(color = AppTheme.Hairline)
                SubjectColors.subjects.forEachIndexed { index, subject ->
                    val selected = SubjectColors.choice(overrides[subject.key]) ?: SubjectColors.choice(subject.defaultColor)!!
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { choosingSubject = subject }
                            .semantics { contentDescription = "Cambia colore ${subject.label}, attuale ${selected.label}" }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatch(selected.key, selected.colors)
                        Text(subject.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark)
                        Text(selected.label, style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
                    }
                    if (index != SubjectColors.subjects.lastIndex) HorizontalDivider(color = AppTheme.Hairline)
                }
                TextButton(onClick = { SubjectColors.subjects.forEach { onColorChange(it.key, null) } }) {
                    Text("Ripristina colori predefiniti", color = AppTheme.PrimaryBlue)
                }
            }
        }
    }

    choosingSubject?.let { subject ->
        val current = overrides[subject.key] ?: subject.defaultColor
        AlertDialog(
            onDismissRequest = { choosingSubject = null },
            title = { Text("Colore · ${subject.label}") },
            text = {
                Column {
                    SubjectColors.choices.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) {
                                    onColorChange(subject.key, choice.key)
                                    choosingSubject = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorSwatch(choice.key, choice.colors)
                            Text(choice.label, Modifier.weight(1f), color = AppTheme.TextDark)
                            if (choice.key == current) Text("✓", color = AppTheme.PrimaryBlue)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onColorChange(subject.key, null); choosingSubject = null }) {
                    Text("Predefinito")
                }
            },
            dismissButton = { TextButton(onClick = { choosingSubject = null }) { Text("Chiudi") } }
        )
    }
}

@Composable
private fun ColorSwatch(key: String, colors: List<androidx.compose.ui.graphics.Color>) {
    val brush = if (key == "rainbow") Brush.horizontalGradient(colors) else Brush.linearGradient(colors)
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .background(brush)
            .semantics { contentDescription = "Campione colore" }
    )
}
