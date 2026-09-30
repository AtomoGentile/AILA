package circolareplus.work

import circolareplus.ai.tier
import circolareplus.data.AppContainer
import kotlinx.coroutines.CancellationException

/**
 * Scarica e classifica in background le circolari rimaste indietro, senza una schermata aperta.
 *
 * Lo stesso giro per le due piattaforme: su Android lo lancia `CircularsSyncWorker`
 * (WorkManager, ogni 15 minuti e dopo il push di una circolare nuova), su iOS il task
 * `BGAppRefreshTask` registrato in AilaBackground.swift (vedi `IosBackgroundWork`), quando iOS decide
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
        try {
            classifyNewCirculars(maxCirculars, shouldStop)
        } finally {
            // Copia offline aggiornata anche ad app chiusa: a scuola, senza rete, si trovano gia'
            // i dati di stamattina senza aver dovuto ricordarsi di scaricarli. Dopo le analisi
            // (piu' importanti se il tempo concesso dal sistema e' poco); ogni file salvato resta
            // anche se il sistema interrompe il giro a meta', e il giro dopo riparte da li'.
            if (!shouldStop()) {
                try {
                    circolareplus.data.OfflineSync.runIfDue(background = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Rete assente: si riprova al prossimo giro.
                }
            }
        }
    }

    private suspend fun classifyNewCirculars(maxCirculars: Int, shouldStop: () -> Boolean) {
        // Il motore locale e' uno solo: se e' il provider scelto, va usato una circolare alla
        // volta quando l'utente la apre, non da un giro che il telefono fa partire in tasca
        // (stesso motivo per cui MainAppShell ferma il suo ciclo in background in questo caso).
        if (AppContainer.isUsingLocalAiFirst()) return
        // Senza chiave personale l'analisi dal telefono sarebbe solo il ripiego euristico, che non
        // si condivide: inutile scaricare PDF e interrogare il server.
        if (AppContainer.settings.userAiApiKey.isBlank()) return

        // Una sola richiesta per sapere cosa e' cambiato sul server dall'ultimo giro, al posto di
        // una per circolare: prima erano 30 richieste ogni 15 minuti per telefono, anche con
        // tutti i riassunti gia' pronti, e con qualche decina di telefoni bastavano a esaurire la
        // quota giornaliera del Worker.
        val settings = AppContainer.settings
        val sync = AppContainer.circularsRepository.getAnalysesSince(settings.backgroundAnalysesCursor.ifBlank { null })
        settings.mergeClassifications(sync.analyses)
        sync.cursor?.let { settings.backgroundAnalysesCursor = it }
        val known = settings.readClassificationCache()

        val circulars = AppContainer.circularsRepository.listCirculars(limit = 30)
        var processed = 0
        for (circular in circulars.sortedByDescending { it.number }) {
            if (shouldStop() || processed >= maxCirculars) break
            // Gia' fatta da Gemini (qui o sul server): niente da migliorare.
            val existing = known[circular.number]
            if (existing != null && !existing.isFallback && existing.tier >= 2) continue
            // La riassume il server con la sua chiave: farlo anche da qui voleva dire una chiamata
            // Gemini per ogni telefono sulla stessa circolare, appena arrivata la notifica.
            if (sync.serverWillSummarize(circular.number)) continue
            try {
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
                if (!result.isFallback) {
                    val better = AppContainer.circularsRepository.saveAnalysis(result)
                    settings.saveClassification(better ?: result)
                }
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
