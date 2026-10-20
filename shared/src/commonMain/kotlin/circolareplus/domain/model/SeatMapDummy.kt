package circolareplus.domain.model

import kotlinx.serialization.Serializable

/**
 * Un fantoccio della Mappa Posti: studente inventato dal Rappresentante, con nome e cognome,
 * che occupa un posto come tutti ma non esiste da nessun'altra parte. Non è un utente:
 * nessuna credenziale, nessun accesso, e di conseguenza non vota le preferenze sociali
 * e non può essere votato.
 */
@Serializable
data class SeatMapDummy(
    val id: String,
    val firstName: String,
    val lastName: String
) {
    val fullName: String get() = "$firstName $lastName"
}

/**
 * Vista [User] per l'ottimizzatore, che ragiona su studenti: username vuoto e ruolo STUDENT.
 * Resta un fantoccio: nessun'altra parte dell'app lo vede come utente vero.
 */
fun SeatMapDummy.asUser(): User = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    username = "",
    role = UserRole.STUDENT
)
