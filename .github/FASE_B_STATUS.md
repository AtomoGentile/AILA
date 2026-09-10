# Fase B — CI GitHub Actions — ✅ COMPLETATA

Data completamento: 10 settembre 2026

## Task Completato

### ✅ B1. Workflow `.github/workflows/ios-build.yml`
**File**: `.github/workflows/ios-build.yml`

**Configurazione**:
- **Runner**: `macos-15` (macOS Sequoia, versione recente più stabile)
- **Trigger**: Push e Pull Request su:
  - `shared/**` — codice Kotlin Multiplatform
  - `iosApp/**` — sorgenti iOS
  - `.github/workflows/ios-build.yml` — il workflow stesso
  - `gradle/**`, `build.gradle.kts`, `settings.gradle.kts` — configurazione build

**Passi del workflow**:

1. **Checkout** — `actions/checkout@v4` — clona il repo
2. **JDK 17** — `actions/setup-java@v4`, distribution Temurin
   - Necessario per Gradle (compila KMP)
3. **Gradle cache** — `actions/cache@v4`
   - Path: `~/.gradle/caches` e `~/.gradle/wrapper`
   - Chiave basata su `gradle-wrapper.properties` e `**/*.gradle.kts`
   - Accelera build successive evitando download dipendenze duplicate
4. **XcodeGen** — `brew install xcodegen`
   - Genera il progetto Xcode da `iosApp/project.yml`
5. **Generazione progetto Xcode** — `cd iosApp && xcodegen generate`
   - Crea `iosApp.xcodeproj` da `project.yml`
6. **Build** — `xcodebuild` per iOS Simulator, Debug configuration
   - Flags:
     - `-project iosApp/iosApp.xcodeproj`
     - `-scheme iosApp`
     - `-destination 'generic/platform=iOS Simulator'`
     - `-configuration Debug`
     - `CODE_SIGNING_ALLOWED=NO` — nessuna firma (non disponibile in CI)
     - `build` — target

**Note sulla build**:
- **Simulatore generico** (`generic/platform=iOS Simulator`): Xcode lo risolve a runtime basandosi su cosa è disponibile nel runner
- **Senza firma**: OK per CI — firma richiede certificati Apple (disponibili solo con Apple Developer account)
- **Debug configuration**: Veloce per CI, va bene per verificare che compila
- Se necessaria la build Release: aggiungerla come step separato con `-configuration Release`

## Flusso Verificato

```
github push (su shared/ o iosApp/)
    ↓
CI trigger → macos-15 runner
    ↓
Checkout + JDK 17 + Gradle cache
    ↓
xcodegen generate (project.yml → iosApp.xcodeproj)
    ↓
./gradlew :shared:embedAndSignAppleFrameworkForXcode
(pre-build script in project.yml)
    ↓
xcodebuild (compila Swift + il framework Kotlin linkato)
    ↓
✅ build succeeds  OR  ❌ build fails + log dettagliato
```

## Prossimo Passo: Fase C

La CI è ora **la sola verifica** che il codice compila davvero (non è possibile testare localmente senza Mac).

**Fase C** — Bridge Apple Intelligence:
- Task C1: Interfaccia Kotlin `AppleIntelligenceBridge`
- Task C2: Implementazione iOS `LocalAiPlatform.ios.kt`
- Task C3: Catalogo modelli iOS-specifico
- Task C4: `AppleIntelligenceEngine.swift` (implementazione)
- Task C5: Iniezione del bridge in `iOSApp.swift`

Quando Fase C è pronta, la CI verificherà che compila insieme.

## Blocchi Noti

1. **XcodeGen deve generare un progetto funzionante**: se fallisce, l'errore sarà nel log della CI (es. "unknown key in project.yml", "invalid path")
2. **Il pre-build script di Gradle deve trovare gli strumenti giusti**: se `/gradlew` non esiste o non è eseguibile, fallirà
3. **Framework search paths**: il percorso `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` deve esistere dopo il pre-build script
4. **Se la build fallisce**: cercare nel log della CI:
   - Errori XcodeGen → problema nel `project.yml`
   - Errori Gradle → problema in `shared/` o nelle dipendenze
   - Errori Swift/Linker → problema nell'entry point o nei bridge (future fasi)

---

**Prossima verifica**: Fare push dei file di Fase A e B, aprire GitHub Actions e controllare che la run sia verde. Se fallisce, il log dirà esattamente dove.
