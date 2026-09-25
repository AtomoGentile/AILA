package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.ClearFcmTokenRequestDto
import circolareplus.data.remote.dto.RegisterFcmTokenRequestDto
import circolareplus.data.remote.dto.SuccessDto

/** platform: "android" oppure "ios". Vedi anche circolareplus.push per la parte piattaforma-specifica. */
class FcmRepository(private val api: ApiClient) {

    // Ultimo token registrato in questa sessione: serve a rimandare le preferenze quando cambiano
    // nelle Impostazioni, senza richiedere di nuovo il token al sistema.
    private var lastToken: String? = null
    private var lastPlatform: String? = null

    suspend fun registerToken(
        token: String,
        platform: String,
        mutedKinds: List<String>? = null,
        systemNotifications: Boolean? = null
    ) {
        api.post<RegisterFcmTokenRequestDto, SuccessDto>(
            "/api/fcm/token",
            RegisterFcmTokenRequestDto(token, platform, mutedKinds, systemNotifications)
        )
        lastToken = token
        lastPlatform = platform
    }

    /**
     * Rimanda al server le preferenze notifiche dopo un cambio nelle Impostazioni. Non fa nulla se
     * in questa sessione non e' stato registrato alcun token (Firebase non configurato).
     */
    suspend fun syncPreferences(mutedKinds: List<String>, systemNotifications: Boolean) {
        val token = lastToken ?: return
        val platform = lastPlatform ?: return
        registerToken(token, platform, mutedKinds, systemNotifications)
    }

    /** platform = null rimuove tutti i token dell'utente (usato al logout). */
    suspend fun clearTokens(platform: String? = null) {
        lastToken = null
        lastPlatform = null
        api.deleteWithBody<ClearFcmTokenRequestDto, SuccessDto>("/api/fcm/token", ClearFcmTokenRequestDto(platform))
    }
}
