package circolareplus.ai.assistant

import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.UserRole
import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.parseIsoDate
import circolareplus.util.plusDays

/**
 * Costruisce il blocco di CONTESTO che viene messo davanti alla domanda dell'utente.
 *
 * Il principio e' uno solo: **il modello puo' rispondere soltanto con quello che sta qui dentro**.
 * Non ha accesso a niente altro, non naviga, non chiama il server. Quindi questo file decide, di
 * fatto, a cosa l'assistente sa rispondere.
 *
 * Il compromesso di base: **l'indice di tutto, il dettaglio di quello che c'entra**. L'elenco
 * numero + titolo + data di *tutte* le circolari costa una sessantina di caratteri l'una e serve
 * al modello per sapere cosa esiste (e per dire "non c'e' nessuna circolare su questo" con
 * cognizione di causa invece che per assenza di dati); il riassunto AI completo lo ricevono solo
 * le circolari che hanno a che fare con la domanda.
 *
 * Quel compromesso pero' non e' fisso: dipende da **quanto spazio ha il motore che legge**. Fra
 * Gemini e un modello da 4 miliardi di parametri sul telefono ci sono due ordini di grandezza di
 * finestra di contesto, e un contesto unico o spreca la prima o fa fallire il secondo (vedi
 * [circolareplus.ai.AiPromptBuilder]). Per questo [render] prende un tetto in caratteri e da li'
 * ricava tutto il resto: quante circolari dettagliare, se l'indice completo ci sta, se lo storico
 * delle mappe vale il suo costo.
 */
internal object AssistantContext {

    /**
     * Sotto questa soglia il contesto passa in "modalita' stretta": niente indice completo delle
     * circolari, niente storico mappe, niente eventi passati. E' tarata sul caso reale che la
     * rende necessaria — un modello on-device da 4096 token in ingresso, cioe' circa novemila
     * caratteri fra istruzioni e richiesta.
     */
    private const val TIGHT_BUDGET_THRESHOLD = 14_000

    /**
     * Le misure di ogni sezione, ricavate dallo spazio disponibile.
     *
     * Sono scelte esplicite e non una troncatura finale perche' tagliare in fondo significa
     * tagliare *sempre le stesse sezioni* — quelle che stanno in coda — indipendentemente da
     * quanto servano. Meglio decidere in anticipo cosa sacrificare: in modalita' stretta sparisce
     * quello che serve di rado (lo storico delle mappe, le valutazioni, il passato del
     * calendario) e resta intero quello che serve quasi sempre (le scadenze in arrivo e le
     * circolari attinenti).
     */
    private class Budget(val maxChars: Int) {
        val tight = maxChars < TIGHT_BUDGET_THRESHOLD

        // Con il modello sul telefono meno circolari ma lette meglio: le due piu' attinenti
        // arrivano gia' col testo integrale (vedi [AilaAssistant]), e un terzo riassunto
        // toglierebbe spazio proprio a quel testo.
        val detailedCirculars = if (tight) 2 else 12
        val indexEntries = if (tight) 15 else 200
        val summaryChars = if (tight) 450 else 1_400
        val futureEvents = if (tight) 15 else 60
        val pastEvents = if (tight) 0 else 40
        val proposals = if (tight) 5 else 25
        val proposalDescriptionChars = if (tight) 140 else 400
        val includeSeatMapHistory = !tight
        val includeRatings = !tight
        val includePolls = true
        val deepTextChars = if (tight) 2_000 else 9_000
        val classmatesInHeader = !tight
    }

