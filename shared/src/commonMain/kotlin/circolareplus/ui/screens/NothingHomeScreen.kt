@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular
import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.today

internal data class NothingHomeActions(
    val search: () -> Unit,
    val calendar: () -> Unit,
    val openEvent: (CalendarEvent) -> Unit,
    val openCircular: (Int) -> Unit,
    val shortcuts: HomeShortcuts = HomeShortcuts()
)

/** One content overview and one navigation grid. No second copy of the Classic Home. */
@Composable
internal fun NothingHomeScreen(
    firstName: String, events: List<CalendarEvent>, latestCircular: Circular?,
    briefAllowed: Boolean, briefItems: List<AilaBriefItemDto>, briefLoading: Boolean,
    briefError: String?, onRefreshBrief: () -> Unit, onOpenBrief: (AilaBriefItemDto) -> Unit,
    onDismissBrief: (AilaBriefItemDto) -> Unit, onPlanStudy: (String) -> Unit,
    subjectColorsEnabled: Boolean, subjectColorOverrides: Map<String,String>, actions: NothingHomeActions,
    eventsLoading: Boolean, circularsLoading: Boolean,
    conflicts: List<CalendarConflict> = emptyList(),
    calendarUnavailable: Boolean = false,
    homeVisible: Boolean = true,
    glyphConflicts: List<CalendarConflict> = conflicts,
    profileEntry: AilaProfileEntry? = null
) {
    var allBrief by remember { mutableStateOf(false) }
    val date = today()
    val firstCircular = briefItems.firstOrNull { it.kind == AilaBriefKind.CIRCULAR }
    val previewItems = briefItems.take(2).let { leading ->
        if (firstCircular != null && firstCircular !in leading) leading.take(1) + firstCircular else leading
    }
    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)
        .verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(horizontal=20.dp).padding(top=12.dp,bottom=24.dp+LocalBottomBarPadding.current),
        verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("AILA / NOTHING",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
            AilaIconButton("Cerca",actions.search) {AppIcons.Search(Modifier.size(18.dp),it)}
            profileEntry?.let { AilaProfileButton(it) }
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Ciao, $firstName",style=nothingDisplayStyle(30,40),color=AppTheme.TextDark)
            HomeShortcutRow(actions.shortcuts)
            Row(Modifier.heightIn(min=44.dp).clickable(role=Role.Button,onClick=actions.calendar),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(5.dp).background(AppTheme.PrimaryBlue,CircleShape))
                Text("OGGI",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                Text("${date.day} ${ITALIAN_MONTHS[date.month].uppercase()}",style=MaterialTheme.typography.labelMedium,color=AppTheme.TextDark)
            }
        }
        // Brief and the calendar overview are alternatives, so an event isn't repeated twice.
        if(briefAllowed) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.CardSurface).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Per te",Modifier.weight(1f),style=nothingDisplayStyle(24,32),color=AppTheme.TextDark)
                    if(briefItems.isNotEmpty() || conflicts.isNotEmpty()) AilaTextButton({allBrief=true}) {Text(if(briefItems.size>2) "Tutte (${briefItems.size})" else "Apri Brief")}
                }
                conflicts.take(2).forEach { conflict ->
                    CalendarConflictNotice(conflict, includeDate = true, onClick = { actions.openEvent(conflict.first) })
                }
                when {
                    briefLoading && briefItems.isEmpty() -> Text("Aggiornamento…",color=AppTheme.TextMuted)
                    briefError != null -> {
                        Text(briefError,color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
                        AilaTextButton(onRefreshBrief) {Text("Riprova")}
                    }
                    briefItems.isEmpty() && conflicts.isEmpty() -> {
                        val event=events.firstOrNull()
                        if(event==null) Text("Sei in pari.",color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
                        else Column(Modifier.fillMaxWidth().clickable(role=Role.Button) {actions.openEvent(event)}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("PROSSIMO IMPEGNO",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                            Text(event.title,style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
                            Text(circolareplus.util.formatItalianDateWithWeekday(event.date),style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
                        }
                    }
                }
                previewItems.forEachIndexed { index,item ->
                    if(index>0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).clickable(role=Role.Button) {onOpenBrief(item)}.padding(vertical=8.dp),
                            verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            if(subjectColorsEnabled && item.kind==AilaBriefKind.EVENT) {
                                SubjectColors.choiceForSubject(item.title,subjectColorOverrides)?.let { choice ->
                                    val marker = if (choice.key == "black" && AppTheme.isDarkMode) SubjectColors.darkBlackInk
                                        else choice.color
                                    Box(Modifier.size(6.dp).background(marker, CircleShape))
                                }
                            }
                            Text(when(item.kind) { AilaBriefKind.EVENT -> item.date?.let {circolareplus.util.formatItalianDateWithWeekday(it)} ?: "CALENDARIO"; AilaBriefKind.CIRCULAR -> "CIRCOLARE N. ${item.sourceId}"; AilaBriefKind.BUREAUCRACY -> nothingBriefLabel(item); else -> item.actionLabel.uppercase() },
                                style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                            Text(item.title,style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
                        }
                        AilaIconButton("Non ora: ${item.title}",{onDismissBrief(item)}) {AppIcons.Close(Modifier.size(16.dp),it)}
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.CardSurface).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Prossimo impegno",style=MaterialTheme.typography.labelMedium,color=AppTheme.TextMuted)
                val event=events.firstOrNull()
                if(event != null) Column(Modifier.fillMaxWidth().clickable(role=Role.Button) {actions.openEvent(event)}.padding(vertical=8.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(event.title,style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
                    Text("${circolareplus.util.formatItalianDateWithWeekday(event.date)}${event.time?.let {" · $it"}.orEmpty()}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                } else Text(if(eventsLoading) "Caricamento…" else "Nessun impegno in programma.",color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
            }
        }
        if (NothingExperience.preferences.showCalendarGlyph) {
            CalendarConflictGlyph(
                conflicts = glyphConflicts,
                loading = eventsLoading,
                unavailable = calendarUnavailable,
                homeVisible = homeVisible,
                onOpenEvent = actions.openEvent,
                onOpenCalendar = actions.calendar
            )
        }
        if(latestCircular != null && (!briefAllowed || briefItems.none {it.kind==AilaBriefKind.CIRCULAR && it.sourceId==latestCircular.number.toString()})) {
            Column(Modifier.fillMaxWidth().clickable(role=Role.Button) {actions.openCircular(latestCircular.number)}.padding(vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("ULTIMA CIRCOLARE / ${latestCircular.number}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                Text(latestCircular.title,style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextDark)
            }
        } else if(circularsLoading && latestCircular == null) Text("Aggiornamento circolari…",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
    }
    if(allBrief) AilaBottomSheet(onDismissRequest={allBrief=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Per te",Modifier.weight(1f),style=nothingDisplayStyle(28,38),color=AppTheme.TextDark)
                AilaIconButton("Chiudi riepilogo",{allBrief=false}) {AppIcons.Close(Modifier.size(18.dp),it)}
            }
            AilaBriefCard(briefItems,onDismissBrief,{allBrief=false;onOpenBrief(it)},
                {allBrief=false;onPlanStudy(it)},briefLoading,briefError,onRefreshBrief,
                subjectColorsEnabled=subjectColorsEnabled,subjectColorOverrides=subjectColorOverrides,
                conflicts=conflicts,onOpenConflict={allBrief=false;actions.openEvent(it.first)})
        }
    }
}
