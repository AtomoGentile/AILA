package circolareplus.ai.assistant

import kotlinx.serialization.Serializable

/** Chi ha scritto un messaggio della conversazione. */
enum class AssistantAuthor { USER, ASSISTANT }

/**
 * Tipo della fonte citata dall'assistente.
 *
 * Non e' decorativo: decide l'icona del chip e, soprattutto, dove porta il tocco. Una risposta
 * che cita "Circolare n. 214" e non permette di aprirla costringe l'utente a rifare a mano la
 * ricerca che ha appena delegato all'AI.
 */
enum class AssistantSourceKind { CIRCULAR, CALENDAR, BOARD, POLL, SEAT_MAP, CLASS }

@Serializable
data class AssistantSource(
    val kind: AssistantSourceKind,
    val label: String,
    /** Valorizzato quando [kind] e' [AssistantSourceKind.CIRCULAR]: serve ad aprire la circolare. */
    val circularNumber: Int? = null
)

/**
 * Un messaggio della chat.
 *
 * [isError] distingue il messaggio di errore dal messaggio di risposta: sono entrambi testo
 * dell'assistente, ma un errore non va mostrato come se fosse una risposta — vedi la nota su
 * [circolareplus.ai.AiTextResult].
 */
@Serializable
data class AssistantMessage(
    val id: String,
    val author: AssistantAuthor,
    val text: String,
    val sources: List<AssistantSource> = emptyList(),
    val isError: Boolean = false,
    /** Modello che ha prodotto la risposta, mostrato in piccolo sotto il messaggio. */
    val modelLabel: String? = null
)

/** Quello che [AilaAssistant] restituisce per una domanda. */
data class AssistantReply(
    val text: String,
    val sources: List<AssistantSource> = emptyList(),
    val modelLabel: String? = null,
    val isError: Boolean = false
)
