package circolareplus.platform

// Nessun aggiornamento dall'interno su questa piattaforma (vedi AppInstaller.kt): su iPhone e
// iPad lo porta SideStore, la PWA si aggiorna da sola col service worker.
actual fun isInAppUpdateSupported(): Boolean = false

actual fun canInstallPackages(): Boolean = false

actual fun openInstallPermissionSettings() {}

actual suspend fun downloadUpdateApk(
    url: String,
    expectedSize: Long,
    sha256: String?,
    onProgress: (downloaded: Long, total: Long) -> Unit
) {
    error("Aggiornamento dall'app non disponibile su questa piattaforma")
}

actual fun isUpdateApkDownloaded(expectedSize: Long): Boolean = false

actual fun installDownloadedApk(): Boolean = false

actual fun clearDownloadedUpdate() {}
