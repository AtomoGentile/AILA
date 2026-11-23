@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package circolareplus.platform

import circolareplus.data.remote.dto.PasskeyLoginOptionsDto
import circolareplus.data.remote.dto.PasskeyRegisterOptionsDto
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyRef
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnRef
import platform.UIKit.UIDevice
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val NATIVE_PASSKEY_ORIGIN = "https://aila-scuola.pages.dev"
private const val UD_CREDENTIAL_IDS = "aila_native_passkey_ids"
private const val UD_LAST_CREDENTIAL_ID = "aila_native_passkey_last"
private const val UD_SIGN_COUNT_PREFIX = "aila_native_passkey_sc_"
private const val KEY_TAG_PREFIX = "com.circolareplus.passkey."

// Header ASN.1 DER SubjectPublicKeyInfo (26 byte) per ECDSA P-256 (id-ecPublicKey + prime256v1).
private val P256_SPKI_PREFIX = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(),
    0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D,
    0x03, 0x01, 0x07, 0x03, 0x42, 0x00
)

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

private fun b64Url(bytes: ByteArray): String {
    val std = bytes.toNSData().base64EncodedStringWithOptions(0u)
    return std.replace('+', '-').replace('/', '_').trimEnd('=')
}

private fun sha256(bytes: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(
                if (bytes.isEmpty()) null else input.addressOf(0),
                bytes.size.toUInt(),
                output.addressOf(0).reinterpret()
            )
        }
    }
    return digest
}

private fun randomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
    }
    return out
}

private fun buildClientDataJson(type: String, challenge: String): ByteArray =
    """{"type":"$type","challenge":"$challenge","origin":"$NATIVE_PASSKEY_ORIGIN"}""".encodeToByteArray()

private fun buildAuthenticatorData(rpId: String, signCount: Int): ByteArray {
    val rpHash = sha256(rpId.encodeToByteArray())
    val out = ByteArray(37)
    rpHash.copyInto(out, 0)
    out[32] = 0x05 // UP (0x01) + UV (0x04)
    out[33] = ((signCount ushr 24) and 0xFF).toByte()
    out[34] = ((signCount ushr 16) and 0xFF).toByte()
    out[35] = ((signCount ushr 8) and 0xFF).toByte()
    out[36] = (signCount and 0xFF).toByte()
    return out
}

private suspend fun promptFaceIdOrPasscode(reason: String) = suspendCancellableCoroutine<Unit> { cont ->
    val context = LAContext()
    context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, error ->
        if (!cont.isActive) return@evaluatePolicy
        if (success) {
            cont.resume(Unit)
        } else {
            val code = error?.code
            val cancelled = code == LAErrorUserCancel || code == LAErrorSystemCancel || code == LAErrorAppCancel
            if (cancelled) {
                cont.resumeWithException(PasskeyCancelledException())
            } else {
                cont.resumeWithException(
                    IllegalStateException(error?.localizedDescription ?: "Verifica biometrica non riuscita.")
                )
            }
        }
    }
}

private fun findPrivateKey(credentialId: String): SecKeyRef? = memScoped {
    val tagRef = CFBridgingRetain((KEY_TAG_PREFIX + credentialId).encodeToByteArray().toNSData())
    val query = CFDictionaryCreateMutable(null, 5, null, null)
    try {
        CFDictionaryAddValue(query, kSecClass, kSecClassKey)
        CFDictionaryAddValue(query, kSecAttrApplicationTag, tagRef)
        CFDictionaryAddValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(query, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        CFDictionaryAddValue(query, kSecReturnRef, kCFBooleanTrue)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status == errSecSuccess && result.value != null) {
            result.value!!.reinterpret()
        } else {
            null
        }
    } finally {
        if (query != null) CFRelease(query)
        if (tagRef != null) CFRelease(tagRef)
    }
}

actual fun isPasskeySupported(): Boolean {
    val context = LAContext()
    return context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
}

actual fun defaultPasskeyDeviceLabel(): String {
    val model = UIDevice.currentDevice.model.trim()
    return if (model.isNotEmpty()) model.take(45) else "iPhone / iPad"
}

