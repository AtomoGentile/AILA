package circolareplus.platform

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.delay

/** Foreground only. Each pulse is bounded; pause and disposal release the hardware session. */
@Composable
actual fun rememberNothingGlyph(): NothingGlyphConnection {
    val context = LocalContext.current
    val owner = Build.MANUFACTURER.equals("Nothing", true) || Build.BRAND.equals("Nothing", true)
    if (!owner) return PreviewNothingGlyph
    val connection = remember(context) { AndroidNothingGlyph(context) }
    DisposableEffect(connection) {
        connection.connect()
        val application = context.applicationContext as Application
        var host: Context? = context
        while (host is ContextWrapper && host !is Activity) host = host.baseContext
        val hostActivity = host as? Activity
        val observer = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) { if (activity == hostActivity) connection.setForeground(false) }
            override fun onActivityDestroyed(activity: Activity) { if (activity == hostActivity) connection.setForeground(false) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) { if (activity == hostActivity) connection.setForeground(true) }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        application.registerActivityLifecycleCallbacks(observer)
        onDispose {
            application.unregisterActivityLifecycleCallbacks(observer)
            connection.dispose()
        }
    }
    LaunchedEffect(connection) { delay(3000); connection.connectionTimeout() }
    return connection
}

private class AndroidNothingGlyph(private val context: Context) : NothingGlyphConnection {
    override val ownerLabel = Build.MODEL
    override var ready by mutableStateOf(false)
        private set
    override var status by mutableStateOf("Collegamento al Glyph…")
        private set
    private var manager: GlyphManager? = null
    private var session = false
    private var disposed = false
    private var foreground = true
    private var targetDevice: String? = null
    override val notificationSettingsAvailable = true
    override val barSegments get() = when(targetDevice) { Glyph.DEVICE_25111 -> 6; Glyph.DEVICE_25131 -> 4; else -> 0 }
    override fun openNotificationSettings(group: circolareplus.design.NothingNotificationGroup) = NothingNotificationChannels.open(context, group)
    override fun testNotification(group: circolareplus.design.NothingNotificationGroup) = NothingNotificationChannels.test(context, group)
    override val supportsProgress get() = barSegments > 0 || targetDevice in setOf(Glyph.DEVICE_20111, Glyph.DEVICE_22111, Glyph.DEVICE_23111, Glyph.DEVICE_23113, Glyph.DEVICE_24111)
    fun setForeground(value: Boolean) { foreground = value; if (!value) stop() }

    fun connect() {
        // Older Nothing OS versions require a vendor API key; do not pretend that a test key works.
        if (Build.VERSION.SDK_INT < 36) { status = "Le luci richiedono Nothing OS con Android 16 o successivo"; return }
        val device = when {
            Common.is20111() -> Glyph.DEVICE_20111
            Common.is22111() -> Glyph.DEVICE_22111
            Common.is23111() -> Glyph.DEVICE_23111
            Common.is23113() -> Glyph.DEVICE_23113
            Common.is24111() -> Glyph.DEVICE_24111
            Common.is25111() -> Glyph.DEVICE_25111
            Common.is25131() -> Glyph.DEVICE_25131
            else -> null
        }
        if (device == null) { status = "Anteprima disponibile; luci non supportate su questo modello"; return }
        targetDevice = device
        runCatching {
            val gm = GlyphManager.getInstance(context.applicationContext)
            manager = gm
            gm.init(object : GlyphManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    if (disposed) return
                    ready = runCatching { gm.register(device) }.getOrDefault(false)
                    status = if (ready) "Luci posteriori collegate" else "Il sistema non ha autorizzato il Glyph"
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    session = false; ready = false
                    status = "Glyph disconnesso"
                }
            })
        }.onFailure { status = "Glyph non disponibile"; ready = false }
    }

    fun connectionTimeout() { if (!ready && status == "Collegamento al Glyph…") status = "Servizio Glyph non disponibile" }

    override fun pulse(zone: Int, periodMillis: Int): Boolean {
        val gm = manager ?: return false
        if (!ready || disposed || !foreground) return false
        return runCatching {
            if (!session) { gm.openSession(); session = true }
            val builder = gm.glyphFrameBuilder
            val supportedZone = if (targetDevice == Glyph.DEVICE_25111 || targetDevice == Glyph.DEVICE_25131) 1 else zone
            when (supportedZone) {
                1 -> builder.buildChannelA()
                2 -> builder.buildChannelB()
                3 -> builder.buildChannelC()
                else -> builder.buildChannelA().buildChannelB().buildChannelC()
            }
            val frame = builder.buildPeriod(periodMillis.coerceIn(800, 2400)).buildCycles(1).buildInterval(0).build()
            gm.animate(frame)
            true
        }.getOrElse { stop(); ready = false; status = "Il sistema non ha avviato le luci"; false }
    }

    override fun showProgress(fraction: Float): Boolean {
        val gm = manager ?: return false
        if (!ready || disposed || !foreground || !supportsProgress) return false
        return runCatching {
            if (!session) { gm.openSession(); session = true }
            val builder = gm.glyphFrameBuilder
            if (barSegments > 0) {
                val channels = circolareplus.design.nothingBarChannels(fraction, barSegments)
                if(channels.isEmpty()) gm.turnOff() else {
                    // Official 4a A1..A6 indices run from top (0) to bottom (5).
                    channels.forEach { builder.buildChannel(it) }
                    gm.toggle(builder.build())
                }
                return@runCatching true
            }
            if (targetDevice == Glyph.DEVICE_20111) builder.buildChannelD() else builder.buildChannelC()
            gm.displayProgress(builder.build(), (fraction.coerceIn(0f, 1f) * 100).toInt())
            true
        }.getOrElse { stop(); false }
    }

    override fun stop() {
        manager?.let { gm ->
            if (session) {
                runCatching { gm.turnOff() }
                runCatching { gm.closeSession() }
            }
        }
        session = false
    }

    fun dispose() { disposed = true; stop(); manager?.let { runCatching { it.unInit() } }; manager = null; ready = false }
}
