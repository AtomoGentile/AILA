package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.ClassCodeResponseDto
import circolareplus.data.remote.dto.EmptyBodyDto
import circolareplus.data.remote.dto.NameChangeDecisionDto
import circolareplus.data.remote.dto.NameChangeRequestDto
import circolareplus.data.remote.dto.NameChangeRequestSubmittedDto
import circolareplus.data.remote.dto.OwnNameChangeRequestDto
import circolareplus.data.remote.dto.OwnNameChangeStatusDto
import circolareplus.data.remote.dto.PendingNameChangesDto
import circolareplus.data.remote.dto.ResetCodeResponseDto
import circolareplus.data.remote.dto.SetHeightRequestDto
import circolareplus.data.remote.dto.SignatureExpirationRequestDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.TelegramLinkCodeDto
import circolareplus.data.remote.dto.TelegramStatusDto
import circolareplus.data.remote.dto.UsersListResponseDto
import circolareplus.domain.model.User
import circolareplus.platform.getIosSignatureExpirationInfo
import circolareplus.platform.isIos

class UsersRepository(private val api: ApiClient) {

    suspend fun me(): User = api.get<circolareplus.data.remote.dto.UserDto>("/api/users/me").toDomain().first

    /** Invia al backend la data di scadenza della firma iOS (se su iOS). */
    suspend fun syncIosSignatureExpiration() {
        if (!isIos()) return
        val info = getIosSignatureExpirationInfo(api.settings.iosFirstLaunchMillis) ?: return
        try {
            api.put<SignatureExpirationRequestDto, SuccessDto>(
                "/api/users/me/signature-expiration",
                SignatureExpirationRequestDto(expiresAt = info.expiresAtIso)
            )
        } catch (e: Exception) {
            // Se offline o errore di rete, fallisce silenziosamente per il momento
        }
    }

    /** Stato del collegamento Telegram per i promemoria di firma iOS. */
    suspend fun telegramStatus(): TelegramStatusDto =
        api.get<TelegramStatusDto>("/api/telegram/status", offlineCopy = false)

    /** Genera un codice temporaneo monouso per collegare il bot Telegram. */
    suspend fun createTelegramLinkCode(): TelegramLinkCodeDto =
        api.post<EmptyBodyDto, TelegramLinkCodeDto>("/api/telegram/link-code", EmptyBodyDto())

    /** Scollega/disabilita i promemoria Telegram. */
    suspend fun unlinkTelegram() {
        api.post<EmptyBodyDto, SuccessDto>("/api/telegram/unlink", EmptyBodyDto())
    }

    /** Solo Rappresentante: elenco di tutti gli studenti della classe. */
    suspend fun listStudents(): List<User> =
        api.get<UsersListResponseDto>("/api/users").users.map { it.toDomain().first }

    /** Solo Rappresentante: codice per entrare nella classe (creato alla prima richiesta). */
    suspend fun classCode(): String =
        api.get<ClassCodeResponseDto>("/api/users/class-code", offlineCopy = false).code

    /** Solo Rappresentante: nuovo codice classe, il vecchio smette di valere. */
    suspend fun regenerateClassCode(): String =
        api.post<EmptyBodyDto, ClassCodeResponseDto>("/api/users/class-code/regenerate", EmptyBodyDto()).code

    /** Solo Rappresentante: codice monouso (24 ore) con cui un compagno reimposta la password. */
    suspend fun createResetCode(userId: String): ResetCodeResponseDto =
        api.post<EmptyBodyDto, ResetCodeResponseDto>("/api/users/$userId/reset-code", EmptyBodyDto())

    /**
     * Solo Rappresentante: corregge l'altezza di un compagno (140-210, a scaglioni di 5 cm).
     * Il compagno riceve una notifica con il vecchio e il nuovo valore: la correzione non è mai
     * silenziosa.
     */
    suspend fun setStudentHeight(userId: String, heightCm: Int) {
        api.put<SetHeightRequestDto, SuccessDto>("/api/users/$userId/height", SetHeightRequestDto(heightCm))
    }

    // Cambio nome: lo chiede chi ha il nome sbagliato, lo approva (o lo rifiuta) il Rappresentante
    // o la Guardia. Le letture non finiscono nella copia offline (offlineCopy = false): sono dati
    // di sessione, come il resto dei dialoghi del profilo.

    /** Chiede il cambio del proprio nome; la richiesta resta in attesa fino alla decisione. */
    suspend fun requestNameChange(firstName: String, lastName: String): NameChangeRequestSubmittedDto =
        api.post<NameChangeRequestDto, NameChangeRequestSubmittedDto>(
            "/api/users/me/name-request",
            NameChangeRequestDto(firstName = firstName, lastName = lastName)
        )

    /** La propria richiesta in attesa, oppure null. */
    suspend fun ownNameChangeRequest(): OwnNameChangeRequestDto? =
        api.get<OwnNameChangeStatusDto>("/api/users/me/name-request", offlineCopy = false).request

    /** Richieste in attesa nella classe; solo Rappresentanti e Guardie possono leggerlo. */
    suspend fun pendingNameChanges(): PendingNameChangesDto =
        api.get<PendingNameChangesDto>("/api/users/name-requests/pending", offlineCopy = false)

    suspend fun approveNameChange(id: String): NameChangeDecisionDto =
        api.post<EmptyBodyDto, NameChangeDecisionDto>("/api/users/name-requests/$id/approve", EmptyBodyDto())

    suspend fun rejectNameChange(id: String): NameChangeDecisionDto =
        api.post<EmptyBodyDto, NameChangeDecisionDto>("/api/users/name-requests/$id/reject", EmptyBodyDto())
}
