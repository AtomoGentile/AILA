# AILA — Novità: PWA e sezione Gita

Video di 1 minuto e 40 (1920×1080, 30 fps) che presenta le due novità di ottobre: la versione web installabile
e la sezione Gita. Lo stile è quello dei filmati di presentazione di un prodotto: fondo nero per la parte web e
chiarissimo per la Gita, titoli grandi, una cosa per volta, movimenti lenti, musica leggera.

Come gli altri video è una pagina HTML animata esportata fotogramma per fotogramma, con l'audio sintetizzato in
codice. Riusa le parti comuni di `../video-trailer/` (`common.js`, `common.css`, `synth.js`, il font Sora).

| File | Cosa contiene |
|---|---|
| `timeline.js` | Tempo (96 BPM, una battuta = 2,5 s) e inizio di ogni scena in battute |
| `index.html` + `video.js` | Stili e scene. Aprendo `index.html` nel browser parte l'anteprima (barra in basso per scorrere; `#t=52` nell'URL per partire da un secondo preciso) |
| `music.js` | Colonna sonora (re maggiore), sincronizzata con le scene e con gli effetti elencati in `video.js` |
| `render.js` | Esporta il video con Chromium headless (`node render.js snap 30 64` salva singoli fotogrammi in `snaps/`) |
| `mux.js` | Unisce video e musica in `AILA_novita.mp4` |
| `build-icons.js` | Rigenera `icons.js` (icone Lucide) |
| `fonts/` | Inter variabile (licenza SIL Open Font; con l'asse ottico: alle dimensioni grandi usa il taglio Display) |

Per rigenerarlo: `npm install`, poi `npm run build` (una decina di minuti con 3 pagine in parallelo; si cambia con
`WORKERS=…`). Se cambi solo la musica: `npm run music && npm run mux`. Servono `ffmpeg` nel PATH e Chrome o Chromium
(percorso in `CHROME` se non è uno di quelli cercati da `render.js`).
`ONLY=pad,drums,arp,bass,piano,fx` esporta solo alcune parti della musica e `LEVELS=1` stampa il volume per battuta.

## Scaletta

| Tempo | Battute | Scena |
|---|---|---|
| 0:00 | 0–3 | Il logo, "Due novità per AILA." |
| 0:07 | 3–5 | **AILA sul web**: "La stessa app. Ora nel browser." |
| 0:12 | 5–10 | Si scrive l'indirizzo in un browser e si apre la Home; poi la stessa schermata su iPhone (Liquid Glass), computer e Android (Material) |
| 0:25 | 10–15 | Installazione su iPhone: Condividi, "Aggiungi alla schermata Home", l'icona tra le altre, l'app a tutto schermo |
| 0:37 | 15–19 | Griglia riassuntiva: un solo codice, dispositivi e browser, offline, stili, notifiche, PDF, il link |
| 0:47 | 19–21 | **Sezione Gita** (il fondo diventa chiaro): "Tutta la gita. In un posto solo." |
| 0:52 | 21–27 | Dalla Home si tocca il pulmino: circolari, documenti e link; la ricerca filtra l'elenco; il Rappresentante carica un PDF |
| 1:07 | 27–31 | Scheda di un documento: storico delle versioni, poi "Segnala un errore" e lo stato della segnalazione |
| 1:17 | 31–36 | AILA Assistant dalla Gita: risponde citando le fonti e dice quando un'informazione manca |
| 1:30 | 36–40 | Finale: logo, nome e indirizzo |

## Note

- I testi delle schermate (titoli delle sezioni, voci dei fogli, avvisi, la frase "Non lo trovo nel materiale
  disponibile") sono quelli dell'app: `GitaScreen.kt`, `GitaSheets.kt`, `AssistantPrompt.kt`.
- La gita, i documenti, le date e i nomi sono inventati, gli stessi degli altri video (Praga, circolare n. 112).
  Si cambiano tutti insieme nell'oggetto `TRIP` in cima a `video.js`.
- Le affermazioni sulla versione web vengono da `webApp/README.md`.
- La schermata Home del telefono e il foglio di condivisione sono disegnati in modo generico, senza marchi di altri.
