package circolareplus.ai.assistant

import circolareplus.ai.AiPrompt
import circolareplus.ai.AiPromptBuilder
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge

/**
 * Le domande-elenco sulle circolari ("riassumimi le ultime circolari che mi riguardano", "ci
 * sono circolari nuove?", "le circolari di questa settimana") risposte dal codice.
 *
 * Il motivo e' lo stesso di [AssistantAgenda] e [AssistantBoard], con un'aggravante: qui il
 * modello doveva riscrivere cinque o sei riassunti di fila, e Gemini Nano ha una finestra di
 * circa 4000 token e un'uscita pensata per poche centinaia. La risposta si fermava dopo la
 * prima riga ("Ecco il riassunto delle circolari che ti riguardano direttamente:"). Ma i
 * riassunti esistono gia': li ha scritti l'analisi di ogni circolare. Metterli in fila, in
 * ordine di data e filtrati per pertinenza, e' lavoro da codice.
 *
 * Al modello resta la parte in cui aggiunge qualcosa: una o due frasi in cima che dicono cosa
 * e' piu' urgente (vedi [introPrompt]). Sono poche decine di token, ben dentro i limiti di
 * Nano, e se non arrivano o non convincono l'elenco basta da solo.
 *
 * Come gli altri elenchi, scatta solo se ogni parola della domanda e' una parola "da elenco":
 * "cosa dice la circolare sulla gita?" contiene un argomento e resta al modello.
 */
internal object AssistantCirculars {

    /** Senza periodo, "le ultime" sono queste. */
    private const val DEFAULT_ITEMS = 5

    /** Con un periodo ("questa settimana") se ne mostrano di piu', ma non all'infinito. */
    private const val MAX_ITEMS = 10

    /** Lunghezza del riassunto per voce: una o due frasi, il resto e' nella circolare. */
    private const val SUMMARY_CHARS = 240

    /** Lunghezza massima accettata per le frasi del modello: oltre, non sono piu' "due frasi". */
    private const val MAX_INTRO_CHARS = 420

    internal const val MODEL_LABEL = "Dalle circolari di AILA"

    internal data class Request(val mineOnly: Boolean, val scope: TimeScope?)

    /** L'elenco pronto e, se c'e' qualcosa da commentare, il prompt per le frasi in cima. */
    internal data class Listing(
        val reply: AssistantReply,
        val shown: List<Circular>,
        val introPrompt: AiPromptBuilder?
    )

    /** L'elenco, o `null` se la domanda non e' un elenco di circolari. */
    fun answer(knowledge: AssistantKnowledge, question: String): Listing? {
        val request = requestOf(knowledge, question) ?: return null

        if (knowledge.circulars.isEmpty()) {
            return Listing(
                AssistantReply(
                    text = "Non ho ancora nessuna circolare: aprile dalla scheda Circolari per scaricarle.",
                    modelLabel = MODEL_LABEL
                ),
                emptyList(),
                null
            )
        }

        val newestFirst = knowledge.circulars.sortedWith(
            compareByDescending<Circular> { it.publishDate.take(10) }.thenByDescending { it.number }
        )
        val inScope = request.scope?.let { scope -> newestFirst.filter { scope.contains(it.publishDate) } }
            ?: newestFirst
        val analysed = inScope.filter { knowledge.classifications[it.number]?.isFallback == false }
        val candidates = if (request.mineOnly) {
            // Prima quelle che ti riguardano di sicuro, poi quelle di possibile interesse; sempre
            // in ordine di data dentro ciascun gruppo.
            analysed.filter { badgeOf(knowledge, it) == CircularRelevanceBadge.RELEVANT } +
                analysed.filter { badgeOf(knowledge, it) == CircularRelevanceBadge.POTENTIAL }
        } else {
            inScope
        }
        val limit = if (request.scope != null) MAX_ITEMS else DEFAULT_ITEMS
        val shown = candidates.take(limit)
        // Le circolari del periodo mai analizzate: con "mi riguardano" non si puo' dire se ti
        // riguardano, e va detto invece di farle sparire in silenzio.
        val notAnalysed = if (request.mineOnly) inScope.size - analysed.size else 0
        val period = request.scope?.describe()

        val text = buildString {
            when {
                inScope.isEmpty() -> append("Non ci sono circolari pubblicate ${period ?: "di recente"}.")
                shown.isEmpty() -> {
                    append("Fra le circolari ")
                    append(if (period != null) "pubblicate $period" else "recenti")
                    append(" non ne trovo che ti riguardino.")
                }
                else -> {
                    append(heading(request, period, shown.size))
                    shown.forEach { circular ->
                        appendLine()
                        append(line(circular, knowledge))
                    }
                    val hidden = candidates.size - shown.size
                    if (hidden > 0) {
                        appendLine().appendLine()
                        append("…e altre $hidden: le trovi nella scheda Circolari.")
                    }
                }
            }
            if (notAnalysed > 0) {
                appendLine().appendLine()
                append(
                    if (notAnalysed == 1) "Una circolare non e' ancora stata analizzata, quindi non so se ti riguarda."
                    else "$notAnalysed circolari non sono ancora state analizzate, quindi non so se ti riguardano."
                )
            }
        }

        return Listing(
            reply = AssistantReply(
                text = text,
                sources = shown.take(6).map {
                    AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. ${it.number} — ${it.title}", it.number)
                },
                modelLabel = MODEL_LABEL
            ),
            shown = shown,
            introPrompt = if (shown.isEmpty()) null else introPrompt(shown, knowledge)
        )
    }

