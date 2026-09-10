package com.circolareplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import circolareplus.data.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Prima non esisteva NESSUNA classe che ricevesse davvero i push in arrivo: FCM mostrava una
 * notifica di sistema automaticamente SOLO quando l'app era in background/chiusa (comportamento
 * di default per i messaggi con blocco "notification"), ma a schermo aperto il messaggio veniva
 * silenziosamente scartato — nessuna notifica, nessuna traccia da nessuna parte. Questo servizio
 * intercetta ogni messaggio (foreground incluso), lo salva nello storico locale (mai sul server,
 * si autoelimina dopo qualche giorno — vedi [circolareplus.data.local.LocalSettingsManager]) e
 * mostra sempre la notifica di sistema, anche ad app aperta.
 */
class CircolareMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "circolare_plus_default"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "AILA"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        // Storico locale: letto dalla campanella nell'app (si autoelimina dopo qualche giorno).
        AppContainer.settings.addNotification(title, body)

        showSystemNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // La registrazione vera e propria del token avviene già al login/avvio app
        // (MainAppShell chiama PushTokenProvider.getToken() + FcmRepository.registerToken());
        // qui non serve altro, un token rinnovato verrà ripreso al prossimo avvio.
    }

    private fun showSystemNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AILA",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Circolari, bacheca, sondaggi e mappa posti"
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        manager.notify(currentTimeMillisAsNotificationId(), notification)
    }

    private fun currentTimeMillisAsNotificationId(): Int =
        (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
}
