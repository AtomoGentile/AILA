package circolareplus.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import circolareplus.design.NothingNotificationGroup

/** System-owned channels: Nothing OS owns sounds, Glyph patterns, DND and lock-screen delivery. */
object NothingNotificationChannels {
    private const val LEGACY = "aila_notifications"
    fun channelId(group: NothingNotificationGroup) = "aila_glyph_${group.key}_v1"
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val old = manager.getNotificationChannel(LEGACY)
        NothingNotificationGroup.entries.forEach { group ->
            val id = channelId(group)
            if(manager.getNotificationChannel(id) == null) {
                val channel = NotificationChannel(id, "AILA · ${group.label}", old?.importance ?: NotificationManager.IMPORTANCE_HIGH)
                channel.description = "${group.label}: suono, vibrazione e pattern Glyph gestiti da Nothing OS"
                if(old != null) {
                    channel.setSound(old.sound, old.audioAttributes)
                    channel.enableVibration(old.shouldVibrate())
                    old.vibrationPattern?.let { channel.vibrationPattern = it }
                    channel.lockscreenVisibility = old.lockscreenVisibility
                    channel.setShowBadge(old.canShowBadge())
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
    fun route(context: Context, category: String, enabled: Boolean): String {
        if (!enabled || !isNothingDevice()) return LEGACY
        ensure(context)
        // Opting into split channels must not silently bypass a previously blocked AILA channel.
        val manager = context.getSystemService(NotificationManager::class.java)
        if(manager?.getNotificationChannel(LEGACY)?.importance == NotificationManager.IMPORTANCE_NONE) return LEGACY
        return channelId(NothingNotificationGroup.fromCategory(category))
    }
    fun open(context: Context, group: NothingNotificationGroup): Boolean {
        ensure(context)
        return runCatching {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(group))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
