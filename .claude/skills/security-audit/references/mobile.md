# App mobile e desktop

## Dati sul dispositivo
- Token, password, chiavi API: Keychain (iOS) / Keystore o EncryptedSharedPreferences/DataStore
  cifrato (Android), non file o preferenze in chiaro quando il rischio lo giustifica.
- Backup: Android `android:allowBackup` / `dataExtractionRules` / `fullBackupContent`;
  iOS `isExcludedFromBackup` per file sensibili.
- Log (`Log.d`, `println`, `NSLog`, `print`) senza token, password, dati personali.
- Cache di file/immagini/PDF con dati privati in directory dell'app, cancellate al logout.
- Schermate sensibili: valutare `FLAG_SECURE` / oscuramento nello switcher delle app.

## Rete
- Solo HTTPS: niente `usesCleartextTraffic="true"` né `NSAllowsArbitraryLoads` senza motivo;
  nessun `TrustManager`/`HostnameVerifier` che accetta tutto, nessun bypass SSL in release.
- URL e chiavi di ambienti di sviluppo non inclusi nella build di produzione.

## Componenti e link
- Android: `exported="true"` solo dove serve; intent filter e provider non espongono dati;
  `PendingIntent` con `FLAG_IMMUTABLE`.
- Deep link / universal link / URL scheme: trattati come input non fidato; non eseguono azioni
  (cancellare, votare, pagare, cambiare impostazioni) senza conferma dell'utente; nessun
  caricamento di URL arbitrari in WebView.
- WebView: JavaScript e `addJavascriptInterface` solo se servono, solo su contenuti propri;
  niente accesso ai file (`setAllowFileAccess`) senza motivo.

## Codice e build
- Nessun segreto di server nell'app (è estraibile dal pacchetto): chiavi di servizi con
  privilegi stanno sul backend.
- Build di release senza flag di debug (`debuggable`, menu sviluppatore, log verbosi).
- Notifiche push: il contenuto mostrato a schermo bloccato non rivela dati riservati.

## Desktop (Electron, Tauri e simili)
- Electron: `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`, preload minimo,
  `shell.openExternal` solo su URL validati, nessun `webSecurity: false`.
- Tauri: allowlist/permessi minimi, comandi esposti validano gli argomenti.
- Aggiornamenti automatici firmati e scaricati su HTTPS.
