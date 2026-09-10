# Cose da sistemare

Elenco vivo dei problemi aperti e del lavoro ancora mancante, aggiornato mano a mano.
Non è un elenco di feature nuove: sono buchi o rischi concreti nel codice esistente.

## 8/9 (quarta parte): aggiornata la build e sostituito il motore con LiteRT-LM

**Perche'.** MediaPipe `tasks-genai` legge solo modelli con tokenizer SentencePiece — in pratica la
sola famiglia Gemma. I tre Qwen fallivano il caricamento con `SentencePiece tokenizer is not found
in the model` **dopo** che l'utente aveva scaricato qualche giga, e la libreria e' in manutenzione
ferma alla 0.10.35: quel limite non sarebbe mai stato tolto. Non espone nemmeno un'API di tool
calling. Il sostituto indicato da Google, `com.google.ai.edge.litertlm`, non ha nessuno di questi
problemi, ma e' scritto in Kotlin con metadata 2.4 e non era leggibile dal compilatore 2.0.20.

**Cosa e' stato aggiornato.**

| | prima | dopo |
| --- | --- | --- |
| Kotlin | 2.0.20 | 2.4.20 |
| Compose Multiplatform | 1.6.11 | 1.11.1 |
| Android Gradle Plugin | 8.5.2 | 8.13.2 |
| Gradle | 8.9 | 8.14.5 |
| compileSdk | 34 | 36 |
| coroutines / serialization | 1.8.1 / 1.6.3 | 1.11.0 / 1.11.0 |
| Firebase BOM | 33.5.1 | 34.18.0 |
| motore AI locale | mediapipe:tasks-genai 0.10.35 | litertlm-android 0.17.0 |

**Non e' stato preso l'ultimo di tutto, ed e' voluto.** Compose Multiplatform 1.12.0 pretende AGP
9.1 e compileSdk 37, cioe' un altro salto maggiore sopra a questo. La 1.11.1 e' l'ultima che gira
su AGP 8.13, ed e' stata verificata compilando: anche la 1.10.3 passa, quindi c'e' margine di
ripiego se qualcosa emergesse.

**Quattro rotture incontrate, tutte piccole.**
1. `platform(libs.firebase.bom)` non esiste piu' dentro i blocchi `dependencies` dei source set
   KMP: va scritto `project.dependencies.platform(...)`.
2. Il BOM Firebase 34 ha ritirato gli artefatti `-ktx` (le API Kotlin sono state assorbite in
   quelli principali): `firebase-messaging-ktx` diventa `firebase-messaging`.
3. Compose Multiplatform non pubblica piu' per `iosX64`, il simulatore su Mac Intel. Il target e'
   stato tolto; restano il dispositivo vero e il simulatore su Apple Silicon.
4. `compileSdk` 34 non basta piu' per le librerie androidx tirate da Compose 1.11.

Il resto del codice — tutte le schermate, sei versioni minori di Compose — ha compilato senza una
modifica. Anche l'esclusione di `com.google.guava:listenablefuture` e' stata rimossa: serviva
soltanto a MediaPipe.

**Cosa cambia nel motore.** `LocalLlm.android.kt` e' riscritto sull'API Kotlin
(`Engine`/`Conversation`), che e' a coroutine e con lo streaming nativo: fermare la generazione
appena il JSON e' completo ora e' un `takeWhile` sul Flow, invece della cancellazione manuale che
richiedeva l'API Java. Sparisce anche la finestra di contesto dichiarata a mano — LiteRT-LM la
ricava dal file — e resta solo `maxOutputTokens`, che e' la cosa che serviva davvero.

**Catalogo: i Qwen sono tornati**, e sono i consigliati per fascia perche' a ogni taglia pesano
meno del Gemma corrispondente e sono addestrati al tool calling.

| Fascia | Consigliato | Peso |
| --- | --- | --- |
| fino a 4 GB | Qwen3.5 0.8B | 963 MB |
| 4-8 GB | Qwen3.5 2B | 2,1 GB |
| 8 GB o piu' | Qwen3.5 4B | 2,8 GB |

Gemma 4 E2B ed E4B restano selezionabili a mano. Verificati tutti e cinque: HTTP 200 anonimo,
`gated: false`, dimensioni identiche al byte a quelle dichiarate.

**Cosa e' stato verificato e cosa no.** `./gradlew :androidApp:clean :androidApp:assembleDebug`
passa da pulito; l'APK di debug scende da 79 a 74,5 MB (le native di LiteRT-LM pesano meno di
quelle di MediaPipe) e contiene arm64-v8a e x86_64. **Non e' stato eseguito nulla su un
telefono**: che i Qwen partano davvero, se la GPU si attivi con questo runtime e quanto ci metta
una circolare sono tutte cose da misurare sul campo. La build iOS resta non verificabile senza un
Mac, e in piu' ora ha un target in meno.

## 8/9 (terza parte): l'AI locale non finiva mai. Quattro cause, tutte trovate

**La generazione non aveva ne' un freno ne' un limite.** Si usava `generateResponse()`, che e'
bloccante e torna solo quando il modello decide di fermarsi. Con un modello piccolo quel momento
puo' non arrivare: dopo aver prodotto il JSON continua a commentarlo finche' non ha riempito tutta
la finestra di contesto — e tutto quel testo in piu' veniva comunque scartato da
`extractJsonObject`. Ora si genera **in streaming** (`generateResponseAsync`) e si taglia appena il
JSON e' completo, con un tetto di 90 secondi oltre il quale la classificazione si dichiara fallita
invece di restare appesa. In piu' `cancelGenerateResponseAsync` viene chiamata sempre, anche in
caso di taglio anticipato: senza, la generazione continuava in sottofondo tenendo occupato il
motore per la richiesta successiva.

**Il ciclo in background rubava il motore.** Era il difetto peggiore, ed era mio. Il motore e' uno
solo e le richieste sono in coda su un mutex: la circolare appena aperta finiva **dietro** a quella
che il ciclo aveva gia' cominciato in sottofondo, e doveva aspettare che quella finisse prima
ancora di iniziare. Con generazioni da decine di secondi, l'attesa vista da chi guardava lo schermo
raddoppiava. Con l'AI locale quel ciclo ora non gira affatto: si classifica quello che si apre,
quando lo si apre. In rete l'anticipo resta, perche' li' costa un secondo a circolare.

**Il prompt era troppo lungo.** 3.500 caratteri sono circa 1.100 token da leggere prima ancora di
cominciare a rispondere, e su CPU quella lettura e' il costo dominante. Portato a 1.800: le
circolari mettono destinatari, date e adempimenti nelle prime battute. Anche la finestra di
contesto e' scesa da 2048 a 1280 token, perche' decide quanta cache il motore alloca all'avvio e
quello spazio in piu' ora non serve.

**Non si sapeva su quale backend girasse.** Fra GPU e CPU c'e' quasi un fattore dieci in prefill
(3.808 contro 557 token/secondo su un S26 Ultra), quindi "e' lentissimo" ha due significati
completamente diversi a seconda del backend — e quell'informazione non usciva da nessuna parte.
Ora "Prova il modello" scrive su quale dei due sta girando. **Se dice CPU, e' quella la ragione
della lentezza**, e il passo successivo e' capire perche' la GPU non parte.

### Catalogo ridotto

Fuori **Gemma 4 12B**: non ha mai funzionato, e il suo model card lo spiega — e' l'unico della
famiglia senza nemmeno una riga di misure su Android, perche' Google lo presenta come modello da
computer. Sette giga di pesi su un telefono non erano una scommessa sensata.

Fuori **TinyLlama**: pesava 185 MB piu' di Qwen3.5 0.8B, e' del 2023, in italiano rende meno e non
sa produrre azioni per il calendario. Su ogni criterio era la scelta peggiore. Restava solo per
poter dire di avere un Llama.

Restano cinque modelli, tutti capaci di proporre eventi per il calendario:

| Modello | Peso |
| --- | --- |
| Qwen3.5 0.8B | 963 MB |
| Gemma 4 E2B | 2,6 GB |
| Qwen3.5 2B | 2,1 GB |
| Gemma 4 E4B | 3,7 GB |
| Qwen3.5 4B | 2,8 GB |

**La mappatura per fascia scende di un gradino** (fino a 4 GB → Qwen3.5 0.8B, 4-8 → Gemma 4 E2B,
8+ → Gemma 4 E4B): il problema riportato non e' stata la qualita' dei riassunti ma l'attesa, e a
parita' di fascia un modello piu' piccolo la dimezza. Si cambia in `LocalAiCatalog.recommendedFor`,
tre righe.

I 5,9 GB del vecchio 12B solo-GPU non restano bloccati sul telefono: la pulizia dei file orfani
aggiunta nel giro precedente li riconosce e le Impostazioni offrono di liberarli.

**Cosa aspettarsi ora.** Con Qwen3.5 0.8B su GPU una circolare dovrebbe stare in pochi secondi.
Se resta lenta, la prima cosa da guardare e' cosa risponde "Prova il modello": se dice CPU, il
problema e' l'accelerazione e non il modello.

## 8/9 (seconda parte): via i build solo-GPU, catalogo nuovo, azioni sul calendario

Nessun file backend toccato: l'endpoint per creare eventi c'era gia', con tanto di flag
`isAiGenerated` e avviso sui doppioni. Semplicemente non lo chiamava nessuno.

**Tolti i build `-gpu`.** Non si avviavano su nessun telefono provato, ed e' coerente con quello
che sono: file con i soli pesi preconfezionati per la GPU, senza niente che il backend CPU possa
caricare. Al loro posto sono entrati quattro modelli nuovi.

| Modello | Peso | Azioni sul calendario |
| --- | --- | --- |
| Gemma 4 E2B / E4B / 12B | 2,6 / 3,7 / 6,9 GB | si' |
| Qwen3.5 0.8B | 963 MB | si' |
| Qwen3.5 2B | 2,1 GB | si' |
| Qwen3.5 4B (mixed int4) | 2,8 GB | si' |
| TinyLlama 1.1B | 1,1 GB | no |

Verificato uno per uno: tutti rispondono HTTP 200 a un GET anonimo, tutti risultano `gated: false`
sull'API di HuggingFace, e le dimensioni dichiarate nel catalogo coincidono al byte con quelle
reali (le usa il controllo "il file scaricato e' completo").

**Sul Llama.** Llama 3.2 1B e 3B esistono su `litert-community` ma quei repo sono **gated**:
HuggingFace risponde 401 a chi non ha accettato la licenza da loggato, quindi l'app non li puo'
scaricare da sola — lo stesso muro gia' incontrato con Gemma 3. In piu' l'unico file dell'1B e'
proprio un `-gpu`, cioe' della categoria che non parte. L'unico Llama scaricabile senza account e'
TinyLlama 1.1B, che pero' e' del 2023: rende poco in italiano e non e' addestrato al tool calling.
E' in catalogo dichiarato per quello che e'. **Se serve un vero Llama 3.2 l'unica strada e' mettere
un token HuggingFace nell'app, oppure ospitare il file su Cloudflare R2.**

**La parte agentica adesso esiste davvero.** Prima `detectedDeadlines` veniva estratto dall'AI e
buttato via: nessuna schermata lo leggeva. Ora:

- Il prompt chiede esplicitamente azioni per il calendario, con le categorie vere del backend
  (`VERIFICA|INTERROGAZIONE|PAGAMENTO|USCITA_DIDATTICA|AVVISO|ALTRO`) e la regola di non inventare
  date che non stanno nel testo.
- Le azioni malformate si scartano prima di arrivare al server: `dueDate` deve essere una data ISO
  vera, `time` un orario vero, la categoria una di quelle valide. Un modello piccolo ogni tanto
  scrive `"dueDate": "entro venerdi"`, e da qui escono eventi sul calendario condiviso.
- Nel dettaglio della circolare compare "Ho trovato N scadenze", ciascuna con il tasto
  "Aggiungi al calendario". L'evento viene creato con `isAiGenerated = true`, e se il server
  segnala un possibile doppione lo dice invece di far credere che sia stato creato.
- Ai modelli senza tool calling (TinyLlama) la parte sulle azioni non viene nemmeno chiesta:
  chiedergliela li manda fuori strada e rovina anche il riassunto.

**Perche' con conferma e non in automatico.** La scrittura va sul calendario **condiviso della
classe**: una data sbagliata la vedrebbero tutti. Un modello da qualche miliardo di parametri che
gira su un telefono una data ogni tanto la sbaglia, e un tocco di conferma rende l'errore innocuo.
Se lo si vuole automatico e' una riga in `MainAppShell` — chiamare `onCreateCalendarEvent` per ogni
scadenza appena la classificazione finisce — ma va deciso sapendo cosa comporta.

**Contesto per modello.** `maxContextTokens` non e' piu' una costante: dipende da come il file e'
stato convertito, e chiederne piu' di quanti il file ne preveda fa fallire il caricamento.
TinyLlama ha `ekv1280` nel nome ed e' fermo a 1280; gli altri salgono a 2048.

**File orfani.** Cambiando catalogo, i modelli gia' scaricati di una voce sparita resterebbero sul
telefono invisibili e incancellabili — nel caso concreto i 5,9 GB del vecchio 12B solo-GPU. Le
Impostazioni ora mostrano quanto occupano e offrono di liberarli.

