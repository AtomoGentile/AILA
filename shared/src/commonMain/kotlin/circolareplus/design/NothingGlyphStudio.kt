package circolareplus.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.platform.LocalNothingGlyph

@Composable
fun NothingGlyphSettingsSection(modifier: Modifier = Modifier) {
    val preferences = NothingExperience.preferences
    val hardware = LocalNothingGlyph.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Glyph", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
        GlyphSetting("Incastri nella Home", preferences.showCalendarGlyph) {
            NothingExperience.configure(preferences.copy(showCalendarGlyph = it))
        }
        if (hardware.ownerLabel != null) {
            GlyphSetting("Luci per nuove sovrapposizioni", preferences.conflictLights && hardware.ready,
                enabled = hardware.ready) {
                NothingExperience.configure(preferences.copy(conflictLights = it))
            }
            Text(
                if (hardware.ready) "Le luci si accendono quando compare una nuova sovrapposizione certa con la Home aperta."
                else hardware.status,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.TextMuted
            )
        }
        if (hardware.notificationSettingsAvailable) {
            Text(
                "Configura le luci per gli avvisi di AILA nella sezione Notifiche.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.TextMuted
            )
        }
    }
}

@Composable
internal fun NothingGlyphNotificationSettings(modifier: Modifier = Modifier) {
    val hardware = LocalNothingGlyph.current
    if (!hardware.notificationSettingsAvailable) return
    val preferences = NothingExperience.preferences
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        GlyphSetting("Notifiche sul Glyph", preferences.systemNotifications) {
            NothingExperience.configure(preferences.copy(systemNotifications = it))
        }
        if (preferences.systemNotifications) {
            Text(
                "Con le notifiche di sistema e le luci attive, apri una categoria e scegli un suono Nothing con un pattern Glyph.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.TextMuted
            )
            NothingNotificationGroup.entries.forEach { group ->
                AilaTextButton({ hardware.openNotificationSettings(group) }) { Text("Configura ${group.label}") }
            }
        }
    }
}

@Composable
private fun GlyphSetting(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
        AilaSwitch(value, onChange, enabled = enabled)
    }
}
