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
enum class AssistantSourceKind { CIRCULAR, CALENDAR, BOARD, POLL, SEAT_MAP, CLASS, GITA }

/**
 * Da dove arriva la domanda. [GENERAL] e' l'assistente di sempre, generalista, con le fonti
 * dell'app. [GITA_ONLY] e' la chat aperta dalla sezione Gita: risponde solo con il materiale
 * della gita e con le circolari, senza conoscenze generali (vedi AssistantPrompt).
 */
enum class AssistantMode { GENERAL, GITA_ONLY }

enum class AssistantActionStatus { AWAITING_CONFIRMATION, RUNNING, COMPLETED, PARTIAL, CANCELLED, EXPIRED, ERROR }

enum class AssistantAgentPhase { READING, PLANNING, EXECUTING, WRITING, TRANSCRIBING }

@Serializable
data class AssistantAttachedFile(val localId:String,val name:String,val service:String="DOCUMENTS",val generated:Boolean=false,val processingLabel:String?=null)

data class AssistantAttachedSource(val file:AssistantAttachedFile,val text:String)

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
    val modelLabel: String? = null,
    val actionPlan: AssistantActionPlan? = null,
    val actionStatus: AssistantActionStatus? = null,
    val awaitingActionInput: Boolean = false,
    val attachedFiles: List<AssistantAttachedFile> = emptyList(),
    /** Structured read observations, persisted with this turn for contextual follow-ups. */
    val readContext: String = ""
)

/** Confirmation belongs to an assistant preview and to the account that requested it. */
internal fun AssistantMessage.confirmableAction(userId: String): AssistantActionPlan? =
    actionPlan?.takeIf {
        author == AssistantAuthor.ASSISTANT && !isError &&
            actionStatus == AssistantActionStatus.AWAITING_CONFIRMATION && it.requestedByUserId == userId
    }

/** Quello che [AilaAssistant] restituisce per una domanda. */
data class AssistantReply(
    val text: String,
    val sources: List<AssistantSource> = emptyList(),
    val modelLabel: String? = null,
    val isError: Boolean = false,
    val actionPlan: AssistantActionPlan? = null,
    val awaitingActionInput: Boolean = false,
    val createdCalendarEvents: List<circolareplus.domain.model.CalendarEvent> = emptyList(),
    val calendarChanged: Boolean = false,
    val readContext: String = ""
)
