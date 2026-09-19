# iOS App Project (AILA)

Questo progetto Xcode è **generato da `project.yml`** tramite [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## ⚠️ Non editare `iosApp.xcodeproj` a mano

Se il progetto `.xcodeproj` esiste, è un artefatto generato. Le modifiche fatte all'interno di Xcode su quel file (.pbxproj binario) andranno perse alla prossima rigenerazione.

Ogni configurazione deve passare da `project.yml`:

1. **Modifica** `project.yml`
2. Rigenera il progetto:
   ```bash
   cd iosApp
   xcodegen generate
   ```
3. Usa il nuovo `.xcodeproj` in Xcode

## Build e Test

### Con XcodeGen locale (Mac):
```bash
brew install xcodegen
cd iosApp
xcodegen generate
open iosApp.xcodeproj
```

### Con CI (GitHub Actions):
La CI genera e builda automaticamente a ogni push su `shared/`, `iosApp/`, o il workflow stesso.
Vedi `.github/workflows/ios-build.yml`.

## Struttura

- `project.yml` — Configurazione XcodeGen (unica fonte di verità)
- `iosApp/` — Sorgenti Swift
  - `iOSApp.swift` — Entry point SwiftUI
  - `Info.plist` — Configurazione app (sorgente manuale, referenziato da project.yml)
  - `Assets.xcassets/` — Asset bundle (icone, immagini, ecc.)
- `iosApp.xcodeproj` — Progetto Xcode generato da XcodeGen (non committare modifiche a mano)