**Cosa resta da provare sul telefono.** Se i Qwen3.5 partono (sono `.litertlm` come i Gemma 4, ma
non li ha ancora caricati nessuno qui), se TinyLlama in formato `.task` viene accettato dal motore,
e soprattutto quanto sono affidabili le date che i modelli tirano fuori dalle circolari vere: e'
l'unica cosa che dira' se la parte agentica e' utile o solo una funzione in piu' da controllare a
mano.

## 8/9: tre errori segnalati con le schermate, tutti diversi

Nessun file backend toccato.

**1. "SocketTimeoutException" su Google AI Studio.** Non era la chiave ne' la quota: era l'app a
riattaccare troppo presto. Il client HTTP del classificatore non installava il plugin `HttpTimeout`,
quindi valevano i dieci secondi di lettura di default di OkHttp — e una `generateContent` con il
testo di una circolare intera ne impiega regolarmente di piu'. `ApiClient` un blocco di timeout ce
l'aveva gia'; questo client era rimasto indietro. Ora 120 secondi di richiesta e di socket, 20 di
connessione.

**2. "Unable to create LlmLiteRTXnnpackExecutor, model is null" con Gemma 4 12B.** Causa trovata:
i file con il suffisso `-gpu` sono build preconfezionati **per la sola GPU**. Il codice, quando
l'avvio su GPU falliva, ripiegava sulla CPU — e su un file solo-GPU il backend CPU (XNNPACK) non
trova nulla da caricare, da cui "model is null". Due correzioni:

- Il catalogo ora usa i file **standard**, che girano su entrambi i backend e sono anche quelli che
  Google misura nelle tabelle dei model card. I `-gpu` restano selezionabili a mano per chi vuole
  scaricare meno ed e' disposto a rinunciare al ripiego.
- Quando falliscono entrambi i backend, il messaggio riporta **tutti e due** i motivi. Prima
  l'errore della GPU veniva inghiottito e si mostrava solo quello della CPU: sembrava un file
  corrotto, mentre il problema stava a monte.

Il file da 5,9 GB gia' scaricato non diventa spazzatura invisibile: `Gemma 4 12B (solo GPU)` e'
rimasto a catalogo, quindi resta riconosciuto come installato e si puo' cancellare dal pulsante
"Elimina" delle Impostazioni.

**Le stime di memoria erano troppo prudenti.** I model card di Google riportano le misure reali su
Android: E2B occupa 676 MB su GPU e 1733 MB su CPU, E4B 710 MB su GPU e 3283 MB su CPU. Il file
`.litertlm` viene letto in memory mapping, quindi non finisce in RAM per intero — cosa che le stime
fatte a occhio non consideravano. Le soglie sono state riportate su questi numeri (caso peggiore,
cioe' CPU, piu' il margine di sistema): E2B 3,9 GB, E4B 5,5 GB. **La mappatura per fasce e' quindi
molto piu' solida di quanto sembrasse.** Resta un dubbio solo sul 12B: nel suo model card non c'e'
nessuna riga Android, Google lo presenta come modello da computer.

**3. "Destinata esclusivamente a docenti o personale ATA" su una circolare per tutti gli studenti.**
Bug vero nell'euristica di riserva, e presente in due copie (una per classificatore). Fra le parole
che facevano scattare "roba per il personale" c'erano `consiglio di istituto` e
`collegio dei docenti`: sono organi della scuola, che una circolare per gli studenti cita di
continuo — "ai sensi della delibera del Consiglio di Istituto n. 16" — senza che questo cambi il
destinatario. In piu' quel controllo veniva **prima** di quello sugli studenti, quindi vinceva anche
su un esplicito "A tutti gli studenti dell'istituto".

Ora l'euristica sta in un solo posto (`HeuristicClassification`), guarda prima a chi e' indirizzata
la circolare, e fra le parole per il personale ci sono solo espressioni di destinatario vero
("ai soli docenti", "riservata al personale"), non nomi di organi. Verificato sul testo reale della
circolare 3: prima `NOT_RELEVANT`, ora `RELEVANT`.

**4. Muri di testo al posto dei riassunti.** L'errore del motore nativo si porta dietro il trace del
codice C++ e i byte grezzi di uno `StatusList` protobuf, e finiva in schermata per intero: mezzo
schermo illeggibile sia nel riassunto della circolare sia nelle Impostazioni. Ora il motivo tecnico
viene tagliato alla prima riga utile.

**Cosa resta da provare sul telefono.** Se dopo aver scaricato il file standard il 12B parte, e con
quale backend. Se non parte, il messaggio adesso dice anche cosa e' successo sulla GPU: e' quella
l'informazione che serviva e che mancava.

## 7/9: AI locale sul telefono al posto di GitHub Models

Nessun file backend toccato: non serve `wrangler deploy`. I modelli si scaricano direttamente da
HuggingFace, quindi non c'e' nulla da ospitare e nulla da pagare in banda.

**Cosa e' stato rimosso.** `GitHubModelsClassifier` e il campo "Token GitHub" nelle Impostazioni.
Non e' stata una perdita di funzionalita': quel classificatore era costruito soltanto dal pulsante
"Prova token" e non e' mai stato collegato alla classificazione vera, che passava sempre e comunque
da `ClientSideAiClassifier` (Google AI Studio). Chi aveva `GITHUB_MODELS` salvato nelle impostazioni
ricade su Google AI Studio senza bisogno di migrazioni.

**Cosa c'e' adesso.** Un secondo provider, "AI locale", che esegue il modello sul telefono: nessuna
rete, nessuna API key, nessuna quota, e il testo delle circolari non esce dal dispositivo nemmeno
verso Google. Il modello si scarica dalle Impostazioni, una volta sola, con barra di avanzamento e
ripresa automatica se il download si interrompe.

Modelli, tutti Gemma 4 in formato `.litertlm`, licenza Apache 2.0 e scaricabili senza account:

| Fascia di RAM | Modello consigliato | Peso |
|---|---|---|
| fino a 4 GB | Gemma 4 E2B | 2,0 GB |
| 4-8 GB | Gemma 4 E4B | 3,0 GB |
| 8 GB o piu' | Gemma 4 12B | 6,0 GB |

Si puo' comunque sceglierne un altro a mano, comprese le varianti senza accelerazione GPU per i
chip il cui driver OpenCL non regge.

**Avvertenza sulle fasce, da verificare sul campo.** Il peso del file non e' la memoria che serve:
oltre ai pesi ci sono la cache del contesto e i 2-2,5 GB che Android occupa gia' con se' stesso.
Stimando pesi + 40% + margine di sistema, E2B vuole circa 5,4 GB, E4B circa 6,6 GB e 12B circa
11 GB. Le prime due fasce chiedono quindi piu' memoria di quanta ne abbiano: su un telefono da 4 GB
E2B verra' quasi certamente ucciso dal sistema a meta' generazione, e 12B e' realistico solo su un
telefono da 16 GB. L'app lo dice esplicitamente prima del download (riga rossa sulla scheda del
modello) invece di lasciarlo scoprire dopo aver scaricato qualche giga, e lascia scegliere un
modello piu' leggero. **Se sui telefoni veri si vede che non partono, basta spostare la mappatura di
una fascia in `LocalAiCatalog.recommendedFor`: e' una funzione di tre righe.**

**Ripiego automatico fra i due provider.** Se quello scelto non risponde — Google senza rete o con
la quota finita, il locale senza modello scaricato — l'app prova l'altro prima di ricadere
sull'euristica a parole chiave. Serviva un modo per accorgersi del fallimento: i classificatori non
lanciano eccezioni, inghiottono l'errore e restituiscono comunque un risultato, quindi e' stato
aggiunto il campo `isFallback` a `CircularAiClassification`.

**Perche' MediaPipe `tasks-genai` e non la libreria LiteRT-LM Kotlin.** Google indica
`com.google.ai.edge.litertlm:litertlm-android` come sostituto ufficiale, ma i suoi `.class` portano
metadata Kotlin 2.2 (2.4 nella 0.17.0) e il compilatore Kotlin 2.0.20 di questo progetto si rifiuta
di leggerli: adottarla vorrebbe dire aggiornare Kotlin e a cascata Compose Multiplatform.
`tasks-genai` e' Java puro compilato per Java 8 con `minSdk 21`, quindi entra cosi com'e' — e il suo
runtime nativo **e'** LiteRT-LM, che e' il motivo per cui carica i `.litertlm` e non solo i vecchi
bundle `.task`. Il giorno in cui si aggiornera' Kotlin, l'unico file da riscrivere e'
`LocalLlm.android.kt`.

**Due problemi di build trovati e risolti strada facendo.**
1. `gradle.properties` non impostava `org.gradle.jvmargs`, quindi il demone Gradle girava con i
   512 MB di default e la build falliva su `mergeExtDexDebug` con un `Error while merging dex
   archives` senza messaggio — un OutOfMemoryError dentro D8, non un errore di codice. I due file
   `java_pid*.hprof` nella cartella principale sono il residuo di quegli errori (si possono
   cancellare). Ora la memoria e' fissata a 4 GB.
2. Le librerie native del motore esistono per quattro ABI e portavano l'APK di debug da 40 a
   131 MB. Sono state ristrette ad `arm64-v8a` e `x86_64` (APK a 79 MB): i dispositivi a 32 bit
   non potrebbero comunque eseguire il modello, perche' un processo a 32 bit ha 4 GB di spazio di
   indirizzamento in tutto.

**iOS.** L'AI locale resta solo su Android: LiteRT-LM e' una libreria Android e su iPhone servirebbe
un'altra strada (Foundation Models di Apple su iOS 26+), che senza un Mac non e' verificabile. Le
`actual` iOS esistono e dichiarano la funzione non disponibile, la schermata Impostazioni lo scrive,
e su iPhone l'app continua a usare Google AI Studio come prima.

**Cosa e' stato verificato e cosa no.** `./gradlew :androidApp:assembleDebug` passa da pulito e
produce l'APK. **Non e' stato provato nulla su un telefono vero**: il download dei modelli, il
caricamento del motore, la qualita' dei riassunti in italiano e i tempi di generazione sono tutti da
misurare sul campo. `:shared:compileCommonMainKotlinMetadata` fallisce, ma per un problema
preesistente e slegato da questo lavoro (una dipendenza tira dentro un klib di `kotlin-stdlib`
2.1.0, ABI incompatibile con il compilatore 2.0.20).

## Fix del 6/9 (sesta parte): Priority Pass, classificazione AI, tasto indietro

Nessun file backend toccato in questo giro: non serve un nuovo `wrangler deploy`.

- **"Il tasto priority pass ancora non funziona (non succede nulla se lo attivo)"** — codice
  client e backend del toggle in sé erano già corretti (rivisti riga per riga), ma ho trovato un
  bug reale nella gestione dell'errore: se il salvataggio falliva per un qualunque motivo (es. non
  avevi ancora fatto il nuovo `wrangler deploy` di cui ti avevo parlato, quindi il server ancora
  filtrava il Rappresentante fuori dalla lista), l'intera Scheda Classe spariva sostituita da un
  messaggio d'errore a schermo intero — dando l'impressione che "non succedesse nulla" invece di
  un errore visibile. RISOLTO: un salvataggio fallito ora mostra un piccolo banner rosso in cima
  (che si può chiudere) e riporta il valore precedente, senza far sparire il resto della lista.
  **Se il problema persiste dopo questo fix, prova a rifare `npx wrangler deploy` per essere
  sicuro che il backend con l'inclusione del Rappresentante sia davvero online**, poi riprova e
  fammi sapere il messaggio d'errore esatto che compare nel banner.
- **"La funzione di elaborazione AI non funziona ancora, anche se la chiave API è inserita"** —
  trovato un bug reale e piuttosto serio: qualunque fallimento nella chiamata a Google AI Studio
  (rete, chiave rifiutata, quota esaurita, risposta malformata) veniva inghiottito in silenzio e
  sostituito da un messaggio euristico generico — che tra l'altro diceva SEMPRE "Nessuna API Key
  configurata" anche quando la chiave c'era, mentendo sulla causa reale. Questo è esattamente il
  motivo per cui vedevi sempre lo stesso messaggio. RISOLTO: ora, se la chiave è impostata ma la
  classificazione fallisce comunque, il riassunto mostrato in app riporta il motivo esatto (codice
  HTTP e inizio della risposta di Google, o l'eccezione se il problema è ancora prima, tipo il
  download/l'estrazione del PDF). **Non ho potuto testare dal vivo la chiamata a Gemini** (nessuna
  chiave né rete verso Google in questo ambiente): la prossima volta che provi, guarda cosa dice
  esattamente il riassunto AI nel dettaglio della circolare e mandamelo — con il motivo vero
  finalmente visibile posso capire se è la chiave, il modello, o altro.
- **"Tornando indietro con gli swipe di sistema vorrei non si chiudesse l'app"** — bug reale:
  nessuna schermata intercettava il tasto/gesto indietro di sistema di Android, quindi chiudeva
  sempre l'app anche da dentro Mappa Posti, Sondaggi, Scheda Classe, Notifiche o il dettaglio di
  una circolare. RISOLTO: il back di sistema ora si comporta come la freccia disegnata in ogni
  schermata (torna al menu/schermata precedente), e da qualunque tab diversa da Home torna prima a
  Home invece di uscire subito — come nelle app con barra in basso. Vale solo per Android: su iOS
  non c'è un vero equivalente da intercettare allo stesso modo (l'app lì è un'unica vista Compose,
  non uno stack di navigazione UIKit).
