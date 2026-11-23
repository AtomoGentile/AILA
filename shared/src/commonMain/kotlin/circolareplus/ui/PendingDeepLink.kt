package circolareplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Dati precompilati da un link di recupero password (`?reset=CODICE&u=USERNAME`). */
data class PendingPasswordReset(
    val username: String,
    val code: String
)

/**
 * Categoria di notifica da cui l'utente ha appena aperto/riportato in primo piano l'app — tocco
 * su una notifica di sistema, a freddo o ad app già in background — letta da [MainAppShell] per
 * portarlo dritto alla schermata giusta, la stessa destinazione della campanella in-app (vedi
 * [circolareplus.domain.model.NotificationCategoryMapper]).
 *
 * Scritta dal codice nativo di piattaforma PRIMA o DURANTE la composizione di MainAppShell:
 * - Android: MainActivity.onCreate (avvio a freddo dalla notifica) e onNewIntent (app già in
 *   background, riportata in primo piano dal tocco);
 * - iOS: AppDelegate, sia da `launchOptions[.remoteNotification]` (avvio a freddo) sia da
 *   `userNotificationCenter(_:didReceive:)` (app in background).
 *
 * `mutableStateOf` invece di una semplice var: MainAppShell la osserva con `snapshotFlow`, quindi
 * un valore scritto mentre l'app è già in esecuzione (tap con app in background) viene notificato
 * subito, non solo alla prima composizione.
 */
object PendingDeepLink {
    var forumThreadId: String? = null
    var category by mutableStateOf<String?>(null)
    var passwordReset by mutableStateOf<PendingPasswordReset?>(null)
}

/**
 * Costruisce il link diretto di recupero password condivisibile dal Rappresentante.
 * Username e codice vengono normalizzati (niente spazi/trattini nel codice, solo caratteri sicuri).
 */
fun buildPasswordResetLink(baseUrl: String, username: String?, code: String): String {
    val cleanBase = baseUrl.trim().ifEmpty { "https://aila-scuola.pages.dev/" }.let {
        if (it.endsWith("/")) it else "$it/"
    }
    val cleanCode = code.trim().uppercase().filter { it.isLetterOrDigit() }
    val cleanUser = username?.trim()?.lowercase()?.filter {
        it.isLetterOrDigit() || it == '_' || it == '.'
    }.orEmpty()
    return if (cleanUser.isNotEmpty()) {
        "${cleanBase}?reset=$cleanCode&u=$cleanUser"
    } else {
        "${cleanBase}?reset=$cleanCode"
    }
}

/** Valida e normalizza i parametri `?reset=...&u=...` letti all'apertura della PWA. */
fun parsePasswordResetQuery(resetParam: String?, usernameParam: String?): PendingPasswordReset? {
    val cleanCode = resetParam?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }.orEmpty()
    if (cleanCode.isEmpty() || cleanCode.length > 24) return null
    val cleanUser = usernameParam?.trim()?.lowercase()?.filter {
        it.isLetterOrDigit() || it == '_' || it == '.'
    }.orEmpty().take(32)
    return PendingPasswordReset(username = cleanUser, code = cleanCode)
}
