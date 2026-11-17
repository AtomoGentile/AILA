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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular
import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.today

internal data class NothingHomeActions(
    val search: () -> Unit, val gita: () -> Unit, val circulars: () -> Unit,
    val calendar: () -> Unit, val board: () -> Unit, val forum: () -> Unit,
    val polls: () -> Unit, val seats: () -> Unit,
    val openEvent: (CalendarEvent) -> Unit, val openCircular: (Int) -> Unit
)

/** One content overview and one navigation grid. No second copy of the Classic Home. */
@Composable
internal fun NothingHomeScreen(
    firstName: String, events: List<CalendarEvent>, latestCircular: Circular?,
    briefAllowed: Boolean, briefItems: List<AilaBriefItemDto>, briefLoading: Boolean,
    briefError: String?, onRefreshBrief: () -> Unit, onOpenBrief: (AilaBriefItemDto) -> Unit,
    onDismissBrief: (AilaBriefItemDto) -> Unit, onPlanStudy: (String) -> Unit,
    subjectColorsEnabled: Boolean, subjectColorOverrides: Map<String,String>, actions: NothingHomeActions,
    eventsLoading: Boolean, circularsLoading: Boolean
) {
    var studio by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(false) }
    var allBrief by remember { mutableStateOf(false) }
    val date = today()
    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)
        .verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(horizontal=20.dp).padding(top=12.dp,bottom=24.dp+LocalBottomBarPadding.current),
        verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("AILA / NOTHING",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
            AilaIconButton("Cerca",actions.search) {AppIcons.Search(Modifier.size(18.dp),it)}
            AilaIconButton("Gita",actions.gita) {AppIcons.Bus(Modifier.size(18.dp),it)}
            LocalProfileEntry.current?.let {AilaProfileButton(it)}
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Ciao, $firstName",style=nothingDisplayStyle(30,40),color=AppTheme.TextDark)
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
                    if(briefItems.isNotEmpty()) AilaTextButton({allBrief=true}) {Text(if(briefItems.size>2) "Tutte (${briefItems.size})" else "Apri Brief")}
                }
                when {
                    briefLoading && briefItems.isEmpty() -> Text("Aggiornamento…",color=AppTheme.TextMuted)
                    briefError != null -> {
                        Text(briefError,color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
                        AilaTextButton(onRefreshBrief) {Text("Riprova")}
                    }
                    briefItems.isEmpty() -> Text("Sei in pari.",color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
                }
                briefItems.take(2).forEachIndexed { index,item ->
                    if(index>0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).clickable(role=Role.Button) {onOpenBrief(item)}.padding(vertical=8.dp),
                            verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            if(subjectColorsEnabled && item.kind==AilaBriefKind.EVENT) {
                                SubjectColors.choiceForSubject(item.title,subjectColorOverrides)?.let { color ->
                                    Box(Modifier.size(6.dp).background(color.color,CircleShape))
                                }
                            }
                            Text(when(item.kind) { AilaBriefKind.EVENT -> item.date ?: "CALENDARIO"; AilaBriefKind.CIRCULAR -> "CIRCOLARE N. ${item.sourceId}"; else -> item.actionLabel.uppercase() },
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
                    Text("${event.date}${event.time?.let {" · $it"}.orEmpty()}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                } else Text(if(eventsLoading) "Caricamento…" else "Nessun impegno in programma.",color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
            }
        }
        val links=listOf(
            Triple("Circolari",NothingSymbol.DOCUMENT,actions.circulars),Triple("Calendario",NothingSymbol.CALENDAR,actions.calendar),
            Triple("Bacheca",NothingSymbol.CHAT,actions.board),Triple("Pere",NothingSymbol.CHAT,actions.forum),
            Triple("Sondaggi",NothingSymbol.POLL,actions.polls),Triple("Posti",NothingSymbol.SEAT,actions.seats))
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            links.chunked(2).forEach { pair ->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.height(IntrinsicSize.Min)) {
                    pair.forEach { (label,symbol,open) ->
                        Row(Modifier.weight(1f).fillMaxHeight().heightIn(min=64.dp).clip(RoundedCornerShape(20.dp)).background(AppTheme.TrackFill)
                            .clickable(role=Role.Button,onClick=open).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            NothingDotIcon(symbol,Modifier.size(24.dp))
                            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=AppTheme.TextDark)
                        }
                    }
                }
            }
        }
        if(latestCircular != null && (!briefAllowed || briefItems.none {it.kind==AilaBriefKind.CIRCULAR && it.sourceId==latestCircular.number.toString()})) {
            Column(Modifier.fillMaxWidth().clickable(role=Role.Button) {actions.openCircular(latestCircular.number)}.padding(vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("ULTIMA CIRCOLARE / ${latestCircular.number}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                Text(latestCircular.title,style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextDark)
            }
        } else if(circularsLoading && latestCircular == null) Text("Aggiornamento circolari…",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
        if(NothingExperience.preferences.showFocusWidget) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                AilaTextButton({studio=true},Modifier.weight(1f).semantics {contentDescription="Personalizza Glyph"}) {Text("Glyph")}
                AilaSecondaryButton(if(NothingExperience.focus.running) formatFocusTime(NothingExperience.focus.remaining(NothingExperience.nowMillis)) else "Focus",{focus=true},modifier=Modifier.weight(1f))
            }
        }
    }
    if(studio) NothingGlyphStudio {studio=false}
    if(focus) AilaBottomSheet(onDismissRequest={focus=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Focus",Modifier.weight(1f),style=nothingDisplayStyle(28,38),color=AppTheme.TextDark)
                AilaIconButton("Chiudi Focus",{focus=false}) {AppIcons.Close(Modifier.size(18.dp),it)}
            }
            NothingFocusWidget(Modifier.widthIn(max=360.dp).fillMaxWidth())
        }
    }
    if(allBrief) AilaBottomSheet(onDismissRequest={allBrief=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Per te",Modifier.weight(1f),style=nothingDisplayStyle(28,38),color=AppTheme.TextDark)
                AilaIconButton("Chiudi riepilogo",{allBrief=false}) {AppIcons.Close(Modifier.size(18.dp),it)}
            }
            AilaBriefCard(briefItems,onDismissBrief,{allBrief=false;onOpenBrief(it)},
                {allBrief=false;onPlanStudy(it)},briefLoading,briefError,onRefreshBrief,
                subjectColorsEnabled=subjectColorsEnabled,subjectColorOverrides=subjectColorOverrides)
        }
    }
}
