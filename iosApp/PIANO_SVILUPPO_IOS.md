# Piano sviluppo iOS — Circolare+

Decisioni prese prima di scrivere questo piano (non richiedere conferma su questi punti):

- **Deployment target iOS: 26.0** per tutta l'app (non serve gestire compatibilità con versioni precedenti).
- **AI locale su iOS: solo Apple Intelligence** (`FoundationModels`, iOS 26+, richiede iPhone 15 Pro o
  successivo). Nessun download di modelli su iOS: il modello è quello di sistema, sempre "installato"
  se il dispositivo lo supporta. Il catalogo con Qwen/Gemma scaricabili resta solo Android.
- **Push notifications iOS**: si scrive il codice ma resta inerte finché non verrà aggiunto
  `GoogleService-Info.plist` (non ancora disponibile). Fase E, a bassa priorità.
- **Generazione progetto Xcode**: con [XcodeGen](https://github.com/yonaskolb/XcodeGen) da un
  `project.yml` testuale, invece di scrivere a mano un `.pbxproj` (binario/XML fragilissimo da
  editare senza Xcode). XcodeGen si installa anche dentro CI (`brew install xcodegen` sui runner
  macOS di GitHub Actions).
- **Verifica**: nessuno di noi ha un Mac. L'unico modo per sapere se qualcosa compila è la CI
  GitHub Actions (Fase B) — va quindi creata **per prima**, subito dopo lo scheletro del progetto,
  e ogni fase successiva va verificata aprendo l'ultima run della action.

Ogni task sotto è pensato per essere incollato come prompt a un agente. Vanno eseguiti **in
ordine** (rispettare le dipendenze indicate) perché i task successivi assumono che i file dei
precedenti esistano già.

---

## Fase A — Scheletro progetto Xcode (XcodeGen)

### A1. Creare `iosApp/project.yml`
**Dipendenze:** nessuna.
**Prompt:**
> Nel repository `Circolare+`, crea il file `iosApp/project.yml` per XcodeGen che descrive un'app
> iOS chiamata "Circolare+" (nome progetto Xcode: `iosApp`), bundle id `com.circolareplus`,
> deployment target iOS 26.0, piattaforma solo iOS (no macOS/watchOS). Il target `iosApp`
> deve avere come sorgenti la cartella `iosApp/iosApp` (Swift + Info.plist + eventuali asset).
> Deve includere una build phase "pre-build" (o "scripts" con `script:` e `name:` in project.yml)
> che esegue, dalla root del repo:
> ```
> cd "$SRCROOT/.."
> ./gradlew :shared:embedAndSignAppleFrameworkForXcode
> ```
> Questo è il task Gradle standard generato dal plugin Kotlin Multiplatform per compilare
> `shared/` in un framework e copiarlo dove Xcode lo linka: **non inventare un percorso manuale**
> per `shared.framework`, deve passare da questo script perché Xcode fornisce le variabili
> d'ambiente (`CONFIGURATION`, `SDK_NAME`, `ARCHS`, `BUILT_PRODUCTS_DIR`, ecc.) che quel task
> Gradle legge da sé. Aggiungi anche le `settings:` necessarie perché il linker trovi il
> framework (`FRAMEWORK_SEARCH_PATHS` che punti alla cartella di output, tipicamente
> `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`) e
> `OTHER_LDFLAGS: -framework shared`.
> Configura due schemi/configurazioni Debug e Release. Non aggiungere firma (niente
> `CODE_SIGN_IDENTITY`/team): la CI builda senza firmare.
> Alla fine crea anche un piccolo `iosApp/README.md` che spiega: "questo progetto Xcode è
> generato da `project.yml` — non editare `iosApp.xcodeproj` a mano se esiste, va rigenerato con
> `xcodegen generate` eseguito dentro `iosApp/`".
> Non installare XcodeGen né generare il progetto: non è disponibile un Mac in questo ambiente,
> lo farà la CI (task separato). Verifica solo che il YAML sia sintatticamente valido.

### A2. Info.plist e asset minimi
**Dipendenze:** A1.
**Prompt:**
> Nel repository `Circolare+`, dentro `iosApp/iosApp/`, crea:
> 1. `Info.plist` minimale per un'app SwiftUI/Compose Multiplatform: `CFBundleName` "Circolare+",
>    `UILaunchScreen` vuoto (dizionario vuoto, per uno splash bianco di sistema senza storyboard),
>    `UISupportedInterfaceOrientations` con almeno portrait, `LSRequiresIPhoneOS` true. Non
>    aggiungere ancora permessi (notifiche, ecc.): verranno aggiunti nella Fase E quando servirà
>    davvero.
> 2. Un `Assets.xcassets` con solo `AppIcon.appiconset/Contents.json` (senza immagini reali — solo
>    lo scheletro JSON con gli slot delle dimensioni, vuoti) così il progetto compila senza
>    un'icona vera per ora.
> Aggiorna `iosApp/project.yml` (creato nel task A1) se necessario per referenziare questi due
> percorsi (`info: path: iosApp/Info.plist` e la cartella assets nelle sources del target).
> Non serve un Mac per questo task: sono solo file di testo/JSON.

### A3. Entry point Swift (`iOSApp.swift`)
**Dipendenze:** A1, A2.
**Prompt:**
> Leggi il file esistente `iosApp/iosApp/iOSApp.swift` nel repository `Circolare+` e
> `shared/src/iosMain/kotlin/circolareplus/ui/MainViewController.kt` (espone una funzione Kotlin
> `MainViewController()` che ritorna uno `UIViewController`, esportata nel framework `shared`
> come `MainViewControllerKt.MainViewController()` — il nome esatto della classe Objective-C
> generata segue la convenzione `<NomeFileSenzaEstensione>Kt`, verificalo cercando altri esempi
> nel repo o nella documentazione Kotlin/Native se hai dubbi).
> Riscrivi `iOSApp.swift` come `App` SwiftUI standard che nel suo `body` mostra una
> `UIViewControllerRepresentable` che avvolge `MainViewControllerKt.MainViewController()`
> a schermo intero (ignora safe area dove serve, dato che Compose la gestisce già da sé).
> Aggiungi `import shared` in cima al file (è il nome del modulo del framework KMP, vedi
> `baseName = "shared"` in `shared/build.gradle.kts`).
> Non aggiungere ancora nessun AppDelegate né logica di notifiche push: solo l'avvio della UI.
> Non è verificabile compilando in questo ambiente (nessun Mac) — verrà controllato dalla CI
> creata in Fase B.

---

## Fase B — CI GitHub Actions (build-only, senza firma)

### B1. Workflow `.github/workflows/ios-build.yml`
**Dipendenze:** A1, A2, A3.
**Prompt:**
> Nel repository `Circolare+`, crea `.github/workflows/ios-build.yml`: un workflow GitHub Actions
> che gira su `macos-15` (o la label macOS più recente disponibile per runner ospitati, verifica
> quale sia quella corrente), con trigger su `push` e `pull_request` quando toccano `shared/**`,
> `iosApp/**` o il workflow stesso. Passi:
> 1. `actions/checkout@v4`.
> 2. Setup JDK (17, `actions/setup-java@v4`, distribuzione `temurin`) — necessario per Gradle.
> 3. Cache Gradle (`actions/cache@v4` sulla home `~/.gradle/caches` e `~/.gradle/wrapper`, chiave
>    su hash di `gradle/wrapper/gradle-wrapper.properties` e `**/*.gradle.kts`).
> 4. `brew install xcodegen`.
> 5. `cd iosApp && xcodegen generate` per generare `iosApp.xcodeproj` dal `project.yml`.
> 6. Build **senza firma**: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
>    -destination 'generic/platform=iOS Simulator' -configuration Debug
>    CODE_SIGNING_ALLOWED=NO build`. Usa il nome scheme effettivo generato da XcodeGen (di
>    norma coincide col nome del target, verificalo nel `project.yml` del task A1).
> Non serve firma/provisioning: l'obiettivo è solo sapere se il codice Swift+Kotlin compila, non
> produrre un IPA installabile (quello richiederà un Apple Developer account, non ancora attivo).
> Non eseguire questo workflow tu stesso: verrà lanciato da GitHub al prossimo push. Segnala
> solo se noti errori evidenti di sintassi YAML.

---

## Fase C — Bridge Apple Intelligence (Kotlin ↔ Swift)

Contesto per tutti i task di questa fase: `FoundationModels` (il framework di Apple Intelligence)
è un'API **solo Swift** (usa macro ed enum con valori associati non rappresentabili in
Objective-C), quindi non è raggiungibile da `cinterop` diretto. Il pattern standard KMP per
questi casi è: Kotlin dichiara un'interfaccia, un oggetto Swift compilato nel target
`iosApp` la implementa (il framework `shared` la esporta come protocollo Objective-C-compatibile
quando è marcata `expect`/interfaccia pubblica), e l'app Swift la inietta in un holder Kotlin
all'avvio, prima di creare la UI.

### C1. Interfaccia Kotlin del bridge
**Dipendenze:** nessuna (può partire in parallelo alla Fase A/B).
**Prompt:**
> Nel repository `Circolare+`, crea il file
> `shared/src/iosMain/kotlin/circolareplus/ai/AppleIntelligenceBridge.kt` con:
> ```kotlin
> package circolareplus.ai
>
> /**
>  * Implementata in Swift (AppleIntelligenceEngine.swift) sopra FoundationModels.
>  * Iniettata da iOSApp.swift in AppleIntelligenceBridgeHolder.bridge prima che qualunque
>  * schermata possa chiamare isOnDeviceAiAvailable()/LocalLlm().
>  */
> interface AppleIntelligenceBridge {
>     /** true se il dispositivo supporta Apple Intelligence, è attivo e il modello è pronto. */
>     fun isAvailable(): Boolean
>
>     /** Motivo leggibile se isAvailable() è false: dispositivo non supportato, Apple
>      * Intelligence disattivato nelle Impostazioni di sistema, o lingua non supportata. Null
>      * se isAvailable() è true. */
>     fun unavailableReason(): String?
>
>     /**
>      * Genera una risposta in streaming, fermandosi appena stopWhen(testo accumulato) ritorna
>      * true, o dopo timeoutMillis. Lancia un'eccezione (che il chiamante Kotlin già gestisce,
>      * vedi LocalAiClassifier.kt) se la generazione fallisce.
>      */
>     suspend fun generate(
>         systemPrompt: String,
>         userPrompt: String,
>         timeoutMillis: Long,
>         stopWhen: (String) -> Boolean
>     ): String
> }
>
> /** Un solo bridge per tutta l'app, iniettato da Swift all'avvio. Null finché non è stato
>  * iniettato (non dovrebbe mai succedere in pratica: iOSApp.swift lo imposta prima di creare
>  * MainViewController()). */
> object AppleIntelligenceBridgeHolder {
>     var bridge: AppleIntelligenceBridge? = null
> }
> ```
> Non modificare altri file in questo task. Non è verificabile senza compilare per iOS: verrà
> controllato dalla CI dopo il task C2 (che lo usa davvero).

### C2. Riscrivere `LocalAiPlatform.ios.kt` sopra il bridge
**Dipendenze:** C1.
**Prompt:**
> Leggi `shared/src/commonMain/kotlin/circolareplus/ai/LocalAiPlatform.kt` (le `expect`
> dichiarate: `totalDeviceRamMb()`, `isOnDeviceAiAvailable()`, `onDeviceAiUnavailableReason()`,
> `expect class LocalModelStore`, `expect class LocalLlm` — leggi le firme e i commenti di ogni
> metodo, sono la specifica esatta da rispettare) e l'attuale
> `shared/src/iosMain/kotlin/circolareplus/ai/LocalAiPlatform.ios.kt` (oggi tutto stub) nel
> repository `Circolare+`. Leggi anche `shared/src/iosMain/kotlin/circolareplus/ai/AppleIntelligenceBridge.kt`
> (creato nel task C1).
> Riscrivi `LocalAiPlatform.ios.kt` così:
> - `totalDeviceRamMb()`: usa `platform.Foundation.NSProcessInfo.processInfo.physicalMemory`
>   (in byte) diviso 1_000_000, cast a `Int`.
> - `isOnDeviceAiAvailable()`: `AppleIntelligenceBridgeHolder.bridge?.isAvailable() ?: false`.
> - `onDeviceAiUnavailableReason()`: se il bridge è null, ritorna
>   `"AI locale non ancora inizializzata."`; altrimenti
>   `AppleIntelligenceBridgeHolder.bridge?.unavailableReason()`.
> - `LocalModelStore`: qui NON c'è nessun file da scaricare (il modello è quello di sistema).
>   Rappresenta la disponibilità di Apple Intelligence come se fosse "il modello installato":
>   - `isInstalled(model)`: ritorna `isOnDeviceAiAvailable()` (ignora il parametro `model`,
>     su iOS il catalogo ha una sola voce, vedi task C3).
>   - `installedPath(model)`: ritorna la costante `"apple-intelligence"` se `isInstalled(model)`,
>     altrimenti `null`.
>   - `partialBytes`, `freeSpaceBytes`, `orphanBytes`: ritornano `0L` (non esiste download
>     parziale né spazio occupato da gestire).
>   - `delete(model)`: ritorna sempre `false` (non c'è nulla da cancellare: è il modello di
>     sistema, non un file dell'app).
>   - `deleteOrphans()`: ritorna `0L`.
>   - `installedModels()`: ritorna la lista con l'unico modello del catalogo iOS se
>     `isOnDeviceAiAvailable()`, altrimenti lista vuota.
>   - `download(model, onProgress)`: NON scarica nulla in rete. Chiama subito
>     `onProgress(1, 1)` e ritorna `ModelDownloadState.Installed("apple-intelligence")` se
>     `isOnDeviceAiAvailable()`, altrimenti `ModelDownloadState.Failed(onDeviceAiUnavailableReason()
>     ?: "Apple Intelligence non disponibile su questo dispositivo.")`. Questo fa sì che il
>     pulsante "Scarica" esistente nella schermata Impostazioni (pensato per Android) funzioni
>     anche su iOS come un "Attiva" istantaneo, senza dover toccare quella UI in questo task.
> - `LocalLlm`:
>   - `backendLabel()`: ritorna `"Apple Intelligence (Neural Engine)"`.
>   - `generate(modelPath, preferGpu, maxOutputTokens, systemPrompt, userPrompt, timeoutMillis,
>     stopWhen)`: ignora `modelPath`, `preferGpu` e `maxOutputTokens` (Apple Intelligence non ha
>     un file da caricare né un parametro di accelerazione da scegliere; il tetto di lunghezza
>     lo decide il modello di sistema). Delega al bridge:
>     `AppleIntelligenceBridgeHolder.bridge?.generate(systemPrompt, userPrompt, timeoutMillis,
>     stopWhen) ?: throw IllegalStateException("Apple Intelligence non disponibile.")`.
>   - `unload()`: corpo vuoto con un commento che spiega perché (il modello di sistema non si
>     carica/scarica dall'app: lo gestisce iOS).
> Non toccare `shared/src/commonMain/kotlin/circolareplus/ai/LocalAiPlatform.kt`: le firme
> `expect` restano identiche, cambia solo l'implementazione iOS.

### C3. Catalogo modelli iOS (solo Apple Intelligence)
**Dipendenze:** nessuna (indipendente da C1/C2, ma la CI lo verificherà insieme).
**Prompt:**
> Leggi `shared/src/commonMain/kotlin/circolareplus/ai/LocalAiModels.kt` nel repository
> `Circolare+`: oggi `LocalAiCatalog.all` è una lista fissa di 5 modelli (Qwen/Gemma) pensata per
> Android. Va resa specifica per piattaforma perché su iOS l'unico "modello" è Apple Intelligence
> (nessun file da scaricare, nessuna scelta fra taglie).
> Modifica `LocalAiCatalog` in questo file: sostituisci la property `val all: List<LocalAiModel> =
> listOf(QWEN35_08B, ...)` con `expect val all: List<LocalAiModel>` (mantieni tutte le costanti
> `GEMMA4_E2B` ecc. dove sono, restano usabili solo dall'actual Android). Nota: un `object` con
> una property `expect` dentro richiede che anche l'`object` stesso sia dichiarato `expect`/`actual`
> — verifica la sintassi corretta in Kotlin 2.4 (la versione di questo progetto, vedi
> `gradle/libs.versions.toml`) prima di scegliere fra "expect val dentro object normale" (non
> supportato) ed "expect object LocalAiCatalog" con due `actual object` (Android/iOS). Scegli
> l'approccio che compila, motivandolo con un commento breve.
> Poi crea:
> - `shared/src/androidMain/kotlin/circolareplus/ai/LocalAiModels.android.kt` con l'`actual` che
>   restituisce l'elenco attuale dei 5 modelli (spostalo qui pari pari da dov'era).
> - `shared/src/iosMain/kotlin/circolareplus/ai/LocalAiModels.ios.kt` con l'`actual` che dichiara
>   un solo `LocalAiModel`:
>   ```kotlin
>   val APPLE_INTELLIGENCE = LocalAiModel(
>       id = "apple-intelligence",
>       displayName = "Apple Intelligence",
>       fileName = "apple-intelligence",
>       downloadUrl = "",
>       approxSizeBytes = 0L,
>       tier = DeviceTier.UNKNOWN,
>       recommendedRamMb = 0,
>       preferGpu = false,
>       supportsActions = true,
>       maxOutputTokens = 900,
>       description = "Il modello di sistema di Apple Intelligence. Nessun download: gira già " +
>           "sul telefono se il dispositivo lo supporta (iPhone 15 Pro o successivo, iOS 26+)."
>   )
>   ```
>   e ritorna `listOf(APPLE_INTELLIGENCE)`.
> Aggiusta ogni altro riferimento nel modulo `:shared` che si rompe per via del cambio da
> property normale a `expect`/`actual` (cercali con una ricerca testuale di `LocalAiCatalog.all`
> nel repo). Verifica che `LocalAiCatalog.recommendedFor`, `.byId`, `.selectableFor`,
> `.knownFileNames` — che oggi leggono `all` — continuino a compilare senza modifiche di firma.

### C4. `AppleIntelligenceEngine.swift`
**Dipendenze:** C1 (serve il protocollo Kotlin esportato).
**Prompt:**
> Nel repository `Circolare+`, crea `iosApp/iosApp/AppleIntelligenceEngine.swift`. Deve
> implementare in Swift il protocollo Objective-C esportato dal framework KMP `shared` per
> l'interfaccia Kotlin `circolareplus.ai.AppleIntelligenceBridge` (creata nel task C1 in
> `shared/src/iosMain/kotlin/circolareplus/ai/AppleIntelligenceBridge.kt`). Il nome esatto del
> protocollo Swift generato segue la convenzione Kotlin/Native
> `<PackagePrefix><NomeInterfaccia>` (tipicamente `AiAppleIntelligenceBridge` o simile a seconda
> del `packagePrefix` configurato — se non è configurato nessun prefisso in
> `shared/build.gradle.kts`, il nome è semplicemente `AppleIntelligenceBridge`): non indovinare,
> verificalo cercando come questo progetto esporta altre interfacce Kotlin a Swift altrove nel
> repo, oppure documentati sulla convenzione ufficiale Kotlin/Native "Objective-C/Swift
> interop" prima di scrivere il nome della classe.
> Implementazione richiesta, usando `import FoundationModels` (iOS 26+):
> - `isAvailable() -> Bool`: `SystemLanguageModel.default.availability == .available`.
> - `unavailableReason() -> String?`: quando `availability` è `.unavailable(let reason)`, mappa
>   `reason` (i casi sono tipicamente "device not eligible", "Apple Intelligence not enabled",
>   "model not ready") in una frase in italiano comprensibile per un utente non tecnico (es.
>   "Questo iPhone non supporta Apple Intelligence.", "Attiva Apple Intelligence nelle
>   Impostazioni di sistema per usare l'AI locale.", "Il modello si sta preparando, riprova fra
>   poco."). Ritorna `nil` se `isAvailable()` è `true`.
> - `generate(systemPrompt:userPrompt:timeoutMillis:stopWhen:) async throws -> String`: crea una
>   `LanguageModelSession(instructions: systemPrompt)`, chiama
>   `session.streamResponse(to: userPrompt)`, itera lo stream accumulando il testo, e ad ogni
>   chunk chiama la closure Kotlin `stopWhen(testoAccumulato)` — interrompendo il ciclo
>   (`break`) appena ritorna `true`. Avvolgi tutto in un `Task` con timeout: usa
>   `withThrowingTaskGroup` o `Task.sleep` in una race fra la generazione e uno scadenzario di
>   `timeoutMillis` millisecondi, lanciando un errore descrittivo se scade prima che la
>   generazione finisca o che `stopWhen` diventi vero. Il metodo Kotlin `suspend` diventa in
>   Swift una funzione `async throws` con una closure di completamento generata dall'interop, o
>   — più semplice — implementa qui il protocollo con la firma `async throws` che Xcode/Kotlin
>   si aspettano: se il compilatore Kotlin/Native genera invece una firma con completion handler
>   invece di `async`, adatta di conseguenza (dipende dalla configurazione
>   `-Xbinary=bundleId` / coroutine interop del progetto: verificalo, non assumerlo).
> Non è verificabile senza Mac in questo ambiente: verrà controllato dalla prossima run della CI
> (task B1) dopo aver completato anche il task C5.

### C5. Iniezione del bridge in `iOSApp.swift`
**Dipendenze:** A3, C4.
**Prompt:**
> Nel repository `Circolare+`, modifica `iosApp/iosApp/iOSApp.swift` (creato nel task A3):
> prima di costruire la view che mostra `MainViewControllerKt.MainViewController()`, imposta
> `AppleIntelligenceBridgeHolder.shared.bridge = AppleIntelligenceEngine()` (o il nome esatto
> generato per l'oggetto/holder Kotlin esportato — verifica in
> `shared/src/iosMain/kotlin/circolareplus/ai/AppleIntelligenceBridge.kt`, creato nel task C1,
> come Kotlin/Native espone un `object` Kotlin a Swift: di norma `NomeOggetto.shared`).
> Fallo nell'inizializzatore dell'`App` SwiftUI (`init()`), così è garantito che accada prima
> che qualunque schermata Compose possa chiamare `isOnDeviceAiAvailable()`.

---

## Fase D — Copy Impostazioni per iOS

### D1. Adattare la schermata Impostazioni AI al caso iOS
**Dipendenze:** C2, C3.
**Prompt:**
> Nel repository `Circolare+`, leggi `shared/src/commonMain/kotlin/circolareplus/ui/MainAppShell.kt`
> intorno alla riga 1247 (cerca `LocalAiCatalog.selectableFor` per trovare il punto esatto: è la
> sezione Impostazioni che mostra l'elenco dei modelli scaricabili con barra di avanzamento,
> pensata per Android dove ci sono 5 modelli fra cui scegliere). Su iOS il catalogo
> (task C3) ha una sola voce ("Apple Intelligence", sempre "installata" se il dispositivo la
> supporta, vedi task C2) — mostrare comunque l'elenco con pulsante "Scarica"/barra di
> avanzamento funzionerebbe ma sarebbe fuorviante (non c'è nulla da scaricare davvero).
> Adatta quella sezione perché quando la piattaforma è iOS (usa una `expect fun` esistente o
> creane una minima tipo `expect fun isIos(): Boolean` in `circolareplus.platform` se non esiste
> già nulla di equivalente nel repo — cercalo prima) mostri invece un semplice stato: "Apple
> Intelligence: disponibile" (verde, con un pulsante "Prova il modello" che chiama
> `testConfiguration()` come già accade per gli altri provider) oppure "Apple Intelligence: non
> disponibile — <motivo da onDeviceAiUnavailableReason()>" (senza pulsante scarica/elimina, che
> su iOS non hanno senso). Non toccare il ramo Android della UI, deve restare identico a oggi.
> Verifica anche `ProfileScreen.kt`/le Impostazioni per altri punti che assumano un elenco
> modelli multiplo (cerca `LocalAiModel` nell'intero modulo `:shared` con una ricerca testuale)
> e applica lo stesso adattamento se ne trovi.

---

## Fase E — Push notifications iOS (bassa priorità, differibile)

Non bloccante per l'AI locale. Farla solo dopo che le Fasi A-D sono verdi in CI, e comunque il
risultato resterà inerte finché non verrà aggiunto `GoogleService-Info.plist` (non disponibile
ora — vedi `FIREBASE_SETUP.md`, sezione 4).

### E1. AppDelegate Swift per la registrazione APNs
**Dipendenze:** A3.
**Prompt:**
> Leggi `FIREBASE_SETUP.md` (sezione 4, "Solo iOS: registrazione token") e
> `shared/src/iosMain/kotlin/circolareplus/push/PushTokenProvider.ios.kt` nel repository
> `Circolare+`. Crea `iosApp/iosApp/AppDelegate.swift` con un `UIApplicationDelegate` che
> implementa `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` e
> `application(_:didFailToRegisterForRemoteNotificationsWithError:)`. NON aggiungere ancora
> `import FirebaseMessaging` né chiamate a `Messaging.messaging()`: il pacchetto Firebase iOS SDK
> non è stato ancora aggiunto al progetto (richiede Swift Package Manager configurato in
> `iosApp/project.yml`, task che manca ancora `GoogleService-Info.plist`). Per ora limitati a:
> salvare il device token APNs grezzo (convertito in stringa esadecimale) in una property
> statica leggibile, e richiedere il permesso di notifica con `UNUserNotificationCenter` +
> `UIApplication.shared.registerForRemoteNotifications()` dentro
> `application(_:didFinishLaunchingWithOptions:)`. Collega l'AppDelegate all'App SwiftUI in
> `iOSApp.swift` con `@UIApplicationDelegateAdaptor`. Lascia un commento `// TODO Firebase:` nel
> punto esatto dove, una volta aggiunto il plist e il pacchetto SPM, andrà passato il token ad
> `Messaging.messaging().apnsToken` — non implementarlo ora.

---

## Fase F — Verifica e chiusura

### F1. Loop di verifica CI
**Dipendenze:** tutte le precedenti.
**Prompt (da eseguire tu stesso, non un agente Haiku — richiede di leggere log CI e decidere
i fix, è lavoro di debugging non meccanico):**
> Dopo aver eseguito i task A-E, fai push, apri l'ultima run di `.github/workflows/ios-build.yml`
> su GitHub Actions, leggi gli errori di build (se ce ne sono) e correggili, ripetendo finché la
> run non è verde. I punti più a rischio di errore reale, da controllare per primi: il nome
> esatto del protocollo Swift generato per `AppleIntelligenceBridge` (task C4), la firma
> `async throws` vs completion-handler per il metodo `suspend fun generate` (task C4), e il
> refactor di `LocalAiCatalog` da property a `expect`/`actual` object (task C3).
