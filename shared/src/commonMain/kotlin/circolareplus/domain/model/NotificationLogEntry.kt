package circolareplus.domain.model

import kotlinx.serialization.Serializable

/**
 * Una notifica ricevuta, tenuta in un log locale sul dispositivo (mai sul server): l'app non ha
 * mai avuto un vero storico notifiche, solo l'invio push "al volo". Vedi [circolareplus.data.local.NotificationLogStore].
 */
@Serializable
data class NotificationLogEntry(
    val id: String,
    val title: String,
    val body: String,
    val receivedAtMillis: Long,
    val read: Boolean = false,
    /**
     * Chiave di [circolareplus.ui.screens.NotificationKind] (o "seatmap_preferences" per la
     * votazione, più specifica del semplice "seatmap"): dice a NotificationsScreen dove portare
     * l'utente al tocco. Vuota per le notifiche push generiche, che non sanno da dove vengono.
     */
    val category: String = ""
)
