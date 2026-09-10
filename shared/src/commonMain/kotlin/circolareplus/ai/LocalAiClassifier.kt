package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification

/**
 * Classificatore che gira **interamente sul telefono**, con il modello scaricato dalle
 * Impostazioni. Nessuna chiamata di rete, nessuna API key, nessun testo di circolare che esce dal
 * dispositivo — la stessa privacy-by-design di [ClientSideAiClassifier], ma senza dipendere né
 * dalla connessione né dalla quota gratuita di Google.
 *
 * Il prezzo è il tempo: una circolare richiede da qualche secondo con l'accelerazione GPU a
 * parecchie decine di secondi su CPU, contro il secondo scarso di Gemini.
 */
class LocalAiClassifier(
    private val model: LocalAiModel?,
    private val modelPath: String?,
    private val llm: LocalLlm
) : AiClassifier {

    private companion object {
        /**
         * Oltre questo, la classificazione si dichiara fallita invece di restare appesa.
         *
         * Serve un tetto perche' senza non ce n'e' nessuno: la chiamata al motore nativo non si
         * interrompe a comando e una circolare puo' andare avanti per minuti senza che niente lo
         * dica. Meglio un messaggio che suggerisce un modello piu' piccolo che una rotellina che
         * gira per sempre.
         */
        const val GENERATION_TIMEOUT_MILLIS = 90_000L

        /** Lo stesso, molto piu' corto, per la prova dalle Impostazioni. */
        const val TEST_TIMEOUT_MILLIS = 45_000L
    }

    override suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): CircularAiClassification {
        val path = modelPath
        if (model == null || path == null) {
            return heuristicFallback(
                circularNumber, circularTitle, pdfText,
                "nessun modello locale installato"
            )
        }

        val raw = try {
            llm.generate(
                modelPath = path,
                preferGpu = model.preferGpu,
                maxOutputTokens = model.maxOutputTokens,
                timeoutMillis = GENERATION_TIMEOUT_MILLIS,
                // Appena il JSON e' completo si smette: tutto quello che il modello scrive dopo
                // verrebbe comunque scartato da extractJsonObject, quindi aspettarlo e' tempo
                // regalato.
                stopWhen = { partial ->
                    CircularClassificationPrompt.extractJsonObject(partial) != null
                },
                systemPrompt = CircularClassificationPrompt.SYSTEM_PROMPT,
                userPrompt = CircularClassificationPrompt.buildUserPrompt(
                    circularNumber = circularNumber,
                    circularTitle = circularTitle,
                    pdfText = pdfText,
                    studentContext = studentContext,
                    // Un modello non addestrato al tool calling, se gli si chiede anche di
                    // produrre azioni per il calendario, tende a perdere il filo e a rovinare
                    // pure il riassunto: a TinyLlama si chiede soltanto di classificare.
                    askForCalendarActions = model.supportsActions
                )
            )
        } catch (e: Exception) {
            return heuristicFallback(
                circularNumber, circularTitle, pdfText,
                "il modello ${model.displayName} non è riuscito a rispondere: " +
                    "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            )
        }

        val jsonText = CircularClassificationPrompt.extractJsonObject(raw)
            ?: return heuristicFallback(
                circularNumber, circularTitle, pdfText,
                "${model.displayName} non ha risposto in JSON: ${raw.take(120)}"
            )

        val parsed = CircularClassificationPrompt.parse(circularNumber, jsonText)
            ?: return heuristicFallback(
                circularNumber, circularTitle, pdfText,
                "JSON di ${model.displayName} incompleto: ${jsonText.take(120)}"
            )
        return parsed.copy(modelLabel = "AI locale (${model.displayName})")
    }

    override suspend fun parseEventPrompt(userPrompt: String): EventDraft {
        val path = modelPath
        if (model == null || path == null) {
            return EventDraft("", "", "ALTRO", null, null, null)
        }

        return try {
            val raw = llm.generate(
                modelPath = path,
                preferGpu = model.preferGpu,
                maxOutputTokens = model.maxOutputTokens,
                timeoutMillis = GENERATION_TIMEOUT_MILLIS,
                stopWhen = { partial ->
                    EventGenerationPrompt.extractJsonObject(partial) != null
                },
                systemPrompt = EventGenerationPrompt.SYSTEM_PROMPT,
                userPrompt = EventGenerationPrompt.buildUserPrompt(userPrompt)
            )

            val jsonText = EventGenerationPrompt.extractJsonObject(raw)
            if (jsonText != null) {
                EventGenerationPrompt.parse(jsonText) ?: EventDraft("", "", "ALTRO", null, null, null)
            } else {
                EventDraft("", "", "ALTRO", null, null, null)
            }
        } catch (e: Exception) {
            EventDraft("", "", "ALTRO", null, null, null)
        }
    }

    override suspend fun testConfiguration(): String {
        val unavailable = onDeviceAiUnavailableReason()
        if (unavailable != null) return unavailable
        if (model == null) return "Nessun modello locale selezionato."
        val path = modelPath
            ?: return "${model.displayName} non è ancora stato scaricato."

        return try {
            val answer = llm.generate(
                modelPath = path,
                preferGpu = model.preferGpu,
                maxOutputTokens = model.maxOutputTokens,
                timeoutMillis = TEST_TIMEOUT_MILLIS,
                stopWhen = { partial -> partial.length >= 8 },
                systemPrompt = "Rispondi con una sola parola.",
                userPrompt = "Scrivi soltanto: ok"
            )
            "${model.displayName} funziona su ${llm.backendLabel()}. Risposta di prova: \"${answer.trim().take(60)}\""
        } catch (e: Exception) {
            // Accorciato: l'errore del motore nativo porta con sé il trace del codice C++ e i
            // byte grezzi di uno StatusList protobuf, che riempivano mezza schermata delle
            // Impostazioni senza dire niente di piu' della prima riga.
            "${model.displayName} non è riuscito a partire — " +
                HeuristicClassification.shortenReason(
                    "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
                )
        }
    }

    /**
     * Ripiego a parole chiave, condiviso con [ClientSideAiClassifier] tramite
     * [HeuristicClassification].
     */
    private fun heuristicFallback(
        circularNumber: Int,
        title: String,
        text: String,
        failureReason: String
    ): CircularAiClassification = HeuristicClassification.classify(
        circularNumber = circularNumber,
        title = title,
        text = text,
        failureReason = failureReason,
        notConfiguredMessage = "Nessun modello di AI locale installato: scaricalo dalle Impostazioni."
    )
}
