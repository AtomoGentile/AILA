package circolareplus.platform

import circolareplus.data.remote.dto.PasskeyLoginOptionsDto
import circolareplus.data.remote.dto.PasskeyRegisterOptionsDto

/** Lanciata quando l'utente annulla il prompt Face ID / Touch ID / Windows Hello. */
class PasskeyCancelledException : Exception("Operazione annullata")

data class PasskeyCreationResult(
    val credentialId: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val publicKeySpki: String,
    val algorithm: Int
)

data class PasskeyAssertionResult(
    val credentialId: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String
)

/** True solo dove WebAuthn / Passkey e' disponibile (PWA / browser moderni su HTTPS o localhost). */
expect fun isPasskeySupported(): Boolean

/** Etichetta predefinita per una nuova passkey (es. "iPhone / iPad", "Android", "Computer"). */
expect fun defaultPasskeyDeviceLabel(): String

expect suspend fun createPlatformPasskey(options: PasskeyRegisterOptionsDto): PasskeyCreationResult

expect suspend fun signInWithPlatformPasskey(options: PasskeyLoginOptionsDto): PasskeyAssertionResult
