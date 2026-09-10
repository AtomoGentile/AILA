package circolareplus.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine

actual class PushTokenProvider actual constructor() {
    actual suspend fun getToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(token))
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
        }
    } catch (e: Exception) {
        // FirebaseApp non inizializzata (manca ancora google-services.json / il plugin
        // com.google.gms.google-services non è applicato): degrada senza notifiche push
        // invece di far crashare l'app. Vedi FIREBASE_SETUP.md.
        null
    }
}

actual fun currentPushPlatform(): String = "android"
