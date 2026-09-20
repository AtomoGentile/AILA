package circolareplus.ai

import android.app.ActivityManager
import android.content.Context
import circolareplus.platform.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

// ---------------------------------------------------------------------------------------------
// Capacità del dispositivo
// ---------------------------------------------------------------------------------------------

actual fun totalDeviceRamMb(): Int {
    val context = AndroidAppContext.getOrNull() ?: return 0
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L)).toInt()
    } catch (e: Exception) {
        0
    }
}

actual fun isOnDeviceAiAvailable(): Boolean = onDeviceAiUnavailableReason() == null

actual fun onDeviceAiUnavailableReason(): String? {
    if (AndroidAppContext.getOrNull() == null) {
        return "AI locale non ancora inizializzata."
    }
    // Il motore nativo è compilato solo per arm64-v8a e armeabi-v7a nella pratica: su un
    // emulatore x86 senza la libreria giusta il caricamento fallirebbe con un errore illeggibile
    // (UnsatisfiedLinkError) al primo utilizzo. Meglio dirlo prima di far scaricare 2 GB.
    val supported = android.os.Build.SUPPORTED_ABIS.orEmpty()
    if (supported.none { it.startsWith("arm") }) {
        return "L'AI locale funziona solo su processori ARM: questo dispositivo è ${supported.firstOrNull() ?: "sconosciuto"}."
    }
    return null
}

// ---------------------------------------------------------------------------------------------
// Archivio dei modelli
// ---------------------------------------------------------------------------------------------

