package circolareplus.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import circolareplus.data.AppContainer
import circolareplus.platform.appVersionName
import circolareplus.platform.canInstallPackages
import circolareplus.platform.clearDownloadedUpdate
import circolareplus.platform.currentTimeMillis
import circolareplus.platform.downloadUpdateApk
import circolareplus.platform.installDownloadedApk
import circolareplus.platform.isInAppUpdateSupported
import circolareplus.platform.isUpdateApkDownloaded
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** In che punto e' l'aggiornamento: lo legge la riga delle Impostazioni e la finestra di dialogo. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val downloaded: Long, val total: Long) : UpdateState
    data class ReadyToInstall(val update: AppUpdate) : UpdateState
    /** Android chiede di consentire ad AILA di installare app: l'utente e' nelle impostazioni di sistema. */
    data class NeedsPermission(val update: AppUpdate) : UpdateState
    data class Failed(val message: String, val update: AppUpdate?) : UpdateState
}

/**
 * Aggiornamento dell'app dalle release di GitHub (solo Android, vedi
 * circolareplus.platform.isInAppUpdateSupported).
 *
 * Quando si pubblica una release (tag `vX.Y.Z`) la CI allega `AILA.apk`; l'app, all'avvio e al
 * ritorno in primo piano (al massimo ogni [AUTO_CHECK_INTERVAL_MS]) legge `releases/latest`, e se la
 * versione e' piu' alta di quella installata propone di scaricarla e installarla. Lo stato e' di
 * Compose, global come AppTheme: lo scaricamento continua anche se si chiude la finestra.
 */
object AppUpdateManager {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/AtomoGentile/AILA/releases/latest"
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    /** La finestra "Aggiornamento disponibile" e' aperta? */
    var dialogVisible: Boolean by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    // Client proprio e leggero: la risposta e' un piccolo JSON, non serve ApiClient (token,
    // retry verso il backend) e l'API di GitHub e' pubblica.
    private val http by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    private val busy: Boolean
        get() = state is UpdateState.Checking || state is UpdateState.Downloading

    /** Controllo automatico (avvio e ritorno in primo piano): rispetta l'intervallo e non disturba. */
    fun checkAutomatically() {
        if (!isInAppUpdateSupported() || busy) return
        val last = AppContainer.settings.updateLastCheckMillis
        if (currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    /** Controllo chiesto dall'utente dalle Impostazioni: ignora l'intervallo e mostra sempre l'esito. */
    fun checkNow() {
        if (!isInAppUpdateSupported() || busy) return
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        val previous = state
        state = UpdateState.Checking
        job = scope.launch {
            try {
                val update = fetchLatest()
                AppContainer.settings.updateLastCheckMillis = currentTimeMillis()
                if (update == null || !AppVersion.isNewer(update.version, appVersionName())) {
                    // Aggiornata: l'APK scaricato in passato non serve piu'.
                    clearDownloadedUpdate()
                    state = UpdateState.UpToDate
                    return@launch
                }
                state = if (isUpdateApkDownloaded(update.apkSize)) {
                    UpdateState.ReadyToInstall(update)
                } else {
                    UpdateState.Available(update)
                }
                if (manual || AppContainer.settings.updateDismissedTag != update.tag) dialogVisible = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Niente rete o GitHub non risponde: in automatico si resta zitti (si riprova al
                // prossimo avvio), a mano si dice che non e' andata.
                state = if (manual) UpdateState.Failed("Controllo non riuscito. Riprova tra poco.", null) else previous
            }
        }
    }

    private suspend fun fetchLatest(): AppUpdate? {
        val response = http.get(LATEST_RELEASE_URL) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            // GitHub rifiuta le richieste senza User-Agent.
            header(HttpHeaders.UserAgent, "AILA-app")
        }
        // 404 = ancora nessuna release pubblicata (o solo pre-release): nessun aggiornamento.
        if (response.status.value == 404) return null
        if (!response.status.isSuccess()) error("GitHub ha risposto ${response.status.value}")
        return AppUpdateParser.parseLatestRelease(response.bodyAsText())
    }

    fun openDialog() {
        dialogVisible = true
    }

    /** Chiude la finestra; con [later] l'avviso automatico per questa versione non si ripete. */
    fun dismissDialog(later: Boolean = false) {
        dialogVisible = false
        if (later) {
            val shown = when (val s = state) {
                is UpdateState.Available -> s.update
                is UpdateState.Failed -> s.update
                else -> null
            }
            if (shown != null) AppContainer.settings.updateDismissedTag = shown.tag
        }
    }

    /** Tocco su "Aggiorna" / "Riprova": scarica l'APK (se non e' gia' qui) e apre l'installazione. */
    fun startUpdate() {
        val update = when (val s = state) {
            is UpdateState.Available -> s.update
            is UpdateState.ReadyToInstall -> s.update
            is UpdateState.NeedsPermission -> s.update
            is UpdateState.Failed -> s.update
            else -> null
        } ?: return

        if (isUpdateApkDownloaded(update.apkSize)) {
            install(update)
            return
        }
        job = scope.launch {
            state = UpdateState.Downloading(update, 0L, update.apkSize)
            try {
                downloadUpdateApk(update.apkUrl, update.apkSize, update.sha256) { done, total ->
                    state = UpdateState.Downloading(update, done, total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = UpdateState.Failed(
                    "Download non riuscito: ${e.message ?: "errore di rete"}. Riprova con una connessione migliore.",
                    update
                )
                return@launch
            }
            install(update)
        }
    }

    private fun install(update: AppUpdate) {
        if (!canInstallPackages()) {
            state = UpdateState.NeedsPermission(update)
            return
        }
        if (installDownloadedApk()) {
            state = UpdateState.ReadyToInstall(update)
            dialogVisible = false
        } else {
            state = UpdateState.Failed("Impossibile avviare l'installazione.", update)
        }
    }

    /**
     * L'app torna in primo piano. Se si era in attesa del permesso di installare e ora c'e', si
     * prosegue da soli; altrimenti e' il momento di un controllo automatico.
     */
    fun onForeground() {
        val s = state
        if (s is UpdateState.NeedsPermission) {
            if (canInstallPackages()) install(s.update)
            return
        }
        checkAutomatically()
    }

    /** Tocco sulla riga "Aggiornamenti" delle Impostazioni. */
    fun onSettingsRowClick() {
        when (state) {
            is UpdateState.Available,
            is UpdateState.ReadyToInstall,
            is UpdateState.Downloading,
            is UpdateState.NeedsPermission -> openDialog()
            is UpdateState.Failed -> if ((state as UpdateState.Failed).update != null) openDialog() else checkNow()
            else -> checkNow()
        }
    }

    /** Testo sotto "Aggiornamenti" nelle Impostazioni. */
    fun statusLabel(): String = when (val s = state) {
        UpdateState.Idle -> "Tocca per cercare una nuova versione"
        UpdateState.Checking -> "Controllo in corso…"
        UpdateState.UpToDate -> "AILA è aggiornata"
        is UpdateState.Available -> "Disponibile la versione ${s.update.version}"
        is UpdateState.Downloading -> "Scarico la versione ${s.update.version}…"
        is UpdateState.ReadyToInstall -> "Versione ${s.update.version} pronta da installare"
        is UpdateState.NeedsPermission -> "Serve il permesso per installare"
        is UpdateState.Failed -> s.message
    }
}