    /**
     * Il contesto completo per una domanda, entro [maxChars] caratteri.
     *
     * [deepTexts] contiene il testo estratto dai PDF delle circolari che il modello ha chiesto
     * esplicitamente di leggere al giro precedente (vedi [AilaAssistant]): e' vuoto alla prima
     * chiamata, perche' scaricare PDF "per sicurezza" a ogni domanda vorrebbe dire megabyte di
     * traffico per una domanda sul calendario.
     */
    fun render(
        knowledge: AssistantKnowledge,
        question: String,
        deepTexts: Map<Int, String> = emptyMap(),
        maxChars: Int = 28_000
    ): String {
        val budget = Budget(maxChars)
        val terms = tokenize(question)
        val explicitNumbers = circularNumbersIn(question)
        // "questa settimana", "domani"...: il filtro lo fa il codice (vedi TimeScope).
        val scope = TimeScopeParser.parse(question, knowledge.todayIso)
        val builder = StringBuilder()

        builder.appendSection("OGGI") {
            appendLine(readableDate(knowledge.todayIso, knowledge.todayIso, withYear = true).removeSuffix(" (oggi)"))
            if (scope != null) {
                appendLine(
                    "PERIODO CHIESTO: ${scope.describe()}. Le sezioni qui sotto contengono gia' " +
                        "solo gli eventi e le scadenze di questo periodo: rispondi con quelli, " +
                        "e se non ce ne sono dillo. Ignora ogni data fuori dal periodo, anche " +
                        "se la leggi in un riassunto."
                )
            }
        }

        builder.appendSection("CHI TI STA FACENDO LA DOMANDA") {
            val user = knowledge.user
            if (user == null) {
                appendLine("Utente non identificato.")
            } else {
                appendLine("Nome: ${user.firstName} ${user.lastName}")
                appendLine(
                    "Ruolo: " + when (user.role) {
                        UserRole.REPRESENTATIVE -> "Rappresentante di classe"
                        UserRole.STUDENT -> "Studente"
                        UserRole.SECURITY_GUARD -> "Personale di vigilanza"
                    }
                )
            }
            knowledge.profile?.let { profile ->
                if (profile.className.isNotBlank()) appendLine("Classe: ${profile.className}")
                if (profile.academicYear.isNotBlank()) appendLine("Anno scolastico: ${profile.academicYear}")
                appendLine("Priority Pass: ${if (profile.priorityPass) "si" else "no"}")
            }
            if (budget.classmatesInHeader && knowledge.classmates.isNotEmpty()) {
                appendLine(
                    "Compagni di classe (${knowledge.classmates.size}): " +
                        knowledge.classmates.joinToString(", ") { "${it.firstName} ${it.lastName}" }
                )
            }
        }

        renderCirculars(builder, knowledge, terms, explicitNumbers, deepTexts, budget, scope)
        renderCalendar(builder, knowledge, terms, budget, scope)
        renderBoard(builder, knowledge, terms, budget)
        if (budget.includePolls) renderPolls(builder, knowledge, scope)
        renderSeatMap(builder, knowledge, budget)
        renderClassData(builder, knowledge, budget)

        if (knowledge.dynamic.unavailable.isNotEmpty()) {
            builder.appendSection("SEZIONI CHE NON SI SONO CARICATE") {
                appendLine(
                    "Su questi argomenti NON hai i dati: non dire che non esistono, di' che non " +
                        "sei riuscito a leggerli."
                )
                knowledge.dynamic.unavailable.forEach { appendLine("- $it") }
            }
        }

        val text = builder.toString()
        return if (text.length <= maxChars) {
            text
        } else {
            // Rete di sicurezza, non il meccanismo principale: con le misure di [Budget] si
            // arriva qui di rado. Il taglio e' dichiarato invece che silenzioso, perche' un
            // modello che sa di avere un contesto troncato lo dice ("negli ultimi mesi non trovo
            // nulla") invece di affermare che una cosa non esiste perche' e' finita oltre il
            // taglio.
            val notice = "\n\n[CONTESTO TRONCATO: oltre questo punto i dati non ti sono stati " +
                "passati. Se la risposta dipendesse da li', dillo.]"
            text.take((maxChars - notice.length).coerceAtLeast(0)) + notice
        }
    }

    // -----------------------------------------------------------------------
    // Circolari
    // -----------------------------------------------------------------------

