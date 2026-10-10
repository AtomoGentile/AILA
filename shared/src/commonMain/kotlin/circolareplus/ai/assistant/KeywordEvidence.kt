package circolareplus.ai.assistant

/**
 * Riscontri per le parole della domanda, calcolati dal codice e non dal modello.
 *
 * Il modello non cerca: riceve per ogni parola chiave un punteggio già deciso. Un modello
 * piccolo (o un modello cloud che legge un indice lungo) puo' dimenticarsi una parola sparsa in
 * una circolare e rispondere "non risulta" anche quando c'e'. Un controllo deterministico non
 * si dimentica.
 *
 * Punteggio, da -10 a +10:
 * - [TITLE_HIT] (+10): la parola e' nel titolo di almeno una circolare;
 * - [SUMMARY_HIT] (+5): non nel titolo, ma nel riassunto AI, in una scadenza o nel testo
 *   integrale gia' letto in questa sessione;
 * - [NO_HIT] (-10): nessun riscontro nei dati in memoria.
 *
 * Il -10 vale per tutto quello che l'app ha: titoli, riassunti, scadenze e il testo delle
 * circolari salvate sul telefono. Le circolari fuori dal limite offline non sono controllate, e il
 * messaggio lo dice, altrimenti "non risulta" sarebbe un'affermazione piu' forte di quello che
 * si e' verificato.
 */
internal object KeywordEvidence {

    const val TITLE_HIT = 10
    const val SUMMARY_HIT = 5
    const val NO_HIT = -10

    /** Quante parole della domanda controllare: oltre, il messaggio diventa un elenco di rumore. */
    private const val MAX_TERMS = 4

    /**
     * Parole che nella domanda non indicano un argomento: verbi di contorno e termini come
     * "circolare" che sono in ogni domanda. Se restassero, "si e' parlato di ICDL" darebbe -10
     * a "parlato" e il modello direbbe che "parlato" non compare nelle circolari.
     */
    private val IGNORED = setOf(
        "parlato", "parlare", "parla", "parlo", "scritto", "scritta", "scritti", "detto", "dice",
        "dicono", "esiste", "esistono", "controllare", "controlla", "controlli", "guarda", "cerca",
        "cercare", "trovo", "trova", "circolare", "circolari", "avviso", "avvisi", "nelle", "nella",
        "negli", "dove", "quando", "sempre", "anche", "ancora", "dimmi", "informazioni", "info"
    )

    /**
     * Esito per una parola: [score] e' il punteggio, [circulars] le circolari in cui compare.
     * [checked] quante circolari hanno titolo e riassunto controllati, [textChecked] quante hanno
     * anche il testo integrale sul telefono.
     */
    data class Evidence(
        val term: String,
        val score: Int,
        val circulars: List<Int>,
        val checked: Int,
        val textChecked: Int
    )

    /** Le parole della domanda da controllare, senza verbi di contorno, al massimo [MAX_TERMS]. */
    fun terms(question: String): List<String> =
        AssistantContext.tokenize(question).filter { it !in IGNORED }.take(MAX_TERMS)

    /** Un riscontro per ogni parola significativa della domanda, nell'ordine in cui compare. */
    fun check(knowledge: AssistantKnowledge, question: String, deepTexts: Map<Int, String>): List<Evidence> =
        terms(question).map { evaluate(it, knowledge, deepTexts) }

    private fun evaluate(term: String, knowledge: AssistantKnowledge, deepTexts: Map<Int, String>): Evidence {
        val stem = AssistantContext.stemOf(term)
        val titleHits = mutableListOf<Int>()
        val otherHits = mutableListOf<Int>()
        knowledge.circulars.forEach { circular ->
            val analysis = knowledge.classifications[circular.number]
            val inTitle = AssistantContext.normalize(circular.title).contains(stem)
            val inSummary = analysis != null && (
                AssistantContext.normalize(analysis.personalSummary).contains(stem) ||
                    analysis.detectedDeadlines.any { AssistantContext.normalize(it.title).contains(stem) }
                )
            val inText = deepTexts[circular.number]?.let { AssistantContext.normalize(it).contains(stem) } == true ||
                circular.number in knowledge.textHits.byTerm[term].orEmpty()
            when {
                inTitle -> titleHits += circular.number
                inSummary || inText -> otherHits += circular.number
            }
        }
        val checked = knowledge.circulars.size
        val textChecked = knowledge.textHits.circularsWithText
        return when {
            titleHits.isNotEmpty() -> Evidence(term, TITLE_HIT, titleHits, checked, textChecked)
            otherHits.isNotEmpty() -> Evidence(term, SUMMARY_HIT, otherHits, checked, textChecked)
            else -> Evidence(term, NO_HIT, emptyList(), checked, textChecked)
        }
    }

    /** Il blocco di testo che il modello legge: una riga per parola, con il punteggio. */
    fun render(evidence: List<Evidence>): List<String> = evidence.map { e ->
        val score = if (e.score > 0) "+${e.score}" else "${e.score}"
        when (e.score) {
            TITLE_HIT -> "- \"${e.term}\": $score, nel titolo della circolare " +
                e.circulars.joinToString(", ") { "n. $it" } + ". Usa quelle circolari."
            SUMMARY_HIT -> "- \"${e.term}\": $score, nel riassunto o nel testo della circolare " +
                e.circulars.joinToString(", ") { "n. $it" } + "."
            else -> "- \"${e.term}\": $score, nessun riscontro in ${e.checked} circolari (titoli, " +
                "riassunti, scadenze) e nel testo di ${e.textChecked} circolari sul telefono. " +
                if (e.textChecked >= e.checked) {
                    "Non dire che compare nelle circolari."
                } else {
                    "Le circolari senza testo sul telefono non sono controllate: non dire che " +
                        "la parola non compare, ma che nelle circolari controllate non c'e'."
                }
        }
    }
}
