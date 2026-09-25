package circolareplus.platform

import circolareplus.background.BackgroundRefreshBridgeHolder
import kotlin.experimental.ExperimentalNativeApi

actual fun isBackgroundRefreshSupported(): Boolean = true

@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary

actual suspend fun simulateBackgroundWakeUp(): String {
    // Il bridge è iniettato da iOSApp.swift: la notifica locale la mostra Swift.
    val bridge = BackgroundRefreshBridgeHolder.bridge ?: return "Bridge Swift non collegato"
    return try {
        bridge.simulateWakeUp()
    } catch (e: Exception) {
        "Errore: ${e.message}"
    }
}
