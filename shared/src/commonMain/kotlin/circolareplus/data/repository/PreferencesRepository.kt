package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.ClassConfigDto
import circolareplus.data.remote.dto.MyPreferencesResponseDto
import circolareplus.data.remote.dto.PreferenceMatrixResponseDto
import circolareplus.data.remote.dto.PreferencesConfigDto
import circolareplus.data.remote.dto.PreferencesProgressDto
import circolareplus.data.remote.dto.SetPreferencesConfigRequestDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.VoteSocialPreferenceRequestDto
import circolareplus.domain.model.SocialPreferenceScore

/**
 * Trigger Admin (`preferences_open`) + votazione preferenze sociali sui compagni.
 * I voti -2 restano "blindati": il matrix endpoint è riservato al Rappresentante e va
 * usato SOLO per alimentare il SeatMapOptimizer, mai mostrato in una UI leggibile dagli studenti.
 */
class PreferencesRepository(private val api: ApiClient) {

    // NB: il router `preferences.ts` è montato su `/api/preferences` e definisce la rotta come
    // `/config` relativo a quel mount, quindi il path reale è `/api/preferences/config` — prima
    // era invertito in `/api/config/preferences`, che non esiste (404 "endpoint non trovato"),
    // motivo per cui l'apertura/chiusura della finestra voti falliva sempre e la votazione dei
    // posti non risultava mai attiva.
    suspend fun getConfig(): PreferencesConfigDto = api.get("/api/preferences/config")

    /**
     * Stessa risposta di [getConfig], letta però con il DTO che comprende anche `classId` e
     * `classLabel`. È l'unico punto in cui l'app scopre in che classe si trova: il profilo
     * utente (`/api/users/me`) li restituisce, ma il suo DTO vive nella cartella `dto/` che il
     * ponte con il computer non raggiunge (vedi la nota in AilaApiDto.kt).
     */
    suspend fun getClassConfig(): ClassConfigDto = api.get("/api/preferences/config")

    /** Solo Rappresentante: apre/chiude la finestra di raccolta voti (invia anche la push FCM). */
    suspend fun setPreferencesOpen(open: Boolean): PreferencesConfigDto {
        api.post<SetPreferencesConfigRequestDto, SuccessDto>(
            "/api/preferences/config",
            SetPreferencesConfigRequestDto(open)
        )
        return getConfig()
    }

    /** score: -2, -1, 0, 1, 2. Il server applica i vincoli anti-gaming (max 2 voti +2 e max 2 -2). */
    suspend fun vote(toStudentId: String, score: SocialPreferenceScore) {
        api.post<VoteSocialPreferenceRequestDto, SuccessDto>(
            "/api/preferences/vote",
            VoteSocialPreferenceRequestDto(toStudentId, score.value)
        )
    }

    /** Quanti compagni hanno gia' votato e quanti mancano (i nomi solo al Rappresentante). */
    suspend fun progress(): PreferencesProgressDto = api.get("/api/preferences/progress")

    suspend fun myVotes(): MyPreferencesResponseDto = api.get("/api/preferences/my")

    /** Solo Rappresentante, solo per l'algoritmo: include anche i voti -2. */
    suspend fun matrixForAlgorithm(): PreferenceMatrixResponseDto = api.get("/api/preferences/matrix")
}
