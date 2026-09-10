# Circolare+ — Guida Operativa di Sviluppo e Deployment

Questo repository contiene l'implementazione completa dell'ecosistema **Circolare+** per la gestione della classe scolastica (Target: Android, iOS, iPadOS e Cloudflare Serverless).

---

## Struttura del Progetto

```
Circolare+/
├── backend/                              # Backend Serverless Cloudflare
│   ├── schema.sql                        # Schema D1 SQL con classe singola (DEFAULT_CLASS)
│   ├── wrangler.toml                     # Configurazione Cloudflare (Bindings D1, R2, Cron Trigger)
│   ├── package.json                      # Dipendenze Worker Hono/TypeScript
│   └── src/
│       └── index.ts                      # Cron Spaggiari ogni 15 min, Storage R2, Trigger FCM, API REST
├── shared/                               # Core Kotlin Multiplatform & Compose UI
│   ├── src/commonMain/kotlin/circolareplus/
│   │   ├── algorithms/
│   │   │   ├── SeatMapOptimizer.kt       # Algoritmo disposizione banchi, blindatura rifiuti e burnout L5
│   │   │   └── SondaggiEngine.kt         # Griglia interrogazioni, budget voti e bonus sacrificio
│   │   ├── domain/model/
│   │   │   ├── UserModels.kt             # User, StudentProfile (altezza 140-210 cm a step di 5cm), Rating
│   │   │   └── ContentModels.kt          # Circolari, AI Badges, Eventi Calendario, Bacheca e Anonimato
│   │   ├── data/
│   │   │   ├── repository/SeatMapRepository.kt # Generatore delle 3 proposte d'aula
│   │   │   └── local/LocalSettingsManager.kt   # Gestione API Key AI e preferenze locali
│   │   ├── ai/
│   │   │   └── ClientSideAiClassifier.kt # Classificazione AI locale privacy-first con API Key personale
│   │   ├── design/
│   │   │   └── AppTheme.kt               # Design System School-tech (Raggi, spaziatura 4dp, colori)
│   │   └── ui/
│   │       ├── MainAppShell.kt           # Navigazione tab bar a 4 tab + Profilo
│   │       └── screens/
│   │           ├── HomeScreen.kt         # Home need-to-know identica al mockup approvato
│   │           ├── CalendarScreen.kt     # Calendario con eventi AI e badge 'Per Tutti'
│   │           ├── CircularsScreen.kt    # Elenco circolari con filtri per badge di pertinenza
│   │           ├── CircularDetailScreen.kt # Dettaglio con PDF centrale e box 'Analisi personale AI'
│   │           ├── BoardScreen.kt        # Bacheca proposte per colonne di stato, voti e anonimato
│   │           ├── SeatMapScreen.kt      # Mappa 2D orientata con lavagna e comandi admin slider pesi
│   │           ├── SocialPreferencesVotingScreen.kt # Scheda voto compagni con vincoli anti-gaming
│   │           ├── PollsScreen.kt        # Selezione date interrogazioni con contatori e bonus sacrificio
│   │           └── ProfileScreen.kt      # Scheda utente, altezza, silenzia bacheca e API Key personale
│   └── src/commonTest/kotlin/circolareplus/algorithms/
│       ├── SeatMapOptimizerTest.kt       # Test unitari per tutte le casistiche matematiche della mappa
│       └── SondaggiEngineTest.kt         # Test unitari per budget voti e bonus sacrificio
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/
    └── libs.versions.toml                # Version catalog Kotlin 2.0.20 e Compose Multiplatform
```

---

## Istruzioni di Build ed Esecuzione

### 1. Backend Serverless (Cloudflare)
```bash
cd backend
npm install

# Test in locale con emulatore D1 e R2
npx wrangler dev

# Inizializzazione schema database D1 in locale
npx wrangler d1 execute circolare_d1 --local --file=./schema.sql

# Deployment in produzione
npx wrangler deploy
npx wrangler d1 execute circolare_d1 --remote --file=./schema.sql
```

### 2. Core KMP e Test Unitari
```bash
# Esecuzione della test suite degli algoritmi (SeatMapOptimizer & SondaggiEngine)
./gradlew check
```
