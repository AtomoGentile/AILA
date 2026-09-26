package com.circolareplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import circolareplus.data.AppContainer
import circolareplus.work.CircularsSyncWorker
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
        // Importanza HIGH: solo cosi' Android mostra il banner a comparsa (heads-up) anche ad app
        // aperta. Con DEFAULT la notifica finiva soltanto come icona nella barra di stato e
        // sembrava "non arrivata". L'importanza di un canale gia' creato non si puo' cambiare da
        // codice, quindi serve un ID nuovo; il vecchio si elimina qui sotto.
        // Lo stesso ID e' dichiarato nel manifest (default_notification_channel_id).
        private const val CHANNEL_ID = "aila_notifications"
        private const val OLD_CHANNEL_ID = "circolare_plus_default"

        /**
         * Crea il canale delle notifiche (idempotente). Chiamato da
         * [CircolarePlusApplication.onCreate]: cosi' il canale compare nelle impostazioni di
         * sistema fin dal primo avvio, e non solo dopo l'arrivo del primo push (prima l'utente non
         * poteva regolare suono e vibrazione prima di riceverne uno). Il servizio lo richiama
         * comunque prima di notificare, per sicurezza.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AILA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Circolari, bacheca, sondaggi e mappa posti"
            }
            manager.createNotificationChannel(channel)
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Il backend manda ad Android messaggi SOLO data (titolo e testo dentro "data"), cosi'
        // questo metodo gira sempre, anche ad app chiusa; il fallback sul blocco "notification"
        // copre un eventuale invio vecchio stile dalla console Firebase.
        val title = message.data["title"] ?: message.notification?.title ?: "AILA"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val category = circolareplus.domain.model.NotificationCategoryMapper.categoryFrom(message.data)

        // Interruttori dell'utente, deduplica per messageId e storico locale (la campanella
        // dell'app, che si autoelimina dopo qualche giorno) stanno tutti in onPushReceived: la
        // notifica di sistema si mostra solo se ritorna true.
        val show = AppContainer.settings.onPushReceived(message.messageId, title, body, category)
        // A schermata aperta la lista deve aggiornarsi da sola, non solo mostrare la notifica.
        circolareplus.push.DataRefreshEvents.request()
        if (show) showSystemNotification(title, body, category)
        // Circolare nuova: un giro di classificazione subito (solo cloud, vedi
        // BackgroundCircularsSync), senza aspettare il periodico da 15 minuti.
        if (category == "circulars") CircularsSyncWorker.runOnce(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // La registrazione vera e propria del token avviene già al login/avvio app
        // (MainAppShell chiama PushTokenProvider.getToken() + FcmRepository.registerToken());
        // qui non serve altro, un token rinnovato verrà ripreso al prossimo avvio.
    }

    private fun showSystemNotification(title: String, body: String, category: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        ensureNotificationChannel(this)

        // Intent esplicito verso MainActivity (non più getLaunchIntentForPackage): serve a
        // poter allegare la categoria della notifica come extra, così MainActivity può
        // impostare PendingDeepLink e far navigare l'utente alla schermata giusta, esattamente
        // come già fa il tocco sulla campanella in-app.
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            // Con un'azione che corrisponde al filtro di MainActivity: le versioni recenti di
            // Android possono rifiutare gli intent espliciti che non combaciano con i filtri.
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (category.isNotBlank()) {
                putExtra("notification_category", category)
            }
        }
        // Un id per notifica, usato sia per la notifica sia come requestCode del PendingIntent:
        // con requestCode fisso tutte le notifiche condividevano lo stesso PendingIntent e
        // FLAG_UPDATE_CURRENT ne sovrascriveva l'extra, quindi toccando una notifica vecchia si
        // apriva la sezione dell'ultima arrivata.
        val notificationId = currentTimeMillisAsNotificationId()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aila)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun currentTimeMillisAsNotificationId(): Int =
        (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
}
