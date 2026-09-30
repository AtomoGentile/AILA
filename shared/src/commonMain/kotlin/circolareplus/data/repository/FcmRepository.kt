package circolareplus.data.repository

import circolareplus.data.local.LocalSettingsManager
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.FcmTopicsDto
import circolareplus.data.remote.dto.TopicsSubscribedRequestDto
import circolareplus.push.PushTokenProvider
import circolareplus.data.remote.dto.ClearFcmTokenRequestDto
import circolareplus.data.remote.dto.RegisterFcmTokenRequestDto
import circolareplus.data.remote.dto.SuccessDto

/** platform: "android" oppure "ios". Vedi anche circolareplus.push per la parte piattaforma-specifica. */
class FcmRepository(
    private val api: ApiClient,
    private val settings: LocalSettingsManager,
    private val pushTokens: PushTokenProvider
) {

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
     * Dopo [registerToken]: iscrive il telefono ai topic FCM di istituto e classe e lo dice al server,
     * che da quel momento non manda piu' anche il messaggio per token (un'unica richiesta per
     * notifica, qualunque sia il numero di telefoni: limite del piano Free di Cloudflare). Se
     * l'iscrizione fallisce (o su iOS) il server continua per token.
     */
    suspend fun subscribeToTopics(token: String) {
        val topics = api.get<FcmTopicsDto>("/api/fcm/topics", offlineCopy = false)
        val current = listOf(topics.school, topics.classTopic)
        // Entra un account di un'altra classe su questo telefono: via dai topic di prima, o
        // continuerebbe a ricevere le notifiche della classe precedente.
        pushTokens.unsubscribeFromTopics(settings.fcmTopics.filter { it !in current })
        if (!pushTokens.subscribeToTopics(current)) return
        settings.fcmTopics = current
        api.post<TopicsSubscribedRequestDto, SuccessDto>("/api/fcm/topics/subscribed", TopicsSubscribedRequestDto(token))
    }

    /** Al logout, con la sessione ancora salvata: toglie il telefono dai topic (senza rete: non serve). */
    fun unsubscribeFromTopics() {
        pushTokens.unsubscribeFromTopics(settings.fcmTopics)
        settings.fcmTopics = emptyList()
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

    /**
     * platform = null rimuove tutti i token dell'utente (usato al logout). [authToken]: il token
     * della sessione che si sta chiudendo, se nel frattempo quello salvato e' gia' stato cancellato.
     */
    suspend fun clearTokens(platform: String? = null, authToken: String? = null) {
        lastToken = null
        lastPlatform = null
        api.deleteWithBody<ClearFcmTokenRequestDto, SuccessDto>(
            "/api/fcm/token",
            ClearFcmTokenRequestDto(platform),
            bearerOverride = authToken
        )
    }
}
