package circolareplus.data.repository

import circolareplus.data.local.LocalSettingsManager
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.apiJson
import circolareplus.data.remote.dto.LoginRequestDto
import circolareplus.data.remote.dto.AuthResponseDto
import circolareplus.data.remote.dto.ClassesListResponseDto
import circolareplus.data.remote.dto.ClassOptionDto
import circolareplus.data.remote.dto.ChangePasswordRequestDto
import circolareplus.data.remote.dto.ChangePasswordResponseDto
import circolareplus.data.remote.dto.ResetPasswordRequestDto
import circolareplus.data.remote.dto.DeleteAccountRequestDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.RegisterWithClassRequestDto
import circolareplus.data.remote.dto.ToggleBoardNotificationsRequestDto
import circolareplus.data.remote.dto.ToggleBoardNotificationsResponseDto
import circolareplus.data.remote.dto.UserDto
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Esito del ripristino di sessione all'avvio.
 *
 * Prima [restoreSession] restituiva solo `Pair?`, e questo era la causa di un bug preciso
 * segnalato da Simone: con la modalità aereo attiva l'app lo buttava fuori dall'account. Il
 * motivo è che il `catch (e: Exception)` non distingueva "il token non vale più" da "non c'è
 * rete", e in entrambi i casi cancellava il token salvato. Un aereo, una galleria o un secondo
 * di 4G ballerino equivalevano quindi a un logout, con la password da riscrivere.
 *
 * Ora i due casi sono separati: solo un 401/403 del server (l'unico che dica davvero che la
 * sessione non è più valida) chiude la sessione; qualunque altro errore vale come "offline" e
 * l'app riparte con l'ultimo utente conosciuto, mostrando i propri stati di errore.
 */
sealed class SessionRestore {
    /** Server raggiunto: dati freschi. */
    data class Online(val user: User, val profile: StudentProfile) : SessionRestore()

    /** Server irraggiungibile ma token ancora valido: si entra con l'ultimo profilo salvato. */
    data class Offline(val user: User, val profile: StudentProfile) : SessionRestore()

    /**
     * Server irraggiungibile e nessun profilo in cache (primo avvio dopo l'installazione, mai
     * andato a buon fine). Non si può entrare, ma il token resta: al ritorno della rete non
     * servirà rifare il login.
     */
    object OfflineWithoutCache : SessionRestore()

    /** Il server ha risposto che la sessione non vale più: qui il logout è corretto. */
    object SessionExpired : SessionRestore()

    /** Nessun token salvato: si va al login, come sempre. */
    object NoSession : SessionRestore()
}

/**
 * Autenticazione reale contro le rotte /api/auth del Worker Cloudflare (nessuna selezione
 * classe: la classe è unica e fissa lato server, come da Specifica Tecnica Master v3.0).
 */
