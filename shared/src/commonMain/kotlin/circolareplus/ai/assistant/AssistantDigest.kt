package circolareplus.ai.assistant

import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge

/**
 * "Riassumimi le ultime circolari (che mi riguardano)" risposto dal codice invece che dal
 * modello.
 *
 * E' una delle domande suggerite dall'app, e con Gemini Nano la risposta era un elenco di
 * titoli ("sono la n. 13 ... e la n. 9 ...") senza nessun riassunto, con una sola delle due
 * circolari fra le fonti. I riassunti pero' esistono gia': sono le analisi personali prodotte
 * all'apertura delle circolari. Scegliere le piu' recenti, tenere quelle che ti riguardano e
 * metterci sotto il loro riassunto sono cose che il codice fa senza sbagliare e subito; al
 * modello si chiedeva di ricopiare testo, che e' proprio quello che fa peggio.
 *
 * Come [AssistantAgenda], scatta solo se ogni parola della domanda e' una parola "da elenco di
 * circolari": "riassumi la circolare sulla gita" resta al modello, che deve cercare la gita.
 */
internal object AssistantDigest {

    /** Circolari riassunte senza un periodo nella domanda. */
    private const val DEFAULT_ITEMS = 4

    /** Con un periodo ("di questa settimana") se ne mostrano di piu', ma non all'infinito. */
    private const val MAX_ITEMS = 8

    /** Riassunto per circolare: la chat e' su telefono, il resto e' nella circolare. */
    private const val SUMMARY_CHARS = 320

    internal data class Request(val onlyMine: Boolean)

    /** La risposta gia' pronta, o `null` se la domanda non e' un riepilogo di circolari. */
    fun answer(knowledge: AssistantKnowledge, question: String): AssistantReply? {
        val request = requestOf(question) ?: return null
        val scope = TimeScopeParser.parse(question, knowledge.todayIso)

        val recent = knowledge.circulars
            .filter { scope == null || scope.contains(it.publishDate) }
            .sortedWith(compareByDescending<Circular> { it.publishDate.take(10) }.thenByDescending { it.number })
        val analysis = { circular: Circular ->
            knowledge.classifications[circular.number]?.takeIf { !it.isFallback }
        }

        val limit = if (scope == null) DEFAULT_ITEMS else MAX_ITEMS
        val relevant = recent.filter { analysis(it)?.badge == CircularRelevanceBadge.RELEVANT }
        val potential = recent.filter { analysis(it)?.badge == CircularRelevanceBadge.POTENTIAL }
        // "Che mi riguardano": prima quelle che ti riguardano; se sono poche si completa con
        // quelle di potenziale interesse, dette come tali. Senza "mi riguardano" le ultime e basta.
        val shown = if (request.onlyMine) {
            (relevant.take(limit) + potential.take((limit - relevant.size).coerceAtLeast(0)))
                .sortedWith(compareByDescending<Circular> { it.publishDate.take(10) }.thenByDescending { it.number })
        } else {
            recent.take(limit)
        }
        // Le circolari recenti mai analizzate non si possono dire "tue" ne' "non tue": si
        // segnalano, cosi' un riepilogo corto non sembra completo quando non lo e'.
        val oldestShown = shown.lastOrNull()?.publishDate?.take(10)
        val notAnalyzed = recent
            .take(if (request.onlyMine) MAX_ITEMS else limit)
            .filter { analysis(it) == null }
            .filter { oldestShown == null || it.publishDate.take(10) >= oldestShown }
            .filter { it !in shown }

        val period = scope?.describe()
        val text = buildString {
            when {
                knowledge.circulars.isEmpty() ->
                    append("Non ho ancora nessuna circolare: apri la scheda Circolari per caricarle.")
                recent.isEmpty() ->
                    append("Non trovo circolari pubblicate $period.")
                shown.isEmpty() && request.onlyMine -> {
                    append("Fra le circolari ")
                    append(period ?: "recenti")
                    append(" non ne trovo che ti riguardino")
                    if (notAnalyzed.isNotEmpty()) append(" tra quelle gia' analizzate")
                    append('.')
                }
                else -> {
                    append(
                        when {
                            request.onlyMine && period != null -> "Le circolari che ti riguardano $period:"
                            request.onlyMine -> "Le ultime circolari che ti riguardano:"
                            period != null -> "Le circolari pubblicate $period:"
                            else -> "Le ultime circolari:"
                        }
                    )
                    shown.forEach { circular ->
                        appendLine().appendLine()
                        append(entry(circular, analysis(circular), knowledge.todayIso, request.onlyMine))
                    }
                }
            }
            if (notAnalyzed.isNotEmpty()) {
                appendLine().appendLine()
                append(if (notAnalyzed.size == 1) "Non ancora analizzata: " else "Non ancora analizzate: ")
                append(notAnalyzed.joinToString(", ") { "n. ${it.number} (${it.title.trim()})" })
                append(if (notAnalyzed.size == 1) ". Aprila per avere il riassunto." else ". Aprile per avere il riassunto.")
            }
        }

        return AssistantReply(
            text = text,
            sources = (shown + notAnalyzed).map { circular ->
                AssistantSource(
                    kind = AssistantSourceKind.CIRCULAR,
                    label = "Circolare n. ${circular.number} — ${circular.title}",
                    circularNumber = circular.number
                )
            },
            modelLabel = MODEL_LABEL
        )
    }

