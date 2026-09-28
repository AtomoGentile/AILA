package circolareplus.ai.assistant

import circolareplus.ai.AiClassifier
import circolareplus.ai.AiTextResult
import circolareplus.ai.PdfTextExtractor
import circolareplus.data.repository.CircularsRepository
import circolareplus.domain.model.Circular
import circolareplus.platform.currentTimeMillis
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

        /** Attesa massima di una risposta di Gemini, dalla domanda alla risposta in chat. */
        private const val TARGET_REPLY_MS = 19_000L

        /** Tempo concesso ai PDF letti prima della domanda: il resto va al modello. */
        private const val PREFETCH_BUDGET_MS = 4_000L

        /** Sotto questo margine il secondo giro (PDF richiesti + nuova chiamata) non si tenta. */
        private const val MIN_SECOND_ROUND_MS = 7_000L

        /**
         * Attesa massima per le frasi in cima all'elenco delle circolari. Con Gemini o Nano sono
         * uno o due secondi; un modello su CPU che ci mette di piu' non fa aspettare l'elenco.
         */
        private const val INTRO_BUDGET_MS = 12_000L

        /** Circolari lette per intero prima di chiedere al modello: le due piu' attinenti. */
        private const val PREFETCHED_CIRCULARS = 2

        /**
         * Circolari recenti in cui cercare le parole della domanda **dentro il testo**. Il titolo
         * e il riassunto non bastano: "quando inizia il corso di teatro?" sta spesso in una
         * circolare intitolata "Attivita' pomeridiane a.s. 2026/27", e cercando solo li'
         * l'assistente rispondeva che il corso non esiste.
         */
        private const val SEARCHED_CIRCULARS = 15
    }

    /**
     * Il testo integrale delle circolari che c'entrano con la domanda, scelte in due modi:
     * la migliore per titolo e riassunto, poi le migliori per contenuto del PDF (vedi
     * [AssistantContext.rankByText]). I PDF gia' letti (dall'analisi o da una domanda
     * precedente) non si riscaricano; gli altri si scaricano in parallelo entro
     * [PREFETCH_BUDGET_MS] e, se non arrivano in tempo, si usa quello che c'e'.
     */
    private suspend fun relevantCircularTexts(knowledge: AssistantKnowledge, question: String): Map<Int, String> {
        val byTitle = AssistantContext.mostRelevantCirculars(knowledge, question, PREFETCHED_CIRCULARS)
        if (!AssistantContext.wantsTextSearch(knowledge, question)) {
            return fetchCircularTexts(byTitle, knowledge.circulars, budgetMs = PREFETCH_BUDGET_MS)
        }
        val recent = knowledge.circulars
            .sortedWith(compareByDescending<Circular> { it.publishDate.take(10) }.thenByDescending { it.number })
            .take(SEARCHED_CIRCULARS)
            .map { it.number }
        val texts = fetchCircularTexts((byTitle + recent).distinct(), knowledge.circulars, budgetMs = PREFETCH_BUDGET_MS)
        val byText = AssistantContext.rankByText(texts, question)
        val chosen = (byTitle.take(1) + byText + byTitle).distinct().take(PREFETCHED_CIRCULARS)
        return texts.filterKeys { it in chosen }
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
        // Scadenze, pagamenti, "cosa ho questa settimana": l'elenco lo fa il codice, esatto e
        // subito. Il modello sul telefono lo ricopiava storpiato (vedi [AssistantAgenda]).
        AssistantAgenda.answer(knowledge, question)?.let { return it }
        // Stessa cosa per "quali proposte sono aperte?": vedi [AssistantBoard].
        AssistantBoard.answer(knowledge, question)?.let { return it }
        // "Riassumimi le ultime circolari": elenco e riassunti dal codice, al modello solo le
        // frasi in cima (vedi [AssistantCirculars]).
        AssistantCirculars.answer(knowledge, question)?.let { return withIntro(it, knowledge) }

        val classifier = classifierFactory()
        val startedAt = currentTimeMillis()

        // Le circolari che c'entrano davvero con la domanda si leggono per intero subito, senza
        // aspettare che sia il modello a chiederlo: i modelli sul telefono non lo chiedono quasi
        // mai e rispondevano col solo riassunto, dove dettagli come "scienze il martedi'" non
        // ci sono. Per saluti e domande generali la lista e' vuota e non si scarica niente.
        val prefetched = relevantCircularTexts(knowledge, question)

        val firstRaw = when (
            val result = classifier.generateAnswer(
                AssistantPrompt.builderFor(knowledge, history, question, prefetched)
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
            sources = checkedSources(firstAnswer, question, prefetched.keys, knowledge),
            modelLabel = firstRaw.modelLabel
        )
        val requested = firstAnswer.needsCircularText.filter { it !in prefetched }
        if (requested.isEmpty()) return firstReply

        // Con Gemini la risposta deve arrivare entro [TARGET_REPLY_MS]: il secondo giro si fa
        // solo se resta il tempo per un altro PDF e un'altra chiamata, e comunque non oltre.
        // Il modello sul telefono non ha questo tetto: e' lento per natura, e un secondo giro
        // saltato li' vorrebbe dire rispondere quasi sempre col solo riassunto.
        val isCloud = firstRaw.modelLabel.startsWith("Google")
        val remainingMs = TARGET_REPLY_MS - (currentTimeMillis() - startedAt)
        if (isCloud && remainingMs < MIN_SECOND_ROUND_MS) return firstReply

        val deepTexts = prefetched + fetchCircularTexts(
            requested,
            knowledge.circulars,
            budgetMs = if (isCloud) minOf(PREFETCH_BUDGET_MS, remainingMs - MIN_SECOND_ROUND_MS / 2) else Long.MAX_VALUE
        )
        if (deepTexts.size == prefetched.size) {
            // Nessuno dei PDF richiesti si e' lasciato leggere (rete, scansione senza testo):
            // si tiene la prima risposta, che per contratto contiene gia' quello che il modello
            // sapeva, invece di far ripartire un giro identico al precedente.
            return firstReply
        }

        val secondPrompt = AssistantPrompt.builderFor(knowledge, history, question, deepTexts)
        val secondResult = if (isCloud) {
            val left = TARGET_REPLY_MS - (currentTimeMillis() - startedAt)
            if (left <= 0) return firstReply
            withTimeoutOrNull(left) { classifier.generateAnswer(secondPrompt) } ?: return firstReply
        } else {
            classifier.generateAnswer(secondPrompt)
        }
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
                checkedSources(secondAnswer, question, deepTexts.keys, knowledge),
                requested.filter { it in deepTexts }.toSet(),
                knowledge.circulars
            ),
            modelLabel = secondResult.modelLabel
        )
    }

    /**
     * L'elenco delle circolari con, in cima, le frasi del modello su cosa e' piu' urgente.
     *
     * Le frasi sono un di piu': se il modello non risponde entro [INTRO_BUDGET_MS], fallisce o
     * scrive qualcosa che non torna con i dati (vedi [AssistantCirculars.acceptIntro]), si
     * mostra l'elenco da solo, che e' gia' una risposta completa.
     */
    private suspend fun withIntro(listing: AssistantCirculars.Listing, knowledge: AssistantKnowledge): AssistantReply {
        val prompt = listing.introPrompt ?: return listing.reply
        val result = try {
            withTimeoutOrNull(INTRO_BUDGET_MS) { classifierFactory().generateAnswer(prompt) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val success = result as? AiTextResult.Success ?: return listing.reply
        val intro = AssistantCirculars.acceptIntro(success.text, listing.shown, knowledge) ?: return listing.reply
        return listing.reply.copy(
            text = intro + "\n\n" + listing.reply.text,
            modelLabel = "${success.modelLabel} · elenco dalle circolari di AILA"
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
            val cached = CircularTextCache.get(number)
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
        val downloaded = arrayOfNulls<String>(toDownload.size)
        coroutineScope {
            val jobs = toDownload.mapIndexed { index, circular ->
                launch { downloaded[index] = downloadCircularText(circular) }
            }
            withTimeoutOrNull(budgetMs) { jobs.joinAll() }
            jobs.forEach { it.cancel() }
            jobs.joinAll()
        }
        toDownload.forEachIndexed { index, circular ->
            val text = downloaded[index] ?: return@forEachIndexed
            result[circular.number] = text
            CircularTextCache.put(circular.number, text)
        }
        return result
    }

    /** Testo del PDF di una circolare con i suoi allegati, o `null` se non si lascia leggere. */
    private suspend fun downloadCircularText(circular: Circular): String? {
        return try {
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
            text.takeIf { it.isNotBlank() }?.take(MAX_PDF_CHARS_PER_CIRCULAR)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignorato di proposito: vedi commento sopra.
            null
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
