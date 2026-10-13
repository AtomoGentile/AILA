package circolareplus.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import circolareplus.update.AppUpdateManager

/**
 * Riceve l'esito dell'installazione avviata da [installDownloadedApk] (PackageInstaller).
 *
 * Android risponde in due tempi: prima chiede la conferma all'utente (STATUS_PENDING_USER_ACTION,
 * con dentro la schermata da aprire), poi dice com'e' andata. Con il vecchio ACTION_VIEW il
 * sistema mostrava solo un generico "impossibile installare" e l'app non sapeva nulla: qui invece
 * arriva il motivo (firma diversa, versione piu' vecchia, spazio, blocco di sistema...) e si
 * mostra nella finestra di aggiornamento.
 *
 * Dichiarato in androidApp/src/androidMain/AndroidManifest.xml.
 */
class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            // Installata: il processo viene sostituito, non c'e' niente da fare.
            PackageInstaller.STATUS_SUCCESS -> {}
            // L'utente ha toccato "Annulla" nella schermata di sistema: non e' un errore.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {}
            else -> AppUpdateManager.onInstallFailed(
                describeInstallFailure(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            )
        }
    }
}

/** Il motivo del rifiuto in italiano, con il messaggio tecnico di Android in coda per chi deve cercarlo. */
internal fun describeInstallFailure(status: Int, systemMessage: String?): String {
    val reason = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Android non può aggiornare AILA: quella installata è firmata con una chiave diversa " +
                "o è più recente di questa versione. Disinstalla AILA e installa questa versione " +
                "(i dati salvati sul telefono si perdono, l'account resta sul server)."
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "L'installazione è stata bloccata dal sistema (per esempio dal Blocco automatico di " +
                "Samsung o da Play Protect). Consenti l'installazione e riprova."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Spazio insufficiente sul telefono."
        PackageInstaller.STATUS_FAILURE_INVALID -> "Il file scaricato non è un aggiornamento valido."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Questa versione non è compatibile con il telefono."
        else -> "Installazione non riuscita."
    }
    return if (systemMessage.isNullOrBlank()) reason else "$reason ($systemMessage)"
}
