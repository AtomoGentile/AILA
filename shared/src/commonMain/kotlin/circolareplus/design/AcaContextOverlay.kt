package circolareplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable fun AcaContextOverlay(thinking:Boolean,answer:String?,onClose:()->Unit,onOpenChat:()->Unit,onSend:(String,Boolean)->Unit,contextPreview:String="",providerLabel:String="",action:circolareplus.ai.assistant.AssistantMessage?=null,onConfirm:()->Unit={},onCancel:()->Unit={}){
 val focus=remember{FocusRequester()}
 LaunchedEffect(Unit){focus.requestFocus()}
 var question by remember{mutableStateOf("")}
 var includeContext by remember(contextPreview,providerLabel){mutableStateOf(false)}
 Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).padding(12.dp)
  .background(AppTheme.CardSurface,RoundedCornerShape(28.dp)).heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Text("AILA ACA",style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
   TextButton(onClick=onClose){Text("Chiudi")}
  }
  Row{Checkbox(includeContext,{includeContext=it});Text("Includi il contesto della schermata",color=AppTheme.TextDark)}
  if(includeContext)Text("Provider: $providerLabel",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
  if(includeContext)Text(contextPreview.take(6000),Modifier.heightIn(max=100.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
  OutlinedTextField(question,{question=it},placeholder={Text("Chiedi su ciò che stai guardando…")},modifier=Modifier.fillMaxWidth().focusRequester(focus),maxLines=3)
  if(thinking)Text("AILA sta lavorando…",color=AppTheme.PrimaryBlue)
  answer?.let{Text(it,Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),color=AppTheme.TextDark)}
  action?.takeIf{it.actionPlan!=null}?.let{circolareplus.ui.screens.AssistantActionPreview(message=it,isBusy=thinking,onConfirm=onConfirm,onCancel=onCancel,onRecover={})}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(enabled=question.isNotBlank()&&!thinking,onClick={val q=question;question="";onSend(q,includeContext)}){Text("Invia")}
   TextButton(onClick=onOpenChat){Text("Apri la conversazione")}
  }
 }
}
