# AILA — "Fig. 1"

Video di un minuto (1920×1080, 30 fps) che racconta AILA come una tavola tecnica animata: l'aula disegnata in 3D
con solidi scuri e contorni chiari, etichette monospaziate, didascalie numerate collegate a un punto della scena e,
sotto, tre righe di tasti (fasi, sistemi, luci) che si accendono mentre il video va avanti.

Come gli altri due video è una pagina HTML esportata fotogramma per fotogramma, con l'audio sintetizzato in codice.
Qui però la scena non è fatta di elementi HTML: è un canvas disegnato da un piccolo proiettore prospettico scritto
a mano dentro `figura.js`, senza librerie.

| File | Cosa contiene |
|---|---|
| `timeline.js` | I tempi di tutto il video, in secondi. Li leggono sia la pagina sia l'audio |
| `index.html` + `figura.js` | La scena, le scritte e i tasti. Aprendo `index.html` nel browser parte l'anteprima (barra in basso per scorrere; `#t=22` nell'URL per partire da un secondo preciso) |
| `audio.js` | Colonna sonora: un tappeto calmo che cambia accordo a ogni fase, più i suoni degli eventi |
| `render.js` | Esporta il video con Chromium headless (`node render.js snap 10.5 24.6` salva singoli fotogrammi in `snaps/`) |
| `mux.js` | Unisce video e audio in `AILA_figura.mp4` |

Per rigenerarlo: `npm install`, poi `npm run build` (circa un quarto d'ora con 2 pagine in parallelo; si cambia con
`WORKERS=…`). Se cambi solo l'audio: `npm run audio && npm run mux`. Servono `ffmpeg` nel PATH e Chrome o Chromium
(percorso in `CHROME` se non è uno di quelli cercati da `render.js`). Il font Sora viene letto da
`../video-trailer/fonts/`.

## Scaletta

| Tempo | Tasto | Cosa succede |
|---|---|---|
| 0:00 | — | Si traccia la cornice, poi l'aula si disegna pezzo per pezzo e si accendono le finestre |
| 0:05 | 1 Circolari | Arrivano cinque circolari, la scintilla le legge e a ognuna assegna la categoria; parte l'onda delle notifiche e si accendono i telefoni sui banchi |
| 0:12 | 2 Assistant | Da un banco parte una domanda, la scintilla consulta le due fonti (circolare n. 112 e calendario) e risponde |
| 0:20 | 3 Calendario | La lavagna diventa il calendario di ottobre 2026; la scadenza della circolare n. 112 vola sul 23 |
| 0:27 | 4 Interrogazioni | Controcampo: nove posti ricevono la loro data, tre per ognuna |
| 0:35 | 5 Mappa posti | Dall'alto: le 24 pedine passano dalla proposta A alla B alla C |
| 0:42 | 6 Bacheca | Sulla bacheca arriva una proposta anonima e i voti a favore salgono da 12 a 31 |
| 0:50 | 7 Dall'alto | Vista dall'alto: scintilla e banchi si collegano come una rete, cioè il logo; poi il logo vero e il nome |

I testi delle didascalie vengono dal `README.md` principale. Nomi, circolari, date e voti sono inventati, gli stessi
del trailer. Come lì, la versione web non viene citata.

## Come è fatta la scena

- **Misure in metri.** L'aula è larga 9,2 m e profonda 9,3 m; la lavagna è sulla parete di fondo (z = 0) e la
  classe guarda verso di lei. Quattro file da tre banchi doppi: 24 posti.
- **Oggetti.** Tutto è fatto di parallelepipedi (`box`) e segmenti. Di ogni parallelepipedo si disegnano solo le
  facce rivolte verso la camera, più scure o più chiare a seconda del lato, e sopra i loro spigoli.
- **Ordine.** Prima pavimento e pareti, poi gli oggetti dal più lontano al più vicino, infine onde, traiettorie e
  didascalie. Le pareti si disegnano solo se la camera è dal lato interno: da fuori resta lo spaccato.
- **Camera.** `CAM` elenca le inquadrature: ognuna parte a un certo secondo, ci arriva in `d` secondi dalla
  precedente e poi scivola piano. `cut: 1` fa uno stacco netto (serve per il controcampo della fase 4).
- **Scritte.** Il numero grande, le didascalie e i tasti sono elementi HTML sopra il canvas; i numeri sul
  calendario e sulle proposte sono invece disegnati nel canvas, appoggiati alle pareti (`text3D`).

Per cambiare un testo o la posizione di una didascalia si tocca `INFO` in `figura.js`; per i tasti, `KEYS`.
