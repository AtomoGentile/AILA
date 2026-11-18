package circolareplus.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaBottomSheet
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.MAX_FOCUS_SECONDS
import circolareplus.design.NothingExperience
import circolareplus.design.nothingDisplayStyle
import circolareplus.domain.model.CalendarEvent
import circolareplus.platform.currentTimeMillis
import circolareplus.platform.localUtcOffsetMillis
import circolareplus.util.civilFromEpochMillis
import circolareplus.util.daysBetween
import circolareplus.util.daysInMonth
import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.parseIsoDate
import circolareplus.util.parseTimeToMinutes
import kotlinx.coroutines.delay

private const val MILLIS_PER_DAY = 86_400_000L
private const val PREPARATION_MINUTES = 5
private val ClockTime = Regex("(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?")

internal data class GlyphAppointment(
    val event: CalendarEvent,
    val startsAt: Long,
    val wallClockMinute: Int
) {
    val preparationAt: Long get() = startsAt - PREPARATION_MINUTES * 60_000L
}

internal fun nextGlyphAppointment(
    events: List<CalendarEvent>,
    now: Long,
    utcOffset: (Long) -> Long = ::localUtcOffsetMillis
): GlyphAppointment? {
    val offsetNow = utcOffset(now)
    val localNow = now + offsetNow
    val today = civilFromEpochMillis(localNow)
    val midnight = localNow - localNow % MILLIS_PER_DAY
    return events.asSequence()
        .filter { it.isRelevantToMe != false }
        .mapNotNull { event ->
            val date = parseIsoDate(event.date) ?: return@mapNotNull null
            if (date.day > daysInMonth(date.year, date.month)) return@mapNotNull null
            val time = event.time?.trim()?.takeIf(ClockTime::matches) ?: return@mapNotNull null
            val minute = parseTimeToMinutes(time) ?: return@mapNotNull null
            val seconds = time.split(':').getOrNull(2)?.toInt() ?: 0
            val wallTime = midnight + daysBetween(today, date) * MILLIS_PER_DAY + minute * 60_000L + seconds * 1000L
            val startsAt = wallTime - utcOffset(wallTime - offsetNow)
            // Skip nonexistent wall times, for example during a daylight saving transition.
            if (startsAt <= now || startsAt + utcOffset(startsAt) != wallTime) return@mapNotNull null
            GlyphAppointment(event, startsAt, minute)
        }.minByOrNull { it.startsAt }
}

private fun clockLabel(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun GlyphPreparationSheet(
    events: List<CalendarEvent>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenCalendar: () -> Unit
) {
    var clockRefresh by remember { mutableIntStateOf(0) }
    val now by produceState(currentTimeMillis(), clockRefresh) {
        while (true) {
            val current = currentTimeMillis()
            value = current
            delay(60_000L - current % 60_000L)
        }
    }
    val appointment = remember(events, now) { nextGlyphAppointment(events, now) }
    val focus = NothingExperience.focus
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Glyph", Modifier.weight(1f), style = nothingDisplayStyle(28, 38), color = AppTheme.TextDark)
                AilaIconButton("Personalizza Glyph", onOpenStudio) { AppIcons.Sliders(Modifier.size(18.dp), it) }
                AilaIconButton("Chiudi Glyph", onDismiss) { AppIcons.Close(Modifier.size(18.dp), it) }
            }
            if (appointment == null) {
                Text(
                    if (loading) "Caricamento degli impegni…" else "Nessun prossimo impegno con orario.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.TextMuted
                )
                AilaSecondaryButton("Apri calendario", onOpenCalendar)
            } else {
                Text("PROSSIMO IMPEGNO", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                Text(appointment.event.title, style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                Text(
                    "${formatItalianDateWithWeekday(appointment.event.date)} · ${clockLabel(appointment.wallClockMinute)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.TextMuted
                )
                val remaining = appointment.preparationAt - now
                when {
                    !focus.canStartPreparation -> AilaPrimaryButton("Apri Focus", onOpenFocus, fillMaxWidth = true)
                    remaining in 1L..MAX_FOCUS_SECONDS * 1000L -> {
                        val localEnd = appointment.preparationAt + localUtcOffsetMillis(appointment.preparationAt)
                        val endMinute = ((localEnd % MILLIS_PER_DAY) / 60_000L).toInt()
                        AilaPrimaryButton("Avvia Focus fino alle ${clockLabel(endMinute)}", onClick = {
                            val current = currentTimeMillis()
                            if (NothingExperience.startPreparation(current, appointment.preparationAt)) onOpenFocus()
                            else clockRefresh++
                        }, fillMaxWidth = true)
                        Text("Il Focus termina 5 minuti prima dell’impegno.",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                    remaining <= 0 -> Text("L’impegno inizia entro 5 minuti.",
                        style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                    else -> Text("Il Focus di preparazione si attiva quando mancano al massimo 95 minuti.",
                        style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                }
                AilaSecondaryButton("Apri impegno", { onOpenEvent(appointment.event) })
            }
        }
    }
}
