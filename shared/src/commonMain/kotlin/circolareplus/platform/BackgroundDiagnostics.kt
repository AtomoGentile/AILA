package circolareplus.platform

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
