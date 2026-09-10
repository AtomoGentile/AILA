# Fase A — Scheletro Progetto Xcode — ✅ COMPLETATA

Data completamento: 10 settembre 2026

## Task Completati

### ✅ A1. `iosApp/project.yml` 
**File**: `iosApp/project.yml`  
**Contenuto**:
- Configurazione XcodeGen per progetto iOS
- Bundle ID: `com.circolareplus`
- Deployment target: iOS 26.0
- Pre-build script: esegue `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
- Framework search paths: punta a `shared.framework` generato da Gradle
- Scheme Debug e Release configurati
- Senza firma (Code signing disabilitato per CI)

**Note**: Sintassi YAML verificata. Non è possibile generare il progetto senza Mac + XcodeGen.

### ✅ A2. Info.plist e Assets minimi
**File creati**:
- `iosApp/iosApp/Info.plist` — Plist minimale ma completo
  - CFBundleName: "Circolare+"
  - Orientamenti supportati: Portrait (iPad supporta anche landscape)
  - UILaunchScreen vuoto (splash bianco di sistema)
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` — Scheletro AppIcon
  - 8 slot di icon per iPhone (20x20@2x, 20x20@3x, 29x29@2x, ecc.)
  - Nessuna immagine reale (placeholder)

**Note**: Il progetto Xcode compilerà anche senza le immagini, mostrerà una schermata grigia.

### ✅ A3. Entry point Swift (`iOSApp.swift`)
**File aggiornato**: `iosApp/iosApp/iOSApp.swift`  
**Cambiamenti**:
- Aggiunto `init()` con placeholder comment per l'iniezione del bridge (Fase C)
- Resto già corretto: SwiftUI App che avvolge `MainViewControllerKt.MainViewController()`
- Import `shared` già presente

## Documentazione Aggiunta

- `iosApp/README.md` — Spiega il flusso XcodeGen e come modificare il progetto

## Prossimo Passo: Fase B

**Fase B** — CI GitHub Actions (build-only, senza firma)

Task B1: Creare `.github/workflows/ios-build.yml`
- Runner: `macos-15`
- Trigger: push/PR su `shared/`, `iosApp/`, workflow stesso
- Passi: Checkout → JDK → Gradle cache → `brew install xcodegen` → `xcodegen generate` → `xcodebuild` senza firma
- **Questa è la prima verifica che il codice compila davvero.**

## Blocchi Noti

1. **Non è possibile testare XcodeGen localmente** senza un Mac.
2. **La CI (Fase B) è essenziale** per verificare che il YAML e il Gradle script funzionano insieme.
3. Nessun'icona reale (Assets) — andrà aggiunta quando il logo AILA sarà disponibile in formato esportabile.
4. Nessuna firma (per ora ok, la CI non firma).

## File State

```
iosApp/
├── project.yml                    ← XcodeGen config
├── README.md                      ← Istruzioni
├── PIANO_SVILUPPO_IOS.md          ← Piano originale (esterno)
├── FASE_A_STATUS.md               ← Questo file
└── iosApp/
    ├── iOSApp.swift               ← Entry point Swift
    ├── Info.plist                 ← Configurazione app
    └── Assets.xcassets/
        └── AppIcon.appiconset/
            └── Contents.json      ← Scheletro AppIcon
```

---

**Verifica successiva**: Eseguire Fase B (GitHub Actions CI) dopo aver fatto push.
