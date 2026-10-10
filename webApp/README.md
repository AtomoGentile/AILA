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
| AI locale (modelli sul telefono, Apple Intelligence) | non c'è: resta l'analisi del server e Gemini con la chiave personale |
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

Workflow `.github/workflows/web-deploy.yml`: su ogni push a `main` che tocca `shared/` o `webApp/` compila
e pubblica su Cloudflare Pages; sulle pull request compila soltanto. Controlla che nessun file superi i
25 MiB del limite di Pages.
