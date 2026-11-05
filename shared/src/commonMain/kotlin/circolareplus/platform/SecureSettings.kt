package circolareplus.platform

import com.russhwolf.settings.Settings

/** Native secrets use Android Keystore encryption or the iOS Keychain. Web uses site storage. */
expect fun createSecureSettings(): Settings?