actual class LocalModelStore actual constructor() {

    private companion object {
        /**
         * Blocchi da 256 KB: abbastanza grandi da non fare una syscall ogni sospiro su un file da
         * 2 GB, abbastanza piccoli da aggiornare la barra di avanzamento con continuità.
         */
        const val BUFFER_BYTES = 256 * 1024

        /** Un download interrotto resta come `<nome>.part` finché non è completo. */
        const val PARTIAL_SUFFIX = ".part"

        /**
         * Margine di sicurezza sullo spazio libero. Android si comporta male quando la memoria
         * finisce del tutto, e comunque un modello scaricato a filo lascerebbe il telefono
         * inutilizzabile.
         */
        const val FREE_SPACE_MARGIN_BYTES = 300L * 1024 * 1024
    }

    /**
     * I modelli stanno in `filesDir/ai-models` e non nella cache: `cacheDir` può essere svuotata
     * dal sistema quando lo spazio scarseggia, e ritrovarsi cancellati 2 GB scaricati con la rete
     * della scuola sarebbe la cosa peggiore che possa capitare a questa funzione.
     */
    private fun modelsDir(): File {
        val dir = File(AndroidAppContext.require().filesDir, "ai-models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun modelFile(model: LocalAiModel) = File(modelsDir(), model.fileName)

    private fun partialFile(model: LocalAiModel) = File(modelsDir(), model.fileName + PARTIAL_SUFFIX)

    /**
     * `true` per le voci "di sistema" (oggi solo AICore): nessun file da scaricare, il segnale è
     * `downloadUrl` vuoto. Stesso criterio già usato lato iOS per Apple Intelligence.
     */
    private fun isSystemTier(model: LocalAiModel) = model.downloadUrl.isBlank()

    actual fun isInstalled(model: LocalAiModel): Boolean {
        if (isSystemTier(model)) return AiCoreEngine.isAvailable()
        val file = modelFile(model)
        // Solo l'esistenza non basta: un file troncato da un'installazione andata male
        // manderebbe il motore in errore. Si accetta una tolleranza del 5% perché la dimensione
        // nel catalogo è approssimata a mano.
        return file.isFile && file.length() >= (model.approxSizeBytes * 95L / 100L)
    }

    actual fun installedPath(model: LocalAiModel): String? {
        if (isSystemTier(model)) return if (isInstalled(model)) model.fileName else null
        return if (isInstalled(model)) modelFile(model).absolutePath else null
    }

    actual fun partialBytes(model: LocalAiModel): Long {
        val partial = partialFile(model)
        return if (partial.isFile) partial.length() else 0L
    }

    actual fun freeSpaceBytes(): Long = try {
        modelsDir().usableSpace
    } catch (e: Exception) {
        0L
    }

    actual fun delete(model: LocalAiModel): Boolean {
        val deletedModel = modelFile(model).let { it.isFile && it.delete() }
        val deletedPartial = partialFile(model).let { it.isFile && it.delete() }
        return deletedModel || deletedPartial
    }

    actual fun installedModels(): List<LocalAiModel> =
        LocalAiCatalog.all.filter { isInstalled(it) }

    /**
     * I file nella cartella dei modelli che non corrispondono a nessuna voce del catalogo.
     *
     * Nascono quando il catalogo cambia: chi aveva scaricato un modello poi rimosso — i build
     * solo-GPU, per dirne una, tolti perche' non si avviavano — si ritroverebbe qualche giga
     * occupato da un file che nessuna schermata mostra piu' e che quindi non potrebbe nemmeno
     * cancellare dall'app.
     */
    private fun orphanFiles(): List<File> {
        val known = LocalAiCatalog.knownFileNames()
        return modelsDir().listFiles().orEmpty().filter { file ->
            file.isFile && file.name.removeSuffix(PARTIAL_SUFFIX) !in known
        }
    }

    actual fun orphanBytes(): Long = try {
        orphanFiles().sumOf { it.length() }
    } catch (e: Exception) {
        0L
    }

    actual fun deleteOrphans(): Long = try {
        orphanFiles().sumOf { file ->
            val size = file.length()
            if (file.delete()) size else 0L
        }
    } catch (e: Exception) {
        0L
    }

    /**
     * Punto d'ingresso usato da tutta l'app (Impostazioni, anticipo, ecc.): non scarica più in
     * linea nella coroutine di chi chiama, ma passa da [circolareplus.work.LocalModelDownloadWorker]
     * tramite WorkManager. La differenza si vede quando si esce dalle Impostazioni o l'app va in
     * background durante un download da qualche giga: prima la coroutine legata alla schermata
     * veniva cancellata e il download si fermava lì; ora il lavoro vero gira in un Worker
     * indipendente dal ciclo di vita della UI, e questa funzione si limita a osservarlo — se chi
     * la chiama smette di aspettare, il download prosegue comunque fino alla fine.
     */
    actual suspend fun download(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState {
        // Le voci "di sistema" (AICore) non hanno un file nostro da scaricare: il tasto
        // "Scarica" delle Impostazioni avvia direttamente la preparazione del motore AICore
        // (che scarica il modello di sistema se serve). Senza questo salto finirebbe per aprire
        // una connessione HTTP verso downloadUrl = "" tramite WorkManager.
        if (isSystemTier(model)) {
            return if (AiCoreEngine.prepare(maxOutputTokens = model.maxOutputTokens, onProgress = onProgress)) {
                ModelDownloadState.Installed(model.fileName)
            } else {
                ModelDownloadState.Failed(AiCoreEngine.unavailableReason())
            }
        }
        return circolareplus.work.LocalModelDownloadWorker.downloadViaWorkManager(
            context = AndroidAppContext.require(),
            model = model,
            onProgress = onProgress
        )
    }

    /**
     * Il download vero, byte per byte: eseguito dentro [circolareplus.work.LocalModelDownloadWorker],
     * non più da [download] direttamente. `internal` (non `private`) perché il Worker vive in un
     * altro package dello stesso modulo.
     */
    internal suspend fun downloadRaw(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState = withContext(Dispatchers.IO) {
        val target = modelFile(model)
        if (isInstalled(model)) return@withContext ModelDownloadState.Installed(target.absolutePath)

        val partial = partialFile(model)
        var alreadyDownloaded = if (partial.isFile) partial.length() else 0L

        val needed = model.approxSizeBytes - alreadyDownloaded + FREE_SPACE_MARGIN_BYTES
        if (freeSpaceBytes() < needed) {
            return@withContext ModelDownloadState.Failed(
                "Spazio insufficiente: servono circa ${model.readableSize} liberi."
            )
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                // HuggingFace rimanda a un CDN su un host diverso: senza questo il download si
                // fermerebbe sulla risposta 302 invece di seguirla.
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                // Ripresa: si chiedono solo i byte mancanti. Se il server non supporta le
                // richieste parziali risponde 200 con tutto il file e si riparte da zero.
                if (alreadyDownloaded > 0) setRequestProperty("Range", "bytes=$alreadyDownloaded-")
            }
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext ModelDownloadState.Failed(
                    "Il server ha risposto HTTP $status durante il download di ${model.displayName}."
                )
            }

            val resuming = status == HttpURLConnection.HTTP_PARTIAL
            if (!resuming) alreadyDownloaded = 0L

            val remaining = connection.contentLengthLong.takeIf { it > 0 } ?: 0L
            val total = if (remaining > 0) alreadyDownloaded + remaining else model.approxSizeBytes

            var downloaded = alreadyDownloaded
            onProgress(downloaded, total)

            connection.inputStream.use { input ->
                FileOutputStream(partial, resuming).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        // Il download di 2 GB può durare parecchi minuti: senza questo controllo,
                        // uscire dalle Impostazioni non lo fermerebbe e continuerebbe a
                        // consumare dati in sottofondo.
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }

            // Il rename avviene solo a download finito: è ciò che rende `isInstalled` una verità
            // e non una speranza — un file col nome definitivo è per costruzione completo.
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                return@withContext ModelDownloadState.Failed(
                    "Download completato ma non è stato possibile salvare il file."
                )
            }
            ModelDownloadState.Installed(target.absolutePath)
        } catch (e: Exception) {
            // Il parziale NON viene cancellato: è esattamente ciò che permette di riprendere.
            ModelDownloadState.Failed(
                "Download interrotto: ${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
