package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.ClassCodeResponseDto
import circolareplus.data.remote.dto.EmptyBodyDto
import circolareplus.data.remote.dto.ResetCodeResponseDto
import circolareplus.data.remote.dto.SetHeightRequestDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.UsersListResponseDto
import circolareplus.domain.model.User

class UsersRepository(private val api: ApiClient) {

    suspend fun me(): User = api.get<circolareplus.data.remote.dto.UserDto>("/api/users/me").toDomain().first

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
}