    /**
     * Che elenco chiede la domanda, o `null` se non e' un elenco di circolari: niente parola
     * "circolare", un numero di circolare preciso, o un argomento fra le parole.
     */
    internal fun requestOf(knowledge: AssistantKnowledge, question: String): Request? {
        if (AssistantContext.circularNumbersIn(question).isNotEmpty()) return null
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        if (words.none { it.startsWith("circolar") }) return null
        val terms = AssistantContext.tokenize(question)
        if (terms.any { term -> CIRCULAR_WORDS.none { term.startsWith(it) } }) return null

        fun has(vararg stems: String) = words.any { word -> stems.any { word.startsWith(it) } }
        val phrase = " " + words.joinToString(" ") + " "
        val mineOnly = has("riguard", "interess", "rilevant", "pertinent", "important") ||
            phrase.contains(" per me ") || phrase.contains(" mie ")
        return Request(mineOnly = mineOnly, scope = TimeScopeParser.parse(question, knowledge.todayIso))
    }

    private fun badgeOf(knowledge: AssistantKnowledge, circular: Circular): CircularRelevanceBadge? =
        knowledge.classifications[circular.number]?.takeIf { !it.isFallback }?.badge

    private fun heading(request: Request, period: String?, count: Int): String = buildString {
        append(
            when {
                request.mineOnly && count == 1 -> "La circolare che ti riguarda"
                request.mineOnly -> "Le circolari che ti riguardano"
                count == 1 -> "La circolare"
                period != null -> "Le circolari"
                else -> "Le ultime $count circolari"
            }
        )
        if (period != null) append(" pubblicate ").append(period)
        append(':')
    }

    private fun line(circular: Circular, knowledge: AssistantKnowledge): String = buildString {
        append("• n. ").append(circular.number).append(" — ").append(circular.title.trim())
        append(" (").append(AssistantContext.readableDate(circular.publishDate, knowledge.todayIso)).append(')')
        val analysis = knowledge.classifications[circular.number]?.takeIf { !it.isFallback }
        if (analysis == null) {
            appendLine().append("  Non ancora analizzata: aprila per leggerla.")
            return@buildString
        }
        if (analysis.badge == CircularRelevanceBadge.POTENTIAL) append(" — potrebbe interessarti")
        val summary = shortSummary(analysis)
        if (summary.isNotEmpty()) appendLine().append("  ").append(summary)
        nextDeadline(analysis, knowledge.todayIso)?.let { appendLine().append("  Scadenza: ").append(it) }
    }

    /** Le prime frasi del riassunto, tagliate a fine frase quando si puo'. */
    internal fun shortSummary(analysis: CircularAiClassification): String {
        val text = analysis.personalSummary.replace(Regex("\\s+"), " ").trim()
        if (text.length <= SUMMARY_CHARS) return text
        val cut = text.take(SUMMARY_CHARS)
        val sentenceEnd = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (sentenceEnd >= SUMMARY_CHARS / 3) cut.take(sentenceEnd + 1) else cut.trimEnd() + "…"
    }

