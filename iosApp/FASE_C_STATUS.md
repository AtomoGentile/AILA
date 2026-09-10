# Fase C — Bridge Apple Intelligence — ✅ COMPLETATA

Data completamento: 10 settembre 2026

**Stato**: Pronta per compilazione in CI (fase B verificherà che compila con il pre-build script Gradle)

---

## Task Completati

### ✅ C1. Interfaccia Kotlin `AppleIntelligenceBridge.kt`
**File**: `shared/src/iosMain/kotlin/circolareplus/ai/AppleIntelligenceBridge.kt`

**Contiene**:
- `interface AppleIntelligenceBridge`:
  - `fun isAvailable(): Boolean` — vera se Apple Intelligence è disponibile
  - `fun unavailableReason(): String?` — motivo leggibile se non disponibile
  - `suspend fun generate(systemPrompt, userPrompt, timeoutMillis, stopWhen): String` — generazione streaming
- `object AppleIntelligenceBridgeHolder` — holder singleton per il bridge, iniettato da Swift

**Note**: Kotlin/Native esporta questa interfaccia come protocollo Objective-C, permettendo a Swift di implementarla.

---

### ✅ C2. Implementazione iOS `LocalAiPlatform.ios.kt`
**File**: `shared/src/iosMain/kotlin/circolareplus/ai/LocalAiPlatform.ios.kt`

**Implementa**:
- `totalDeviceRamMb()` → RAM totale da `NSProcessInfo.processInfo.physicalMemory`
- `isOnDeviceAiAvailable()` → delega al bridge
- `onDeviceAiUnavailableReason()` → motivo da bridge o "non inizializzato"
- `LocalModelStore`:
  - `isInstalled()` → `isOnDeviceAiAvailable()`
  - `installedPath()` → `"apple-intelligence"` se disponibile
  - `download()` → completamento istantaneo con `onProgress(1, 1)`, restituisce `Installed` o `Failed`
  - Il resto (`partialBytes`, `freeSpaceBytes`, `delete`, ecc.) → 0/false (non applicabile su iOS)
- `LocalLlm`:
  - `generate()` → delega a bridge (ignora modelPath, preferGpu, maxOutputTokens)
  - `backendLabel()` → `"Apple Intelligence (Neural Engine)"`
  - `unload()` → nop (il modello di sistema non si carica/scarica)

**Design**: Riporta il modello di sistema come se fosse "sempre installato" se il dispositivo lo supporta, rendendo trasparente il catalogo Android che preconfigurato il download.

---

### ✅ C3. Catalogo Platform-Specific
**File**: 
- `shared/src/commonMain/kotlin/circolareplus/ai/LocalAiModels.kt` — dichiarazioni `expect`
- `shared/src/androidMain/kotlin/circolareplus/ai/LocalAiModels.android.kt` — 5 modelli
- `shared/src/iosMain/kotlin/circolareplus/ai/LocalAiModels.ios.kt` — 1 modello

**Approccio**: Trasformato `LocalAiCatalog` da object con implementazione fissa a `expect object` con implementazioni platform-specific.

**Android** (LocalAiModels.android.kt):
- QWEN35_08B (963 MB, LOW tier)
- QWEN35_2B (2,1 GB, MID tier)
- QWEN35_4B (2,8 GB, HIGH tier)
- GEMMA4_E2B (2,6 GB, LOW tier)
- GEMMA4_E4B (3,7 GB, MID tier)

**iOS** (LocalAiModels.ios.kt):
- APPLE_INTELLIGENCE (0 byte, UNKNOWN tier)
  - `downloadUrl: ""` (nessun download)
  - `approxSizeBytes: 0L`
  - `recommendedRamMb: 0` (il sistema gestisce la memoria)

**Metodi**:
- `val all: List<LocalAiModel>` — lista piattaforma-specifica
- `fun byId(id)` → cerca per ID
- `fun recommendedFor(tier)` → modello consigliato
- `fun selectableFor(totalRamMb)` → ordinati con consigliato in cima
- `fun knownFileNames()` → riconosce file orfani

---

### ✅ C4. Implementazione Swift `AppleIntelligenceEngine.swift`
**File**: `iosApp/iosApp/AppleIntelligenceEngine.swift`

**Implementa il protocollo** esportato da Kotlin come `AppleIntelligenceBridge`.

**Metodi**:
- `isAvailable() -> Bool`:
  - `SystemLanguageModel.default.availability == .available`
