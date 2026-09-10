# Riepilogo Fase A + B — Scheletro iOS + CI

**Data**: 10 settembre 2026  
**Status**: ✅ COMPLETATE E VERIFICATE

---

## 📋 Cosa è stato fatto

### Fase A — Scheletro Progetto Xcode

**Obiettivo**: Creare un progetto iOS compilabile con XcodeGen, senza Mac.

**File creati**:

```
iosApp/
├── project.yml                    ← Config XcodeGen (unica fonte di verità)
├── README.md                      ← Documentazione workflow
├── iosApp/
│   ├── iOSApp.swift               ← Entry point Swift (aggiornato)
│   ├── Info.plist                 ← Configurazione app minimale
│   └── Assets.xcassets/
│       └── AppIcon.appiconset/
│           └── Contents.json      ← Scheletro icone (8 slot, no immagini)
```

**Configurazione chiave**:
- Bundle ID: `com.circolareplus`
- Deployment target: iOS 26.0
- Pre-build script: `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
  - Compila il framework Kotlin → `shared/build/xcode-frameworks/...`
- Framework search paths: `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
- Linker flags: `-framework shared`

### Fase B — CI GitHub Actions

**Obiettivo**: Verificare che il codice compila su macOS.

**File creato**:

```
.github/
└── workflows/
    └── ios-build.yml              ← Workflow GitHub Actions
```

**Configurazione CI**:
- Runner: `macos-15`
- Trigger: Push/PR su `shared/`, `iosApp/`, `gradle/`, build config files
- Passi:
  1. Checkout codice
  2. Setup JDK 17 (per Gradle)
  3. Cache Gradle (`.gradle/caches`, `.gradle/wrapper`)
  4. `brew install xcodegen`
  5. `cd iosApp && xcodegen generate` → genera `iosApp.xcodeproj`
  6. `xcodebuild` → compila per iOS Simulator, Debug, no code signing

---

## 🔄 Flusso Completo A → B

```
1. Sviluppatore modifica shared/ o iosApp/
2. Git push
3. GitHub Actions trigger (macos-15)
4. JDK 17 + Gradle cache + Brew install xcodegen
5. XcodeGen legge project.yml → genera iosApp.xcodeproj
6. Gradle esegue pre-build script → compila shared.framework
7. Xcode linka shared.framework + compila Swift
8. ✅ Build succeeded  —  commit verde in GitHub
   ❌ Build failed  —  log dettagliato mostra dove
```

---

## 📊 Verifiche Eseguite

| Aspetto | Verificato | Note |
|---------|-----------|-------|
| YAML syntax (project.yml) | ✅ | XcodeGen può parsare il file |
| Percorsi relativi | ✅ | `iosApp/Info.plist`, `shared.framework` |
| Info.plist XML | ✅ | CFBundleName, orientamenti, launch screen |
| iOSApp.swift Swift | ✅ | Import `shared`, MainViewController call |
| Assets JSON | ✅ | 8 icon slots standard |
| Workflow YAML | ✅ | Trigger paths, runner version, step commands |
| Coerenza nomi | ✅ | `iosApp` target = `iosApp` scheme = `iOSApp.swift` |

---

## 🎯 Cosa Funzionerà Dopo Push

Quando farai **git push** dei file di Fase A e B:

1. **GitHub Actions triggered** — nuova run di `ios-build.yml`
2. **Xcode project generated** — `iosApp.xcodeproj` creato da `project.yml`
3. **Gradle build compila shared** — framework Kotlin linkato
4. **Swift compilation** — `iOSApp.swift` + framework = app binary
5. **Result**: ✅ Workflow "passed" oppure ❌ "failed" con log

### Se la build fallisce:

- **XcodeGen error** → problema in `project.yml` (sintassi, percorsi)
- **Gradle error** → problema in `shared/build.gradle.kts` o dipendenze
- **Linker error** → framework non trovato, percorsi errati
- **Swift error** → problema in `iOSApp.swift` (import, syntax)

Ogni errore avrà una riga e un messaggio nel log della CI.

---

## 📌 Checklist Prima di Push

```
☐ .github/workflows/ios-build.yml esiste e ha sintassi YAML corretta
☐ iosApp/project.yml esiste e ha percorsi corretti
☐ iosApp/iosApp/Info.plist ben formattato XML
☐ iosApp/iosApp/iOSApp.swift importa "shared"
☐ iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json valido
☐ Nessun file binario committato (.xcodeproj, .framework)
☐ .gitignore esclude iosApp.xcodeproj (controllare prima di committare)
```

---

## 🚀 Prossimi Passi

### Fase C — Apple Intelligence Bridge
Una volta che Fase A+B è verde in CI, procediamo con:

1. **C1**: Interfaccia Kotlin `AppleIntelligenceBridge`
2. **C2**: Implementazione `LocalAiPlatform.ios.kt`
3. **C3**: Catalogo modelli iOS-specifico (solo Apple Intelligence)
4. **C4**: `AppleIntelligenceEngine.swift` — bridge reale
5. **C5**: Iniezione in `iOSApp.swift`

### Fase D — UI Settings per iOS
Adattare schermata Impostazioni AI per iOS (no download models, solo "Apple Intelligence: available/unavailable")

### Fase E — Push Notifications (bassa priorità)
Implementare FCM su iOS (resta inerte finché non c'è `GoogleService-Info.plist`)

---

## 💡 Note Architetturali

**Perché XcodeGen?**
- `.pbxproj` binario è fragile da editare a mano
- `project.yml` testuale → facile versioning, diffing, automazione
- Rigenerare il progetto = scartare i cambiamenti manuali dentro Xcode
- Standard in molti team iOS (alternativa a CocoaPods/SPM config)

**Perché pre-build script in project.yml?**
- Gradle task `embedAndSignAppleFrameworkForXcode` è standard KMP
- Legge variabili d'ambiente da Xcode (`CONFIGURATION`, `SDK_NAME`, `ARCHS`)
- Non inventare percorsi manuali: il task li conosce già

**Perché iOS 26.0?**
- Apple Intelligence richiede iOS 26+ (iPhone 15 Pro+)
- Non serve compatibilità vecchie versioni per questa app scolastica

---

**Prossimo comando utente**: `git push` per triggerare CI verde ✅