    /**
     * Se la domanda chiede un riepilogo delle circolari, e se solo di quelle che ti riguardano;
     * `null` se non lo chiede o se nomina un argomento o un numero di circolare.
     */
    internal fun requestOf(question: String): Request? {
        if (AssistantContext.circularNumbersIn(question).isNotEmpty()) return null
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        fun has(vararg stems: String) = words.any { word -> stems.any { word.startsWith(it) } }

        if (!has("circolar", "comunicazion", "avvis")) return null
        if (AssistantContext.tokenize(question).any { term -> DIGEST_WORDS.none { term.startsWith(it) } }) return null
        // Serve che chieda davvero un giro sulle ultime: "circolari" da sola e' troppo poco.
        if (!has("riassum", "riassunt", "riepilog", "sintes", "ultim", "recent", "nuov", "uscit", "arrivat", "pubblicat")) {
            return null
        }
        return Request(onlyMine = has("riguard", "interess", "mie", "mia", "miei", "import", "rilevant", "pertinent"))
    }

    private fun entry(
        circular: Circular,
        analysis: CircularAiClassification?,
        todayIso: String,
        onlyMine: Boolean
    ): String = buildString {
        append("• n. ").append(circular.number)
        append(" del ").append(AssistantContext.readableDate(circular.publishDate, todayIso))
        append(" — ").append(circular.title.trim())
        when (analysis?.badge) {
            CircularRelevanceBadge.RELEVANT -> if (!onlyMine) append(" (ti riguarda)")
            CircularRelevanceBadge.POTENTIAL -> append(" (potrebbe interessarti)")
            CircularRelevanceBadge.NOT_RELEVANT -> append(" (non sembra riguardarti)")
            null -> append(" (non ancora analizzata: aprila per il riassunto)")
        }
        val summary = analysis?.personalSummary?.trim().orEmpty()
        if (summary.isNotEmpty()) appendLine().append(shorten(summary))
        analysis?.detectedDeadlines.orEmpty()
            .filter { it.dueDate.take(10) >= todayIso.take(10) }
            .sortedBy { it.dueDate }
            .take(2)
            .forEach { deadline ->
                appendLine().append("Scadenza: ").append(deadline.title.trim())
                append(", ").append(AssistantContext.readableDate(deadline.dueDate, todayIso))
                deadline.time?.takeIf { it.isNotBlank() }?.let { append(" ore ").append(it) }
            }
    }

    /** Il riassunto fino a [SUMMARY_CHARS], tagliato alla fine di una frase quando si puo'. */
    internal fun shorten(summary: String): String {
        if (summary.length <= SUMMARY_CHARS) return summary
        val cut = summary.take(SUMMARY_CHARS)
        val sentenceEnd = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (sentenceEnd >= SUMMARY_CHARS / 2) {
            cut.take(sentenceEnd + 1)
        } else {
            cut.substringBeforeLast(' ').trimEnd(',', ';', ':') + "…"
        }
    }

    private const val MODEL_LABEL = "Dalle analisi delle circolari di AILA"

    /**
     * Radici ammesse in una domanda di riepilogo, oltre alle stopword. Tutto il resto e' un
     * argomento ("la circolare sulla gita"), e la domanda va al modello.
     */
    private val DIGEST_WORDS = listOf(
        // cosa
        "circolar", "comunicazion", "avvis", "scuola", "classe",
        // riassunto
        "riassum", "riassunt", "riepilog", "sintes", "sintetizz", "spiega", "spiegam", "breve",
        // quali
        "ultim", "recent", "nuov", "uscit", "arrivat", "pubblicat", "mandat", "inviat",
        "riguard", "interess", "import", "rilevant", "pertinent",
        // quando
        "settiman", "oggi", "ieri", "mese", "scorsa", "scorso", "giorn", "questi", "ultimi",
        // come lo si chiede
        "elenc", "mostr", "fammi", "fai", "dammi", "leggi", "leggim", "quali", "ci", "abbiamo",
        "sono", "state", "stat", "tutt", "per", "favore", "potresti", "vuoi"
    )
}
