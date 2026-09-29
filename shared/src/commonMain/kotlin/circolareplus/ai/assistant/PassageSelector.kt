package circolareplus.ai.assistant

import kotlin.math.ln

/**
 * Sceglie, dal testo di una circolare, i passaggi che c'entrano con la domanda.
 *
 * Prima al modello arrivavano i primi N caratteri del PDF: con il modello sul telefono circa una
 * pagina. Di una circolare da 30 pagine la risposta a "che giorni c'e' lo sportello di scienze?"
 * poteva stare a pagina 12 e non arrivava mai. Ora il testo si divide in blocchi, si tengono
 * l'intestazione (destinatari, oggetto) e i blocchi con piu' parole della domanda, nell'ordine in
 * cui compaiono nel documento, con "[...]" dove manca qualcosa. Stesso spazio, testo scelto meglio,
 * nessun costo in piu' per il modello.
 */
internal object PassageSelector {

    /** Dimensione indicativa di un blocco: circa un paragrafo o una piccola tabella. */
    private const val CHUNK_CHARS = 450

    /** L'intestazione del documento, tenuta sempre: di solito dice a chi e' rivolto e perche'. */
    private const val HEADER_CHARS = 300

    private const val GAP = "\n[...]\n"

    /** Oltre queste righe una parola non e' piu' rara, e l'elenco diventerebbe un doppione. */
    private const val MAX_KEY_LINES = 8
    private const val MAX_KEY_LINE_CHARS = 200
    private const val KEY_LINES_TITLE = "Righe del documento con le parole della domanda:\n"

    fun select(text: String, question: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val stems = withSynonyms(AssistantContext.tokenize(question).map { AssistantContext.stemOf(it) }.distinct())
        if (stems.isEmpty()) return text.take(maxChars)

        // L'intestazione e' un pezzo a parte, tagliato a fine riga, e si tiene se e' corta
        // abbastanza da non togliere spazio ai passaggi.
        val headerEnd = text.lastIndexOf('\n', HEADER_CHARS).takeIf { it > 0 } ?: HEADER_CHARS
        val header = text.take(headerEnd).trimEnd()
        val keepHeader = header.isNotBlank() && header.length <= maxChars / 4
        val body = if (keepHeader) text.drop(headerEnd) else text

        val chunks = chunk(body)
        if (chunks.isEmpty()) return text.take(maxChars)
        val found = chunks.map { chunk -> stemsIn(chunk, stems) }
        if (found.all { it.isEmpty() }) return text.take(maxChars)

        // Le righe con le parole rare della domanda, messe in fila subito dopo l'intestazione.
        // Una tabella spezzata fra due blocchi arrivava a pezzi, e un modello piccolo (visto con
        // Gemini Nano) rispondeva con la prima riga trovata: "scienze il lunedi'" e basta, anche
        // se la tabella diceva anche martedi' e mercoledi'. Le righe una sotto l'altra le vede
        // tutte insieme.
        val keyLines = keyLines(text, stems)
        val keyBlock = if (keyLines.isEmpty()) "" else
            KEY_LINES_TITLE + keyLines.joinToString("\n") { "- $it" }
        val keepKeyBlock = keyBlock.isNotEmpty() && keyBlock.length <= maxChars / 3

        val chosen = mutableSetOf<Int>()
        val covered = mutableSetOf<String>()
        var used = (if (keepHeader) header.length + GAP.length else 0) +
            (if (keepKeyBlock) keyBlock.length + GAP.length else 0)

        fun fits(index: Int) = used + chunks[index].length + GAP.length <= maxChars

        // Peso di ogni parola della domanda: alto se compare in pochi blocchi. In una circolare
        // sugli sportelli "sportell" sta in quasi tutti i blocchi e non distingue niente, mentre
        // "scienz" indica proprio i blocchi che servono. Senza pesi, dopo il primo blocco si
        // prendevano quelli con piu' "sportelli", e a un modello con poco spazio (quello sul
        // telefono) arrivava solo la frase "non sono previsti sportelli di ... scienze motorie".
        val weight = stems.associateWith { stem ->
            val df = found.count { stem in it }
            if (df == 0) 0.0 else ln(1.0 + chunks.size.toDouble() / df)
        }
        fun score(index: Int, onlyNew: Boolean) =
            found[index].keys.filter { !onlyNew || it !in covered }.sumOf { weight[it] ?: 0.0 }

        // Scelta golosa: ogni volta il blocco che aggiunge piu' peso di parole non ancora
        // coperte, poi quello con le parole piu' rare in assoluto, poi quello con piu'
        // occorrenze.
        while (true) {
            val best = chunks.indices
                .filter { it !in chosen && found[it].isNotEmpty() && fits(it) }
                .maxWithOrNull(
                    compareBy<Int> { index -> score(index, onlyNew = true) }
                        .thenBy { index -> score(index, onlyNew = false) }
                        .thenBy { index -> found[index].values.sum().coerceAtMost(found[index].size * 5) }
                        .thenByDescending { it }
                ) ?: break
            chosen += best
            covered += found[best].keys
            used += chunks[best].length + GAP.length
            // Il blocco dopo spesso completa quello trovato (la riga di una tabella dopo il suo
            // titolo): se c'e' spazio si prende anche lui.
            val next = best + 1
            if (next < chunks.size && next !in chosen && fits(next)) {
                chosen += next
                used += chunks[next].length + GAP.length
            }
        }

        return buildString {
            if (keepHeader) append(header)
            if (keepKeyBlock) {
                if (isNotEmpty()) append("\n\n")
                append(keyBlock)
                append("\n\n")
            }
            var previous = -1
            for (index in chosen.sorted()) {
                // Il primo blocco del corpo segue l'intestazione senza salti (se in mezzo non ci
                // sono le righe chiave).
                val adjacent = if (previous < 0) index == 0 && keepHeader && !keepKeyBlock else index == previous + 1
                when {
                    isEmpty() -> if (index > 0) append("[...]\n")
                    adjacent -> append('\n')
                    else -> append(GAP)
                }
                append(chunks[index])
                previous = index
            }
            if (previous < chunks.size - 1) append(GAP.trimEnd())
        }.take(maxChars)
    }

