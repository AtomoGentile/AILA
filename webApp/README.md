# AILA — PWA

Versione web installabile di AILA. Non è una seconda interfaccia: è **lo stesso codice Compose di
`shared/` compilato per il browser** (Kotlin/wasmJs, WebAssembly). Schermate, grafica e logica sono
quelle dell'app; questo modulo contiene solo il punto d'ingresso (`src/wasmJsMain/kotlin/main.kt`) e
i file statici (`src/wasmJsMain/resources/`: `index.html`, manifest, icone, `_headers`, pagina
`/sidestore`, libreria pdf.js).

- **Indirizzo**: https://aila-scuola.pages.dev (Cloudflare Pages, progetto `aila-scuola`).
- **Browser**: servono WasmGC e `wasm-unsafe-eval`: Safari/iOS/iPadOS 18.2+, Chrome/Edge 119+, Firefox 120+.
- **Stile**: come l'app, Liquid Glass su iPhone/iPad e Material su Android e computer (si riconosce
  dallo user agent, vedi `Platform.wasmJs.kt`).

## Cosa cambia rispetto all'app

Le parti di piattaforma stanno in `shared/src/wasmJsMain/` (gli `actual` delle `expect` di `commonMain`):

| Funzione | Sul web |
| --- | --- |
| Copia offline | IndexedDB, caricata in memoria all'avvio (`OfflineStore.wasmJs.kt`) |
| PDF (lettura e testo per l'AI) | pdf.js (`pdf/`, `ai/PdfTextExtractor.wasmJs.kt`) |
| PDF della mappa posti | disegnato come in Android, incollato in un PDF e scaricato o condiviso |
| Tasto indietro | voce sentinella nella cronologia del browser (`PlatformBackHandler.wasmJs.kt`) |
| AI locale (modelli sul telefono, Apple Intelligence) | non c'è: la chat gira sul servizio incluso di AILA (senza chiavi), l'analisi del server resta e Gemini usa la chiave personale |
| Notifiche e sincronizzazione in background | service worker e Web Push |

## Sviluppo

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21.0.12" ./gradlew :webApp:wasmJsBrowserDevelopmentExecutableDistribution
# cartella da servire: webApp/build/dist/wasmJs/developmentExecutable  (python -m http.server)
./gradlew :webApp:wasmJsBrowserDistribution   # produzione: webApp/build/dist/wasmJs/productionExecutable
```

Il Worker accetta il CORS da `localhost`, quindi la build locale parla anche con il server vero.
Per provare senza toccare dati reali si può far girare il Worker in locale (`npx wrangler dev`).

## Deploy

Workflow `.github/workflows/web-deploy.yml`: compila le pull request, i tag beta e le release; pubblica
su Cloudflare Pages solo i tag stabili `vX.Y.Z`. I push a `main` e i tag `vX.Y.Z-beta.N` non cambiano
il sito/PWA, così le beta restano fuori dalla distribuzione web. Il deploy manuale richiede il tag
da compilare e pubblica soltanto se è stabile. Il workflow controlla anche che nessun file superi i 25 MiB del limite
di Pages.

## Avvio e schermo blu

La lettura della cache offline precede la UI. Apertura e lettura di IndexedDB hanno ciascuna un
limite di 8 secondi: se il browser non risponde, l'app prosegue in memoria per quell'avvio, senza
cancellare i dati persistenti. I documenti gia' salvati tornano disponibili quando una successiva
apertura riesce a leggere il database.

Se dopo 20 secondi lo splash e' ancora presente, lo script `startup.js` mostra un pulsante
"Riprova". Non ricarica automaticamente e non cancella cache, sessione o documenti. Il timer
funziona anche se WebAssembly non si avvia, purche' il browser esegua ancora JavaScript.

Verifiche senza dipendenze: `node --test webApp/tests/*.test.cjs`. Su un dispositivo interessato
dal blocco, verificare anche versione iOS, primo avvio online, avvio offline e riapertura dopo un
aggiornamento. Il fallback non identifica da solo la causa di un errore del runtime.

## Provare la beta desktop su Windows senza distribuire la PWA

Apri la PR desktop su GitHub, scegli il controllo **PWA deploy** relativo all'ultimo
commit e scarica l'artifact **aila-pwa** dalla pagina della build riuscita (serve l'accesso
a GitHub per il download). Il nome del workflow comprende “deploy”, ma sulle PR compila
soltanto e non pubblica la PWA.

Estrai **tutto** lo ZIP, avvia `AVVIA-ANTEPRIMA.cmd` e lascia la finestra aperta.
Serve Python 3. Apri **http://localhost:8765** in Chrome o Edge aggiornato e accedi
con il tuo account. In alternativa, dalla cartella estratta:

```powershell
py -3 -m http.server 8765 --bind 127.0.0.1
```

Non aprire direttamente `index.html`. La sessione su localhost è separata dalla PWA
stabile, ma il backend e i dati della classe sono quelli reali. Per fermare il server
premi Ctrl+C. Le istruzioni sono incluse anche in `LEGGIMI-ANTEPRIMA.txt` nello ZIP.

Per provare ACA e Brief, abilita AI e ACA nelle impostazioni AI e il servizio opzionale
Brief (`NOW_NUDGE`). Il pulsante **Apri ACA** della sidebar apre l'assistente contestuale;
se ACA è disattivata porta alle sue impostazioni, senza cambiare i consensi. Il Brief
mantiene gli stessi criteri di visibilità e le stesse azioni della versione mobile.
