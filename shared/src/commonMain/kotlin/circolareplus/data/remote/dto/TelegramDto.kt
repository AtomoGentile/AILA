package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignatureExpirationRequestDto(
    val expiresAt: String
)

@Serializable
data class TelegramStatusDto(
    val linked: Boolean,
    val active: Boolean,
    val linkedAt: String? = null,
    val signatureExpiresAt: String? = null
)

@Serializable
data class TelegramLinkCodeDto(
    val code: String,
    val expiresAt: String
)
