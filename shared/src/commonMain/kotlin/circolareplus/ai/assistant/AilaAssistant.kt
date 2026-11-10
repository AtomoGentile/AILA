package circolareplus.ai.assistant

import circolareplus.ai.withAttachmentServices

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
 * Assistant con letture iterative limitate: il modello può cercare eventi, confrontare attività
 * e approfondire circolari prima di rispondere. Gli esiti e le bozze accompagnano i turni successivi.
 * Le mutazioni passano sempre dal pianificatore validato e da una nuova anteprima confermabile;
 * questa classe non scrive eventi. Il limite dei giri evita cicli e chiamate senza fine.
 */
class AilaAssistant internal constructor(
    private val classifierFactory: () -> AiClassifier,
    private val downloadPdf: suspend (String) -> ByteArray,
    private val extractPdf: suspend (ByteArray) -> String,
    private val actionsEnabled: Boolean = false
) {
    constructor(
        classifierFactory: () -> AiClassifier,
        circularsRepository: CircularsRepository,
        pdfTextExtractor: PdfTextExtractor,
        actionsEnabled: Boolean = false
    ) : this(classifierFactory, circularsRepository::downloadPdfBytes, pdfTextExtractor::extractText, actionsEnabled)

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
        knowledge: AssistantKnowledge,
        mode: AssistantMode = AssistantMode.GENERAL,
        onAgentPhase: (AssistantAgentPhase) -> Unit = {}
    ): AssistantReply {
        if (mode == AssistantMode.GENERAL && !actionsEnabled && AssistantActionIntent.matches(question, history))
            return AssistantReply("Le azioni di ACA sono disattivate. Puoi abilitarle in Impostazioni → AI → ACA e Calendario.", modelLabel = "AILA")
        // The model can request reads or a validated action plan. Keyword intent is only a
        // fast disabled-feature notice, never the gate that decides what the model may do.
        // "Ciao", "grazie": niente dati e niente modello, vedi [AssistantGreeting].
        AssistantGreeting.answer(question)?.let { return it }
        // Le risposte pronte del codice rispondono sull'app intera: nella chat della Gita no,
        // li' l'unica fonte e' il materiale della gita e il modello la legge tutta.
        val studyPlan = AssistantContext.normalize(question).let { "piano" in it && ("studio" in it || "ripasso" in it) }
        if (mode == AssistantMode.GENERAL && !studyPlan && knowledge.attachedSources.isEmpty() && history.isEmpty() && !AssistantActionIntent.matches(question, history) && knowledge.calendarWarning == null) {
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
        }

        val classifier = classifierFactory()
        val searchQuery = AssistantContext.searchQuery(question, history)
        var known = knowledge.copy(textHits = textHitsFor(question))
        var texts = fetchCircularTexts(
            AssistantContext.mostRelevantCirculars(known, searchQuery, PREFETCHED_CIRCULARS),
            known.circulars, budgetMs = PREFETCH_BUDGET_MS
        )
        val observations = mutableListOf<String>()
        val seenQueries = mutableSetOf<AssistantCalendarQuery>()
        val seenSearches = mutableSetOf<String>()
        val seenCirculars = texts.keys.toMutableSet()
        val studyOnly = studyPlan
        var reviewAttempted = false
        var readsPerformed = false
        var partialCandidate: AssistantReply? = null
        fun partialReply(parsed: AssistantPrompt.ParsedAnswer, modelLabel: String): AssistantReply = AssistantReply(
            parsed.answer + "\n\nNon ho completato la verifica di tutte le parti della richiesta: questa risposta può essere parziale.",
            sources = checkedSources(parsed, searchQuery, known), modelLabel = modelLabel, readContext = known.readContext
        )
        for (round in 0 until AssistantReadTools.MAX_ROUNDS) {
            val canRead = round < AssistantReadTools.MAX_ROUNDS - 1
            onAgentPhase(AssistantAgentPhase.WRITING)
            var result = classifier.generateAnswer(
                AssistantPrompt.builderFor(known, history, question, texts, searchQuery, mode, canRead)
                    .withAttachmentServices(known.attachedSources.map { it.file.service }.toSet())
            )
            if (result is AiTextResult.Failure) return partialCandidate?.copy(readContext = known.readContext)
                ?: AssistantReply(result.reason, isError = true)
            result as AiTextResult.Success
            var parsed = AssistantPrompt.parse(result.text)
            // Check an answer which skipped retrieval once. After reads, the next generation
            // already has the evidence and should answer directly, without another model round.
            if (mode == AssistantMode.GENERAL && !reviewAttempted && !readsPerformed &&
                !parsed.incomplete && parsed.answer.isNotBlank() && parsed.actionRequest.isBlank() &&
                parsed.calendarQueries.isEmpty() && parsed.needsCircularText.isEmpty() && parsed.searches.isEmpty()) {
                reviewAttempted = true
                val candidate = partialReply(parsed, result.modelLabel)
                partialCandidate = candidate
                result = classifier.generateAnswer(
                    AssistantPrompt.builderFor(known, history, question, texts, searchQuery, mode, canRead, result.text)
                        .withAttachmentServices(known.attachedSources.map { it.file.service }.toSet())
                )
                if (result is AiTextResult.Failure) return candidate
                result as AiTextResult.Success
                parsed = AssistantPrompt.parse(result.text)
                if (parsed.incomplete) return candidate
                if (parsed.verified == false && parsed.actionRequest.isBlank() &&
                    parsed.calendarQueries.isEmpty() && parsed.needsCircularText.isEmpty() && parsed.searches.isEmpty()) {
                    if (parsed.answer.isNotBlank()) partialCandidate = partialReply(parsed, result.modelLabel)
                    if (!canRead) return partialCandidate!!
                    observations += "La verifica precedente ha segnalato parti non completate. Recupera i dati mancanti con gli strumenti oppure rispondi con le informazioni disponibili e limiti espliciti. Non chiedere di ripetere la stessa domanda."
                    known = known.copy(readContext = AssistantReadTools.boundedEvidence(observations.joinToString("\n\n")))
                    continue
                }
                // `verified` is advisory, never an execution permission or a required schema
                // field: ordinary valid JSON/plain-text replies must remain usable.
                if (parsed.answer.isBlank() && parsed.actionRequest.isBlank() &&
                    parsed.calendarQueries.isEmpty() && parsed.needsCircularText.isEmpty() && parsed.searches.isEmpty()) return candidate
            }
            if (parsed.incomplete) return partialCandidate?.copy(readContext = known.readContext) ?: AssistantReply(
                "La risposta è incompleta. Riprova: nessuna nuova azione è stata eseguita.", isError = true,
                modelLabel = result.modelLabel
            )
            if (mode == AssistantMode.GENERAL && parsed.actionRequest.isNotBlank() && parsed.calendarQueries.isEmpty() && parsed.needsCircularText.isEmpty() && parsed.searches.isEmpty()) {
                if (!actionsEnabled) return AssistantReply(
                    "Le azioni di ACA sono disattivate. Puoi abilitarle in Impostazioni → AI → ACA e Calendario.", modelLabel = "AILA"
                )
                return planAction(question, history, known, onAgentPhase, parsed.actionRequest)
            }
            val queries = if (mode == AssistantMode.GENERAL) parsed.calendarQueries.filter { seenQueries.add(it) } else emptyList()
            val requested = parsed.needsCircularText.filter { seenCirculars.add(it) }
            val searches = parsed.searches.filter { seenSearches.add(it) }
            val wantsMore = queries.isNotEmpty() || requested.isNotEmpty() || searches.isNotEmpty()
            val requestedReads = parsed.calendarQueries.isNotEmpty() || parsed.needsCircularText.isNotEmpty() || parsed.searches.isNotEmpty()
            if (!canRead || !wantsMore) {
                if (parsed.answer.isBlank() && partialCandidate != null) return partialCandidate.copy(readContext = known.readContext)
                val limitNotice = if (requestedReads || parsed.verified == false) "\n\nNon ho completato tutte le verifiche richieste: i risultati possono essere parziali." else ""
                return AssistantReply(
                    text = parsed.answer.ifBlank { "Non ho trovato informazioni sufficienti per completare la richiesta." } + limitNotice,
                    sources = checkedSources(parsed, searchQuery, known), modelLabel = result.modelLabel,
                    readContext = known.readContext
                )
            }
            onAgentPhase(AssistantAgentPhase.READING)
            readsPerformed = true
            queries.forEach { observations += AssistantReadTools.execute(it, known, studyOnly) }
            val searched = searchedTexts(searches, texts.keys)
            val fetched = fetchCircularTexts(requested, known.circulars, PREFETCH_BUDGET_MS)
            if (requested.any { it !in fetched }) observations += "Alcuni testi richiesti non sono stati caricati; non dedurre assenze dal testo mancante."
            texts = texts + searched + fetched
            known = known.copy(readContext = AssistantReadTools.boundedEvidence(observations.joinToString("\n\n")))
        }
        error("Unreachable assistant loop")
    }

    private suspend fun planAction(
        question: String,
        history: List<AssistantMessage>,
        knowledge: AssistantKnowledge,
        onAgentPhase: (AssistantAgentPhase) -> Unit,
        resolvedRequest: String
    ): AssistantReply {
        AssistantAudienceEdit.plan(question, history, knowledge)?.let { plan ->
            if (AssistantActionPrompt.validate(plan, knowledge)) return actionReply(plan)
        }
        onAgentPhase(AssistantAgentPhase.READING)
        val searchQuery = AssistantContext.searchQuery(resolvedRequest, history)
        val planningRequest = "$question\n\nRiferimenti ricostruiti dalla conversazione, da verificare sulla richiesta originale (che prevale):\n$resolvedRequest"
        val texts = fetchCircularTexts(
            AssistantContext.mostRelevantCirculars(knowledge, searchQuery, PREFETCHED_CIRCULARS),
            knowledge.circulars, budgetMs = PREFETCH_BUDGET_MS
        )
        onAgentPhase(AssistantAgentPhase.PLANNING)
        val classifier = classifierFactory()
        suspend fun generate(repair: Boolean) = classifier.generateAnswer(
            AssistantActionPrompt.builderFor(knowledge, history, planningRequest, texts, searchQuery, repair)
                .withAttachmentServices(knowledge.attachedSources.map{it.file.service}.toSet())
        )
        var result = generate(false)
        var plan = (result as? AiTextResult.Success)?.let { AssistantActionPrompt.parse(it.text, knowledge) }
        if (result is AiTextResult.Success && (plan == null || !AssistantActionPrompt.validate(plan, knowledge))) {
            // Malformed model output is not missing user information. Retry once with
            // the compact schema rather than repeatedly asking for the same event title.
            result = generate(true)
            plan = (result as? AiTextResult.Success)?.let { AssistantActionPrompt.parse(it.text, knowledge) }
        }
        return when (val answer = result) {
            is AiTextResult.Failure -> AssistantReply(answer.reason, isError = true)
            is AiTextResult.Success -> {
                val verified = plan?.copy(requestedByUserId = knowledge.user?.id)
                if (verified == null || !AssistantActionPrompt.validate(verified, knowledge)) {
                    AssistantReply("Il modello non è riuscito a preparare la modifica. Nessun evento è stato modificato. Riprova oppure scegli un altro modello nelle impostazioni AI.",
                        modelLabel = "AILA ACA", isError = true)
                } else {
                    actionReply(verified)
                }
            }
        }
    }

    private fun actionReply(plan: AssistantActionPlan): AssistantReply = if (plan.actions.isEmpty()) {
        AssistantReply(plan.clarification.ifBlank { "Quali eventi vuoi creare o modificare e con quali dettagli?" },
            modelLabel = "AILA ACA", awaitingActionInput = true)
    } else {
        AssistantReply("Ho preparato queste azioni. Controlla eventi, date, orari e destinatari, poi conferma." +
            plan.clarification.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
            modelLabel = "AILA ACA", actionPlan = plan,
            awaitingActionInput = plan.clarification.isNotBlank())
    }

    /**
     * Le fonti dichiarate dal modello, tolte quelle che non possono essere vere.
     *
     * Un modello piccolo copia gli esempi e cita circolari a caso anche per rispondere a un
     * "ciao": una fonte sbagliata e' peggio di nessuna, perche' manda a leggere il documento
     * sbagliato. Una circolare resta fra le fonti solo se esiste, e se e' citata esplicitamente nella domanda o nella risposta,
     * oppure e' fra le piu' attinenti alla domanda. La sola lettura non e' una citazione.
     */
    private fun checkedSources(
        parsed: AssistantPrompt.ParsedAnswer,
        question: String,
        knowledge: AssistantKnowledge
    ): List<AssistantSource> {
        if (AssistantContext.isSmallTalk(knowledge, question)) return emptyList()
        val byNumber = knowledge.circulars.associateBy { it.number }
        val plausible = AssistantContext.circularNumbersIn(question) +
            AssistantContext.mostRelevantCirculars(knowledge, question, limit = 6)
        return parsed.sources.mapNotNull { source ->
            if (source.kind == AssistantSourceKind.GITA) {
                // Una fonte Gita vale solo se il titolo esiste davvero nel materiale: la sua
                // etichetta si ricostruisce dai dati, come per le circolari.
                val doc = knowledge.gita.firstOrNull { source.label.contains(it.title, ignoreCase = true) }
                    ?: return@mapNotNull null
                return@mapNotNull AssistantSource(
                    AssistantSourceKind.GITA,
                    AssistantContext.gitaSourceLabel(doc, knowledge.todayIso)
                )
            }
            if (source.kind != AssistantSourceKind.CIRCULAR) return@mapNotNull source
            val number = source.circularNumber ?: return@mapNotNull null
            val circular = byNumber[number] ?: return@mapNotNull null
            val cited = number in plausible ||
                number in AssistantContext.circularNumbersIn(parsed.answer)
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
            val bytes = downloadPdf(circular.r2PdfKey)
            var text = extractPdf(bytes)
            if (text.isNotBlank()) onText(text.take(MAX_PDF_CHARS_PER_CIRCULAR))

            for (attachment in circular.attachments) {
                val pdfKey = attachment.pdfKey ?: continue
                if (text.length >= MAX_PDF_CHARS_PER_CIRCULAR) break
                try {
                    val attachmentBytes = downloadPdf(pdfKey)
                    val attachmentText = extractPdf(attachmentBytes)
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

}
