@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import circolareplus.design.*

@Composable
internal fun NothingSeatMapContent(assignments: List<DeskAssignment>, studentsMap: Map<String,User>,
    focusedStudentId: String?, representative: Boolean, generating: Boolean,
    onManage: ()->Unit, search: @Composable ()->Unit, management: @Composable ()->Unit,
    modifier: Modifier=Modifier) {
    var reading by remember {mutableStateOf(false)}
    var manage by remember {mutableStateOf(false)}
    var detail by remember {mutableStateOf<DeskAssignment?>(null)}
    val cells=remember(assignments) {deskGridCells(assignments)}
    Column(modifier.fillMaxWidth()) {
        search()
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(generating) "Ricerca in corso…" else "${assignments.count {!it.isVacantDesk}} banchi",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
            if(assignments.isNotEmpty()) AilaTextButton({reading=!reading}) {Text(if(reading) "Vista aula" else "Leggi nomi")}
            if(representative) AilaTextButton({onManage();manage=true}) {Text("Gestione")}
        }
        if(assignments.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                AilaEmptyState("Nessuna mappa pubblicata",if(representative) "Apri Gestione per raccogliere le preferenze e preparare la disposizione." else "La disposizione comparirà qui dopo la pubblicazione.")
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(AppTheme.Hairline))
                Text("LAVAGNA E CATTEDRA",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                Box(Modifier.weight(1f).height(1.dp).background(AppTheme.Hairline))
            }
            if(reading) Text("Scorri la mappa per leggere tutti i banchi.",Modifier.padding(horizontal=20.dp).padding(bottom=8.dp),style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=20.dp).padding(bottom=16.dp+LocalBottomBarPadding.current)) {
                val columns=cells.first.coerceAtLeast(1)
                val available=maxWidth
                val planWidth=if(reading) maxOf(available, (164*columns+8*(columns-1)).dp) else available
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(AppTheme.TrackFill)
                    .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                    Column(Modifier.width(planWidth).padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        cells.second.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach { cell ->
                                    if(cell==null || cell.value.isVacantDesk) Box(Modifier.weight(1f).fillMaxHeight())
                                    else {
                                        val desk=cell.value
                                        val requester=remember(desk.row,desk.column) {BringIntoViewRequester()}
                                        val active=focusedStudentId != null && focusedStudentId in listOf(desk.studentAId,desk.studentBId,desk.studentCId)
                                        LaunchedEffect(active,reading) {if(active) {withFrameNanos {};requester.bringIntoView()}}
                                        SeatMapDeskCard(desk,studentsMap,desk.hasThirdSeat,
                                            Modifier.weight(1f).fillMaxHeight().bringIntoViewRequester(requester)
                                                .clickable(role=Role.Button) {detail=desk}
                                                .semantics {contentDescription="Dettaglio banco F${desk.row+1}C${desk.column+1}"},
                                            focusedStudentId=focusedStudentId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(manage) AilaBottomSheet(onDismissRequest={manage=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Gestione aula",Modifier.weight(1f),style=nothingDisplayStyle(28,38),color=AppTheme.TextDark)
                AilaIconButton("Chiudi gestione aula",{manage=false}) {AppIcons.Close(Modifier.size(18.dp),it)}
            }
            management()
        }
    }
    detail?.let {desk -> AilaBottomSheet(onDismissRequest={detail=null}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Fila ${desk.row+1} · Banco ${desk.column+1}",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark)
                AilaIconButton("Chiudi dettaglio banco",{detail=null}) {AppIcons.Close(Modifier.size(18.dp),it)}
            }
            val ids=if(desk.hasThirdSeat) listOf(desk.studentAId,desk.studentBId,desk.studentCId) else listOf(desk.studentAId,desk.studentBId)
            ids.forEachIndexed {index,id ->
                Text("POSTO ${index+1}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                Text(if(id==null) "Vuoto" else studentsMap[id]?.let {"${it.firstName} ${it.lastName}"} ?: "Studente non disponibile",style=MaterialTheme.typography.bodyLarge,color=AppTheme.TextDark)
            }
        }
    } }
}

@Composable
internal fun NothingDeskCard(desk: DeskAssignment, students: Map<String,User>, third: Boolean,
    focusedId: String?, numbers: Boolean, modifier: Modifier) {
    val ids=if(third) listOf(desk.studentAId,desk.studentBId,desk.studentCId) else listOf(desk.studentAId,desk.studentBId)
    val focused=focusedId != null && focusedId in ids
    val shape=RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(AppTheme.CardSurface)
        .border(if(focused)2.dp else 1.dp,if(focused)AppTheme.PrimaryBlue else AppTheme.Hairline,shape)
        .padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("F${desk.row+1} · C${desk.column+1}"+(if(third) " / 3" else ""),style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
        ids.forEachIndexed { index,id ->
            val active=id != null && id==focusedId
            val ink=if(active)AppTheme.BackgroundLight else AppTheme.TextDark
            if(index>0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).then(if(active)Modifier.background(AppTheme.TextDark).padding(4.dp) else Modifier),
                verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                if(numbers) Text("${index+1}",style=MaterialTheme.typography.labelSmall,color=if(active)ink else AppTheme.TextMuted)
                Column(Modifier.weight(1f)) {
                    val user=students[id]
                    Text(user?.firstName ?: if(id==null)"Vuoto" else "Studente",style=MaterialTheme.typography.bodyMedium,color=ink,
                        maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if(user != null) Text(user.lastName,style=MaterialTheme.typography.bodySmall,color=if(active)ink else AppTheme.TextMuted,
                        maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
