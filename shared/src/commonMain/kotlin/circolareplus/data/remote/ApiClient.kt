package circolareplus.data.remote

import circolareplus.data.local.LocalSettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import circolareplus.platform.OfflineStore
import circolareplus.platform.currentTimeMillis
import circolareplus.platform.readText
import circolareplus.platform.writeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Eccezione applicativa per una risposta di errore del Worker (400/401/403/404/409/...).
 * [message] è il testo leggibile restituito dal backend (campo "error" del JSON), quando presente.
 */
class ApiException(override val message: String, val statusCode: Int) : Exception(message)

/** statusCode di [ApiException] quando il server non e' raggiungibile (niente rete). */
const val OFFLINE_STATUS = 0

/**
 * Stato della connessione visto dalle chiamate al server, osservabile da Compose: la striscia
 * "offline" in cima all'app compare e sparisce da sola seguendo questo, anche se la rete cade o
 * torna a app gia' aperta.
 */
object ConnectivityState {
    var isOffline by mutableStateOf(false)
        internal set

    /** Ultima volta che il server ha risposto (millisecondi epoch), 0 = mai in questa sessione. */
    var lastOnlineMillis by mutableStateOf(0L)
        internal set

    internal var lastFailureMillis = 0L

    /**
     * Subito dopo un errore di rete si risponde dalla copia offline senza ritentare: a scuola il
     * Wi-Fi senza Internet (o a pagamento) fa aspettare il timeout intero a ogni richiesta, e
     * l'app sembrerebbe bloccata. Passata la finestra si riprova la rete.
     */
    internal fun shouldSkipNetwork(): Boolean =
        isOffline && currentTimeMillis() - lastFailureMillis < 20_000L

    @PublishedApi internal fun markOnline() {
        if (isOffline) isOffline = false
        lastOnlineMillis = currentTimeMillis()
    }

    @PublishedApi internal fun markOffline() {
        lastFailureMillis = currentTimeMillis()
        if (!isOffline) isOffline = true
    }
}

/** Segnale "la sessione non vale piu'", osservato da MainAppShell. */
object SessionEvents {
    var expired by mutableStateOf(false)
}

val apiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Wrapper condiviso KMP attorno a Ktor per parlare con il Worker Cloudflare di AILA.
 * Aggiunge automaticamente l'header Authorization con il token JWT salvato localmente
 * (impostato da [circolareplus.data.repository.AuthRepository] dopo login/registrazione).
 */
