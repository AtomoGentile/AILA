package circolareplus.platform

import circolareplus.data.AppContainer

/**
 * Diagnostica del refresh in background (schermata debug nelle Impostazioni).
 * Esiste solo su iOS (BGTaskScheduler): su Android le circolari arrivano già da FCM/WorkManager.
 */
expect fun isBackgroundRefreshSupported(): Boolean

/** Build di debug: la voce "Diagnostica background" è sempre visibile. */
expect fun isDebugBuild(): Boolean

/**
 * Esegue la stessa logica di un risveglio reale (sync + notifica locale) e restituisce l'esito
 * in una riga leggibile.
 */
expect suspend fun simulateBackgroundWakeUp(): String

/**
 * Debug: porta il segnalibro del refresh a (ultima circolare - 1), così il prossimo risveglio
 * trova 1 circolare nuova. Legge soltanto dal server, cambia solo lo storage locale.
 */
suspend fun rewindBackgroundBookmark(): String {
    val newest = AppContainer.circularsRepository.listCirculars(limit = 1).firstOrNull()?.number
        ?: return "Nessuna circolare sul server"
    AppContainer.settings.bgLastCircularNumber = newest - 1
    return "Segnalibro portato a n. ${newest - 1} (ultima: n. $newest)"
}
