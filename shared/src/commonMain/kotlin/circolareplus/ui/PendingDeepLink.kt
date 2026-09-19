package circolareplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    var category by mutableStateOf<String?>(null)
}
