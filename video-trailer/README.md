# Trailer e intro di AILA

Trailer "hype" di circa 2 minuti (2:04, 1920×1080, 30 fps) da proiettare in classe per invogliare a installare
l'app. Come `video-presentazione/` è una pagina HTML animata esportata fotogramma per fotogramma, con musica
sintetizzata in codice, ma è più veloce: 128 BPM, un taglio su ogni battuta, due drop.

| File | Cosa contiene |
|---|---|
| `timeline.js` | Tempo (128 BPM, una battuta = 1,875 s) e inizio di ogni sezione in battute |
| `index.html` + `video.js` | Le scene e i dispositivi. Aprendo `index.html` nel browser parte l'anteprima (barra in basso per scorrere; `#t=86` nell'URL per partire da un secondo preciso) |
| `music.js` | Colonna sonora (la minore, Am–F–C–G), sincronizzata con le scene e con gli effetti elencati in `video.js` |
| `render.js` | Esporta il video con Chromium headless (`node render.js snap 30 64` salva singoli fotogrammi in `snaps/`) |
| `mux.js` | Unisce video e musica in `AILA_trailer.mp4` |
| `common.js`, `common.css` | Parti condivise dai due video: logo, temi dell'app (Glass/Material, chiaro/scuro, accenti), dispositivi e le sei schermate |
| `synth.js` | Sintetizzatore condiviso dalle due colonne sonore (strumenti, riverbero, mastering) |
| `intro.html` + `intro.js`, `intro-timeline.js`, `intro-music.js` | L'intro (vedi sotto) |
| `build-icons.js`, `build-qr.js` | Rigenerano `icons.js` (icone Lucide) e `qr.js` (QR code della pagina Releases) |

Per rigenerare il trailer: `npm install`, poi `npm run build` (circa 25 minuti con 3 pagine in parallelo; si cambia con
`WORKERS=…`). Se cambi solo la musica: `npm run music && npm run mux`. Servono `ffmpeg` nel PATH e Chrome o
Chromium (percorso in `CHROME` se non è uno di quelli cercati da `render.js`).
Per l'intro: `npm run intro:build` (→ `AILA_intro.mp4`); `node render.js snap 20 --page=intro` per i fotogrammi singoli.

## Scaletta

| Tempo | Battute | Scena |
|---|---|---|
| 0:00 | 0–8 | Il caos: notifiche del registro e del gruppo classe, parole a tempo, "E se la tua classe avesse un superpotere?" |
| 0:15 | 8–12 | Drop: il logo esplode, "La tua scuola, sincronizzata." e i sei moduli |
| 0:22 | 12–36 | Sei funzioni, quattro battute ciascuna: Circolari, AILA Assistant, Calendario, Sondaggi interrogazioni, Mappa posti, Bacheca e sondaggi a classifica |
| 1:07 | 36–46 | "Un'app. Ogni schermo.": Android, iPhone, tablet e iPadOS, poi un'onda di luce parte da tutti insieme ("Tutta la classe, sempre aggiornata.") |
| 1:26 | 46–52 | Temi: Liquid Glass e Material, chiaro e scuro, i sei colori d'accento (come in Impostazioni > Aspetto) |
| 1:37 | 52–56 | Il muro delle 24 combinazioni (2 stili × chiaro/scuro × 6 colori) |
| 1:45 | 56–59 | L'AI può girare sul telefono, dati separati per classe, l'AI non riceve i dati personali |
| 1:51 | 59–66 | Finale con il QR code della pagina Releases per installarla |

Ogni funzione è mostrata su un dispositivo e in un tema diversi, così la varietà si vede già prima della
scena dedicata. I colori dei temi vengono da `shared/.../design/AppTheme.kt` e `AilaGlass.kt`.
I nomi, le circolari e le date sono inventati. La versione web (PWA) non viene citata.

## Intro

Lunga quanto il trailer (circa 2:03), con più energia: 144 BPM, cambi d'inquadratura ogni due o tre quarti,
strisce di luce sullo sfondo, inquadratura che pompa sulla cassa, bande cinema nell'apertura. I testi sono
frasi normali, come le direbbe uno di classe. Musica: cassa dritta e basso a sedicesimi nei momenti forti,
metà tempo sotto le funzioni, archi in ostinato, coro e colpi orchestrali.

| Tempo | Battute | Scena |
|---|---|---|
| 0:00 | 0–6 | Linea di luce, "Anno scolastico 2026/27", poi dettagli dell'app sempre più fitti |
| 0:10 | 6–9 | Drop: logo, "L'app per la vita di classe" |
| 0:15 | 9–45 | Le sei funzioni, sei battute ciascuna: numero a tutto schermo, dettaglio dello schermo, telefono, nome e una frase |
| 1:15 | 45–51 | "Va sul telefono e sul tablet, Android o Apple che sia", poi Android, iPhone, Tablet, iPadOS e tutti insieme |
| 1:25 | 51–57 | Temi: stile, tema e i sei colori, come in Impostazioni › Aspetto |
| 1:35 | 57–61 | Il muro delle 24 versioni |
| 1:42 | 61–64 | Privacy: l'AI legge solo le circolari, e può girare sul telefono |
| 1:47 | 64–74 | Finale: logo, QR code e piattaforme |