    private fun renderCirculars(
        builder: StringBuilder,
        knowledge: AssistantKnowledge,
        terms: List<String>,
        explicitNumbers: Set<Int>,
        deepTexts: Map<Int, String>,
        budget: Budget,
        scope: TimeScope?
    ) {
        if (knowledge.circulars.isEmpty()) {
            builder.appendSection("CIRCOLARI") { appendLine("Nessuna circolare scaricata.") }
            return
        }

        val indexed = knowledge.circulars.take(budget.indexEntries)
        val title = if (indexed.size < knowledge.circulars.size) {
            "INDICE DELLE ULTIME ${indexed.size} CIRCOLARI (di ${knowledge.circulars.size} in tutto)"
        } else {
            "INDICE DI TUTTE LE CIRCOLARI (${indexed.size})"
        }
        builder.appendSection(title) {
            appendLine("Formato: numero | data di pubblicazione | titolo")
            indexed.forEach { appendLine("${it.number} | ${it.publishDate} | ${it.title}") }
        }

        val ranked = knowledge.circulars
            .sortedByDescending { circular -> scoreCircular(circular, knowledge, terms, explicitNumbers) }
        // Con un periodo chiesto si dettagliano solo le circolari che c'entrano con quel
        // periodo: una scadenza al suo interno, o la pubblicazione. Le altre restano nell'indice.
        val detailed = if (scope == null) {
            ranked.take(budget.detailedCirculars)
        } else {
            ranked.filter { circular ->
                circular.number in explicitNumbers ||
                    scope.contains(circular.publishDate) ||
                    knowledge.classifications[circular.number]?.detectedDeadlines.orEmpty()
                        .any { scope.contains(it.dueDate) }
            }.take(budget.detailedCirculars)
        }

        builder.appendSection("CIRCOLARI PIU' ATTINENTI ALLA DOMANDA") {
            appendLine(
                "Solo di queste hai il riassunto. Se la domanda riguarda una circolare " +
                    "dell'indice che qui non compare, chiedine il testo con needsCircularText."
            )
            if (scope != null && detailed.isEmpty()) {
                appendLine("Nessuna circolare ha scadenze o e' stata pubblicata in questo periodo.")
            }
            detailed.forEach { circular ->
                appendLine("")
                appendLine("--- Circolare n. ${circular.number} (${circular.publishDate}) ---")
                appendLine("Titolo: ${circular.title}")
                val analysis = knowledge.classifications[circular.number]
                if (budget.tight && circular.number in deepTexts) {
                    // Con poco spazio il riassunto e il testo integrale della stessa circolare
                    // sarebbero un doppione: resta il testo, che contiene tutto.
                    appendLine("Testo integrale nella sezione piu' sotto.")
                } else if (analysis == null) {
                    appendLine("Analisi AI: non ancora prodotta per questa circolare.")
                } else {
                    appendLine(
                        "Pertinenza per te: " + when (analysis.badge) {
                            CircularRelevanceBadge.RELEVANT -> "ti riguarda"
                            CircularRelevanceBadge.POTENTIAL -> "potrebbe interessarti"
                            CircularRelevanceBadge.NOT_RELEVANT -> "non sembra riguardarti"
                        }
                    )
                    // Un riassunto di ripiego e' testo scritto da un'euristica a parole chiave,
                    // non dal documento: se il modello lo trattasse come contenuto della
                    // circolare ne ricaverebbe affermazioni che il PDF non contiene.
                    if (analysis.isFallback) {
                        appendLine(
                            "ATTENZIONE: questo NON e' un riassunto del documento, e' un " +
                                "messaggio di errore dell'analisi. Non ricavarne contenuti."
                        )
                    }
                    appendLine("Riassunto: ${analysis.personalSummary.take(budget.summaryChars)}")
                    val deadlines = if (scope == null) {
                        analysis.detectedDeadlines
                    } else {
                        analysis.detectedDeadlines.filter { scope.contains(it.dueDate) }
                    }
                    if (deadlines.isNotEmpty()) {
                        appendLine(if (scope == null) "Scadenze rilevate:" else "Scadenze nel periodo:")
                        deadlines.forEach { deadline ->
                            appendLine(
                                "  - ${readableDate(deadline.dueDate, knowledge.todayIso)}" +
                                    (deadline.time?.takeIf { it.isNotBlank() }?.let { ", ore $it" } ?: "") +
                                    " — ${deadline.title}" +
                                    (categoryLabel(deadline.category)?.let { " ($it)" } ?: "")
                            )
                        }
                    }
                }
                if (circular.attachments.isNotEmpty()) {
                    appendLine("Allegati: ${circular.attachments.joinToString(", ") { it.label }}")
                }
            }
        }

        if (deepTexts.isNotEmpty()) {
            builder.appendSection("TESTO INTEGRALE DELLE CIRCOLARI PIU' ATTINENTI") {
                appendLine(
                    "Dei documenti lunghi ci sono solo l'inizio e i passaggi che c'entrano con la " +
                        "domanda; \"[...]\" indica una parte saltata. Se la risposta non c'e', dillo."
                )
                deepTexts.forEach { (number, text) ->
                    val circularTitle = knowledge.circulars.firstOrNull { it.number == number }?.title ?: ""
                    appendLine("")
                    appendLine("--- Testo della circolare n. $number: $circularTitle ---")
                    appendLine(PassageSelector.select(text, question, budget.deepTextChars))
                }
            }
        }
    }

