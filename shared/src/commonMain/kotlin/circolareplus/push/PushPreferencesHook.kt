package circolareplus.push

/**
 * Aggancio per la piattaforma quando l'utente cambia un interruttore delle notifiche nelle
 * Impostazioni. MainAppShell lo chiama dentro lo stesso tocco, prima di qualunque attesa.
 *
 * Serve solo alla PWA: li' le preferenze non passano da FCM ma dall'iscrizione Web Push, e il
 * permesso del browser si puo' chiedere solo durante un gesto dell'utente (Safari su iPhone lo
 * rifiuta altrimenti). Su Android e iOS resta null: nessun cambiamento di comportamento.
 */
object PushPreferencesHook {
    var onChangedByUser: (() -> Unit)? = null
}
