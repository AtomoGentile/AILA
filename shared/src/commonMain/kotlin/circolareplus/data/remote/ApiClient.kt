package circolareplus.data.remote

import circolareplus.data.local.LocalSettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Eccezione applicativa per una risposta di errore del Worker (400/401/403/404/409/...).
 * [message] è il testo leggibile restituito dal backend (campo "error" del JSON), quando presente.
 */
class ApiException(override val message: String, val statusCode: Int) : Exception(message)

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
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        install(Logging) { level = LogLevel.INFO }
    }
) {
    @PublishedApi internal val client = engine

    val baseUrl: String
        get() = settings.apiBaseUrlOverride.ifBlank { ApiConfig.DEFAULT_BASE_URL }

    @PublishedApi internal suspend fun HttpResponse.parsedOrThrow(): String {
        val text = bodyAsText()
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

    suspend inline fun <reified T> get(path: String, auth: Boolean = true): T {
        val response = client.get(baseUrl + path) {
            if (auth) authHeader()
        }
        val text = response.parsedOrThrow()
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T {
        val response = client.post(baseUrl + path) {
            if (auth) authHeader()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.parsedOrThrow()
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified B, reified T> put(path: String, body: B, auth: Boolean = true): T {
        val response = client.put(baseUrl + path) {
            if (auth) authHeader()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.parsedOrThrow()
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified T> delete(path: String, auth: Boolean = true): T {
        val response = client.delete(baseUrl + path) {
            if (auth) authHeader()
        }
        val text = response.parsedOrThrow()
        return apiJson.decodeFromString(text)
    }

    suspend inline fun <reified B, reified T> deleteWithBody(path: String, body: B, auth: Boolean = true): T {
        val response = client.delete(baseUrl + path) {
            if (auth) authHeader()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.parsedOrThrow()
        return apiJson.decodeFromString(text)
    }

    /**
     * Scarica un contenuto binario (i PDF delle circolari, proxati da R2) senza tentare la
     * decodifica JSON usata dalle altre funzioni. Usata da [circolareplus.data.repository.CircularsRepository]
     * per alimentare l'estrazione testo PDF client-side.
     */
    suspend fun getBytes(path: String, auth: Boolean = true): ByteArray {
        val response = client.get(baseUrl + path) {
            if (auth) authHeader()
        }
        if (!response.status.isSuccess()) {
            throw ApiException("Impossibile scaricare il file (${response.status.value})", response.status.value)
        }
        return response.body()
    }

    // Non privata: le funzioni inline reified sopra (get/post/put/delete) devono poterla
    // inlineare nel codice chiamante, cosa non permessa per membri "private".
    fun HttpRequestBuilder.authHeader() {
        val token = settings.authToken
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
        }
    }
}