    /**
     * Quanto una circolare c'entra con la domanda.
     *
     * Il numero citato esplicitamente vince su tutto: "cosa dice la 214" deve portare la 214 in
     * cima anche se il suo titolo non contiene nessuna delle parole della domanda. Sotto, le
     * parole pesano di piu' nel titolo che nel riassunto, perche' il titolo e' scritto dalla
     * scuola e il riassunto da un modello. A parita' di punteggio vince la piu' recente: e'
     * quasi sempre quella di cui si sta parlando.
     */
    private fun scoreCircular(
        circular: Circular,
        knowledge: AssistantKnowledge,
        terms: List<String>,
        explicitNumbers: Set<Int>
    ): Double {
        var score = 0.0
        if (circular.number in explicitNumbers) score += 1_000.0

        val title = normalize(circular.title)
        val summary = normalize(knowledge.classifications[circular.number]?.personalSummary ?: "")
        terms.forEach { term ->
            // Si confronta la radice e non la parola intera: "scienze" deve trovare anche
            // "scientifiche", "sportelli" anche "sportello".
            val stem = stemOf(term)
            if (title.contains(stem)) score += 6.0
            if (summary.contains(stem)) score += 2.5
        }
        // Spareggio sulla recenza, sempre minore del peso di una singola parola trovata.
        score += circular.number.toDouble() / 100_000.0
        return score
    }

    /**
     * Sotto questo punteggio una circolare non c'entra davvero con la domanda: serve almeno una
     * parola nel titolo, o due nel riassunto. Lo spareggio sulla recenza da solo non basta.
     */
    private const val RELEVANCE_THRESHOLD = 5.0

    /**
     * Le circolari da leggere per intero prima ancora di chiedere al modello (vedi
     * [AilaAssistant]): al massimo [limit], e solo quelle che c'entrano davvero con la domanda.
     * Vuota per saluti, domande generali e domande su un periodo ("cosa ho questa settimana"),
     * che si reggono su calendario e scadenze e non sul testo di una circolare.
     */
    fun mostRelevantCirculars(knowledge: AssistantKnowledge, question: String, limit: Int): List<Int> {
        if (TimeScopeParser.parse(question, knowledge.todayIso) != null) return emptyList()
        val terms = tokenize(question)
        val explicitNumbers = circularNumbersIn(question)
        return knowledge.circulars
            .map { it to scoreCircular(it, knowledge, terms, explicitNumbers) }
            .filter { (_, score) -> score >= RELEVANCE_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (circular, _) -> circular.number }
    }

    /**
     * `true` per un saluto o una domanda senza nessun aggancio ai dati ("ciao", "grazie").
     * Serve a non allegare fonti a una risposta che non ne ha: un modello piccolo altrimenti
     * cita la prima circolare che vede.
     */
    fun isSmallTalk(knowledge: AssistantKnowledge, question: String): Boolean =
        tokenize(question).isEmpty() &&
            circularNumbersIn(question).isEmpty() &&
            TimeScopeParser.parse(question, knowledge.todayIso) == null

    /** Radice approssimata di una parola italiana: bastano le prime lettere oltre la desinenza. */
    internal fun stemOf(term: String): String =
        if (term.length >= 6) term.dropLast(2) else term

    // -----------------------------------------------------------------------
    // Calendario
    // -----------------------------------------------------------------------

