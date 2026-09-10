# Attivare le notifiche push (Firebase Cloud Messaging)

Tutto il codice per le notifiche push è già scritto e pronto: l'app compila e funziona
normalmente anche senza Firebase, semplicemente senza inviare/ricevere notifiche. Quando vorrai
attivarle, questi sono gli unici passaggi che mancano — nessuno richiede di riscrivere codice.

## 1. Crea il progetto Firebase

1. Vai su [Firebase Console](https://console.firebase.google.com/) → **Aggiungi progetto**.
2. Dagli un nome (es. "Circolare Plus"). Google Analytics è facoltativo, puoi disattivarlo.
3. Nel progetto, aggiungi un'app Android:
   - **Nome pacchetto Android**: `com.circolareplus` (deve combaciare esattamente con
     `applicationId` in `androidApp/build.gradle.kts`).
   - Scarica il file **`google-services.json`** che ti propone e mettilo in
     `androidApp/google-services.json` (stessa cartella di `androidApp/build.gradle.kts`).
4. Aggiungi anche un'app iOS con bundle ID `com.circolareplus` e scarica
   `GoogleService-Info.plist`: va messo in `iosApp/iosApp/GoogleService-Info.plist` (stessa
   cartella di `iOSApp.swift`). È nel `.gitignore` come `google-services.json`: resta locale,
   non va committato.

## 2. Attiva il plugin Android

In `androidApp/build.gradle.kts`, nel blocco `plugins { ... }`, togli il commento dalla riga:

```kotlin
alias(libs.plugins.googleServices)
```

Questa riga è già presente ma commentata apposta: applicarla prima di avere
`google-services.json` avrebbe fatto fallire subito la build.

## 3. Genera il Service Account per l'invio (lato backend)

Le notifiche vengono INVIATE dal Worker Cloudflare, che deve autenticarsi con Google tramite un
Service Account (la vecchia "Server Key" non esiste più dal 2024, non serve cercarla):

1. Firebase Console → icona ingranaggio → **Impostazioni progetto** → scheda **Account di servizio**.
2. Clicca **Genera nuova chiave privata** → scarica il file JSON.
3. Da terminale, nella cartella `backend/`, imposta i due secret del Worker:

   ```bash
   npx wrangler secret put FCM_PROJECT_ID
   # incolla l'ID del progetto Firebase (lo trovi nella stessa pagina, es. "circolare-plus-xxxxx")

   npx wrangler secret put FCM_SERVICE_ACCOUNT_KEY
   # incolla l'INTERO contenuto del file JSON scaricato al punto 2 (tutto su una riga va bene)
   ```

4. Ridistribuisci il Worker (`npx wrangler deploy`) perché legga i nuovi secret.

Non serve nessun'altra modifica: `backend/src/services/fcm.ts` usa già la HTTP v1 API di FCM
(l'unica ancora supportata da Google) e resta silenziosamente disattivato finché questi due
secret non sono impostati — nessun errore, nessuna notifica.

## 4. iOS — già cablato, basta il file al punto 1.4

A differenza di Android, su iOS la registrazione alle notifiche remote (APNs) passa
dall'AppDelegate nativo in Swift prima che Firebase possa restituire un token — non è
raggiungibile da Kotlin condiviso puro. È già tutto scritto:

- `iosApp/iosApp/AppDelegate.swift` chiama `FirebaseApp.configure()` e registra il device alle
  notifiche remote, ma **solo se** `GoogleService-Info.plist` è presente nel bundle — altrimenti
  salta silenziosamente questi passaggi (l'app resta utilizzabile, semplicemente senza notifiche,
  esattamente come oggi).
- `iosApp/iosApp/FirebasePushTokenBridge.swift` espone il token FCM (via
  `Messaging.messaging().token(completion:)`) a
  `shared/src/iosMain/kotlin/circolareplus/push/PushTokenProvider.ios.kt`, con lo stesso pattern
  di bridge Kotlin↔Swift già usato per l'AI locale (`AppleIntelligenceBridge`).
- `iosApp/project.yml` dichiara Firebase (`FirebaseMessaging` + `FirebaseCore`) come pacchetto
  Swift Package Manager: nessun CocoaPods, nessun `.xcworkspace` da gestire a parte.

Con `GoogleService-Info.plist` al suo posto (punto 1.4), non serve altro: la prima build Xcode
scarica il pacchetto Firebase da SPM da sola.

## Come verificare che funzioni

Dopo i passaggi 1-3, fai login nell'app (Android o iOS): al primo avvio registra automaticamente
il token su `/api/fcm/token`. Da rappresentante, pubblica una nuova mappa posti o apri una
proposta in bacheca: dovrebbe arrivare una notifica push a tutta la classe (vedi le chiamate a
`notifyClass(...)` in `backend/src/routes/*.ts`).
