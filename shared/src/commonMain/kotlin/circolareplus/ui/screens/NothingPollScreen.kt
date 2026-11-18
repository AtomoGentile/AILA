package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.algorithms.InterrogationVoteType
import circolareplus.design.*

/** Dates are the main content. Rules and management have explicit, separate entry points. */
@Composable
internal fun NothingPollContent(subject: String, slots: List<SlotUiItem>, bonus: Int, globalAssignment: Boolean,
    submitted: Boolean, submittedCount: Int, totalStudents: Int, deadline: String?, expired: Boolean,
    submitting: Boolean, canVote: Boolean, representative: Boolean,
    onVote: (String,InterrogationVoteType)->Unit, onSubmit: ()->Unit, onReopen: ()->Unit,
    onClose: ()->Unit, onDelete: ()->Unit) {
    var help by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    val voted=slots.count {it.currentVote != null}
    val darkRedLeft=2-slots.count {it.currentVote==InterrogationVoteType.DARK_RED}
    LazyColumn(Modifier.fillMaxSize().background(AppTheme.BackgroundLight),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=24.dp+LocalBottomBarPadding.current),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(subject,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark)
                    AilaTextButton({help=true}) {Text("Come votare")}
                    if(representative) Box {
                        AilaIconButton("Gestisci sondaggio",{menu=true}) {AppIcons.Sliders(Modifier.size(18.dp),it)}
                        AilaDropdownMenu(menu,{menu=false}) {
                            AilaDropdownMenuItem({Text("Chiudi sondaggio")},{menu=false;onClose()})
                            AilaDropdownMenuItem({Text("Elimina sondaggio")},{menu=false;onDelete()})
                        }
                    }
                }
                Text(when {expired -> "Sondaggio chiuso";submitted -> "Scelte inviate";!canVote -> "Questo sondaggio non è rivolto a te.";else -> "Scegli le date che vanno bene per te."},
                    style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted)
                AilaProgressBar(if(slots.isEmpty())0f else voted.toFloat()/slots.size)
                Text("$voted / ${slots.size} date valutate"+(if(totalStudents>0) " · $submittedCount / $totalStudents inviati" else ""),
                    style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
                if(deadline != null && !expired) Text("Entro il $deadline",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
                if(bonus>0) Text("Bonus sacrificio: +$bonus",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextDark)
                Spacer(Modifier.height(4.dp))
            }
        }
        if(slots.isEmpty()) item { AilaEmptyState("Nessuna data proposta","Le date compariranno qui quando verranno aggiunte al sondaggio.") }
        items(slots,key={it.slotId}) {slot ->
            SlotRowItem(slot,darkRedLeft,canVote && !submitted && !expired && !submitting,{onVote(slot.slotId,it)})
        }
        if(canVote) item {
            Column(Modifier.fillMaxWidth().padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when {
                    expired -> Text(if(submitted) "Scelte inviate. Il sondaggio è chiuso." else "Il sondaggio è chiuso.",style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted)
                    submitted -> AilaSecondaryButton("Modifica le mie scelte",onReopen,modifier=Modifier.fillMaxWidth())
                    else -> {
                        Text(if(voted==slots.size && slots.isNotEmpty())"Tutte le date sono state valutate." else "${slots.size-voted} ${if(slots.size-voted==1)"data da valutare"else"date da valutare"}",
                            style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
                        AilaPrimaryButton(if(submitting)"Invio…" else "Invia le mie scelte",onSubmit,
                            enabled=slots.isNotEmpty() && voted==slots.size && !submitting,fillMaxWidth=true)
                    }
                }
            }
        }
    }
    if(help) AilaAlertDialog(onDismissRequest={help=false},title={Text("Come votare")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Ci sto: la data va bene. Indifferente: non hai preferenze. Meglio di no: preferisci evitarla. Impossibile: puoi usarlo per un massimo di due date.")
            Text("Valuta tutte le date e premi Invia le mie scelte. Puoi modificarle finché il sondaggio è aperto.")
            Text((if(globalAssignment)"Le assegnazioni considerano tutto il calendario. " else "")+"Il bonus premia chi accetta date rifiutate dagli altri. I meglio di no contano pienamente fino al 40% delle date; oltre questa soglia pesano meno.")
        }
    },confirmButton={AilaTextButton({help=false}){Text("Ho capito")}})
}
