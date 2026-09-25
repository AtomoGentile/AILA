package circolareplus.work

import circolareplus.ai.tier
import circolareplus.data.AppContainer
import kotlinx.coroutines.CancellationException

/**
 * Scarica e classifica in background le circolari rimaste indietro, senza una schermata aperta.
 *
 * Lo stesso giro per le due piattaforme: su Android lo lancia `CircularsSyncWorker`
 * (WorkManager, ogni 15 minuti e dopo il push di una circolare nuova), su iOS il task
 * `BGAppRefreshTask` registrato in AppDelegate.swift (vedi `IosBackgroundWork`), quando iOS decide
 * di concedere qualche secondo all'app sospesa.
 *
 * Non prova l'AI locale se non e' il provider primario dello studente: se il primario e' il cloud
 * e fallisce, qui NON si ricade sul modello on-device (`allowLocalFallback = false`) — farlo senza
 * che l'utente lo veda scaldava il telefono in tasca senza nessun indicatore. La circolare che il
 * cloud non riesce a classificare resta per l'apertura manuale, dove l'attesa del modello locale
 * e' accettata perche' e' l'utente a chiederla.
 */
object BackgroundCircularsSync {

    /**
     * Un giro. Lancia un'eccezione solo se l'elenco circolari non si legge (rete assente, server
     * giu'): il chiamante decide se riprovare. Le singole circolari che falliscono si saltano.
     *
     * @param maxCirculars tetto per giro: il sistema concede una finestra di tempo limitata (su iOS
     *   circa 30 secondi), e classificare tutte le arretrate in un colpo consumerebbe la quota AI
     *   senza che l'utente abbia nemmeno aperto l'app.
     * @param shouldStop controllato fra una circolare e l'altra (Worker fermato, tempo scaduto).
     */
    suspend fun run(maxCirculars: Int, shouldStop: () -> Boolean = { false }) {
        // Il motore locale e' uno solo: se e' il provider scelto, va usato una circolare alla
        // volta quando l'utente la apre, non da un giro che il telefono fa partire in tasca
        // (stesso motivo per cui MainAppShell ferma il suo ciclo in background in questo caso).
        if (AppContainer.isUsingLocalAiFirst()) return

        val circulars = AppContainer.circularsRepository.listCirculars(limit = 30)
        var processed = 0
        for (circular in circulars.sortedByDescending { it.number }) {
            if (shouldStop() || processed >= maxCirculars) break
            try {
                // Si salta solo quella gia' fatta da Gemini: una fatta dall'AI locale di un
                // compagno si puo' migliorare, e il server tiene comunque la migliore.
                val existing = AppContainer.circularsRepository.getCachedAnalysis(circular.number)
                if (existing != null && existing.tier >= 2) continue

                val bytes = AppContainer.circularsRepository.downloadPdfBytes(circular.r2PdfKey)
                val text = AppContainer.pdfTextExtractor.extractText(bytes)
                val result = AppContainer.newAiClassifier(allowLocalFallback = false)
                    .classifyCircularText(
                        circularNumber = circular.number,
                        circularTitle = circular.title,
                        pdfText = text
                    )
                // Il ripiego euristico non si condivide: e' un messaggio d'errore, non un
                // riassunto, e il server lo rifiuterebbe comunque.
                if (!result.isFallback) AppContainer.circularsRepository.saveAnalysis(result)
                processed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Una singola circolare che fallisce (PDF non raggiungibile, server giu' per
                // quella chiamata) non deve fermare le altre: si prova la prossima.
            }
        }
    }
}
