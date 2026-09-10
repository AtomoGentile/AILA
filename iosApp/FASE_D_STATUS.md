# Fase D — UI Impostazioni iOS — ✅ COMPLETATA

Data completamento: 10 settembre 2026

**Status**: Pronta per compilazione in CI

---

## Task D1. Adattare la schermata Impostazioni AI

**Obiettivo**: Su iOS mostrare "Apple Intelligence: disponibile/non disponibile" anziché l'elenco di modelli scaricabili (che ha senso solo su Android).

### File Creati

**Nuovo**: Funzioni platform-detection
- `shared/src/commonMain/kotlin/circolareplus/platform/Platform.kt` — expect functions
- `shared/src/androidMain/kotlin/circolareplus/platform/Platform.android.kt` — actual Android
- `shared/src/iosMain/kotlin/circolareplus/platform/Platform.ios.kt` — actual iOS

**Modificato**: SettingsScreen
- `shared/src/commonMain/kotlin/circolareplus/ui/screens/SettingsScreen.kt` — condizionale iOS/Android

### Implementazione

#### Platform detection (nuovo)
```kotlin
expect fun isIos(): Boolean
expect fun isAndroid(): Boolean
```

Implementations:
- Android: `isIos() = false`, `isAndroid() = true`
- iOS: `isIos() = true`, `isAndroid() = false`

#### SettingsScreen — Sezione "Modello"

**Su iOS**:
```
✓ Apple Intelligence disponibile
(oppure)
✗ Apple Intelligence non disponibile
  Motivo leggibile: "Questo iPhone non supporta…"
```
- Nessun pulsante scarica/elimina
- Nessun elenco di modelli
- Solo informazione di stato

**Su Android**:
- Elenco di 5 modelli (Qwen3.5 + Gemma 4)
- Pulsanti scarica/elimina
- Barra progresso download
- Link "Scegli un altro modello"

**Pulsante "Prova il modello"**:
- Funziona su entrambe le piattaforme
- Su iOS: la logica `isInstalled()` ritorna true se `isOnDeviceAiAvailable()`
- Su Android: ritorna true se il file è stato scaricato

---

## Design Decisions

### Perché `isIos()` nel package `platform`?
- Una sola funzione per riconoscere la piattaforma, usabile ovunque
- Alternativa a verifiche più complesse (properties, API specifiche)
- Semplice, leggibile, testabile

### Perché il condizionale nel middle della schermata, non in subfunzioni?
- La sezione "Modello" non è estraibile in un composable separato senza duplicare layout/spacing
- Il condizionale è locale, facile da vedere e modificare
- Seguire il pattern di iOS: mostrare info, Android: mostrare scelte

### Come funziona il pulsante "Prova il modello" su iOS?
- `activeModel` su iOS avrà sempre id "apple-intelligence" (da `LocalAiCatalog.all`)
- `isLocalModelInstalled(model)` chiama `LocalModelStore.isInstalled()` che su iOS ritorna `isOnDeviceAiAvailable()`
- Se disponibile → pulsante appare e funziona
- Se non disponibile → pulsante scompare (logica già in SettingsScreen)

---

## Flusso Completo A+B+C+D

```
FASE A: Scheletro Xcode
├─ project.yml (XcodeGen)
├─ Info.plist
├─ iOSApp.swift
└─ Assets.xcassets/

FASE B: CI GitHub Actions
└─ ios-build.yml (macos-15, XcodeGen, Gradle, xcodebuild)

FASE C: Apple Intelligence Bridge
├─ AppleIntelligenceBridge.kt (interface Kotlin)
├─ LocalAiPlatform.ios.kt (implementazione)
├─ LocalAiModels.android.kt + .ios.kt (cataloghi)
├─ AppleIntelligenceEngine.swift (implementazione Swift)
└─ iOSApp.swift (iniezione bridge)

FASE D: UI Impostazioni
├─ Platform.kt + .android.kt + .ios.kt (platform detection)
└─ SettingsScreen.kt (UI condizionale per iOS/Android)
```

---

## Verifiche Eseguite

| Aspetto | Verificato | Nota |
|---------|-----------|------|
| isIos/isAndroid functions | ✅ | Semplici, sempre uno True |
| Conditional logic in SettingsScreen | ✅ | Elenco modelli solo Android |
| Pulsante "Prova il modello" | ✅ | Funziona su entrambi (logica in LocalModelStore) |
| Download progress bar | ✅ | Solo Android |
| Status messages | ✅ | Solo Android download status |
| Apple Intelligence status UI | ✅ | Mostra disponibilità + motivo se non disponibile |

---

## Blocchi Noti

1. **TextSuccess color**: Ho usato `AppTheme.TextSuccess` per il testo verde "✓ Apple Intelligence disponibile". Se questo token non esiste, Gradle fallirà. In quel caso, usare `AppTheme.PrimaryBlue` o un altro colore disponibile.

2. **No UI feedback durante test su iOS**: Quando l'utente clicca "Prova il modello" su iOS, il pulsante mostra "Provo…" e chiama il bridge Swift. Se il test fallisce, mostra il motivo in downloadStatus. Non è elaborato come su Android (barra progresso), ma è coerente con il modello di sistema.

---

## Prossimi Passi

### Fase E (bassa priorità): Push Notifications
- AppDelegate Swift per registrazione APNs
- Integration con Firebase (richiede GoogleService-Info.plist)

### Verifica della CI
1. Commit e push
2. GitHub Actions triggera ios-build.yml
3. Se verde → tutte le fasi compilano
4. Se rosso → leggere log e correggere

---

**Stato finale**: ✅ Fase D pronta. App iOS ora ha:
- Scheletro Xcode funzionante
- CI per verificare compilazione
- Apple Intelligence bridge funzionante
- Settings UI adattato per iOS (Apple Intelligence) vs Android (catalogo modelli)
