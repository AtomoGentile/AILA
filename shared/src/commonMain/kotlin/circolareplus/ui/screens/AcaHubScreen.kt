package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.ai.acaDeviceCapabilities
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.*
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AcaHubScreen(onBack:()->Unit,onOpenAssistant:()->Unit,onOpenForum:()->Unit,onOpenContent:(IncomingContent)->Unit,onStudyPlan:()->Unit,onOpenAiSettings:()->Unit,onOpenLocalFiles:()->Unit) {

 var error by remember{mutableStateOf<String?>(null)};var recording by remember{mutableStateOf(false)}
 val picker=rememberContentPicker({error=it},onOpenContent)
 val recorder=rememberVoiceRecorder({recording=it},{error=it},onOpenContent)
 Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).windowInsetsPadding(WindowInsets.safeDrawing)) {
  AilaBackBar("AILA ACA",onBackClick=onBack)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
    AilaAssistantHalo(size=64.dp)
    Column{Text("Il contesto diventa un aiuto.",style=MaterialTheme.typography.headlineSmall,color=AppTheme.TextDark)
     Text("Chiedi, approfondisci e prepara le azioni da confermare.",color=AppTheme.TextMuted)}
   }
   Button(onClick=onOpenAssistant,modifier=Modifier.fillMaxWidth()){Text("Parla con AILA")}
   OutlinedButton(onClick=onOpenForum,modifier=Modifier.fillMaxWidth()){Text("Educazione tra pere")}
   OutlinedButton(onClick=onOpenLocalFiles,modifier=Modifier.fillMaxWidth()){Text("File sul dispositivo")}
   OutlinedButton(onClick=picker,modifier=Modifier.fillMaxWidth()){Text("Chiedi su un documento, un’immagine o un audio")}
   OutlinedButton(onClick={if(recording)recorder.stop()else recorder.start()},modifier=Modifier.fillMaxWidth()){
    Text(if(recording)"Termina registrazione"else"Registra un vocale")
   }
   if(recording)TextButton(onClick=recorder.cancel){Text("Annulla registrazione")}
   OutlinedButton(onClick=onStudyPlan,modifier=Modifier.fillMaxWidth()){Text("Prepara un piano di studio")}
   error?.let{Text(it,color=AppTheme.TintRedInk)}
   HorizontalDivider(color=AppTheme.Hairline)
   Text("L’aiuto che scegli tu",style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark)
   Text("AI e ACA sono opzionali. Nella sezione AI delle impostazioni puoi attivarle, scegliere i servizi e leggere l’informativa privacy.",color=AppTheme.TextMuted)
   OutlinedButton(onClick=onOpenAiSettings,modifier=Modifier.fillMaxWidth()){Text("Apri impostazioni AI")}
   Text("Sul server: 90 giorni fino a 3 MB, 7 giorni oltre. Le copie sul dispositivo restano fino alla cancellazione manuale. Discussioni e messaggi rimangono. Gli eventi si creano, modificano o eliminano solo dopo la tua conferma.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
  }
 }
}
