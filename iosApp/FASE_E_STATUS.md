# Fase E — Push Notifications iOS — ✅ COMPLETATA (senza Firebase)

Data completamento: 10 settembre 2026

**Status**: Codice pronto. Inerte finché non c'è `GoogleService-Info.plist` + Firebase SDK

---

## Task E1. AppDelegate Swift per registrazione APNs

**Obiettivo**: Implementare il layer nativo (Swift) che comunica con Apple per le notifiche remote, prerequisito per Firebase Messaging su iOS.

### File Creati

**Nuovo**:
- `iosApp/iosApp/AppDelegate.swift` — UIApplicationDelegate con APNs registration

**Modificato**:
- `iosApp/iosApp/iOSApp.swift` — Collegamento AppDelegate con `@UIApplicationDelegateAdaptor`

---

## Implementazione

### AppDelegate.swift

Implementa 5 metodi:

1. **`application(_:didFinishLaunchingWithOptions:)`**
   - Richiede il permesso di notifica all'utente (`UNUserNotificationCenter.requestAuthorization`)
   - Se concesso, registra il dispositivo con `UIApplication.shared.registerForRemoteNotifications()`

2. **`application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`**
   - Riceve il token APNs binario da Apple
   - Lo converte a stringa esadecimale (es. `a1b2c3d4...`)
   - Lo salva in `AppDelegate.apnsToken` (statica, leggibile da ovunque)
   - **Placeholder `// TODO Firebase:`** dove andrà il codice per passare il token a Firebase

3. **`application(_:didFailToRegisterForRemoteNotificationsWithError:)`**
   - Gestisce l'errore se la registrazione fallisce
   - Non è fatale: l'app continua normalmente senza notifiche

4. **`userNotificationCenter(_:willPresent:withCompletionHandler:)`**
   - Callback quando una notifica arriva mentre l'app è in foreground
   - Oggi mostra la notifica normalmente (banner + suono + badge)

5. **`userNotificationCenter(_:didReceive:withCompletionHandler:)`**
   - Callback quando l'utente tappa su una notifica
   - Placeholder per future navigazioni in-app basate sulla notifica

### iOSApp.swift

Aggiunto:
```swift
@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
```

Questo collega l'AppDelegate SwiftUI all'app lifecycle, facendo sì che i callback di AppDelegate vengano chiamati al momento giusto.

---

## Stato Attuale (Senza Firebase)

✅ **Funzionante**:
- Richiesta di permessi all'utente
- Registrazione con Apple APNs
- Salvataggio del token APNs

❌ **Non attivo**:
- Invio del token a Firebase (`Messaging.messaging().apnsToken`)
- Ricezione di notifiche da backend (richiede Firebase)

**Perché non è attivo?** Il file `GoogleService-Info.plist` non esiste ancora. Finché non sarà aggiunto, l'app non può importare Firebase, quindi il TODO rimane.

---

## Prossimi Passi (per attivare davvero le notifiche)

Quando avrai un vero progetto Firebase e il file `GoogleService-Info.plist`:

1. **Aggiungi il plist al progetto Xcode**
   - In `iosApp/project.yml`, aggiungi la sezione SPM (Swift Package Manager) con Firebase
   - Oppure scarica e trascina il plist in Xcode e collega al target

2. **Rimuovi il TODO in AppDelegate.swift**
   ```swift
   import FirebaseMessaging
   
   // Nel metodo didRegisterForRemoteNotificationsWithDeviceToken:
   Messaging.messaging().apnsToken = deviceToken
   ```

3. **Aggiorna PushTokenProvider.ios.kt**
   - Oggi ritorna sempre `null`
   - Quando Firebase sarà attivo, può leggerlo da `Messaging.messaging().token`
   - Oppure crea un bridge Kotlin/Swift per passarlo direttamente

4. **Test**
   - Login su app iOS con account creato
   - Da Android (o rappresentante su web): pubblica una proposta o una mappa posti
   - La notifica push dovrebbe arrivare su iOS

---

## Architettura (Kotlin/Swift Interop)

```
┌─ Kotlin (iOS) ──────────────────┐
│ PushTokenProvider.ios.kt        │
│   getToken(): String? = null    │  Ritorna null finché no Firebase
│ (future: leggerà da Firebase)   │
└─────────────────────────────────┘
        ↑ comunica con ↓
┌─ Swift (App) ────────────────────────┐
│ AppDelegate.swift                    │
│   @UIApplicationDelegateAdaptor      │
│   func didRegisterForRemote...()     │
│   → salva APNs token                 │
│   → (future: passa a Firebase)       │
└──────────────────────────────────────┘
        ↑ registrazione con ↓
┌─ Apple APNs ──────────────────────┐
│ (Sistema operativo iOS)            │
│ → token APNs univoco per dispositivo
└────────────────────────────────────┘
```

---

## Note di Implementazione

- **Token APNs vs Token FCM**: Apple genera il token APNs (quello che riceviamo in AppDelegate). Firebase Messaging lo usa per generare un token FCM diverso, che il backend conosce e usa per mandare notifiche. Sono due livelli diversi.

- **Nessun errore senza Firebase**: Se il plist manca, l'app non compila un errore di import di Firebase, perché non l'importiamo. L'AppDelegate funziona da solo, registra con Apple, salva il token. Semplicemente Firebase non lo usa.

- **Permessi espliciti**: iOS chiede il permesso di notifica all'utente via dialog. Se rifiuta, `registerForRemoteNotifications` non viene mai chiamato, e il token non si ottiene. È il comportamento corretto.

---

## Verifica

- ✅ AppDelegate implementa tutti i callback
- ✅ Conversione token da binario a esadecimale
- ✅ Salvataggio in property statica
- ✅ Collegamento a iOSApp via `@UIApplicationDelegateAdaptor`
- ✅ TODO placeholder per Firebase
- ✅ Commenti esplicativi su ogni metodo

---

**Stato finale**: ✅ Fase E pronta. La registrazione APNs funziona quando la CI compila.