actual suspend fun createPlatformPasskey(options: PasskeyRegisterOptionsDto): PasskeyCreationResult {
    promptFaceIdOrPasscode("Conferma per attivare la Passkey su questo dispositivo.")

    val credentialId = b64Url(randomBytes(24))
    val spkiBytes = memScoped {
        val tagRef = CFBridgingRetain((KEY_TAG_PREFIX + credentialId).encodeToByteArray().toNSData())
        val keySizeRef = CFBridgingRetain(NSNumber(int = 256))
        val privateAttrs = CFDictionaryCreateMutable(null, 2, null, null)
        val attrs = CFDictionaryCreateMutable(null, 3, null, null)
        val privateKey: SecKeyRef? = try {
            CFDictionaryAddValue(privateAttrs, kSecAttrIsPermanent, kCFBooleanTrue)
            CFDictionaryAddValue(privateAttrs, kSecAttrApplicationTag, tagRef)
            CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, keySizeRef)
            CFDictionaryAddValue(attrs, kSecPrivateKeyAttrs, privateAttrs)
            val err = alloc<CFErrorRefVar>()
            SecKeyCreateRandomKey(attrs, err.ptr)
        } finally {
            if (attrs != null) CFRelease(attrs)
            if (privateAttrs != null) CFRelease(privateAttrs)
            if (keySizeRef != null) CFRelease(keySizeRef)
            if (tagRef != null) CFRelease(tagRef)
        }
        if (privateKey == null) {
            throw IllegalStateException("Impossibile generare la chiave nel Portachiavi iOS.")
        }
        try {
            val publicKey = SecKeyCopyPublicKey(privateKey)
                ?: throw IllegalStateException("Impossibile leggere la chiave pubblica iOS.")
            try {
                val err = alloc<CFErrorRefVar>()
                val rawPubCf = SecKeyCopyExternalRepresentation(publicKey, err.ptr)
                    ?: throw IllegalStateException("Impossibile esportare la chiave pubblica iOS.")
                val rawPubData = CFBridgingRelease(rawPubCf) as NSData
                val rawPub = rawPubData.toByteArray()
                if (rawPub.size != 65 || rawPub[0] != 0x04.toByte()) {
                    throw IllegalStateException("Formato chiave P-256 non valido.")
                }
                P256_SPKI_PREFIX + rawPub
            } finally {
                CFRelease(publicKey)
            }
        } finally {
            CFRelease(privateKey)
        }
    }

    val ud = NSUserDefaults.standardUserDefaults
    val existing = (ud.stringArrayForKey(UD_CREDENTIAL_IDS) as? List<*>)
        ?.filterIsInstance<String>()
        ?.toMutableList()
        ?: mutableListOf()
    if (credentialId !in existing) existing.add(credentialId)
    ud.setObject(existing, forKey = UD_CREDENTIAL_IDS)
    ud.setObject(credentialId, forKey = UD_LAST_CREDENTIAL_ID)
    ud.setInteger(1, forKey = UD_SIGN_COUNT_PREFIX + credentialId)
    ud.synchronize()

    val clientData = buildClientDataJson("webauthn.create", options.challenge)
    val authData = buildAuthenticatorData(options.rpId, 1)

    return PasskeyCreationResult(
        credentialId = credentialId,
        clientDataJSON = b64Url(clientData),
        authenticatorData = b64Url(authData),
        publicKeySpki = b64Url(spkiBytes),
        algorithm = -7
    )
}

actual suspend fun signInWithPlatformPasskey(options: PasskeyLoginOptionsDto): PasskeyAssertionResult {
    val ud = NSUserDefaults.standardUserDefaults
    val storedIds = (ud.stringArrayForKey(UD_CREDENTIAL_IDS) as? List<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
    val lastId = ud.stringForKey(UD_LAST_CREDENTIAL_ID)

    fun hasKey(id: String): Boolean {
        val ref = findPrivateKey(id) ?: return false
        CFRelease(ref)
        return true
    }

    val chosenId = when {
        options.allowCredentialIds.isNotEmpty() ->
            options.allowCredentialIds.firstOrNull { it in storedIds && hasKey(it) }
                ?: options.allowCredentialIds.firstOrNull { hasKey(it) }
        lastId != null && hasKey(lastId) -> lastId
        else -> storedIds.firstOrNull { hasKey(it) }
    } ?: throw IllegalStateException("Nessuna Passkey attiva su questo dispositivo. Accedi con password e attivala dal Profilo.")

    promptFaceIdOrPasscode("Conferma per accedere ad AILA con Passkey.")

    val nextCount = ud.integerForKey(UD_SIGN_COUNT_PREFIX + chosenId).toInt().coerceAtLeast(1) + 1
    ud.setObject(chosenId, forKey = UD_LAST_CREDENTIAL_ID)
    ud.setInteger(nextCount.toLong(), forKey = UD_SIGN_COUNT_PREFIX + chosenId)
    ud.synchronize()

    val clientData = buildClientDataJson("webauthn.get", options.challenge)
    val authData = buildAuthenticatorData(options.rpId, nextCount)
    val payloadToSign = authData + sha256(clientData)

    val signatureBytes = memScoped {
        val privateKey = findPrivateKey(chosenId)
            ?: throw IllegalStateException("Chiave biometrica non trovata. Riattivala dal Profilo.")
        try {
            val dataRef = CFBridgingRetain(payloadToSign.toNSData()) as CFDataRef
            val err = alloc<CFErrorRefVar>()
            val sigCf = SecKeyCreateSignature(
                privateKey,
                kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                dataRef,
                err.ptr
            )
            CFRelease(dataRef)
            if (sigCf == null) {
                throw IllegalStateException("Firma biometrica non riuscita.")
            }
            (CFBridgingRelease(sigCf) as NSData).toByteArray()
        } finally {
            CFRelease(privateKey)
        }
    }

    return PasskeyAssertionResult(
        credentialId = chosenId,
        clientDataJSON = b64Url(clientData),
        authenticatorData = b64Url(authData),
        signature = b64Url(signatureBytes)
    )
}
