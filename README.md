# AILA — Guida Operativa di Sviluppo e Deployment

**AILA** (ex "Circolare+") è un'app multipiattaforma per la gestione della vita di classe scolastica: circolari con
analisi AI, calendario condiviso, bacheca proposte, sondaggi per le interrogazioni, mappa posti in aula e notifiche
push in tempo reale.

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
- [Notifiche push (Firebase)](#notifiche-push-firebase)
- [Privacy & AI](#privacy--ai)
- [Documentazione di riferimento](#documentazione-di-riferimento)

---

## Cosa fa l'app

| Modulo | Descrizione |
|---|---|
| **Circolari** | Il backend controlla il portale Spaggiari della scuola ogni 10-15 minuti, mette in cache i PDF nuovi e notifica la classe. Ogni dispositivo scarica il PDF e lo classifica **in locale** (vedi [Privacy & AI](#privacy--ai)) in una delle categorie *Ti riguarda / Potenziale interesse / Non ti riguarda*, con estrazione automatica di eventuali scadenze. |
| **Calendario** | Eventi scolastici, anche generati automaticamente dalle scadenze estratte dalle circolari. |
| **Bacheca proposte** | Proposte della classe con voti, commenti e possibilità di pubblicare in forma anonima (con quorum di governance per lo sblocco identità in caso di abuso: 2 Rappresentanti + 1 Guardia di Sicurezza). |
| **Sondaggi interrogazioni** | Prenotazione delle date d'interrogazione con un sistema a budget di voti (verde/giallo/rosso chiaro/rosso scuro) e "bonus sacrificio" per chi rinuncia più spesso alla data preferita, per prevenire il gaming del sistema. |
| **Mappa posti** | Il Rappresentante genera 3 proposte di disposizione banchi con l'algoritmo `SeatMapOptimizer`, che bilancia preferenze sociali, tutoring tra pari, livello di chiasso e altezza — con blindatura dei rifiuti assoluti e prevenzione del "burnout" per chi fa sempre da tutor. |
| **Preferenze sociali** | Votazione di gradimento reciproco fra compagni (-2…+2), aperta e chiusa esplicitamente dal Rappresentante (mai raccolta di nascosto), usata come input dell'algoritmo mappa posti. |
| **Notifiche push** | Firebase Cloud Messaging avvisa in tempo reale per nuove circolari, sondaggi, proposte, apertura preferenze e aggiornamenti mappa posti. |
| **Multi-classe** | Ogni classe ha i propri dati (utenti, circolari, bacheca, ecc.) isolati nello stesso database. |

---

## Stack tecnologico

**Frontend (mobile)**
- **Kotlin Multiplatform (KMP)** + **Compose Multiplatform** — codice condiviso tra Android e iOS
- Piattaforme: Android, iOS, iPadOS
- Architettura modulare: `domain` (modelli), `data` (repository/API), `algorithms`, `ai`, `design` (design system), `ui` (screen Compose)
- AI di classificazione **client-side**: gira sul dispositivo, non sul server (vedi sotto)

**Backend (serverless)**
- **Cloudflare Workers** (TypeScript + [Hono](https://hono.dev)) con **Cron Trigger** ogni 15 minuti
- **Cloudflare D1** — database SQL relazionale
- **Cloudflare R2** — cache centralizzata dei PDF delle circolari
- **Firebase Cloud Messaging (HTTP v1 API)** — notifiche push

---

## Struttura del repository

```
AILA/
├── backend/                       # Cloudflare Worker (TypeScript / Hono)
│   ├── schema.sql                 # Schema Cloudflare D1
│   ├── wrangler.toml              # Config Cloudflare (D1, R2, Cron Trigger, secrets richiesti)
│   ├── package.json
│   └── src/
│       ├── index.ts               # Entry point, mount delle route + cron Spaggiari
│       ├── routes/                # auth, users, circulars, calendar, proposals, polls,
│       │                          # preferences, ratings, seatmap, fcm
│       └── services/               # spaggiari.ts (scraping+cache), fcm.ts (invio push)
│
├── shared/src/commonMain/kotlin/circolareplus/   # Codice condiviso Android + iOS
│   ├── algorithms/                # SeatMapOptimizer, SondaggiEngine
│   ├── ai/                        # Classificazione AI locale delle circolari (client-side)
│   ├── domain/model/              # Modelli di dominio (User, Circular, Proposal, SeatMap, ...)
│   ├── data/                      # Repository, client API (Ktor), cache/preferenze locali
│   ├── design/                    # Design system (colori, tipografia, componenti condivisi)
│   └── ui/
│       ├── MainAppShell.kt        # Navigazione a tab
│       └── screens/               # Home, Calendario, Circolari, Bacheca, Sondaggi,
│                                   # Mappa Posti, Profilo, Impostazioni, ...
│
├── androidApp/                    # Wrapper Android (Jetpack Compose, servizi nativi, FCM)
├── iosApp/                        # Progetto iOS (XcodeGen + SwiftUI/Compose entrypoint)
│   └── project.yml                # Config XcodeGen — unica fonte di verità del progetto Xcode
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

Secrets richiesti (`npx wrangler secret put <NOME>`, da dentro `backend/`):

| Secret | Obbligatorio | Descrizione |
|---|---|---|
| `JWT_SECRET` | Sì | Firma dei token di autenticazione |
| `REPRESENTATIVE_SIGNUP_CODE` | No | Chi lo inserisce in registrazione ottiene il ruolo Rappresentante |
| `FCM_PROJECT_ID` | Per le push | ID progetto Firebase |
| `FCM_SERVICE_ACCOUNT_KEY` | Per le push | JSON del Service Account Firebase — vedi [Notifiche push](#notifiche-push-firebase) |

Senza i due secret FCM l'app funziona normalmente, semplicemente senza inviare notifiche push (nessun errore).

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

La classificazione AI delle circolari (rilevanza, scadenze) gira **esclusivamente sul dispositivo dello studente**,
usando una API Key personale di Google AI Studio inserita dall'utente stesso. Il testo delle circolari e i riassunti
non passano mai dal server di AILA. Se la chiave non è impostata o la chiamata fallisce, l'app ricade su una
classificazione euristica locale a parole chiave — resta utilizzabile senza configurare nulla.

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
