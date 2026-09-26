# AILA — PWA

Versione web installabile di AILA per chi usa iPhone o iPad senza SideStore. Affianca l'app nativa
(Android e iOS restano identici): stesso login, stesso JWT, stesse API del Worker.

- **Stack**: Vite + TypeScript + Preact (UI) + Workbox (service worker) + pdf.js (PDF e testo per l'AI).
- **Indirizzo**: https://aila-scuola.pages.dev (Cloudflare Pages, progetto `aila-scuola`).
- **Tipi condivisi**: `backend/src/contracts.ts`, importato con `import type` dall'alias `@worker/contracts`.
- **Logica portata dal Kotlin** (`src/ai/`): prompt e lettura della risposta di Gemini, ripiego euristico,
  pulizia/taglio del testo PDF, riparazione del JSON, doppioni del calendario. I test in `test/` sono gli
  stessi casi dei test Kotlin: se cambi la logica nell'app, aggiorna anche qui.

## Moduli

| Modulo | Cosa fa |
| --- | --- |
| Circolari | Lista, dettaglio, PDF servito dal Worker/R2, analisi AI condivisa o fatta nel browser con la chiave personale, scadenze nel calendario senza doppioni |
| Bacheca | Proposte (anche anonime), voti, commenti, colonne per stato, azioni del Rappresentante |
| Calendario | Eventi con filtri per categoria, inserimento, eliminazione con le stesse regole del server |
| Mappa posti | Solo la vista della mappa pubblicata |
| Impostazioni | API key Google AI Studio, notifiche push e categorie, uscita |

## Sviluppo

```bash
cd web
npm install
npm run dev        # http://localhost:5173 (CORS del Worker già aperto a localhost)
npm test           # test della logica portata
npm run build      # typecheck (app + service worker) e build in dist/
```

`VITE_API_BASE` (vedi `.env.example`) punta a un altro Worker, per esempio `wrangler dev` in locale.
Se cambia l'indirizzo del Worker in produzione va aggiornato anche `connect-src` in `public/_headers`.

Il service worker gira solo nella build: per provarlo `npm run build && npx vite preview`.

## Deploy

Workflow `.github/workflows/web-deploy.yml`: parte a ogni push su `main` che tocca `web/**` (o il
contratto condiviso) e si può lanciare a mano. Fa test, build e `wrangler pages deploy`, creando il
progetto Pages al primo giro.

## Sicurezza

- CSP in `public/_headers`: niente script inline né `eval`, rete solo verso il Worker e
  `generativelanguage.googleapis.com`.
- La chiave Google AI Studio resta in IndexedDB su quel dispositivo e va solo a Google (Gemini accetta
  le chiamate dirette dal browser: CORS verificato).
- Al logout: iscrizione push rimossa, cache `aila-api`/`aila-pdf` del service worker svuotate,
  IndexedDB cancellato.
