package circolareplus.platform

import androidx.compose.runtime.Composable

/** Device information stays on this device. No identifiers are sent to a server. */
interface NothingGlyphConnection {
    val ownerLabel: String?
    val ready: Boolean
    val status: String
    fun pulse(): Boolean
    fun stop()
}

@Composable
expect fun rememberNothingGlyph(): NothingGlyphConnection

internal object PreviewNothingGlyph : NothingGlyphConnection {
    override val ownerLabel: String? = null
    override val ready = false
    override val status = "Anteprima sullo schermo"
    override fun pulse() = false
    override fun stop() = Unit
}
