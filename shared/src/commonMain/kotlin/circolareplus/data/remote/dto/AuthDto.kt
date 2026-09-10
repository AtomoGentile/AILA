package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val firstName: String,
    val lastName: String,
    val username: String,
    val password: String,
    val heightCm: Int,
    // Se valorizzato e corretto, il server assegna il ruolo REPRESENTATIVE invece di STUDENT.
    val representativeCode: String? = null
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class UserDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    // Nullable: GET /api/users (elenco compagni) restituisce lo username solo al Rappresentante,
    // per privacy. GET /api/users/me e login/registrazione lo restituiscono sempre.
    val username: String? = null,
    val role: String,
    val heightCm: Int? = null,
    val priorityPass: Boolean? = null,
    val notificationBoardEnabled: Boolean? = null,
    val createdAt: String? = null
)

@Serializable
data class AuthResponseDto(
    val success: Boolean,
    val token: String,
    val user: UserDto
)

@Serializable
data class ToggleBoardNotificationsRequestDto(val boardEnabled: Boolean)

@Serializable
data class ToggleBoardNotificationsResponseDto(val success: Boolean, val notificationBoardEnabled: Boolean)

@Serializable
data class UsersListResponseDto(val users: List<UserDto>)
