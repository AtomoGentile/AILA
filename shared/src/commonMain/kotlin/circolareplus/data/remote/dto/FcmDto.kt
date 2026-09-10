package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFcmTokenRequestDto(val token: String, val platform: String)

@Serializable
data class ClearFcmTokenRequestDto(val platform: String? = null)
