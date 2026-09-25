package circolareplus.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import circolareplus.ai.LocalAiCatalog
import circolareplus.ai.LocalAiModel
import circolareplus.ai.LocalModelStore
import circolareplus.ai.ModelDownloadState
import kotlinx.coroutines.flow.first

/**
 * Scarica un modello di AI locale in un Worker di WorkManager invece che in una coroutine legata
 * alla schermata Impostazioni.
 *
 * Perché serve (punto 7 dei problemi segnalati): un modello pesa da 1 a 4 GB, e prima il download
 * girava dentro la coroutine della composable delle Impostazioni. Uscire dalla schermata,
 * cambiare tab o mettere l'app in background la cancellava (o rischiava di farlo appena Android
 * decideva di liberare memoria), perdendo il lavoro fatto fino a lì — il file `.part` restava sul
 * disco, ma bisognava tornare apposta nelle Impostazioni e restare lì per farlo ripartire. Con
 * WorkManager il download prosegue anche se l'utente esce dall'app, con una notifica che mostra
 * l'avanzamento.
 */
class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Nessun modello indicato.")
        )
        val model = LocalAiCatalog.byId(modelId) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Modello sconosciuto: $modelId")
        )

        setForeground(foregroundInfo(model, 0L, model.approxSizeBytes))

        // Le notifiche piu' frequenti dei singoli blocchi da 256 KB martellerebbero il sistema
        // di aggiornamenti (un download da 2 GB sono circa 8.000 blocchi): si aggiorna la barra
        // di avanzamento di WorkManager a ogni blocco (e' economico, resta in memoria) ma la
        // notifica visibile solo ogni mezzo secondo abbondante.
        var lastNotificationUpdateMs = 0L
        val result = LocalModelStore().downloadRaw(model) { downloaded, total ->
            setProgressAsync(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateMs > 500L) {
                lastNotificationUpdateMs = now
                notificationManager().notify(model.notificationId, buildNotification(model, downloaded, total))
            }
        }

        return when (result) {
            is ModelDownloadState.Installed -> {
                notificationManager().cancel(model.notificationId)
                Result.success(workDataOf(KEY_PATH to result.path))
            }
            is ModelDownloadState.Failed -> {
                notificationManager().cancel(model.notificationId)
                Result.failure(workDataOf(KEY_ERROR to result.reason))
            }
            ModelDownloadState.Idle -> Result.failure(workDataOf(KEY_ERROR to "Stato inatteso."))
            is ModelDownloadState.InProgress -> Result.failure(workDataOf(KEY_ERROR to "Stato inatteso."))
        }
    }

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager().getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download modello AI",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Avanzamento del download del modello di AI locale."
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(model: LocalAiModel, downloaded: Long, total: Long): android.app.Notification {
        ensureChannel()
        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Scaricando ${model.displayName}")
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Si ferma anche dalla tendina, senza dover riaprire l'app e cercare le Impostazioni.
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Interrompi",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()
    }

    private fun foregroundInfo(model: LocalAiModel, downloaded: Long, total: Long): ForegroundInfo {
        val notification = buildNotification(model, downloaded, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                model.notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(model.notificationId, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ai_model_download"
        private const val KEY_MODEL_ID = "modelId"
        private const val KEY_DOWNLOADED = "downloadedBytes"
        private const val KEY_TOTAL = "totalBytes"
        private const val KEY_PATH = "path"
        private const val KEY_ERROR = "error"

        /** Un id di notifica stabile per modello, cosi' due download diversi non si sovrascrivono. */
        private val LocalAiModel.notificationId: Int get() = 9000 + id.hashCode() % 1000

        private fun uniqueWorkName(modelId: String) = "download-model-$modelId"

        /**
         * Accoda (o riusa, se già in corso) il download di [model] e resta in ascolto finché non
         * finisce, riportando l'avanzamento su [onProgress] — la stessa firma che aveva
         * [circolareplus.ai.LocalModelStore.download] quando scaricava in linea, cosi' tutte le
         * chiamate esistenti restano invariate.
         *
         * Se la coroutine chiamante viene cancellata (schermata chiusa, app in background) si
         * smette solo di ASCOLTARE: il Worker accodato in WorkManager continua fino alla fine per
         * conto suo, che è esattamente il punto di usare WorkManager invece di scaricare in linea.
         */
        suspend fun downloadViaWorkManager(
            context: Context,
            model: LocalAiModel,
            onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
        ): ModelDownloadState {
            val workManager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to model.id))
                .build()
            val workName = uniqueWorkName(model.id)
            // KEEP: se un download per questo modello è già in corso (avviato da un'altra
            // schermata, o mai fermato dopo che l'utente ha lasciato le Impostazioni), ci si
            // aggancia a quello invece di accodarne un secondo che scaricherebbe lo stesso file.
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)

            var lastReported = -1L
            var finalInfo: WorkInfo? = null
            workManager.getWorkInfosForUniqueWorkFlow(workName).first { infos ->
                val info = infos.firstOrNull() ?: return@first false
                val progress = info.progress
                val downloaded = progress.getLong(KEY_DOWNLOADED, -1L)
                if (downloaded >= 0 && downloaded != lastReported) {
                    lastReported = downloaded
                    onProgress(downloaded, progress.getLong(KEY_TOTAL, model.approxSizeBytes))
                }
                if (info.state.isFinished) finalInfo = info
                info.state.isFinished
            }
            val info = finalInfo ?: return ModelDownloadState.Failed("Stato del download non disponibile.")

            return when (info.state) {
                WorkInfo.State.SUCCEEDED -> ModelDownloadState.Installed(
                    info.outputData.getString(KEY_PATH)
                        ?: LocalModelStore().installedPath(model)
                        ?: return ModelDownloadState.Failed("Download completato ma percorso mancante.")
                )
                WorkInfo.State.CANCELLED -> ModelDownloadState.Failed("Download annullato.")
                else -> ModelDownloadState.Failed(
                    info.outputData.getString(KEY_ERROR) ?: "Download non riuscito."
                )
            }
        }

        /**
         * Annulla un download in corso per questo modello — usato dai tasti "Annulla download"
         * (tramite [LocalModelStore.cancelDownload]).
         */
        fun cancel(context: Context, model: LocalAiModel) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(model.id))
            // Il Worker aggiorna la notifica a ogni blocco e la toglie solo a fine lavoro: nei
            // pochi istanti fino a che vede la cancellazione potrebbe ripubblicarla, quindi la
            // si toglie anche da qui (il servizio in primo piano la rimuove comunque quando si ferma).
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(model.notificationId)
        }
    }
}
