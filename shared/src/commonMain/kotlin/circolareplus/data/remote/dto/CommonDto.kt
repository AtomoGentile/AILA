package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

/** Risposta generica {"success": true, ...} usata da molti endpoint di scrittura del Worker. */
@Serializable
data class SuccessDto(
    val success: Boolean = false,
    val id: String? = null,
    val message: String? = null
)
