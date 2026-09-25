package circolareplus.platform

import android.content.pm.ApplicationInfo

actual fun isBackgroundRefreshSupported(): Boolean = false

actual fun isDebugBuild(): Boolean {
    val flags = AndroidAppContext.getOrNull()?.applicationInfo?.flags ?: return false
    return flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

actual suspend fun simulateBackgroundWakeUp(): String = "Non applicabile su Android"
