package circolareplus.domain.model

/**
 * Converte il payload `data` di un messaggio push FCM nella categoria usata da
 * [circolareplus.ui.screens.NotificationKind] (o "seatmap_preferences" per la votazione, o
 * "ranking_polls" per i sondaggi a ordinamento), così
 * sia la campanella in-app sia il tocco sulla notifica di sistema (Android e iOS) sanno dove
 * portare l'utente. Le chiavi vanno tenute allineate al campo `data.action` inviato dalle rotte
 * del backend (vedi notifyClass/notifyUser in services/fcm.ts); i fallback sotto coprono le
 * chiamate più vecchie che non avevano ancora `action`.
 */
object NotificationCategoryMapper {
    fun categoryFrom(data: Map<String, String>): String {
        val fromAction = when (data["action"]) {
            "open_preferences" -> "seatmap_preferences"
            "preferences_complete" -> "seatmap"
            "seat_map_updated" -> "seatmap"
            "ranking_poll_published" -> "ranking_polls"
            "poll_published", "poll_complete", "swap_request", "swap_accepted" -> "polls"
            "new_circular" -> "circulars"
            "new_proposal" -> "board"
            else -> null
        }
        if (fromAction != null) return fromAction

        return when {
            data.containsKey("circular_number") -> "circulars"
            data.containsKey("proposal_id") -> "board"
            data.containsKey("poll_id") || data.containsKey("swap_id") || data.containsKey("grid_id") -> "polls"
            else -> ""
        }
    }
}
