package com.circolareplus

import android.app.Application
import circolareplus.ai.PdfBoxInit
import circolareplus.platform.AndroidAppContext
import circolareplus.work.AnalysisForegroundService
import circolareplus.work.CircularsSyncWorker

/**
 * `Application` del processo, non solo `MainActivity`.
 *
 * Prima [AndroidAppContext] e [PdfBoxInit] venivano inizializzati solo in `MainActivity.onCreate`:
 * andava bene finché tutto il lavoro (download modello, classificazione AI) girava dentro
 * coroutine legate alla UI, perché a quel punto un'Activity era per forza già partita. Con
 * WorkManager (vedi `circolareplus.work.*`) il sistema può far ripartire il processo dell'app
 * solo per eseguire un Worker in background — schermo spento, app mai aperta in questa sessione
 * del telefono — senza mai passare da `MainActivity`. Senza questa classe, in quel caso
 * `AndroidAppContext.require()` lancerebbe l'errore "init() non è stata chiamata" e il Worker
 * fallirebbe sempre.
 *
 * `Application.onCreate()` gira invece sempre, qualunque sia la ragione per cui il processo è
 * partito: è il punto giusto per queste due inizializzazioni una tantum.
 */
class CircolarePlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.init(this)
        PdfBoxInit.init(this)
        // Punto 7: prima l'unico modo di classificare le circolari arretrate era un ciclo dentro
        // MainAppShell, che si fermava appena l'app andava in background. Questo copre
        // l'intervallo in mezzo, con un giro ogni 15 minuti quando c'e' rete.
        CircularsSyncWorker.schedulePeriodic(this)
        // Il canale delle notifiche esiste fin dall'avvio, cosi' e' regolabile dalle impostazioni
        // di sistema anche prima del primo push.
        CircolareMessagingService.ensureNotificationChannel(this)
        // Notifica con tasto Stop mentre il modello sul telefono analizza una circolare: tiene
        // viva l'analisi anche con l'app in background.
        AnalysisForegroundService.observe(this)
    }
}
