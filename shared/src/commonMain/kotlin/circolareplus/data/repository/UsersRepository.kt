package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.UsersListResponseDto
import circolareplus.domain.model.User

class UsersRepository(private val api: ApiClient) {

    suspend fun me(): User = api.get<circolareplus.data.remote.dto.UserDto>("/api/users/me").toDomain().first

    /** Solo Rappresentante: elenco di tutti gli studenti della classe. */
    suspend fun listStudents(): List<User> =
        api.get<UsersListResponseDto>("/api/users").users.map { it.toDomain().first }
}
