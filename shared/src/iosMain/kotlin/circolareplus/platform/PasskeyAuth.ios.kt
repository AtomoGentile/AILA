package circolareplus.platform

import circolareplus.data.remote.dto.PasskeyLoginOptionsDto
import circolareplus.data.remote.dto.PasskeyRegisterOptionsDto

actual fun isPasskeySupported(): Boolean = false

actual fun defaultPasskeyDeviceLabel(): String = "iPhone / iPad"

actual suspend fun createPlatformPasskey(options: PasskeyRegisterOptionsDto): PasskeyCreationResult {
    throw UnsupportedOperationException("Passkey supportate sulla versione Web / PWA")
}

actual suspend fun signInWithPlatformPasskey(options: PasskeyLoginOptionsDto): PasskeyAssertionResult {
    throw UnsupportedOperationException("Passkey supportate sulla versione Web / PWA")
}
