package circolareplus.ai

import circolareplus.ai.assistant.AssistantContext
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.ExtractedDeadline

/**
 * Riconosce una scadenza estratta da una circolare che e' gia' in calendario.
 *
 * Serve al pulsante "Aggiungi al calendario": premuto due volte (o su una circolare gia' aggiunta
 * in una visita precedente, o da un altro studente sul calendario condiviso) creava un evento
 * doppio. Il server avvisa dei doppioni solo per verifiche e interrogazioni entro tre giorni, e
 * per tutte le altre categorie non controlla niente, quindi il controllo sta anche qui.
 */
internal object CalendarDuplicates {

    /** Parole piu' corte di cosi' ("per", "del", "gita"...) non bastano a dire che due titoli sono lo stesso. */
    private const val MIN_SIGNIFICANT_WORD_CHARS = 5

    /**
     * L'evento del calendario che corrisponde a [deadline], o `null` se non c'e'.
     *
     * Serve lo stesso giorno **e** un titolo che si assomigli: uguale, una frase di almeno due
     * parole significative contenuta nell'altro titolo, o almeno due parole significative in
     * comune. Una materia o un tema generico da solo non identifica lo stesso impegno.
     */
    fun findExisting(deadline: ExtractedDeadline, events: List<CalendarEvent>): CalendarEvent? {
        val sameDay = events.filter { it.date.take(10) == deadline.dueDate }
        if (sameDay.isEmpty()) return null

        val wanted = AssistantContext.normalize(deadline.title).trim()
        val wantedWords = significantWords(wanted)
        return sameDay.firstOrNull { event ->
            val other = AssistantContext.normalize(event.title).trim()
            val otherWords = significantWords(other)
            wanted == other ||
                (wanted.isNotEmpty() && other.isNotEmpty() &&
                    (otherWords.size >= MIN_MATCHING_WORDS && wanted.contains(other) ||
                        wantedWords.size >= MIN_MATCHING_WORDS && other.contains(wanted))) ||
                matchingWordCount(wantedWords, otherWords) >= MIN_MATCHING_WORDS
        }
    }

    private const val MIN_MATCHING_WORDS = 2

    /** Counts distinct word pairs so one generic word cannot match several variants at once. */
    private fun matchingWordCount(left: Set<String>, right: Set<String>): Int {
        val unmatched = right.toMutableSet()
        var matches = 0
        for (word in left) {
            val match = unmatched.firstOrNull { candidate ->
                word == candidate ||
                    (minOf(word.length, candidate.length) >= MIN_SIGNIFICANT_WORD_CHARS &&
                        (word.startsWith(candidate) || candidate.startsWith(word)))
            }
            if (match != null) {
                matches++
                unmatched.remove(match)
            }
        }
        return matches
    }

    private fun significantWords(normalizedTitle: String): Set<String> =
        normalizedTitle.split(' ').filter { it.length >= MIN_SIGNIFICANT_WORD_CHARS }.toSet()
}
