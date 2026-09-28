package circolareplus.ai.assistant

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

    fun select(text: String, question: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val stems = AssistantContext.tokenize(question).map { AssistantContext.stemOf(it) }.distinct()
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

        val chosen = mutableSetOf<Int>()
        val covered = mutableSetOf<String>()
        var used = if (keepHeader) header.length + GAP.length else 0

        fun fits(index: Int) = used + chunks[index].length + GAP.length <= maxChars

        // Scelta golosa: ogni volta il blocco che aggiunge piu' parole della domanda non ancora
        // coperte, poi quello con piu' occorrenze. Cosi' "scienze" e "orari" non finiscono tutti
        // sulla stessa parola ripetuta in venti blocchi uguali.
        while (true) {
            val best = chunks.indices
                .filter { it !in chosen && found[it].isNotEmpty() && fits(it) }
                .maxWithOrNull(
                    compareBy<Int> { index -> found[index].keys.count { it !in covered } }
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
            var previous = -1
            for (index in chosen.sorted()) {
                // Il primo blocco del corpo segue l'intestazione senza salti.
                val adjacent = if (previous < 0) index == 0 && keepHeader else index == previous + 1
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
