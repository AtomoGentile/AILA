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
     * Serve lo stesso giorno **e** un titolo che si assomigli: uguale, uno contenuto nell'altro, o
     * almeno una parola significativa in comune. La sola data non basta — due cose diverse nello
     * stesso giorno esistono, e nasconderne una dietro l'altra sarebbe l'errore opposto.
     */
    fun findExisting(deadline: ExtractedDeadline, events: List<CalendarEvent>): CalendarEvent? {
        val sameDay = events.filter { it.date.take(10) == deadline.dueDate }
        if (sameDay.isEmpty()) return null

        val wanted = AssistantContext.normalize(deadline.title).trim()
        val wantedWords = significantWords(wanted)
        return sameDay.firstOrNull { event ->
            val other = AssistantContext.normalize(event.title).trim()
            wanted == other ||
                (wanted.isNotEmpty() && other.isNotEmpty() && (wanted.contains(other) || other.contains(wanted))) ||
                wantedWords.intersect(significantWords(other)).isNotEmpty()
        }
    }

    private fun significantWords(normalizedTitle: String): Set<String> =
        normalizedTitle.split(' ').filter { it.length >= MIN_SIGNIFICANT_WORD_CHARS }.toSet()
}
