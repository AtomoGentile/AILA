package circolareplus.platform

import kotlinx.serialization.Serializable

@Serializable
data class SignatureExpirationInfo(
    val expiresAtIso: String,
    val isEstimated: Boolean
)

/**
 * Legge o stima la data di scadenza della firma iOS SideStore/Apple ID.
 * Ritorna null se la piattaforma non e' iOS.
 */
expect fun getIosSignatureExpirationInfo(firstLaunchMillis: Long): SignatureExpirationInfo?