- **"Continua con l'integrazione della nuova identità visiva"** — aggiunta la schermata di
  caricamento iniziale in stile AILA (sfondo blu scuro sfumato, logo, "Caricamento...") al posto
  del semplice spinner su sfondo bianco di prima — corrisponde alla schermata "Caricamento" del
  mockup. Il logo è ancora un segnaposto disegnato a mano (stessa tecnica delle altre icone
  dell'app): resta valido quanto detto prima sull'icona reale, mi serve l'asset esportato per
  sostituirlo con quello vero.
- **Multi-classe**: confermato con te il modello — ogni utente vede solo la propria classe, ma
  classi diverse (isolate tra loro) devono poter usare la stessa app. Corrisponde esattamente a
  quanto avevo proposto. Non ancora implementato in questo giro (nessun cambio allo schema o al
  backend oggi): lo affronto nel prossimo passaggio, cominciando dalla migrazione additiva e dal
  nuovo flusso di registrazione con codice classe, così può essere verificato un pezzo alla volta
  prima di toccare le rotte che servono i contenuti (circolari, calendario, bacheca, ecc.).

## Cambio di identità: Circolare+ → AILA (fatto in parte, piano per il resto)

Ricevuti i mockup del nuovo brand "AILA" (logo, palette, font Sora, nuova struttura di
navigazione) con la richiesta esplicita di: rifare anche la struttura (non solo l'estetica) e
costruire supporto multi-classe. Tre decisioni prese insieme a te prima di partire: sì alla
ristrutturazione della navigazione, sì al multi-classe, ma **nome tecnico invariato**
(applicationId `com.circolareplus`, progetto Firebase, package del codice) — cambia solo ciò che
si vede.

### Fatto in questo passaggio

- **Palette e nome visibile**: `AppTheme.kt` aggiornato ai colori del brand kit (Primario
  `#3B82F6`, Secondario `#8B5CF6`, nuovo `AccentCyan #06B6D4`, sfondo `#F8FAFF`) — i nomi dei
  token restano quelli di prima (`PrimaryBlue`, ecc.) apposta, per non dover toccare ogni
  schermata che li usa già. "Circolare+"/"CIRCOLARE+" sostituito con "AILA" ovunque compare
  all'utente: schermata di login, etichetta app Android (`AndroidManifest.xml`), notifiche
  push, prompt dell'AI di classificazione.
- **Icona app**: non ancora fatta. L'app oggi non ha proprio un'icona personalizzata (usa quella
  di default di Android) — per farla bene servirebbe l'asset esportato dal tool con cui è stato
  fatto il mockup (almeno 512×512, idealmente anche i due layer "adaptive icon" foreground/
  background separati); mandamelo e la monto, invece che provare a ridisegnare l'icona a mano
  con path vettoriali scritti a occhio, che verrebbe quasi sicuramente diversa dall'originale e
  non potrei nemmeno vederla renderizzata per controllarla.
- **Font Sora**: non ancora integrato. Richiede di scaricare i file `.ttf` di Sora e aggiungerli
  come risorsa font del progetto (Compose Multiplatform supporto font custom) — rimandato,
  perché tocca la configurazione Gradle dei resource multipiattaforma senza poter verificare
  visivamente il risultato da qui.
- **Ristrutturazione navigazione**: fatta. La bottom bar ora è **Home · Calendario · Classe ·
  Mappa posti · Altro**, come nei mockup:
  - "Circolari" e "Bacheca" non sono più tab separate: vivono dentro la nuova tab "Classe" con un
    selettore interno (stessa UI di prima, solo riorganizzata), più il tasto "👑 Scheda Classe"
    per il Rappresentante.
  - "Mappa Posti" prima era un flusso apribile solo dalla Home (con freccia indietro che
    nascondeva la bottom bar); ora è una tab vera e propria, sempre raggiungibile.
  - "Profilo" è diventato "Altro" (stesso contenuto per ora: chiave API, notifiche bacheca,
    accesso a Scheda Classe, logout — un vero hub "Altro" con voci aggiuntive tipo Aiuto/Privacy/
    Lingua come nei mockup è rimandato finché non c'è contenuto reale dietro quelle voci).
  - "Sondaggi Interrogazioni" resta un flusso aperto dalla Home (i mockup di riferimento sono un
    kit generico e non hanno una schermata equivalente da cui prendere spunto).

### Multi-classe — PROGETTATO, NON ANCORA IMPLEMENTATO (serve un altro passaggio dedicato)

Qui mi sono fermato deliberatamente prima di scrivere codice. Il motivo: l'intero schema del
database oggi è pensato per **una classe sola implicita** — nessuna tabella (circolari,
calendario, bacheca, sondaggi, mappa posti) ha una colonna che dice "di quale classe è". Il
codice di registrazione rappresentante è un singolo segreto globale sul Worker
(`REPRESENTATIVE_SIGNUP_CODE`), non legato a una classe specifica. Rendere l'app multi-classe
vuol dire toccare lo schema e *ogni* rotta del backend che legge/scrive contenuti — un lavoro
grande, da fare con attenzione perché il database D1 è quello vero, già con account reali dei
tuoi compagni dentro: un errore in una migrazione dello schema può cancellare o corrompere dati
veri, non è reversibile come un `git revert`.

Il piano concreto (da confermare/discutere prima di eseguirlo):

1. **Interpretazione scelta** (dettaglio dei mockup ambiguo, lo segnalo): "Le mie classi" nel PDF
   mostra un account con 5 classi diverse — ma nell'app di oggi un utente appartiene a una classe
   sola (è uno studente/rappresentante di UNA classe reale). Ho scelto di interpretare "multi-
   classe" come: **il database smette di essere legato a una classe sola cablata**, così più
   classi diverse (della tua scuola o di altre) possono usare la stessa installazione
   dell'app senza vedere i dati l'una dell'altra — non come "un utente vede più classi
   contemporaneamente". Se invece intendevi la seconda cosa, dimmelo prima che parta: cambia
   parecchio il modello dati (servirebbe una tabella di appartenenza utente↔classe multipla
   invece di una singola colonna).
2. **Nuova tabella `classes`**: `id`, `name`, `academic_year`, `join_code` (univoco, per
   registrarsi come studente di quella classe), `representative_code` (univoco, sostituisce il
   segreto globale attuale — ogni classe ha il proprio codice rappresentante).
3. **Migrazione additiva** (mai distruttiva): crea `classes`, inserisce una riga per la tua
   classe attuale riusando i dati già in `app_config`, aggiunge `class_id` alle tabelle
   `users`, `circulars`, `calendar_events`, `proposals`, `interrogation_grids`,
   `seat_map_history`, riempendo automaticamente quello esistente con l'id della classe appena
   creata (nessun dato perso, nessuna riga toccata in modo distruttivo).
4. **Registrazione**: il campo "Codice rappresentante" diventa "Codice classe" (obbligatorio,
   sempre) + "Codice rappresentante" (opzionale, verificato contro la classe specifica invece che
   contro il segreto globale) — più un percorso "Crea una nuova classe" per chi non ha ancora un
   codice, che genera i due codici e registra chi la crea come Rappresentante.
5. **Ogni rotta esistente** (`circulars.ts`, `calendar.ts`, `proposals.ts`, `polls.ts`,
   `ratings.ts`, `preferences.ts`, tutto ciò che tocca la mappa posti) va aggiornata per filtrare
   sempre per `class_id` (preso dal JWT, che guadagna un campo `classId`) — altrimenti,
   registrare una seconda classe di prova mostrerebbe comunque le circolari/bacheca/calendario
   della prima: peggio che non avere il multi-classe affatto.
6. **Client**: nuova schermata "Le mie classi" / selezione classe in registrazione, nessun vero
   "cambio classe" al volo (coerente col punto 1: un utente sta in una classe sola).

Datomi che tocca **ogni** rotta backend più una migrazione sul database reale, lo tratto come un
blocco di lavoro a sé, da affrontare con te un pezzo alla volta (prima lo schema+registrazione,
verificato, poi le singole rotte una per una) invece che in un colpo solo senza modo di testarlo
prima che tocchi il database vero.

## 1. Assegnazione ruolo REPRESENTATIVE — RISOLTO (via codice di registrazione)

In registrazione c'è ora un campo opzionale "Codice rappresentante": se compilato e corretto,
`POST /api/auth/register` assegna `role: 'REPRESENTATIVE'` invece di `STUDENT'`. Il codice va
impostato una volta sul Worker:

```
npx wrangler secret put REPRESENTATIVE_SIGNUP_CODE
npx wrangler deploy
```

Senza questo secret impostato, il campo va lasciato vuoto (chiunque provi un codice riceve un
errore esplicito, non un ruolo silenzioso). Per correggere un ruolo dopo la registrazione, o per
un account già esistente, resta comunque disponibile la via manuale:

```
npx wrangler d1 execute <nome-db> --remote --command "UPDATE users SET role='REPRESENTATIVE' WHERE username='...'"
```

## 2. Errore 500 su login/registrazione — email eliminata, RICHIEDE di riapplicare lo schema D1

Test dal vivo (6/9): "Errore interno del server" — testo che coincide esattamente con il fallback
generico di `backend/src/index.ts` (`app.onError`), quindi un'eccezione non gestita lato Worker.
Causa più probabile: lo schema D1 non era mai stato applicato al database **remoto** (il deploy
del Worker non lo fa automaticamente).

Nel frattempo (6/9, sessione successiva) l'intero sistema è stato cambiato da email a **username**
(nessun dato reale raccolto, su richiesta esplicita): la colonna `users.email` non esiste più,
sostituita da `users.username`. Questo significa che **è obbligatorio riapplicare lo schema al
database remoto**, altrimenti la tabella esistente (se c'è) ha ancora la vecchia colonna `email` e
ogni query fallirebbe comunque con lo stesso errore 500. Dato che finora nessuna registrazione è
mai andata a buon fine, si può ripartire puliti:

```
npx wrangler d1 execute circolare_d1 --remote --command "DROP TABLE IF EXISTS student_profiles"
npx wrangler d1 execute circolare_d1 --remote --command "DROP TABLE IF EXISTS users"
npx wrangler d1 execute circolare_d1 --remote --file=./backend/schema.sql
npx wrangler deploy
```

Poi rilanciare la build Android (i sorgenti client sono già aggiornati a username) e riprovare
registrazione/login. Se l'errore 500 persiste anche dopo questo, il prossimo passo è `npx wrangler
tail` mentre si riproduce il problema, per vedere lo stack trace vero invece di indovinare.

L'etichetta "Email o Username" nel campo di login è stata sostituita da un singolo campo
"Username", coerente col resto del sistema.

## Fix del 6/9 (dopo la prima build riuscita)

- **Bacheca: non si riusciva a commentare** — RISOLTO. La freccetta sul contatore commenti
  ruotava ma non mostrava mai nulla sotto: la sezione commenti (lista + campo di testo + invio)
  non era mai stata collegata all'interfaccia, solo a repository/backend. Aggiunta in
  `BoardScreen.kt`, collegata a `listComments`/`addComment` già esistenti.
- **Priority Pass: regola cambiata** — da "sempre e solo prima fila" a "prima, seconda o terza
  fila" (`SeatMapOptimizer.MAX_PRIORITY_ROW = 2`). Non viene chiesto in registrazione perché,
  come da specifica originale, è il rappresentante ad assegnarlo (Scheda Classe), non lo studente
  stesso — altrimenti chiunque potrebbe auto-assegnarselo.
- **Home: quasi tutta la schermata era finta** — RISOLTO. "Circolare n. 142", i due eventi
  "Matematica"/"Inglese", "Aula 3B • Fila 2 • Banco 4" e "2 nuove proposte" erano tutti valori
  hardcoded lasciati dal prototipo iniziale, mai collegati ai dati reali. Ora mostra l'ultima
  circolare vera, le prossime scadenze vere dal calendario, e il conteggio vero delle proposte
  (caricati in anteprima già all'apertura dell'app, non solo visitando le rispettive tab).
- **Campanella notifiche non cliccabile** — RISOLTO. Era puramente decorativa (nessun
  `clickable`) e mostrava sempre un pallino rosso finto di "non lette" anche senza notifiche reali
  da mostrare. Ora porta alle impostazioni notifiche in Profilo; il pallino finto è stato tolto
  (l'app non tiene traccia di notifiche lette/non lette, quindi mostrarlo sarebbe stata
  un'informazione inventata).
- **Calendario: inserimento data poco efficiente** — RISOLTO. Andava digitato a mano il formato
  esatto "AAAA-MM-GG"; ora c'è un vero calendario nativo (Material3 DatePicker) da toccare. Le
  categorie evento (6 in totale) ora vanno a capo invece di essere tagliate fuori dallo schermo.

## Fix del 6/9 (seconda parte): mancava la schermata per assegnare il Priority Pass

Domanda diretta: "Come faccio ad assegnare il priority pass?" — risposta trovata leggendo il
codice: `RatingsRepository.setRating()`/`setPriorityPass()` e le relative route backend
(`/api/ratings/*`, tutte protette `requireRole('REPRESENTATIVE')`) erano completamente
implementate, ma **non esisteva alcuna schermata che le richiamasse**. L'unico punto dell'app che
leggeva `listRatings()` era la generazione automatica della mappa posti — mai per modificare un
valore. In pratica non c'era alcun modo, nell'app, di assegnare priority pass o valutazioni a
chicchessia, nonostante la spiegazione precedente ("è il rappresentante ad assegnarlo") fosse
corretta solo a metà: corretto *chi* dovrebbe farlo, ma mancava il *dove*.

RISOLTO aggiungendo una vera "Scheda Classe": nuovo file `ClassRosterScreen.kt` (lista compagni
con stepper +/- 1-5 per Didattica/Comportamento e uno Switch per Priority Pass), raggiungibile da
un nuovo pulsante "Gestisci" nel Profilo, visibile solo se `user.role == REPRESENTATIVE`. Ogni
modifica chiama subito la relativa route.

Durante l'implementazione è emerso anche un bug latente nel backend mai innescato finora: il
client Kotlin serializza sempre entrambi i campi `didactic`/`behavior`, usando `null` per "non
toccare questo campo" quando se ne aggiorna uno solo per volta (esattamente il caso della Scheda
Classe, con due stepper indipendenti). La route `PUT /api/ratings/:studentId` però controllava
`!== undefined`, che è vero anche per `null`, e poi `null < 1` in JavaScript vale `true` (coercizione
a 0): il risultato sarebbe stato un errore di validazione ogni volta che si cambiava un solo valore.
RISOLTO normalizzando `null` a "non fornito" nella route, e — per la primissima valutazione di uno
studente mai valutato prima — usando 3 (valore neutro) come default per il campo non ancora
impostato, invece di pretendere che vengano forniti entrambi insieme.

**Da fare su questo fix**: `backend/src/routes/ratings.ts` è cambiato, quindi serve un nuovo
`npx wrangler deploy` prima che la modifica sia visibile in produzione. Non richiede modifiche allo
schema D1 (nessuna nuova colonna/tabella).

## Fix del 6/9 (terza parte): API Key, sondaggi, voto mappa posti

- **"Che formato accetta il tasto Salva Chiave? Comunque non la salva"** — due problemi distinti:
  1. Il placeholder del campo era `"sk-..."` (formato OpenAI), ma `ClientSideAiClassifier` chiama
     Google AI Studio (Gemini): la chiave giusta è quella generata su aistudio.google.com/apikey
     (in genere inizia con `AIza...`), non una chiave OpenAI. RISOLTO: placeholder corretto e
     aggiunta indicazione esplicita nel testo della card.
  2. Il salvataggio in realtà funzionava già (va su storage locale persistente via
     multiplatform-settings), ma il tasto non dava nessun riscontro visivo: sembrava non fare
     nulla. RISOLTO aggiungendo una conferma "✅ Salvata" accanto al tasto, che sparisce non
     appena si torna a modificare il campo.
- **"Non riesco a creare sondaggi"** — stesso identico problema già visto per il Priority Pass:
  `PollsRepository.createPoll()`/`publishPoll()` esistevano lato client e backend, ma nessuna
  schermata li richiamava. Gli studenti vedevano sempre "Nessun sondaggio disponibile" perché non
  c'era modo di crearne uno. RISOLTO aggiungendo un modulo "Nuovo sondaggio" (visibile solo al
  Rappresentante dentro la schermata Sondaggi): materia, elenco date/slot con capienza e casella
  "presenza obbligatoria", crea e pubblica in un solo passaggio.
- **"La votazione dei posti non funziona (endpoint non trovato)"** — bug reale, non mancanza di
  UI: `PreferencesRepository.getConfig()`/`setPreferencesOpen()` chiamavano `/api/config/preferences`,
  ma la rotta reale (montata in `index.ts` su `/api/preferences` e definita come `/config` nel
  router) è `/api/preferences/config` — i due segmenti erano invertiti, quindi 404 ogni volta.
  Questo impediva al Rappresentante di aprire la finestra voti e, di conseguenza, agli studenti di
  vedere mai il banner per votare le preferenze sociali. RISOLTO correggendo il path. Nessuna
  modifica al backend: basta la ricompilazione del client.

## UI "sembra tutto AI generated", poco curata — confronto fatto col PDF di riferimento

Ricevuto il PDF (`CircolarePlus_Architettura_UIUX.pdf`) più 6 screenshot dell'app reale (Home,
Calendario, Circolari, dettaglio Circolare, Bacheca, Profilo) da confrontare con i mockup target
(Material 3 su Android, Glassmorphism su iOS).

Confronto fatto schermata per schermata: la maggior parte delle differenze visibili negli
screenshot **non sono un problema di grafica**, ma il fatto ovvio che l'account di test non ha
ancora dati reali (nessun evento in calendario, poche circolari, bacheca vuota) — le card, i
colori e il layout di Home/Calendario/Bacheca/Profilo corrispondono già abbastanza da vicino alla
struttura del PDF. Non ha senso "decorare" schermate che sono semplicemente vuote di dati veri.

Due scarti concreti e specifici, invece, erano reali e sono stati corretti in questo passaggio:

- **Circolari**: nel design mancavano, rispetto al PDF, la scheda "⚪ Non rilevanti" tra i filtri e
  una barra di ricerca per numero/titolo. RISOLTO: aggiunta la scheda mancante e un campo di
  ricerca sopra i filtri.
- **Dettaglio Circolare**: il PDF mostra un'anteprima del documento vera e propria e un tasto
  "Mostra PDF"; l'app mostrava un'icona generica grigia, il testo tecnico "Cache R2: ..." (un
  dettaglio di implementazione del backend, non pensato per l'utente) e "Apri/Scarica PDF
  originale". RISOLTO parzialmente: icona in riquadro colorato, testo utente-centrico, tasto
  rinominato "📄 Mostra PDF". **Non ancora fatto**: una vera anteprima del PDF renderizzata dentro
  l'app (come nel mockup) richiede un motore di rendering nativo separato per Android
  (`PdfRenderer`) e iOS (`PDFKit`) — è una feature a sé, più grande di un ritocco grafico, e resta
  aperta; per ora il tasto continua ad aprire il PDF nel visualizzatore di sistema del telefono.

Animazioni più elaborate (transizioni tra schermate, micro-interazioni) restano deliberatamente
rimandate: prima le funzionalità, poi il polish, come da priorità concordata (punto #8 sotto,
ora in parte superato da questo confronto ma valido per il resto).

## Fix del 6/9 (quarta parte): cancellazioni, storico sondaggi, download circolari

**Serve un nuovo `npx wrangler deploy`**: `proposals.ts`, `polls.ts` e `circulars.ts` sono
cambiati.

- **"In bacheca non si possono cancellare i post"** — mancava del tutto: nessuna rotta
  `DELETE /api/proposals/:id`. RISOLTO: aggiunta la rotta (l'autore può cancellare la propria
  proposta, il Rappresentante qualunque) e un'icona 🗑 nella card, visibile solo a chi ha il
  permesso. Voti e commenti collegati si cancellano da soli (`ON DELETE CASCADE` già nello schema).
- **Sondaggi: "selezione con latenza eccessiva"** — non era un problema di rete lenta ma di
  codice: ogni tap su un voto aspettava un giro completo (voto + ricarica di tutto il sondaggio)
  prima che il pulsante cambiasse colore. RISOLTO con aggiornamento ottimistico: il tasto
  selezionato cambia subito, la richiesta al server parte in background; se fallisce, torna allo
  stato precedente e mostra l'errore.
- **"I sondaggi non possono essere cancellati. Non c'è un sistema per vedere i risultati né uno
  storico"** — anche qui mancava tutto: nessuna rotta `DELETE /api/polls/:id`, e `getAssignments()`/
  `runAssignments()` (già scritti) non erano richiamati da nessuna schermata. RISOLTO: aggiunta la
  rotta di cancellazione, e una nuova schermata "Storico e risultati" (pulsante nella pagina
  Sondaggi, solo Rappresentante) che elenca tutti i sondaggi creati, con un tasto "Risultati" che
  calcola/mostra le assegnazioni studente↔data e un cestino per eliminarli.
- **"Il fetching delle circolari non funziona: ottengo sistematicamente sempre gli stessi
  messaggi dall'AI"** — nessun bug di fetching delle circolari in sé: il testo generico ripetuto
  ("Nessuna API Key AI configurata...") è esattamente il messaggio di fallback che
  `ClientSideAiClassifier` mostra quando la chiave è vuota o viene rifiutata da Google (401/403).
  Molto probabilmente la causa è la stessa segnalata prima: veniva inserita una chiave in formato
  sbagliato (OpenAI) perché il placeholder era `"sk-..."`. Ora che il placeholder è corretto
  (`"AIzaSy..."`) e il salvataggio dà conferma visiva, va reinserita una vera chiave di Google AI
  Studio e riprovato. Se il problema persiste anche con una chiave Gemini valida, serve vedere
  l'errore esatto (log lato client) per capire se è la chiave, l'estrazione testo del PDF, o altro.
- **"Apri/Scarica chiede l'autenticazione"** — bug reale confermato: la rotta
  `GET /api/circulars/pdf/:key` (quella aperta dal tasto nel browser di sistema del telefono) era
  dietro lo stesso middleware di autenticazione JWT di tutte le altre rotte di `/api/circulars`,
  ma il browser di sistema non può allegare l'header Authorization — 401 ogni volta. RISOLTO
  applicando l'autenticazione solo a `/` e `/:number` (che passano sempre dall'app, autenticata) e
  lasciando `/pdf/:key` senza JWT: la chiave R2 non è indovinabile, quindi resta comunque protetta
  "come un link condiviso", non pubblica a chiunque.

## Scheda Classe vuota / notifiche — chiarimenti, non bug (per ora)

- **"La scheda classe è vuota, io non appaio"**: per costruzione la Scheda Classe non deve
  mostrare il Rappresentante (non assegna un Priority Pass a se stesso) — questo è corretto. Se
  però risulta **completamente vuota** e non solo priva del proprio nome, la spiegazione più
  probabile è che nel database di test non esiste ancora nessun account con `role = 'STUDENT'`:
  finché nessuno si registra come studente (senza codice rappresentante), la lista resta vuota per
  definizione, non per un bug. Da verificare registrando un secondo account di prova.
- **"Le notifiche non funzionano: mi porta alla pagina profilo"**: RISOLTO, vedi sezione
  successiva — ora esiste un vero centro notifiche con storico.
- **"Le notifiche non sembrano funzionare (da testare meglio)"**: causa reale trovata, vedi
  sezione successiva — non era (solo) un problema di rete/Firebase, mancava proprio il codice che
  riceve i push ad app aperta. Resta comunque da verificare end-to-end con un invio reale su
  dispositivo (punto #6 qui sotto).

## Fix del 6/9 (quinta parte): Priority Pass del Rappresentante, centro notifiche vero

**Serve un nuovo `npx wrangler deploy`**: `ratings.ts` è cambiato.

- **"Anche il rappresentante però deve avere il priority pass se necessario. Dimentichi che questo
  esclude le ultime file e ciò è raro sia voluto"** — bug reale, non una scelta voluta: le tre
  rotte di `ratings.ts` (`GET /api/ratings` e i due `PUT`) filtravano `role = 'STUDENT'`, quindi il
  Rappresentante non poteva mai comparire nella propria Scheda Classe né avere `priority_pass`
  letto dall'algoritmo di disposizione banchi — restava strutturalmente escluso dalle prime file
  anche quando necessario. RISOLTO: il filtro ora include anche `REPRESENTATIVE` in tutte e tre le
  rotte. Il Rappresentante compare ora nella propria Scheda Classe con l'etichetta "(Tu)" e può
  attivarsi il Priority Pass da solo, esattamente come per un compagno.
- **Notifiche: centro notifiche vero, solo lato client, con scadenza automatica** — richiesto
  esplicitamente: "Le notifiche servono, magari lato client solo, salvate in una memoria che
  elimina ogni notifica dopo tot giorni". Costruito da zero:
  - Uno storico locale (mai inviato al server) in `LocalSettingsManager`, salvato come JSON nelle
    impostazioni della piattaforma: ogni notifica ricevuta viene aggiunta con data/ora, e ad ogni
    lettura quelle più vecchie di 7 giorni vengono scartate automaticamente.
  - **Bug più grosso scoperto in questo passaggio, indipendente da quanto segnalato**: l'app non
    aveva MAI avuto una classe che ricevesse davvero i push in arrivo (`FirebaseMessagingService`)
    — solo il codice che *registra* il dispositivo per riceverli. Risultato pratico: con app in
    background/chiusa, Android mostra la notifica di sistema per conto suo (funzionava "per
    sbagliato"); con app aperta in quel momento, il messaggio veniva scartato in silenzio, senza
    notifica e senza traccia da nessuna parte. Questo spiega la segnalazione "non sembrano
    funzionare". RISOLTO: creato `CircolareMessagingService`, che intercetta ogni push (foreground
    incluso), lo salva nello storico locale e mostra sempre la notifica di sistema.
  - Nuova schermata "Notifiche" (icona campanella in Home): elenco delle notifiche ricevute con
    titolo, testo e tempo relativo ("5 min fa", "2 g fa"); il pallino sulla campanella si accende
    solo se c'è almeno una notifica non letta, e sparisce uscendo dalla schermata (segnate come
    lette). Prima la campanella portava semplicemente al Profilo.
  - **Limite noto**: funziona solo su Android per ora. Su iOS servirebbe l'equivalente
    (`UNUserNotificationCenter` / APNs delegate), non ancora scritto — coerente con il limite già
    noto "iOS senza Mac disponibile" (punto #4 qui sotto).

## 3. Build Android/KMP mai verificata end-to-end

Wrapper Gradle generato, `google-services.json` posizionato, plugin attivato — ma finora nessuna
build è mai stata completata in un ambiente reale. È la build in corso al momento della stesura di
questa lista. Possibili problemi mai emersi: versioni di dipendenze incompatibili, target Compose
Compiler/Kotlin, naming dei source set.

## 4. iOS — nessun progetto Xcode reale

`PdfTextExtractor.ios.kt` e `PushTokenProvider.ios.kt` sono scritti seguendo le API previste
(PDFKit, Firebase) ma mai compilati né eseguiti su un target iOS reale. Serve un Mac (anche in
cloud) o CI con runner macOS (es. GitHub Actions) per procedere. Per la distribuzione/test su un
iPhone vero serve inoltre un account Apple Developer (99€/anno).

## 5. Nessun test automatico

Zero unit test o test di integrazione: né sulle repository/ViewModel del client, né sulle route del
backend. Il typecheck TypeScript (`tsc --noEmit`) copre solo la correttezza dei tipi, non il
comportamento.

## 6. Notifiche push FCM mai inviate per davvero

Codice pronto sui due lati (FCM HTTP v1, firma JWT RS256 lato Worker), secret configurati, ma non è
mai stato inviato un messaggio reale end-to-end. Da verificare: login → token registrato su
`/api/fcm/token` → azione da rappresentante (mappa posti, proposta) → notifica ricevuta sul device.

## 7. Preparazione al rilascio non affrontata

Firma dell'APK/AAB, icone dell'app, eventuale privacy policy (l'app tratta indirettamente dati di
minori, essendo per una classe di liceo) — rilevante solo se l'app dovesse uscire dall'uso interno
alla classe.

## 8. Polish grafico/UI

Deliberatamente rimandato a dopo che tutto il resto funziona, per non investire tempo su
un'interfaccia che potrebbe cambiare se emergono problemi funzionali.

## 9. Primo errore di compilazione reale (6/9) — risolto

Il primo log di errore Gradle/Kotlin autentico ricevuto in questo progetto (fino ad ora
l'unica autoverifica possibile era il conteggio manuale di parentesi, molto più debole).
Ho scaricato i file veri dal tuo PC per controllare (i numeri di riga del log non
corrispondevano più al file: probabilmente il log era di una compilazione fatta prima
dell'ultimo giro di modifiche, quindi ho verificato ogni bug sul contenuto attuale invece
che sul numero di riga esatto).

Bug reali trovati e corretti:
- `ClientSideAiClassifier.kt`: mancava `import io.ktor.serialization.kotlinx.json.json`,
  la funzione di estensione Ktor usata da `install(ContentNegotiation) { json(...) }`.
  Senza quell'import il nome `json` non si risolveva.
- `MainAppShell.kt`: mancava `import androidx.compose.ui.draw.clip`, usato dal banner di
  errore della Scheda Classe (`Modifier.clip(RoundedCornerShape(...))`).
- `MainAppShell.kt`: `CreatePollDialog` usa `DatePicker`/`DatePickerDialog`/
  `rememberDatePickerState` (API sperimentali di Material3) senza
  `@OptIn(ExperimentalMaterial3Api::class)` — aggiunto. Molto probabilmente è la causa
  reale anche degli altri errori del log ("Cannot infer type", "Conflicting overloads",
  "@Composable invocations...): un'API sperimentale non risolta manda in confusione il
  compilatore e genera errori a cascata sul resto della stessa funzione/file, che
  spariscono da soli una volta risolto il problema di fondo. Ho controllato comunque
  `AddCalendarEventDialog` (ha già l'OptIn corretto) e `AddProposalDialog` (non usa API
  sperimentali, nessuna modifica necessaria), e verificato che non esistano dichiarazioni
  duplicate delle funzioni coinvolte.

Nessuna modifica al backend in questo giro: non serve un `wrangler deploy`, basta
ricompilare il client con questi due file aggiornati.

### Secondo giro (stesso giorno): un altro import mancante, resto era rumore

Dopo la prima correzione, un nuovo log mostrava: gli stessi 4 "Cannot infer type" (ora alla
riga 458, spostata di una riga per l'import aggiunto), lo stesso "Conflicting overloads" alla
riga 1224 e gli stessi due "@Composable invocations..." — MA anche un errore nuovo e pulito:
`Unresolved reference 'background'` alla riga 747 (mancava `import
androidx.compose.foundation.background`, usato dal banner rosso della Scheda Classe accanto
a `.clip(...)`). Aggiunto.

Ho controllato a fondo gli altri tre: il call site di `AddCalendarEventDialog` (riga 456) è
scritto correttamente, non c'è nessun'altra dichiarazione della funzione da nessuna parte nel
progetto, e le colonne indicate per i due "@Composable invocations" (es. 1291:58) cadono oltre
la fine della riga reale nel file — cioè puntano a codice che non esiste. Sono quindi rumore
generato dal compilatore a cascata da un riferimento non risolto nello stesso file (qui
`background`, prima `clip`), non bug distinti: quando il frontend di Kotlin non risolve un
simbolo, il resto del type-check su quel file può produrre diagnosi fasulle altrove. Restano
da confermare con la prossima compilazione, ma non ho trovato nulla da correggere oltre
all'import.

### Terzo giro: l'ipotesi "cascata" era sbagliata su questo punto — trovata la causa vera

Con `background` corretto, il nuovo log mostrava di nuovo esattamente gli stessi 4 "Cannot
infer type", lo stesso "Conflicting overloads" e gli stessi due "@Composable invocations",
tutti spostati di +1 riga (coerente con la riga aggiunta per l'import) — quindi non erano
rumore, erano stabili e reali. Ho ricontrollato l'intero repository (non solo questo file):
nessuna seconda dichiarazione di `AddCalendarEventDialog` esiste da nessuna parte.

La causa più probabile: il messaggio di errore di "Conflicting overloads" stampa
esplicitamente `@ParameterName(...)` per ciascuno dei 4 parametri — l'annotazione sintetica
che Kotlin genera quando un tipo funzione ha i parametri nominati (es.
`(title: String, date: String, ...) -> Unit`). Con una funzione `@Composable` che ha un
parametro di questo tipo, alcune combinazioni di compilatore Kotlin/Compose confondono la
rappresentazione sintetica generata con una dichiarazione duplicata di se stessa — il che
spiega anche perché i 4 "Cannot infer type" cadono esattamente sullo stesso lambda al call
site (non riescono a risolvere che overload usare) e perché i due "@Composable invocations"
sono rumore aggiuntivo dalla stessa confusione.

Fix: rimossi i nomi dai parametri nei tipi funzione di `onConfirm` in tutti e tre i dialog
(`AddCalendarEventDialog`, `CreatePollDialog`, `AddProposalDialog` — gli ultimi due non
ancora comparsi nel log ma con lo stesso pattern, corretti preventivamente). Sono solo
annotazioni documentative, nessun cambio di comportamento: i lambda ai call site usano già
nomi posizionali propri (`{ title, date, time, category -> ... }`) indipendenti dai nomi nel
tipo dichiarato.

### Quarto giro: trovata la causa vera (build pulita l'aveva esclusa giustamente)

Build pulita fatta, stesso identico errore, parola per parola — compreso `@ParameterName(...)`
sulla firma che nel file avevo già tolto. Questo escludeva sia la cache sia la mia ipotesi sui
nomi dei parametri: il messaggio doveva riferirsi a una dichiarazione che non avevo ancora
toccato. Ho listato per la prima volta l'INTERA cartella `ui/screens` invece di guardare solo
`MainAppShell.kt`, e trovato `ui/screens/AddCalendarEventDialog.kt`: un vecchio file rimasto lì
da prima che la dialog venisse portata dentro `MainAppShell.kt` (usa `EventCategorySelector`
invece di FlowRow/AnimatedFilterChip — evidentemente la versione precedente). Stesso package
(`circolareplus.ui.screens`), funzione pubblica con lo stesso nome — mentre quella dentro
`MainAppShell.kt` è `private`. Una privata nel file e una pubblica nello stesso package sono
entrambe visibili in quel punto: da qui "Conflicting overloads" sulla dichiarazione e, di
conseguenza, l'impossibilità di dedurre i tipi del lambda passato alla chiamata (non sapeva
quale delle due usare). I due "@Composable invocations..." erano molto probabilmente rumore
della stessa ambiguità.

Fix: svuotato `ui/screens/AddCalendarEventDialog.kt` (lasciato solo un commento — non l'ho
cancellato per non toccare file sul tuo PC senza permesso esplicito; se compila puoi cancellarlo
tu, o dimmelo e te lo chiedo). `EventCategorySelector.kt` resta come codice morto innocuo, non
serve toccarlo.

Lezione per il futuro: quando sposto codice tra file durante un refactor, controllare sempre se
il file vecchio va rimosso, non solo svuotato di significato.

### Quinto giro: ultimo errore, causa banale

Con il file duplicato tolto, i 4 "Cannot infer type" e il "Conflicting overloads" sono spariti
(confermato dal log successivo) — restavano solo i due "@Composable invocations...", ora isolati
e reali. Puntavano entrambi esattamente sulle chiamate a `epochMillisToIsoDate(...)` dentro un
`.let { }`/`.map { }`. Causa: la funzione aveva per sbaglio l'annotazione `@Composable` sopra il
commento KDoc (riga 1206) — probabilmente un residuo di copia-incolla da una funzione vicina —
anche se è pura matematica su un epoch millis, nessuna UI. Un'annotazione seguita da un
commento e poi dalla dichiarazione è comunque valida in Kotlin, quindi il compilatore la
prendeva sul serio: chiamarla da un lambda non-Composable (`.let`/`.map` dentro un `onClick`)
dava esattamente questo errore. Tolta l'annotazione, nessun altro cambiamento necessario.

Con questo, tutti gli otto errori del log originale del 6/9 sono spiegati e corretti: 2 import
mancanti (`json`, `background`), 1 file duplicato mai rimosso dopo un refactor, e 1 annotazione
`@Composable` piazzata per errore.

## Piano di conversione al nuovo linguaggio grafico AILA (6/9)

Build ora verde. Confermato con le immagini che l'app gira ancora con il vecchio stile chiaro
(quello dello spec dettagliato "Circolare+ Architettura UI/UX", pre-rebrand), non con quello del
brand kit AILA (sfondo a gradiente blu scuro, saluto e campanella bianchi, riga di 4 icone di
accesso rapido Circolari/Calendario/Bacheca/Mappa posti). Finora avevo aggiornato solo la
palette in `AppTheme.kt`, non la struttura visiva delle schermate. Piano concordato: convertire
tutto, in ordine, senza inventare nuove funzionalità (solo restyling + i componenti condivisi
che servono a farlo bene).

Fasi:

1. **Fondamenta design system** (fatto in questo turno) — aggiunti a `AppTheme.kt`:
   `HeroGradient` (stesso gradiente blu scuro già usato in `AilaLoadingScreen`) e i token
   `OnHeroPrimary`/`OnHeroSecondary`/`OnHeroSurface` per testo e riquadri translucidi sopra
   sfondo scuro, cosi le prossime schermate riusano gli stessi nomi invece di colori hard-coded
   sparsi.
2. **Home** (fatto in questo turno) — sfondo `HeroGradient` a tutto schermo, saluto e campanella
   in bianco, aggiunta la riga di 4 icone di accesso rapido dal mockup (mancava del tutto prima).
   "Sondaggi Interrogazioni" (funzione reale, non presente nel mockup che si ferma a 4 icone)
   resta come card sotto, in stile chiaro. Tolte le card "Il mio posto"/"Bacheca" ridondanti con
   la nuova riga di icone. **Nota:** il mockup mostra anche una sezione "Messaggi dalla classe"
   (anteprima ultimi messaggi Bacheca) che non esiste ancora come funzione nell'app — non l'ho
   costruita perché sarebbe una nuova feature con dati reali da collegare, non solo restyling;
   se la vuoi la aggiungo come passo a parte.
3. **Bottom navigation bar** — non toccata: confrontando col mockup è già abbastanza simile
   (barra bianca, icone+etichette, tab attiva evidenziata in blu), non sembra necessario un
   restyling qui.
4. **Da fare — Calendario, Classe (Circolari+Bacheca), Mappa posti**: portare header e sfondo
   allo stesso linguaggio della Home (dove ha senso: probabilmente header scuro solo nella parte
   superiore, contenuto liste su sfondo chiaro per leggibilità, da verificare schermata per
   schermata).
5. **Da fare — schermate secondarie**: Dettaglio circolare, Notifiche, Profilo/Altro, Scheda
   Classe (rappresentante), Sondaggi, tutti i dialog (nuovo evento, nuovo sondaggio, nuova
   proposta).
6. **Da fare — stati**: schermata di errore/vuoto (attualmente stile vecchio), verificare
   coerenza con la schermata di caricamento già fatta.

Eseguo le fasi in ordine, un pezzo alla volta, e ti mando i file man mano così puoi vedere
com'è a occhio (io non posso vedere l'interfaccia renderizzata, solo il codice) prima che vada
avanti con la fase successiva.

### Correzione Home dopo confronto diretto con lo screenshot del mockup

Il primo tentativo aveva 3 differenze reali rispetto al mockup, viste confrontando gli
screenshot fianco a fianco:
1. Il gradiente scuro copriva TUTTO lo schermo; nel mockup copre solo il pannello superiore
   (saluto + icone), sotto è chiaro.
2. "Nuova circolare" era un pannello verde verticale (eyebrow + icona + titolo + sottotitolo +
   link); nel mockup è una card compatta a riga singola (icona + titolo + sottotitolo + freccia).
3. "Prossimi eventi" erano due card affiancate; nel mockup è UNA card con la lista degli eventi,
   righe separate da un divisore sottile, riquadro data (es. "12 MAG") a sinistra di ogni riga.

Corretto tutto e aggiunto il token `AppTheme.Space20` che mancava. "Messaggi dalla classe" (che
il mockup mostra sotto gli eventi) resta non costruito: servirebbe collegare dati veri della
Bacheca, è una funzionalità nuova non un restyling — aspetto conferma prima di aggiungerla.

### Logo vero, icona app e "Messaggi dalla classe" (6/9, da "loghi." e "Si, voglio anche quella funzione.")

Hai mandato il foglio con i 4 loghi veri AILA (chiaro/scuro, quadrato/tondo). Uso fatto finora:

1. **Icona app Android**: prima non c'era NESSUNA icona personalizzata (mancavano del tutto gli
   attributi `android:icon`/`android:roundIcon` nel manifest, quindi Android mostrava l'icona
   di default). Ritagliata la variante "Logo app (chiaro)" dal tuo foglio, generati i PNG per
   tutte le densità standard (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) sia quadrati che tondi, messi in
   `androidApp/src/androidMain/res/mipmap-*/`, e aggiunti gli attributi nel manifest. È l'approccio
   "legacy" (non adaptive icon con livelli separati): più semplice, funziona ovunque, in futuro
   si può raffinare con un'icona adattiva vera se vuoi.
2. **Schermata di caricamento**: tolto il segno "A" disegnato a mano (placeholder) e messa
   l'immagine vera, ritagliata dalla variante "Logo app (scuro)" (si abbina meglio allo sfondo
   scuro sfumato della schermata).
3. **Prima volta che uso le "Compose Resources"** (il meccanismo di Compose Multiplatform per
   includere immagini): ho messo il PNG in
   `shared/src/commonMain/composeResources/drawable/aila_logo.png` e configurato esplicitamente
   in `shared/build.gradle.kts` il nome del package generato (`circolareplus.generated.resources`)
   così so con certezza cosa importare. **Attenzione: questa parte non l'ho mai potuta compilare
   qui, è la prima volta che questa funzione viene usata nel progetto — se la build fallisce
   proprio su questo (import di `Res`/`aila_logo`, o `painterResource`), mandami l'errore preciso
   così la correggo.**
4. **"Messaggi dalla classe" in Home** (confermato: "Si, voglio anche quella funzione."):
   aggiunta la card sotto "Prossimi eventi" con l'ultimo messaggio della Bacheca (autore + titolo
   proposta), cliccabile per andare alla Bacheca. Non serviva nuovo codice per scaricare i dati:
   la Home carica già le proposte quando apri quella scheda, e il backend le restituisce già
   ordinate dalla più recente. Se non ci sono ancora messaggi in Bacheca la card semplicemente
   non appare.

Non ancora consegnati/committati: i loghi orizzontali e la variante tonda del foglio non sono
stati usati da nessuna parte per ora — se vuoi usarli (es. nella schermata di login, o come
icona "tonda" alternativa) dimmelo.

### Fasi 4, 5 e 6 completate (6/9, da "Nelle foto hai come dovrebbe e come è in realtà la UI")

Simone ha mandato mockup e screenshot reali affiancati. Tre scelte prese prima di scrivere codice:
intestazioni **chiare** su tutte le schermate tranne la Home (fedeli al mockup, niente gradiente
scuro ovunque); font **Sora non integrato** (resta quello di sistema); ambito: prima il
restyling, poi stati vuoti/errore, poi onboarding.

**Nuovo file `design/AilaComponents.kt`** — finora ogni schermata si scriveva l'intestazione a
mano, con spaziature e dimensioni leggermente diverse: è una delle ragioni per cui l'app sembrava
meno curata del mockup. Ora ci sono `AilaScreenHeader` (barra bianca: titolo + sottotitolo +
azione + filo di divisore), `AilaBackBar` (schermate a schermo intero), `AilaSegmentedTabs`,
`AilaEmptyState` e `AilaErrorState`.

Cosa è cambiato, schermata per schermata:

- **Calendario**: intestazione condivisa, liste con padding coerente, card con bordo sottile
  (senza, su sfondo chiarissimo sparivano), stato vuoto con icona + spiegazione + "Aggiungi
  evento" al posto della riga di testo grigio.
- **Classe (Circolari + Bacheca)**: il selettore non è più due chip identiche a quelle dei filtri
  di contenuto (non si capiva che cambiavano schermata invece di filtrare) ma un selettore a
  segmenti su barra bianca; il tasto Scheda Classe accanto. Le due schermate figlie hanno perso
  il titolo grande, che ora è dato dal selettore, e hanno stati vuoti distinti per "non c'è
  niente" e "il filtro/la ricerca non trova niente".
- **Mappa posti**: intestazione condivisa; stato vuoto esplicito quando nessuna disposizione è
  stata pubblicata (prima la griglia restava semplicemente vuota, senza spiegare perché).
- **Altro/Profilo**: intestazione condivisa e — bug vero, non solo estetica — **il contenuto ora
  scorre**: era una Column fissa, quindi il tasto "Esci dall'Account" in fondo restava fuori
  schermo e irraggiungibile.
- **Dettaglio circolare, Notifiche, Scheda Classe, Sondaggi, Preferenze Sociali**: tutte usano la
  stessa `AilaBackBar`; via i titoli duplicati (es. "Sondaggi Interrogazioni" compariva due volte,
  nella barra e sotto).
- **Login**: logo vero al posto della sola scritta, e selettore Accedi/Registrati a segmenti.
- **Bottom bar**: etichette a una riga sola (`maxLines = 1`, `softWrap = false`, 10sp): "Mappa
  posti" andava a capo su due righe e sballava l'altezza della barra. **Da verificare a occhio**:
  se a 10sp l'etichetta risultasse tagliata, la via più semplice è accorciarla in "Posti".
- **Stati d'errore**: `LoadableContent` non mostra più una riga di testo rosso al centro dello
  schermo ma lo stato curato del mockup con **"Riprova"**; per farlo funzionare sono stati
  aggiunti i contatori `circularsRefreshTrigger` e `seatMapRefreshTrigger` (calendario, bacheca e
  sondaggi avevano già il proprio).
- **Onboarding**: nuova `OnboardingScreen`, 3 schermate come nel mockup, mostrata una volta sola
  al primo avvio prima del login. Il flag sta in `LocalSettingsManager.hasSeenOnboarding`, e
  `clear()` (che gira al logout) ora lo preserva: dopo un logout si torna al login, non alle
  schermate di presentazione. Niente `HorizontalPager`: avanzamento a pulsante, per non dipendere
  da API della libreria pager mai usate altrove in questo progetto.

**Non compilato.** In questo passaggio non ho potuto lanciare Gradle: il ponte verso il tuo
computer questa volta espone solo lettura/scrittura file, non l'esecuzione di comandi. Ho
verificato a mano import, riferimenti e bilanciamento delle parentesi di tutti i file toccati, ma
la prova vera è `./gradlew :androidApp:assembleDebug`. Se salta qualcosa, mandami l'errore
preciso. Punto più a rischio, già noto: le Compose Resources (`Res.drawable.aila_logo`), ora usate
anche nel login oltre che nella schermata di caricamento — se il meccanismo non compila, l'errore
arriva da lì.

**Restano fuori** (concordato: "le cose principali prima, il resto dopo"): la ricerca globale del
mockup (funzione nuova, non restyling), il font Sora, e le schermate "Classi"/"Dettaglio classe"
del mockup, che presuppongono il multi-classe non ancora implementato.

### "Questo non è quello che ti ho chiesto" — seconda passata: il linguaggio visivo (6/9)

Il passaggio precedente aveva sistemato la *struttura* (intestazioni allineate, divisori, stati
vuoti) ma non l'*aspetto*: l'app era più ordinata e continuava a non somigliare al mockup. Errore
mio a monte: la domanda iniziale l'avevo impostata su "quale stile di intestazione", che è un
dettaglio, invece che sul linguaggio visivo. Differenze concrete che erano rimaste:

- il gradiente era un blu notte piatto, nel mockup è blu → viola come il logo;
- nel mockup ogni riga ha la sua icona in un riquadro colorato tenue, in app erano righe di testo;
- le card avevano un filo di bordo, nel mockup hanno un'ombra morbida;
- il marchio AILA non compariva in nessuna schermata;
- i pulsanti erano rettangoli blu piatti, nel mockup sono pieni e sfumati.

Deciso con Simone: prima il linguaggio visivo su ciò che c'è, poi le schermate mancanti. Font
Sora confermato **fuori** (resta quello di sistema).

**`AppTheme` — token che mancavano**: `HeroGradient` (blu → viola, quello vero del brand),
`HeroGradientDeep` (splash e onboarding), `PrimaryGradient` (riempimento dei pulsanti),
`CardElevation`, le sei coppie di tinte per i riquadri icona (`TintBlue`/`TintBlueInk`, ecc.) e i
colori di testo (`TextDark`/`TextMuted`/`TextFaint`/`Hairline`). Prima ogni schermata si scriveva
i propri esadecimali a mano: è la ragione tecnica per cui sembrava "fatta da un'altra persona"
rispetto al mockup.

**`AilaComponents` — componenti nuovi**: `AilaCard` (card bianca con ombra), `AilaIconTile`
(riquadro icona colorato), `AilaListRow` (riga icona + titolo + sottotitolo + freccia),
`AilaPrimaryButton` / `AilaSecondaryButton` (pulsanti sfumati), `AilaSectionTitle`,
`AilaBrandMark`. L'intestazione ora porta il marchio in cima.

Schermate riscritte o ripassate: Home (pannello a gradiente vero, righe con riquadro icona,
sezione "Scorciatoie"), Profilo (card d'identità a gradiente + righe raggruppate in sezioni, non
più quattro riquadri bianchi identici), Calendario, Circolari, Bacheca, Mappa posti, Notifiche,
Onboarding, Splash, bottom bar.

Correzioni di contenuto fatte per strada:
- il sottotitolo degli eventi stampava il nome grezzo dell'enum ("VERIFICA" tutto maiuscolo): ora
  è "Verifica • 09:00" o "• Tutto il giorno";
- nelle circolari il badge aveva sia l'emoji del pallino sia il testo: ora c'è un pallino
  disegnato e il testo, senza emoji.

**Icona dell'app rifatta (era la segnalazione più netta: "questo è il modo di fare un'icona?").**
L'icona era un PNG "legacy" che conteneva già dentro di sé la piastrella bianca con angoli
arrotondati e ombra: il launcher lo rimpiccioliva e lo incollava nella propria maschera, per cui
sulla home compariva un quadratino bianco con il logo piccolo dentro, diverso da tutte le altre
icone. Ora è un'**icona adattiva** vera:
- `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml` con livelli separati;
- `drawable/ic_launcher_background.xml`: gradiente blu notte a tutto campo;
- `mipmap-*/ic_launcher_foreground.png`: solo il segno, dentro la safe zone (66dp su 108dp), a
  tutte le densità — ritagliato dall'asset del brand kit isolando il segno dallo sfondo;
- `mipmap-*/ic_launcher_monochrome.png`: silhouette per le icone a tema di Android 13+, che prima
  mancava del tutto (con "icone a tema" attive AILA sarebbe rimasta l'unica a colori);
- i PNG legacy sono stati rigenerati a tutto campo (fondo + segno) invece che con la piastrella
  bianca dentro.
Il manifest non è cambiato: `@mipmap/ic_launcher` e `@mipmap/ic_launcher_round` ora risolvono
prima ai file `anydpi-v26`. Va disinstallata e reinstallata l'app per vedere l'icona nuova: il
launcher tiene in cache la vecchia.

Restano fuori: le schermate del mockup che ancora non esistono (ricerca globale, Classi/Dettaglio
classe — che presuppongono il multi-classe non implementato), e il font Sora.

### Schermate mancanti dal mockup (6/9, terza passata)

Costruite le due che si reggono su dati veri:

- **Ricerca globale** (`SearchScreen.kt`, mockup 23) — una casella sola che cerca insieme tra
  circolari, eventi di calendario e proposte della bacheca, con filtri per tipo e risultati che
  portano alla schermata giusta. Punto d'ingresso: la lente accanto alla campanella nella Home,
  che prima non esisteva. Le ricerche recenti sono salvate in locale
  (`LocalSettingsManager.recentSearches`, al massimo cinque, mai inviate al server).
  **Limite dichiarato:** cerca solo tra i dati già scaricati, e per le circolari confronta numero
  e titolo, non il contenuto del PDF. Cercare dentro i PDF vorrebbe dire scaricarli e analizzarli
  tutti a ogni lettera digitata: andrebbe fatto con un indice costruito una volta sola quando la
  circolare viene classificata.
- **Dettaglio notifica** (mockup 17/24) — toccando una notifica si apre il testo per intero, con
  quando è arrivata. Nella lista il corpo resta troncato a due righe.
- Aggiunta l'icona `AppIcons.Search` (lente), disegnata con curve quadratiche e non con
  `drawArc`/`addArc`, che come già annotato nel file fanno crashare Android in KMP.

**Non costruite, con motivo:** le schermate "Le mie classi" e "Dettaglio classe" del mockup
presuppongono il multi-classe, che non è implementato (è progettato più su in questo file). Farne
l'interfaccia adesso vorrebbe dire costruire una schermata che mostra sempre e solo una classe
finta: è lavoro buttato finché il modello dati non c'è. Stessa ragione per "Tutorial rapido" e
"Permessi" del mockup: sono schermate di sistema che hanno senso solo quando ci saranno permessi
veri da chiedere (notifiche push a Firebase configurato).

### Quarta passata: tema Material, campi, dettagli (6/9, "continua, non è perfetta")

**Il tema mancava del tutto** (`design/AilaTheme.kt`, nuovo). L'app non impostava nessuno schema
di colori Material, quindi tutti i componenti standard — interruttori, slider, campi di testo,
indicatori di caricamento, pulsanti dei dialoghi — uscivano nel **viola di default di Material 3**.
Si vede negli screenshot che mi hai mandato: l'interruttore delle notifiche nel Profilo e lo slider
dei pesi nella Mappa posti sono viola, mentre tutto il resto è blu AILA. Le schermate erano state
colorate a mano una per una, ma il colore dei componenti di sistema non lo decide la schermata: lo
decide il tema. Ora c'è `AilaTheme`, applicato nei punti d'ingresso di piattaforma (MainActivity e
MainViewController) così vale anche per login, splash e onboarding. Disattivata anche la
"tinta di elevazione" di Material, che avrebbe reso azzurrine le card bianche.

**Bug trovato per strada, non grafico:** `MainViewController.kt` (iOS) chiamava
`MainAppShell(currentUserId = ..., isRepresentative = ...)`, parametri che nella firma attuale non
esistono più — residuo della versione con l'utente fittizio. Da quando sono stati aggiunti i target
iOS alla build quel file non compilava. Ora chiama `MainAppShell()` come fa MainActivity.

Altro in questa passata:
- `ailaFieldColors()`: stile unico per tutti i campi di testo (sfondo bianco, bordo appena
  percettibile, cursore blu). Applicato a login, ricerca circolari, ricerca globale, API key.
- Nella ricerca circolari la lente era l'emoji 🔍: ora è l'icona vettoriale.
- Le chip filtro selezionate usano il riempimento sfumato, come le tab segmentate.
- Login: il messaggio d'errore era testo rosso nudo in mezzo ai campi, ora è un riquadro; pulsante
  d'azione ad altezza piena.
- Dettaglio circolare: card condivise, riquadro icona per il PDF e per l'analisi AI, badge col
  pallino colorato invece dell'emoji, pulsante "Apri il PDF" primario.
- Bacheca: voti e commenti erano tre coppie "emoji + numero" allineate nel vuoto, senza area di
  tocco riconoscibile; ora sono pillole colorate.
- Scheda Classe, Sondaggi e Storico sondaggi: card con ombra morbida come il resto.

Non verificato in build: non hai potuto compilare in questo passaggio, quindi tutto quanto sopra è
controllato solo a lettura (import, riferimenti, parentesi bilanciate).

### Riassunto AI rotto + animazioni (6/9, quinta passata)

**Perché il riassunto AI non funzionava.** Il modello impostato era `gemini-2.0-flash`, che Google
ha messo in dismissione: la chiamata tornava con un errore di modello non trovato e l'app ricadeva
in silenzio sulla classificazione euristica a parole chiave. Tre cambiamenti in
`ClientSideAiClassifier`:

1. **Modello predefinito `gemini-flash-latest`** (alias, non un numero di versione): punta sempre
   al flash corrente, quindi il problema non si ripresenta al prossimo giro di versioni.
2. **Scaletta di ripiego**: se il modello viene rifiutato con "non trovato", l'app prova gli altri
   nomi noti invece di dichiarare fallita la classificazione. Un errore diverso (chiave non valida,
   quota esaurita, rete assente) interrompe subito, senza tentativi inutili.
3. **Rilevamento automatico**: se nessun nome noto viene accettato, l'app chiede a Google l'elenco
   dei modelli disponibili per quella chiave (`GET /v1beta/models`) e ne sceglie uno che supporti
   `generateContent`, preferendo un "flash" stabile. Il modello che funziona resta in memoria per
   le circolari successive. Così l'app si ripara da sola anche fra un anno.

Aggiunta anche la chiave nell'header `x-goog-api-key` oltre che come parametro `?key=`: è la forma
documentata da Google, e mandarle entrambe non costa nulla.

**Animazioni** (`design/AilaMotion.kt`, nuovo). Finora l'unica animazione era quella delle chip
filtro: liste, card e schermate comparivano di colpo.
- `Modifier.ailaAppear(index)`: entrata in dissolvenza con una risalita di pochi dp, sfalsata per
  posizione così le liste arrivano a cascata. Applicata a Home, calendario, circolari, bacheca,
  risultati di ricerca.
- `Modifier.ailaBreathe()`: respiro lento del logo nella schermata di caricamento.
- Passaggio Circolari ↔ Bacheca in dissolvenza.
- Card della bacheca con `animateContentSize()`: aprendo i commenti la card cresce invece di far
  saltare la lista sotto.

**Calendario, difetto trovato:** la striscia dei giorni era decorativa — si toccava, cambiava
colore e non filtrava niente. Ora il giorno selezionato filtra davvero, c'è una cella "Tutti" per
togliere il filtro, e sotto i giorni con almeno un evento c'è il pallino del mockup.

### Sesta passata: calendario vero, PDF interno, emoji fuori, funzioni rotte (6/9)

**Calendario vero.** Al posto della striscia orizzontale di numeri (1-15, scollegata dal calendario
reale) c'è la griglia del mese come nel mockup: mese sfogliabile avanti e indietro, giorni nella
colonna del loro giorno della settimana, oggi cerchiato, pallino sotto i giorni con eventi, e sotto
la griglia gli eventi del giorno scelto. Ha richiesto un po' di aritmetica delle date scritta a mano
(`util/CivilDate.kt`): quanti giorni ha un mese, su che giorno cade il primo (Sakamoto), conversione
da millisecondi a data civile. Nessuna libreria aggiunta, stesso codice su Android e iOS.

**PDF dentro l'app** (`pdf/PdfPageRenderer.kt` + attuazioni per piattaforma). Il dettaglio di una
circolare mostrava un segnaposto e un tasto che apriva il visualizzatore di sistema: per leggere una
circolare si usciva da AILA. Ora su **Android** le pagine vengono disegnate con `PdfRenderer` di
sistema (nessuna libreria in più nell'APK) e scorrono dentro la schermata; il tasto "Apri fuori"
resta come alternativa. Su **iOS non è implementato**: la strada è CoreGraphics, ma non ho modo di
provarla qui e preferisco lasciarla dichiarata mancante piuttosto che scrivere codice iOS mai
eseguito e spacciarlo per funzionante — lì la schermata si comporta come prima.

**Emoji fuori, icone dentro.** Le emoji (👍 👎 🗑 👑 ⭐ 📝 💳 🤖 …) le disegna il sistema operativo:
cambiano forma tra Android e iOS, non seguono la palette e non si possono animare. Ne ho disegnate
18 come icone vettoriali in `AppIcons` (pollici, cestino, corona, stella, matita, carta, pullman,
scudo, lampadina, lucchetto, cursori, scintilla, spunta, avviso, fulmine, più, frecce) e le ho
sostituite ovunque. Due nuovi componenti animati al loro posto:
- `AilaIconAction`: voto/commento con icona, rimpicciolisce alla pressione e rimbalza quando il
  contatore cambia — così il tocco si vede prima ancora della risposta del server;
- `AilaAiBadge`: marchio "generato dall'AI" con la scintilla che pulsa piano.

**Funzioni rotte, trovate leggendo il codice:**
1. **Mappa posti — i cursori.** C'era **un solo slider per tre pesi**: quello sociale. Disciplina e
   Didattica comparivano nell'etichetta ma non erano regolabili in alcun modo, restavano a 1.0x
   qualunque cosa si facesse. Ora ognuno ha il suo cursore, con scritto sotto cosa cambia.
2. **Mappa posti — le votazioni.** Il banner "il Rappresentante ha aperto la votazione" compariva
   solo agli studenti NON rappresentanti (`isPreferencesOpen && !isRepresentative`): tu apri la
   votazione e poi non hai nessun modo di votare a tua volta, pur sedendo in classe come tutti. Ora
   il banner c'è per chiunque quando la finestra è aperta.
3. **Sondaggi — UX.** I quattro pulsanti erano etichettati solo col punteggio ("+50 pt", "-300 pt")
   e i limiti di budget erano scritti in una riga di testo ma non applicati: si potevano assegnare
   più voti del consentito e scoprirlo dopo. Ora ogni opzione dice cosa significa ("Ci sto",
   "Indifferente", "Meglio di no", "Impossibile"), il budget è una fila di pallini che si riempie, e
   le opzioni esaurite si spengono da sole. Aggiunta la spiegazione di come funziona il meccanismo,
   che prima non c'era da nessuna parte.

**Card profilo** rifatta: era un riquadro bianco con le iniziali in un cerchietto e due righe
"etichetta: valore", indistinguibile dalle card di impostazioni sotto. Ora è il pannello a gradiente
del brand con l'avatar grande, il ruolo in una pillola e i due dati in riquadri affiancati.

Non verificato in build (non hai potuto compilare): controllati a lettura import, riferimenti,
parentesi e chiamanti di ogni firma cambiata, due volte.

---

## 7 settembre 2026 — Logo vettoriale, bug modalità aereo, login e onboarding, Impostazioni

### Il logo "quadrato spiaccicato"

`aila_logo.png` è un'immagine **512x512 in RGB, senza canale alpha**: lo sfondo blu notte è parte
dell'immagine e non si può togliere. Nel login veniva mostrata a 72dp senza alcun ritaglio, quindi
sulla card bianca compariva un quadrato scuro netto — quello che hai visto. Nelle intestazioni era
ritagliata ad angoli tondi, ma ridotta da 512px a 24px il disegno si impastava.

Ho ricostruito il marchio come **geometria vettoriale** in `design/AilaLogo.kt`: due gambe dritte
che si chiudono in una punta arrotondata più il pallino centrale, sul riquadro ad angoli tondi con
il gradiente del brand e un riflesso in alto a sinistra. Essendo vettoriale è nitido a ogni misura,
ha sfondo trasparente e segue il tema scuro. `AilaBrandMark`, la schermata di caricamento e il
login ora usano questo; il PNG non è più referenziato da nessun file Kotlin (l'icona del launcher
Android resta quella vera, sono file diversi).

Vincolo rispettato, lo stesso già annotato in `AppIcons`: niente `drawArc`/`addArc`/`drawRoundRect`,
che su Android in KMP crashano con `ClassNotFoundException SkiaBackedPath`. Il pallino è un cerchio
fatto con quattro bezier cubiche, gli angoli tondi vengono da `clip(RoundedCornerShape)`.

### Bug: la modalità aereo faceva uscire dall'account

Trovato. Era in `AuthRepository.restoreSession()`:

```kotlin
} catch (e: Exception) {
    settings.authToken = ""      // <- qualunque errore = logout
    settings.currentUserId = ""
    null
}
```

Il `catch (e: Exception)` non distingueva **"il token non vale più"** da **"non c'è rete"**. Una
galleria, un aereo o un secondo di 4G ballerino equivalevano quindi a un logout, con la password da
riscrivere — e le schermate di errore che avevo curato non venivano mai raggiunte, perché l'app era
già tornata al login.

Cosa ho cambiato:
- `restoreSession()` ora restituisce un `SessionRestore` (`Online` / `Offline` / `OfflineWithoutCache`
  / `SessionExpired` / `NoSession`) invece di un `Pair?` che appiattiva tutto;
- **solo un 401/403 del server** chiude la sessione. È l'unica risposta che dica davvero che il
  token non è più valido. 500, 502, timeout, DNS, rete assente: il token resta;
- il profilo utente viene salvato in locale a ogni accesso riuscito (`cachedUserJson` in
  `LocalSettingsManager`), così senza rete si entra comunque con l'ultimo profilo conosciuto;
- in cima all'app compare una striscia gialla "Nessuna connessione — sei entrato con gli ultimi dati
  salvati", con "Riprova" e "Chiudi". Le singole sezioni mostrano i loro stati d'errore con il
  pulsante Riprova, come previsto;
- caso raro (primo avvio dopo l'installazione, senza rete e senza copia locale): schermata dedicata
  con "Riprova", e il logout come **scelta esplicita**, non più come conseguenza automatica.

La cache viene svuotata al logout.

### Login e registrazione, rifatti

- logo vettoriale con alone morbido al posto del PNG;
- velatura del gradiente del brand in alto: non è più un rettangolo grigio piatto;
- segnaposto al posto delle etichette fluttuanti, come nel mockup;
- **mostra/nascondi password**: prima una password sbagliata di un carattere restava invisibile e
  sembrava un errore del server;
- i campi della registrazione entrano ed escono con un'animazione invece di apparire di scatto;
- l'altezza non è più uno slider nudo: valore in una pillola e spiegazione del perché viene chiesta;
- errore in un riquadro con icona, non testo rosso in mezzo ai campi.

### Onboarding, rifatto

Prima era corretto ma inerte: un riquadro sfumato con dentro un cerchio fermo, cambio pagina solo
col pulsante, e l'unica animazione erano i pallini in fondo.

- illustrazione viva: l'icona respira dentro un anello di otto puntini che ruota lentamente, e due
  aloni di luce si spostano nel riquadro;
- ogni pagina ha un colore d'accento e un gradiente propri, così si vede di aver cambiato schermata;
- **si scorre col dito**, avanti e indietro (`detectHorizontalDragGestures`, niente HorizontalPager:
  nessuna dipendenza nuova). Era il gesto che chiunque prova per primo e non faceva nulla;
- passaggio in dissolvenza fra una pagina e l'altra;
- testi riscritti: dicono cosa fa l'app, non solo che esiste.

### Impostazioni (punto 4 della tua lista) — fatto

Nuova schermata `SettingsScreen`, raggiungibile da Altro → Impostazioni:
- **Chiave AI**, spostata fuori dal profilo (dove stava in mezzo ai dati personali, con la casella
  sempre aperta), più il pulsante **"Prova la chiave"**: fa una chiamata minima e riporta per
  esteso cosa risponde Google. Nasce dal tuo punto 3 — se l'AI non funzionava, l'app ricadeva in
  silenzio sull'euristica a parole chiave e non c'era modo di sapere se fosse la chiave, la quota,
  il modello ritirato o la rete;
- **filtri notifiche** per categoria (circolari, calendario, bacheca, mappa posti, sondaggi), non
  più solo l'interruttore unico della bacheca;
- **tema chiaro/scuro** e **stile Android/iOS**, entrambi persistenti e riletti prima della prima
  composizione (su Android in `MainActivity`, su iOS in `MainViewController`) così chi usa il tema
  scuro non vede il lampo bianco all'avvio.

Sullo stile iOS c'è scritto in schermata cosa fa davvero: cambia raggi, ombre e proporzioni, ma i
componenti restano quelli di Material, non sono quelli nativi di Apple.

### Restano aperti (dalla lista dei 12 punti)

- **(2) Una classe per studente, selezionabile all'accesso.** Richiede un intervento sul backend:
  `schema.sql` dice esplicitamente "Classe singola implicita server-side" con
  `app_config.class_id = 'DEFAULT_CLASS'`. Serve una colonna `class_id` su `users`, il filtro in
  ogni rotta e una migrazione D1. Non è una modifica di UI e non la faccio finta.
- **(1) Notifiche di novità** per circolari/bacheca/mappa posti: i marcatori
  (`lastSeenCircularNumber`, `lastSeenProposalId`, `lastSeenSeatMapSignature`) sono in
  `LocalSettingsManager`, manca il collegamento in `MainAppShell`.
- **(8) Proposte modificabili** con la dicitura "Modificato": la rotta `PUT /api/proposals/:id`
  esiste già lato backend, mancano `ProposalsRepository.updateProposal(...)` e la finestra di
  modifica.
- **(10) Invio collettivo dei sondaggi e scadenza.** L'invio locale ("Invia le mie scelte") c'è, ma
  perché l'algoritmo parta "quando tutti hanno finito" serve che l'invio arrivi al server:
  `interrogation_votes` non ha né un campo `submitted_at` né un `closes_at` sulla griglia.
- **(6) Animazioni al tocco** su card e righe di lista: ancora da fare.

Non verificato in build (non puoi ancora compilare): controllati a lettura bilanciamento delle
parentesi su tutti i file, import inutilizzati o mancanti sui file toccati, e ogni chiamante delle
firme cambiate (`ProfileScreen`, `AuthRepository.restoreSession`, `SettingsScreen`).

---

## 7 settembre 2026 (2) — I punti rimasti: multi-classe, invio sondaggi, proposte modificabili, notifiche, tocco

Questa passata tocca anche il **backend**, non solo l'app: due dei punti della tua lista non erano
risolvibili lato client e li avevo lasciati indietro. Ora ci sono.

### Punto 2 — Una classe per studente, scelta all'accesso

Era la modifica più profonda: `schema.sql` diceva "Classe singola implicita server-side", con
`app_config.class_id = 'DEFAULT_CLASS'` cablato nelle query. Nessuna tabella sapeva a quale classe
appartenesse un utente: **bastava che si registrasse uno studente di un'altra classe per vedere la
tua bacheca, il tuo calendario e la tua mappa posti.**

Cosa è cambiato:

- nuova tabella `classes` (id, etichetta, anno, finestra preferenze). Non l'ho precompilata con
  classi inventate: parte da quella che c'era già e cresce quando qualcuno si registra indicando
  una classe nuova;
- colonna `class_id` su `users`, `calendar_events`, `proposals`, `interrogation_grids` e
  `seat_map_history`, con indice;
- **le circolari restano volutamente fuori**: arrivano da Spaggiari e valgono per tutto l'istituto,
  duplicarle per classe vorrebbe dire scaricare lo stesso PDF N volte. È l'analisi AI, che gira sul
  tuo telefono con la tua chiave, a dire se una circolare ti riguarda;
- il JWT porta la classe, così ogni rotta filtra senza una query in più. **I token già emessi non
  ce l'hanno**: in quel caso `resolveClassId()` la rilegge dal database invece di invalidare la
  sessione di tutti — altrimenti l'aggiornamento avrebbe buttato fuori l'intera classe;
- filtro applicato in: `users` (l'elenco "compagni di classe" adesso lo è davvero — prima la mappa
  posti e la votazione preferenze elencavano *tutti* gli utenti registrati), `calendar`,
  `proposals`, `preferences` (**la finestra preferenze era globale: aprirla in una classe la
  apriva a tutte**), `ratings`, `seatmap` (`reset-history` azzerava lo storico di tutte le classi),
  `polls`;
- anche le notifiche push: `notifyClass` usava il topic unico `class`, che avrebbe raggiunto pure
  le altre classi. Ora, quando la notifica è di classe, manda ai token di quella soltanto;
- in registrazione: le classi esistenti come pastiglie da toccare, più il campo per scriverne una
  nuova. L'etichetta viene normalizzata lato server — "4csa", "4^ CSA" e "4 CSA" sono la stessa
  classe — altrimenti sarebbero tre classi separate, ognuna con la sua bacheca, e nessuno potrebbe
  più unirle.

**Da fare a mano, una volta:**

```
cd backend
wrangler d1 execute <NOME_DB> --remote --file=./migrations/001_multiclasse.sql
```

La migrazione mette tutti i dati esistenti in `DEFAULT_CLASS` — cioè esattamente dov'erano. È
rieseguibile, ma gli `ALTER TABLE` al secondo giro danno "duplicate column name": è innocuo, vuol
dire che quel pezzo era già applicato. Il tuo utente resta in `DEFAULT_CLASS`, la cui etichetta
diventa quella che avevi in `app_config` (4^ CSA).

### Punto 10 — Invio delle scelte e scadenza dei sondaggi

Prima "Invia le mie scelte" era **solo un interruttore sul tuo telefono**: chiudeva il flusso lato
studente, ma il server non ne sapeva nulla. Il Rappresentante non poteva vedere chi avesse finito, e
l'algoritmo non aveva modo di partire "quando hanno votato tutti".

- nuova tabella `interrogation_submissions` e colonna `closes_at` sulla griglia;
- `POST /:id/submit` e `DELETE /:id/submit` (per correggere prima della scadenza),
  `PUT /:id/deadline` per il Rappresentante;
- dopo l'invio i voti si bloccano: altrimenti "invia" non vorrebbe dire niente. A tempo scaduto non
  si vota e non si annulla più — si potrebbero altrimenti cambiare le scelte dopo aver visto il
  calendario che ne è uscito;
- `assignments/run` parte quando hanno inviato tutti **oppure** quando la scadenza è passata; con
  `?force=1` il Rappresentante procede lo stesso quando si sa che qualcuno non voterà (assente da
  settimane, telefono rotto) e la classe non può restare ferma;
- in cima al sondaggio, sotto la barra di avanzamento, ora c'è "3 di 21 compagni hanno inviato" e la
  scadenza. Serve a capire perché l'algoritmo non è ancora partito senza doverlo chiedere;
- quando l'ultimo invia, parte una notifica alla classe.

**Bug trovato strada facendo (punto 11).** Avevo tolto il tetto ai Verdi e ai Rossi Chiari lato app,
ma il server continuava a imporne 3 ciascuno: l'app avrebbe accettato il quarto voto e il server lo
avrebbe rifiutato. Ora la regola è la stessa da entrambe le parti — **l'unico tetto è il Rosso Scuro
(max 2)**, che è il veto: se fosse illimitato, bloccare tutte le date sarebbe gratis e l'algoritmo
non avrebbe più margine.

### Punto 8 — Proposte modificabili

- `PUT /api/proposals/:id` non è più riservata al Rappresentante: **l'autore può modificare la
  propria**. Prima chi si accorgeva di un refuso poteva solo cancellare e riscrivere, perdendo voti
  e commenti già raccolti;
- nuova colonna `edited_at`, valorizzata a ogni modifica da chiunque provenga;
- in bacheca compare la pastiglia **"Modificato"** (prima era un "(modificato dai rappresentanti)"
  fra parentesi, che si accendeva solo per le modifiche del Rappresentante);
- finestra di modifica con titolo e descrizione. La categoria non si tocca di proposito: cambiarla
  sposterebbe la proposta sotto un altro filtro senza che chi l'ha votata se ne accorga.

### Punto 1 — Le novità si accumulano nella campanella

- `noteNovelties()` confronta quello che arriva dal server con l'ultima cosa vista e scrive nello
  storico locale: nuova circolare, nuova proposta, nuova disposizione dei banchi;
- **al primo avvio non notifica nulla**, prende solo nota del punto di partenza: altrimenti la
  campanella si riempirebbe di avvisi per roba già lì da settimane;
- i filtri per categoria delle Impostazioni valgono qui. Una categoria spenta aggiorna comunque il
  segnalibro, così riaccendendola non arriva un arretrato;
- controllo anche in sottofondo ogni 5 minuti mentre l'app è aperta: prima una novità si scopriva
  solo entrando nella sua sezione. Volutamente **non** aggiorna le liste a schermo — sovrascrivere
  quello che stai guardando mentre lo guardi è quello che fa "saltare" una lista sotto il dito;
- per la mappa posti il segnalibro è una firma della disposizione (chi siede dove): cambia
  esattamente quando cambia la disposizione, che è la cosa da notificare.

### Punto 6 — Animazioni al tocco

- nuovo `Modifier.ailaPressable`: l'elemento si rimpicciolisce mentre il dito preme e torna con un
  piccolo rimbalzo al rilascio. Applicato a card, righe di lista, pulsanti primari e secondari,
  linguette del selettore e freccia indietro;
- la scala è tarata sulla dimensione — una card grande che rimpicciolisce del 1,5% si nota quanto un
  pulsante che rimpicciolisce del 4,5%;
- l'increspatura di Material è tolta di proposito: sommata alla scala il risultato è confuso, e su
  iOS non esiste;
- ultime frecce di testo sostituite con icone vettoriali: "←" nella barra indietro, "›" nelle righe
  di lista, "→" nella riga di uscita. Erano glifi disegnati dal sistema operativo, quindi diversi
  fra Android e iOS e di peso incoerente con le altre icone.

### Una nota sul ponte con il tuo computer

La cartella `shared/src/commonMain/kotlin/circolareplus/data/remote/dto/` è **otto** livelli sotto
`Circolare+`, e il ponte con cui scrivo sul tuo computer ne regge al massimo sette: quei file non
riesco né a leggerli né a scriverli. I DTO nuovi (classi, avanzamento sondaggio, modifica proposta)
sono quindi in `data/remote/AilaApiDto.kt`, con `package ...data.remote.dto`: per il compilatore non
cambia nulla, Kotlin non richiede che il percorso rispecchi il package.

Per la stessa ragione `ProposalDto` non ha potuto ricevere il campo `editedAt`: il server manda
`modifiedByRep = true` per *qualunque* modifica (il nome resta per non rompere le app già
installate) e lato app il campo si chiama ora `Proposal.isEdited`. Se colleghi dal desktop anche la
cartella `dto`, sistemo entrambe le cose per bene.

### Stato

Ho visto che hai aggiunto tu `import androidx.compose.runtime.remember` a HomeScreen.kt — l'ho
ripreso dalla tua versione invece di sovrascriverlo, ed era un errore mio della passata precedente.
Ho aggiunto al controllo automatico anche la ricerca degli import di runtime mancanti, così quel
tipo di svista non dovrebbe ripetersi.

Controlli fatti: bilanciamento parentesi su tutti i file Kotlin; import mancanti/inutilizzati sui
file toccati; ogni chiamante di ogni firma cambiata; esistenza di ogni metodo `AppContainer.<repo>`
chiamato; corrispondenza fra segnaposto `?` e argomenti di `bind()` in tutte le query SQL; parsing
TypeScript del backend con `tsc` (restano solo errori dovuti ai tipi Cloudflare non installati qui,
che spariscono con `npm install` nella tua cartella `backend`).

Il Kotlin non è compilato: quello puoi farlo solo tu.
