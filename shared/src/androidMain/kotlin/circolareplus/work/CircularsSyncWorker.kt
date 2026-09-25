package circolareplus.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Il giro di [BackgroundCircularsSync] (codice condiviso con iOS) dentro WorkManager: ogni 15
 * minuti quando c'e' rete, e subito dopo il push di una circolare nuova ([runOnce]).
 *
 * Prima questo lavoro girava solo dentro un `LaunchedEffect` di `MainAppShell`, legato al
 * lifecycle della composable: se l'utente chiudeva l'app o Android la metteva in background,
 * il ciclo si fermava e riprendeva solo alla riapertura. Questo Worker copre l'intervallo in mezzo.
 */
class CircularsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            BackgroundCircularsSync.run(MAX_CIRCULARS_PER_RUN) { isStopped }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Elenco circolari irraggiungibile (rete assente, server giù): ha senso riprovare al
            // giro periodico successivo invece di segnare il Worker come fallito in modo definitivo.
            Result.retry()
        }
    }

    companion object {
        /** Tetto per esecuzione, vedi [BackgroundCircularsSync.run]. */
        private const val MAX_CIRCULARS_PER_RUN = 8

        private const val UNIQUE_PERIODIC_NAME = "circulars-sync-periodic"
        private const val UNIQUE_ONE_TIME_NAME = "circulars-sync-once"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Da chiamare una volta all'avvio dell'app (es. in Application.onCreate): schedula il
         * controllo periodico. `KEEP` perché richiamarla a ogni avvio dell'app non deve resettare
         * il timer di un ciclo già schedulato.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CircularsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Un giro immediato, chiamato da CircolareMessagingService quando arriva il push di una
         * circolare nuova: non aspetta i 15 minuti del periodico, cosi' il riassunto e' spesso
         * gia' pronto quando l'utente tocca la notifica.
         */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<CircularsSyncWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
