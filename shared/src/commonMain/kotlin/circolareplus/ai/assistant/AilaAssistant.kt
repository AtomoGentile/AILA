package circolareplus.ai.assistant

import circolareplus.ai.AiClassifier
import circolareplus.ai.AiTextResult
import circolareplus.ai.PdfTextExtractor
import circolareplus.data.repository.CircularsRepository
import circolareplus.domain.model.Circular
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

        /**
         * Testo letto da ogni PDF. Alto di proposito: nel prompt non entra tutto, ma solo i
         * passaggi che c'entrano con la domanda (vedi [PassageSelector]), e per sceglierli serve
         * il documento intero, anche di 30 pagine. Il tetto evita solo casi patologici.
         */
        private const val MAX_PDF_CHARS_PER_CIRCULAR = 150_000

        /** Tempo concesso ai PDF letti prima della domanda: il resto va al modello. */
        private const val PREFETCH_BUDGET_MS = 4_000L

        /** Circolari lette per intero prima di chiedere al modello: le due piu' attinenti. */
        private const val PREFETCHED_CIRCULARS = 2

        /** Circolari portate al secondo giro da una ricerca dell'assistente nel testo integrale. */
        private const val MAX_SEARCHED_CIRCULARS = 2

        /**
         * Testo gia' estratto per circolare: la stessa domanda riformulata, o la domanda dopo,
         * non riscarica e non rilegge lo stesso PDF.
         */
        private val textCache = mutableMapOf<Int, String>()
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
        // "Ciao", "grazie": niente dati e niente modello, vedi [AssistantGreeting].
        AssistantGreeting.answer(question)?.let { return it }
        // Scadenze, pagamenti, "cosa ho questa settimana": l'elenco lo fa il codice, esatto e
        // subito. Il modello sul telefono lo ricopiava storpiato (vedi [AssistantAgenda]).
        AssistantAgenda.answer(knowledge, question)?.let { return it }
        // Stessa cosa per "quali proposte sono aperte?": vedi [AssistantBoard].
        AssistantBoard.answer(knowledge, question)?.let { return it }
        // "Puoi creare un sondaggio?": dove si fa nell'app, vedi [AssistantCapabilities].
        AssistantCapabilities.answer(knowledge, question)?.let { return it }
        // "Che cos'e' AILA?", "cosa sai fare?": la risposta la sa il codice, non il modello.
        AssistantAbout.answer(question, history)?.let { return it }
        // "Riassumimi le ultime circolari": i riassunti ci sono gia', vedi [AssistantDigest].
        AssistantDigest.answer(knowledge, question)?.let { return it }

        val classifier = classifierFactory()
        // "e dei genitori?": si cerca insieme alla domanda prima, vedi AssistantContext.searchQuery.
        val searchQuery = AssistantContext.searchQuery(question, history)
        // Riscontri nel testo integrale delle circolari sul telefono: li calcola il codice, prima
        // del modello, cosi' un "non compare" vale per tutte le circolari che ci sono.
        val known = knowledge.copy(textHits = textHitsFor(question))

        // Le circolari che c'entrano davvero con la domanda si leggono per intero subito, senza
        // aspettare che sia il modello a chiederlo: i modelli sul telefono non lo chiedono quasi
        // mai e rispondevano col solo riassunto, dove dettagli come "scienze il martedi'" non
        // ci sono. Per saluti e domande generali la lista e' vuota e non si scarica niente.
        val prefetched = fetchCircularTexts(
            AssistantContext.mostRelevantCirculars(known, searchQuery, PREFETCHED_CIRCULARS),
            known.circulars,
            budgetMs = PREFETCH_BUDGET_MS
        )

        val firstRaw = when (
            val result = classifier.generateAnswer(
                AssistantPrompt.builderFor(known, history, question, prefetched, searchQuery)
            )
        ) {
            is AiTextResult.Failure -> return AssistantReply(
                text = result.reason,
                isError = true
            )
            is AiTextResult.Success -> result
        }

        val firstAnswer = AssistantPrompt.parse(firstRaw.text)
        val firstReply = AssistantReply(
            text = firstAnswer.answer,
            sources = checkedSources(firstAnswer, searchQuery, prefetched.keys, knowledge),
            modelLabel = firstRaw.modelLabel
        )
        val requested = firstAnswer.needsCircularText.filter { it !in prefetched }
        // Le parole che il modello vuole cercare nel testo integrale: i passaggi entrano al
        // secondo giro, come le circolari richieste per intero.
        val searched = searchedTexts(firstAnswer.searches, prefetched.keys)
        if (requested.isEmpty() && searched.isEmpty()) return firstReply

        // Il secondo giro ha il suo tempo: ogni chiamata al modello e' limitata dal classificatore
        // (vedi ChainedAiClassifier), quindi qui basta leggere le circolari richieste.
        val fetched = if (requested.isEmpty()) emptyMap() else fetchCircularTexts(
            requested,
            known.circulars,
            budgetMs = PREFETCH_BUDGET_MS
        )
        val deepTexts = prefetched + searched + fetched
        if (deepTexts.size == prefetched.size) {
            // Nessuno dei PDF richiesti si e' lasciato leggere (rete, scansione senza testo):
            // si tiene la prima risposta, che per contratto contiene gia' quello che il modello
            // sapeva, invece di far ripartire un giro identico al precedente.
            return firstReply
        }

        val secondPrompt = AssistantPrompt.builderFor(known, history, question, deepTexts, searchQuery)
        val secondResult = classifier.generateAnswer(secondPrompt)
        if (secondResult !is AiTextResult.Success) {
            // Il secondo giro e' un miglioramento, non un requisito: se cade (tipicamente per
            // quota esaurita dopo la prima chiamata) resta la risposta del primo giro.
            return firstReply
        }

        val secondAnswer = AssistantPrompt.parse(secondResult.text)
        return AssistantReply(
            text = secondAnswer.answer,
            // Le circolari richieste e lette per intero entrano fra le fonti anche se il modello
            // si dimentica di citarle: sono quelle su cui la risposta si regge davvero.
            sources = mergeSources(
                checkedSources(secondAnswer, searchQuery, deepTexts.keys, known),
                (requested + searched.keys).filter { it in deepTexts }.toSet(),
                knowledge.circulars
            ),
            modelLabel = secondResult.modelLabel
        )
    }

    /**
     * Le fonti dichiarate dal modello, tolte quelle che non possono essere vere.
     *
     * Un modello piccolo copia gli esempi e cita circolari a caso anche per rispondere a un
     * "ciao": una fonte sbagliata e' peggio di nessuna, perche' manda a leggere il documento
     * sbagliato. Una circolare resta fra le fonti solo se esiste, e se e' stata letta per intero,
     * e' citata nella domanda o nella risposta, o e' fra le piu' attinenti alla domanda.
     */
    private fun checkedSources(
        parsed: AssistantPrompt.ParsedAnswer,
        question: String,
        readNumbers: Set<Int>,
        knowledge: AssistantKnowledge
    ): List<AssistantSource> {
        if (AssistantContext.isSmallTalk(knowledge, question)) return emptyList()
        val byNumber = knowledge.circulars.associateBy { it.number }
        val plausible = readNumbers +
            AssistantContext.circularNumbersIn(question) +
            AssistantContext.mostRelevantCirculars(knowledge, question, limit = 6)
        return parsed.sources.mapNotNull { source ->
            if (source.kind != AssistantSourceKind.CIRCULAR) return@mapNotNull source
            val number = source.circularNumber ?: return@mapNotNull null
            val circular = byNumber[number] ?: return@mapNotNull null
            val cited = number in plausible ||
                Regex("(?<!\\d)$number(?!\\d)").containsMatchIn(parsed.answer)
            if (!cited) return@mapNotNull null
            // L'etichetta si ricostruisce dai dati veri: il modello puo' dare solo il numero
            // (meno token da generare sul telefono) e non puo' storpiare o inventare il titolo.
            source.copy(label = circularLabel(circular))
        }.distinctBy { it.kind to (it.circularNumber ?: it.label) }
    }

    private fun circularLabel(circular: Circular) = "Circolare n. ${circular.number} — ${circular.title}"

    /**
     * Per ogni parola della domanda, in quali circolari compare nel testo integrale sul telefono.
     * Serve a [KeywordEvidence] per dire "non compare" solo quando il testo e' stato controllato.
     */
    private suspend fun textHitsFor(question: String): AssistantTextHits = AssistantTextHits(
        circularsWithText = CircularTextStore.count(),
        byTerm = KeywordEvidence.terms(question).associateWith { term ->
            CircularTextStore.search(listOf(term)).map { it.number }.toSet()
        }
    )

    /**
     * Il testo integrale delle circolari trovate con le [terms] scelte dal modello, escluse quelle
     * che il primo giro ha gia' letto. Al secondo giro [AssistantContext] ne ricava i passaggi
     * che servono alla domanda.
     */
    private suspend fun searchedTexts(terms: List<String>, exclude: Set<Int>): Map<Int, String> {
        if (terms.isEmpty()) return emptyMap()
        return CircularTextStore.search(terms)
            .map { it.number }
            .filter { it !in exclude }
            .take(MAX_SEARCHED_CIRCULARS)
            .mapNotNull { number -> CircularTextStore.text(number)?.takeIf { it.isNotBlank() }?.let { number to it } }
            .toMap()
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
        circulars: List<Circular>,
        budgetMs: Long
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val toDownload = numbers.mapNotNull { number ->
            val cached = textCache[number]
            if (cached != null) {
                result[number] = cached
                null
            } else {
                circulars.firstOrNull { it.number == number }
            }
        }
        if (toDownload.isEmpty() || budgetMs <= 0) return result

        // In parallelo e con un tetto di tempo: prima si scaricavano uno dopo l'altro, e due PDF
        // con allegati su una rete lenta mangiavano da soli meta' dell'attesa. Allo scadere si
        // usa quello che e' arrivato; il resto lo chiede il modello, se gli serve davvero.
        //
        // "Quello che e' arrivato" e' il testo letto finora per circolare, aggiornato dopo il PDF
        // principale e dopo ogni allegato. Prima arrivava solo a lettura finita: se allo scadere del tempo
        // mancava ancora un allegato si perdeva anche il PDF principale gia' letto, e il modello
        // rispondeva dal solo riassunto (visto con la circolare 8 degli sportelli: stessa risposta
        // sbagliata sia da Gemini sia dal modello sul telefono).
        val downloaded = arrayOfNulls<String>(toDownload.size)
        val complete = BooleanArray(toDownload.size)
        coroutineScope {
            val jobs = toDownload.mapIndexed { index, circular ->
                launch {
                    downloadCircularText(circular) { text -> downloaded[index] = text }
                    complete[index] = true
                }
            }
            withTimeoutOrNull(budgetMs) { jobs.joinAll() }
            jobs.forEach { it.cancel() }
            jobs.joinAll()
        }
        toDownload.forEachIndexed { index, circular ->
            val text = downloaded[index] ?: return@forEachIndexed
            result[circular.number] = text
            // In cache solo il testo completo: uno parziale lascerebbe fuori gli allegati per
            // sempre, anche dalle domande successive che avrebbero il tempo di leggerli.
            if (complete[index]) textCache[circular.number] = text
        }
        return result
    }

    /**
     * Legge il PDF di una circolare e poi i suoi allegati, passando a [onText] il testo letto
     * finora dopo ogni documento. Una circolare che non si lascia leggere non chiama [onText].
     */
    private suspend fun downloadCircularText(circular: Circular, onText: (String) -> Unit) {
        try {
            val bytes = circularsRepository.downloadPdfBytes(circular.r2PdfKey)
            var text = pdfTextExtractor.extractText(bytes)
            if (text.isNotBlank()) onText(text.take(MAX_PDF_CHARS_PER_CIRCULAR))

            for (attachment in circular.attachments) {
                val pdfKey = attachment.pdfKey ?: continue
                if (text.length >= MAX_PDF_CHARS_PER_CIRCULAR) break
                try {
                    val attachmentBytes = circularsRepository.downloadPdfBytes(pdfKey)
                    val attachmentText = pdfTextExtractor.extractText(attachmentBytes)
                    if (attachmentText.isNotBlank()) {
                        text += "\n\n--- Allegato: ${attachment.label} ---\n\n$attachmentText"
                        onText(text.take(MAX_PDF_CHARS_PER_CIRCULAR))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignorato di proposito: vedi commento sopra.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignorato di proposito: vedi commento sopra.
        }
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
                    label = circularLabel(circular),
                    circularNumber = circular.number
                )
            }
        return (declared + missing).distinctBy { it.kind to (it.circularNumber ?: it.label) }.take(6)
    }
}
