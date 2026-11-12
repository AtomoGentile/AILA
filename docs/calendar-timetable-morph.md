# Calendario ↔ Orario — Interactive Calendar Morphing

Base: `cf11468d12b555b90c86067428b3d11f56e31f5e` (beta 41). Candidata: `v2.0.0-beta.42`.

## Comportamento

Il bordo inferiore del mese contiene un handle con area di tocco di 48 dp. Trascinando verso il basso si riconfigura la stessa griglia: le celle non selezionate si comprimono e perdono opacità, mentre la cella selezionata si allarga e diventa l'intestazione della data. La superficie del mese perde progressivamente riempimento e arrotondamento. Sotto emerge una timeline con materie e ore effettivamente presenti in `SchoolTimetable.lessons(classe, data)`.

Il gesto è riconosciuto soltanto sull'handle. La distanza di riferimento è 220 dp; al rilascio si usa la soglia del 50% oppure una velocità di almeno 2,5 avanzamenti/secondo (550 dp/s). Il trascinamento aggiorna sincronicamente lo stato; una sola `Animatable` conclude o annulla il movimento. Un nuovo trascinamento interrompe la conclusione precedente. Un evento di cancellazione torna alla modalità da cui era iniziato il gesto. Il feedback aptico leggero accompagna l'attraversamento della soglia.

L'handle accetta anche clic, tastiera e azioni dei lettori di schermo. Nella modalità Orario, trascinamento verso l'alto, clic sull'handle e navigazione indietro richiamano lo stesso percorso inverso. Le frecce passano da mesi a date consecutive, anche attraverso mesi e anni; Oggi aggiorna la stessa data condivisa. Data, filtro e modalità stabile usano il salvataggio Compose. Una ricreazione durante un trascinamento ripristina la modalità stabile, senza lasciare una trasformazione incompleta.

La bottom navigation rimane gestita dal contenitore dell'app. Non ci sono nuove voci, card d'accesso o fogli modali. Le preferenze di movimento ridotto disabilitano la riconfigurazione spaziale e la molla finale; l'handle continua a dare riscontro al trascinamento. Le superfici seguono i colori Material/Glass e chiaro/scuro già disponibili. La modalità Orario usa `AppTheme.AccentCyan`, senza cambiare la preferenza globale dei colori del calendario.

## Componenti

| File | Intervento |
| --- | --- |
| `shared/src/commonMain/kotlin/circolareplus/ui/screens/CalendarScreen.kt` | Elimina la card e il foglio, condivide la data, integra il morph e riconfigura direttamente la cella selezionata con un `Layout` personalizzato. |
| `shared/src/commonMain/kotlin/circolareplus/ui/screens/CalendarMorphState.kt` | Stato salvabile, trascinamento immediato, destinazione in base a distanza/velocità, annullamento e conclusione reversibile. |
| `shared/src/commonMain/kotlin/circolareplus/ui/screens/CalendarMorphComponents.kt` | Handle accessibile, timeline, superficie comune, esclusione dei controlli invisibili e layout tablet a larghezza progressiva. |
| `shared/src/commonMain/kotlin/circolareplus/ui/screens/TimetableSheet.kt` | Rimosso: non è più una destinazione separata. |
| `shared/src/commonTest/kotlin/circolareplus/ui/screens/CalendarMorphStateTest.kt` | Soglie, velocità, cancellazione, ripetizioni, limiti e ripristino della modalità. |
| `shared/src/androidUnitTest/kotlin/circolareplus/ui/screens/CalendarUiRegressionTest.kt` | Aggiorna i test Orario al nuovo flusso e conserva i test del form eventi e del composer. |
| `docs/calendar-timetable-morph.md` | Architettura, file, verifiche e limiti della candidata. |
| `release-notes/v2.0.0-beta.42.md` | Novità e stato delle verifiche della beta 42. |

## Dati e limiti

Si riutilizza lo schema settimanale ufficiale già presente, limitato all'anno scolastico disponibile. Non si inventano orari dell'orologio, docenti, aule, festività o sostituzioni. Domeniche, date fuori anno e classi assenti hanno stati vuoti espliciti. La fonte ufficiale resta raggiungibile dalla timeline.

Android, iOS e PWA condividono l'implementazione Compose; gesto indietro, movimento ridotto e aptica passano dalle API di piattaforma già presenti. La compilazione iOS richiede macOS/Xcode e non costituisce una verifica eseguibile in questo ambiente Linux. I 60 FPS e la qualità dell'aptica richiedono una prova su dispositivo: non sono deducibili dai test Robolectric.

## Verifica

Comandi previsti:

```sh
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug :shared:lintDebug
./gradlew :shared:compileKotlinWasmJs
python -m unittest discover -s scripts/tests
```

I test UI coprono trascinamento completo e inverso, annullamento prima della soglia, cancellazione del puntatore, movimento prima del rilascio, rilascio rapido, data conservata, cambio giorno e mese, domenica senza lezioni, ripetizioni, scorrimento eventi, indietro Android, riapertura degli eventi, tablet, tema chiaro e movimento ridotto. Gli screenshot dei test includono uno stato intermedio, Orario scuro, Orario chiaro e tablet.

Verifiche completate il 12 novembre 2026:

| Controllo | Risultato |
| --- | --- |
| `:shared:testDebugUnitTest` | 346 test passati; zero falliti, errori o saltati. Include 14 test UI e 4 test dello stato di morphing. |
| `:androidApp:assembleDebug` | Riuscito; metadati APK verificati: `2.0.0-beta.42`. |
| `:shared:compileKotlinWasmJs` | Riuscito sul codice finale. Non equivale a una prova interattiva nel browser o al bundle web di produzione. |
| Test Python degli script di release | 5 test passati; validato anche il tag `v2.0.0-beta.42`. |
| `git diff --check` | Riuscito. |
| Screenshot Robolectric | Controllati stato intermedio, Orario, ritorno al mese, chiaro/scuro, tablet e Glass. |
| `:shared:lintDebug` | Completato con 1 errore e 35 avvisi. L'errore è `SpecifyForegroundServiceType` nel worker di download AI: il servizio `dataSync` è dichiarato nel manifest del modulo app, non in quello della libreria. Nessuna segnalazione sui file del morphing. Il log include inoltre diagnostiche sul lettore Kotlin 2.2 e sui metadati 2.4. |
| `:androidApp:lintDebug` | Riuscito. Il manifest unito dell'APK contiene `SystemForegroundService` con `foregroundServiceType=dataSync`; confermata la dichiarazione che il controllo isolato della libreria non vede. |
| iOS | Non compilato: ambiente Linux. |
| Fluidità / aptica | Da confermare sul dispositivo reale. Nessuna misura FPS eseguita. |

L'APK locale è soltanto di verifica: usa il placeholder Firebase previsto dal workflow e la firma debug locale, con `versionCode = 1`. Non è l'APK distribuibile per aggiornare le installazioni della beta precedente. La pubblicazione deve passare dal workflow esistente, che dispone della firma stabile e della configurazione Firebase del progetto.

## Preparazione beta

Il progetto ricava la versione Android dal tag, senza un contatore da modificare nel codice. La candidatura usa `v2.0.0-beta.42`, con note in `release-notes/v2.0.0-beta.42.md`. La build distribuibile deve utilizzare la firma stabile del workflow Android esistente, per aggiornare le installazioni precedenti senza perdita di dati. Non occorrono migrazioni di database o nuove dipendenze.
