package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.ai.AcaPolicyState
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.AcaPreferencesDto
import circolareplus.data.remote.dto.AcaCapabilitiesDto
import circolareplus.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All AI and ACA switches are explicit, per-account, and disabled by default. */
@Composable fun AiPreferencesControls(){
 var localTranscriptionOnly by remember { mutableStateOf(AppContainer.settings.localTranscriptionOnly) }
 var localTranscription by remember { mutableStateOf(AppContainer.settings.preferLocalTranscription) }
 val scope=rememberCoroutineScope();val live=AcaPolicyState.preferences
 var draft by remember{mutableStateOf<AcaPreferencesDto?>(null)};val p=draft?:live
 val saveLock=remember{Mutex()}
 var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 var capabilities by remember{mutableStateOf(AcaCapabilitiesDto())};var privacy by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){try{capabilities=AppContainer.acaRepository.preferences().capabilities}
  catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}
 fun save(next:AcaPreferencesDto){draft=next;AppContainer.settings.cachedAiPreferences=next;saving=true;error=null;scope.launch{saveLock.withLock{try{val result=AppContainer.acaRepository.savePreferences(next);if(result.capabilities.cloud)capabilities=result.capabilities;if(draft==next){draft=null;error=null}}
  catch(e:CancellationException){throw e}catch(e:Exception){error="La scelta resta salvata sul dispositivo e verrà sincronizzata appena possibile. ${e.message.orEmpty()}"}finally{saving=false}}}}
 Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
  AilaSectionTitle("AI e servizi")
  AilaCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   AiControlRow("Attiva l’AI","Se disattivata, puoi continuare a usare circolari, calendario e forum senza avviare elaborazioni AI.",p.aiEnabled,true){save(p.copy(aiEnabled=it))}
   AiControlRow("Attiva ACA","Opzionale. Aggiunge strumenti contestuali e azioni sul calendario da confermare. Scegli i servizi qui sotto.",p.acaEnabled,p.aiEnabled){save(p.copy(acaEnabled=it))}
   AiControlRow("Apri ACA tenendo premuto","Tieni premuto brevemente in un’area libera di qualsiasi schermata. Scorrimento, controlli e selezione del testo hanno precedenza.",p.holdToOpen,p.aiEnabled&&p.acaEnabled){save(p.copy(holdToOpen=it))}
  }}
  AilaSectionTitle("Servizi da attivare")
  AilaCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   val services=listOf("CIRCULARS" to "Analisi delle circolari","CHAT" to "Chat con AILA","CALENDAR" to "Azioni sul calendario",
    "DOCUMENTS" to "Documenti e testi selezionati","AUDIO" to "Trascrizione dei vocali","IMAGES" to "Analisi delle immagini",
    "FORUM" to "Riassunti e @perabot nel forum","FORUM_SUGGESTIONS" to "Proposte automatiche dalle conversazioni","STUDY_PLAN" to "Piani di studio","NOW_NUDGE" to "AILA brief nella home")
   services.forEach{(id,label)->val aca=id !in setOf("CIRCULARS","CHAT")
    AiControlRow(label,if(id=="FORUM_SUGGESTIONS")"Opzionale: con il consenso cloud, analizza i messaggi della discussione aperta e propone impegni visibili alla classe. Nessun inserimento senza conferma."else if(id=="NOW_NUDGE")"Impegni personali, circolari pertinenti, sondaggi e novità della classe. Non avvia modelli in automatico."else if(aca)"Richiede ACA attiva."else"Servizio AI di base.",id in p.services,p.aiEnabled&&(!aca||p.acaEnabled)){enabled->
     val next=if(enabled)(p.services+id).distinct()else p.services-id
     save(p.copy(services=next,nudgesEnabled=if(id=="NOW_NUDGE")enabled else p.nudgesEnabled))
    }
   }
  }}
  AilaSectionTitle("Consensi e instradamento")
  AilaCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   AiControlRow("Consenti il cloud","Permetti l’elaborazione dei contenuti che scegli da parte del servizio AILA. Il dispositivo resta disponibile quando supportato.",p.cloudConsent,p.aiEnabled){save(p.copy(cloudConsent=it))}
   AiControlRow("Consenti analisi degli allegati","Solo i file o i passaggi scelti. Prima di ogni richiesta il contenuto di riferimento è visibile.",p.attachmentConsent,p.aiEnabled){save(p.copy(attachmentConsent=it))}
   AiControlRow("Consenti trascrizione audio","Le trascrizioni generate sono separate dai vocali originali e possono contenere errori.",p.audioConsent,p.aiEnabled){save(p.copy(audioConsent=it))}
   AiControlRow("Preferisci trascrizione sul dispositivo","Usa il motore vocale del telefono e la lingua italiana installata, quando disponibili. Altrimenti il provider cloud richiede i consensi già previsti.",localTranscription,p.aiEnabled){localTranscription=it;AppContainer.settings.preferLocalTranscription=it;if(!it){localTranscriptionOnly=false;AppContainer.settings.localTranscriptionOnly=false}}
   AiControlRow("Solo locale per i vocali","Blocca il cloud per la trascrizione. Il testo ottenuto può essere inviato al provider scelto per la chat.",localTranscriptionOnly,p.aiEnabled){localTranscriptionOnly=it;AppContainer.settings.localTranscriptionOnly=it;if(it){localTranscription=true;AppContainer.settings.preferLocalTranscription=true}}
   if(localTranscription) Text(if(circolareplus.platform.isLocalAudioTranscriptionAvailable()) "Motore vocale locale rilevato: è richiesta la lingua italiana installata nelle impostazioni del telefono." else "Questo sistema non espone un motore locale utilizzabile per i file audio. Con Solo locale attivo non verrà usato il cloud.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)



   AiControlRow("Consenti provider esterni","Vale per le API personali e per un eventuale servizio avanzato. Le credenziali non vengono condivise con la classe.",p.externalConsent,p.aiEnabled){save(p.copy(externalConsent=it))}
  }}
  AilaCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   TextButton(onClick={privacy=!privacy}){Text(if(privacy)"Chiudi informativa AI e privacy"else"Informativa AI e privacy")}
   if(privacy){
    Text("AI e ACA sono opzionali. I servizi disattivati non avviano elaborazioni sul dispositivo o nel cloud. Disattivare una funzione non elimina gli eventi già confermati o i messaggi già pubblicati. I lettori autorizzati possono usare la propria AI sui messaggi condivisi; le proposte automatiche sono un’opzione separata, inizialmente disattivata.",color=AppTheme.TextMuted)
    Text("Le richieste cloud includono solo il contesto autorizzato necessario: messaggi, testi, file o dati AILA pertinenti. Valutazioni dei compagni e preferenze sociali sono escluse dal pianificatore ACA. Un cambio di provider richiede i relativi consensi: non vengono inviati silenziosamente contenuti più sensibili a un altro servizio.",color=AppTheme.TextMuted)
    Text("Allegati fino a 3 MB: 90 giorni. Allegati più grandi: 7 giorni. Audio: massimo 10 minuti e 20 MB. La scadenza riguarda il server: file e trascrizioni memorizzate lì vengono rimossi; i messaggi restano. Le copie salvate sul dispositivo restano fino alla cancellazione manuale. Il download automatico è una scelta nelle impostazioni generali. Gli pseudonimi del forum sono stabili per discussione e non espongono l’identità reale agli altri partecipanti.",color=AppTheme.TextMuted)
    Text("Le API personali sono separate dagli abbonamenti ChatGPT o Claude. L’AI può sbagliare: puoi verificare le fonti originali e ogni creazione, modifica o eliminazione di eventi richiede la tua conferma.",color=AppTheme.TextMuted)
   }
  }}
  if(saving)LinearProgressIndicator(Modifier.fillMaxWidth())
  error?.let{Text(it,color=AppTheme.TintRedInk)}
 }
}
@Composable private fun AiControlRow(title:String,description:String,value:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().toggleable(value=value,enabled=enabled,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleSmall,color=AppTheme.TextDark);Text(description,style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)}
  Switch(value,onCheckedChange=null,enabled=enabled)
 }
}