    /** La prima scadenza da oggi in poi, gia' scritta come va mostrata. */
    private fun nextDeadline(analysis: CircularAiClassification, todayIso: String): String? {
        val deadline = analysis.detectedDeadlines
            .filter { it.dueDate.take(10) >= todayIso.take(10) }
            .minByOrNull { it.dueDate.take(10) + (it.time ?: "") }
            ?: return null
        return buildString {
            append(AssistantContext.readableDate(deadline.dueDate, todayIso))
            deadline.time?.takeIf { it.isNotBlank() }?.let { append(", ore ").append(it) }
            append(" — ").append(deadline.title.trim())
        }
    }

    /**
     * Il prompt per le frasi in cima all'elenco: piccolo per costruzione (i dati sono gia' le
     * righe dell'elenco), quindi entra anche nella finestra di Gemini Nano senza tagli.
     *
     * La nota sul JSON c'e' perche' il client di Gemini chiede risposte in JSON: cosi' il
     * modello cloud sa che forma dare, e quello sul telefono scrive le frasi e basta.
     */
    private fun introPrompt(shown: List<Circular>, knowledge: AssistantKnowledge): AiPromptBuilder {
        val data = shown.joinToString("\n") { line(it, knowledge) }
        return AiPromptBuilder { maxChars ->
            AiPrompt(
                systemPrompt = INTRO_SYSTEM_PROMPT,
                userPrompt = buildString {
                    appendLine("CIRCOLARI (oggi e' ${AssistantContext.readableDate(knowledge.todayIso, knowledge.todayIso)}):")
                    appendLine(data.take((maxChars - INTRO_SYSTEM_PROMPT.length - 200).coerceAtLeast(500)))
                    appendLine()
                    append("Scrivi ora le frasi.")
                }
            )
        }
    }

    private val INTRO_SYSTEM_PROMPT = """
Sei l'assistente di un'app scolastica. Sotto trovi un elenco di circolari che lo studente vedra' per intero subito dopo le tue parole.
Scrivi AL MASSIMO 2 frasi brevi (40 parole in tutto) che dicano cosa conta di piu': una scadenza vicina, qualcosa da fare o da portare. Se non c'e' niente di urgente, dillo in una frase.
Non ripetere l'elenco, non salutare, niente premesse. Usa solo i dati dell'elenco: numeri e date COPIATI da li', mai inventati.
Scrivi solo le frasi, in testo semplice. Se devi rispondere in JSON usa {"answer":"le frasi"}.
""".trim()

    /**
     * Le frasi del modello, se sono utilizzabili; altrimenti `null` e si mostra l'elenco da solo.
     *
     * Il controllo che conta e' sui numeri: ogni cifra delle frasi deve comparire nei dati
     * dati al modello. Una frase in cima che cita una circolare o una data inesistente e'
     * esattamente l'errore che l'elenco fatto dal codice serve a evitare.
     */
    internal fun acceptIntro(raw: String, shown: List<Circular>, knowledge: AssistantKnowledge): String? {
        val intro = AssistantPrompt.parse(raw).answer.trim()
        if (intro.isEmpty() || intro.length > MAX_INTRO_CHARS) return null
        if (intro.contains("interrott", ignoreCase = true)) return null
        if (intro.lines().count { it.isNotBlank() } > 3) return null
        val data = shown.joinToString("\n") { line(it, knowledge) } + " " +
            AssistantContext.readableDate(knowledge.todayIso, knowledge.todayIso)
        val known = Regex("\\d+").findAll(data).map { it.value.trimStart('0') }.toSet()
        val invented = Regex("\\d+").findAll(intro).any { it.value.trimStart('0') !in known }
        return if (invented) null else intro
    }

    /**
     * Le radici delle parole ammesse in una domanda-elenco sulle circolari, oltre alle
     * stopword. Tutto il resto e' un argomento, e la domanda va al modello.
     */
    private val CIRCULAR_WORDS = listOf(
        // cosa
        "circolar", "comunicazion", "avvis", "scuola", "classe", "preside", "segreteria",
        // quali
        "ultim", "recent", "nuov", "arrivat", "pubblicat", "mandat", "inviat", "novita",
        "riguard", "interess", "rilevant", "pertinent", "important",
        // quando
        "oggi", "ieri", "settiman", "mese", "scors", "passat", "giorn", "prossim",
        // come lo si chiede
        "riassum", "riassunt", "sintesi", "sintetizz", "elenc", "mostr", "ricord", "legg",
        "lett", "perso", "sapere", "aggiorn", "dovrei", "abbiamo", "avete", "hanno",
        "nessun", "qualche", "altre", "tutt", "breve", "brevemente", "velocemente", "fammi",
        "dammi", "fai", "puoi", "vedere", "sono", "stat"
    )
}
