package circolareplus.platform

// Sul web l'aggiornamento in background lo fa il service worker quando arriva una notifica push.
actual fun isBackgroundRefreshSupported(): Boolean = true

actual fun isDebugBuild(): Boolean = false

actual suspend fun simulateBackgroundWakeUp(): String = "Non disponibile sul web"