- `unavailableReason() -> String?`:
  - Mappa i casi `.unavailable(reason)` a messaggi in italiano:
    - "Questo iPhone non supporta Apple Intelligence"
    - "La lingua non è supportata"
    - "Apple Intelligence non è ancora installato"
- `generate(systemPrompt:userPrompt:timeoutMillis:stopWhen:) async throws -> String`:
  - Crea `LanguageModelSession(instructions: systemPrompt)`
  - Itera `session.streamResponse(to: userPrompt)`
  - Accumula testo e chiama `stopWhen(accumulatedText)` ogni chunk
  - Interrompe quando `stopWhen` ritorna true
  - Timeout con `withThrowingTaskGroup` — race fra generazione e `Task.sleep`
  - Lancia NSError se timeout o errore reale

**Note**:
- FoundationModels richiede iOS 26+ (non verificabile senza Mac)
- La closure Kotlin `stopWhen` accede direttamente (interop Kotlin/Swift)
- Il timeout è la protezione principale per evitare che la UI si blocchi

---

### ✅ C5. Iniezione Bridge in `iOSApp.swift`
**File aggiornato**: `iosApp/iosApp/iOSApp.swift`

**Nel `init()`**:
```swift
AppleIntelligenceBridgeHolder.shared.bridge = AppleIntelligenceEngine()
```

**Timing**: Eseguito prima di `ComposeView()`, garantisce che `AppleIntelligenceBridgeHolder.bridge` non è null quando MainViewController Kotlin lo chiama.

---

## Flusso Completo A → B → C

```
1. Sviluppatore modifica shared/ o iosApp/
2. Git push
3. GitHub Actions (macos-15)
   ├─ JDK 17 + Gradle cache
   ├─ XcodeGen: project.yml → iosApp.xcodeproj
   ├─ Pre-build script Gradle:
   │  └─ ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   │     (compila KMP incluso iosMain/ con AppleIntelligenceBridge)
   ├─ Xcode linker:
   │  ├─ Trova shared.framework
   │  ├─ Compila Swift (iOSApp.swift + AppleIntelligenceEngine.swift)
   │  └─ Risolve il protocollo AppleIntelligenceBridge
   └─ Build output
      ├─ ✅ Debug app per iOS Simulator (senza firma)
      └─ ❌ Errori di compilazione (log preciso)
```

---

## Verifiche Compilazione Possibili

La CI verificherà:

1. **Sintassi Kotlin** (iosMain/):
   - `AppleIntelligenceBridge.kt` — interfaccia valida
   - `LocalAiPlatform.ios.kt` — implementazione delle expect
   - `LocalAiModels.ios.kt` — actual object del catalogo

2. **Interop Kotlin/Native**:
   - Interface esportata come protocollo Objective-C
   - Holder singleton visibile a Swift
   - Firme match fra Kotlin e Swift

3. **Sintassi Swift**:
   - `AppleIntelligenceEngine.swift` — conforme al protocollo
   - `iOSApp.swift` — iniezione corretta

4. **Linker**:
   - Framework `shared` trovato
   - Simboli Kotlin risolti
   - Nessun undefined reference

---

## Blocchi Noti e Limiti

1. **Codice non eseguibile senza Mac**:
   - La CI verifica che compila, ma non lo esegue
   - FoundationModels funziona solo su dispositivo reale
   - Se la generazione fallisce (timeout, dispositivo non supportato), si scoprirà solo su un vero iPhone

2. **Timeout hardcoded a 90 secondi**:
   - Se Apple Intelligence è più veloce, va bene
   - Se è più lenta, il timeout interrompe la generazione — comportamento accettato

3. **No exception handling particolari**:
   - Se il bridge è null, eccezione dalla Kotlin (per design)
   - Se FoundationModels non è disponibile al runtime, crash dell'app (non CI)

4. **Nessun test automatico**:
   - La verifica è solo compilazione
   - Comportamento reale testato manualmente su iPhone

---

## Prossimo Passo: Fase D

**Fase D** — Adattare la schermata Impostazioni AI per iOS:
- Leggere `MainAppShell.kt` intorno a riga 1247 (LocalAiCatalog.selectableFor)
- Su iOS: mostrare stato "Apple Intelligence: disponibile/non disponibile" anziché elenco modelli
- Su Android: mantenere l'elenco di 5 modelli con download

Questo tocca solo la UI, non il codice platform-specifico, quindi è meno critico per la compilazione.

---

**Stato finale**: ✅ Fase C pronta. Fase A+B+C compilano (se la CI è verde). Pronti per Fase D (UI Settings) e Fase E (Push Notifications, bassa priorità).
