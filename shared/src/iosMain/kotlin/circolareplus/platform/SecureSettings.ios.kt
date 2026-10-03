package circolareplus.platform

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/**
 * Portachiavi, solo su questo dispositivo (niente backup ne' sincronizzazione iCloud) e leggibile
 * dopo il primo sblocco: il token serve anche alle notifiche e al refresh in background, che
 * girano a telefono bloccato.
 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
actual fun createSecureSettings(): Settings? = KeychainSettings(
    kSecAttrService to CFBridgingRetain("circolareplus.secrets"),
    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
)
