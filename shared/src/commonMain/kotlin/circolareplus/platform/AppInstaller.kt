package circolareplus.platform

/**
 * Aggiornamento dell'app dall'interno, senza passare dal browser.
 *
 * Solo Android: li' l'APK della release di GitHub si scarica e lo installa il sistema sopra
 * l'app presente (stessa firma, vedi android-build.yml). Su iPhone/iPad gli aggiornamenti li
 * porta SideStore dalla source dell'app, e la PWA si aggiorna da sola: le altre piattaforme
 * rispondono "non supportato" e non mostrano nulla.
 */
expect fun isInAppUpdateSupported(): Boolean

/** Vero se l'utente ha consentito a AILA di installare app (Android 8+: permesso per singola app). */
expect fun canInstallPackages(): Boolean

/** Apre la schermata di sistema in cui consentire a AILA di installare app da fonti sconosciute. */
expect fun openInstallPermissionSettings()

/**
 * Scarica l'APK in una cartella privata dell'app. [onProgress] riceve (byte scaricati, byte totali;
 * 0 se il totale non e' noto). Se [sha256] e' indicato, il file scaricato deve corrispondere.
 * Lancia un'eccezione con un messaggio leggibile se qualcosa va storto; il file parziale si elimina.
 */
expect suspend fun downloadUpdateApk(
    url: String,
    expectedSize: Long,
    sha256: String?,
    onProgress: (downloaded: Long, total: Long) -> Unit
)

/** Vero se un APK gia' scaricato e completo e' pronto da installare. */
expect fun isUpdateApkDownloaded(expectedSize: Long): Boolean

/** Apre l'installazione di sistema sull'APK scaricato. Falso se non e' stato possibile avviarla. */
expect fun installDownloadedApk(): Boolean

/** Elimina l'APK scaricato (dopo l'installazione, o quando non serve piu'). */
expect fun clearDownloadedUpdate()
