package circolareplus.platform

import androidx.compose.runtime.Composable

/** Device information stays on this device. No identifiers are sent to a server. */
interface NothingGlyphConnection {
    val ownerLabel: String?
    val ready: Boolean
    val status: String
    val notificationSettingsAvailable: Boolean get() = false
    fun openNotificationSettings(group: circolareplus.design.NothingNotificationGroup): Boolean = false
    fun pulse(zone: Int = 0, periodMillis: Int = 1200): Boolean
    fun stop()
}

@Composable
expect fun rememberNothingGlyph(connectLights: Boolean): NothingGlyphConnection

internal object PreviewNothingGlyph : NothingGlyphConnection {
    override val ownerLabel: String? = null
    override val ready = false
    override val status = "Anteprima sullo schermo"
    override fun pulse(zone: Int, periodMillis: Int) = false
    override fun stop() = Unit
}

val LocalNothingGlyph = androidx.compose.runtime.staticCompositionLocalOf<NothingGlyphConnection> { PreviewNothingGlyph }
