package circolareplus.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import circolareplus.ai.AnalysisActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servizio in primo piano che tiene vivo il processo mentre il modello sul telefono analizza una
 * circolare, con la notifica "Analisi della circolare n. X" e il tasto Stop.
 *
 * Il lavoro vero non gira qui: gira in [AnalysisActivity.scope], nel codice condiviso. Senza
 * questo servizio pero' Android puo' chiudere il processo pochi secondi dopo che l'app va in
 * background, e un'analisi da un minuto andrebbe persa. Parte da solo quando comincia
 * un'analisi sul telefono e si ferma da solo quando la coda e' vuota (vedi [observe]).
 * Le analisi con Gemini durano pochi secondi e non lo avviano.
 */
class AnalysisForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Va chiamato subito: Android concede pochi secondi fra startForegroundService e
        // startForeground prima di chiudere l'app.
        startInForeground(AnalysisActivity.state.value)
        scope.launch {
            AnalysisActivity.state.collect { state ->
                if (!state.needsService()) {
                    ServiceCompat.stopForeground(this@AnalysisForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Si ferma l'analisi in corso e tutta la coda: chi preme Stop dalla notifica vuole
            // che il telefono smetta di lavorare, non passare alla circolare successiva.
            val state = AnalysisActivity.state.value
            (listOfNotNull(state.running) + state.queued).forEach { AnalysisActivity.requestStop(it) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(state: AnalysisActivity.State) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(state: AnalysisActivity.State): android.app.Notification {
        ensureChannel()
        val title = state.running?.let { "Analisi della circolare n. $it" } ?: "Analisi circolari in coda"
        val text = buildString {
            if (state.runningTitle.isNotBlank()) append(state.runningTitle)
            if (state.queued.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(if (state.queued.size == 1) "1 in coda" else "${state.queued.size} in coda")
            }
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AnalysisForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // L'icona di stato dell'app sta nel modulo :androidApp, non visibile da qui per nome.
        val smallIcon = resources.getIdentifier("ic_stat_aila", "drawable", packageName)
            .takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(smallIcon)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager().getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Analisi circolari", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostrata mentre l'AI sul telefono riassume una circolare."
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "aila_analysis"
        private const val NOTIFICATION_ID = 7_301
        private const val ACTION_STOP = "circolareplus.analysis.STOP"

        private fun AnalysisActivity.State.needsService(): Boolean =
            (running != null && onDevice) || queued.isNotEmpty()

        private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var observing = false

        /**
         * Avvia il servizio quando parte un'analisi sul telefono. Da chiamare una volta, in
         * `Application.onCreate`: il servizio poi si ferma da solo a coda vuota.
         */
        fun observe(context: Context) {
            if (observing) return
            observing = true
            val appContext = context.applicationContext
            observerScope.launch {
                AnalysisActivity.state
                    .map { it.needsService() }
                    .distinctUntilChanged()
                    .collect { needed ->
                        if (!needed) return@collect
                        try {
                            ContextCompat.startForegroundService(
                                appContext,
                                Intent(appContext, AnalysisForegroundService::class.java)
                            )
                        } catch (e: Exception) {
                            // Da Android 12 un servizio in primo piano non si avvia con l'app in
                            // background (ForegroundServiceStartNotAllowedException): l'analisi
                            // prosegue lo stesso, solo senza notifica finche' l'app e' viva.
                        }
                    }
            }
        }
    }
}
