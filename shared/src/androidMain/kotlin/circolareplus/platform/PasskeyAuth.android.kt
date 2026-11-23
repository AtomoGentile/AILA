package circolareplus.platform

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import circolareplus.data.remote.dto.PasskeyLoginOptionsDto
import circolareplus.data.remote.dto.PasskeyRegisterOptionsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val NATIVE_PASSKEY_ORIGIN = "https://aila-scuola.pages.dev"
private const val PREFS_NAME = "aila_native_passkeys"
private const val KEY_CREDENTIAL_IDS = "credential_ids"
private const val KEY_LAST_CREDENTIAL_ID = "last_credential_id"
private const val KEY_SIGN_COUNT_PREFIX = "sign_count_"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS_PREFIX = "aila_passkey_"

private val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

private fun b64Url(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun buildClientDataJson(type: String, challenge: String): ByteArray {
    val json = """{"type":"$type","challenge":"$challenge","origin":"$NATIVE_PASSKEY_ORIGIN"}"""
    return json.toByteArray(Charsets.UTF_8)
}

private fun buildAuthenticatorData(rpId: String, signCount: Int): ByteArray {
    val rpHash = sha256(rpId.toByteArray(Charsets.UTF_8))
    val out = ByteBuffer.allocate(37)
    out.put(rpHash)
    out.put(0x05.toByte()) // UP (0x01) + UV (0x04)
    out.putInt(signCount)
    return out.array()
}

private suspend fun promptBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String
) = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine<Unit> { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!cont.isActive) return
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (cancelled) {
                    cont.resumeWithException(PasskeyCancelledException())
                } else {
                    cont.resumeWithException(IllegalStateException(errString.toString().ifBlank { "Verifica biometrica non riuscita." }))
                }
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

actual fun isPasskeySupported(): Boolean {
    val ctx = AndroidAppContext.getOrNull() ?: return false
    val status = BiometricManager.from(ctx).canAuthenticate(ALLOWED_AUTHENTICATORS)
    return status == BiometricManager.BIOMETRIC_SUCCESS
}

actual fun defaultPasskeyDeviceLabel(): String {
    val model = Build.MODEL?.trim().orEmpty()
    return if (model.isNotEmpty()) "Android ($model)".take(45) else "Android"
}

actual suspend fun createPlatformPasskey(options: PasskeyRegisterOptionsDto): PasskeyCreationResult {
    val activity = AndroidAppContext.currentActivity()
        ?: throw IllegalStateException("Schermata non pronta per la verifica biometrica.")
    promptBiometric(
        activity = activity,
        title = "Attiva Passkey",
        subtitle = "Conferma per salvare la Passkey su questo dispositivo"
    )

    val credentialBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
    val credentialId = b64Url(credentialBytes)
    val alias = KEY_ALIAS_PREFIX + credentialId

    val keyPair = withContext(Dispatchers.Default) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    val spki = keyPair.public.encoded
    val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getStringSet(KEY_CREDENTIAL_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    existing.add(credentialId)
    prefs.edit()
        .putStringSet(KEY_CREDENTIAL_IDS, existing)
        .putString(KEY_LAST_CREDENTIAL_ID, credentialId)
        .putInt(KEY_SIGN_COUNT_PREFIX + credentialId, 1)
        .apply()

    val clientData = buildClientDataJson("webauthn.create", options.challenge)
    val authData = buildAuthenticatorData(options.rpId, 1)

    return PasskeyCreationResult(
        credentialId = credentialId,
        clientDataJSON = b64Url(clientData),
        authenticatorData = b64Url(authData),
        publicKeySpki = b64Url(spki),
        algorithm = -7
    )
}

actual suspend fun signInWithPlatformPasskey(options: PasskeyLoginOptionsDto): PasskeyAssertionResult {
    val activity = AndroidAppContext.currentActivity()
        ?: throw IllegalStateException("Schermata non pronta per la verifica biometrica.")
    val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val storedIds = prefs.getStringSet(KEY_CREDENTIAL_IDS, emptySet()).orEmpty()
    val lastId = prefs.getString(KEY_LAST_CREDENTIAL_ID, null)

    val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    fun hasKey(id: String): Boolean = ks.containsAlias(KEY_ALIAS_PREFIX + id)

    val chosenId = when {
        options.allowCredentialIds.isNotEmpty() ->
            options.allowCredentialIds.firstOrNull { it in storedIds && hasKey(it) }
                ?: options.allowCredentialIds.firstOrNull { hasKey(it) }
        lastId != null && hasKey(lastId) -> lastId
        else -> storedIds.firstOrNull { hasKey(it) }
    } ?: throw IllegalStateException("Nessuna Passkey attiva su questo telefono. Accedi con password e attivala dal Profilo.")

    promptBiometric(
        activity = activity,
        title = "Accedi con Passkey",
        subtitle = "Conferma per accedere ad AILA"
    )

    val nextCount = prefs.getInt(KEY_SIGN_COUNT_PREFIX + chosenId, 1) + 1
    prefs.edit()
        .putString(KEY_LAST_CREDENTIAL_ID, chosenId)
        .putInt(KEY_SIGN_COUNT_PREFIX + chosenId, nextCount)
        .apply()

    val clientData = buildClientDataJson("webauthn.get", options.challenge)
    val authData = buildAuthenticatorData(options.rpId, nextCount)

    val signatureBytes = withContext(Dispatchers.Default) {
        val privateKey = ks.getKey(KEY_ALIAS_PREFIX + chosenId, null) as? PrivateKey
            ?: throw IllegalStateException("Chiave biometrica non trovata. Riattivala dal Profilo.")
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(authData)
        sig.update(sha256(clientData))
        sig.sign()
    }

    return PasskeyAssertionResult(
        credentialId = chosenId,
        clientDataJSON = b64Url(clientData),
        authenticatorData = b64Url(authData),
        signature = b64Url(signatureBytes)
    )
}
