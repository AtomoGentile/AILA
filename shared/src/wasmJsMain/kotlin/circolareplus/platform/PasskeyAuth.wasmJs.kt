package circolareplus.platform

import circolareplus.data.remote.apiJson
import circolareplus.data.remote.dto.PasskeyLoginOptionsDto
import circolareplus.data.remote.dto.PasskeyRegisterOptionsDto
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.js.JsString
import kotlin.js.Promise

private fun jsIsPasskeySupported(): Boolean =
    js("typeof window !== 'undefined' && typeof window.PublicKeyCredential !== 'undefined' && !!navigator.credentials && (window.isSecureContext === true)")

private fun jsCreatePasskey(optionsJson: String): Promise<JsString> = js(
    """(async () => {
        const b64ToBytes = (s) => {
            const norm = s.replace(/-/g, '+').replace(/_/g, '/');
            const pad = norm.length % 4 ? '='.repeat(4 - (norm.length % 4)) : '';
            const bin = atob(norm + pad);
            const out = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
            return out;
        };
        const bytesToB64 = (buf) => {
            const bytes = new Uint8Array(buf);
            let bin = '';
            for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
            return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        };
        try {
            const opts = JSON.parse(optionsJson);
            const cred = await navigator.credentials.create({
                publicKey: {
                    challenge: b64ToBytes(opts.challenge),
                    rp: { id: opts.rpId, name: opts.rpName || 'AILA' },
                    user: {
                        id: b64ToBytes(opts.userId),
                        name: opts.username,
                        displayName: opts.displayName || opts.username
                    },
                    pubKeyCredParams: [
                        { type: 'public-key', alg: -7 },
                        { type: 'public-key', alg: -257 }
                    ],
                    authenticatorSelection: {
                        residentKey: 'required',
                        requireResidentKey: true,
                        userVerification: 'required'
                    },
                    excludeCredentials: (opts.excludeCredentialIds || []).map((id) => ({
                        type: 'public-key',
                        id: b64ToBytes(id)
                    })),
                    timeout: 60000,
                    attestation: 'none'
                }
            });
            if (!cred || !cred.response) {
                return JSON.stringify({ ok: false, cancelled: true, error: 'Operazione annullata' });
            }
            const resp = cred.response;
            if (typeof resp.getPublicKey !== 'function' || typeof resp.getAuthenticatorData !== 'function') {
                return JSON.stringify({ ok: false, cancelled: false, error: 'Browser non aggiornato per le Passkey' });
            }
            const pubKey = resp.getPublicKey();
            const authData = resp.getAuthenticatorData();
            const alg = typeof resp.getPublicKeyAlgorithm === 'function' ? resp.getPublicKeyAlgorithm() : -7;
            if (!pubKey || !authData) {
                return JSON.stringify({ ok: false, cancelled: false, error: 'Impossibile leggere la chiave Passkey' });
            }
            return JSON.stringify({
                ok: true,
                credentialId: cred.id,
                clientDataJSON: bytesToB64(resp.clientDataJSON),
                authenticatorData: bytesToB64(authData),
                publicKeySpki: bytesToB64(pubKey),
                algorithm: alg
            });
        } catch (e) {
            const name = (e && e.name) ? String(e.name) : '';
            const cancelled = name === 'NotAllowedError' || name === 'AbortError';
            const msg = name === 'InvalidStateError'
                ? 'Questo dispositivo ha già una Passkey attiva per questo account.'
                : ((e && e.message) ? String(e.message) : 'Impossibile creare la Passkey');
            return JSON.stringify({ ok: false, cancelled, error: msg });
        }
    })()"""
)

private fun jsGetPasskey(optionsJson: String): Promise<JsString> = js(
    """(async () => {
        const b64ToBytes = (s) => {
            const norm = s.replace(/-/g, '+').replace(/_/g, '/');
            const pad = norm.length % 4 ? '='.repeat(4 - (norm.length % 4)) : '';
            const bin = atob(norm + pad);
            const out = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
            return out;
        };
        const bytesToB64 = (buf) => {
            const bytes = new Uint8Array(buf);
            let bin = '';
            for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
            return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        };
        try {
            const opts = JSON.parse(optionsJson);
            const publicKey = {
                challenge: b64ToBytes(opts.challenge),
                rpId: opts.rpId,
                userVerification: 'required',
                timeout: 60000
            };
            if (opts.allowCredentialIds && opts.allowCredentialIds.length > 0) {
                publicKey.allowCredentials = opts.allowCredentialIds.map((id) => ({
                    type: 'public-key',
                    id: b64ToBytes(id)
                }));
            }
            const cred = await navigator.credentials.get({ publicKey });
            if (!cred || !cred.response) {
                return JSON.stringify({ ok: false, cancelled: true, error: 'Operazione annullata' });
            }
            const resp = cred.response;
            return JSON.stringify({
                ok: true,
                credentialId: cred.id,
                clientDataJSON: bytesToB64(resp.clientDataJSON),
                authenticatorData: bytesToB64(resp.authenticatorData),
                signature: bytesToB64(resp.signature)
            });
        } catch (e) {
            const name = (e && e.name) ? String(e.name) : '';
            const cancelled = name === 'NotAllowedError' || name === 'AbortError';
            const msg = (e && e.message) ? String(e.message) : 'Accesso con Passkey non riuscito';
            return JSON.stringify({ ok: false, cancelled, error: msg });
        }
    })()"""
)

@Serializable
private data class JsCreateResult(
    val ok: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null,
    val credentialId: String = "",
    val clientDataJSON: String = "",
    val authenticatorData: String = "",
    val publicKeySpki: String = "",
    val algorithm: Int = -7
)

@Serializable
private data class JsGetResult(
    val ok: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null,
    val credentialId: String = "",
    val clientDataJSON: String = "",
    val authenticatorData: String = "",
    val signature: String = ""
)

actual fun isPasskeySupported(): Boolean = jsIsPasskeySupported()

actual fun defaultPasskeyDeviceLabel(): String = when {
    isIos() -> "iPhone / iPad"
    isAndroid() -> "Android"
    else -> "Computer"
}

actual suspend fun createPlatformPasskey(options: PasskeyRegisterOptionsDto): PasskeyCreationResult {
    val raw = jsCreatePasskey(apiJson.encodeToString(options)).await<JsString>().toString()
    val parsed = apiJson.decodeFromString<JsCreateResult>(raw)
    if (!parsed.ok) {
        if (parsed.cancelled) throw PasskeyCancelledException()
        throw IllegalStateException(parsed.error ?: "Impossibile creare la Passkey")
    }
    return PasskeyCreationResult(
        credentialId = parsed.credentialId,
        clientDataJSON = parsed.clientDataJSON,
        authenticatorData = parsed.authenticatorData,
        publicKeySpki = parsed.publicKeySpki,
        algorithm = parsed.algorithm
    )
}

actual suspend fun signInWithPlatformPasskey(options: PasskeyLoginOptionsDto): PasskeyAssertionResult {
    val raw = jsGetPasskey(apiJson.encodeToString(options)).await<JsString>().toString()
    val parsed = apiJson.decodeFromString<JsGetResult>(raw)
    if (!parsed.ok) {
        if (parsed.cancelled) throw PasskeyCancelledException()
        throw IllegalStateException(parsed.error ?: "Accesso con Passkey non riuscito")
    }
    return PasskeyAssertionResult(
        credentialId = parsed.credentialId,
        clientDataJSON = parsed.clientDataJSON,
        authenticatorData = parsed.authenticatorData,
        signature = parsed.signature
    )
}
