@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaBottomSheet
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.NothingExperience
import circolareplus.design.nothingDisplayStyle
import circolareplus.domain.model.CalendarEvent
import circolareplus.platform.AppForeground
import circolareplus.platform.LocalNothingGlyph
import circolareplus.platform.currentTimeMillis
import circolareplus.util.CivilDate
import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.nowMinutesOfDay
import circolareplus.util.parseTimeToMinutes
import circolareplus.util.plusDays
import circolareplus.util.today
import kotlinx.coroutines.delay

internal fun currentGlyphConflicts(
    conflicts: List<CalendarConflict>,
    today: CivilDate,
    currentMinute: Int
): List<CalendarConflict> {
    val todayIso = today.toIso()
    val lastDay = today.plusDays(6).toIso()
    return conflicts.filter { conflict ->
        if (conflict.date != todayIso) return@filter conflict.date in todayIso..lastDay
        val first = conflict.first.time?.let(::parseTimeToMinutes)
        val second = conflict.second.time?.let(::parseTimeToMinutes)
        first == null || second == null || maxOf(first, second) >= currentMinute
    }
}

private data class ConflictKey(val date: String, val eventIds: List<String>)

@Composable
internal fun CalendarConflictGlyph(
    conflicts: List<CalendarConflict>,
    loading: Boolean,
    unavailable: Boolean,
    homeVisible: Boolean,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenCalendar: () -> Unit
) {
    val minute by produceState(currentTimeMillis() / 60_000L) {
        while (true) {
            val now = currentTimeMillis()
            value = now / 60_000L
            delay(60_000L - now % 60_000L)
        }
    }
    val relevant = remember(conflicts, minute) { currentGlyphConflicts(conflicts, today(), nowMinutesOfDay()) }
    val byDate = remember(relevant) { relevant.groupBy { it.date } }
    val hasConfirmed = relevant.any { it.kind == CalendarConflictKind.OVERLAP }
    val confirmedKeys = remember(relevant) {
        relevant.filter { it.kind == CalendarConflictKind.OVERLAP }.mapTo(mutableSetOf()) {
            ConflictKey(it.date, listOf(it.first.id, it.second.id).sorted())
        }
    }
    val hardware = LocalNothingGlyph.current
    val signalEnabled = NothingExperience.preferences.conflictLights && hardware.ready &&
        homeVisible && AppForeground.isForeground
    var observed by remember { mutableStateOf<Set<ConflictKey>?>(null) }
    LaunchedEffect(confirmedKeys, loading, unavailable, signalEnabled) {
        if (loading || unavailable) return@LaunchedEffect
        val previous = observed
        observed = confirmedKeys
        if (!signalEnabled || previous == null || confirmedKeys.all { it in previous }) return@LaunchedEffect
        try {
            if (hardware.pulse(zone = 1, periodMillis = 1200)) delay(1300)
        } finally { hardware.stop() }
    }

    val status = when {
        unavailable -> "Calendario da aggiornare"
        loading -> "Controllo il calendario…"
        byDate.isEmpty() -> "Nessun incastro nei prossimi 7 giorni"
        byDate.size == 1 -> "1 giorno da controllare"
        else -> "${byDate.size} giorni da controllare"
    }
    val count = if ((unavailable || loading) && byDate.isEmpty()) "--" else byDate.size.toString().padStart(2, '0')
    var expanded by remember { mutableStateOf(false) }
    AilaCard {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Apri incastri in agenda") {
                expanded = true
            }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(count, style = nothingDisplayStyle(32, 38, dots = true),
                color = if (hasConfirmed) AppTheme.TintRedInk else AppTheme.TextDark)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Incastri in agenda", style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
                Text(status, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextMuted)
        }
    }
    if (expanded) AilaBottomSheet(onDismissRequest = { expanded = false }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Incastri in agenda", Modifier.weight(1f), style = nothingDisplayStyle(24, 32),
                    color = AppTheme.TextDark)
                AilaIconButton("Chiudi incastri", { expanded = false }) { AppIcons.Close(Modifier.size(18.dp), it) }
            }
            if (byDate.isEmpty() || loading || unavailable) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            }
            for ((date, dayConflicts) in byDate) {
                Text(formatItalianDateWithWeekday(date), style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
                for (conflict in dayConflicts) {
                    CalendarConflictNotice(conflict, onClick = {
                        expanded = false
                        onOpenEvent(conflict.first)
                    })
                }
            }
            AilaSecondaryButton("Apri calendario", onClick = { expanded = false; onOpenCalendar() })
        }
    }
}
