# AILA — Guida Operativa di Sviluppo e Deployment

**AILA** (ex "Circolare+") è un'app multipiattaforma per la gestione della vita di classe scolastica: circolari con
analisi AI, assistente conversazionale, calendario condiviso, bacheca proposte, sondaggi, mappa posti in aula e
notifiche push in tempo reale.

Target: **Android**, **iOS** / **iPadOS**, backend **Cloudflare Serverless**.

---

## Indice

- [Cosa fa l'app](#cosa-fa-lapp)
- [Stack tecnologico](#stack-tecnologico)
- [Struttura del repository](#struttura-del-repository)
- [Setup e build](#setup-e-build)
  - [Backend (Cloudflare Worker)](#1-backend-cloudflare-worker)
  - [Android](#2-android)
  - [iOS](#3-ios)
  - [Test unitari condivisi](#4-test-unitari-condivisi)
  - [Pubblicare APK e IPA](#5-pubblicare-apk-e-ipa)
- [Notifiche push (Firebase)](#notifiche-push-firebase)
- [Privacy & AI](#privacy--ai)
- [Documentazione di riferimento](#documentazione-di-riferimento)

---

## Cosa fa l'app

| Modulo | Descrizione |
|---|---|
| **Circolari** | Il backend controlla il portale Spaggiari della scuola ogni 10-15 minuti, mette in cache i PDF nuovi e notifica la classe. Il **server** riassume ogni circolare nuova una sola volta con Google Gemini, per tutte le classi, e assegna a ciascuna classe una categoria (*Ti riguarda / Potenziale interesse / Non ti riguarda*) e una nota, con estrazione automatica delle scadenze. Il risultato è condiviso: chi apre la circolare lo trova già pronto. Se il server non l'ha ancora analizzata, il telefono ripiega sull'AI locale o su una chiave personale (vedi [Privacy & AI](#privacy--ai)). |
| **AILA Assistant e Ricerca** | L'assistente dell'app: una domanda in italiano e una risposta costruita solo sui dati che AILA ha già (circolari e loro analisi, calendario, bacheca, sondaggi, mappa posti), con i riferimenti cliccabili delle fonti, cronologia delle conversazioni e ricerca globale. Gira con la stessa chiave AI personale della classificazione (vedi [Privacy & AI](#privacy--ai)). |
| **Calendario** | Eventi scolastici, anche generati automaticamente dalle scadenze estratte dalle circolari. L'orario si sceglie in ore di lezione (menu "Da" / "A", 1ª-6ª ora); il server accetta anche formule come "3ª ora" o "Dalla 2ª alla 4ª ora". |
| **Bacheca proposte** | Proposte della classe con voti, commenti e possibilità di pubblicare in forma anonima (con quorum di governance per lo sblocco identità in caso di abuso: 2 Rappresentanti + 1 Guardia di Sicurezza scelta dal Rappresentante nella Scheda Classe, ciascuno approva dal proprio account; vale anche per i commenti anonimi). Le proposte chiuse sono accettate o rifiutate. |
| **Sondaggi interrogazioni** | Storico visibile a tutta la classe; elimina e "aggiungi al calendario" restano al Rappresentante. Prenotazione delle date d'interrogazione con un sistema a budget di voti (verde/giallo/rosso chiaro/rosso scuro) e "bonus sacrificio" per chi rinuncia più spesso alla data preferita, per prevenire il gaming del sistema. |
| **Sondaggi a ordinamento** | Il Rappresentante propone da 2 a 10 opzioni e ognuno le mette in ordine. La classifica della classe è a punti (Borda: con N opzioni il primo posto vale N-1, l'ultimo 0) ed è sempre aggiornata; si vede dopo aver inviato la propria o a sondaggio chiuso. |
| **Mappa posti** | Il Rappresentante genera 3 proposte di disposizione banchi con l'algoritmo `SeatMapOptimizer`, che bilancia preferenze sociali, tutoring tra pari, livello di chiasso e altezza — con blindatura dei rifiuti assoluti e prevenzione del "burnout" per chi fa sempre da tutor. La mappa è personalizzabile: tipo di banco preferito, numero di file e posti per fila (es. 8, 7, 7); i posti riempiti sono quanti gli iscritti, i posti in più si tolgono da dietro e coppie e trii restano misti. |
| **Preferenze sociali** | Votazione di gradimento reciproco fra compagni (-2…+2), aperta e chiusa esplicitamente dal Rappresentante (mai raccolta di nascosto), usata come input dell'algoritmo mappa posti. Il voto compare subito e torna indietro se il server lo rifiuta. |
| **Notifiche push** | Firebase Cloud Messaging avvisa in tempo reale per nuove circolari, sondaggi, proposte, apertura preferenze e aggiornamenti mappa posti. |
| **Multi-classe** | Ogni classe ha i propri dati (utenti, circolari, bacheca, ecc.) isolati nello stesso database. |
| **Aspetto** | Due stili grafici, **Glass** (vetro, iOS) e **Material**, con scala tipografica e animazioni condivise dal design system, container transform tra pulsanti e fogli, e rispetto di "Riduci movimento". |

Navigazione a tab: Calendario · Classe (Circolari e Bacheca) · Home · Sondaggi · Mappa posti.


---

## Stack tecnologico

**Frontend (mobile)**
- **Kotlin Multiplatform (KMP)** + **Compose Multiplatform** — codice condiviso tra Android e iOS
- Piattaforme: Android, iOS, iPadOS
- Architettura modulare: `domain` (modelli), `data` (repository/API), `algorithms`, `ai`, `design` (design system), `ui` (screen Compose)
- Classificazione AI **ibrida**: di norma la fa il server con Gemini; il telefono interviene come riserva con l'AI locale o una chiave personale (vedi sotto)

**Backend (serverless)**
- **Cloudflare Workers** (TypeScript + [Hono](https://hono.dev)) con **Cron Trigger** ogni 15 minuti
- **Cloudflare D1** — database SQL relazionale
- **Cloudflare R2** — cache centralizzata dei PDF delle circolari
- **Google Gemini** — riassunto e classificazione delle circolari, eseguiti dal cron del Worker
- **Firebase Cloud Messaging (HTTP v1 API)** — notifiche push

---

## Struttura del repository

```
AILA/
├── backend/                       # Cloudflare Worker (TypeScript / Hono)
│   ├── schema.sql                 # Schema Cloudflare D1
│   ├── wrangler.toml              # Config Cloudflare (D1, R2, Cron Trigger)
│   ├── package.json
│   └── src/
│       ├── index.ts               # Entry point, mount delle route + cron Spaggiari
│       ├── routes/                # auth, admin, users, circulars, calendar,
│       │                          # proposals, polls, rankingPolls, preferences, ratings, seatmap, fcm
│       └── services/               # spaggiari.ts (scraping+cache), fcm.ts (invio push)
│
├── shared/src/commonMain/kotlin/circolareplus/   # Codice condiviso Android + iOS
│   ├── algorithms/                # SeatMapOptimizer, SondaggiEngine
│   ├── ai/                        # Classificazione AI di riserva sul telefono (AI locale / chiave personale)
│   ├── domain/model/              # Modelli di dominio (User, Circular, Proposal, SeatMap, ...)
│   ├── data/                      # Repository, client API (Ktor), cache/preferenze locali
│   ├── design/                    # Design system (colori, tipografia, componenti condivisi)
│   └── ui/
│       ├── MainAppShell.kt        # Navigazione a tab
│       └── screens/               # Home, Calendario, Circolari, Bacheca, Sondaggi, Assistant,
│                                   # Ricerca, Mappa Posti, Profilo, Impostazioni, ...
│
├── androidApp/                    # Wrapper Android (Jetpack Compose, servizi nativi, FCM)
├── iosApp/                        # Progetto iOS (XcodeGen + SwiftUI/Compose entrypoint)
│   └── project.yml                # Config XcodeGen — unica fonte di verità del progetto Xcode
├── web/                           # PWA (non mantenuta)
├── design/ · video-presentazione/ # Logo e video di presentazione
│
├── settings.gradle.kts / build.gradle.kts / gradle/   # Config Gradle e version catalog
└── *.pdf                          # Documenti di specifica tecnica originali (vedi in fondo)
```

---

## Setup e build

### 1. Backend (Cloudflare Worker)

```bash
cd backend
npm install

# Sviluppo locale con emulatore D1/R2
npx wrangler dev

# Inizializzazione schema database D1 in locale
npx wrangler d1 execute circolare_d1 --local --file=./schema.sql

# Deploy in produzione
npx wrangler deploy
npx wrangler d1 execute circolare_d1 --remote --file=./schema.sql
```

La configurazione (database D1, bucket R2, cron e variabili d'ambiente) è in `backend/wrangler.toml`, dove sono
elencati e commentati anche i secret richiesti. I secret non vanno mai committati: si impostano con
`npx wrangler secret put <NOME>`. Senza i secret opzionali (analisi AI sul server, notifiche push) l'app funziona
lo stesso, con meno funzioni.

### 2. Android

Prerequisiti: Android Studio (o solo Gradle), JDK 17+.

```bash
./gradlew :androidApp:assembleDebug
```

- `applicationId`: `com.circolareplus` · `minSdk 26` · `targetSdk 34`
- Per le notifiche push serve `androidApp/google-services.json` (vedi sotto) — senza, l'app compila comunque.

### 3. iOS

Richiede **macOS** con Xcode e [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

Il progetto Xcode **non va editato a mano**: è generato da `iosApp/project.yml`.

```bash
cd iosApp
xcodegen generate
open iosApp.xcodeproj
```

Ogni modifica alla configurazione del progetto va fatta in `project.yml`, poi si rigenera. La CI
(`.github/workflows/ios-build.yml`) fa lo stesso automaticamente a ogni push su `shared/` o `iosApp/`. Dettagli in
[iosApp/README.md](iosApp/README.md).

### 4. Test unitari condivisi

```bash
./gradlew check
```

Copre principalmente gli algoritmi (`SeatMapOptimizer`, `SondaggiEngine`) in `shared/src/commonTest/`.

### 5. Pubblicare APK e IPA

Entrambi i file escono dalla CI di GitHub Actions, senza account a pagamento.

- **APK** (`AILA.apk`): lo produce il workflow *Android Build* a ogni push su `shared/` o `androidApp/` (artifact
  `AILA-apk`). Creando un tag `v*` (per esempio `v0.0.1`) viene allegato anche alla Release di GitHub.
- **IPA** (`AILA.ipa`, non firmata, per SideStore): si lancia a mano da *Actions → iOS IPA (SideStore) → Run
  workflow* (artifact `AILA-ipa`; sui tag `v*` finisce anche nella Release). La firma la fa SideStore sul
  dispositivo con l'Apple ID gratuito.

---

## Notifiche push (Firebase)

Il codice è già scritto e pronto su entrambe le piattaforme; senza Firebase configurato l'app funziona lo stesso, solo
senza notifiche. Guida completa passo-passo in **[FIREBASE_SETUP.md](FIREBASE_SETUP.md)**: creazione progetto
Firebase, `google-services.json` (Android) / `GoogleService-Info.plist` (iOS), generazione del Service Account e
configurazione dei secret sul Worker.

⚠️ Il file JSON del Service Account contiene una chiave privata: **non va mai committato**. È già escluso da
`.gitignore` (pattern `*firebase-adminsdk*.json`) — va sempre passato al Worker solo tramite
`npx wrangler secret put`.

---

## Privacy & AI

La classificazione delle circolari (rilevanza per classe, scadenze) la fa di norma il **server**: il cron del Worker
manda a Google Gemini il PDF già in cache e salva un'analisi condivisa dalla classe. La chiave sta solo nei secret
di Cloudflare, mai nell'app né in una rotta HTTP, e l'AI non riceve dati personali degli utenti.

Se il server non ha ancora analizzato una circolare (o non ha `GEMINI_API_KEY`), il telefono la analizza da sé, con
l'AI sul dispositivo oppure con una chiave personale di Google AI Studio inserita dall'utente, e può condividere il
risultato. Se anche questo fallisce, l'app usa una classificazione euristica a parole chiave: resta utilizzabile
senza configurare nulla. L'informativa completa è in [PRIVACY.md](PRIVACY.md).

---

## Documentazione di riferimento

Nella root del repository sono presenti i documenti di specifica tecnica originali, utili per capire il "perché"
dietro alle scelte di architettura, algoritmi e regole di dominio:

- `CircolarePlus_Specifica_Tecnica_Master_v3.pdf` — specifica master: architettura, schema dati, algoritmo mappa posti, modulo sondaggi, pipeline circolari
- `CircolarePlus_Architettura_UIUX.pdf` — linee guida di design e UI/UX
- `Circolare_Modulo_Sondaggi_v2.pdf` — dettaglio del modulo sondaggi interrogazioni
- `Circolare_Riepilogo_Moduli_v1.2_Tecnica_FrecceCorrette_v2-1.pdf` — riepilogo tecnico dei moduli
- `Specifiche_Tecniche_Mappa_Posti_Rappresentante.pdf` — specifica dell'algoritmo mappa posti

Nota: questi documenti descrivono la visione originale del progetto; per lo stato attuale del codice fai sempre
riferimento al codice sorgente e a questo README, che possono essere andati oltre la specifica iniziale (es. supporto
multi-classe).
