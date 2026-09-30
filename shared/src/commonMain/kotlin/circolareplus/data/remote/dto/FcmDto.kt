package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFcmTokenRequestDto(
    val token: String,
    val platform: String,
    // Preferenze delle Impostazioni: il server le usa per i messaggi iOS, che con l'app in
    // background mostra il sistema senza passare da LocalSettingsManager.onPushReceived.
    val mutedKinds: List<String>? = null,
    val systemNotifications: Boolean? = null
)

@Serializable
data class ClearFcmTokenRequestDto(val platform: String? = null)

/** Nomi (segreti) dei topic FCM di istituto e classe: GET /api/fcm/topics. */
@Serializable
data class FcmTopicsDto(
    val school: String,
    @kotlinx.serialization.SerialName("class") val classTopic: String
)

@Serializable
data class TopicsSubscribedRequestDto(val token: String)
