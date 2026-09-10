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
import circolareplus.data.AppContainer
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Scarica e classifica in background le circolari rimaste indietro, senza dipendere da una
 * schermata aperta.
 *
 * Prima questo lavoro girava solo dentro un `LaunchedEffect` di `MainAppShell`, legato al
 * lifecycle della composable: se l'utente chiudeva l'app o Android la metteva in background,
 * il ciclo si fermava e riprendeva daccapo (dal punto dove era rimasto, perché il progresso è
 * comunque salvato via [circolareplus.data.repository.CircularsRepository.saveAnalysis]) solo
 * alla riapertura. Questo Worker copre l'intervallo in mezzo.
 *
 * Non prova l'AI locale se non è il provider primario dello studente: se il primario è il cloud e
 * fallisce, qui NON si ricade sul modello on-device (`allowLocalFallback = false`) — farlo in un
 * Worker periodico senza che l'utente lo veda scaldava il telefono in tasca senza nessun
 * indicatore, esattamente il problema del punto 2. La circolare che il cloud non riesce a
 * classificare resta per l'apertura manuale, dove l'attesa del modello locale è accettata perché
 * è l'utente a chiederla.
 */
class CircularsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Il motore locale è mutex-single-thread: se e' il provider scelto, va usato una
        // circolare alla volta quando l'utente la apre, non in coda in un Worker che il telefono
        // può far partire mentre e' in tasca (stesso motivo per cui MainAppShell disattiva il suo
        // ciclo in background in questo caso — vedi il commento su isUsingLocalAiFirst()).
        if (AppContainer.isUsingLocalAiFirst()) return Result.success()

        return try {
            val circulars = AppContainer.circularsRepository.listCirculars(limit = 30)
            var processed = 0
            for (circular in circulars.sortedByDescending { it.number }) {
                if (isStopped || processed >= MAX_CIRCULARS_PER_RUN) break
                try {
                    if (AppContainer.circularsRepository.getCachedAnalysis(circular.number) != null) continue

                    val bytes = AppContainer.circularsRepository.downloadPdfBytes(circular.r2PdfKey)
                    val text = AppContainer.pdfTextExtractor.extractText(bytes)
                    val result = AppContainer.newAiClassifier(allowLocalFallback = false)
                        .classifyCircularText(
                            circularNumber = circular.number,
                            circularTitle = circular.title,
                            pdfText = text
                        )
                    AppContainer.circularsRepository.saveAnalysis(result)
                    processed++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Una singola circolare che fallisce (PDF non raggiungibile, server proprio
                    // giù per quella chiamata) non deve fermare le altre: si prova la prossima.
                }
            }
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
        /**
         * Tetto per esecuzione: un Worker periodico gira in una finestra di tempo limitata, e
         * scaricare+classificare tutte le circolari arretrate in un colpo solo (potenzialmente
         * decine) rischierebbe di non finire in tempo e di consumare la quota AI in un colpo solo
         * senza che l'utente abbia nemmeno aperto l'app.
         */
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
         * Un giro immediato, per esempio subito dopo che l'elenco circolari si è aggiornato:
         * non aspetta i 15 minuti del periodico.
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