class AuthRepository(
    private val api: ApiClient,
    private val settings: LocalSettingsManager
) {

    /** True se è presente un token salvato localmente (non garantisce che sia ancora valido). */
    fun hasStoredSession(): Boolean = settings.authToken.isNotBlank()

    /**
     * Elenco delle classi già esistenti, per il selettore in registrazione. Non richiede il
     * token: serve prima di avere un account.
     */
    suspend fun listClasses(): List<ClassOptionDto> {
        val response: ClassesListResponseDto = api.get("/api/auth/classes", auth = false)
        return response.classes
    }

    /**
     * Registrazione. [classLabel] è nuovo: prima la classe era una sola, fissa lato server, e
     * chiunque si registrasse finiva a vedere bacheca, calendario e mappa posti di quella.
     */
    suspend fun register(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        heightCm: Int,
        classLabel: String,
        representativeCode: String? = null,
        classCode: String? = null
    ): Pair<User, StudentProfile> {
        val response: AuthResponseDto = api.post(
            "/api/auth/register",
            RegisterWithClassRequestDto(
                firstName = firstName,
                lastName = lastName,
                username = username,
                password = password,
                heightCm = heightCm,
                representativeCode = representativeCode?.ifBlank { null },
                classLabel = classLabel,
                classCode = classCode?.trim()?.ifBlank { null }
            ),
            auth = false
        )
        return persistSession(response)
    }

    /**
     * Nuova password con il codice monouso dato dal Rappresentante (nell'app non c'e' un'email
     * per il recupero). A reset riuscito si e' gia' dentro, come dopo un login.
     */
    suspend fun resetPassword(username: String, code: String, newPassword: String): Pair<User, StudentProfile> {
        val response: AuthResponseDto = api.post(
            "/api/auth/reset-password",
            ResetPasswordRequestDto(username.trim(), code.trim(), newPassword),
            auth = false
        )
        return persistSession(response)
    }

    /**
     * Cambia la password. Il server restituisce un token nuovo (quelli di prima smettono di
     * valere, anche su altri telefoni): si salva subito, cosi' qui la sessione continua.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val response: ChangePasswordResponseDto = api.put(
            "/api/users/me/password",
            ChangePasswordRequestDto(currentPassword, newPassword)
        )
        if (response.token.isNotBlank()) settings.authToken = response.token
    }

    suspend fun login(username: String, password: String): Pair<User, StudentProfile> {
        val response: AuthResponseDto = api.post(
            "/api/auth/login",
            LoginRequestDto(username, password),
            auth = false
        )
        return persistSession(response)
    }

    /**
     * Ricarica utente + profilo usando il token già salvato (es. all'avvio dell'app).
     *
     * Vedi [SessionRestore] per il perché della distinzione fra sessione scaduta e assenza di
     * rete: è il punto in cui la modalità aereo faceva uscire dall'account.
     */
    suspend fun restoreSession(): SessionRestore {
        if (!hasStoredSession()) return SessionRestore.NoSession
        return try {
            // Mai dalla copia offline: qui si verifica proprio se il server risponde.
            val dto: UserDto = api.get("/api/users/me", offlineCopy = false)
            cacheUser(dto)
            val (user, profile) = dto.toDomain()
            SessionRestore.Online(user, profile)
        } catch (e: ApiException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                // Questo sì è un logout: il server dice esplicitamente che il token non vale più.
                logout()
                SessionRestore.SessionExpired
            } else {
                // 500, 502, manutenzione del Worker...: il token è ancora buono, non si tocca.
                offlineFromCache()
            }
        } catch (e: Exception) {
            // Nessuna rete, DNS che non risolve, timeout: NON è un logout.
            offlineFromCache()
        }
    }

    private fun offlineFromCache(): SessionRestore {
        val cached = cachedUser() ?: return SessionRestore.OfflineWithoutCache
        val (user, profile) = cached.toDomain()
        return SessionRestore.Offline(user, profile)
    }

    /**
     * Ultimo profilo conosciuto, salvato in locale a ogni accesso riuscito. Serve solo a poter
     * entrare senza rete: non contiene circolari, voti o proposte, che restano da scaricare.
     */
    private fun cacheUser(dto: UserDto) {
        settings.cachedUserJson = try {
            apiJson.encodeToString(dto)
        } catch (e: Exception) {
            ""
        }
    }

    private fun cachedUser(): UserDto? {
        val raw = settings.cachedUserJson
        if (raw.isBlank()) return null
        return try {
            apiJson.decodeFromString<UserDto>(raw)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toggleBoardNotifications(enabled: Boolean): Boolean {
        val response: ToggleBoardNotificationsResponseDto = api.put(
            "/api/users/me/notifications",
            ToggleBoardNotificationsRequestDto(enabled)
        )
        return response.notificationBoardEnabled
    }

    /**
     * Elimina definitivamente l'account sul server, dopo aver verificato la [password].
     *
     * Solo a eliminazione riuscita si chiude la sessione locale e si cancella la cronologia
     * dell'assistente (che sta sul telefono ma e' fatta di dati dell'account): se il server
     * rifiuta — password sbagliata, rete assente — l'utente resta dov'e' e riprova.
     */
    suspend fun deleteAccount(password: String) {
        api.deleteWithBody<DeleteAccountRequestDto, SuccessDto>(
            "/api/users/me",
            DeleteAccountRequestDto(password)
        )
        logoutAndWipe()
    }

    /**
     * Chiude la sessione (token scaduto, password cambiata altrove, "Esci" dalla schermata offline).
     * I dati personali restano: se rientra la stessa persona li ritrova; se entra qualcun altro
     * li cancella [persistSession].
     */
    fun logout() {
        settings.authToken = ""
        settings.currentUserId = ""
        settings.cachedUserJson = ""
        // I dati offline sono dell'account appena uscito: non devono restare a chi entra dopo.
        circolareplus.platform.OfflineStore.clear()
        settings.lastOnlineSyncMillis = 0L
        settings.lastFullOfflineSyncMillis = 0L
    }

    /** Esce e cancella anche i dati personali salvati sul telefono (vedi LocalSettingsManager.clearUserData). */
    private fun logoutAndWipe() {
        logout()
        settings.clearUserData()
    }

    /**
     * Uscita scelta dall'utente: prima si toglie dal server il token push di questo telefono, poi
     * si chiude la sessione locale. La richiesta parte con il token catturato qui, perche' subito
     * dopo quello salvato viene cancellato: prima partiva senza autorizzazione (401), il token push
     * restava sul server e il telefono continuava a ricevere le notifiche dell'account uscito.
     * Non blocca l'uscita: senza rete il logout locale avviene comunque.
     */
    fun signOut(fcmRepository: FcmRepository, pushPlatform: String?) {
        // Prima della sessione: senza, il telefono resta iscritto ai topic della classe.
        fcmRepository.unsubscribeFromTopics()
        val token = settings.authToken
        if (token.isNotBlank()) {
            circolareplus.data.AppContainer.appScope.launch {
                try {
                    fcmRepository.clearTokens(pushPlatform, authToken = token)
                } catch (e: Exception) {
                    // Rete assente: il token verra' riassegnato al prossimo accesso su questo telefono.
                }
            }
        }
        logoutAndWipe()
    }

    private fun persistSession(response: AuthResponseDto): Pair<User, StudentProfile> {
        // Entra un account diverso da quello a cui appartengono i dati personali salvati qui
        // (chiave AI, campanella, cronologia dell'assistente): si cancellano prima di cominciare.
        val owner = settings.dataOwnerId.ifBlank { settings.currentUserId }
        if (owner.isNotBlank() && owner != response.user.id) settings.clearUserData()
        settings.dataOwnerId = response.user.id
        settings.authToken = response.token
        settings.currentUserId = response.user.id
        cacheUser(response.user)
        return response.user.toDomain()
    }
}

fun UserDto.toDomain(): Pair<User, StudentProfile> {
    val user = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        username = username ?: "",
        role = try {
            UserRole.valueOf(role)
        } catch (e: Exception) {
            UserRole.STUDENT
        }
    )
    val profile = StudentProfile(
        userId = id,
        heightCm = heightCm ?: 175,
        priorityPass = priorityPass ?: false,
        notificationBoardEnabled = notificationBoardEnabled ?: true
    )
    return user to profile
}
