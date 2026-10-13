package circolareplus.platform

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Cartella privata nella cache dell'app: l'APK scaricato resta li' finche' non si installa.
private const val UPDATES_DIR = "updates"
private const val APK_FILE = "AILA-update.apk"

private fun updatesDir(): File = File(AndroidAppContext.require().cacheDir, UPDATES_DIR).apply { mkdirs() }

private fun apkFile(): File = File(updatesDir(), APK_FILE)

actual fun isInAppUpdateSupported(): Boolean = AndroidAppContext.getOrNull() != null

actual fun canInstallPackages(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        AndroidAppContext.require().packageManager.canRequestPackageInstalls()

actual fun openInstallPermissionSettings() {
    val context = AndroidAppContext.require()
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        // Context dell'applicazione, non di un'Activity: senza NEW_TASK startActivity lancia.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

actual suspend fun downloadUpdateApk(
    url: String,
    expectedSize: Long,
    sha256: String?,
    onProgress: (downloaded: Long, total: Long) -> Unit
): Unit = withContext(Dispatchers.IO) {
    val target = apkFile()
    val partial = File(target.parentFile, "$APK_FILE.part")
    target.delete()
    partial.delete()

    var connection: HttpURLConnection? = null
    try {
        // GitHub risponde con un redirect verso il suo CDN (https -> https): HttpURLConnection lo
        // segue da solo.
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AILA-Android")
        }
        val code = connection.responseCode
        if (code !in 200..299) error("Il server ha risposto $code")
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
        val digest = if (sha256 != null) MessageDigest.getInstance("SHA-256") else null

        var downloaded = 0L
        var lastReported = 0L
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    downloaded += read
                    // Non a ogni blocco da 64 KB: per un APK da un centinaio di MB sarebbero
                    // migliaia di ridisegni. Un aggiornamento ogni ~256 KB basta a una barra fluida.
                    if (downloaded - lastReported >= 256 * 1024) {
                        lastReported = downloaded
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        onProgress(downloaded, total)

        if (expectedSize > 0 && downloaded != expectedSize) {
            error("Download incompleto ($downloaded di $expectedSize byte)")
        }
        if (digest != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256, ignoreCase = true)) error("Il file scaricato non corrisponde a quello pubblicato")
        }
        if (!partial.renameTo(target)) error("Impossibile salvare il file scaricato")
    } catch (e: Exception) {
        partial.delete()
        target.delete()
        throw e
    } finally {
        connection?.disconnect()
    }
}

actual fun isUpdateApkDownloaded(expectedSize: Long): Boolean {
    val file = apkFile()
    return file.isFile && file.length() > 0 && (expectedSize <= 0 || file.length() == expectedSize)
}

actual fun installDownloadedApk(): String? {
    val context = AndroidAppContext.require()
    val file = apkFile()
    if (!file.isFile) return "Il file scaricato non c'è più: riprova il download."
    return try {
        // PackageInstaller al posto di ACTION_VIEW: con ACTION_VIEW il sistema mostrava solo un
        // generico "impossibile installare" e l'app non sapeva il perche'. Qui l'esito torna ad
        // AppInstallReceiver, con il motivo.
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("AILA.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(context, AppInstallReceiver::class.java).setPackage(context.packageName)
            // Mutabile: e' il sistema ad aggiungere all'intent l'esito e la schermata di conferma.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(pending.intentSender)
        }
        null
    } catch (e: Exception) {
        e.message?.takeIf { it.isNotBlank() } ?: "Impossibile avviare l'installazione."
    }
}

actual fun clearDownloadedUpdate() {
    try {
        File(AndroidAppContext.require().cacheDir, UPDATES_DIR).deleteRecursively()
    } catch (e: Exception) {
        // Cache gia' sparita o non scrivibile: nessuna conseguenza.
    }
}
