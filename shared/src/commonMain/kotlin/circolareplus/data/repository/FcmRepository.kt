package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.ClearFcmTokenRequestDto
import circolareplus.data.remote.dto.RegisterFcmTokenRequestDto
import circolareplus.data.remote.dto.SuccessDto

/** platform: "android" oppure "ios". Vedi anche circolareplus.push per la parte piattaforma-specifica. */
class FcmRepository(private val api: ApiClient) {

    suspend fun registerToken(token: String, platform: String) {
        api.post<RegisterFcmTokenRequestDto, SuccessDto>("/api/fcm/token", RegisterFcmTokenRequestDto(token, platform))
    }

    /** platform = null rimuove tutti i token dell'utente (usato al logout). */
    suspend fun clearTokens(platform: String? = null) {
        api.deleteWithBody<ClearFcmTokenRequestDto, SuccessDto>("/api/fcm/token", ClearFcmTokenRequestDto(platform))
    }
}