    /**
     * "Cosa e' vietato in palestra?" e la circolare scrive "e' fatto divieto di...": le radici
     * "vieta" e "diviet" non coincidono, e i passaggi con le regole restavano fuori. Le parole
     * che nelle circolari si dicono in piu' modi si cercano tutte.
     */
    private val SYNONYM_GROUPS = listOf(
        listOf("vieta", "diviet", "proibi", "non e consentit", "non e permess"),
        listOf("regol", "norm", "disposizion", "obbligo")
    )

    internal fun withSynonyms(stems: List<String>): List<String> {
        val extra = SYNONYM_GROUPS.flatMap { group ->
            if (stems.any { stem -> group.any { stem.startsWith(it) || it.startsWith(stem) && stem.length >= 4 } }) {
                group.filter { it !in stems }
            } else {
                emptyList()
            }
        }
        return (stems + extra).distinct()
    }

    /** Blocchi di circa [CHUNK_CHARS] caratteri, spezzati a fine riga quando possibile. */
    internal fun chunk(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (line in text.split('\n')) {
            var rest = line
            // Una riga lunghissima (PDF senza a capo) si spezza a misura.
            while (rest.length > CHUNK_CHARS) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
                result += rest.take(CHUNK_CHARS)
                rest = rest.drop(CHUNK_CHARS)
            }
            if (current.length + rest.length + 1 > CHUNK_CHARS && current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(rest)
        }
        if (current.isNotBlank()) result += current.toString()
        return result.filter { it.isNotBlank() }
    }

    /**
     * Le righe del documento che contengono una parola rara della domanda: una che compare in
     * al massimo [MAX_KEY_LINES] righe. "sportell" in una circolare sugli sportelli sta in
     * decine di righe e non e' rara; "scienz" sta nelle tre righe della tabella e in una frase.
     */
    internal fun keyLines(text: String, stems: List<String>): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val normalized = lines.map { AssistantContext.normalize(it) }
        val rare = stems.filter { stem -> normalized.count { stem in it } in 1..MAX_KEY_LINES }
        if (rare.isEmpty()) return emptyList()
        return lines.filterIndexed { index, _ -> rare.any { it in normalized[index] } }
            .distinct()
            .take(MAX_KEY_LINES)
            .map { if (it.length <= MAX_KEY_LINE_CHARS) it else it.take(MAX_KEY_LINE_CHARS - 1) + "…" }
    }

    /** Per ogni parola della domanda trovata nel blocco, quante volte compare. */
    private fun stemsIn(chunk: String, stems: List<String>): Map<String, Int> {
        val normalized = AssistantContext.normalize(chunk)
        return stems.associateWith { countOccurrences(normalized, it) }.filterValues { it > 0 }
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var from = text.indexOf(needle)
        while (from >= 0) {
            count++
            from = text.indexOf(needle, from + needle.length)
        }
        return count
    }
}