    private fun renderCalendar(
        builder: StringBuilder,
        knowledge: AssistantKnowledge,
        terms: List<String>,
        budget: Budget,
        scope: TimeScope?
    ) {
        if (knowledge.calendarEvents.isEmpty()) {
            builder.appendSection("CALENDARIO") { appendLine("Nessun evento in calendario.") }
            return
        }

        // Periodo chiesto: solo gli eventi di quell'intervallo, passati o futuri che siano, al
        // posto delle due sezioni generiche qui sotto.
        if (scope != null) {
            val inRange = knowledge.calendarEvents.filter { scope.contains(it.date) }.sortedBy { it.date }
            builder.appendSection("CALENDARIO — EVENTI DEL PERIODO CHIESTO") {
                if (inRange.isEmpty()) appendLine("Nessun evento in calendario in questo periodo.")
                inRange.take(budget.futureEvents + budget.pastEvents).forEach { appendLine(formatEvent(it, knowledge)) }
            }
            return
        }

        val (future, past) = knowledge.calendarEvents
            .sortedBy { it.date }
            .partition { it.date >= knowledge.todayIso }

        builder.appendSection("CALENDARIO — EVENTI DA OGGI IN POI") {
            if (future.isEmpty()) appendLine("Nessun evento futuro.")
            future.take(budget.futureEvents).forEach { appendLine(formatEvent(it, knowledge)) }
        }

        if (budget.pastEvents == 0) return

        // Il passato serve alle domande del tipo "quando abbiamo fatto la gita?", ma tenerlo
        // tutto costerebbe quanto il futuro senza servire quasi mai: si tengono gli ultimi
        // quindici e quelli che contengono una parola della domanda.
        val relevantPast = (past.takeLast(15) + past.filter { event ->
            val title = normalize(event.title)
            terms.any { title.contains(it) }
        }).distinctBy { it.id }.sortedBy { it.date }

        if (relevantPast.isNotEmpty()) {
            builder.appendSection("CALENDARIO — EVENTI GIA' PASSATI (parziale)") {
                relevantPast.take(budget.pastEvents).forEach { appendLine(formatEvent(it, knowledge)) }
            }
        }
    }

    private fun formatEvent(
        event: circolareplus.domain.model.CalendarEvent,
        knowledge: AssistantKnowledge
    ): String {
        val visible = event.visibleToUserIds
        val recipients = when {
            event.isForAll -> "tutta la classe"
            visible.isNullOrEmpty() -> "tutta la classe"
            else -> visible
                .mapNotNull { knowledge.nameOf(it) }
                .ifEmpty { listOf("alcuni studenti") }
                .joinToString("/")
        }
        // Una riga gia' nella forma in cui va mostrata: "- venerdi' 25 settembre, ore 14:15 —
        // Incontro (avviso)". Il modello la copia cosi' com'e'. Prima riceveva
        // "2026-09-25 (venerdi' 25 settembre) | 14:15 | AVVISO | ..." e i modelli piccoli
        // ricopiavano pipe, categorie in maiuscolo e date ISO storpiate ("206-9-27").
        return buildString {
            append("- ").append(readableDate(event.date, knowledge.todayIso))
            event.time?.takeIf { it.isNotBlank() }?.let { append(", ore ").append(it) }
            append(" — ").append(event.title)
            event.category.readable()?.let { append(" (").append(it).append(")") }
            if (recipients != "tutta la classe") append(", solo per ").append(recipients)
            if (event.isAiGenerated) append(" [creato dall'AI da una circolare]")
            event.notes?.takeIf { it.isNotBlank() }?.let { append(". Note: ").append(it.take(200)) }
        }
    }

    // -----------------------------------------------------------------------
    // Bacheca
    // -----------------------------------------------------------------------

