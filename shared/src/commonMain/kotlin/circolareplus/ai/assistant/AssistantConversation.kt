package circolareplus.ai.assistant

import kotlinx.serialization.Serializable

/**
 * Una conversazione archiviata di AILA Assistant: i messaggi piu' il minimo che serve per
 * riconoscerla in una lista.
 *
 * Il titolo e' salvato e non ricalcolato a ogni lettura perche' e' quello che l'utente ha visto
 * la volta prima: se domani cambia il modo di ricavarlo, le conversazioni vecchie non si
 * rinominano sotto le mani di chi le sta cercando.
 */
@Serializable
data class AssistantConversation(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messages: List<AssistantMessage>
) {
    companion object {
        /** Oltre questa lunghezza il titolo non entra comunque nella riga della lista. */
        private const val MAX_TITLE_CHARS = 48

        /**
         * Titolo della conversazione: la prima domanda dell'utente, su una riga sola.
         *
         * E' la prima domanda e non l'ultima perche' e' quella che identifica il filo del
         * discorso — le successive sono spesso richieste di chiarimento ("e la settimana dopo?")
         * che da sole non dicono niente.
         */
        fun titleFrom(messages: List<AssistantMessage>): String {
            val first = messages.firstOrNull { it.author == AssistantAuthor.USER }
                ?.text
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
            if (first.isNullOrEmpty()) return "Conversazione"
            return if (first.length <= MAX_TITLE_CHARS) {
                first
            } else {
                first.take(MAX_TITLE_CHARS).trimEnd() + "…"
            }
        }
    }
}
