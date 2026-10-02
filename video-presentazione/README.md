# Video di presentazione di AILA

Video di circa 2 minuti e mezzo (1920×1080, 30 fps) da proiettare in classe. È una pagina HTML animata
esportata fotogramma per fotogramma, con musica sintetizzata in codice.

| File | Cosa contiene |
|---|---|
| `timeline.js` | Durata di ogni scena nel video finale (le scene sono scritte su un tempo interno più corto e vengono distese) |
| `index.html` + `video.js` | Le scene. Aprendo `index.html` nel browser parte l'anteprima (barra in basso per scorrere; `#t=58` nell'URL per partire da un secondo preciso) |
| `music.js` | Colonna sonora (120 BPM), sincronizzata con le scene e con gli effetti elencati in `video.js` |
| `render.js` | Esporta il video con Chrome headless (`node render.js snap 30 64` salva singoli fotogrammi in `snaps/`) |
| `mux.js` | Unisce video e musica in `AILA_presentazione.mp4` |

Per rigenerare tutto dopo una modifica: `npm run build` (circa mezz'ora). Se cambi solo la musica:
`npm run music && npm run mux`.

Scaletta: 0:00 il caos (circolari e chat) · 0:16 logo · 0:22 Circolari e AI · 0:44 AILA Assistant ·
1:00 Calendario · 1:10 Sondaggi interrogazioni (budget di voti, bonus sacrificio) · 1:32 Mappa posti ·
1:56 Bacheca e sondaggi a ordinamento · 2:10 piattaforme e privacy · 2:18 finale.

I nomi dei compagni, le circolari e le date sono inventati. Gli algoritmi mostrano solo i risultati,
senza punteggi (tranne i punti del sondaggio a ordinamento). La versione web (PWA) non viene citata.
