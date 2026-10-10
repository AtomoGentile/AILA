package circolareplus.ai.assistant

import kotlin.math.ln

/**
 * Indice a parole sul testo integrale delle circolari, tenuto sul dispositivo.
 *
 * Non fa intervenire il modello nella ricerca: l'app cerca, e al modello arrivano solo i
 * passaggi trovati (vedi [passages]). Con un centinaio di circolari all'anno il testo sta in
 * memoria senza problemi e una ricerca e' un ciclo sulle parole.
 *
 * Le parole si confrontano per radice, come nel resto dell'assistente, e con un prefisso
 * comune: "sportelli" trova "sportello" anche se le radici di [AssistantContext.stemOf] non
 * coincidono. Il prefisso minimo evita che "scienze" trovi "scienza" per caso e che parole
 * corte trovino ovunque.
 */
internal class CircularTextIndex {

    /** Radice -> circolare -> quante volte compare. */
    private val postings = mutableMapOf<String, MutableMap<Int, Int>>()
    private val texts = mutableMapOf<Int, String>()

    /** Una circolare trovata da una ricerca: [score] cresce con le parole trovate e le occorrenze. */
    data class Hit(val number: Int, val score: Double, val matchedTerms: Set<String>)

    val size: Int get() = texts.size

    fun contains(number: Int): Boolean = number in texts

    /** Numeri delle circolari nell'indice. */
    fun numbers(): Set<Int> = texts.keys.toSet()

    /** Il testo integrale di una circolare, se e' nell'indice. */
    fun text(number: Int): String? = texts[number]

    /** Aggiunge o sostituisce il testo di una circolare. */
    fun put(number: Int, text: String) {
        remove(number)
        texts[number] = text
        AssistantContext.normalize(text).split(' ')
            .filter { it.length >= MIN_TERM_CHARS }
            .groupingBy { AssistantContext.stemOf(it) }
            .eachCount()
            .forEach { (stem, count) -> postings.getOrPut(stem) { mutableMapOf() }[number] = count }
    }

    fun remove(number: Int) {
        if (texts.remove(number) == null) return
        postings.values.forEach { it.remove(number) }
        postings.entries.removeAll { it.value.isEmpty() }
    }

    /**
     * Le circolari che contengono almeno una delle [terms], la più pertinente per prima.
     *
     * Il punteggio somma, per ogni parola trovata, `1 + ln(occorrenze)`: una parola che compare
     * molte volte pesa di più, ma senza che un documento lungo domini solo per la lunghezza.
     */
    fun search(terms: List<String>): List<Hit> {
        val stems = terms.filter { it.length >= MIN_TERM_CHARS }.map { AssistantContext.stemOf(it) }.distinct()
        if (stems.isEmpty()) return emptyList()

        val scores = mutableMapOf<Int, Double>()
        val matched = mutableMapOf<Int, MutableSet<String>>()
        for (stem in stems) {
            for ((key, byCircular) in postings) {
                if (!sameStem(stem, key)) continue
                for ((number, count) in byCircular) {
                    scores[number] = (scores[number] ?: 0.0) + 1.0 + ln(count.toDouble())
                    matched.getOrPut(number) { mutableSetOf() } += stem
                }
            }
        }
        return scores.map { (number, score) -> Hit(number, score, matched[number].orEmpty()) }
            .sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.number })
    }

    /**
     * I passaggi della circolare [number] che contengono le [terms], entro [maxChars].
     * Si riusa [PassageSelector], che tiene l'intestazione e i blocchi con le parole chiave.
     */
    fun passages(number: Int, terms: List<String>, maxChars: Int): String {
        val text = texts[number] ?: return ""
        return PassageSelector.select(text, terms.joinToString(" "), maxChars)
    }

    private fun sameStem(query: String, key: String): Boolean {
        if (query == key) return true
        val shorter = minOf(query.length, key.length)
        return shorter >= MIN_PREFIX_CHARS && (query.startsWith(key) || key.startsWith(query))
    }

    private companion object {
        const val MIN_TERM_CHARS = 3
        const val MIN_PREFIX_CHARS = 5
    }
}