class ApiClient(
    @PublishedApi internal val settings: LocalSettingsManager,
    engine: HttpClient = HttpClient {
        install(ContentNegotiation) { json(apiJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
        install(HttpRequestRetry) {
            // Solo le letture: ritentare una POST/PUT dopo un 5xx poteva creare doppioni
            // (proposte, commenti, eventi) se il server aveva gia' salvato prima dell'errore.
            maxRetries = 2
            retryIf { request, response ->
                request.method == HttpMethod.Get && response.status.value in 500..599
            }
            exponentialDelay()
        }
    }
) {
    @PublishedApi internal val client = engine

    val baseUrl: String
        get() = settings.apiBaseUrlOverride.ifBlank { ApiConfig.DEFAULT_BASE_URL }

    @PublishedApi internal suspend fun HttpResponse.parsedOrThrow(): String {
        val text = bodyAsText()
        // Il server non riconosce piu' la sessione (password cambiata su un altro telefono,
        // account eliminato, token scaduto): l'app torna al login invece di mostrare errori ovunque.
        // Solo se la richiesta usava la sessione in corso (non, per esempio, quella appena chiusa).
        val current = settings.authToken
        if (status.value == 401 && current.isNotBlank() && request.headers[HttpHeaders.Authorization] == "Bearer $current") {
            SessionEvents.expired = true
        }
        if (!status.isSuccess()) {
            val errorMessage = try {
                val obj = apiJson.parseToJsonElement(text) as? JsonObject
                obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "Errore del server (${status.value})"
            } catch (e: Exception) {
                "Errore del server (${status.value})"
            }
            throw ApiException(errorMessage, status.value)
        }
        return text
    }

    /**
     * GET con copia offline: ogni risposta buona viene salvata su disco e, se poi il server non
     * e' raggiungibile (niente rete, timeout, server giu'), si restituisce l'ultima copia salvata
     * invece di fallire. Cosi' tutta l'app resta consultabile senza connessione con gli ultimi
     * dati scaricati. [offlineCopy] = false per le richieste che devono per forza parlare col
     * server (verifica della sessione, elenchi "novita' dopo X").
     */
    suspend inline fun <reified T> get(path: String, auth: Boolean = true, offlineCopy: Boolean = true): T {
        val text = fetchText(path, auth, offlineCopy)
        return apiJson.decodeFromString(text)
    }

    /** Come [get] ma restituisce il testo grezzo (stessa copia offline). */
    suspend fun getText(path: String, auth: Boolean = true, offlineCopy: Boolean = true): String =
        fetchText(path, auth, offlineCopy)

    @PublishedApi internal fun offlineName(path: String, prefix: String): String {
        val readable = path.replace(Regex("[^A-Za-z0-9]"), "_").take(80)
        return "${prefix}_${readable}_${path.hashCode().toUInt().toString(16)}"
    }

    @PublishedApi internal suspend fun readOffline(name: String): String? =
        withContext(Dispatchers.Default) { OfflineStore.readText(name) }

    @PublishedApi internal suspend fun fetchText(path: String, auth: Boolean, offlineCopy: Boolean): String {
        val name = offlineName(path, "j")
        if (offlineCopy && ConnectivityState.shouldSkipNetwork()) {
            readOffline(name)?.let { return it }
        }
        return try {
            val response = client.get(baseUrl + path) {
                if (auth) authHeader()
            }
            val text = response.parsedOrThrow()
            ConnectivityState.markOnline()
            if (offlineCopy) {
                withContext(Dispatchers.Default) { OfflineStore.writeText(name, text) }
                settings.lastOnlineSyncMillis = currentTimeMillis()
            }
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // Il server ha risposto: la rete c'e'. Se pero' e' giu' (5xx) meglio i dati salvati
            // di una schermata d'errore.
            ConnectivityState.markOnline()
            if (offlineCopy && e.statusCode >= 500) readOffline(name) ?: throw e else throw e
        } catch (e: Exception) {
            ConnectivityState.markOffline()
            if (offlineCopy) readOffline(name) ?: throw e else throw e
        }
    }

    /**
     * Le modifiche (voti, messaggi, proposte...) richiedono il server: senza rete si ferma subito
     * con un messaggio chiaro invece dell'eccezione tecnica di Ktor.
     */
    @PublishedApi internal suspend inline fun sendText(request: () -> HttpResponse): String {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectivityState.markOffline()
            throw ApiException("Sei offline: questa azione richiede la connessione. Riprova quando torni online.", OFFLINE_STATUS)
        }
        ConnectivityState.markOnline()
        return response.parsedOrThrow()
    }

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T {
        val text = sendText {
            client.post(baseUrl + path) {
                if (auth) authHeader()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified B, reified T> put(path: String, body: B, auth: Boolean = true): T {
        val text = sendText {
            client.put(baseUrl + path) {
                if (auth) authHeader()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified T> delete(path: String, auth: Boolean = true): T {
        val text = sendText {
            client.delete(baseUrl + path) {
                if (auth) authHeader()
            }
        }
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified B, reified T> deleteWithBody(
        path: String,
        body: B,
        auth: Boolean = true,
        bearerOverride: String? = null
    ): T {
        val text = sendText {
            client.delete(baseUrl + path) {
                if (bearerOverride != null) header("Authorization", "Bearer $bearerOverride")
                else if (auth) authHeader()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return apiJson.decodeFromString(text)
    }

    /**
     * Scarica un contenuto binario (i PDF delle circolari, proxati da R2) senza tentare la
     * decodifica JSON usata dalle altre funzioni. Usata da [circolareplus.data.repository.CircularsRepository]
     * per alimentare l'estrazione testo PDF client-side.
     */
    suspend fun getBytes(path: String, auth: Boolean = true): ByteArray {
        // I PDF non cambiano mai una volta pubblicati: se c'e' la copia su disco si usa quella,
        // anche online (piu' veloce e senza consumare dati).
        val name = offlineName(path, "b")
        withContext(Dispatchers.Default) { OfflineStore.readBytes(name) }?.let { return it }
        val response = try {
            client.get(baseUrl + path) {
                if (auth) authHeader()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectivityState.markOffline()
            throw ApiException("Sei offline e questo file non e' ancora stato scaricato su questo dispositivo.", OFFLINE_STATUS)
        }
        ConnectivityState.markOnline()
        if (!response.status.isSuccess()) {
            throw ApiException("Impossibile scaricare il file (${response.status.value})", response.status.value)
        }
        val bytes: ByteArray = response.body()
        withContext(Dispatchers.Default) { OfflineStore.writeBytes(name, bytes) }
        return bytes
    }

    /** true se il file e' gia' disponibile offline (senza scaricarlo). */
    fun hasOfflineBytes(path: String): Boolean = OfflineStore.exists(offlineName(path, "b"))

    // Non privata: le funzioni inline reified sopra (get/post/put/delete) devono poterla
    // inlineare nel codice chiamante, cosa non permessa per membri "private".
    fun HttpRequestBuilder.authHeader() {
        val token = settings.authToken
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
        }
    }
}
