package circolareplus.background

import circolareplus.data.AppContainer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Tetto ben sotto i ~30 s concessi da iOS a un BGAppRefreshTask. */
private const val SYNC_TIMEOUT_MS = 20_000L

/**
 * Refresh in background iOS: chiede al Worker le circolari con numero maggiore dell'ultimo
 * visto, aggiorna il segnalibro e restituisce quante sono nuove. Niente AI qui.
 *
 * Da Swift: `try await BackgroundSyncKt.backgroundSync()`, dal main thread.
 */
@Throws(Exception::class)
suspend fun backgroundSync(): Int {
    val settings = AppContainer.settings
    if (settings.authToken.isBlank()) throw IllegalStateException("Nessun login: sync saltato")

    return try {
        withTimeout(SYNC_TIMEOUT_MS) {
            val repo = AppContainer.circularsRepository
            var after = settings.bgLastCircularNumber
            if (after == 0) {
                // Primo giro: si fissa il punto di partenza senza notificare l'arretrato.
                after = settings.lastSeenCircularNumber.takeIf { it > 0 }
                    ?: repo.listCirculars(limit = 1).firstOrNull()?.number
                    ?: 0
                settings.bgLastCircularNumber = after
                if (after == 0) return@withTimeout 0
            }
            val newer = repo.listNewerThan(after)
            if (newer.isNotEmpty()) settings.bgLastCircularNumber = newer.maxOf { it.number }
            newer.size
        }
    } catch (e: TimeoutCancellationException) {
        throw Exception("Timeout dopo ${SYNC_TIMEOUT_MS / 1000} s")
    }
}

/**
 * Implementato in Swift (AilaBackground.swift): esegue sync + notifica locale come un
 * risveglio reale. Usato dal pulsante "Simula risveglio" della schermata debug.
 */
interface BackgroundRefreshBridge {
    suspend fun simulateWakeUp(): String
}

/** Iniettato da iOSApp.swift all'avvio. */
object BackgroundRefreshBridgeHolder {
    var bridge: BackgroundRefreshBridge? = null
}
