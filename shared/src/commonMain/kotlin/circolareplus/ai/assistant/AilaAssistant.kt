package circolareplus.ai.assistant

import circolareplus.ai.AiClassifier
import circolareplus.ai.AiTextResult
import circolareplus.ai.PdfTextExtractor
import circolareplus.data.repository.CircularsRepository
import circolareplus.domain.model.Circular
import kotlinx.coroutines.CancellationException

/**
 * L'assistente globale dell'app: una domanda in italiano, una risposta costruita su tutto
 * quello che AILA sa — circolari, calendario, bacheca, sondaggi, mappa posti e storico.
 *
 * Non e' una ricerca con sopra un riassunto: il modello riceve un contesto gia' selezionato
 * (vedi [AssistantContext]) e, se quello che ha non basta, puo' **chiedere** il testo integrale
 * di una o due circolari, che gli viene scaricato ed estratto dal PDF prima di rifargli la
 * domanda. E' l'unico modo per rispondere a "a che ora parte il pullman?" senza scaricare
 * decine di PDF a ogni domanda: si scarica solo quello che serve, solo quando serve, e a
 * deciderlo e' chi sta leggendo i dati.
 *
 * Perche' il giro e' al massimo uno: ogni giro in piu' e' un'altra chiamata al modello e un
 * altro paio di PDF, cioe' altri dieci secondi di attesa su una domanda che l'utente ha fatto
 * in chat e si aspetta veloce. Con due giri si copre il caso reale (riassunto insufficiente su
 * una circolare specifica); dal terzo in poi il modello di solito sta girando a vuoto.
 */
class AilaAssistant(
    private val classifierFactory: () -> AiClassifier,
    private val circularsRepository: CircularsRepository,
    private val pdfTextExtractor: PdfTextExtractor
) {

    companion object {
        /** Domande di esempio mostrate a chat vuota: servono a far capire cosa si puo' chiedere. */
        val SUGGESTED_QUESTIONS = listOf(
            "Cosa devo fare questa settimana?",
            "Ci sono pagamenti o scadenze in arrivo?",
            "Riassumimi le ultime circolari che mi riguardano",
            "Con chi sono seduto in aula e dov'e' il mio banco?",
            "Quali proposte della bacheca sono ancora aperte?"
        )

        /** Testo dei PDF: lo stesso tetto della classificazione, per gli stessi motivi di quota. */
        private const val MAX_PDF_CHARS_PER_CIRCULAR = 12_000
    }

    /**
     * Risponde a [question] tenendo conto di [history] (la conversazione in corso) e di
     * [knowledge] (lo stato dell'app).
     *
     * Non lancia mai per un fallimento del modello: torna un [AssistantReply] con
     * `isError = true` e il motivo vero, che la chat mostra come messaggio di errore e non come
     * risposta. La cancellazione invece passa: e' cosi' che si ferma una domanda quando si esce
     * dalla schermata.
     */
    suspend fun ask(
        question: String,
        history: List<AssistantMessage>,
        knowledge: AssistantKnowledge
    ): AssistantReply {
        val classifier = classifierFactory()

        val firstRaw = when (
            val result = classifier.generateAnswer(
                AssistantPrompt.builderFor(knowledge, history, question, emptyMap())
            )
        ) {
            is AiTextResult.Failure -> return AssistantReply(
                text = result.reason,
                isError = true
            )
            is AiTextResult.Success -> result
        }

        val firstAnswer = AssistantPrompt.parse(firstRaw.text)
        if (firstAnswer.needsCircularText.isEmpty()) {
            return AssistantReply(
                text = firstAnswer.answer,
                sources = firstAnswer.sources,
                modelLabel = firstRaw.modelLabel
            )
        }

        val deepTexts = fetchCircularTexts(firstAnswer.needsCircularText, knowledge.circulars)
        if (deepTexts.isEmpty()) {
            // Nessuno dei PDF richiesti si e' lasciato leggere (rete, scansione senza testo):
            // si tiene la prima risposta, che per contratto contiene gia' quello che il modello
            // sapeva, invece di far ripartire un giro identico al precedente.
            return AssistantReply(
                text = firstAnswer.answer,
                sources = firstAnswer.sources,
                modelLabel = firstRaw.modelLabel
            )
        }

        val secondResult = classifier.generateAnswer(
            AssistantPrompt.builderFor(knowledge, history, question, deepTexts)
        )
        if (secondResult !is AiTextResult.Success) {
            // Il secondo giro e' un miglioramento, non un requisito: se cade (tipicamente per
            // quota esaurita dopo la prima chiamata) resta la risposta del primo giro.
            return AssistantReply(
                text = firstAnswer.answer,
                sources = firstAnswer.sources,
                modelLabel = firstRaw.modelLabel
            )
        }

        val secondAnswer = AssistantPrompt.parse(secondResult.text)
        return AssistantReply(
            text = secondAnswer.answer,
            // Le circolari lette per intero entrano fra le fonti anche se il modello si
            // dimentica di citarle: sono quelle su cui la risposta si regge davvero.
            sources = mergeSources(secondAnswer.sources, deepTexts.keys, knowledge.circulars),
            modelLabel = secondResult.modelLabel
        )
    }

    /**
     * Scarica ed estrae il testo delle circolari indicate, allegati PDF compresi.
     *
     * Un allegato che non si scarica viene saltato senza far fallire la circolare, e una
     * circolare che non si legge viene saltata senza far fallire le altre: la stessa tolleranza
     * della classificazione, per lo stesso motivo — meglio una risposta basata su meno
     * documenti che nessuna risposta.
     */
    private suspend fun fetchCircularTexts(
        numbers: List<Int>,
        circulars: List<Circular>
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (number in numbers) {
            val circular = circulars.firstOrNull { it.number == number } ?: continue
            try {
                val bytes = circularsRepository.downloadPdfBytes(circular.r2PdfKey)
                var text = pdfTextExtractor.extractText(bytes)

                for (attachment in circular.attachments) {
                    val pdfKey = attachment.pdfKey ?: continue
                    if (text.length >= MAX_PDF_CHARS_PER_CIRCULAR) break
                    try {
                        val attachmentBytes = circularsRepository.downloadPdfBytes(pdfKey)
                        val attachmentText = pdfTextExtractor.extractText(attachmentBytes)
                        if (attachmentText.isNotBlank()) {
                            text += "\n\n--- Allegato: ${attachment.label} ---\n\n$attachmentText"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignorato di proposito: vedi commento sopra.
                    }
                }

                if (text.isNotBlank()) result[number] = text.take(MAX_PDF_CHARS_PER_CIRCULAR)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignorato di proposito: vedi commento sopra.
            }
        }
        return result
    }

    private fun mergeSources(
        declared: List<AssistantSource>,
        readNumbers: Set<Int>,
        circulars: List<Circular>
    ): List<AssistantSource> {
        val missing = readNumbers
            .filter { number -> declared.none { it.circularNumber == number } }
            .mapNotNull { number ->
                val circular = circulars.firstOrNull { it.number == number } ?: return@mapNotNull null
                AssistantSource(
                    kind = AssistantSourceKind.CIRCULAR,
                    label = "Circolare n. ${circular.number} — ${circular.title}",
                    circularNumber = circular.number
                )
            }
        return (declared + missing).distinctBy { it.kind to it.label }.take(6)
    }
}
