package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PasskeyInfoDto(
    val credentialId: String,
    val label: String = "Dispositivo",
    val createdAt: String = "",
    val lastUsedAt: String? = null
)

@Serializable
data class PasskeyListResponseDto(
    val passkeys: List<PasskeyInfoDto> = emptyList()
)

@Serializable
data class PasskeyRegisterOptionsDto(
    val challenge: String,
    val rpId: String,
    val rpName: String = "AILA",
    val userId: String,
    val username: String,
    val displayName: String,
    val excludeCredentialIds: List<String> = emptyList()
)

@Serializable
data class PasskeyRegisterVerifyRequestDto(
    val credentialId: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val publicKeySpki: String,
    val algorithm: Int,
    val label: String
)

@Serializable
data class PasskeyLoginOptionsRequestDto(
    val username: String? = null
)

@Serializable
data class PasskeyLoginOptionsDto(
    val challenge: String,
    val rpId: String,
    val allowCredentialIds: List<String> = emptyList()
)

@Serializable
data class PasskeyLoginVerifyRequestDto(
    val credentialId: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String
)