    private fun renderBoard(
        builder: StringBuilder,
        knowledge: AssistantKnowledge,
        terms: List<String>,
        budget: Budget
    ) {
        if (knowledge.proposals.isEmpty()) {
            builder.appendSection("BACHECA") { appendLine("Nessuna proposta in bacheca.") }
            return
        }

        val ranked = knowledge.proposals.sortedByDescending { proposal ->
            val haystack = normalize(proposal.title + " " + proposal.description)
            terms.count { haystack.contains(it) } * 10.0 + (proposal.upvotes - proposal.downvotes)
        }

        builder.appendSection("BACHECA — PROPOSTE DELLA CLASSE (${knowledge.proposals.size})") {
            ranked.take(budget.proposals).forEach { proposal ->
                appendLine("")
                appendLine(
                    "[${proposal.status.name}] ${proposal.title} — di ${proposal.authorName}" +
                        " — ${proposal.upvotes} a favore / ${proposal.downvotes} contrari" +
                        " — ${proposal.commentsCount} commenti — ${proposal.createdAt}" +
                        (if (proposal.isEdited) " — modificata dopo la pubblicazione" else "")
                )
                appendLine("Categoria: ${proposal.category}")
                appendLine(proposal.description.take(budget.proposalDescriptionChars))
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sondaggi
    // -----------------------------------------------------------------------

    private fun renderPolls(builder: StringBuilder, knowledge: AssistantKnowledge, scope: TimeScope?) {
        val dynamic = knowledge.dynamic
        if (dynamic.polls.isEmpty() && dynamic.currentPoll == null) {
            builder.appendSection("SONDAGGI") { appendLine("Nessun sondaggio.") }
            return
        }

        builder.appendSection("SONDAGGI (verifiche e interrogazioni programmate)") {
            dynamic.polls.take(15).forEach { poll ->
                appendLine(
                    "${poll.subject} | creato ${poll.createdAt} | " +
                        (if (poll.isPublished) "pubblicato" else "bozza") + " | " +
                        "${poll.submittedCount}/${poll.totalStudents} hanno votato | " +
                        (if (poll.isCalculated) "assegnazioni calcolate" else "assegnazioni non ancora calcolate")
                )
            }

            dynamic.currentPoll?.let { poll ->
                appendLine("")
                appendLine("--- Sondaggio attivo: ${poll.subject} ---")
                appendLine("Il tuo bonus sacrificio: ${poll.mySacrificeBonus}")
                appendLine("Date proposte (posti, il tuo voto, preferenze della classe):")
                val slots = if (scope == null) poll.slots else poll.slots.filter { scope.contains(it.slotDate) }
                if (scope != null && slots.isEmpty()) {
                    appendLine("Nessuna data di questo sondaggio cade nel periodo chiesto.")
                }
                slots.forEach { slot ->
                    appendLine(
                        "- ${readableDate(slot.slotDate, knowledge.todayIso)}: ${slot.capacity} posti; " +
                            "tuo voto: ${slot.myVote?.toString() ?: "non votato"}; " +
                            "verde ${slot.counts.green}, giallo ${slot.counts.yellow}, " +
                            "rosso chiaro ${slot.counts.redLight}, rosso scuro ${slot.counts.redDark}" +
                            (if (slot.teacherMandatory) "; data imposta dal docente" else "")
                    )
                }
            }

            if (dynamic.pollAssignments.isNotEmpty()) {
                appendLine("")
                appendLine("--- Assegnazioni calcolate ---")
                dynamic.pollAssignments.forEach { assignment ->
                    val name = assignment.studentName
                        ?: knowledge.nameOf(assignment.studentId)
                        ?: "studente"
                    appendLine("- ${assignment.slotDate?.let { readableDate(it, knowledge.todayIso) } ?: "data ignota"}: $name")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mappa posti
    // -----------------------------------------------------------------------

    private fun renderSeatMap(builder: StringBuilder, knowledge: AssistantKnowledge, budget: Budget) {
        val dynamic = knowledge.dynamic
        if (dynamic.seatMap.isEmpty()) {
            builder.appendSection("MAPPA POSTI") { appendLine("Nessuna mappa posti pubblicata.") }
        } else {
            builder.appendSection("MAPPA POSTI ATTUALMENTE PUBBLICATA") {
                appendLine("Fila 1 = la piu' vicina alla cattedra. Banco 1 = il piu' a sinistra.")
                dynamic.seatMap
                    .sortedWith(compareBy({ it.row }, { it.column }))
                    .forEach { desk ->
                        val a = knowledge.nameOf(desk.studentAId) ?: "posto libero"
                        val b = knowledge.nameOf(desk.studentBId) ?: "posto libero"
                        appendLine("Fila ${desk.row + 1}, banco ${desk.column + 1}: $a / $b")
                    }
                val myId = knowledge.user?.id
                if (myId != null) {
                    val mine = dynamic.seatMap.firstOrNull { it.studentAId == myId || it.studentBId == myId }
                    if (mine == null) {
                        appendLine("Tu non risulti assegnato a nessun banco in questa mappa.")
                    } else {
                        val deskMateId = if (mine.studentAId == myId) mine.studentBId else mine.studentAId
                        appendLine(
                            "Il tuo posto: fila ${mine.row + 1}, banco ${mine.column + 1}, " +
                                "compagno di banco: ${knowledge.nameOf(deskMateId) ?: "nessuno"}"
                        )
                    }
                }
            }
        }

        if (budget.includeSeatMapHistory && dynamic.seatMapHistory.isNotEmpty()) {
            builder.appendSection("STORICO MAPPE POSTI (visibile solo al Rappresentante)") {
                appendLine("N-1 e' la mappa precedente a quella attuale, N-4 la piu' vecchia tenuta.")
                dynamic.seatMapHistory.sortedBy { it.mapIndex }.forEach { record ->
                    appendLine("")
                    appendLine("--- Mappa N-${record.mapIndex} ---")
                    record.deskAssignments.entries
                        .sortedWith(compareBy({ it.value.first }, { it.value.second }))
                        .forEach { (studentId, position) ->
                            val name = knowledge.nameOf(studentId) ?: "studente non piu' in elenco"
                            appendLine("Fila ${position.first + 1}, banco ${position.second + 1}: $name")
                        }
                    if (record.pairs.isNotEmpty()) {
                        appendLine(
                            "Coppie di banco: " + record.pairs.joinToString("; ") { pair ->
                                val first = knowledge.nameOf(pair.first) ?: "?"
                                val second = knowledge.nameOf(pair.second) ?: "?"
                                "$first + $second"
                            }
                        )
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Dati di classe (valutazioni, preferenze proprie)
    // -----------------------------------------------------------------------

    private fun renderClassData(builder: StringBuilder, knowledge: AssistantKnowledge, budget: Budget) {
        val dynamic = knowledge.dynamic

        if (budget.includeRatings && dynamic.ratings.isNotEmpty()) {
            builder.appendSection("VALUTAZIONI DELLA CLASSE (visibili solo al Rappresentante)") {
                appendLine(
                    "Didattica: 1 = in difficolta', 5 = eccellente. Chiasso: 1 = silenzioso, " +
                        "5 = molto chiassoso. Servono all'ottimizzatore della mappa posti."
                )
                dynamic.ratings.forEach { rating ->
                    appendLine(
                        "${rating.firstName} ${rating.lastName} | " +
                            "didattica ${rating.didactic?.toString() ?: "non valutata"} | " +
                            "chiasso ${rating.behavior?.toString() ?: "non valutato"} | " +
                            "Priority Pass ${if (rating.priorityPass) "si" else "no"}" +
                            (rating.heightCm?.let { " | altezza $it cm" } ?: "")
                    )
                }
            }
        }

        builder.appendSection("PREFERENZE SOCIALI") {
            appendLine(
                "Finestra di voto: " + when (dynamic.preferencesOpen) {
                    true -> "aperta, si puo' votare"
                    false -> "chiusa"
                    null -> "stato sconosciuto"
                }
            )
            appendLine(
                "Le preferenze degli ALTRI studenti non ti sono state passate e non ti saranno " +
                    "mai passate: nessuno in questa app puo' vedere chi ha votato cosa su chi. " +
                    "Se te le chiedono, spiega che non sono consultabili da nessuno."
            )
            if (dynamic.myPreferences.isEmpty()) {
                appendLine("Non hai espresso nessuna preferenza.")
            } else {
                appendLine("Le TUE preferenze (solo tue, visibili solo a te):")
                dynamic.myPreferences.forEach { vote ->
                    val meaning = when (vote.score) {
                        2 -> "affinita' forte"
                        1 -> "gradimento"
                        -1 -> "preferirei di no"
                        -2 -> "rifiuto assoluto"
                        else -> "indifferente"
                    }
                    appendLine("- ${vote.toName}: ${vote.score} ($meaning)")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utilita' di testo
    // -----------------------------------------------------------------------

    /**
     * "venerdi' 25 settembre", con "(oggi)"/"(domani)" quando serve e l'anno solo se diverso da
     * quello in corso. E' la forma in cui ogni data arriva al modello: niente AAAA-MM-GG da
     * convertire, perche' e' proprio la conversione che i modelli sul telefono sbagliano.
     */
    internal fun readableDate(iso: String, todayIso: String, withYear: Boolean = false): String {
        val date = parseIsoDate(iso.take(10)) ?: return iso
        val base = formatItalianDateWithWeekday(iso)
        val today = parseIsoDate(todayIso.take(10))
        val year = if (withYear || today == null || today.year != date.year) " ${date.year}" else ""
        val relative = when {
            today == null -> ""
            date == today -> " (oggi)"
            date == today.plusDays(1) -> " (domani)"
            date == today.plusDays(-1) -> " (ieri)"
            else -> ""
        }
        return base + year + relative
    }

    private fun CalendarEventCategory.readable(): String? = when (this) {
        CalendarEventCategory.VERIFICA -> "verifica"
        CalendarEventCategory.INTERROGAZIONE -> "interrogazione"
        CalendarEventCategory.PAGAMENTO -> "pagamento"
        CalendarEventCategory.USCITA_DIDATTICA -> "uscita didattica"
        CalendarEventCategory.AVVISO -> "avviso"
        CalendarEventCategory.ALTRO -> null
    }

    /** Le categorie delle scadenze sono stringhe libere del classificatore: si traducono se note. */
    private fun categoryLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return CalendarEventCategory.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?.readable()
            ?: raw.trim().lowercase().replace('_', ' ')
    }

    private inline fun StringBuilder.appendSection(title: String, body: StringBuilder.() -> Unit) {
        append("\n=== ").append(title).append(" ===\n")
        body()
    }

    /**
     * Parole della domanda utili a cercare: minuscole, senza accenti e senza le parole troppo
     * comuni, che altrimenti farebbero risultare "attinente" qualunque cosa.
     */
    internal fun tokenize(text: String): List<String> =
        normalize(text)
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()

    /** Minuscole, accenti tolti, tutto cio' che non e' lettera o cifra diventa spazio. */
    internal fun normalize(text: String): String = buildString {
        text.lowercase().forEach { char ->
            val replacement = when (char) {
                'à', 'á', 'â', 'ä' -> 'a'
                'è', 'é', 'ê', 'ë' -> 'e'
                'ì', 'í', 'î', 'ï' -> 'i'
                'ò', 'ó', 'ô', 'ö' -> 'o'
                'ù', 'ú', 'û', 'ü' -> 'u'
                else -> char
            }
            append(if (replacement.isLetterOrDigit()) replacement else ' ')
        }
    }

    /**
     * Numeri di circolare citati nella domanda.
     *
     * Solo quelli introdotti da "circolare"/"circ."/"n." o da un cancelletto: un numero isolato
     * ("quanti giorni di gita a maggio 2026?") non e' un riferimento a una circolare, e trattarlo
     * come tale porterebbe in cima alla ricerca una circolare a caso.
     */
    internal fun circularNumbersIn(question: String): Set<Int> {
        val normalized = normalize(question)
        val words = normalized.split(' ').filter { it.isNotBlank() }
        val result = mutableSetOf<Int>()
        words.forEachIndexed { index, word ->
            val isMarker = word == "circolare" || word == "circolari" || word == "circ" ||
                word == "n" || word == "num" || word == "numero"
            if (isMarker) {
                words.getOrNull(index + 1)?.toIntOrNull()?.let { result += it }
                words.getOrNull(index + 2)?.toIntOrNull()?.let { result += it }
            }
        }
        // "#214" — normalize() ha gia' trasformato il cancelletto in spazio, quindi si guarda il
        // testo originale.
        Regex("#\\s*(\\d{1,5})").findAll(question).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { result += it }
        }
        return result
    }

    private val STOPWORDS = setOf(
        "che", "cosa", "come", "quando", "dove", "chi", "per", "con", "del", "della", "dei",
        "delle", "dal", "dalla", "nel", "nella", "una", "uno", "gli", "sono", "essere", "hai",
        "sai", "dimmi", "mio", "mia", "miei", "mie", "sul", "sulla", "questo", "questa", "quale",
        "quali", "piu", "meno", "tutti", "tutte", "tutto", "non", "anche", "ancora", "solo",
        "ciao", "grazie", "puoi", "devo", "posso", "fare", "quanto", "quanti", "quante", "ho",
        "mi", "ti", "si", "la", "le", "lo", "il", "un", "di", "da", "in", "su", "tra", "fra",
        // Saluti e verbi di contorno: non dicono niente su quale circolare serva.
        "buongiorno", "buonasera", "salve", "hey", "trovo", "trova", "trovare", "vorrei",
        "sapere", "serve", "servono", "giorni", "giorno", "aiutarmi", "aiuto", "bene", "okay"
    )
}
