package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTO aggiunti con il passaggio a multi-classe, con l'invio dei sondaggi e con la modifica
 * delle proposte.
 *
 * **Perché stanno qui e non nella cartella `dto/`.** Il ponte con cui scrivo sul computer di
 * Simone accetta al massimo sette cartelle sotto quella collegata, e
 * `shared/src/commonMain/kotlin/circolareplus/data/remote/dto/` ne conta otto: i file lì dentro
 * non riesco né a leggerli né a scriverli. Il `package` qui è comunque `...data.remote.dto`,
 * quindi per il compilatore e per chi importa non cambia nulla — Kotlin non richiede che il
 * percorso del file rispecchi il package. Se un giorno viene collegata dal desktop anche la
 * cartella `dto`, questo file può essere spostato lì dentro senza toccare una riga.
 *
 * Tutti i campi nuovi hanno un valore di default: un'app aggiornata contro un backend non ancora
 * aggiornato continua a funzionare invece di fallire la deserializzazione.
 */

// --- Classi ----------------------------------------------------------------------------------

@Serializable
data class ClassOptionDto(
    val id: String,
    val label: String,
    val studentCount: Int = 0
)

@Serializable
data class ClassesListResponseDto(
    val classes: List<ClassOptionDto> = emptyList()
)

/**
 * Registrazione con la classe. Sostituisce `RegisterRequestDto`, che non aveva `classLabel`
 * perché la classe era una sola e implicita nel server.
 */
@Serializable
data class RegisterWithClassRequestDto(
    val firstName: String,
    val lastName: String,
    val username: String,
    val password: String,
    val heightCm: Int,
    val representativeCode: String? = null,
    val classLabel: String
)

/** Risposta di `/api/preferences/config`: ora dice anche di quale classe si sta parlando. */
@Serializable
data class ClassConfigDto(
    val preferencesOpen: Boolean = false,
    val classId: String? = null,
    val classLabel: String? = null
)

// --- Sondaggi: invio e scadenza ---------------------------------------------------------------

/**
 * Avanzamento della compilazione di un sondaggio. Sono i campi che `/api/polls/:id` restituisce
 * accanto al dettaglio: si leggono dalla stessa risposta con una seconda deserializzazione,
 * senza una chiamata in più (vedi PollsRepository.getPollWithProgress).
 */
@Serializable
data class PollProgressDto(
    val closesAt: String? = null,
    val totalStudents: Int = 0,
    val submittedCount: Int = 0,
    val hasSubmitted: Boolean = false,
    val isExpired: Boolean = false,
    val canRunAssignments: Boolean = false
)

@Serializable
data class PollSubmitResponseDto(
    val success: Boolean = true,
    val submittedCount: Int = 0,
    val totalStudents: Int = 0
)

@Serializable
data class SetPollDeadlineRequestDto(
    val closesAt: String?
)

// --- Bacheca: modifica di una proposta ---------------------------------------------------------

@Serializable
data class UpdateProposalRequestDto(
    val title: String? = null,
    val description: String? = null,
    val category: String? = null
)
