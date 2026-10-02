package circolareplus.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import dev.chrisbanes.haze.hazeEffect
import circolareplus.design.ailaGlassBackdrop
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.InterrogationVoteType
import circolareplus.algorithms.OptimizerWeights
import circolareplus.algorithms.SeatMapHistoryRecord
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.algorithms.SeatMapProposal
import circolareplus.domain.model.RepresentativeRating
import circolareplus.data.AppContainer
import circolareplus.data.remote.ConnectivityState
import circolareplus.data.remote.dto.PollDetailDto
import circolareplus.data.remote.dto.CreatePollSlotRequestDto
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.data.repository.CircularsRepository
import circolareplus.data.repository.SessionRestore
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.appImePadding
import circolareplus.design.appSafeDrawingPadding
import circolareplus.design.appContentWidth
import circolareplus.design.MaxFormWidth
// Estensione (non richiamabile per nome qualificato come le altre composable di
// circolareplus.design usate in questo file): va importata per poterla usare come Modifier.ailaPressable(...).
import circolareplus.design.ailaPressable
import circolareplus.design.ailaSpatialSpring
import circolareplus.design.ailaPushTransition
import circolareplus.design.ailaTabTransition
import circolareplus.design.ailaContainerReveal
import androidx.compose.ui.layout.onGloballyPositioned
import circolareplus.design.ailaMorphClip
import circolareplus.design.ailaSheetReveal
import circolareplus.design.GlassBase
import circolareplus.design.GlassEdge
import circolareplus.design.ailaGlassSurface
import androidx.compose.animation.core.animateFloat
import circolareplus.design.ailaSelectionPop
import circolareplus.design.ailaUnlock
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.ai.ATTACHMENT_TEXT_MARKER
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantDynamicKnowledge
import circolareplus.ai.assistant.AssistantKnowledge
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.ai.EventDraft
import circolareplus.ai.HeuristicClassification
import circolareplus.ai.AnalysisActivity
import circolareplus.ai.tier
import circolareplus.ai.CircularClassificationPrompt
import circolareplus.ai.cleanPdfTextForAi
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.UnlockRequest
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import circolareplus.platform.currentTimeMillis
import circolareplus.push.currentPushPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Bottom bar riorganizzata secondo il nuovo IA di AILA: Circolari e Bacheca non sono più tab
// separate, ma vivono dentro "Classe" (con un selettore interno); Mappa Posti diventa una tab
// vera e propria invece di un flusso aperto solo dalla Home; "Profilo" diventa "Altro".
/**
 * Le tab della barra in basso, nell'ordine in cui compaiono: Home al centro, due sezioni per lato.
 * "Altro" (profilo e impostazioni) non e' piu' una tab: si apre dall'avatar nelle intestazioni, e
 * il suo posto l'hanno preso i Sondaggi, che prima stavano solo fra le scorciatoie della Home.
 */
enum class MainTab(val title: String) {
    CALENDAR("Calendario"),
    CLASS("Classe"),
    HOME("Home"),
    POLLS("Sondaggi"),
    SEATMAP("Mappa posti")
}

/** Sotto-sezione mostrata dentro la tab "Classe" (Circolari e Bacheca condividono la stessa tab). */
enum class ClassSection(val title: String) {
    CIRCULARS("Circolari"),
    BOARD("Bacheca")
}

/**
 * Categoria di [circolareplus.domain.model.NotificationLogEntry] più specifica di
 * [NotificationKind.SEATMAP]: dice a NotificationsScreen di portare direttamente alla votazione
 * preferenze invece che alla sola mappa dei posti.
 */
const val NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES = "seatmap_preferences"

/**
 * Categoria più specifica di [NotificationKind.POLLS] per i sondaggi a ordinamento: apre la
 * schermata Sondaggi direttamente sulla sezione "Ordinamento". Segue l'interruttore Sondaggi.
 */
const val NOTIFICATION_CATEGORY_RANKING_POLLS = "ranking_polls"

@OptIn(ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun MainAppShell(
    initialUser: User? = null,
    initialProfile: StudentProfile? = null
) {
    var currentUser by remember { mutableStateOf<User?>(initialUser) }
    var currentProfile by remember { mutableStateOf<StudentProfile?>(initialProfile) }

    // Sessione rifiutata dal server mentre l'app e' aperta (password cambiata altrove, account
    // eliminato): si chiude quella locale e si torna al login.
    LaunchedEffect(circolareplus.data.remote.SessionEvents.expired) {
        if (circolareplus.data.remote.SessionEvents.expired) {
            circolareplus.data.remote.SessionEvents.expired = false
            if (currentUser != null && AppContainer.settings.authToken.isNotBlank()) {
                AppContainer.authRepository.logout()
                currentUser = null
                currentProfile = null
            }
        }
    }

    // All'avvio, se non ci sono già uno user/profile forniti dall'esterno, prova a ripristinare
    // la sessione da un token salvato localmente (persistenza login tra un avvio e l'altro).
    var isRestoringSession by remember { mutableStateOf(currentUser == null && AppContainer.authRepository.hasStoredSession()) }

    // L'app è partita senza rete ma con una sessione valida: si entra con l'ultimo profilo
    // salvato e lo si dice, invece di far finta che sia tutto normale (o, come prima, invece di
    // buttare fuori dall'account).
    var startedOffline by remember { mutableStateOf(false) }
    var offlineBannerDismissed by remember { mutableStateOf(false) }

    // Sessione valida ma nessun profilo in cache: non si può entrare, però il token resta.
    // Prima questo caso finiva schiacciato sul login con le credenziali già cancellate.
    var offlineWithoutCache by remember { mutableStateOf(false) }
    var restoreAttempt by remember { mutableStateOf(0) }

    // Onboarding: dichiarato qui insieme agli altri remember, prima di qualunque return
    // anticipato, perché i remember di Compose sono posizionali e metterlo più in basso lo
    // renderebbe condizionale.
    var hasSeenOnboarding by remember { mutableStateOf(AppContainer.settings.hasSeenOnboarding) }

    // Dichiarati qui (invece che più sotto, dove servono davvero) perché lo schermo di
    // caricamento qui sotto prova ad anticipare la classificazione AI delle circolari più recenti
    // mentre comunque aspetta il giro di rete del ripristino sessione — così, se ci riesce in
    // tempo, l'utente trova già pronte le analisi appena entra invece di aspettarle una per una
    // aprendo ciascuna circolare.
    var circulars by remember { mutableStateOf<List<Circular>>(emptyList()) }
    var isCircularsLoading by remember { mutableStateOf(false) }
    var circularsError by remember { mutableStateOf<String?>(null) }
    // Contatore di ricarica: serve al pulsante "Riprova" del nuovo stato d'errore, che prima
    // non esisteva (l'errore era solo una riga di testo rosso, senza modo di ritentare).
    var circularsRefreshTrigger by remember { mutableStateOf(0) }
    // Le analisi gia' fatte si rileggono dal telefono: a ogni avvio la lista le mostra subito
    // invece di ripartire vuota (vedi LocalSettingsManager.readClassificationCache).
    val classifications = remember {
        mutableStateMapOf<Int, CircularAiClassification>().apply {
            putAll(AppContainer.settings.readClassificationCache())
        }
    }
    // Circolari attualmente in fase di classificazione (splash, sfondo, o apertura manuale):
    // evita che due punti diversi del codice scarichino/classifichino la stessa circolare in
    // parallelo, sprecando chiamate AI e banda per lo stesso risultato.
    // E' uno stato osservabile (non un insieme qualunque) perche' la lista delle circolari
    // mostra "Analisi in corso" per ognuna: l'analisi non dipende piu' dalla schermata di
    // dettaglio, quindi deve restare visibile anche dopo esserne usciti.
    val inFlightClassification = remember { mutableStateListOf<Int>() }
    // Sul telefono gira una sola analisi alla volta: due in parallelo si rubano CPU/NPU e
    // memoria e finiscono entrambe piu' tardi. Le altre aspettano qui il loro turno.
    val localAnalysisGate = remember { Mutex() }
    val queuedClassification = remember { mutableStateListOf<Int>() }
    var runningLocalClassification by remember { mutableStateOf<Int?>(null) }
    // Analisi in corso con Gemini (dal telefono, con la chiave personale): non passano dalla coda.
    val cloudClassification = remember { mutableStateListOf<Int>() }
    // Livello (vedi [circolareplus.ai.tier]) che l'analisi in corso produrra': un riassunto di
    // livello uguale o superiore arrivato dal server la rende inutile e la ferma.
    val inFlightTier = remember { mutableMapOf<Int, Int>() }
    val analysisJobs = remember { mutableMapOf<Int, Job>() }
    val analysisTitles = remember { mutableMapOf<Int, String>() }
    // Fermate a mano: non ripartono da sole riaprendo la circolare, solo col tasto "Analizza".
    val stoppedByUser = remember { mutableStateListOf<Int>() }
    // Circolari troppo lunghe per l'AI del telefono (ne leggerebbe solo l'inizio) che il server
    // riassumera' con Gemini: si aspetta lui invece di produrre un riassunto parziale.
    val awaitingServer = remember { mutableStateListOf<Int>() }
    // Da quando si aspetta il server per ciascuna circolare: l'attesa ha un tetto (vedi
    // SERVER_WAIT_MILLIS) perche' il server puo' non farcela (quota Gemini, rete) senza mai
    // arrendersi del tutto.
    val awaitingServerSince = remember { mutableMapOf<Int, Long>() }
    // Due giri di cron del server (ogni 15 minuti) piu' un margine.
    val SERVER_WAIT_MILLIS = 30 * 60_000L
    // Cosa fa il server per conto suo (ultima lettura di /analyses), vedi AnalysesSync.
    var serverSyncInfo by remember { mutableStateOf<CircularsRepository.AnalysesSync?>(null) }
    // Classe dell'utente ("4 CSA"), letta alla prima analisi fatta sul telefono: il contesto
    // dell'AI era fisso sulla 4^CSA e le altre classi ricevevano un'analisi pensata per lei.
    var myClassLabel by remember { mutableStateOf<String?>(null) }

    fun storeClassification(classification: CircularAiClassification) {
        classifications[classification.circularNumber] = classification
        AppContainer.settings.saveClassification(classification)
    }

    // Lo stato letto dalla notifica di Android (servizio in primo piano con il tasto Stop).
    fun publishAnalysisActivity() {
        val running = runningLocalClassification ?: cloudClassification.firstOrNull()
        AnalysisActivity.update(
            AnalysisActivity.State(
                running = running,
                runningTitle = running?.let { analysisTitles[it] } ?: "",
                onDevice = running != null && running == runningLocalClassification,
                queued = queuedClassification.toList()
            )
        )
    }

    /**
     * Mostra un'analisi arrivata dal server se vale almeno quanto quella che c'e', e ferma
     * l'analisi in corso su questo telefono se ormai e' inutile: un riassunto fatto da un
     * compagno o dal server compare subito invece di aspettare che finisca la propria.
     * Un'analisi con Gemini si ferma solo per un altro riassunto di Gemini, mai per uno locale.
     */
    fun adoptServerAnalysis(incoming: CircularAiClassification) {
        val number = incoming.circularNumber
        val current = classifications[number]
        if (current != null && current.tier > incoming.tier) return
        if (current != incoming) storeClassification(incoming)
        awaitingServer -= number
        val runningTier = inFlightTier[number] ?: return
        if (incoming.tier >= runningTier) analysisJobs[number]?.cancel()
    }

    suspend fun fetchServerAnalysisOrNull(number: Int): CircularAiClassification? = try {
        AppContainer.circularsRepository.getCachedAnalysis(number)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // Server irraggiungibile: si procede con l'analisi come prima.
    }

    // Download del PDF (con gli allegati), classificazione, e condivisione del risultato.
    suspend fun classifyAndShare(
        circular: Circular,
        allowLocalFallback: Boolean,
        /** Con l'AI del telefono: una circolare lunga si lascia al server, se la riassumera' lui. */
        waitForServerIfLong: Boolean = false,
        /**
         * Circolare appena uscita: il server la riassume con Gemini nel giro di qualche minuto,
         * quindi si aspetta lui (fino a SERVER_WAIT_MILLIS) invece di rifarla subito sul telefono
         * con i suoi limiti — e col rischio di salvare un'analisi parziale o interrotta.
         */
        waitForFreshServer: Boolean = false
    ) {
        if (waitForFreshServer && serverSyncInfo?.serverWillSummarize(circular.number) == true) {
            val since = awaitingServerSince.getOrPut(circular.number) { currentTimeMillis() }
            if (currentTimeMillis() - since < SERVER_WAIT_MILLIS) {
                if (circular.number !in awaitingServer) awaitingServer += circular.number
                return
            }
        }
        val pdfBytes = AppContainer.circularsRepository.downloadPdfBytes(circular.r2PdfKey)
        var pdfText = AppContainer.pdfTextExtractor.extractText(pdfBytes)

        // Gli allegati PDF (colonna "Allegati" di Spaggiari) entrano nello stesso testo
        // analizzato dall'AI: un'informativa pubblicata come allegato invece che nel corpo
        // della circolare non deve passare inosservata al riassunto/classificazione. Un
        // allegato che non si scarica o non si legge viene saltato senza far fallire
        // l'intera analisi — meglio un riassunto senza quell'allegato che nessun riassunto.
        for (attachment in circular.attachments) {
            val pdfKey = attachment.pdfKey ?: continue
            try {
                val attBytes = AppContainer.circularsRepository.downloadPdfBytes(pdfKey)
                val attText = AppContainer.pdfTextExtractor.extractText(attBytes)
                if (attText.isNotBlank()) {
                    pdfText += "$ATTACHMENT_TEXT_MARKER${attachment.label} ---\n\n$attText"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignorato di proposito: vedi commento sopra.
            }
        }

        // Il modello sul telefono legge al massimo CircularClassificationPrompt.MAX_PDF_CHARS
        // caratteri (2-3 pagine): di una circolare da 30 pagine riassumerebbe solo l'inizio, dopo
        // minuti di calcolo, e il riassunto verrebbe comunque sostituito da quello del server.
        if (
            waitForServerIfLong &&
            cleanPdfTextForAi(pdfText).length > CircularClassificationPrompt.MAX_PDF_CHARS &&
            serverSyncInfo?.serverWillSummarize(circular.number) == true
        ) {
            if (circular.number !in awaitingServer) awaitingServer += circular.number
            return
        }

        if (myClassLabel == null) {
            myClassLabel = try {
                AppContainer.preferencesRepository.getClassConfig().classLabel
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val classLabel = myClassLabel?.let { circolareplus.util.compactClassLabels(it.replace(Regex("^(\\d)\\s+"), "$1^")) }
        val result = AppContainer.newAiClassifier(
            allowLocalFallback = allowLocalFallback,
            pdfTextLength = pdfText.length
        ).classifyCircularText(
            circularNumber = circular.number,
            circularTitle = circular.title,
            pdfText = pdfText,
            studentContext = if (classLabel.isNullOrBlank()) "Studente di scuola superiore"
            else "Studente di scuola superiore, classe $classLabel"
        )
        // Nel frattempo potrebbe essere arrivato un riassunto migliore dal server: non si
        // sostituisce con uno peggiore.
        val current = classifications[circular.number]
        if (current == null || current.tier <= result.tier) storeClassification(result)

        // Il ripiego euristico e' un messaggio d'errore, non un riassunto: resta su questo
        // telefono e non si condivide (il server lo rifiuterebbe comunque).
        if (result.isFallback) return

        // Si condivide il risultato con tutti gli altri utenti: il server tiene quello di
        // livello piu' alto e, se ne ha gia' uno migliore, lo restituisce e si mostra quello.
        // Un fallimento nel salvataggio non deve rompere la classificazione gia' ottenuta.
        try {
            AppContainer.circularsRepository.saveAnalysis(result)?.let { better -> adoptServerAnalysis(better) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignorato di proposito: vedi commento sopra.
        }
    }

    // Un solo posto che sa come classificare una circolare, usato sia dall'anticipo durante il
    // caricamento, sia dal ciclo in background mentre si è dentro l'app, sia dall'apertura
    // manuale di una circolare — prima questa logica era duplicata in più punti.
    suspend fun classifyCircularIfNeeded(
        circular: Circular,
        forceReanalyze: Boolean = false,
        allowLocalFallback: Boolean = true
    ) {
        val number = circular.number
        if ((classifications.containsKey(number) && !forceReanalyze) || number in inFlightClassification) return
        inFlightClassification += number
        analysisTitles[number] = circular.title
        val onDevice = AppContainer.isUsingLocalAiFirst()
        try {
            // Prima di rifare l'analisi sul telefono si controlla se qualcuno l'ha già mandata al
            // server: la cache è condivisa fra tutti gli utenti (stesso numero circolare, stesso
            // contesto studente di default), quindi non ha senso spendere quota/tempo AI per un
            // risultato che esiste già. "Rianalizza" salta questo controllo di proposito: serve
            // proprio a rifare un'analisi giudicata scadente.
            if (!forceReanalyze) {
                fetchServerAnalysisOrNull(number)?.let { cached ->
                    storeClassification(cached)
                    return
                }
            }

            if (onDevice) {
                inFlightTier[number] = 1
                queuedClassification += number
                publishAnalysisActivity()
                localAnalysisGate.withLock {
                    queuedClassification -= number
                    // Mentre era in coda un compagno o il server potrebbero averla gia' fatta.
                    if (!forceReanalyze) {
                        fetchServerAnalysisOrNull(number)?.let { cached ->
                            storeClassification(cached)
                            return
                        }
                    }
                    runningLocalClassification = number
                    publishAnalysisActivity()
                    try {
                        // "Analizza"/"Rianalizza" (forceReanalyze) e' una richiesta esplicita:
                        // si analizza sul telefono anche una circolare lunga.
                        classifyAndShare(
                            circular, allowLocalFallback,
                            waitForServerIfLong = !forceReanalyze,
                            waitForFreshServer = !forceReanalyze
                        )
                    } finally {
                        runningLocalClassification = null
                    }
                }
            } else {
                inFlightTier[number] = 2
                cloudClassification += number
                publishAnalysisActivity()
                classifyAndShare(circular, allowLocalFallback, waitForFreshServer = !forceReanalyze)
            }
        } catch (e: CancellationException) {
            // Va sempre rilanciata: è così che funzionano la cancellazione strutturata, il
            // timeout dell'anticipo durante il caricamento (withTimeoutOrNull più sotto) e il
            // tasto Stop. Inghiottirla qui romperebbe tutti e tre i meccanismi.
            throw e
        } catch (e: Exception) {
            // Fallimento prima ancora di arrivare a un classificatore (download del PDF o
            // estrazione testo falliti): passa dalla stessa euristica di riserva usata dai
            // classificatori, invece di una CircularAiClassification costruita a mano con
            // isFallback di default a false — altrimenti questo errore si mimetizzava da vera
            // classificazione POTENTIAL, esattamente il problema descritto nel punto 1.
            if (!classifications.containsKey(number)) {
                classifications[number] = HeuristicClassification.classify(
                    circularNumber = number,
                    title = circular.title,
                    text = "",
                    failureReason = "impossibile analizzare il PDF: ${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}",
                    notConfiguredMessage = "Analisi di AILA Assistant non disponibile."
                )
            }
        } finally {
            inFlightClassification -= number
            queuedClassification -= number
            cloudClassification -= number
            inFlightTier.remove(number)
            publishAnalysisActivity()
        }
    }

    /**
     * Avvia l'analisi di una circolare fuori dalla schermata, cosi' prosegue anche uscendo dal
     * dettaglio o mettendo l'app in background (su Android la tiene viva il servizio in primo
     * piano, vedi AnalysisForegroundService). Il Job resta qui per il tasto Stop.
     */
    fun launchClassification(
        circular: Circular,
        forceReanalyze: Boolean = false,
        allowLocalFallback: Boolean = true
    ): Job {
        analysisJobs[circular.number]?.takeIf { it.isActive }?.let { return it }
        stoppedByUser -= circular.number
        if (forceReanalyze) awaitingServer -= circular.number
        val job = AnalysisActivity.scope.launch {
            classifyCircularIfNeeded(circular, forceReanalyze, allowLocalFallback)
        }
        analysisJobs[circular.number] = job
        job.invokeOnCompletion { if (analysisJobs[circular.number] === job) analysisJobs.remove(circular.number) }
        return job
    }

    fun stopClassification(number: Int) {
        stoppedByUser += number
        analysisJobs[number]?.cancel()
    }

    // --- Stato Notifiche (storico locale, mai sul server) -------------------------------------
    var notificationLog by remember { mutableStateOf(AppContainer.settings.listNotifications()) }
    val hasUnreadNotifications = notificationLog.any { !it.read }

    /**
     * Confronta quello che è appena arrivato dal server con l'ultima cosa vista e, se c'è
     * qualcosa di nuovo, scrive una notifica nello storico locale (quello della campanella).
     *
     * Perché serve: il push FCM copre solo i casi in cui il server manda davvero un messaggio, e
     * non è configurato finché non esiste il progetto Firebase. Senza questo, aprire l'app e
     * trovare una circolare nuova non lasciava alcuna traccia: la campanella restava vuota e
     * l'unico modo di accorgersene era guardare l'elenco.
     *
     * Al primo avvio non si notifica nulla: si prende solo nota del punto di partenza, altrimenti
     * la campanella si riempirebbe di avvisi per roba che era già lì da settimane.
     *
     * I filtri per categoria delle Impostazioni valgono anche qui: una categoria spenta aggiorna
     * comunque il segnalibro (così riaccendendola non arriva un arretrato di notifiche vecchie)
     * ma non scrive nulla.
     */
    fun noteNovelties(
        freshCirculars: List<Circular>? = null,
        freshProposals: List<Proposal>? = null,
        freshSeatMap: List<DeskAssignment>? = null,
        freshPreferencesOpen: Boolean? = null
    ) {
        val settings = AppContainer.settings
        var wroteSomething = false

        if (freshCirculars != null && freshCirculars.isNotEmpty()) {
            val newest = freshCirculars.maxOf { it.number }
            val lastSeen = settings.lastSeenCircularNumber
            if (lastSeen == 0) {
                settings.lastSeenCircularNumber = newest
            } else if (newest > lastSeen) {
                if (settings.isNotificationKindEnabled(NotificationKind.CIRCULARS.key)) {
                    // Il push FCM scrive gia' la sua voce ("Circolare n. N: titolo"): senza questo
                    // controllo la stessa circolare compariva due volte in campanella, e con il
                    // rinfresco dinamico (che rilegge la lista appena arriva il push) quasi sempre.
                    val alreadyLogged = settings.listNotifications()
                        .filter { it.category == NotificationKind.CIRCULARS.key }
                    freshCirculars
                        .filter { it.number > lastSeen }
                        .sortedBy { it.number }
                        .forEach { circular ->
                            val duplicate = alreadyLogged.any {
                                it.title == "Circolare n. ${circular.number}" ||
                                    it.body.startsWith("Circolare n. ${circular.number}:")
                            }
                            if (!duplicate) {
                                settings.addNotification("Circolare n. ${circular.number}", circular.title, NotificationKind.CIRCULARS.key)
                            }
                        }
                    wroteSomething = true
                }
                settings.lastSeenCircularNumber = newest
            }
        }

        if (freshProposals != null && freshProposals.isNotEmpty()) {
            // Le proposte non hanno un numero progressivo: si usa l'id della più recente come
            // segnalibro, e si notificano quelle che nell'elenco stanno prima di esso.
            val ordered = freshProposals.sortedByDescending { it.createdAt }
            val newestId = ordered.first().id
            val lastSeenId = settings.lastSeenProposalId
            if (lastSeenId.isBlank()) {
                settings.lastSeenProposalId = newestId
            } else if (newestId != lastSeenId) {
                if (settings.isNotificationKindEnabled(NotificationKind.BOARD.key)) {
                    val markerIndex = ordered.indexOfFirst { it.id == lastSeenId }
                    // Segnalibro non più nell'elenco (proposta cancellata): si notifica solo la
                    // più recente, invece di rovesciare in campanella tutta la bacheca.
                    val fresh = if (markerIndex >= 0) ordered.take(markerIndex) else ordered.take(1)
                    // A app aperta il push FCM scrive già la sua voce nella campanella (vedi
                    // CircolareMessagingService, dove il corpo è il solo titolo): senza questo
                    // controllo la stessa proposta compariva due volte, una per il push e una per
                    // questo confronto.
                    // Solo le voci recenti: un titolo uguale a quello di una proposta di settimane
                    // fa non deve far tacere una proposta nuova.
                    val recentCutoff = currentTimeMillis() - 10 * 60_000L
                    val alreadyLogged = settings.listNotifications()
                        .filter { it.category == NotificationKind.BOARD.key && it.receivedAtMillis >= recentCutoff }
                    fresh.reversed().forEach { proposal ->
                        if (alreadyLogged.any { it.body.trim() == proposal.title.trim() }) return@forEach
                        settings.addNotification(
                            "Nuova proposta in bacheca",
                            "${proposal.title} — ${if (proposal.isAnonymous) "Anonimo" else proposal.authorName}",
                            NotificationKind.BOARD.key
                        )
                    }
                    wroteSomething = true
                }
                settings.lastSeenProposalId = newestId
            }
        }

        if (freshSeatMap != null && freshSeatMap.isNotEmpty()) {
            // La mappa non ha id né data di pubblicazione nel modello del client: come
            // segnalibro si usa una firma della disposizione, cioè chi siede dove. Cambia
            // esattamente quando cambia la disposizione, che è la cosa da notificare.
            val signature = freshSeatMap
                .sortedWith(compareBy({ it.row }, { it.column }))
                .joinToString("|") { "${it.row},${it.column},${it.studentAId},${it.studentBId}" }
            val lastSeen = settings.lastSeenSeatMapSignature
            if (lastSeen.isBlank()) {
                settings.lastSeenSeatMapSignature = signature
            } else if (signature != lastSeen) {
                if (settings.isNotificationKindEnabled(NotificationKind.SEATMAP.key)) {
                    settings.addNotification(
                        "Nuova disposizione dei banchi",
                        "Il Rappresentante ha pubblicato una nuova mappa dei posti.",
                        NotificationKind.SEATMAP.key
                    )
                    wroteSomething = true
                }
                settings.lastSeenSeatMapSignature = signature
            }
        }

        if (freshPreferencesOpen != null) {
            val lastSeen = settings.lastSeenPreferencesOpen
            if (freshPreferencesOpen && !lastSeen) {
                // Notifichiamo solo quando si APRE, non quando si chiude (sarebbe rumore).
                if (settings.isNotificationKindEnabled(NotificationKind.SEATMAP.key)) {
                    settings.addNotification(
                        "Preferenze compagni di banco",
                        "Il Rappresentante ha aperto la votazione preferenze. Tocca per votare →",
                        NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES
                    )
                    wroteSomething = true
                }
            }
            settings.lastSeenPreferencesOpen = freshPreferencesOpen
        }

        if (wroteSomething) {
            notificationLog = AppContainer.settings.listNotifications()
        }
    }

    LaunchedEffect(restoreAttempt) {
        if (isRestoringSession) {
            // Vedi SessionRestore: distinguere "sessione scaduta" da "non c'è rete" è ciò che
            // impedisce alla modalità aereo di far uscire dall'account.
            val outcome = AppContainer.authRepository.restoreSession()
            val restored = when (outcome) {
                is SessionRestore.Online -> outcome.user to outcome.profile
                is SessionRestore.Offline -> outcome.user to outcome.profile
                else -> null
            }
            startedOffline = outcome is SessionRestore.Offline
            offlineWithoutCache = outcome is SessionRestore.OfflineWithoutCache
            if (restored != null) {
                currentUser = restored.first
                currentProfile = restored.second
                // Calendario, bacheca, compagni e sondaggi partono adesso, insieme alle circolari
                // qui sotto, invece di aspettare la Home (vedi StartupPrefetch).
                if (outcome is SessionRestore.Online) StartupPrefetch.begin()

                // Budget di tempo volutamente breve: lo schermo di caricamento serve già a
                // coprire il giro di rete del ripristino sessione, ma non deve trasformarsi in
                // un'attesa lunga e fastidiosa solo per classificare circolari in anticipo. Se il
                // tempo scade prima di finire, si procede comunque: il ciclo in background (una
                // volta dentro l'app, vedi più sotto) continua da dove questo si è fermato.
                // Ridotto a 1500ms per evitare delay di 10+ secondi al boot su dispositivi lenti.
                // Con l'AI locale l'anticipazione si salta completamente: i modelli locali
                // sono troppo lenti (decine di secondi per circolare) e occuperebbero il motore
                // proprio mentre l'utente entra nell'app. In rete, il caricamento della lista è
                // prioritario e la classificazione aspetta.
                if (!AppContainer.isUsingLocalAiFirst()) {
                    withTimeoutOrNull(1500L) {
                        try {
                            val fetched = AppContainer.circularsRepository.listCirculars()
                            circulars = fetched
                            noteNovelties(freshCirculars = fetched)
                            // Le più recenti prima: sono quelle che l'utente vede subito in Home
                            // e apre più probabilmente per prime.
                            for (circular in fetched.sortedByDescending { it.number }) {
                                classifyCircularIfNeeded(circular)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Nessuna connessione o elenco circolari non raggiungibile: si procede
                            // comunque, verranno ricaricate normalmente una volta dentro l'app.
                        }
                    }
                } else {
                    // Carica la lista senza classificare subito
                    try {
                        val fetched = AppContainer.circularsRepository.listCirculars()
                        circulars = fetched
                        noteNovelties(freshCirculars = fetched)
                    } catch (e: Exception) {
                        // Silenziosamente ignorato: il caricamento si ripeterà alla navigazione
                    }
                }
            }
            isRestoringSession = false
        }
    }

    // All'avvio a freddo il logo si costruisce (vedi AilaLoadingScreen): la schermata resta finché
    // l'intro non è finita, anche se la sessione si ripristina prima o non c'è niente da ripristinare.
    var introPending by remember { mutableStateOf(circolareplus.ui.screens.isAilaIntroPending()) }

    if (isRestoringSession || introPending) {
        // Prima: spinner generico su sfondo bianco. Ora nello stile della schermata
        // "Caricamento" del kit AILA (sfondo scuro sfumato, logo, testo).
        AilaLoadingScreen(onIntroFinished = { introPending = false })
        return
    }

    // Primo avvio in assoluto: le 3 schermate di presentazione del mockup, prima del login.
    // Chi ha già una sessione salvata non le vede (non passa di qui: currentUser è valorizzato).
    if (currentUser == null && !hasSeenOnboarding) {
        // L'ultimo passo dell'onboarding fa scegliere l'AI. Scrive nelle stesse impostazioni che
        // poi mostra la schermata Impostazioni (chiave, provider, modello): niente stato suo.
        // `remember` perché l'oggetto legge la RAM del telefono e ordina il catalogo modelli.
        val aiSetup = remember {
            val ramMb = circolareplus.ai.totalDeviceRamMb()
            OnboardingAiSetup(
                unavailableReason = circolareplus.ai.onDeviceAiUnavailableReason(),
                deviceRamMb = ramMb,
                localModels = circolareplus.ai.LocalAiCatalog.selectableFor(ramMb),
                isModelInstalled = { model -> AppContainer.localModelStore.isInstalled(model) },
                testApiKey = { key ->
                    // Come in Impostazioni: si prova la chiave digitata, non quella salvata.
                    circolareplus.ai.ClientSideAiClassifier(userApiKey = key).testKey()
                },
                saveApiKey = { key ->
                    AppContainer.settings.userAiApiKey = key
                    AppContainer.settings.aiProvider = circolareplus.ai.AiProvider.GOOGLE_AI_STUDIO.id
                },
                downloadModel = { model, onProgress ->
                    AppContainer.localModelStore.download(model, onProgress)
                },
                activateModel = { model ->
                    AppContainer.settings.localAiModelId = model.id
                    AppContainer.settings.aiProvider = circolareplus.ai.AiProvider.ON_DEVICE.id
                    // Il motore tiene in memoria l'ultimo modello caricato: si scarica per
                    // essere certi che la prima analisi usi quello appena scelto.
                    AppContainer.localLlm.unload()
                },
                cancelDownload = { model -> AppContainer.localModelStore.cancelDownload(model) }
            )
        }
        OnboardingScreen(
            onFinish = {
                AppContainer.settings.hasSeenOnboarding = true
                hasSeenOnboarding = true
            },
            aiSetup = aiSetup
        )
        return
    }

    // Sessione ancora valida ma nessuna copia locale del profilo e niente rete: prima si finiva
    // qui con il token appena cancellato, cioè con un logout mascherato da schermata di login.
    // Ora il token resta e si può semplicemente riprovare quando la rete torna.
    if (offlineWithoutCache && currentUser == null) {
        OfflineGateScreen(
            onRetry = {
                offlineWithoutCache = false
                isRestoringSession = true
                restoreAttempt++
            },
            onLogout = {
                AppContainer.authRepository.logout()
                offlineWithoutCache = false
            }
        )
        return
    }

    // Se l'utente non è autenticato, mostra la schermata di Login / Registrazione
    if (currentUser == null || currentProfile == null) {
        AuthScreen(
            onLoginSuccess = { user, profile ->
                currentUser = user
                currentProfile = profile
            }
        )
        return
    }

    val user = currentUser!!
    val profile = currentProfile!!
    val isRepresentative = user.role == UserRole.REPRESENTATIVE
    // Chi firma lo svelamento di un autore anonimo: 2 Rappresentanti + 1 Guardia di Sicurezza. La
    // Guardia resta uno studente (la sceglie il Rappresentante dalla Scheda Classe), quindi non si
    // riconosce dal ruolo ma da quello che risponde il server (vedi il caricamento della bacheca).
    var isSecurityGuard by remember { mutableStateOf(false) }
    val canModerateIdentity = isRepresentative || isSecurityGuard
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    // Avvio senza rete: la striscia "Nessuna connessione" veniva decisa una volta sola e restava
    // anche dopo il ritorno della rete (lista e calendario si aggiornavano, la striscia no, e
    // bastava un timeout all'avvio su una rete lenta). Si riprova in silenzio finche' il server
    // risponde: allora la striscia sparisce e il profilo si aggiorna.
    LaunchedEffect(startedOffline) {
        while (startedOffline) {
            delay(8_000L)
            when (val outcome = AppContainer.authRepository.restoreSession()) {
                is SessionRestore.Online -> {
                    currentUser = outcome.user
                    currentProfile = outcome.profile
                    startedOffline = false
                }
                // Il server ha risposto che il token non vale piu' (restoreSession ha gia' fatto
                // il logout): si torna al login come a un avvio normale.
                is SessionRestore.SessionExpired, is SessionRestore.NoSession -> {
                    startedOffline = false
                    currentUser = null
                    currentProfile = null
                }
                else -> Unit // Ancora offline: si riprova al giro dopo.
            }
        }
    }

    // Rete caduta ad app gia' aperta (es. entrando a scuola): si comporta come un avvio offline,
    // cioe' striscia in cima e ricontrollo periodico finche' il server torna a rispondere. Le
    // schermate intanto mostrano le copie salvate (vedi ApiClient.get).
    // Un solo errore di rete non basta per dire "sei offline": rientrando nell'app da un'altra,
    // le prime richieste falliscono spesso perche' il sistema ha chiuso le connessioni mentre
    // l'app era in background, anche con la rete perfetta. Prima di mostrare la striscia si
    // riprova (con una pausa) a parlare davvero col server, e solo se fallisce ancora si e' offline.
    LaunchedEffect(ConnectivityState.isOffline) {
        if (!ConnectivityState.isOffline || startedOffline) return@LaunchedEffect
        repeat(2) { attempt ->
            delay(if (attempt == 0) 800L else 2_500L)
            when (val outcome = AppContainer.authRepository.restoreSession()) {
                is SessionRestore.Online -> {
                    currentUser = outcome.user
                    currentProfile = outcome.profile
                    return@LaunchedEffect
                }
                is SessionRestore.SessionExpired, is SessionRestore.NoSession -> {
                    currentUser = null
                    currentProfile = null
                    return@LaunchedEffect
                }
                else -> Unit
            }
        }
        startedOffline = true
    }

    // Download automatico per l'uso offline: con la rete, in sottofondo, si scarica tutta l'app
    // (anche le schermate mai aperte, i dettagli dei sondaggi, i commenti e i PDF delle circolari
    // recenti). Riparte al ritorno della rete e comunque non piu' di una volta ogni mezz'ora.
    // Si aspetta qualche secondo per non rubare banda al caricamento delle schermate.
    LaunchedEffect(user.id, ConnectivityState.isOffline) {
        if (ConnectivityState.isOffline) return@LaunchedEffect
        delay(5_000L)
        while (isActive) {
            circolareplus.platform.awaitForeground()
            circolareplus.data.OfflineSync.runIfDue()
            delay(10L * 60L * 1000L)
        }
    }

    // Registra il token push per questo dispositivo una volta per sessione (utente loggato).
    // Restituisce silenziosamente null finché Firebase non è configurato (vedi PushTokenProvider):
    // in quel caso la registrazione viene semplicemente saltata, senza errori visibili.
    LaunchedEffect(user.id) {
        val token = AppContainer.pushTokenProvider.getToken()
        if (token != null) {
            try {
                AppContainer.fcmRepository.registerToken(
                    token,
                    currentPushPlatform(),
                    mutedKinds = AppContainer.settings.mutedNotificationKinds,
                    systemNotifications = AppContainer.settings.isSystemNotificationsEnabled
                )
                // Topic FCM: una sola richiesta del server per notifica (piano Free di Cloudflare).
                AppContainer.fcmRepository.subscribeToTopics(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non bloccante: l'app resta utilizzabile anche se la registrazione fallisce.
            }
        }
    }

    // Dopo un cambio negli interruttori delle notifiche: il server li applica ai messaggi iOS
    // (con l'app in background il banner lo mostra il sistema, non onPushReceived).
    fun syncPushPreferences() {
        coroutineScope.launch {
            try {
                AppContainer.fcmRepository.syncPreferences(
                    mutedKinds = AppContainer.settings.mutedNotificationKinds,
                    systemNotifications = AppContainer.settings.isSystemNotificationsEnabled
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si riprova da sola al prossimo avvio, che registra token e preferenze insieme.
            }
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    // Giorno da aprire nel calendario (evento toccato nella Home), consumato dal calendario.
    var calendarFocusDateIso by remember { mutableStateOf<String?>(null) }
    // I Sondaggi sono ora una tab: "essere nei sondaggi" vuol dire avere quella tab selezionata.
    val isInPollsScreen = selectedTab == MainTab.POLLS
    var isInProfileScreen by rememberSaveable { mutableStateOf(false) }
    var isInClassRosterScreen by rememberSaveable { mutableStateOf(false) }
    var isInNotificationsScreen by rememberSaveable { mutableStateOf(false) }
    var isInSettingsScreen by rememberSaveable { mutableStateOf(false) }
    // Diagnostica background (iOS), aperta dalle Impostazioni.
    var isInBackgroundDebugScreen by rememberSaveable { mutableStateOf(false) }
    var debugMenuUnlocked by remember { mutableStateOf(AppContainer.settings.isDebugMenuEnabled) }
    // Ricomposizione dopo il salvataggio della chiave: la schermata legge il valore da
    // LocalSettingsManager, che non è stato di Compose e da solo non farebbe ridisegnare nulla.
    var apiKeyRevision by remember { mutableStateOf(0) }
    var selectedCircularForDetail by remember { mutableStateOf<Circular?>(null) }
    // Su schermi larghi (iPad in orizzontale, tablet) la circolare si apre accanto alla lista,
    // nella tab Classe, invece che sopra a tutto: e' questa, non selectedCircularForDetail.
    var paneCircular by remember { mutableStateOf<Circular?>(null) }
    val lastDetailCircular = remember { arrayOf<Circular?>(null) }

    // --- Stato Scheda Classe (solo Rappresentante) ------------------------------------------
    var classRosterEntries by remember { mutableStateOf<List<RatingEntryDto>>(emptyList()) }
    var disciplinePairs by remember { mutableStateOf<List<circolareplus.data.remote.dto.DisciplinePairDto>>(emptyList()) }
    var isClassRosterLoading by remember { mutableStateOf(false) }
    var classRosterError by remember { mutableStateOf<String?>(null) }
    // Errore di una singola azione (stepper/switch), separato dall'errore di caricamento iniziale:
    // prima entrambi finivano nella stessa variabile passata a LoadableContent, che sostituisce
    // TUTTO il contenuto con il messaggio di errore — un salvataggio fallito faceva sparire
    // l'intera Scheda Classe invece di segnalare solo quella riga, sembrando "non succede nulla".
    var classRosterActionError by remember { mutableStateOf<String?>(null) }

    // --- Stato Calendario --------------------------------------------------------------
    var calendarEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isCalendarLoading by remember { mutableStateOf(false) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var calendarRefreshTrigger by remember { mutableStateOf(0) }
    var handledCalendarRefresh by remember { mutableStateOf(0) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    // Chiede al foglio "Nuovo evento" di chiudersi con la sua animazione (poi lui chiama onDismiss):
    // togliendolo dalla composizione di colpo, come prima, spariva senza scendere.
    var addEventCloseRequested by remember { mutableStateOf(false) }
    // Eventi che si stanno eliminando: la card esce con un'animazione prima di sparire.
    var removingEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Con quale passo si apre il foglio "Nuovo evento" e quale data preselezionare: impostati da
    // dove si tocca "+" (menu generico dall'header, scorciatoia AILA, o "+" sul giorno scelto nella
    // griglia, che prima non permetteva di creare un evento per quel giorno specifico).
    var addEventInitialStep by remember { mutableStateOf(EventCreationStep.MENU) }
    var addEventInitialDateIso by remember { mutableStateOf<String?>(null) }
    var pendingDuplicateWarning by remember { mutableStateOf<String?>(null) }
    var pendingDuplicateRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Evento su cui si è toccato "vedi dettagli" dal calendario: prima onEventClick non faceva
    // nulla, quindi orario, note e destinatari specifici erano invisibili fuori dalla card
    // riassuntiva.
    var eventDetailToShow by remember { mutableStateOf<CalendarEvent?>(null) }

    // --- Stato Bacheca ---------------------------------------------------------------------
    var proposals by remember { mutableStateOf<List<Proposal>>(emptyList()) }
    var isProposalsLoading by remember { mutableStateOf(false) }
    var proposalsError by remember { mutableStateOf<String?>(null) }
    var proposalsRefreshTrigger by remember { mutableStateOf(0) }
    var showAddProposalDialog by remember { mutableStateOf(false) }
    // Richieste di svelamento in attesa di firme: le carica solo chi le può firmare.
    var unlockRequests by remember { mutableStateOf<List<UnlockRequest>>(emptyList()) }

    // --- Stato tab "Classe" (Circolari + Bacheca unite in un'unica tab, con selettore interno) --
    var classSection by rememberSaveable { mutableStateOf(ClassSection.CIRCULARS) }

    // --- Stato Ricerca globale --------------------------------------------------------------
    var isInSearchScreen by rememberSaveable { mutableStateOf(false) }

    // --- Stato AILA Assistant (il pulsante dell'assistente nella Ricerca) --------------------
    // La conversazione vive qui e non dentro la schermata di proposito: una risposta puo' durare
    // una decina di secondi fra chiamata al modello ed eventuale lettura di un PDF, e chi torna
    // indietro un attimo per controllare una circolare non deve perderla ne' doverla rifare.
    var isInAssistantScreen by rememberSaveable { mutableStateOf(false) }
    // La conversazione in corso ha un id suo, cosi' ogni salvataggio aggiorna la stessa voce
    // dello storico invece di aggiungerne una a ogni domanda.
    var assistantConversationId by rememberSaveable { mutableStateOf("c${currentTimeMillis()}") }
    val assistantMessages = remember {
        mutableStateListOf<AssistantMessage>().also { list ->
            // Quando il sistema uccide il processo, l'id della conversazione torna (e'
            // `rememberSaveable`) ma i messaggi no: si ripescano dall'archivio locale. Senza
            // questo, la prima domanda dopo il ripristino salverebbe la conversazione con quel
            // vecchio id tenendo solo se stessa, cancellando il resto del filo.
            AppContainer.settings.listAssistantConversations()
                .firstOrNull { it.id == assistantConversationId }
                ?.let { list.addAll(it.messages) }
        }
    }
    // Le conversazioni con una risposta in arrivo, per id. Non e' un booleano unico: chi apre una
    // "Nuova chat" mentre il modello sta ancora rispondendo deve vedere una chat pulita e libera,
    // mentre la risposta atterra nella conversazione da cui e' partita la domanda.
    val pendingAssistantConversations = remember { mutableStateListOf<String>() }
    val isAssistantThinking = assistantConversationId in pendingAssistantConversations
    // I dati che non sono gia' in memoria (sondaggi, mappa, storico, valutazioni) si caricano
    // una volta sola per conversazione: sono le stesse rotte che le altre schermate chiamano
    // quando le apri, e rifarle a ogni domanda sarebbe traffico per dati che non cambiano
    // durante una chat.
    var assistantDynamic by remember { mutableStateOf<AssistantDynamicKnowledge?>(null) }
    var assistantMessageCounter by remember { mutableStateOf(0) }
    // Ragionamento del modello locale: la scelta e' salvata nelle impostazioni, qui se ne tiene
    // una copia perche' l'interruttore deve muoversi al tocco.
    var assistantThinking by remember { mutableStateOf(AppContainer.settings.assistantThinkingEnabled) }
    var assistantConversations by remember {
        mutableStateOf(AppContainer.settings.listAssistantConversations())
    }
    var recentSearches by remember { mutableStateOf(AppContainer.settings.recentSearches) }

    // --- Stato Mappa Posti & Preferenze Sociali --------------------------------------------
    var classmates by remember { mutableStateOf<List<User>>(emptyList()) }
    var isClassmatesLoading by remember { mutableStateOf(false) }
    var seatMapAssignments by remember { mutableStateOf<List<DeskAssignment>>(emptyList()) }
    var isSeatMapLoading by remember { mutableStateOf(false) }
    // La mappa e' stata letta almeno una volta in questa sessione (anche se vuota).
    var seatMapLoadedOnce by remember { mutableStateOf(false) }
    var seatMapError by remember { mutableStateOf<String?>(null) }
    var seatMapRefreshTrigger by remember { mutableStateOf(0) }
    var isPreferencesOpen by remember { mutableStateOf(false) }
    // Quanti compagni hanno gia' votato le preferenze (null = non ancora caricato).
    var preferencesProgress by remember {
        mutableStateOf<circolareplus.data.remote.dto.PreferencesProgressDto?>(null)
    }
    var seatMapMode by rememberSaveable { mutableStateOf("MAP") } // "MAP" | "VOTE_PREFERENCES"
    val socialVotes = remember { mutableStateMapOf<String, SocialPreferenceScore>() }
    var proposalOptions by remember { mutableStateOf<List<SeatMapProposal>>(emptyList()) }
    // Modalità richiesta per l'ultima generazione: serve alla schermata delle proposte per
    // etichettare correttamente "banchi da due/tre" anche quando nessun banco della disposizione
    // calcolata finisce per avere un terzo occupante (classe piccola, coppie vietate che spezzano
    // i trii), caso in cui dedurlo dai soli dati (studentCId != null) darebbe "banchi da due" pur
    // avendo l'utente scelto i trii.
    var lastRequestedSeatsPerDesk by remember { mutableStateOf(SeatMapOptimizer.SEATS_PER_DESK_PAIR) }
    var isGeneratingProposals by remember { mutableStateOf(false) }
    var seatMapActionError by remember { mutableStateOf<String?>(null) }
    var isExportingSeatMapPdf by remember { mutableStateOf(false) }
    // Disposizione in editing manuale (dopo aver scelto una delle 3 proposte) e copia originale
    // per il pulsante "Ripristina Proposta Algoritmo"; null quando l'editor non è aperto.
    var editingSeatMapProposal by remember { mutableStateOf<List<DeskAssignment>?>(null) }
    val lastEditorAssignments = remember { arrayOf<List<DeskAssignment>>(emptyList()) }
    var originalSeatMapProposal by remember { mutableStateOf<List<DeskAssignment>?>(null) }
    // Input dell'ultimo calcolo dell'ottimizzatore, tenuti in stato per poter ricalcolare live il
    // punteggio nell'editor manuale senza rifare le chiamate di rete (ratings/matrice/storico).
    var seatMapOptimizerProfiles by remember { mutableStateOf<Map<String, StudentProfile>>(emptyMap()) }
    var seatMapOptimizerRatings by remember { mutableStateOf<Map<String, RepresentativeRating>>(emptyMap()) }
    var seatMapOptimizerDisciplinePairs by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) }
    var seatMapOptimizerSocialMap by remember { mutableStateOf<Map<Pair<String, String>, SocialPreferenceScore>>(emptyMap()) }
    var seatMapOptimizerHistory by remember { mutableStateOf<List<SeatMapHistoryRecord>>(emptyList()) }
    var seatMapOptimizerWeights by remember { mutableStateOf(OptimizerWeights()) }
    var seatMapOptimizerIsSmallClass by remember { mutableStateOf(false) }

    // --- Stato Sondaggi Interrogazioni ------------------------------------------------------
    var currentPoll by remember { mutableStateOf<PollDetailDto?>(null) }
    var isPollLoading by remember { mutableStateOf(false) }
    var pollError by remember { mutableStateOf<String?>(null) }
    var pollsRefreshTrigger by remember { mutableStateOf(0) }
    var showCreatePollDialog by remember { mutableStateOf(false) }
    var isCreatingPoll by remember { mutableStateOf(false) }
    var showPollHistory by remember { mutableStateOf(false) }
    // 0 = sondaggi interrogazioni, 1 = sondaggi a ordinamento.
    var pollsSection by rememberSaveable { mutableStateOf(0) }
    var rankingPolls by remember { mutableStateOf<List<circolareplus.data.remote.dto.RankingPollDto>>(emptyList()) }
    var rankingTotalStudents by remember { mutableStateOf(0) }
    var isRankingLoading by remember { mutableStateOf(false) }
    var rankingError by remember { mutableStateOf<String?>(null) }
    var rankingRefreshTrigger by remember { mutableStateOf(0) }
    var submittingRankingPollId by remember { mutableStateOf<String?>(null) }
    var showCreateRankingPollDialog by remember { mutableStateOf(false) }
    var isCreatingRankingPoll by remember { mutableStateOf(false) }
    var allPolls by remember { mutableStateOf<List<circolareplus.data.remote.dto.PollSummaryDto>>(emptyList()) }
    var expandedResultsPollId by remember { mutableStateOf<String?>(null) }
    var isLoadingPollResults by remember { mutableStateOf(false) }
    var pollResultsError by remember { mutableStateOf<String?>(null) }

    // Avanzamento della compilazione lato classe (quanti hanno inviato, scadenza, se è scaduto).
    // Arriva dalla stessa risposta del dettaglio, letta con un secondo DTO.
    var pollProgress by remember { mutableStateOf<circolareplus.data.remote.dto.PollProgressDto?>(null) }
    var isSubmittingPoll by remember { mutableStateOf(false) }
    var pollAssignments by remember { mutableStateOf<List<circolareplus.data.remote.dto.PollAssignmentDto>>(emptyList()) }
    // Sondaggi per cui questo studente ha già premuto "Invia le mie scelte" (solo locale, vedi
    // LocalSettingsManager.isPollSubmitted).
    var submittedPollIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Stato del pulsante "Aggiungi tutte le date al calendario" nello Storico sondaggi.
    var isAddingPollToCalendar by remember { mutableStateOf(false) }
    var pollCalendarMessage by remember { mutableStateOf<String?>(null) }
    var pollCalendarMessageIsError by remember { mutableStateOf(false) }

    /**
     * Manda una domanda all'assistente globale e appende la risposta alla conversazione.
     *
     * Tutto il lavoro sta qui e non nella schermata: la schermata mostra i messaggi e basta,
     * cosi' uscire dalla chat non annulla la domanda in corso (vedi il commento sullo stato).
     *
     * La conoscenza passata all'assistente e' fatta di due pezzi: quella gia' in memoria in
     * questa schermata (circolari con le loro analisi, calendario, bacheca, compagni), che e' la
     * stessa che l'utente vede a video, e quella caricata a parte la prima volta
     * ([AppContainer.assistantKnowledgeLoader], filtrata per ruolo).
     */
    /**
     * Archivia la conversazione in corso sul dispositivo (mai sul server: vedi
     * [circolareplus.data.local.LocalSettingsManager.listAssistantConversations]).
     *
     * Si chiama a ogni messaggio, non all'uscita dalla schermata: l'app puo' essere chiusa dal
     * sistema mentre il modello sta ancora rispondendo, e la conversazione che si perderebbe e'
     * proprio quella che serviva.
     */
    fun saveAssistantConversation() {
        val messages = assistantMessages.toList()
        // Niente domande dell'utente = nessuna conversazione da ricordare (es. si e' aperta la
        // chat, si e' letto il benvenuto e si e' usciti).
        if (messages.none { it.author == AssistantAuthor.USER }) return
        val now = currentTimeMillis()
        val existing = assistantConversations.firstOrNull { it.id == assistantConversationId }
        AppContainer.settings.saveAssistantConversation(
            AssistantConversation(
                id = assistantConversationId,
                title = existing?.title ?: AssistantConversation.titleFrom(messages),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
                messages = messages
            )
        )
        assistantConversations = AppContainer.settings.listAssistantConversations()
    }

    /**
     * Aggiunge [message] alla conversazione [conversationId], che puo' non essere piu' quella
     * aperta: se l'utente ha iniziato una nuova chat mentre il modello rispondeva, la risposta
     * va comunque nel filo da cui e' partita la domanda, aggiornandone la copia archiviata.
     */
    fun appendAssistantMessage(conversationId: String, message: AssistantMessage) {
        if (conversationId == assistantConversationId) {
            assistantMessages += message
            saveAssistantConversation()
            return
        }
        val stored = AppContainer.settings.listAssistantConversations()
            .firstOrNull { it.id == conversationId }
            // Cancellata nel frattempo dallo storico: la risposta non ha piu' dove andare.
            ?: return
        AppContainer.settings.saveAssistantConversation(
            stored.copy(
                updatedAtMillis = currentTimeMillis(),
                messages = stored.messages + message
            )
        )
        assistantConversations = AppContainer.settings.listAssistantConversations()
    }

    fun askAssistant(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || assistantConversationId in pendingAssistantConversations) return

        // La conversazione a cui appartiene questa domanda, fissata adesso: quando la risposta
        // arriva `assistantConversationId` potrebbe essere cambiato.
        val conversationId = assistantConversationId

        assistantMessageCounter++
        assistantMessages += AssistantMessage(
            id = "q${currentTimeMillis()}-$assistantMessageCounter",
            author = AssistantAuthor.USER,
            text = trimmed
        )
        // La cronologia che vede il modello non deve contenere la domanda appena fatta: quella
        // gli arriva a parte, come domanda corrente.
        val history = assistantMessages.dropLast(1).toList()
        pendingAssistantConversations += conversationId
        saveAssistantConversation()

        coroutineScope.launch {
            try {
                val dynamic = assistantDynamic
                    ?: AppContainer.assistantKnowledgeLoader
                        .load(currentUser?.role ?: UserRole.STUDENT)
                        .also { assistantDynamic = it }

                // La bacheca si carica solo entrando in Classe (o dalla Home se vuota): se in chat
                // si chiede delle proposte prima che sia arrivata, la si legge adesso. Senza, il
                // modello leggeva "nessuna proposta" e rispondeva che non risulta niente.
                var boardUnavailable: String? = null
                val boardProposals = proposals.ifEmpty {
                    try {
                        AppContainer.proposalsRepository.listProposals().also { proposals = it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        boardUnavailable = "${circolareplus.ai.assistant.AssistantBoard.UNAVAILABLE_LABEL}: " +
                            "non caricata (${e.message ?: e::class.simpleName})"
                        emptyList()
                    }
                }

                val reply = AppContainer.newAssistant().ask(
                    question = trimmed,
                    history = history,
                    knowledge = AssistantKnowledge(
                        todayIso = circolareplus.util.today().toIso(),
                        user = currentUser,
                        profile = currentProfile,
                        classmates = classmates,
                        circulars = circulars,
                        classifications = classifications.toMap(),
                        calendarEvents = calendarEvents,
                        proposals = boardProposals,
                        dynamic = boardUnavailable?.let { dynamic.copy(unavailable = dynamic.unavailable + it) } ?: dynamic
                    )
                )

                assistantMessageCounter++
                appendAssistantMessage(
                    conversationId,
                    AssistantMessage(
                        id = "a${currentTimeMillis()}-$assistantMessageCounter",
                        author = AssistantAuthor.ASSISTANT,
                        text = reply.text,
                        sources = reply.sources,
                        isError = reply.isError,
                        modelLabel = reply.modelLabel
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                assistantMessageCounter++
                appendAssistantMessage(
                    conversationId,
                    AssistantMessage(
                        id = "e${currentTimeMillis()}-$assistantMessageCounter",
                        author = AssistantAuthor.ASSISTANT,
                        text = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}",
                        isError = true
                    )
                )
            } finally {
                // Nel `finally` perche' vale per tutti e tre gli esiti: risposta, errore
                // riportato dall'assistente ed eccezione inattesa.
                pendingAssistantConversations -= conversationId
            }
        }
    }

    fun reloadCalendar() { calendarRefreshTrigger++ }
    fun reloadProposals() { proposalsRefreshTrigger++ }

    // Destinazione comune per una notifica (categoria di NotificationKind, o
    // NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES): usata sia dal tocco sulla campanella in-app
    // sia — tramite PendingDeepLink più sotto — dal tocco su una notifica di sistema (push),
    // così le due strade portano esattamente nello stesso posto invece di duplicare la logica.
    fun navigateForNotificationCategory(category: String) {
        isInNotificationsScreen = false
        isInSearchScreen = false
        isInSettingsScreen = false
        isInBackgroundDebugScreen = false
        isInClassRosterScreen = false
        isInProfileScreen = false
        when (category) {
            NotificationKind.CIRCULARS.key -> {
                classSection = ClassSection.CIRCULARS
                selectedTab = MainTab.CLASS
            }
            NotificationKind.BOARD.key -> {
                classSection = ClassSection.BOARD
                selectedTab = MainTab.CLASS
            }
            NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES -> {
                selectedTab = MainTab.SEATMAP
                seatMapMode = "VOTE_PREFERENCES"
            }
            NotificationKind.SEATMAP.key -> {
                selectedTab = MainTab.SEATMAP
                seatMapMode = "MAP"
            }
            NotificationKind.POLLS.key -> {
                pollsSection = 0
                selectedTab = MainTab.POLLS
            }
            NOTIFICATION_CATEGORY_RANKING_POLLS -> {
                pollsSection = 1
                selectedTab = MainTab.POLLS
            }
            NotificationKind.CALENDAR.key -> {
                selectedTab = MainTab.CALENDAR
            }
            // Categoria sconosciuta/vuota (es. push generica senza tipo): nessuna destinazione
            // certa, si resta dove si è.
            else -> {}
        }
    }

    // Notifica di sistema toccata (push, non campanella in-app): PendingDeepLink viene scritta
    // dal codice nativo di piattaforma (Android: MainActivity; iOS: AppDelegate) sia all'avvio a
    // freddo sia ad app già in esecuzione in background. snapshotFlow, non un semplice
    // LaunchedEffect(valore), perché deve reagire anche quando il valore cambia mentre questa
    // composable è già attiva (app riportata in primo piano dal tocco), non solo alla prima
    // composizione.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { circolareplus.ui.PendingDeepLink.category }
            .collect { category ->
                if (category != null) {
                    navigateForNotificationCategory(category)
                    circolareplus.ui.PendingDeepLink.category = null
                }
            }
    }

    LaunchedEffect(selectedTab, calendarRefreshTrigger) {
        // Carica il calendario solo se:
        // 1. È stato chiesto un refresh nuovo (calendarRefreshTrigger cambiato da quello già servito)
        // 2. Non abbiamo ancora dati e siamo in Calendario o in Home
        // Prima bastava `calendarRefreshTrigger > 0`: dopo il primo refresh, ogni ingresso nella tab
        // ricaricava tutto coprendo la griglia con lo spinner, da qui il "ritardo" nel comparire.
        val refreshRequested = calendarRefreshTrigger != handledCalendarRefresh
        val shouldLoadCalendar = (refreshRequested && (selectedTab == MainTab.CALENDAR || selectedTab == MainTab.HOME)) ||
            (calendarEvents.isEmpty() && !isCalendarLoading && (selectedTab == MainTab.CALENDAR || selectedTab == MainTab.HOME))
        if (shouldLoadCalendar) {
            handledCalendarRefresh = calendarRefreshTrigger
            // Lo spinner a tutto schermo solo al primo caricamento: con dati già presenti il
            // ricaricamento avviene in silenzio e la griglia resta visibile.
            val showSpinner = calendarEvents.isEmpty()
            if (showSpinner) isCalendarLoading = true
            calendarError = null
            try {
                calendarEvents = StartupPrefetch.take<List<CalendarEvent>>("calendar")
                    ?: AppContainer.calendarRepository.listEvents()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (showSpinner) calendarError = "Impossibile caricare il calendario. Controlla la connessione."
            } finally {
                if (showSpinner) isCalendarLoading = false
            }
        }
        // Anche qui, non solo alla tab Mappa Posti: il dettaglio di un evento con destinatari
        // specifici deve poter mostrare i loro nomi anche se il rappresentante non ha ancora
        // aperto la Mappa Posti in questa sessione.
        if (selectedTab == MainTab.CALENDAR && classmates.isEmpty() && !isClassmatesLoading) {
            isClassmatesLoading = true
            try {
                classmates = StartupPrefetch.take<List<User>>("students")
                    ?: AppContainer.usersRepository.listStudents()
            } catch (e: Exception) {
                // Non bloccante: il calendario resta usabile, i nomi dei destinatari specifici
                // ripiegano su un placeholder finché il caricamento non riesce.
            } finally {
                isClassmatesLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab, circularsRefreshTrigger) {
        if ((selectedTab == MainTab.CLASS || selectedTab == MainTab.HOME) && circulars.isEmpty() && !isCircularsLoading) {
            isCircularsLoading = true
            circularsError = null
            try {
                circulars = AppContainer.circularsRepository.listCirculars()
                noteNovelties(freshCirculars = circulars)
                // Nota: la classificazione AI (client-side, con la chiave personale dello
                // studente) richiede il testo estratto dal PDF della circolare. L'estrazione
                // testo PDF multipiattaforma è un modulo separato non ancora implementato:
                // finché non è pronto, le circolari restano visibili come "Da classificare"
                // invece di mostrare un'etichetta finta.
            } catch (e: Exception) {
                circularsError = "Impossibile caricare le circolari. Controlla la connessione."
            } finally {
                isCircularsLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab, proposalsRefreshTrigger) {
        val shouldLoadProposals = selectedTab == MainTab.CLASS ||
            (selectedTab == MainTab.HOME && proposals.isEmpty() && !isProposalsLoading)
        if (shouldLoadProposals) {
            isProposalsLoading = true
            proposalsError = null
            try {
                // Le due richieste sono indipendenti, e prima partivano in sequenza: il tempo di
                // rete di listUnlockRequests() si sommava a quello di listProposals() invece di
                // sovrapporsi, rallentando l'ingresso in bacheca di un'attesa che non serviva.
                coroutineScope {
                    val proposalsDeferred = async {
                        StartupPrefetch.take<List<Proposal>>("proposals")
                            ?: AppContainer.proposalsRepository.listProposals()
                    }
                    // Un errore qui non deve far sparire la bacheca: le richieste sono un di più. Lo
                    // chiedono tutti, perché è la risposta a dire se si è la Guardia della classe.
                    val unlockDeferred = async {
                        try {
                            AppContainer.proposalsRepository.listUnlockRequests()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    proposals = proposalsDeferred.await()
                    noteNovelties(freshProposals = proposals)
                    val state = unlockDeferred.await()
                    if (state != null) {
                        isSecurityGuard = state.canSign && !isRepresentative
                        unlockRequests = state.requests
                    } else {
                        unlockRequests = emptyList()
                    }
                }
            } catch (e: Exception) {
                proposalsError = "Impossibile caricare la bacheca. Controlla la connessione."
            } finally {
                isProposalsLoading = false
            }
        }
    }

    // Le tre letture della Mappa Posti (compagni, mappa attuale, finestra preferenze) partono
    // insieme invece che una dopo l'altra: erano tre giri di rete in fila dietro lo spinner a
    // piena schermata, il secondo abbondante che si vedeva entrando nella tab.
    suspend fun loadSeatMapData(reportErrors: Boolean = true): Unit = coroutineScope {
        val classmatesDeferred = if (classmates.isEmpty()) {
            async {
                runCatching {
                    StartupPrefetch.take<List<User>>("students") ?: AppContainer.usersRepository.listStudents()
                }
            }
        } else {
            null
        }
        val mapDeferred = async { runCatching { AppContainer.seatMapRepository.getCurrentSeatMap() ?: emptyList() } }
        val configDeferred = async { runCatching { AppContainer.preferencesRepository.getConfig() } }

        classmatesDeferred?.await()?.let { result ->
            result.onSuccess { classmates = it }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (reportErrors) seatMapError = "Impossibile caricare l'elenco dei compagni."
                }
        }
        val map = mapDeferred.await()
        val config = configDeferred.await()
        map.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        config.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val freshMap = map.getOrNull()
        val freshConfig = config.getOrNull()
        if (reportErrors && (freshMap == null || freshConfig == null)) {
            seatMapError = "Impossibile caricare la mappa posti attuale."
        }
        if (freshMap != null) seatMapAssignments = freshMap
        if (freshConfig != null) isPreferencesOpen = freshConfig.preferencesOpen
        if (freshMap != null && freshConfig != null) {
            seatMapLoadedOnce = true
            noteNovelties(freshSeatMap = freshMap, freshPreferencesOpen = freshConfig.preferencesOpen)
        }
    }

    // Ingresso nella tab Mappa Posti: carica compagni, mappa attuale e stato finestra preferenze.
    // Prima era un flusso aperto solo dalla Home (isInSeatMapScreen); ora è una tab vera e
    // propria della bottom bar, quindi si aggancia direttamente al cambio di selectedTab.
    LaunchedEffect(selectedTab, seatMapRefreshTrigger) {
        if (selectedTab == MainTab.SEATMAP) {
            seatMapMode = "MAP"
            seatMapError = null
            // Lo spinner a piena schermata solo la prima volta in assoluto: dopo, la mappa gia'
            // in memoria resta a schermo e si aggiorna sotto. Prima valeva "mappa vuota", quindi
            // senza una mappa pubblicata lo spinner tornava a ogni ingresso nella tab.
            val showSpinner = !seatMapLoadedOnce
            if (showSpinner) isSeatMapLoading = true
            try {
                loadSeatMapData()
            } finally {
                if (showSpinner) isSeatMapLoading = false
            }
        }
    }

    // La mappa si legge anche in anticipo, pochi secondi dopo l'ingresso nell'app: cosi' la prima
    // apertura della tab la trova gia' pronta invece di aspettare la rete.
    LaunchedEffect(user.id) {
        delay(600L)
        if (!seatMapLoadedOnce) {
            try {
                loadSeatMapData(reportErrors = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si riprova entrando nella tab.
            }
        }
    }

    // Avanzamento della votazione: finche' la finestra e' aperta e si e' sulla Mappa Posti si
    // rilegge ogni 20 secondi, cosi' il Rappresentante vede salire il conteggio senza dover
    // uscire e rientrare. Fuori dalla tab (o a finestra chiusa) il ciclo si ferma da solo.
    LaunchedEffect(selectedTab, isPreferencesOpen, seatMapMode) {
        if (selectedTab != MainTab.SEATMAP) return@LaunchedEffect
        if (!isPreferencesOpen && !isRepresentative) return@LaunchedEffect
        while (true) {
            circolareplus.platform.awaitForeground()
            try {
                preferencesProgress = AppContainer.preferencesRepository.progress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Informazione accessoria: se non arriva, la mappa funziona lo stesso.
            }
            if (!isPreferencesOpen) break
            kotlinx.coroutines.delay(20_000L)
        }
    }

    // Ingresso nella votazione preferenze: precarica i voti già espressi dallo studente.
    LaunchedEffect(seatMapMode) {
        if (seatMapMode == "VOTE_PREFERENCES") {
            // Notifica di apertura votazione preferenze
            if (AppContainer.settings.isNotificationKindEnabled(NotificationKind.SEATMAP.key)) {
                AppContainer.settings.addNotification(
                    "Votazione Preferenze Aperta",
                    "Esprimi i tuoi voti sulle preferenze di seduta",
                    NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES
                )
                notificationLog = AppContainer.settings.listNotifications()
            }

            try {
                val myVotes = AppContainer.preferencesRepository.myVotes()
                socialVotes.clear()
                myVotes.votes.forEach { v ->
                    socialVotes[v.toStudentId] = SocialPreferenceScore.fromValue(v.score)
                }
            } catch (e: Exception) {
                seatMapActionError = "Impossibile caricare i tuoi voti precedenti."
            }
        }
    }

    // Apertura del dettaglio di una circolare non ancora classificata: la classifica subito
    // (con priorità sul ciclo in background qui sotto, che nel frattempo potrebbe già essere al
    // lavoro su di lei — classifyCircularIfNeeded evita il doppio lavoro).
    LaunchedEffect(selectedCircularForDetail ?: paneCircular) {
        val circular = selectedCircularForDetail ?: paneCircular
        if (circular != null && !classifications.containsKey(circular.number)) {
            // Fuori dallo scope di questo effetto: l'effetto viene cancellato appena si esce dal
            // dettaglio, e con lui l'analisi (minuti di lavoro sul telefono buttati). Cosi'
            // prosegue, e la lista mostra "Analisi in corso".
            // Fermata a mano: riparte solo col tasto "Analizza", non riaprendo la circolare.
            if (circular.number !in stoppedByUser && circular.number !in awaitingServer) {
                launchClassification(circular)
            }
        }
    }

    // Il dettaglio di una circolare deve sapere cosa c'e' gia' in calendario, altrimenti propone
    // come "da aggiungere" scadenze che ci sono gia' (evento doppio). Il calendario si caricava
    // solo aprendo la sua tab, e dopo un'aggiunta non si ricaricava se si era altrove: si rilegge
    // ad ogni apertura del dettaglio e ad ogni aggiunta (calendarRefreshTrigger).
    LaunchedEffect((selectedCircularForDetail ?: paneCircular)?.number, calendarRefreshTrigger) {
        if (selectedCircularForDetail != null || paneCircular != null) {
            try {
                calendarEvents = AppContainer.calendarRepository.listEvents()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Senza rete resta l'elenco che c'e': al peggio il tasto compare.
            }
        }
    }

    // I riassunti gia' pronte sul server (fatti da altri, da un altro telefono o dal server
    // stesso con Gemini) si scaricano tutti insieme con una sola richiesta, e poi solo quelli
    // cambiati dall'ultima volta. Prima si chiedeva circolare per circolare una volta sola:
    // chi era dentro l'app non vedeva il riassunto appena fatto da un compagno e continuava ad
    // analizzare il suo finche' non riapriva l'app. Ogni 60 secondi, ogni 15 mentre un'analisi
    // e' in corso su questo telefono (un riassunto arrivato la ferma, vedi adoptServerAnalysis).
    var serverAnalysesCursor by remember { mutableStateOf<String?>(null) }
    suspend fun syncServerAnalyses() {
        val sync = try {
            AppContainer.circularsRepository.getAnalysesSince(serverAnalysesCursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // Rete assente o server vecchio senza questa rotta: si riprova al giro dopo.
        }
        serverAnalysesCursor = sync.cursor
        serverSyncInfo = sync.copy(analyses = emptyList())
        sync.analyses.forEach { adoptServerAnalysis(it) }
        // Se il server ha smesso di provarci (o non ha piu' la chiave), le circolari lunghe in
        // attesa tornano all'AI del telefono: si possono analizzare col tasto "Analizza".
        val now = currentTimeMillis()
        val noLongerAwaited = awaitingServer.filter {
            !sync.serverWillSummarize(it) ||
                now - (awaitingServerSince[it] ?: now) >= SERVER_WAIT_MILLIS
        }
        if (noLongerAwaited.isNotEmpty()) awaitingServer.removeAll(noLongerAwaited)
    }
    LaunchedEffect(user.id) {
        // Giri da 15 secondi: la lettura vera parte ogni 4 giri, o a ogni giro se c'e'
        // un'analisi in corso (anche iniziata a meta' di un'attesa lunga).
        var ticksSinceSync = Int.MAX_VALUE
        while (isActive) {
            circolareplus.platform.awaitForeground()
            if (ticksSinceSync >= 4 || inFlightClassification.isNotEmpty()) {
                syncServerAnalyses()
                ticksSinceSync = 0
            }
            delay(15_000L)
            ticksSinceSync++
        }
    }
    LaunchedEffect(user.id) {
        circolareplus.push.DataRefreshEvents.requests.collect { syncServerAnalyses() }
    }
    // Il tasto Stop della notifica di Android.
    LaunchedEffect(user.id) {
        AnalysisActivity.stopRequests.collect { number -> stopClassification(number) }
    }

    // Mentre si è dentro l'app, continua a classificare in background le circolari rimaste
    // indietro (quelle che l'anticipo durante il caricamento non ha fatto in tempo a coprire):
    // così, quando l'utente arriva ad aprirle, sono già pronte invece di dover aspettare lì per
    // lì. Una alla volta (non in parallelo) per non consumare la quota della chiave AI personale
    // più in fretta del necessario, con una pausa breve tra un tentativo e l'altro.
    LaunchedEffect(Unit) {
        // Quante volte di fila, in questo ciclo, il provider cloud ha fallito ed e' scattata
        // l'escalation al modello locale: e' quello che scalda il telefono anche con il
        // provider cloud selezionato (vedi il commento su ChainedAiClassifier.escalateToSecondary).
        // Oltre la soglia si smette di scaricare il lavoro sul locale per il resto del ciclo: le
        // circolari restanti prendono il fallback euristico, istantaneo, e l'utente puo' sempre
        // forzare l'analisi completa aprendo la singola circolare e premendo "Rianalizza".
        var consecutiveLocalFallbacks = 0
        while (isActive) {
            circolareplus.platform.awaitForeground()
            val next = circulars.sortedByDescending { it.number }
                .firstOrNull {
                    it.number !in classifications && it.number !in inFlightClassification &&
                        it.number !in stoppedByUser
                }
            // Con l'AI locale questo ciclo non gira affatto, e non e' una prudenza: e' la
            // ragione principale per cui aprire una circolare sembrava non finire mai. Il motore
            // e' uno solo e le richieste sono in coda su un mutex, quindi la circolare appena
            // aperta finiva **dietro** a quella che il ciclo aveva gia' cominciato in sottofondo,
            // e doveva aspettare che quella finisse prima ancora di iniziare. Con una
            // generazione da decine di secondi, l'attesa vista dall'utente raddoppiava o peggio.
            //
            // In rete l'anticipo ha senso — un secondo a circolare, e trovarle gia' pronte e'
            // meglio. Sul telefono no: si classifica quello che si apre, quando lo si apre.
            if (AppContainer.isUsingLocalAiFirst()) {
                delay(3000L)
            } else if (next != null) {
                // Come Job a parte e non chiamata diretta: cosi' anche questa si ferma col tasto Stop.
                launchClassification(
                    next,
                    allowLocalFallback = consecutiveLocalFallbacks < 2
                ).join()
                val usedLocal = classifications[next.number]?.modelLabel?.startsWith("AI locale") == true
                consecutiveLocalFallbacks = if (usedLocal) consecutiveLocalFallbacks + 1 else 0
            } else {
                delay(3000L)
            }
        }
    }

    // Circolari: rilettura dinamica dell'elenco.
    //
    // Prima l'elenco si caricava una volta sola (quando era vuoto) e una circolare nuova, anche
    // con la notifica gia' arrivata, restava invisibile finche' non si usciva e rientrava.
    // Ora si rilegge ogni minuto, subito quando arriva un push o l'app torna in primo piano
    // (DataRefreshEvents), e si aggiorna quello che si vede solo se e' davvero cambiato. La lista
    // usa il numero come chiave, quindi le voci gia' a schermo non saltano.
    suspend fun refreshCirculars() {
        if (isCircularsLoading) return
        try {
            val fresh = AppContainer.circularsRepository.listCirculars()
            if (fresh != circulars) circulars = fresh
            noteNovelties(freshCirculars = fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Rete assente: si riprova al giro dopo.
        }
    }
    LaunchedEffect(user.id) {
        while (isActive) {
            delay(60_000L)
            circolareplus.platform.awaitForeground()
            refreshCirculars()
        }
    }
    LaunchedEffect(user.id) {
        circolareplus.push.DataRefreshEvents.requests.collect { refreshCirculars() }
    }

    // Controllo periodico delle novità mentre l'app è aperta.
    //
    // Senza, una novità si scopriva solo entrando nella sua sezione: la campanella restava vuota
    // per tutto il tempo in cui si stava, per dire, sul calendario. Il push FCM coprirebbe il
    // caso, ma vale solo quando il server manda davvero un messaggio e non è configurato finché
    // non esiste il progetto Firebase.
    //
    // Circolari escluse (le gestisce il rinfresco dinamico qui sopra). Le altre liste non si
    // aggiornano a schermo: questo giro si limita a rilevare e a scrivere in
    // campanella. Sovrascrivere quello che l'utente sta guardando mentre lo guarda è il tipo di
    // cosa che fa "saltare" una lista sotto il dito.
    LaunchedEffect(user.id) {
        while (isActive) {
            delay(5 * 60 * 1000L)
            circolareplus.platform.awaitForeground()
            try {
                noteNovelties(freshProposals = AppContainer.proposalsRepository.listProposals())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Idem.
            }
            try {
                noteNovelties(
                    freshSeatMap = AppContainer.seatMapRepository.getCurrentSeatMap(),
                    freshPreferencesOpen = AppContainer.preferencesRepository.getConfig().preferencesOpen
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Idem.
            }
        }
    }

    LaunchedEffect(isInPollsScreen, pollsRefreshTrigger) {
        if (isInPollsScreen) {
            isPollLoading = true
            pollError = null
            try {
                val polls = StartupPrefetch.take<List<circolareplus.data.remote.dto.PollSummaryDto>>("polls")
                    ?: AppContainer.pollsRepository.listPolls()
                allPolls = polls

                // Notifica per nuovo sondaggio pubblicato
                val publishedPolls = polls.filter { it.isPublished }
                // Un sondaggio con le assegnazioni già calcolate (tutti hanno inviato, l'algoritmo
                // è già girato lato server) è chiuso: deve sparire da "Sondaggio" e restare visibile
                // solo nello Storico, altrimenti resterebbe "aperto" all'infinito anche a risultato
                // già pronto.
                // Anche quelli chiusi in anticipo dal Rappresentante ("Chiudi" = calcolo forzato):
                // prima restavano "aperti" perché non avevano tutti gli invii.
                val openPolls = publishedPolls.filterNot { it.isCalculated }
                if (publishedPolls.isNotEmpty()) {
                    // Si confrontano le date di creazione (stesso formato del server, ordinabili come
                    // testo). Prima la data si confrontava con l'id dell'ultimo visto, un UUID.
                    val newest = publishedPolls.maxOf { it.createdAt }
                    val lastSeen = AppContainer.settings.lastSeenPollCreatedAt
                    if (lastSeen.isBlank()) {
                        AppContainer.settings.lastSeenPollCreatedAt = newest
                    } else {
                        val newPolls = publishedPolls.filter { it.createdAt > lastSeen }
                        if (newPolls.isNotEmpty()) {
                            newPolls.forEach { poll ->
                                if (AppContainer.settings.isNotificationKindEnabled(NotificationKind.POLLS.key)) {
                                    AppContainer.settings.addNotification("Nuovo sondaggio: ${poll.subject}", "Apertura prenotazioni", NotificationKind.POLLS.key)
                                }
                            }
                            AppContainer.settings.lastSeenPollCreatedAt = newest
                            notificationLog = AppContainer.settings.listNotifications()
                        }
                    }
                }

                val target = openPolls.firstOrNull() ?: polls.firstOrNull { !it.isCalculated }
                if (target != null) {
                    val loaded = AppContainer.pollsRepository.getPollWithProgress(target.id)
                    currentPoll = loaded.detail
                    pollProgress = loaded.progress
                    AppContainer.settings.setPollSubmitted(target.id, loaded.progress.hasSubmitted)
                } else {
                    currentPoll = null
                    pollProgress = null
                }
                submittedPollIds = polls
                    .filter { AppContainer.settings.isPollSubmitted(it.id) }
                    .map { it.id }
                    .toSet()
            } catch (e: Exception) {
                pollError = "Impossibile caricare i sondaggi. Controlla la connessione."
            } finally {
                isPollLoading = false
            }
        }
    }

    // Espande/carica i risultati (assegnazioni) di un sondaggio dello storico: li calcola al volo
    // con l'algoritmo già esistente (`assignments/run`) invece di richiedere un passaggio separato.
    // Scaricati appena si apre la tab Sondaggi, non solo passando a "Ordinamento": prima il primo
    // passaggio mostrava la rotellina finche' la lista non arrivava (il "micro periodo di attesa").
    // Tornando su "Ordinamento" si ricarica in silenzio, con i dati gia' a schermo.
    LaunchedEffect(isInPollsScreen, pollsSection, rankingRefreshTrigger) {
        if (isInPollsScreen) {
            // Lo spinner solo alla prima lettura: dopo un invio o una chiusura si ricarica in
            // silenzio, senza far sparire le card sotto il dito.
            isRankingLoading = rankingPolls.isEmpty()
            rankingError = null
            try {
                val response = StartupPrefetch.take<circolareplus.data.remote.dto.RankingPollsListResponseDto>("rankingPolls")
                    ?: AppContainer.rankingPollsRepository.listPolls()
                rankingPolls = response.polls
                rankingTotalStudents = response.totalStudents
            } catch (e: Exception) {
                rankingError = "Impossibile caricare i sondaggi. Controlla la connessione."
            } finally {
                isRankingLoading = false
            }
        }
    }

    LaunchedEffect(expandedResultsPollId) {
        val pollId = expandedResultsPollId
        if (pollId != null) {
            isLoadingPollResults = true
            pollResultsError = null
            try {
                // Nello storico ci sono solo sondaggi già calcolati: basta rileggere le
                // assegnazioni. Prima si rifaceva girare l'algoritmo a ogni apertura, che
                // riassegnava i bonus sacrificio ogni volta e falliva (409) sui sondaggi chiusi
                // in anticipo senza tutti gli invii.
                pollAssignments = AppContainer.pollsRepository.getAssignments(pollId).assignments
            } catch (e: Exception) {
                pollResultsError = "Impossibile calcolare i risultati: ${e.message}"
            } finally {
                isLoadingPollResults = false
            }
        }
    }

    // Ingresso nella Scheda Classe: carica le valutazioni correnti (solo Rappresentante).
    LaunchedEffect(isInClassRosterScreen) {
        if (isInClassRosterScreen) {
            isClassRosterLoading = true
            classRosterError = null
            classRosterActionError = null
            try {
                classRosterEntries = AppContainer.ratingsRepository.listRatings()
            } catch (e: Exception) {
                classRosterError = "Impossibile caricare la scheda classe."
            } finally {
                isClassRosterLoading = false
            }
            // A parte: un server senza la rotta nuova non deve nascondere le valutazioni.
            try {
                disciplinePairs = AppContainer.ratingsRepository.listDisciplinePairs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disciplinePairs = emptyList()
            }
        }
    }

    // Ingresso nella schermata Notifiche: ricarica lo storico (elimina quelle scadute) e,
    // uscendo, segna tutto come letto (così il pallino sulla campanella sparisce).
    LaunchedEffect(isInNotificationsScreen) {
        if (isInNotificationsScreen) {
            notificationLog = AppContainer.settings.listNotifications()
        } else if (notificationLog.any { !it.read }) {
            AppContainer.settings.markAllNotificationsRead()
            notificationLog = AppContainer.settings.listNotifications()
        }
    }

    // Il foglio "Nuovo evento": in Glass e' il foglio di sistema (una finestra, si compone qui); in
    // Material e' un pannello dentro l'app che scende dall'alto (vedi in fondo, sopra le schermate).
    val addEventDialog: @Composable () -> Unit = {
    if (showAddEventDialog) {
        AddCalendarEventDialog(
            onDismiss = {
                showAddEventDialog = false
                addEventCloseRequested = false
                addEventInitialStep = EventCreationStep.MENU
                addEventInitialDateIso = null
            },
            closeRequested = addEventCloseRequested,
            initialStep = addEventInitialStep,
            initialDateIso = addEventInitialDateIso,
            onConfirm = { title, date, time, category, visibleToUserIds, notes ->
                fun create(force: Boolean) {
                    coroutineScope.launch {
                        try {
                            val response = AppContainer.calendarRepository.createEvent(
                                title = title,
                                eventDate = date,
                                startTime = time,
                                category = category,
                                visibleToUserIds = visibleToUserIds,
                                notes = notes,
                                force = force
                            )
                            if (response.warning != null) {
                                // Lo stesso evento c'e' gia' quel giorno: si chiede se aggiungerlo
                                // comunque, senza chiudere il modulo (prima si perdeva cio' che si era scritto).
                                pendingDuplicateRetry = { create(force = true) }
                                pendingDuplicateWarning = response.warning
                            } else {
                                // Il foglio scende con la sua animazione (poi chiama onDismiss). Intanto il
                                // calendario va sul giorno del nuovo evento e l'evento c'e' gia': si
                                // inserisce subito, senza aspettare la rilettura, cosi' la sua card entra
                                // mentre il foglio finisce di scendere. La rilettura poi lo riallinea.
                                addEventCloseRequested = true
                                val newId = response.id
                                if (newId != null && calendarEvents.none { it.id == newId }) {
                                    calendarEvents = calendarEvents + CalendarEvent(
                                        id = newId,
                                        title = circolareplus.util.compactClassLabels(title),
                                        date = date,
                                        time = time,
                                        category = category,
                                        isForAll = visibleToUserIds == null,
                                        createdByUserId = user.id,
                                        visibleToUserIds = visibleToUserIds,
                                        notes = notes
                                    )
                                }
                                calendarFocusDateIso = date
                                reloadCalendar()
                            }
                        } catch (e: Exception) {
                            calendarError = "Impossibile creare l'evento: ${e.message}"
                        }
                    }
                }
                create(force = false)
            }
        )
    }
    }
    if (AppTheme.isGlass) addEventDialog()

    if (pendingDuplicateWarning != null) {
        AlertDialog(
            onDismissRequest = { pendingDuplicateWarning = null; pendingDuplicateRetry = null },
            title = { Text("Evento già presente") },
            text = { Text(pendingDuplicateWarning ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    val retry = pendingDuplicateRetry
                    pendingDuplicateWarning = null
                    pendingDuplicateRetry = null
                    retry?.invoke()
                }) {
                    Text("Aggiungi comunque")
                }
            },
            dismissButton = {
                // Il modulo resta aperto: si puo' correggere titolo o data.
                TextButton(onClick = { pendingDuplicateWarning = null; pendingDuplicateRetry = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    // Eliminare un evento: la sua card esce chiudendosi (vedi removingEventIds in CalendarScreen),
    // poi parte la richiesta e la lista si aggiorna subito senza aspettare la rilettura. Se la
    // richiesta fallisce la card rientra e si dice perche'.
    fun deleteEventAnimated(event: CalendarEvent, startDelayMs: Long = 0L) {
        coroutineScope.launch {
            // Dal dettaglio: prima la pagina si richiude nella card, poi la card esce.
            if (startDelayMs > 0L) delay(startDelayMs)
            removingEventIds = removingEventIds + event.id
            delay(360L)
            try {
                AppContainer.calendarRepository.deleteEvent(event.id)
                calendarEvents = calendarEvents.filterNot { it.id == event.id }
                reloadCalendar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                calendarError = "Impossibile eliminare l'evento: ${e.message}"
            } finally {
                removingEventIds = removingEventIds - event.id
            }
        }
    }

    // "Apri la circolare" dal dettaglio di un evento inserito da AILA Assistant: stessa apertura
    // della Ricerca (circolare sopra le tab, nella sezione Circolari della tab Classe).
    val openCircularFromEvent: (Int) -> Unit = { number ->
        circulars.firstOrNull { it.number == number }?.let { circular ->
            eventDetailToShow = null
            selectedCircularForDetail = circular
            classSection = ClassSection.CIRCULARS
            selectedTab = MainTab.CLASS
        }
    }

    // Material: il dettaglio e' una pagina (vedi EventDetailPage, in fondo al livello delle
    // schermate). Il foglio dal basso resta solo in Liquid Glass.
    eventDetailToShow?.takeIf { AppTheme.isGlass }?.let { event ->
        EventDetailDialog(
            event = event,
            classmates = classmates,
            currentUser = user,
            onOpenCircular = openCircularFromEvent,
            onDismiss = { eventDetailToShow = null },
            // Il foglio e' gia' sceso quando arriva qui (vedi EventDetailDialog): si toglie e
            // l'eliminazione parte con la card che esce dal calendario.
            onDelete = {
                eventDetailToShow = null
                deleteEventAnimated(event)
            }
        )
    }

    if (showAddProposalDialog) {
        AddProposalDialog(
            onDismiss = { showAddProposalDialog = false },
            onConfirm = { title, description, category, isAnonymous ->
                coroutineScope.launch {
                    try {
                        AppContainer.proposalsRepository.createProposal(title, description, category, isAnonymous)
                        showAddProposalDialog = false
                        reloadProposals()
                    } catch (e: Exception) {
                        proposalsError = "Impossibile pubblicare la proposta: ${e.message}"
                    }
                }
            }
        )
    }

    if (showCreateRankingPollDialog) {
        CreateRankingPollDialog(
            isSubmitting = isCreatingRankingPoll,
            onDismiss = { showCreateRankingPollDialog = false },
            onConfirm = { question, options, audience ->
                coroutineScope.launch {
                    isCreatingRankingPoll = true
                    try {
                        AppContainer.rankingPollsRepository.createPoll(question, options, audience)
                        showCreateRankingPollDialog = false
                        rankingRefreshTrigger++
                    } catch (e: Exception) {
                        rankingError = "Impossibile creare il sondaggio: ${e.message}"
                        showCreateRankingPollDialog = false
                    } finally {
                        isCreatingRankingPoll = false
                    }
                }
            }
        )
    }

    if (showCreatePollDialog) {
        CreatePollDialog(
            isSubmitting = isCreatingPoll,
            onDismiss = { showCreatePollDialog = false },
            onConfirm = { subject, slots, audience ->
                coroutineScope.launch {
                    isCreatingPoll = true
                    try {
                        val created = AppContainer.pollsRepository.createPoll(subject, slots, audience)
                        val newId = created.id
                        if (newId != null) {
                            AppContainer.pollsRepository.publishPoll(newId)
                        }
                        showCreatePollDialog = false
                        pollsRefreshTrigger++
                    } catch (e: Exception) {
                        pollError = "Impossibile creare il sondaggio: ${e.message}"
                    } finally {
                        isCreatingPoll = false
                    }
                }
            }
        )
    }

    if (seatMapActionError != null) {
        AlertDialog(
            onDismissRequest = { seatMapActionError = null },
            title = { Text("Attenzione") },
            text = { Text(seatMapActionError ?: "") },
            confirmButton = {
                TextButton(onClick = { seatMapActionError = null }) { Text("Ho capito") }
            }
        )
    }

    // Push/pop "alla iOS" delle schermate a tutto schermo (Impostazioni, Sondaggi, Assistente,
    // ...): vivono in un livello SOPRA lo Scaffold, cosi' scorrono sopra anche la barra in basso
    // invece di farla sparire di colpo. La nuova entra da destra con una molla, le tab sotto
    // scivolano di un terzo a sinistra e si scuriscono; indietro l'inverso.
    val shellRoute = when {
        isInSettingsScreen && isInBackgroundDebugScreen -> ShellRoute.BACKGROUND_DEBUG
        isInSettingsScreen -> ShellRoute.SETTINGS
        isInClassRosterScreen -> ShellRoute.CLASS_ROSTER
        isInProfileScreen -> ShellRoute.PROFILE
        isInAssistantScreen -> ShellRoute.ASSISTANT
        isInSearchScreen -> ShellRoute.SEARCH
        isInNotificationsScreen -> ShellRoute.NOTIFICATIONS
        proposalOptions.isNotEmpty() && editingSeatMapProposal == null -> ShellRoute.SEATMAP_PROPOSALS
        editingSeatMapProposal != null -> ShellRoute.SEATMAP_EDITOR
        else -> ShellRoute.TABS
    }
    // Container transform (Material): a ogni cambio di schermata si annota da dove parte la
    // nuova (l'elemento appena toccato) o che si sta tornando indietro. Fatto qui, durante la
    // composizione e prima dell'AnimatedContent, cosi' la transizione lo trova gia' pronto.
    val previousShellRoute = remember { arrayOf(shellRoute) }
    // Container transform solo da elementi grandi (card, righe): da un avatar o un'icona di 44dp
    // la pagina che nasce da un puntino e ci rientra "lampeggia". Da li' si usa lo shared axis.
    // Eccezione: i pulsanti in alto a destra della Home (ricerca, notifiche, avatar), che hanno il
    // loro colore (buttonColor) e si aprono sempre alla Pixel, anche con l'interruttore spento.
    val minOriginWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    fun largeOrigin(origin: circolareplus.design.AilaTransformOrigin?) =
        origin?.takeIf {
            it.buttonColor != null ||
                (circolareplus.design.AILA_CONTAINER_TRANSFORM_ENABLED && it.bounds.width >= minOriginWidthPx)
        }
    // Verso dell'ultima navigazione (avanti = si apre una schermata sopra): serve al push di
    // Liquid Glass, che anima da se' il contenuto (vedi glassSlide piu' sotto).
    val navForward = remember { arrayOf(true) }
    // Material: l'ultima navigazione fra le tab e una schermata sopra e' un container transform
    // (aperta o chiusa su un elemento). Allora le tab restano ferme e piene sotto la forma che si
    // allarga, invece di svanire: e' la pagina a nascere dal pulsante, non la Home a sparire.
    val shellContainer = remember { arrayOf(false) }
    // true quando la pagina aperta col container transform ha finito di allargarsi: solo allora
    // le tab sotto smettono di essere disegnate (vedi il graphicsLayer dello Scaffold).
    val shellRevealDone = remember { mutableStateOf(true) }
    if (previousShellRoute[0] != shellRoute) {
        val forward = shellRoute.depth > previousShellRoute[0].depth
        navForward[0] = forward
        if (forward) {
            circolareplus.design.AilaContainerTransform.assignFreshTo(shellRoute)
        } else {
            circolareplus.design.AilaContainerTransform.onBack()
        }
        val moving = if (forward) shellRoute else previousShellRoute[0]
        if (forward && previousShellRoute[0] == ShellRoute.TABS || !forward && shellRoute == ShellRoute.TABS) {
            shellContainer[0] = !AppTheme.isGlass &&
                largeOrigin(circolareplus.design.AilaContainerTransform.originOf(moving)) != null
            if (forward && shellContainer[0]) shellRevealDone.value = false
        }
        previousShellRoute[0] = shellRoute
    }
    // Posizione delle pagine mentre le si trascina col dito (es. 2.4 = fra la terza e la quarta),
    // null quando non si trascina. La barra in basso la segue fotogramma per fotogramma; si legge
    // solo dentro la barra, cosi' lo scorrimento non ricompone tutta la shell.
    val tabBarDrag = remember { mutableStateOf<Float?>(null) }
    val shellProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shellRoute != ShellRoute.TABS) 1f else 0f,
        // In container transform la stessa curva della forma (molla aprendo, durata fissa
        // chiudendo): le tab spariscono sotto solo quando la pagina le copre del tutto.
        animationSpec = when {
            !shellContainer[0] -> circolareplus.design.ailaNavigationSpring()
            navForward[0] -> circolareplus.design.ailaContainerFloatSpring()
            else -> circolareplus.design.ailaContainerCloseSpec()
        },
        label = "shellPush"
    )

    // Il dettaglio circolare entra "alla iOS" sopra le tab, invece di sostituirle di colpo:
    // scorre da destra con una molla mentre la schermata sotto scivola di un terzo a sinistra e
    // si scurisce; indietro il contrario. Le tab restano composte sotto, quindi al ritorno la
    // lista delle circolari e' ancora dove l'avevi lasciata.
    val detailProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selectedCircularForDetail != null) 1f else 0f,
        animationSpec = circolareplus.design.ailaNavigationSpring(),
        label = "circularDetailPush"
    )
    // Durante l'uscita selectedCircularForDetail e' gia' null: si mostra l'ultima aperta.
    selectedCircularForDetail?.let { lastDetailCircular[0] = it }
    // Container transform del dettaglio (Material): all'apertura si prende l'elemento toccato.
    val detailOpen = selectedCircularForDetail != null
    val detailWasOpen = remember { arrayOf(false) }
    if (detailOpen && !detailWasOpen[0]) {
        circolareplus.design.AilaContainerTransform.assignFreshTo(DETAIL_TRANSFORM_KEY)
    }
    detailWasOpen[0] = detailOpen
    // Al ritorno ci si richiude sulla card della stessa circolare che si vede adesso (es. nella
    // lista delle Circolari, se era stata aperta dalla Home); se non c'e', sul punto di partenza.
    val detailOrigin = if (AppTheme.isGlass || !circolareplus.design.AILA_CONTAINER_TRANSFORM_ENABLED) null else {
        val stored = circolareplus.design.AilaContainerTransform.originOf(DETAIL_TRANSFORM_KEY)
        val number = (selectedCircularForDetail ?: lastDetailCircular[0])?.number
        if (stored != null && number != null && !detailOpen) {
            circolareplus.design.AilaContainerTransform.liveOf("circular:$number") ?: stored
        } else stored
    }
    // Dettaglio di un evento del calendario (Material): come per le circolari, la card toccata si
    // allarga nella pagina e ci si richiude tornando indietro (sulla stessa card, anche se nel
    // frattempo la lista e' scorsa). In Glass resta il foglio dal basso.
    val lastEventDetail = remember { arrayOf<CalendarEvent?>(null) }
    eventDetailToShow?.let { lastEventDetail[0] = it }
    val eventDetailOpen = eventDetailToShow != null
    val eventDetailWasOpen = remember { arrayOf(false) }
    if (eventDetailOpen && !eventDetailWasOpen[0]) {
        circolareplus.design.AilaContainerTransform.assignFreshTo(EVENT_DETAIL_KEY)
    }
    eventDetailWasOpen[0] = eventDetailOpen
    val eventDetailOrigin = if (AppTheme.isGlass) null else {
        val stored = circolareplus.design.AilaContainerTransform.originOf(EVENT_DETAIL_KEY)
        val id = (eventDetailToShow ?: lastEventDetail[0])?.id
        if (stored != null && id != null && !eventDetailOpen) {
            circolareplus.design.AilaContainerTransform.liveOf("event:$id") ?: stored
        } else stored
    }
    // Il dettaglio di una circolare, lo stesso sopra le tab (telefono) e nel pannello accanto
    // alla lista (schermi larghi, vedi paneCircular).
    val circularDetailContent: @Composable (Circular, () -> Unit) -> Unit = { circular, onClose ->
        CircularDetailScreen(
            circular = circular,
            classification = classifications[circular.number],
            isClassifying = circular.number in inFlightClassification,
            isQueued = circular.number in queuedClassification,
            isAwaitingServer = circular.number in awaitingServer,
            analysisOnDevice = circular.number !in cloudClassification,
            onStopAnalysis = { stopClassification(circular.number) },
            calendarEvents = calendarEvents,
            onBackClick = onClose,
            onDownloadPdfClick = {
                circular.downloadUrl?.let { uriHandler.openUri(it) }
            },
            // Allegati (colonna "Allegati" di Spaggiari): sia quelli in cache R2 sia i link
            // esterni si aprono allo stesso modo del PDF principale, nel browser di sistema —
            // niente anteprima inline per loro, solo per il documento principale.
            onOpenAttachmentClick = { attachment ->
                if (attachment.downloadUrl.isNotBlank()) {
                    uriHandler.openUri(attachment.downloadUrl)
                }
            },
            // Scaricare il PDF è già una cosa che l'app sa fare (serve alla classificazione AI):
            // qui gli stessi byte alimentano l'anteprima interna, senza una seconda strada.
            onLoadPdfBytes = {
                AppContainer.circularsRepository.downloadPdfBytes(circular.r2PdfKey)
            },
            // Allegati PDF (colonna "Allegati" di Spaggiari): stessa rotta del documento
            // principale, con la loro chiave R2 invece che quella della circolare.
            onLoadAttachmentPdfBytes = { attachment ->
                AppContainer.circularsRepository.downloadPdfBytes(
                    attachment.pdfKey ?: error("Allegato \"${attachment.label}\" senza chiave PDF")
                )
            },
            // La scadenza riconosciuta dall'AI diventa un evento vero. `isAiGenerated = true`
            // esiste gia' nell'API: serve a distinguere in calendario cosa ha proposto l'AI da
            // cosa ha inserito una persona.
            onCreateCalendarEvent = { deadline ->
                try {
                    val category = try {
                        circolareplus.domain.model.CalendarEventCategory.valueOf(deadline.category)
                    } catch (e: IllegalArgumentException) {
                        circolareplus.domain.model.CalendarEventCategory.ALTRO
                    }
                    val response = AppContainer.calendarRepository.createEvent(
                        title = deadline.title,
                        eventDate = deadline.dueDate,
                        startTime = deadline.time,
                        category = category,
                        isAiGenerated = true,
                        circularNumber = circular.number
                    )
                    when {
                        // La stessa scadenza e' gia' in calendario quel giorno: non si duplica.
                        response.warning != null ->
                            "\"${deadline.title}\" è già in calendario il ${deadline.dueDate}."
                        response.success -> {
                            // Senza questo, l'evento veniva creato sul server ma restava invisibile
                            // in Calendario/Home finché l'app non veniva riavviata: calendarEvents
                            // si ricarica solo quando calendarRefreshTrigger cambia (vedi sopra),
                            // e qui non veniva mai toccato.
                            reloadCalendar()
                            "Aggiunto al calendario."
                        }
                        else ->
                            "Il server non ha creato l'evento."
                    }
                } catch (e: Exception) {
                    "Non aggiunto: ${e.message ?: e::class.simpleName}"
                }
            },
            // Un riassunto di Gemini non si rifà con l'AI del telefono: il risultato sarebbe
            // peggiore e il server lo rifiuterebbe comunque.
            onReanalyze = if (
                classifications[circular.number]?.tier == 2 && AppContainer.isUsingLocalAiFirst()
            ) {
                null
            } else {
                {
                    // Il risultato vecchio resta visibile finché non arriva quello nuovo (o finché
                    // non si ferma l'analisi col tasto Stop).
                    launchClassification(circular, forceReanalyze = true)
                    Unit
                }
            }
        )
    }
    // Material: fondo pieno dietro alle tab, perche' in "fade through" le tab stesse svaniscono.
    // Liquid Glass: con un menu dal basso aperto l'app dietro si sfoca, cosi' il foglio di vetro
    // trasparente resta leggibile (vedi AilaBottomSheet).
    // Sfocatura accesa di colpo, non animata: animarla voleva dire ricalcolare a ogni fotogramma
    // una sfocatura a tutto schermo mentre il foglio sale (poco reattivo). Il passaggio lo
    // ammorbidisce il velo del foglio, che si dissolve da se'. Letta solo nel livello grafico.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (AppTheme.isGlass) Modifier else Modifier.background(AppTheme.BackgroundLight))
            .graphicsLayer {
                val radius = if (AppTheme.isGlass && circolareplus.design.AilaSheetBackdrop.openSheets > 0) 20.dp.toPx() else 0f
                renderEffect = if (radius > 0.5f) {
                    androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Clamp)
                } else null
            }
    ) {
    // Tablet e iPad: barra delle tab a sinistra invece che in basso e, sugli schermi piu' larghi,
    // circolari aperte accanto alla lista e bacheca su due colonne (vedi PlatformInsets.kt).
    val useRail = maxWidth >= circolareplus.design.RailMinWidth && maxHeight >= circolareplus.design.RailMinHeight
    val twoPane = useRail && maxWidth >= circolareplus.design.TwoPaneMinWidth
    // Circolari e dettaglio affiancati: solo nella sezione Circolari della tab Classe.
    val circularsPane = twoPane && classSection == ClassSection.CIRCULARS
    // Ruotando l'iPad (o ridimensionando la finestra) la circolare aperta resta aperta: passa dal
    // pannello accanto alla lista alla schermata sopra le tab, e viceversa.
    LaunchedEffect(twoPane) {
        val inClassCirculars = shellRoute == ShellRoute.TABS &&
            selectedTab == MainTab.CLASS && classSection == ClassSection.CIRCULARS
        if (twoPane) {
            val open = selectedCircularForDetail
            if (open != null && inClassCirculars) {
                paneCircular = open
                selectedCircularForDetail = null
            }
        } else {
            val open = paneCircular
            if (open != null) {
                paneCircular = null
                if (inClassCirculars && selectedCircularForDetail == null) selectedCircularForDetail = open
            }
        }
    }
    // Apre una circolare dalla lista o dalla Home: accanto alla lista se c'e' posto, sopra le tab
    // altrimenti. Da Ricerca, Notifiche e Assistente resta sempre sopra (sono schermate intere).
    val openCircularInClass: (Circular?) -> Unit = { circular ->
        if (twoPane) paneCircular = circular else selectedCircularForDetail = circular
    }
    // Le pagine dei pulsanti in alto (ricerca, notifiche, profilo) si compongono una volta,
    // nascoste, a app ferma: cosi' la prima apertura vera non paga il caricamento delle classi.
    circolareplus.design.AilaWarmUp(
        listOf<@Composable () -> Unit>(
            {
                SearchScreen(
                    circulars = circulars,
                    calendarEvents = calendarEvents,
                    proposals = proposals,
                    recentSearches = recentSearches,
                    onBackClick = {}
                )
            },
            {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenBackBar(title = "Profilo", onBackClick = {})
                    ProfileScreen(user = user, showHeader = false, profile = profile)
                }
            },
            {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenBackBar(title = "Notifiche", onBackClick = {})
                    NotificationsScreen(notifications = notificationLog)
                }
            }
        )
    )
    Scaffold(
        modifier = Modifier
            .graphicsLayer {
                val covered = maxOf(shellProgress.value, detailProgress.value)
                translationX = -size.width * circolareplus.design.ailaUnderlayShift * covered
                if (!AppTheme.isGlass && shellContainer[0] && detailProgress.value == 0f) {
                    // Container transform: tab ferme e piene sotto la forma; nascoste solo a pagina
                    // aperta del tutto, cosi' non si disegnano inutilmente sotto di lei.
                    // Tornando alle tab la Home ricompare subito, sotto la pagina ancora intera: si
                    // disegna durante l'attesa iniziale della chiusura, non nel primo fotogramma in cui
                    // la forma si stringe.
                    alpha = if (shellRevealDone.value && shellRoute != ShellRoute.TABS) 0f else 1f
                } else if (!AppTheme.isGlass) {
                    // Material "fade through" anche per le tab: aprendo una schermata svaniscono,
                    // tornando (es. dalla lettura di una circolare) riemergono crescendo appena,
                    // invece di essere gia' li' ferme sotto la pagina che si chiude.
                    val c = covered.coerceIn(0f, 1f)
                    alpha = 1f - c
                    val scale = 1f - 0.06f * c
                    scaleX = scale
                    scaleY = scale
                }
            }
            .drawWithContent {
                drawContent()
                // Velo scuro sulla schermata che resta sotto, come nel push di iOS. Con velo a 0
                // (oggi sempre) non si legge nemmeno l'avanzamento: leggerlo nella fase di disegno
                // faceva ridisegnare da capo tutta la Home a ogni fotogramma dell'animazione di
                // apertura e chiusura di ricerca, notifiche e profilo, per disegnare un rettangolo
                // trasparente (micro-scatti).
                val dim = circolareplus.design.ailaUnderlayDim
                if (dim > 0f) {
                    val covered = maxOf(shellProgress.value, detailProgress.value)
                    if (covered > 0f) drawRect(Color.Black.copy(alpha = dim * covered))
                }
            }
            // Lo "sblocco" di iOS: all'ingresso nell'app tutto arriva un po' ingrandito e si
            // posa con un piccolo rimbalzo (una volta sola per avvio).
            .ailaUnlock(),
        // Lo spazio attorno alla pillola della barra ha lo stesso fondo delle schermate.
        containerColor = AppTheme.BackgroundLight,
        // Niente margine in basso: il contenuto arriva fino al fondo dello schermo e scorre sotto
        // la barra flottante, che sta sopra (vedi FloatingTabBar piu' giu').
        // In alto niente margine: la Home fa arrivare il suo pannello sotto la barra di stato (ora,
        // Wi-Fi), le altre tab e la striscia offline se lo aggiungono da se' (vedi sotto).
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Tastiera (vedi PlatformInsets.kt): l'altezza di innerPadding include
                // gia' la barra in basso, quindi la si scala prima di applicare il margine.
                .consumeWindowInsets(innerPadding)
                .appImePadding(),
            color = AppTheme.BackgroundLight
        ) {
            // Su tablet e iPad il contenuto resta una colonna centrata (vedi appContentWidth). Con
            // la barra laterale la colonna la centra ogni pagina da se', a destra della barra.
            Column(modifier = if (useRail) Modifier.fillMaxSize() else Modifier.fillMaxHeight().appContentWidth()) {
                // Striscia "sei offline": compare solo se l'avvio è avvenuto senza rete, e si
                // può chiudere. Prima, in quel caso, non compariva niente perché l'app aveva
                // già fatto uscire dall'account.
                val offlineBannerShown = startedOffline && !offlineBannerDismissed
                if (offlineBannerShown) Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                    OfflineBanner(
                        onRetry = {
                            startedOffline = false
                            offlineBannerDismissed = false
                            isRestoringSession = true
                            restoreAttempt++
                        },
                        onDismiss = { offlineBannerDismissed = true }
                    )
                }
                // Il contenuto prende l'altezza che resta: senza weight, i figli con
                // fillMaxSize prenderebbero tutta l'altezza della Column e la striscia offline
                // spingerebbe la barra inferiore fuori dallo schermo.
                // Altezza occupata dalla barra flottante (pillola + margini + barra di sistema):
                // le schermate delle tab la lasciano libera in fondo alle liste.
                // Con la barra laterale in fondo resta solo la barra di sistema.
                val bottomBarPadding = (if (useRail) 0.dp else 84.dp) +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                // Tastiera aperta = il suo inset in basso e' > 0. (`isImeVisible` esiste solo su
                // Android: su iOS non compilava.)
                val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                // Sorgente della sfocatura della barra (Liquid Glass): il contenuto delle tab.
                val hazeState = dev.chrisbanes.haze.rememberHazeState()
                Box(modifier = Modifier.weight(1f)) {
                val profileEntry = remember(user.firstName, user.lastName) {
                    circolareplus.design.AilaProfileEntry(
                        initials = "${user.firstName.take(1)}${user.lastName.take(1)}".uppercase(),
                        onClick = { isInProfileScreen = true }
                    )
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    circolareplus.design.LocalBottomBarPadding provides bottomBarPadding,
                    circolareplus.design.LocalProfileEntry provides profileEntry,
                    circolareplus.design.LocalWideLayout provides twoPane
                ) {
                    // Da qualunque tab diversa da Home, il back di sistema torna a Home invece di
                    // chiudere l'app subito — comportamento standard delle bottom bar Android. Da
                    // Home il back non viene intercettato: lì si comporta come sempre (chiude
                    // l'app), coerente con le altre app che usano una bottom bar. Caso speciale:
                    // dentro "Preferenze Sociali" (sotto-schermata di Mappa Posti) il back torna
                    // prima alla mappa, non subito a Home.
                    circolareplus.platform.PlatformBackHandler(enabled = selectedTab != MainTab.HOME) {
                        if (selectedTab == MainTab.SEATMAP && seatMapMode == "VOTE_PREFERENCES") {
                            seatMapMode = "MAP"
                        } else {
                            selectedTab = MainTab.HOME
                        }
                    }
                    // Transizione fra le tab: la nuova schermata entra scivolando di poco dal lato
                    // della tab toccata (destra se e' piu' a destra nella barra, sinistra altrimenti)
                    // mentre la vecchia sfuma via. Prima il cambio era uno stacco secco e il movimento
                    // lo dava solo la cascata degli elementi, che ora si vede solo la prima volta.
                    // Le tab sono pagine affiancate: si passa dall'una all'altra anche scorrendo
                    // col dito a destra e sinistra (oltre che toccando la barra in basso).
                    val tabPager = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = MainTab.entries.indexOf(selectedTab)
                    ) { MainTab.entries.size }
                    // Dissolvenza del cambio di tab toccando la barra: la pagina non scorre attraverso
                    // quelle in mezzo, ma svanisce e la nuova emerge (fade through, come prima).
                    val tabFade = remember { androidx.compose.animation.core.Animatable(1f) }
                    // Barra -> pagine. Se la pagina giusta e' gia' quella verso cui si sta
                    // scorrendo col dito non si fa nulla; altrimenti (tocco sulla barra) si salta
                    // alla pagina con la dissolvenza.
                    LaunchedEffect(selectedTab) {
                        val target = MainTab.entries.indexOf(selectedTab)
                        val alreadyThere = (tabPager.targetPage == target && tabPager.isScrollInProgress) ||
                            (tabPager.currentPage == target && tabPager.currentPageOffsetFraction == 0f)
                        if (alreadyThere) {
                            // Un tocco rapido annullato a meta' non deve lasciare la pagina sbiadita.
                            if (tabFade.value < 1f) tabFade.animateTo(1f, androidx.compose.animation.core.tween(140))
                            return@LaunchedEffect
                        }
                        tabFade.animateTo(0f, androidx.compose.animation.core.tween(if (AppTheme.isGlass) 90 else 70))
                        tabPager.scrollToPage(target)
                        tabFade.animateTo(1f, androidx.compose.animation.core.tween(if (AppTheme.isGlass) 140 else 210))
                    }
                    // Pagine -> barra, in tempo reale: mentre si scorre col dito la barra segue la
                    // pagina verso cui si sta andando (non solo a scorrimento finito).
                    LaunchedEffect(tabPager) {
                        androidx.compose.runtime.snapshotFlow { tabPager.targetPage to tabPager.isScrollInProgress }
                            .collect { (page, scrolling) ->
                                if (!scrolling || tabFade.value < 1f) return@collect
                                val tab = MainTab.entries[page]
                                if (selectedTab != tab) selectedTab = tab
                            }
                    }
                    // Pagine -> barra, continuo: mentre si trascina (e mentre la pagina si posa
                    // dopo il rilascio) la barra non sceglie una voce a meta' strada ma segue la
                    // posizione esatta delle pagine, cosi' fermandosi a meta' non va avanti e
                    // indietro. Il cambio da tocco (con la dissolvenza) resta alla molla.
                    LaunchedEffect(tabPager) {
                        androidx.compose.runtime.snapshotFlow {
                            val following = tabPager.isScrollInProgress && tabFade.value >= 1f
                            if (following) {
                                (tabPager.currentPage + tabPager.currentPageOffsetFraction)
                                    .coerceIn(0f, (MainTab.entries.size - 1).toFloat())
                            } else null
                        }.collect { tabBarDrag.value = it }
                    }
                    LaunchedEffect(tabPager) {
                        androidx.compose.runtime.snapshotFlow { tabPager.settledPage }.collect { page ->
                            val tab = MainTab.entries[page]
                            if (selectedTab != tab && !tabPager.isScrollInProgress) selectedTab = tab
                        }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = tabPager,
                        beyondViewportPageCount = 1,
                        // Su tablet e iPad si cambia tab dalla barra laterale: lo scorrimento
                        // orizzontale lo prendono le pagine (il PDF accanto alla lista, le card).
                        userScrollEnabled = !useRail,
                        key = { it },
                        // Al rilascio la pagina si posa con una molla. Pixel: appena elastica, come
                        // le spatial spring di M3 Expressive. Glass: morbida e senza rimbalzo, come iOS.
                        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                            state = tabPager,
                            snapAnimationSpec = if (AppTheme.isGlass) {
                                androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f)
                            } else {
                                androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 380f)
                            }
                        ),
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                val f = tabFade.value
                                alpha = f
                                if (!AppTheme.isGlass) {
                                    val scale = 0.94f + 0.06f * f
                                    scaleX = scale
                                    scaleY = scale
                                }
                            }
                            .then(
                            // Lo sfondo a macchie e' fermo sullo schermo: le pagine trasparenti ci
                            // scorrono sopra. Qui se ne ridisegna una copia identica, spostata al
                            // contrario delle tab che escono (cosi' resta ferma), solo perche' la
                            // barra in basso lo sfochi insieme al contenuto.
                            if (AppTheme.isGlass) Modifier.hazeSource(hazeState).ailaGlassBackdrop {
                                circolareplus.design.ailaUnderlayShift * maxOf(shellProgress.value, detailProgress.value)
                            } else Modifier
                        )
                    ) { page ->
                    val tab = MainTab.entries[page]
                    // Margine della barra di stato per ogni tab tranne la Home (che ci fa passare
                    // sotto il suo pannello) e salvo quando c'e' gia' la striscia offline in cima.
                    Box(modifier = Modifier.fillMaxSize().then(
                        // Scorrendo col dito fra le tab, ogni stile ha il suo movimento. Pixel: le
                        // pagine si comportano come carte, quella che esce si rimpicciolisce e
                        // sbiadisce, quella che entra si ingrandisce fino a piena misura, con un
                        // piccolo stacco fra le due. Liquid Glass: piu' sobrio, come iOS, la pagina
                        // si allontana appena e si vela sopra lo sfondo che resta fermo. Niente
                        // parallasse: le pagine sono trasparenti e sovrapposte si mescolerebbero.
                        // Il salto dalla barra in basso resta la dissolvenza qui sopra.
                        Modifier.graphicsLayer {
                            val offset = (tabPager.currentPage - page) + tabPager.currentPageOffsetFraction
                            val d = kotlin.math.abs(offset).coerceIn(0f, 1f)
                            val glass = AppTheme.isGlass
                            val scale = 1f - (if (glass) 0.07f else 0.13f) * d
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (if (glass) 0.45f else 0.6f) * d
                        }
                    ).then(
                        if (tab != MainTab.HOME && !offlineBannerShown) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        else Modifier
                    ).then(
                        // Barra laterale: la pagina parte alla sua destra, in una colonna centrata;
                        // la tab Classe con le circolari affiancate prende tutta la larghezza, e
                        // le altre, che su schermi larghi vanno su due colonne, arrivano fino a
                        // MaxWideContentWidth.
                        if (!useRail) Modifier
                        else Modifier.padding(start = RailInset).then(
                            when {
                                tab == MainTab.CLASS && circularsPane -> Modifier
                                twoPane ->
                                    Modifier.appContentWidth(circolareplus.design.MaxWideContentWidth)
                                else -> Modifier.appContentWidth()
                            }
                        )
                    )) {
                    when (tab) {
                        MainTab.HOME -> {
                            HomeScreen(
                                studentFirstName = user.firstName,
                                circulars = circulars,
                                calendarEvents = calendarEvents,
                                openProposalsCount = proposals.size,
                                onNavigateToCircularDetail = { number ->
                                    openCircularInClass(circulars.firstOrNull { it.number == number })
                                    classSection = ClassSection.CIRCULARS
                                    selectedTab = MainTab.CLASS
                                },
                                onNavigateToSeatMap = { selectedTab = MainTab.SEATMAP },
                                onNavigateToBoard = {
                                    classSection = ClassSection.BOARD
                                    selectedTab = MainTab.CLASS
                                },
                                onNavigateToPolls = { selectedTab = MainTab.POLLS },
                                onNavigateToCalendar = { selectedTab = MainTab.CALENDAR },
                                onEventClick = { event ->
                                    calendarFocusDateIso = event.date
                                    selectedTab = MainTab.CALENDAR
                                },
                                onNavigateToCirculars = {
                                    classSection = ClassSection.CIRCULARS
                                    selectedTab = MainTab.CLASS
                                },
                                onNavigateToNotifications = { isInNotificationsScreen = true },
                                onNavigateToSearch = { isInSearchScreen = true },
                                hasUnreadNotifications = hasUnreadNotifications
                            )
                        }
                        MainTab.CALENDAR -> {
                            LoadableContent(
                                isLoading = isCalendarLoading,
                                error = calendarError,
                                onRetry = { reloadCalendar() }
                            ) {
                                CalendarScreen(
                                    events = calendarEvents,
                                    onAddEventClick = {
                                        addEventInitialStep = EventCreationStep.MENU
                                        addEventInitialDateIso = null
                                        showAddEventDialog = true
                                    },
                                    onAddEventForDayClick = { dateIso ->
                                        addEventInitialStep = EventCreationStep.MANUAL
                                        addEventInitialDateIso = dateIso
                                        showAddEventDialog = true
                                    },
                                    onEventClick = { event -> eventDetailToShow = event },
                                    focusDateIso = calendarFocusDateIso,
                                    onFocusConsumed = { calendarFocusDateIso = null },
                                    removingEventIds = removingEventIds
                                )
                            }
                        }
                        MainTab.SEATMAP -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LoadableContent(
                                    // Il caricamento dei compagni avviato dal calendario non deve
                                    // coprire la mappa con lo spinner: ci pensa loadSeatMapData.
                                    isLoading = isSeatMapLoading,
                                    error = seatMapError,
                                    onRetry = { seatMapRefreshTrigger++ }
                                ) {
                                    // Mappa <-> voto delle preferenze: stessa transizione delle altre
                                    // schermate aperte "sopra" (push iOS in Glass, shared axis in
                                    // Material) invece dello stacco secco.
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = seatMapMode == "VOTE_PREFERENCES",
                                        // Glass: dissolvenza (lo sfondo e' quello della tab, fermo);
                                        // due pagine col proprio sfondo che scorrono mostravano lo stacco.
                                        transitionSpec = { if (AppTheme.isGlass) ailaTabTransition() else ailaPushTransition(forward = targetState) },
                                        label = "seatMapMode",
                                        modifier = Modifier.fillMaxSize()
                                    ) { votingPreferences ->
                                    Box(modifier = Modifier.fillMaxSize().then(
                                        // Fondo pieno: durante lo scorrimento le due schermate non si sovrappongono.
                                        if (AppTheme.isGlass) Modifier else Modifier.background(AppTheme.BackgroundLight)
                                    )) {
                                    if (votingPreferences) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            ScreenBackBar(
                                                title = "Preferenze Sociali",
                                                onBackClick = { seatMapMode = "MAP" }
                                            )
                                            SocialPreferencesVotingScreen(
                                                classmates = classmates.filter { it.id != user.id },
                                                currentVotes = socialVotes,
                                                onVoteChanged = { targetId, score ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.preferencesRepository.vote(targetId, score)
                                                            socialVotes[targetId] = score
                                                        } catch (e: Exception) {
                                                            seatMapActionError = "Voto non registrato: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onSubmitVotes = { seatMapMode = "MAP" }
                                            )
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // Il banner per votare le preferenze compariva solo
                                            // agli studenti NON rappresentanti: il rappresentante
                                            // apriva la votazione e poi non aveva alcun modo di
                                            // votare a sua volta, pur sedendo in classe come tutti.
                                            if (isPreferencesOpen) {
                                                PreferencesOpenBanner(onClick = { seatMapMode = "VOTE_PREFERENCES" })
                                            }
                                            val memoizedStudentsMap = remember(classmates, user) {
                                                classmates.associateBy { it.id } + (user.id to user)
                                            }
                                            SeatMapScreen(
                                                currentUserId = user.id,
                                                isRepresentative = isRepresentative,
                                                assignments = seatMapAssignments,
                                                studentsMap = memoizedStudentsMap,
                                                isPreferencesOpen = isPreferencesOpen,
                                                preferencesProgress = preferencesProgress,
                                                isExportingPdf = isExportingSeatMapPdf,
                                                isGeneratingProposals = isGeneratingProposals,
                                                onExportPdf = {
                                                    coroutineScope.launch {
                                                        isExportingSeatMapPdf = true
                                                        try {
                                                            circolareplus.platform.exportSeatMapPdf(
                                                                assignments = seatMapAssignments,
                                                                studentsMap = memoizedStudentsMap
                                                            )
                                                        } catch (e: Exception) {
                                                            seatMapActionError = "Impossibile generare il PDF: ${e.message}"
                                                        } finally {
                                                            isExportingSeatMapPdf = false
                                                        }
                                                    }
                                                },
                                                onTogglePreferencesWindow = { open ->
                                                    // Subito, senza aspettare il server: prima il
                                                    // tasto restava fermo per tutto l'invio delle
                                                    // notifiche alla classe. Se la richiesta fallisce
                                                    // si torna allo stato di prima.
                                                    val previous = isPreferencesOpen
                                                    isPreferencesOpen = open
                                                    coroutineScope.launch {
                                                        try {
                                                            isPreferencesOpen = AppContainer.preferencesRepository.setPreferencesOpen(open).preferencesOpen
                                                        } catch (e: CancellationException) {
                                                            throw e
                                                        } catch (e: Exception) {
                                                            isPreferencesOpen = previous
                                                            seatMapActionError = "Impossibile aggiornare la finestra preferenze: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onGenerateProposals = { weights, seatsPerDesk ->
                                                    coroutineScope.launch {
                                                        isGeneratingProposals = true
                                                        lastRequestedSeatsPerDesk = seatsPerDesk
                                                        try {
                                                            val disciplinePairsDeferred = async {
                                                                // Un server senza la rotta nuova non deve impedire le proposte.
                                                                try {
                                                                    AppContainer.ratingsRepository.listDisciplinePairs()
                                                                        .map { it.studentA to it.studentB }
                                                                        .toSet()
                                                                } catch (e: CancellationException) {
                                                                    throw e
                                                                } catch (e: Exception) {
                                                                    emptySet()
                                                                }
                                                            }
                                                            val (ratings, matrix, history) = coroutineScope {
                                                                val ratingsDeferred = async { AppContainer.ratingsRepository.listRatings() }
                                                                val matrixDeferred = async { AppContainer.preferencesRepository.matrixForAlgorithm().matrix }
                                                                val historyDeferred = async { AppContainer.seatMapRepository.getHistoryForOptimizer() }
                                                                Triple(ratingsDeferred.await(), matrixDeferred.await(), historyDeferred.await())
                                                            }
                                                            val disciplinePairs = disciplinePairsDeferred.await()
                                                            val ratingsMap = ratings.associate { rating ->
                                                                rating.studentId to circolareplus.domain.model.RepresentativeRating(
                                                                    studentId = rating.studentId,
                                                                    didactic = rating.didactic ?: 3,
                                                                    behavior = rating.behavior ?: 3
                                                                )
                                                            }
                                                            val profiles = ratings.associate { rating ->
                                                                rating.studentId to StudentProfile(
                                                                    userId = rating.studentId,
                                                                    heightCm = (rating.heightCm ?: 175).let { h -> (h / 5) * 5 }.coerceIn(140, 210),
                                                                    priorityPass = rating.priorityPass
                                                                )
                                                            }
                                                            val socialMap = matrix.associate { entry ->
                                                                (entry.from to entry.to) to SocialPreferenceScore.fromValue(entry.score)
                                                            }
                                                            // Tenuti in stato per il ricalcolo live nell'editor manuale (swap-by-tap),
                                                            // che riusa questi stessi input invece di rifare le chiamate di rete.
                                                            seatMapOptimizerProfiles = profiles
                                                            seatMapOptimizerRatings = ratingsMap
                                                            seatMapOptimizerDisciplinePairs = disciplinePairs
                                                            seatMapOptimizerSocialMap = socialMap
                                                            seatMapOptimizerHistory = history
                                                            seatMapOptimizerWeights = weights
                                                            seatMapOptimizerIsSmallClass = classmates.size < 22
                                                            val computed = withContext(Dispatchers.Default) {
                                                                AppContainer.seatMapRepository.generateThreeProposals(
                                                                    students = classmates,
                                                                    profiles = profiles,
                                                                    ratings = ratingsMap,
                                                                    socialPreferences = socialMap,
                                                                    history = history,
                                                                    weights = weights,
                                                                    seatsPerDesk = seatsPerDesk,
                                                                    disciplinePairs = disciplinePairs
                                                                )
                                                            }
                                                            // La pagina delle proposte si apre dal pulsante "Calcola":
                                                            // il tocco e' di qualche secondo fa, lo si rinnova.
                                                            circolareplus.design.AilaContainerTransform.touchFresh()
                                                            proposalOptions = computed
                                                        } catch (e: Exception) {
                                                            seatMapActionError = "Impossibile calcolare le proposte: ${e.message}"
                                                        } finally {
                                                            isGeneratingProposals = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    }
                                    }
                                }
                            }
                        }
                        MainTab.CLASS -> {
                            // "Classe" unisce quello che prima erano le tab separate "Circolari" e
                            // "Bacheca" (design AILA): un selettore interno sostituisce le due tab.
                            // Schermi larghi: lista delle circolari a sinistra, circolare aperta a
                            // destra (come Mail su iPad).
                            Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = if (circularsPane) Modifier.fillMaxHeight().width(CircularsListPaneWidth)
                                else Modifier.fillMaxSize()
                            ) {
                                // Intestazione della tab: prima Circolari e Bacheca erano due chip
                                // identiche a quelle dei filtri di contenuto, quindi non si capiva
                                // che cambiavano schermata invece di filtrare la lista. Ora sono un
                                // selettore a segmenti su barra bianca, come nel mockup.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppTheme.BackgroundLight)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = AppTheme.Space16,
                                                end = AppTheme.Space16,
                                                top = AppTheme.Space20,
                                                bottom = AppTheme.Space12
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Stesso marchio grande a sinistra delle altre intestazioni
                                        // (AilaScreenHeader): qui al posto del titolo c'e' il selettore.
                                        circolareplus.design.AilaBrandMark(size = 44.dp)
                                        Spacer(modifier = Modifier.width(AppTheme.Space12))
                                        circolareplus.design.AilaSegmentedTabs(
                                            labels = listOf(ClassSection.CIRCULARS.title, ClassSection.BOARD.title),
                                            selectedIndex = if (classSection == ClassSection.CIRCULARS) 0 else 1,
                                            onSelect = { index ->
                                                classSection = if (index == 0) ClassSection.CIRCULARS else ClassSection.BOARD
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isRepresentative) {
                                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                                            circolareplus.design.AilaIconButton(
                                                contentDescription = "Scheda della classe",
                                                onClick = { isInClassRosterScreen = true },
                                                opensPage = true
                                            ) { tint -> AppIcons.People(modifier = Modifier.size(20.dp), color = tint) }
                                        }
                                        circolareplus.design.LocalProfileEntry.current?.let { entry ->
                                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                                            circolareplus.design.AilaProfileButton(entry)
                                        }
                                    }
                                }
                                // Dissolvenza tra le due sezioni: prima il contenuto veniva
                                // sostituito di colpo, e con liste lunghe sembrava un salto.
                                androidx.compose.animation.Crossfade(
                                    targetState = classSection,
                                    label = "classSection"
                                ) { section ->
                                when (section) {
                                    ClassSection.CIRCULARS -> {
                                        LoadableContent(
                                            isLoading = isCircularsLoading,
                                            error = circularsError,
                                            onRetry = { circularsRefreshTrigger++ }
                                        ) {
                                            CircularsScreen(
                                                circulars = circulars,
                                                classifications = classifications,
                                                analyzingNumbers = inFlightClassification,
                                                onSelectCircular = { openCircularInClass(it) },
                                                selectedNumber = if (circularsPane) paneCircular?.number else null
                                            )
                                        }
                                    }
                                    ClassSection.BOARD -> {
                                        // Lo spinner a tutto schermo solo al primo caricamento: prima ogni
                                        // ricarica (dopo un commento, un cambio di stato) smontava la
                                        // bacheca, e con lei i commenti aperti e il voto appena dato.
                                        LoadableContent(
                                            isLoading = isProposalsLoading && proposals.isEmpty(),
                                            error = proposalsError,
                                            onRetry = { reloadProposals() }
                                        ) {
                                            BoardScreen(
                                                proposals = proposals,
                                                currentUserId = user.id,
                                                isRepresentative = isRepresentative,
                                                canModerateIdentity = canModerateIdentity,
                                                unlockRequests = unlockRequests,
                                                onVote = { proposalId, voteType ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.vote(proposalId, voteType)
                                                        } catch (e: Exception) {
                                                            proposalsError = "Voto non riuscito: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onChangeStatus = { proposalId, status, outcome ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.changeStatus(proposalId, status, outcome)
                                                            reloadProposals()
                                                        } catch (e: Exception) {
                                                            proposalsError = "Impossibile cambiare stato: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onEdit = { proposalId, newTitle, newDescription ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.updateProposal(
                                                                proposalId = proposalId,
                                                                title = newTitle,
                                                                description = newDescription
                                                            )
                                                            reloadProposals()
                                                        } catch (e: Exception) {
                                                            proposalsError = "Modifica non riuscita: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onCreateProposalClick = { showAddProposalDialog = true },
                                                onLoadComments = { proposalId ->
                                                    AppContainer.proposalsRepository.listComments(proposalId)
                                                },
                                                onAddComment = { proposalId, content, isAnonymous ->
                                                    AppContainer.proposalsRepository.addComment(proposalId, content, isAnonymous)
                                                    reloadProposals()
                                                },
                                                onRequestUnlock = { proposalId, commentId, reason ->
                                                    // Niente try/catch: la finestra mostra l'errore e
                                                    // lascia riprovare senza perdere il testo del motivo.
                                                    AppContainer.proposalsRepository.requestUnlock(proposalId, reason, commentId)
                                                    reloadProposals()
                                                },
                                                onApproveUnlock = { requestId ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.approveUnlock(requestId)
                                                            reloadProposals()
                                                        } catch (e: Exception) {
                                                            proposalsError = "Approvazione non riuscita: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onRejectUnlock = { requestId ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.rejectUnlock(requestId)
                                                            reloadProposals()
                                                        } catch (e: Exception) {
                                                            proposalsError = "Operazione non riuscita: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onDelete = { proposalId ->
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.proposalsRepository.delete(proposalId)
                                                            reloadProposals()
                                                        } catch (e: Exception) {
                                                            proposalsError = "Impossibile eliminare la proposta: ${e.message}"
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                }
                            }
                            if (circularsPane) {
                                CircularDetailPane(
                                    circular = paneCircular,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) { circular ->
                                    // Il back di sistema chiude la circolare nel pannello prima di
                                    // tornare alla Home (vedi il back delle tab qui sopra).
                                    circolareplus.platform.PlatformBackHandler(enabled = selectedTab == MainTab.CLASS) { paneCircular = null }
                                    circularDetailContent(circular) { paneCircular = null }
                                }
                            }
                            }
                        }
                        MainTab.POLLS -> {
                            // Se si è nello storico, il back torna prima al sondaggio corrente (come la
                            // freccia in ScreenBackBar sotto), solo un secondo back chiude il flusso.
                            // Dallo storico il back torna ai sondaggi in corso; da li' vale il back
                            // delle tab (torna a Home).
                            circolareplus.platform.PlatformBackHandler(enabled = showPollHistory) {
                                showPollHistory = false
                            }
                            Column(modifier = Modifier.fillMaxSize()) {
                                circolareplus.design.AilaScreenHeader(title = "Sondaggi")
                                // Sopra: sondaggi in corso o storico (vale per entrambi i tipi), con
                                // l'azione "nuovo" accanto. Sotto: il tipo di sondaggio.
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = AppTheme.Space16, end = AppTheme.Space16, top = AppTheme.Space8),
                                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    circolareplus.design.AilaSegmentedTabs(
                                        labels = listOf("In corso", "Storico"),
                                        selectedIndex = if (showPollHistory) 1 else 0,
                                        onSelect = { index -> showPollHistory = index == 1 },
                                        modifier = Modifier.weight(1f),
                                        key = showPollHistory
                                    )
                                    if (isRepresentative) {
                                        circolareplus.design.AilaIconButton(
                                            contentDescription = "Nuovo sondaggio",
                                            onClick = {
                                                if (pollsSection == 1) showCreateRankingPollDialog = true else showCreatePollDialog = true
                                            },
                                            primary = true
                                        ) { tint -> AppIcons.Plus(modifier = Modifier.size(18.dp), color = tint) }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    circolareplus.design.AilaSegmentedTabs(
                                        labels = listOf("Interrogazioni", "Ordinamento"),
                                        selectedIndex = pollsSection,
                                        onSelect = { index -> pollsSection = index },
                                        modifier = Modifier.weight(1f),
                                        key = pollsSection
                                    )
                                }
                                if (pollsSection == 1) {
                                    LoadableContent(
                                        isLoading = isRankingLoading,
                                        error = rankingError,
                                        onRetry = { rankingRefreshTrigger++ }
                                    ) {
                                        RankingPollsScreen(
                                            polls = rankingPolls.filter { it.isClosed == showPollHistory },
                                            showingHistory = showPollHistory,
                                            totalStudents = rankingTotalStudents,
                                            isRepresentative = isRepresentative,
                                            submittingPollId = submittingRankingPollId,
                                            onSubmitRanking = { pollId, optionIds ->
                                                if (submittingRankingPollId == null) {
                                                    submittingRankingPollId = pollId
                                                    coroutineScope.launch {
                                                        try {
                                                            AppContainer.rankingPollsRepository.submitRanking(pollId, optionIds)
                                                            // Si rilegge dal server: è lui a calcolare la classifica
                                                            // della classe, che ora diventa visibile.
                                                            rankingRefreshTrigger++
                                                        } catch (e: Exception) {
                                                            rankingError = "Invio non riuscito: ${e.message}"
                                                        } finally {
                                                            submittingRankingPollId = null
                                                        }
                                                    }
                                                }
                                            },
                                            onClosePoll = { pollId ->
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.rankingPollsRepository.closePoll(pollId)
                                                        rankingRefreshTrigger++
                                                    } catch (e: Exception) {
                                                        rankingError = "Impossibile chiudere il sondaggio: ${e.message}"
                                                    }
                                                }
                                            },
                                            onDeletePoll = { pollId ->
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.rankingPollsRepository.deletePoll(pollId)
                                                        rankingPolls = rankingPolls.filterNot { it.id == pollId }
                                                    } catch (e: Exception) {
                                                        rankingError = "Impossibile eliminare: ${e.message}"
                                                    }
                                                }
                                            },
                                            onCreatePoll = { showCreateRankingPollDialog = true }
                                        )
                                    }
                                } else if (showPollHistory && !isRepresentative) {
                                    // Le assegnazioni complete le legge solo il Rappresentante: agli altri
                                    // si dice dove trovare le proprie date invece di una lista vuota.
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                        circolareplus.design.AilaEmptyState(
                                            title = "Le tue interrogazioni",
                                            message = "Quando il Rappresentante chiude un sondaggio e ne pubblica le date, quella assegnata a te compare nel tuo Calendario.",
                                            icon = { AppIcons.Calendar(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                                        )
                                    }
                                } else if (showPollHistory) {
                                    PollHistoryScreen(
                                        polls = allPolls,
                                        expandedPollId = expandedResultsPollId,
                                        isLoadingResults = isLoadingPollResults,
                                        resultsError = pollResultsError,
                                        assignments = pollAssignments,
                                        onToggleResults = { pollId ->
                                            expandedResultsPollId = if (expandedResultsPollId == pollId) null else pollId
                                            pollCalendarMessage = null
                                            pollCalendarMessageIsError = false
                                        },
                                        onDelete = { pollId ->
                                            coroutineScope.launch {
                                                try {
                                                    AppContainer.pollsRepository.deletePoll(pollId)
                                                    if (expandedResultsPollId == pollId) expandedResultsPollId = null
                                                    pollsRefreshTrigger++
                                                } catch (e: Exception) {
                                                    pollResultsError = "Impossibile eliminare: ${e.message}"
                                                }
                                            }
                                        },
                                        isAddingToCalendar = isAddingPollToCalendar,
                                        calendarAddMessage = pollCalendarMessage,
                                        calendarAddIsError = pollCalendarMessageIsError,
                                        onAddToCalendar = { poll, assignmentsToAdd ->
                                            coroutineScope.launch {
                                                isAddingPollToCalendar = true
                                                pollCalendarMessage = null
                                                pollCalendarMessageIsError = false
                                                try {
                                                    // Un evento per data (non per studente): la stessa data
                                                    // interrogazione riguarda più studenti insieme, ed è
                                                    // anche l'unico modo per non far scattare subito il
                                                    // controllo doppioni del server (stessa categoria+
                                                    // periodo per la classe, vedi CalendarRepository.createEvent).
                                                    val byDate = assignmentsToAdd
                                                        .filter { it.slotDate != null }
                                                        .groupBy { it.slotDate!! }
                                                        // toSortedMap() e' solo JVM: non compila su iOS.
                                                        // toMap() da una lista ordinata mantiene l'ordine.
                                                        .toList()
                                                        .sortedBy { it.first }
                                                        .toMap()

                                                    var added = 0
                                                    var skipped = 0
                                                    var failed = 0
                                                    byDate.forEach { (date, group) ->
                                                        val studentNames = group.map { it.studentName ?: it.studentId }
                                                        val studentIds = group.map { it.studentId }.distinct()
                                                        try {
                                                            val response = AppContainer.calendarRepository.createEvent(
                                                                title = "Interrogazione di ${poll.subject}",
                                                                eventDate = date,
                                                                startTime = null,
                                                                category = CalendarEventCategory.INTERROGAZIONE,
                                                                isForAll = false,
                                                                isAiGenerated = true,
                                                                visibleToUserIds = studentIds,
                                                                notes = "Studenti: ${studentNames.joinToString(", ")}"
                                                            )
                                                            if (response.warning != null) skipped++ else added++
                                                        } catch (e: Exception) {
                                                            failed++
                                                        }
                                                    }

                                                    if (added > 0) reloadCalendar()

                                                    pollCalendarMessageIsError = added == 0
                                                    pollCalendarMessage = buildString {
                                                        if (added > 0) append("$added dat${if (added == 1) "a aggiunta" else "e aggiunte"} al calendario.")
                                                        if (skipped > 0) {
                                                            if (isNotEmpty()) append(" ")
                                                            append("$skipped già presenti nel periodo.")
                                                        }
                                                        if (failed > 0) {
                                                            if (isNotEmpty()) append(" ")
                                                            append("$failed non riuscite.")
                                                        }
                                                        if (isEmpty()) append("Nessuna data da aggiungere.")
                                                    }
                                                } finally {
                                                    isAddingPollToCalendar = false
                                                }
                                            }
                                        }
                                    )
                                } else {
                                LoadableContent(
                                    isLoading = isPollLoading,
                                    error = pollError,
                                    onRetry = { pollsRefreshTrigger++ }
                                ) {
                                    val poll = currentPoll
                                    if (poll == null) {
                                        // Tipo esplicito: un lambda scritto direttamente dentro un "if"
                                        // come argomento nullable è ambiguo da leggere (e da inferire).
                                        val createPollAction: (() -> Unit)? =
                                            if (isRepresentative) {
                                                { showCreatePollDialog = true }
                                            } else {
                                                null
                                            }
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                            circolareplus.design.AilaEmptyState(
                                                title = "Nessun sondaggio aperto",
                                                message = if (isRepresentative)
                                                    "Crea un sondaggio per far prenotare alla classe le date delle interrogazioni."
                                                else
                                                    "Quando il Rappresentante apre un sondaggio, lo trovi qui.",
                                                actionLabel = if (isRepresentative) "+ Nuovo sondaggio" else null,
                                                onAction = createPollAction,
                                                icon = { AppIcons.Check(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                                            )
                                        }
                                    } else {
                                        PollsScreen(
                                            subjectName = poll.subject,
                                            slots = poll.slots.map { s ->
                                                SlotUiItem(
                                                    slotId = s.id,
                                                    dateLabel = s.slotDate,
                                                    subject = poll.subject,
                                                    capacity = s.capacity,
                                                    isMandatory = s.teacherMandatory,
                                                    currentVote = s.myVote?.let { InterrogationVoteType.fromScore(it) }
                                                )
                                            },
                                            sacrificeBonus = poll.mySacrificeBonus,
                                            canVote = poll.isTarget,
                                            isRepresentative = isRepresentative,
                                            onClosePoll = {
                                                // "Chiudi" = calcola subito le date anche se manca
                                                // qualcuno (chi non ha votato vale come Giallo ovunque):
                                                // il sondaggio passa nello Storico con il risultato pronto.
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.pollsRepository.runAssignments(poll.id, force = true)
                                                        pollsRefreshTrigger++
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        pollError = "Impossibile chiudere il sondaggio: ${e.message}"
                                                    }
                                                }
                                            },
                                            onDeletePoll = {
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.pollsRepository.deletePoll(poll.id)
                                                        pollsRefreshTrigger++
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        pollError = "Impossibile eliminare: ${e.message}"
                                                    }
                                                }
                                            },
                                            onCastVote = { slotId, voteType ->
                                                // Aggiornamento ottimistico: la selezione cambia subito,
                                                // la richiesta parte in background.
                                                //
                                                // **Il "balbettio" dei pulsanti veniva da qui**: dopo ogni
                                                // voto si ricaricava l'intero sondaggio dal server e si
                                                // sovrascriveva lo stato locale. Cambiando scelta due o tre
                                                // volte di fila, le risposte arrivavano in ordine sparso e
                                                // ognuna riportava indietro la selezione a un valore
                                                // precedente, finché l'ultima non vinceva. Ora la ricarica
                                                // non si fa più: lo stato locale è già quello giusto, e il
                                                // server viene riletto solo se la chiamata fallisce.
                                                val previousPoll = currentPoll
                                                currentPoll = currentPoll?.copy(
                                                    slots = currentPoll!!.slots.map {
                                                        if (it.id == slotId) it.copy(myVote = voteType.score) else it
                                                    }
                                                )
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.pollsRepository.voteSlot(poll.id, slotId, voteType.score)
                                                    } catch (e: Exception) {
                                                        currentPoll = previousPoll
                                                        pollError = "Voto non registrato: ${e.message}"
                                                    }
                                                }
                                            },
                                            isSubmitted = submittedPollIds.contains(poll.id),
                                            submittedCount = pollProgress?.submittedCount ?: 0,
                                            totalStudents = pollProgress?.totalStudents ?: 0,
                                            closesAtLabel = pollProgress?.closesAt
                                                ?.let { circolareplus.util.formatDayMonth(it) }
                                                ?.takeIf { it.isNotBlank() },
                                            isExpired = pollProgress?.isExpired == true,
                                            isSubmitting = isSubmittingPoll,
                                            onSubmit = {
                                                // L'invio adesso arriva al server: è così che il
                                                // Rappresentante vede chi ha finito e che l'algoritmo
                                                // può partire quando hanno inviato tutti. Prima era solo
                                                // un interruttore sul telefono di chi votava.
                                                if (!isSubmittingPoll) {
                                                    isSubmittingPoll = true
                                                    coroutineScope.launch {
                                                        try {
                                                            val result = AppContainer.pollsRepository.submitVotes(poll.id)
                                                            AppContainer.settings.setPollSubmitted(poll.id, true)
                                                            submittedPollIds = submittedPollIds + poll.id
                                                            pollProgress = pollProgress?.copy(
                                                                hasSubmitted = true,
                                                                submittedCount = result.submittedCount,
                                                                totalStudents = result.totalStudents
                                                            )
                                                            // Se questo era l'ultimo invio mancante, il server ha già
                                                            // chiuso il sondaggio e calcolato le assegnazioni (vedi
                                                            // POST /:id/submit in polls.ts), ma qui lo si saprebbe solo
                                                            // ricaricando: senza questo refresh `allPolls`/`currentPoll`
                                                            // restavano quelli di prima, isCalculated risultava ancora
                                                            // falso lato client e il sondaggio restava "aperto" finché
                                                            // non si usciva e rientrava dalla schermata.
                                                            if (result.totalStudents > 0 && result.submittedCount >= result.totalStudents) {
                                                                pollsRefreshTrigger++
                                                            }
                                                        } catch (e: Exception) {
                                                            pollError = "Invio non riuscito: ${e.message}"
                                                        } finally {
                                                            isSubmittingPoll = false
                                                        }
                                                    }
                                                }
                                            },
                                            onReopen = {
                                                coroutineScope.launch {
                                                    try {
                                                        AppContainer.pollsRepository.withdrawSubmission(poll.id)
                                                        AppContainer.settings.setPollSubmitted(poll.id, false)
                                                        submittedPollIds = submittedPollIds - poll.id
                                                        pollProgress = pollProgress?.copy(
                                                            hasSubmitted = false,
                                                            submittedCount = (pollProgress?.submittedCount ?: 1) - 1
                                                        )
                                                    } catch (e: Exception) {
                                                        pollError = "Non sono riuscito ad annullare l'invio: ${e.message}"
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                    }
                    }
                }
                // Sempre presente: le schermate "sopra" le scorrono sopra e la coprono (vedi
                // shellRoute). Si nasconde quando c'e' la tastiera, per non galleggiarci sopra.
                // Tablet e iPad: barra laterale a sinistra, sempre visibile (la tastiera non la
                // copre).
                if (useRail) {
                    FloatingTabRail(
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it },
                        hazeState = hazeState,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                if (!useRail) androidx.compose.animation.AnimatedVisibility(
                    visible = !imeVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()
                ) {
                    FloatingTabBar(
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it },
                        dragPosition = tabBarDrag,
                        hazeState = hazeState
                    )
                }
                }
            }
        }
    }
        // Livello delle schermate "sopra" le tab (vedi shellRoute): se il dettaglio circolare si
        // apre da qui (Ricerca, Notifiche) anche questo livello fa il passo indietro.
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = -size.width * circolareplus.design.ailaUnderlayShift * detailProgress.value
            }
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = shellRoute,
                transitionSpec = {
                    val forward = targetState.depth > initialState.depth
                    // Material con un'origine: container transform, disegnato dalla schermata
                    // stessa (ailaContainerReveal). Qui solo chi sta sopra e quanto dura.
                    val containerKey = if (forward) targetState else initialState
                    // Liquid Glass: lo scorrimento lo fa la schermata stessa (glassSlide), con lo
                    // sfondo fermo; qui solo chi sta sopra.
                    if (AppTheme.isGlass || largeOrigin(circolareplus.design.AilaContainerTransform.originOf(containerKey)) != null) {
                        (androidx.compose.animation.EnterTransition.None togetherWith
                            androidx.compose.animation.ExitTransition.None).apply {
                            targetContentZIndex = if (forward) 1f else -1f
                        }
                    } else {
                        ailaPushTransition(forward = forward)
                    }
                },
                label = "shellRoute",
                modifier = Modifier.fillMaxSize()
            ) { route ->
            // Avanzamento del container transform di questa schermata: 0 = chiusa nell'elemento
            // di partenza, 1 = a tutto schermo. Legato alla transizione, che aspetta la fine.
            val containerOrigin = if (AppTheme.isGlass || route == ShellRoute.TABS) null
                else largeOrigin(circolareplus.design.AilaContainerTransform.originOf(route))
            val containerProgress = transition.animateFloat(
                // Aprendo la molla, chiudendo la curva a durata fissa (vedi ailaContainerCloseSpec).
                transitionSpec = {
                    if (targetState == androidx.compose.animation.EnterExitState.PostExit) circolareplus.design.ailaContainerCloseSpec()
                    else circolareplus.design.ailaContainerFloatSpring()
                },
                label = "containerTransform"
            ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            // Si espande solo entrando "in avanti" e si richiude solo tornando indietro da lei;
            // negli altri casi (es. resta sotto mentre se ne apre un'altra) resta intera.
            val revealing = route == circolareplus.design.AilaContainerTransform.lastForwardKey ||
                shellRoute.depth < route.depth
            // Apertura: parte solo dopo i primi due fotogrammi della pagina nuova. La prima volta
            // che una schermata si apre, comporla e disegnarla richiede un fotogramma lungo; se la
            // molla fosse gia' partita, la forma salterebbe avanti (il lag della prima apertura).
            // Cosi' quel lavoro si fa con la forma ancora ferma sul pulsante.
            val openProgress = remember {
                androidx.compose.animation.core.Animatable(
                    if (containerOrigin != null && transition.currentState == androidx.compose.animation.EnterExitState.PreEnter) 0f else 1f
                )
            }
            LaunchedEffect(Unit) {
                if (openProgress.value < 1f) {
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                    openProgress.animateTo(1f, circolareplus.design.ailaContainerFloatSpring())
                    // Solo se la pagina e' ancora quella aperta (non si e' gia' tornati indietro).
                    if (transition.targetState == androidx.compose.animation.EnterExitState.Visible) shellRevealDone.value = true
                }
            }
            // Liquid Glass, push alla iOS: posizione del contenuto in frazioni di larghezza
            // (0 = al suo posto, 1 = fuori a destra, -1/3 = scivolato sotto quella nuova). Lo
            // sfondo a macchie invece resta fermo e allineato a quello sotto: prima scorreva
            // insieme alla pagina e per un attimo si vedeva lo sfondo "cambiare".
            val forwardNow = navForward[0]
            val glassSlide = if (AppTheme.isGlass && route != ShellRoute.TABS) transition.animateFloat(
                transitionSpec = { circolareplus.design.ailaNavigationSpring() },
                label = "glassPush"
            ) { state ->
                when (state) {
                    androidx.compose.animation.EnterExitState.Visible -> 0f
                    androidx.compose.animation.EnterExitState.PreEnter -> if (forwardNow) 1f else -circolareplus.design.ailaUnderlayShift
                    androidx.compose.animation.EnterExitState.PostExit -> if (forwardNow) -circolareplus.design.ailaUnderlayShift else 1f
                }
            } else null
            // Fondo pieno: durante il push la schermata sopra non deve lasciar intravedere
            // quella sotto.
            Box(
                modifier = if (route == ShellRoute.TABS) Modifier.fillMaxSize() else Modifier
                    .fillMaxSize()
                    .then(
                        if (containerOrigin != null) Modifier.ailaContainerReveal(
                            // Il minimo dei due: aprendo comanda openProgress (in ritardo di due
                            // fotogrammi), chiudendo la curva della transizione, senza salti se si
                            // torna indietro a meta' apertura.
                            progress = { if (revealing) minOf(openProgress.value, containerProgress.value) else 1f },
                            origin = containerOrigin,
                            containerColor = AppTheme.CardSurface,
                            pageColor = AppTheme.BackgroundLight,
                            closing = { transition.targetState == androidx.compose.animation.EnterExitState.PostExit }
                        )
                            // La pagina in un suo livello: a ogni fotogramma si ridisegna solo la
                            // forma che cresce, il contenuto e' gia' pronto e si ricopia com'e'
                            // (niente scatti su schermate pesanti come Impostazioni).
                            .graphicsLayer {}
                        else Modifier
                    )
                    // Material: fondo pieno, copre le tab sotto. Liquid Glass: pagina trasparente
                    // che scorre sullo sfondo fermo dell'app, mentre quella di prima esce dall'altro
                    // lato (vedi glassSlide e ailaUnderlayShift).
                    .then(if (AppTheme.isGlass) Modifier else Modifier.background(AppTheme.BackgroundLight))
                    .then(
                        if (glassSlide != null) Modifier.graphicsLayer {
                            translationX = glassSlide.value * size.width
                        } else Modifier
                    )
                    .appSafeDrawingPadding()
                    // Prende i tocchi: sotto c'e' lo Scaffold con le tab, che non deve riceverli.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            ) {
            // Schermi larghi: le schermate con due colonne (Impostazioni, Profilo, Scheda classe,
            // Notifiche, Ricerca) arrivano a MaxWideContentWidth; l'Assistente resta una chat
            // a colonna singola.
            Box(modifier = Modifier.fillMaxHeight().then(
                if (twoPane && route != ShellRoute.ASSISTANT) Modifier.appContentWidth(circolareplus.design.MaxWideContentWidth)
                else Modifier.appContentWidth()
            )) {
            when (route) {
                ShellRoute.BACKGROUND_DEBUG -> {
                    circolareplus.platform.PlatformBackHandler { isInBackgroundDebugScreen = false }
                    BackgroundDebugScreen(
                        isSupported = circolareplus.platform.isBackgroundRefreshSupported(),
                        readLog = { AppContainer.settings.backgroundLog },
                        onClearLog = { AppContainer.settings.clearBackgroundLog() },
                        onSimulate = { circolareplus.platform.simulateBackgroundWakeUp() },
                        readBookmark = { AppContainer.settings.bgLastCircularNumber },
                        onRewindBookmark = { circolareplus.platform.rewindBackgroundBookmark() },
                        onBackClick = { isInBackgroundDebugScreen = false }
                    )
                }
                ShellRoute.SETTINGS -> {
                    circolareplus.platform.PlatformBackHandler { isInSettingsScreen = false }
                    SettingsScreen(
                        // Numero della build e commit: dice quale APK e' davvero installato.
                        appVersion = circolareplus.platform.appVersionName(),
                        apiKey = run {
                            apiKeyRevision
                            AppContainer.settings.userAiApiKey
                        },
                        onSaveApiKey = { newKey ->
                            AppContainer.settings.userAiApiKey = newKey
                            apiKeyRevision++
                        },
                        onTestApiKey = { key ->
                            // Si costruisce un classificatore con la chiave appena digitata, non
                            // con quella salvata: così si può provare una chiave prima di salvarla.
                            circolareplus.ai.ClientSideAiClassifier(userApiKey = key).testKey()
                        },
                        isDarkMode = AppTheme.isDarkMode,
                        onDarkModeChange = { enabled ->
                            AppTheme.isDarkMode = enabled
                            AppContainer.settings.isDarkMode = enabled
                        },
                        uiStyle = AppTheme.uiStyle,
                        onUiStyleChange = { style ->
                            AppTheme.uiStyle = style
                            AppContainer.settings.uiStyleKey = style.key
                        },
                        accent = AppTheme.accent,
                        onAccentChange = { accent ->
                            AppTheme.accent = accent
                            AppContainer.settings.accentKey = accent.key
                        },
                        isNotificationKindEnabled = { kind ->
                            AppContainer.settings.isNotificationKindEnabled(kind.key)
                        },
                        onNotificationKindChange = { kind, enabled ->
                            AppContainer.settings.setNotificationKindEnabled(kind.key, enabled)
                            syncPushPreferences()
                        },
                        boardNotificationsEnabled = currentProfile?.notificationBoardEnabled ?: true,
                        onToggleBoardNotifications = { enabled ->
                            coroutineScope.launch {
                                try {
                                    AppContainer.authRepository.toggleBoardNotifications(enabled)
                                } catch (e: Exception) {
                                    // Best-effort
                                }
                            }
                        },
                        systemNotificationsEnabled = AppContainer.settings.isSystemNotificationsEnabled,
                        onToggleSystemNotifications = { enabled ->
                            AppContainer.settings.isSystemNotificationsEnabled = enabled
                            syncPushPreferences()
                        },
                        aiProvider = AppContainer.settings.aiProvider,
                        onAiProviderChange = { provider ->
                            AppContainer.settings.aiProvider = provider
                        },
                        // Calcolati una volta sola: prima si rileggevano (memoria del telefono,
                        // AICore di sistema, catalogo) a ogni ridisegno delle Impostazioni, anche
                        // durante l'animazione d'ingresso e lo scorrimento: da qui lo stuttering.
                        localAiUnavailableReason = remember { circolareplus.ai.onDeviceAiUnavailableReason() },
                        showLocalAiSection = remember { circolareplus.ai.isOnDeviceAiOfferedHere() },
                        deviceRamMb = remember { circolareplus.ai.totalDeviceRamMb() },
                        localModels = remember {
                            circolareplus.ai.LocalAiCatalog.selectableFor(circolareplus.ai.totalDeviceRamMb())
                        },
                        selectedLocalModelId = remember { AppContainer.selectedLocalModel().id },
                        onSelectLocalModel = { model ->
                            AppContainer.settings.localAiModelId = model.id
                            // Il motore tiene in memoria il modello caricato in precedenza: se
                            // non lo si scarica, la prima classificazione dopo il cambio userebbe
                            // ancora quello vecchio.
                            AppContainer.localLlm.unload()
                        },
                        isLocalModelInstalled = { model ->
                            AppContainer.localModelStore.isInstalled(model)
                        },
                        downloadLocalModel = { model, onProgress ->
                            when (val result = AppContainer.localModelStore.download(model, onProgress)) {
                                is circolareplus.ai.ModelDownloadState.Installed ->
                                    "${model.displayName} è pronto. Le prossime circolari verranno " +
                                        "analizzate sul telefono, anche senza connessione."
                                is circolareplus.ai.ModelDownloadState.Failed -> result.reason
                                else -> "Download non completato."
                            }
                        },
                        onCancelLocalModelDownload = { model ->
                            AppContainer.localModelStore.cancelDownload(model)
                        },
                        onDeleteLocalModel = { model ->
                            AppContainer.localLlm.unload()
                            AppContainer.localModelStore.delete(model)
                        },
                        // Una volta sola all'apertura: scorre la cartella dei modelli sul disco, e
                        // prima lo faceva a ogni ridisegno delle Impostazioni (stuttering).
                        orphanModelBytes = remember { AppContainer.localModelStore.orphanBytes() },
                        onDeleteOrphanModels = { AppContainer.localModelStore.deleteOrphans() },
                        onTestLocalModel = {
                            val model = AppContainer.selectedLocalModel()
                            circolareplus.ai.LocalAiClassifier(
                                model = model,
                                modelPath = AppContainer.localModelStore.installedPath(model),
                                llm = AppContainer.localLlm
                            ).testConfiguration()
                        },
                        showDebugMenu = debugMenuUnlocked || circolareplus.platform.isDebugBuild(),
                        onUnlockDebugMenu = {
                            AppContainer.settings.isDebugMenuEnabled = true
                            debugMenuUnlocked = true
                        },
                        onOpenBackgroundDebug = { isInBackgroundDebugScreen = true },
                        onBackClick = { isInSettingsScreen = false }
                    )
                }
                ShellRoute.PROFILE -> {
                    circolareplus.platform.PlatformBackHandler { isInProfileScreen = false }
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenBackBar(title = "Profilo", onBackClick = { isInProfileScreen = false })
                        ProfileScreen(
                            user = user,
                            showHeader = false,
                            profile = profile,
                            userAiApiKey = run {
                                apiKeyRevision // dipendenza esplicita: rilegge dopo un salvataggio
                                AppContainer.settings.userAiApiKey
                            },
                            onOpenSettings = {
                                isInBackgroundDebugScreen = false
                                isInSettingsScreen = true
                            },
                            onManageClassRoster = { isInClassRosterScreen = true },
                            onLogoutClick = {
                                // Toglie il token push dal server (con la sessione ancora valida)
                                // e poi chiude quella locale, dati dell'account compresi.
                                AppContainer.authRepository.signOut(AppContainer.fcmRepository, currentPushPlatform())
                                assistantMessages.clear()
                                assistantConversations = emptyList()
                                // Analisi (della classe dell'account) e campanella restano in memoria
                                // oltre il login: chi entra dopo ricomincia dai propri.
                                classifications.clear()
                                notificationLog = emptyList()
                                isInProfileScreen = false
                                currentUser = null
                                currentProfile = null
                            },
                            onDeleteAccount = { password ->
                                try {
                                    AppContainer.authRepository.deleteAccount(password)
                                    // Il token push resta valido solo finche' esiste l'utente: il
                                    // server lo ha gia' cancellato a cascata, qui basta uscire.
                                    assistantMessages.clear()
                                    assistantConversations = emptyList()
                                    classifications.clear()
                                    notificationLog = emptyList()
                                    isInProfileScreen = false
                                    currentUser = null
                                    currentProfile = null
                                    null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: circolareplus.data.remote.ApiException) {
                                    e.message ?: "Eliminazione non riuscita."
                                } catch (e: Exception) {
                                    "Impossibile eliminare l'account: controlla la connessione e riprova."
                                }
                            }
                        )
                    }
                }
                ShellRoute.CLASS_ROSTER -> {
                    circolareplus.platform.PlatformBackHandler { isInClassRosterScreen = false }
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenBackBar(title = "Scheda Classe", onBackClick = { isInClassRosterScreen = false })
                        if (classRosterActionError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)
                                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                    .background(AppTheme.TintRed)
                                    .clickable { classRosterActionError = null }
                                    .padding(AppTheme.Space12),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    AppIcons.Warning(modifier = Modifier.size(15.dp), color = AppTheme.TintRedInk)
                                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                                    Text(
                                        text = "$classRosterActionError",
                                        fontSize = 12.sp,
                                        color = AppTheme.TintRedInk
                                    )
                                }
                                AppIcons.Close(modifier = Modifier.size(15.dp), color = AppTheme.TintRedInk)
                            }
                        }
                        LoadableContent(isLoading = isClassRosterLoading, error = classRosterError) {
                            ClassRosterScreen(
                                entries = classRosterEntries,
                                currentUserId = user.id,
                                disciplinePairs = disciplinePairs,
                                onAddDisciplinePair = { studentA, studentB, duration ->
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.ratingsRepository.addDisciplinePair(studentA, studentB, duration)
                                            disciplinePairs = AppContainer.ratingsRepository.listDisciplinePairs()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            classRosterActionError = "Coppia non salvata: ${e.message}"
                                        }
                                    }
                                },
                                onRemoveDisciplinePair = { id ->
                                    val previous = disciplinePairs
                                    disciplinePairs = disciplinePairs.filter { it.id != id }
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.ratingsRepository.removeDisciplinePair(id)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            disciplinePairs = previous
                                            classRosterActionError = "Coppia non rimossa: ${e.message}"
                                        }
                                    }
                                },
                                onDidacticChange = { studentId, value ->
                                    val previous = classRosterEntries
                                    classRosterEntries = classRosterEntries.map {
                                        if (it.studentId == studentId) it.copy(didactic = value) else it
                                    }
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.ratingsRepository.setRating(studentId, value, null)
                                        } catch (e: Exception) {
                                            classRosterEntries = previous
                                            classRosterActionError = "Valutazione non salvata: ${e.message}"
                                        }
                                    }
                                },
                                onBehaviorChange = { studentId, value ->
                                    val previous = classRosterEntries
                                    classRosterEntries = classRosterEntries.map {
                                        if (it.studentId == studentId) it.copy(behavior = value) else it
                                    }
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.ratingsRepository.setRating(studentId, null, value)
                                        } catch (e: Exception) {
                                            classRosterEntries = previous
                                            classRosterActionError = "Valutazione non salvata: ${e.message}"
                                        }
                                    }
                                },
                                onSecurityGuardChange = { studentId, enabled ->
                                    val previous = classRosterEntries
                                    // Una sola Guardia per classe: sceglierne una toglie la precedente.
                                    classRosterEntries = classRosterEntries.map {
                                        it.copy(
                                            isSecurityGuard = if (it.studentId == studentId) enabled
                                            else if (enabled) false else it.isSecurityGuard
                                        )
                                    }
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.proposalsRepository.setSecurityGuard(if (enabled) studentId else null)
                                        } catch (e: Exception) {
                                            classRosterEntries = previous
                                            classRosterActionError = "Guardia non salvata: ${e.message}"
                                        }
                                    }
                                },
                                onPriorityPassChange = { studentId, enabled ->
                                    val previous = classRosterEntries
                                    classRosterEntries = classRosterEntries.map {
                                        if (it.studentId == studentId) it.copy(priorityPass = enabled) else it
                                    }
                                    coroutineScope.launch {
                                        try {
                                            AppContainer.ratingsRepository.setPriorityPass(studentId, enabled)
                                        } catch (e: Exception) {
                                            classRosterEntries = previous
                                            classRosterActionError = "Priority Pass non salvato: ${e.message}"
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                ShellRoute.ASSISTANT -> {
                    circolareplus.platform.PlatformBackHandler { isInAssistantScreen = false }
                    AssistantChatScreen(
                        messages = assistantMessages,
                        isThinking = isAssistantThinking,
                        conversations = assistantConversations,
                        onSend = { question -> askAssistant(question) },
                        thinkingEnabled = assistantThinking,
                        thinkingAvailable = AppContainer.selectedLocalModel().supportsThinking,
                        onThinkingChange = { enabled ->
                            AppContainer.settings.assistantThinkingEnabled = enabled
                            assistantThinking = enabled
                        },
                        onBackClick = { isInAssistantScreen = false },
                        onClearChat = {
                            // "Nuova chat" archivia, non cancella: quella di prima e' gia' nello
                            // storico, qui basta ripartire con un id nuovo.
                            assistantMessages.clear()
                            assistantConversationId = "c${currentTimeMillis()}"
                            // I dati raccolti restano: sono gli stessi di un minuto fa e
                            // ricaricarli vorrebbe dire far aspettare la prima domanda della
                            // chat nuova per niente.
                        },
                        onOpenConversation = { conversation ->
                            // Si puo' aprire anche con una risposta in arrivo: la risposta e'
                            // instradata per id, non per "chat attualmente a schermo".
                            assistantMessages.clear()
                            assistantMessages.addAll(conversation.messages)
                            assistantConversationId = conversation.id
                        },
                        onDeleteConversation = { id ->
                            AppContainer.settings.deleteAssistantConversation(id)
                            assistantConversations = AppContainer.settings.listAssistantConversations()
                            // Se era quella aperta, la chat torna vuota: lasciarla a schermo
                            // significherebbe che il primo messaggio nuovo la fa riapparire
                            // nello storico appena cancellata.
                            if (id == assistantConversationId) {
                                assistantMessages.clear()
                                assistantConversationId = "c${currentTimeMillis()}"
                            }
                        },
                        onOpenSource = { source ->
                            isInAssistantScreen = false
                            isInSearchScreen = false
                            when (source.kind) {
                                AssistantSourceKind.CIRCULAR -> {
                                    val number = source.circularNumber
                                    val circular = circulars.firstOrNull { it.number == number }
                                    if (circular != null) selectedCircularForDetail = circular
                                    classSection = ClassSection.CIRCULARS
                                    selectedTab = MainTab.CLASS
                                }
                                AssistantSourceKind.CALENDAR -> selectedTab = MainTab.CALENDAR
                                AssistantSourceKind.BOARD -> {
                                    classSection = ClassSection.BOARD
                                    selectedTab = MainTab.CLASS
                                }
                                AssistantSourceKind.POLL -> selectedTab = MainTab.POLLS
                                AssistantSourceKind.SEAT_MAP -> selectedTab = MainTab.SEATMAP
                                AssistantSourceKind.CLASS -> isInClassRosterScreen = true
                            }
                        }
                    )
                }
                ShellRoute.SEARCH -> {
                    circolareplus.platform.PlatformBackHandler { isInSearchScreen = false }
                    SearchScreen(
                        circulars = circulars,
                        calendarEvents = calendarEvents,
                        proposals = proposals,
                        recentSearches = recentSearches,
                        onBackClick = { isInSearchScreen = false },
                        onSubmitQuery = { query ->
                            AppContainer.settings.rememberSearch(query)
                            recentSearches = AppContainer.settings.recentSearches
                        },
                        onOpenAssistant = { question ->
                            isInAssistantScreen = true
                            // La domanda parte da sola: chi ha gia' scritto "gita a Milano" e ha
                            // premuto il pulsante AI la sua domanda l'ha gia' fatta, farla
                            // riscrivere in chat sarebbe un passaggio in piu' e basta.
                            if (question.isNotBlank()) askAssistant(question)
                        },
                        onOpenCircular = { circular ->
                            isInSearchScreen = false
                            selectedCircularForDetail = circular
                            classSection = ClassSection.CIRCULARS
                            selectedTab = MainTab.CLASS
                        },
                        onOpenCirculars = {
                            isInSearchScreen = false
                            classSection = ClassSection.CIRCULARS
                            selectedTab = MainTab.CLASS
                        },
                        onOpenCalendar = {
                            isInSearchScreen = false
                            selectedTab = MainTab.CALENDAR
                        },
                        onOpenBoard = {
                            isInSearchScreen = false
                            classSection = ClassSection.BOARD
                            selectedTab = MainTab.CLASS
                        }
                    )
                }
                ShellRoute.NOTIFICATIONS -> {
                    circolareplus.platform.PlatformBackHandler { isInNotificationsScreen = false }
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenBackBar(title = "Notifiche", onBackClick = { isInNotificationsScreen = false })
                        NotificationsScreen(
                            notifications = notificationLog,
                            onNotificationClick = { entry -> navigateForNotificationCategory(entry.category) }
                        )
                    }
                }
                ShellRoute.SEATMAP_PROPOSALS -> {
                    // Le tre proposte restano tutte disponibili finche' non se ne sceglie una:
                    // l'anteprima con l'occhio non ne scarta nessuna. Il back le abbandona.
                    circolareplus.platform.PlatformBackHandler { proposalOptions = emptyList() }
                    val proposalsStudentsMap = remember(classmates, user) {
                        classmates.associateBy { it.id } + (user.id to user)
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenBackBar(
                            title = "Proposte di disposizione",
                            onBackClick = { proposalOptions = emptyList() }
                        )
                        SeatMapProposalsScreen(
                            proposals = proposalOptions,
                            studentsMap = proposalsStudentsMap,
                            seatsPerDesk = lastRequestedSeatsPerDesk,
                            isBusy = isGeneratingProposals,
                            // Scegliere una proposta non la pubblica direttamente: apre l'editor
                            // manuale (swap-by-tap con ricalcolo live) da cui il Rappresentante
                            // pubblica quando e' pronto. Le proposte NON vengono scartate qui:
                            // il back dall'editor deve tornare a loro, non alla mappa iniziale.
                            onSelect = { chosen ->
                                originalSeatMapProposal = chosen.assignments
                                editingSeatMapProposal = chosen.assignments
                            }
                        )
                    }
                }
                ShellRoute.SEATMAP_EDITOR -> {
                    // Durante l'animazione di uscita dall'editor editingSeatMapProposal e' gia'
                    // null: si continua a mostrare l'ultima disposizione invece di andare in crash.
                    editingSeatMapProposal?.let { lastEditorAssignments[0] = it }
                    val currentAssignments = editingSeatMapProposal ?: lastEditorAssignments[0]
                    // Il back torna alla schermata precedente (le proposte), non alla mappa.
                    circolareplus.platform.PlatformBackHandler {
                        editingSeatMapProposal = null
                        originalSeatMapProposal = null
                    }
                    val editorBreakdown = remember(
                        currentAssignments,
                        seatMapOptimizerProfiles,
                        seatMapOptimizerRatings,
                        seatMapOptimizerSocialMap,
                        seatMapOptimizerHistory,
                        seatMapOptimizerWeights,
                        seatMapOptimizerIsSmallClass,
                        seatMapOptimizerDisciplinePairs
                    ) {
                        SeatMapOptimizer.scoreLayout(
                            assignments = currentAssignments,
                            profiles = seatMapOptimizerProfiles,
                            ratings = seatMapOptimizerRatings,
                            socialPreferences = seatMapOptimizerSocialMap,
                            history = seatMapOptimizerHistory,
                            weights = seatMapOptimizerWeights,
                            isSmallClass = seatMapOptimizerIsSmallClass,
                            disciplinePairs = seatMapOptimizerDisciplinePairs
                        )
                    }
                    val editorSatisfaction = remember(currentAssignments, seatMapOptimizerSocialMap) {
                        SeatMapOptimizer.voteSatisfaction(currentAssignments, seatMapOptimizerSocialMap)
                    }
                    val editorStudentsMap = remember(classmates, user) {
                        classmates.associateBy { it.id } + (user.id to user)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        ScreenBackBar(
                            title = "Modifica disposizione",
                            onBackClick = {
                                editingSeatMapProposal = null
                                originalSeatMapProposal = null
                            }
                        )
                        SeatMapEditorScreen(
                            assignments = currentAssignments,
                            studentsMap = editorStudentsMap,
                            socialPreferences = seatMapOptimizerSocialMap,
                            breakdown = editorBreakdown,
                            satisfaction = editorSatisfaction,
                            seatsPerDesk = lastRequestedSeatsPerDesk,
                            isPublishing = isGeneratingProposals,
                            onSwapSeats = { deskIndex1, seatIndex1, deskIndex2, seatIndex2 ->
                                editingSeatMapProposal = SeatMapOptimizer.swapSeats(
                                    currentAssignments,
                                    SeatMapOptimizer.SeatRef(deskIndex1, seatIndex1),
                                    SeatMapOptimizer.SeatRef(deskIndex2, seatIndex2)
                                )
                            },
                            onRestore = { editingSeatMapProposal = originalSeatMapProposal },
                            onPublish = {
                                coroutineScope.launch {
                                    isGeneratingProposals = true
                                    try {
                                        AppContainer.seatMapRepository.publish(currentAssignments)
                                        seatMapAssignments = currentAssignments
                                        editingSeatMapProposal = null
                                        originalSeatMapProposal = null
                                        // Pubblicata: le proposte non servono più, si torna alla mappa.
                                        proposalOptions = emptyList()
                                    } catch (e: Exception) {
                                        seatMapActionError = "Pubblicazione non riuscita: ${e.message}"
                                    } finally {
                                        isGeneratingProposals = false
                                    }
                                }
                            }
                        )
                    }
                }
                // Le tab stanno sotto, nello Scaffold: qui niente (livello trasparente).
                ShellRoute.TABS -> {}
            }
            }
            }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedCircularForDetail != null,
            // Glass: lo scorrimento lo fa il dettaglio stesso (detailGlassSlide), con lo sfondo fermo.
            enter = if (detailOrigin != null || AppTheme.isGlass) androidx.compose.animation.EnterTransition.None else circolareplus.design.ailaPushEnter(),
            exit = if (detailOrigin != null || AppTheme.isGlass) androidx.compose.animation.ExitTransition.None else circolareplus.design.ailaPushExit()
        ) {
        val circularForDetail = selectedCircularForDetail ?: lastDetailCircular[0]
        val detailGlassSlide = if (AppTheme.isGlass) transition.animateFloat(
            transitionSpec = { circolareplus.design.ailaNavigationSpring() },
            label = "detailGlassPush"
        ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 0f else 1f } else null
        val detailProgressLocal = transition.animateFloat(
            transitionSpec = { circolareplus.design.ailaContainerFloatSpring() },
            label = "detailContainerTransform"
        ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
        // Il dettaglio sta sopra le tab: questo livello "prende" i tocchi, altrimenti quelli
        // sulle zone senza pulsanti arriverebbero alla schermata sotto.
        if (circularForDetail != null) Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (detailOrigin != null) Modifier.ailaContainerReveal(
                        progress = { detailProgressLocal.value },
                        origin = detailOrigin,
                        containerColor = AppTheme.CardSurface,
                        pageColor = AppTheme.BackgroundLight
                    ) else Modifier
                )
                .then(
                    if (detailGlassSlide != null) Modifier
                        .graphicsLayer { translationX = detailGlassSlide.value * size.width }
                    else Modifier
                )
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        ) {
        // Prima lo swipe/tasto indietro di sistema chiudeva l'app anche da qui: nessuna
        // schermata a schermo intero intercettava il back di sistema, solo la freccia disegnata
        // nella UI. Ora il back di sistema fa la stessa cosa della freccia.
        circolareplus.platform.PlatformBackHandler(enabled = selectedCircularForDetail != null) { selectedCircularForDetail = null }
        circularDetailContent(circularForDetail) { selectedCircularForDetail = null }
        }
        }
        // Nuovo evento (Material): il foglio scende dall'alto. Sopra le tab e i
        // dettagli; in Glass e' il foglio di sistema (vedi addEventDialog).
        if (!AppTheme.isGlass) addEventDialog()
        // Dettaglio di un evento (Material): la card si allarga nella pagina e ci si richiude.
        androidx.compose.animation.AnimatedVisibility(
            visible = eventDetailToShow != null && !AppTheme.isGlass,
            enter = if (eventDetailOrigin != null) androidx.compose.animation.EnterTransition.None else circolareplus.design.ailaPushEnter(),
            exit = if (eventDetailOrigin != null) androidx.compose.animation.ExitTransition.None else circolareplus.design.ailaPushExit()
        ) {
            val eventForDetail = eventDetailToShow ?: lastEventDetail[0]
            val eventProgress = transition.animateFloat(
                transitionSpec = {
                    if (targetState == androidx.compose.animation.EnterExitState.PostExit) circolareplus.design.ailaContainerCloseSpec()
                    else circolareplus.design.ailaContainerFloatSpring()
                },
                label = "eventDetailContainer"
            ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            if (eventForDetail != null) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (eventDetailOrigin != null) Modifier
                            .ailaContainerReveal(
                                progress = { eventProgress.value },
                                origin = eventDetailOrigin,
                                containerColor = AppTheme.CardSurface,
                                pageColor = AppTheme.BackgroundLight
                            )
                            .graphicsLayer {}
                        else Modifier
                    )
                    .background(AppTheme.BackgroundLight)
                    .appSafeDrawingPadding()
                    // Il dettaglio sta sopra le tab: prende i tocchi, altrimenti quelli sulle zone
                    // senza pulsanti arriverebbero al calendario sotto.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            ) {
                circolareplus.platform.PlatformBackHandler(enabled = eventDetailToShow != null) { eventDetailToShow = null }
                EventDetailPage(
                    event = eventForDetail,
                    classmates = classmates,
                    currentUser = user,
                    onOpenCircular = openCircularFromEvent,
                    onBack = { eventDetailToShow = null },
                    // La pagina si richiude nella card e poi la card esce (vedi deleteEventAnimated).
                    onDelete = {
                        eventDetailToShow = null
                        deleteEventAnimated(eventForDetail, startDelayMs = 560L)
                    }
                )
            }
        }
    }
}

/**
 * Barra superiore con freccia indietro per le schermate a schermo intero fuori dalla bottom bar.
 * Ora è solo un rinvio ad [circolareplus.design.AilaBackBar]: prima ognuna di queste schermate
 * aveva una barra scritta a mano con altezze e spaziature leggermente diverse.
 */
@Composable
private fun ScreenBackBar(title: String, onBackClick: () -> Unit) {
    circolareplus.design.AilaBackBar(title = title, onBackClick = onBackClick)
}

@Composable
private fun PreferencesOpenBanner(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = AppTheme.TintBlue),
        modifier = Modifier
            .fillMaxWidth()
            // Il banner sta sopra l'intestazione della Mappa Posti: senza padding superiore
            // finiva incollato al bordo alto dello schermo.
            .padding(horizontal = AppTheme.Space16)
            .padding(top = AppTheme.Space16)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Il Rappresentante ha aperto la votazione preferenze. Tocca per votare →",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.TintBlueInk,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(modifier = Modifier.height(AppTheme.Space8))
}

/**
 * Overlay di caricamento/errore comune a tutte le tab con dati remoti. L'errore non è più una
 * riga di testo rosso al centro dello schermo (senza alcun modo di ritentare se non uscire e
 * rientrare dalla scheda) ma lo stato curato del mockup, con il pulsante "Riprova" quando la
 * schermata sa come ricaricarsi.
 */
@Composable
private fun LoadableContent(
    isLoading: Boolean,
    error: String?,
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.PrimaryBlue)
            }
        }
        error != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                circolareplus.design.AilaErrorState(message = error, onRetry = onRetry)
            }
        }
        else -> content()
    }
}

/** Converte epoch millis (UTC, mezzanotte) in "AAAA-MM-GG" senza dipendenze esterne (funziona
 * identica su Android/iOS): algoritmo civil_from_days di Howard Hinnant. */
private fun epochMillisToIsoDate(millis: Long): String {
    val z = millis / 86_400_000L + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    fun pad(n: Long, width: Int) = n.toString().padStart(width, '0')
    return "${pad(year, 4)}-${pad(m, 2)}-${pad(d, 2)}"
}

/** Ordine di visualizzazione delle categorie nel selettore "Tipo di evento". */
private val EVENT_CATEGORY_ORDER = listOf(
    CalendarEventCategory.VERIFICA,
    CalendarEventCategory.INTERROGAZIONE,
    CalendarEventCategory.PAGAMENTO,
    CalendarEventCategory.USCITA_DIDATTICA,
    CalendarEventCategory.AVVISO,
    CalendarEventCategory.ALTRO
)

private fun eventCategoryLabel(category: CalendarEventCategory): String = when (category) {
    CalendarEventCategory.VERIFICA -> "Verifica"
    CalendarEventCategory.INTERROGAZIONE -> "Interrogazione"
    CalendarEventCategory.PAGAMENTO -> "Pagamento"
    CalendarEventCategory.USCITA_DIDATTICA -> "Uscita"
    CalendarEventCategory.AVVISO -> "Avviso"
    CalendarEventCategory.ALTRO -> "Altro"
}

@Composable
private fun eventCategoryIcon(category: CalendarEventCategory, modifier: Modifier, color: Color) {
    when (category) {
        CalendarEventCategory.VERIFICA -> AppIcons.Pencil(modifier, color)
        CalendarEventCategory.INTERROGAZIONE -> AppIcons.ChatBubble(modifier, color)
        CalendarEventCategory.PAGAMENTO -> AppIcons.Card(modifier, color)
        CalendarEventCategory.USCITA_DIDATTICA -> AppIcons.Bus(modifier, color)
        CalendarEventCategory.AVVISO -> AppIcons.Warning(modifier, color)
        CalendarEventCategory.ALTRO -> AppIcons.Sparkle(modifier, color)
    }
}

/** Passi del foglio di creazione evento: menu di scelta, AILA Assistant, compilazione manuale. */
/**
 * Letture avviate mentre si vede ancora la schermata di caricamento (vedi il ripristino della
 * sessione in MainAppShell) e consumate dai caricamenti veri, che prima partivano solo dopo, quando
 * la Home era gia' composta, o addirittura solo entrando nella tab. Ogni voce si consuma una volta
 * sola e solo se e' fresca; se la lettura e' fallita o e' troppo vecchia, il chiamante fa la sua
 * lettura come prima. Nessun cambio di comportamento se non il tempo risparmiato.
 */
private object StartupPrefetch {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    private class Entry(val deferred: kotlinx.coroutines.Deferred<Any?>, val startedAt: kotlin.time.TimeMark)
    private val pending = mutableMapOf<String, Entry>()
    private const val MAX_AGE_SECONDS = 60

    private fun <T> start(key: String, block: suspend () -> T) {
        pending[key] = Entry(scope.async { block() }, kotlin.time.TimeSource.Monotonic.markNow())
    }

    /** Fa partire in parallelo le letture che servono subito dopo l'ingresso. */
    fun begin() {
        start("calendar") { AppContainer.calendarRepository.listEvents() }
        start("proposals") { AppContainer.proposalsRepository.listProposals() }
        start("students") { AppContainer.usersRepository.listStudents() }
        start("polls") { AppContainer.pollsRepository.listPolls() }
        start("rankingPolls") { AppContainer.rankingPollsRepository.listPolls() }
    }

    /** Il risultato della lettura anticipata, o null se manca, e' fallita o e' troppo vecchia. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> take(key: String): T? {
        val entry = pending.remove(key) ?: return null
        if (entry.startedAt.elapsedNow().inWholeSeconds >= MAX_AGE_SECONDS) return null
        return try {
            entry.deferred.await() as T?
        } catch (e: CancellationException) {
            // Annullata la mia coroutine: va propagato. Annullata la lettura: si rifa' da capo.
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
            null
        } catch (e: Exception) {
            null
        }
    }
}

private enum class EventCreationStep { MENU, ASSISTANT, MANUAL }

/** Bozza estratta dal testo libero scritto in AILA Assistant. */
private data class AiEventDraft(
    val title: String,
    val subject: String,
    val category: CalendarEventCategory,
    val dateMillis: Long?,
    val time: String?,
    val notes: String?
)

private val AI_PROMPT_EXAMPLES = listOf(
    "Compito di inglese domani",
    "Interrogazione di storia lunedì alle 9",
    "Verifica di matematica venerdì alle 10",
    "Uscita didattica al museo giovedì"
)

/**
 * Converte una data civile in millisecondi epoch (UTC, mezzanotte): l'inverso di
 * [circolareplus.util.civilFromEpochMillis], algoritmo days_from_civil di Howard Hinnant.
 */
private fun civilToEpochMillis(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365L + yoe / 4 - yoe / 100 + doy
    val days = era * 146097L + doe - 719468L
    return days * 86_400_000L
}

/**
 * Interpretazione euristica del testo libero scritto in AILA Assistant: niente chiamata di
 * rete, solo pattern matching su parole chiave italiane (tipologia, materia, giorno, ora). Non è
 * un vero modello linguistico, ma basta a mostrare all'utente un evento già compilato da
 * rifinire prima di salvarlo, come nel mockup di riferimento.
 */
/**
 * Converte EventDraft (da AI) a AiEventDraft (formato UI).
 */
private fun aiEventDraftFromAi(draft: EventDraft): AiEventDraft {
    val category = try {
        CalendarEventCategory.valueOf(draft.category)
    } catch (e: Exception) {
        CalendarEventCategory.ALTRO
    }

    val dateMillis = draft.dateIso?.let { iso ->
        circolareplus.util.parseIsoDate(iso)?.let { civil ->
            civilToEpochMillis(civil.year, civil.month, civil.day)
        }
    }

    return AiEventDraft(
        title = draft.title,
        subject = draft.subject,
        category = category,
        dateMillis = dateMillis,
        time = draft.timeHm,
        notes = draft.notes
    )
}

private fun parseAiEventPrompt(raw: String): AiEventDraft {
    val text = raw.trim()
    val lower = text.lowercase()

    val category = when {
        Regex("\\b(verifica|compito in classe)\\b").containsMatchIn(lower) -> CalendarEventCategory.VERIFICA
        Regex("\\b(interrogazione|orale)\\b").containsMatchIn(lower) -> CalendarEventCategory.INTERROGAZIONE
        Regex("\\b(pagamento|quota|contributo|versamento)\\b").containsMatchIn(lower) -> CalendarEventCategory.PAGAMENTO
        Regex("\\b(uscita|gita|visita)\\b").containsMatchIn(lower) -> CalendarEventCategory.USCITA_DIDATTICA
        Regex("\\b(avviso|comunicazione|scadenza)\\b").containsMatchIn(lower) -> CalendarEventCategory.AVVISO
        else -> CalendarEventCategory.ALTRO
    }

    val subject = Regex("\\bdi ([a-zàèéìòù]+)").find(lower)
        ?.groupValues?.get(1)
        ?.replaceFirstChar { it.uppercase() }
        ?: ""

    val timeMatch = Regex("\\balle?\\s+(\\d{1,2})([:.](\\d{2}))?").find(lower)
    val time = timeMatch?.let { m ->
        val h = m.groupValues[1].padStart(2, '0')
        val min = m.groupValues[3].ifBlank { "00" }
        "$h:$min"
    }

    fun dateInDays(days: Int): Long {
        val civil = circolareplus.util.civilFromEpochMillis(currentTimeMillis() + days * 86_400_000L)
        return civilToEpochMillis(civil.year, civil.month, civil.day)
    }

    val today = circolareplus.util.today()
    val todayWeekday = circolareplus.util.weekdayOf(today)
    val weekdayNames = listOf(
        "lunedì" to 0, "martedì" to 1, "mercoledì" to 2, "giovedì" to 3,
        "venerdì" to 4, "sabato" to 5, "domenica" to 6
    )
    val dateMillis: Long? = when {
        lower.contains("dopodomani") -> dateInDays(2)
        lower.contains("domani") -> dateInDays(1)
        lower.contains("oggi") -> dateInDays(0)
        else -> weekdayNames.firstOrNull { (name, _) -> lower.contains(name) }?.let { (_, idx) ->
            val delta = ((idx - todayWeekday + 7) % 7).let { if (it == 0) 7 else it }
            dateInDays(delta)
        }
    }

    return AiEventDraft(
        title = text
            .replace(Regex("^(inserisci|aggiungi|segna|metti|crea)(mi)?\\s+(un|una|uno|il|la|lo|l')?\\s*", RegexOption.IGNORE_CASE), "")
            .ifBlank { text }
            .replaceFirstChar { it.uppercase() },
        subject = subject,
        category = category,
        dateMillis = dateMillis,
        time = time,
        notes = null
    )
}

/**
 * Foglio di creazione evento in stile Material 3: al posto del vecchio `AlertDialog` con tutti i
 * campi già in vista, si apre su un menu con due scelte rapide ("AILA Assistant" per compilazione
 * assistita, "Crea manualmente" per i campi classici), che è il pattern del mockup di riferimento.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddCalendarEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, CalendarEventCategory, List<String>?, String?) -> Unit,
    initialStep: EventCreationStep = EventCreationStep.MENU,
    initialDateIso: String? = null,
    /** true quando il chiamante vuole chiudere il foglio (evento creato): scende e poi chiama [onDismiss]. */
    closeRequested: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(initialStep) }

    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    // Precompilata quando si tocca "+" su un giorno specifico della griglia: prima non c'era modo
    // di legare un nuovo evento a un giorno preciso, si doveva sempre riaprire il selettore data.
    // Non si creano eventi nel passato: oggi e' la prima data ammessa (a mezzanotte UTC, come i
    // millisecondi del selettore di data).
    val todayCivil = remember { circolareplus.util.today() }
    val todayMillis = remember { civilToEpochMillis(todayCivil.year, todayCivil.month, todayCivil.day) }
    var selectedDateMillis by remember {
        mutableStateOf(
            initialDateIso?.let { iso -> circolareplus.util.parseIsoDate(iso) }
                ?.let { civil -> civilToEpochMillis(civil.year, civil.month, civil.day) }
                ?.takeIf { it >= todayMillis }
        )
    }
    // Anche una data arrivata dall'assistente (es. "la verifica di ieri") puo' essere passata.
    val isPastDate = selectedDateMillis?.let { it < todayMillis } == true
    var showDatePicker by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CalendarEventCategory.VERIFICA) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiFilled by remember { mutableStateOf(false) }
    var aiFallbackNotice by remember { mutableStateOf(false) }
    var isForAllClass by remember { mutableStateOf(true) }
    var selectedRecipientUserIds by remember { mutableStateOf<List<String>>(emptyList()) }
    // Elenco selezionabile per "Persone specifiche": prima non veniva caricato da nessuna parte,
    // la sezione mostrava solo la scritta "Seleziona fino a 5 persone" senza un solo nome sotto.
    // Si carica qui, non da `classmates` di MainAppShell, perché quello arriva popolato solo dopo
    // una visita alla tab Mappa Posti — un rappresentante che apre "Nuovo evento" per primo non lo
    // troverebbe mai valorizzato.
    var recipientCandidates by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoadingRecipients by remember { mutableStateOf(false) }
    var recipientsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isForAllClass) {
        if (!isForAllClass && recipientCandidates.isEmpty() && !isLoadingRecipients) {
            isLoadingRecipients = true
            recipientsError = null
            try {
                recipientCandidates = AppContainer.usersRepository.listStudents()
            } catch (e: Exception) {
                recipientsError = "Impossibile caricare l'elenco della classe."
            } finally {
                isLoadingRecipients = false
            }
        }
    }
    var isGeneratingEvent by remember { mutableStateOf(false) }
    // Solo per il messaggio "sto ancora elaborando" sotto al pulsante: sull'AI locale una
    // generazione può durare fino a 90 secondi (vedi GENERATION_TIMEOUT_MILLIS in
    // LocalAiClassifier), e senza un segnale che si muove il pulsante "Generando..." fermo per
    // decine di secondi sembra bloccato, non lento.
    var generatingElapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isGeneratingEvent) {
        if (isGeneratingEvent) {
            generatingElapsedSeconds = 0
            while (isGeneratingEvent) {
                kotlinx.coroutines.delay(1000)
                generatingElapsedSeconds++
            }
        }
    }

    val scope = rememberCoroutineScope()
    // Material: il foglio nasce dal "+" toccato (come ricerca e notifiche nella Home) e ci rientra;
    // aperto da un testo (es. "+ Aggiungi" di un giorno) scende dal bordo alto. L'origine si prende
    // una volta sola, alla prima composizione, cioe' nel fotogramma del tocco. La chiusura la fa il
    // foglio stesso (hostClose), la si chiede da qui.
    val sheetOrigin = remember {
        if (AppTheme.isGlass) null else {
            circolareplus.design.AilaContainerTransform.assignFreshTo(ADD_EVENT_KEY)
            circolareplus.design.AilaContainerTransform.originOf(ADD_EVENT_KEY)
        }
    }
    val hostClose = remember { arrayOf<(() -> Unit)?>(null) }
    // Chiudere senza animazione toglieva il foglio di colpo (tasto X, evento creato): prima si
    // risale (Material) o scende (Glass), poi lo si toglie.
    fun closeAnimated() {
        if (AppTheme.isGlass) {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        } else {
            (hostClose[0] ?: onDismiss)()
        }
    }
    LaunchedEffect(closeRequested) {
        if (closeRequested) closeAnimated()
    }

    val dateLabel = selectedDateMillis?.let { millis ->
        val civil = circolareplus.util.parseIsoDate(epochMillisToIsoDate(millis))
        if (civil != null) {
            val weekday = circolareplus.util.ITALIAN_WEEKDAYS.getOrNull(circolareplus.util.weekdayOf(civil))?.take(3) ?: ""
            val month = circolareplus.util.ITALIAN_MONTHS.getOrNull(civil.month)?.lowercase() ?: ""
            "$weekday ${civil.day} $month ${civil.year}"
        } else "Scegli una data"
    } ?: "Scegli una data"

    // Solo stile iOS: stessa data del ramo Android, ma come CivilDate per il wheel picker, e
    // sempre valorizzata (oggi come default) perché il wheel non ha uno stato "vuoto".
    val selectedCivilDate = remember(selectedDateMillis) {
        selectedDateMillis?.let { circolareplus.util.civilFromEpochMillis(it) } ?: circolareplus.util.today()
    }
    val (parsedHour, parsedMinute) = remember(time) {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(time.trim())
        if (match != null) {
            val h = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
            val m = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            h to m
        } else 9 to 0
    }
    val timeLabel = time.ifBlank { "Scegli un orario" }

    // submit estratto in funzione locale: il ramo iOS lo richiama anche dal "Fine" dell'intestazione,
    // il ramo Android solo dal pulsante in fondo — nessuna logica duplicata fra i due punti.
    fun submitManualEvent() {
        val date = selectedDateMillis?.let { epochMillisToIsoDate(it) }
        if (title.isNotBlank() && date != null && !isPastDate) {
            val finalTitle = if (subject.isNotBlank()) "${subject.trim()}: ${title.trim()}" else title.trim()
            val visibleToUserIds = if (isForAllClass) null else selectedRecipientUserIds.ifEmpty { null }
            onConfirm(
                finalTitle,
                date,
                time.trim().ifBlank { null },
                category,
                visibleToUserIds,
                notes.trim().ifBlank { null }
            )
        }
    }
    val canSubmitManualEvent = title.isNotBlank() && selectedDateMillis != null && !isPastDate

    // Il DatePickerDialog Material a griglia resta solo per il ramo Android: in stile iOS
    // "Data" apre invece il wheel picker inline dentro il foglio (vedi più sotto).
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            // Nel selettore i giorni passati sono grigi e non si toccano.
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
                override fun isSelectableYear(year: Int): Boolean = year >= todayCivil.year
            }
        )
        circolareplus.design.AilaDatePickerDialog(
            state = datePickerState,
            onDismiss = { showDatePicker = false },
            confirmLabel = "OK"
        ) {
            selectedDateMillis = datePickerState.selectedDateMillis
            showDatePicker = false
        }
    }

    val sheetContent: @Composable ColumnScope.() -> Unit = {
        // Indietro (gesto o tasto) dentro un passo torna al menu con la stessa animazione della
        // freccia; dal menu chiude il foglio. Prima chiudeva sempre tutto.
        circolareplus.platform.PlatformBackHandler(enabled = step != EventCreationStep.MENU) {
            step = EventCreationStep.MENU
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appImePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            // Intestazione Material: freccia indietro (fuori dal menu iniziale) + titolo + chiudi.
            // La freccia entra e esce, il titolo si dissolve da uno all'altro, e i pulsanti sono
            // tondi e si schiacciano al tocco come quelli del resto dell'app.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = step != EventCreationStep.MENU,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EventSheetRoundButton(onClick = { step = EventCreationStep.MENU }) {
                            AppIcons.ChevronLeft(modifier = Modifier.size(16.dp), color = AppTheme.TextDark)
                        }
                        Spacer(modifier = Modifier.width(AppTheme.Space12))
                    }
                }
                androidx.compose.animation.AnimatedContent(
                    targetState = step,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        androidx.compose.animation.ContentTransform(
                            targetContentEnter = androidx.compose.animation.fadeIn(
                                androidx.compose.animation.core.tween(180, delayMillis = 60)
                            ),
                            initialContentExit = androidx.compose.animation.fadeOut(
                                androidx.compose.animation.core.tween(100)
                            )
                        )
                    },
                    label = "eventSheetTitle"
                ) { shownStep ->
                    Text(
                        text = when (shownStep) {
                            EventCreationStep.MENU -> "Nuovo evento"
                            EventCreationStep.ASSISTANT -> "AILA Assistant"
                            EventCreationStep.MANUAL -> "Dettagli evento"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                }
                EventSheetRoundButton(onClick = { closeAnimated() }) {
                    AppIcons.Close(modifier = Modifier.size(16.dp), color = AppTheme.TextMuted)
                }
            }

            // Passi del foglio: "fade through" di Material. Il passo vecchio si dissolve del tutto
            // e solo dopo entra il nuovo (un filo piu' piccolo che si distende), mai insieme: prima
            // il nuovo cominciava mentre il vecchio spariva e per un momento si vedevano sovrapposti.
            // L'altezza del foglio segue con una molla, senza tagliare il contenuto.
            androidx.compose.animation.AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = androidx.compose.animation.fadeIn(
                            androidx.compose.animation.core.tween(190, delayMillis = 100)
                        ) + androidx.compose.animation.scaleIn(
                            androidx.compose.animation.core.tween(190, delayMillis = 100),
                            initialScale = 0.94f
                        ),
                        initialContentExit = androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(90)
                        ),
                        sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                            androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(
                                dampingRatio = 0.9f, stiffness = 380f
                            )
                        }
                    )
                },
                label = "eventCreationStep"
            ) { shownStep ->
            // Colonna propria: AnimatedContent impila i figli come una Box, senza questa si sovrapporrebbero.
            Column(modifier = Modifier.fillMaxWidth()) {
            when (shownStep) {
                EventCreationStep.MENU -> {
                    Text(
                        text = "Come vuoi crearlo?",
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(0).padding(bottom = AppTheme.Space16)
                    )
                    // Le due card alla stessa altezza: quella con la descrizione piu' lunga decide, l'altra
                    // si allunga (prima la seconda restava piu' bassa).
                    Row(
                        modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                    ) {
                        EventCreationOptionCard(
                            title = "AILA Assistant",
                            subtitle = "Descrivi l'evento, l'AI lo inserisce per te",
                            highlighted = true,
                            icon = { color ->
                                circolareplus.design.AilaAssistantMark(
                                    size = 20.dp,
                                    brush = androidx.compose.ui.graphics.SolidColor(color)
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().ailaSheetReveal(1),
                            onClick = { step = EventCreationStep.ASSISTANT }
                        )
                        EventCreationOptionCard(
                            title = "Crea manualmente",
                            subtitle = "Inserisci i dettagli da solo",
                            highlighted = false,
                            icon = { color -> AppIcons.Pencil(modifier = Modifier.size(20.dp), color = color) },
                            modifier = Modifier.weight(1f).fillMaxHeight().ailaSheetReveal(2),
                            onClick = { step = EventCreationStep.MANUAL }
                        )
                    }
                }

                EventCreationStep.ASSISTANT -> {
                    Text(
                        text = "Descrivi cosa vuoi inserire e AILA Assistant compila tutto per te.",
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(0).padding(bottom = AppTheme.Space12)
                    )
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("Es. Inserisci la verifica di matematica di venerdì alle 10") },
                        minLines = 3,
                        colors = circolareplus.design.ailaFieldColors(),
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                        modifier = Modifier.fillMaxWidth().ailaSheetReveal(1)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space16))
                    Text(
                        text = "Esempi rapidi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(2).padding(bottom = AppTheme.Space8)
                    )
                    Column(
                        modifier = Modifier.ailaSheetReveal(3),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                    ) {
                        AI_PROMPT_EXAMPLES.forEach { example ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                    .background(AppTheme.TintSlate)
                                    .clickable { aiPrompt = example }
                                    .padding(horizontal = AppTheme.Space12, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcons.Sparkle(modifier = Modifier.size(13.dp), color = AppTheme.TintVioletInk)
                                Spacer(modifier = Modifier.width(AppTheme.Space8))
                                Text(text = "\"$example\"", fontSize = 13.sp, color = AppTheme.TextDark)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppTheme.Space20))
                    circolareplus.design.AilaPrimaryButton(
                        text = if (isGeneratingEvent) "Generando..." else "Genera evento",
                        onClick = {
                            scope.launch {
                                isGeneratingEvent = true
                                try {
                                    // Se l'AI non è disponibile (nessuna chiave, modello locale non
                                    // scaricato, rete assente, timeout) parseEventPrompt torna una
                                    // bozza vuota: prima il foglio passava comunque ai campi
                                    // manuali, vuoti, come se non fosse successo niente.
                                    val aiDraft = try {
                                        AppContainer.newAiClassifier().parseEventPrompt(aiPrompt)
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val fromAi = aiDraft?.takeIf { it.title.isNotBlank() }
                                    val draft = if (fromAi != null) {
                                        aiEventDraftFromAi(fromAi)
                                    } else {
                                        parseAiEventPrompt(aiPrompt)
                                    }
                                    title = draft.title
                                    subject = draft.subject
                                    category = draft.category
                                    selectedDateMillis = draft.dateMillis
                                    draft.time?.let { time = it }
                                    draft.notes?.let { notes = it }
                                    aiFilled = fromAi != null
                                    aiFallbackNotice = fromAi == null
                                    step = EventCreationStep.MANUAL
                                } finally {
                                    isGeneratingEvent = false
                                }
                            }
                        },
                        enabled = aiPrompt.isNotBlank() && !isGeneratingEvent,
                        fillMaxWidth = true,
                        icon = { color -> AppIcons.Sparkle(modifier = Modifier.size(14.dp), color = color) }
                    )
                    if (isGeneratingEvent) {
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        Text(
                            text = if (generatingElapsedSeconds < 5) {
                                "Sto leggendo la richiesta..."
                            } else {
                                "Con l'AI locale può richiedere fino a un minuto ($generatingElapsedSeconds s) — resta in attesa, sta ancora lavorando."
                            },
                            fontSize = 12.sp,
                            color = AppTheme.TextMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                EventCreationStep.MANUAL -> {
                    if (aiFilled) {
                        circolareplus.design.AilaAssistantBadge(
                            text = "Generato da AILA Assistant",
                            modifier = Modifier.ailaSheetReveal(0).padding(bottom = AppTheme.Space12)
                        )
                    }
                    if (aiFallbackNotice) {
                        Text(
                            text = "L'AI non è disponibile ora (controlla la chiave o il modello nelle " +
                                "Impostazioni): ho compilato i campi leggendo il testo, controllali prima di salvare.",
                            fontSize = 12.sp,
                            color = AppTheme.TextMuted,
                            modifier = Modifier.padding(bottom = AppTheme.Space12)
                        )
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        colors = circolareplus.design.ailaFieldColors(),
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                        modifier = Modifier.fillMaxWidth().ailaSheetReveal(1)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Materia (opzionale)") },
                        singleLine = true,
                        colors = circolareplus.design.ailaFieldColors(),
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                        modifier = Modifier.fillMaxWidth().ailaSheetReveal(2)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space16))
                    Text(
                        text = "Quando",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(3).padding(bottom = AppTheme.Space8)
                    )
                    Row(
                        modifier = Modifier.ailaSheetReveal(3),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1.4f)
                                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                .border(1.dp, AppTheme.FieldOutline, RoundedCornerShape(AppTheme.SmallElementRadius))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = AppTheme.Space12, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcons.Calendar(modifier = Modifier.size(16.dp), color = AppTheme.PrimaryBlue)
                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                            Text(
                                text = dateLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedDateMillis != null) AppTheme.TextDark else AppTheme.TextFaint,
                                maxLines = 1
                            )
                        }
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            placeholder = { Text("Ora") },
                            singleLine = true,
                            colors = circolareplus.design.ailaFieldColors(),
                            shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isPastDate) {
                        Text(
                            text = "Questa data è già passata: scegli oggi o un giorno futuro.",
                            fontSize = 12.sp,
                            color = AppTheme.TintRedInk,
                            modifier = Modifier.padding(top = AppTheme.Space8)
                        )
                    }
                    Spacer(modifier = Modifier.height(AppTheme.Space16))
                    Text(
                        text = "Tipo di evento",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(4).padding(bottom = AppTheme.Space8)
                    )
                    // FlowRow invece di Row: con 6 categorie una singola riga non ci stava (venivano
                    // tagliate fuori dallo schermo su molti telefoni).
                    FlowRow(
                        modifier = Modifier.ailaSheetReveal(4),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                    ) {
                        EVENT_CATEGORY_ORDER.forEach { cat ->
                            circolareplus.design.AnimatedFilterChip(
                                label = eventCategoryLabel(cat),
                                isSelected = category == cat,
                                onClick = { category = cat },
                                icon = { color -> eventCategoryIcon(cat, Modifier.size(13.dp), color) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppTheme.Space16))
                    Text(
                        text = "Note",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(5).padding(bottom = AppTheme.Space8)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Aggiungi note (facoltativo)") },
                        singleLine = false,
                        maxLines = 3,
                        colors = circolareplus.design.ailaFieldColors(),
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                        modifier = Modifier.fillMaxWidth().ailaSheetReveal(5)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space16))
                    Text(
                        text = "Visibilità",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextMuted,
                        modifier = Modifier.ailaSheetReveal(6).padding(bottom = AppTheme.Space8)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().ailaSheetReveal(6),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                    ) {
                        circolareplus.design.AnimatedFilterChip(
                            label = "Tutta la classe",
                            isSelected = isForAllClass,
                            onClick = { isForAllClass = true; selectedRecipientUserIds = emptyList() },
                            modifier = Modifier.weight(1f)
                        )
                        circolareplus.design.AnimatedFilterChip(
                            label = "Persone specifiche",
                            isSelected = !isForAllClass,
                            onClick = { isForAllClass = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isForAllClass) {
                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                        Text(
                            text = "Seleziona fino a 5 persone",
                            fontSize = 11.sp,
                            color = AppTheme.TextFaint
                        )
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        when {
                            isLoadingRecipients -> Text(
                                text = "Carico l'elenco della classe...",
                                fontSize = 12.sp,
                                color = AppTheme.TextMuted
                            )
                            recipientsError != null -> Text(
                                text = recipientsError ?: "",
                                fontSize = 12.sp,
                                color = AppTheme.TintRedInk
                            )
                            recipientCandidates.isEmpty() -> Text(
                                text = "Nessun compagno registrato ancora. Compariranno qui appena si iscrivono.",
                                fontSize = 12.sp,
                                color = AppTheme.TextMuted
                            )
                            else -> FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                            ) {
                                recipientCandidates.forEach { candidate ->
                                    val isSelected = candidate.id in selectedRecipientUserIds
                                    val atLimit = !isSelected && selectedRecipientUserIds.size >= 5
                                    circolareplus.design.AnimatedFilterChip(
                                        label = "${candidate.firstName} ${candidate.lastName}".trim(),
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedRecipientUserIds = when {
                                                isSelected -> selectedRecipientUserIds - candidate.id
                                                atLimit -> selectedRecipientUserIds
                                                else -> selectedRecipientUserIds + candidate.id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppTheme.Space24))
                    circolareplus.design.AilaPrimaryButton(
                        text = "Aggiungi evento",
                        onClick = { submitManualEvent() },
                        enabled = canSubmitManualEvent,
                        fillMaxWidth = true,
                        modifier = Modifier.ailaSheetReveal(7)
                    )
                }
            }
            }
            }
        }
    }
    if (AppTheme.isGlass) {
        circolareplus.design.AilaBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            content = sheetContent
        )
    } else {
        circolareplus.design.AilaTopSheet(origin = sheetOrigin, onClosed = onDismiss) { requestClose ->
            hostClose[0] = requestClose
            androidx.compose.runtime.CompositionLocalProvider(
                circolareplus.design.LocalAilaSheetReveal provides false
            ) {
                sheetContent()
            }
        }
    }
}

/** Pulsante tondo del foglio "Nuovo evento" (indietro, chiudi): si schiaccia al tocco come gli altri. */
@Composable
private fun EventSheetRoundButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .ailaMorphClip(interactionSource, pressedPercent = 28)
            .background(AppTheme.TintSlate)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Foglio di dettaglio di un evento del calendario, aperto toccando la sua card.
 *
 * Prima `onEventClick` non faceva nulla: l'unica informazione visibile era quella già nella card
 * riassuntiva (titolo, ora o "Tutto il giorno", categoria). Note, destinatari specifici e chi ha
 * creato l'evento non erano raggiungibili da nessun'altra schermata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailDialog(
    event: CalendarEvent,
    classmates: List<User>,
    currentUser: User,
    onOpenCircular: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // X ed elimina: il foglio scende con la sua animazione e solo dopo si agisce (prima spariva di
    // colpo). Scrim, trascinamento e indietro scendono gia' da soli.
    fun closeAnimated(after: () -> Unit) {
        scope.launch {
            sheetState.hide()
            after()
        }
    }

    val dateLabel = remember(event.date) {
        val civil = circolareplus.util.parseIsoDate(event.date)
        if (civil != null) {
            val weekday = circolareplus.util.ITALIAN_WEEKDAYS.getOrNull(circolareplus.util.weekdayOf(civil)) ?: ""
            val month = circolareplus.util.ITALIAN_MONTHS.getOrNull(civil.month)?.lowercase() ?: ""
            "$weekday ${civil.day} $month ${civil.year}"
        } else event.date
    }

    // Nomi dei destinatari specifici: risolti da `classmates` (compagni + rappresentante, caricati
    // altrove) più l'utente corrente, che da solo non basterebbe a coprire il caso in cui il
    // rappresentante includa se stesso fra i destinatari.
    val peopleById = remember(classmates, currentUser) {
        classmates.associateBy { it.id } + (currentUser.id to currentUser)
    }
    val recipientNames = remember(event.visibleToUserIds, peopleById) {
        event.visibleToUserIds?.map { id ->
            peopleById[id]?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Utente rimosso"
        }
    }

    circolareplus.design.AilaBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appImePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(0).padding(bottom = AppTheme.Space16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                circolareplus.design.AilaIconTile(tint = eventCategoryTint(event.category)) {
                    eventCategoryIcon(event.category, Modifier.size(20.dp), eventCategoryInk(event.category))
                }
                Spacer(modifier = Modifier.width(AppTheme.Space12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    Text(
                        text = eventCategoryLabel(event.category),
                        fontSize = 12.sp,
                        color = AppTheme.TextMuted
                    )
                }
                EventSheetRoundButton(onClick = { closeAnimated(onDismiss) }) {
                    AppIcons.Close(modifier = Modifier.size(16.dp), color = AppTheme.TextMuted)
                }
            }

            if (event.isAiGenerated) {
                circolareplus.design.AilaAssistantBadge(
                    text = "Inserito da AILA Assistant",
                    modifier = Modifier.ailaSheetReveal(1).padding(bottom = AppTheme.Space16)
                )
            }

            EventDetailRow(label = "Data", value = dateLabel, modifier = Modifier.ailaSheetReveal(2))
            EventDetailRow(label = "Ora", value = event.time ?: "Tutto il giorno", modifier = Modifier.ailaSheetReveal(3))
            EventDetailRow(
                modifier = Modifier.ailaSheetReveal(4),
                label = "Visibile a",
                value = if (event.isForAll || recipientNames == null) {
                    "Tutta la classe"
                } else if (recipientNames.isEmpty()) {
                    "Nessuno (controlla la selezione)"
                } else {
                    recipientNames.joinToString(", ")
                }
            )

            if (!event.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Text(
                    text = "Note",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.ailaSheetReveal(5).padding(bottom = AppTheme.Space4)
                )
                Text(
                    text = event.notes,
                    fontSize = 14.sp,
                    color = AppTheme.TextDark,
                    modifier = Modifier.ailaSheetReveal(5)
                )
            }

            event.circularNumber?.let { number ->
                Spacer(modifier = Modifier.height(AppTheme.Space16))
                circolareplus.design.AilaSecondaryButton(
                    text = "Apri la circolare n. $number",
                    onClick = { closeAnimated { onOpenCircular(number) } },
                    modifier = Modifier.fillMaxWidth().ailaSheetReveal(6)
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space24))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .ailaSheetReveal(6)
                    .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                    .background(AppTheme.TintRed)
                    .clickable { closeAnimated(onDelete) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcons.Trash(modifier = Modifier.size(14.dp), color = AppTheme.TintRedInk)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "Elimina evento",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TintRedInk
                )
            }
        }
    }
}

/**
 * Dettaglio di un evento come pagina intera (Material): stessa informazione del foglio, ma nasce
 * dalla card toccata e ci ritorna (container transform). Il foglio ([EventDetailDialog]) resta per
 * Liquid Glass.
 */
@Composable
private fun EventDetailPage(
    event: CalendarEvent,
    classmates: List<User>,
    currentUser: User,
    onOpenCircular: (Int) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(event.date) {
        val civil = circolareplus.util.parseIsoDate(event.date)
        if (civil != null) {
            val weekday = circolareplus.util.ITALIAN_WEEKDAYS.getOrNull(circolareplus.util.weekdayOf(civil)) ?: ""
            val month = circolareplus.util.ITALIAN_MONTHS.getOrNull(civil.month)?.lowercase() ?: ""
            "$weekday ${civil.day} $month ${civil.year}"
        } else event.date
    }
    val peopleById = remember(classmates, currentUser) {
        classmates.associateBy { it.id } + (currentUser.id to currentUser)
    }
    val recipientNames = remember(event.visibleToUserIds, peopleById) {
        event.visibleToUserIds?.map { id ->
            peopleById[id]?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Utente rimosso"
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        circolareplus.design.AilaBackBar(title = "Evento", onBackClick = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space16)
                .padding(top = AppTheme.Space8, bottom = AppTheme.Space32)
        ) {
            circolareplus.design.AilaCard {
                Column(modifier = Modifier.padding(AppTheme.Space16)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space16),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        circolareplus.design.AilaIconTile(tint = eventCategoryTint(event.category)) {
                            eventCategoryIcon(event.category, Modifier.size(20.dp), eventCategoryInk(event.category))
                        }
                        Spacer(modifier = Modifier.width(AppTheme.Space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Text(
                                text = eventCategoryLabel(event.category),
                                fontSize = 12.sp,
                                color = AppTheme.TextMuted
                            )
                        }
                    }
                    if (event.isAiGenerated) {
                        circolareplus.design.AilaAssistantBadge(
                            text = "Inserito da AILA Assistant",
                            modifier = Modifier.padding(bottom = AppTheme.Space16)
                        )
                    }
                    EventDetailRow(label = "Data", value = dateLabel)
                    EventDetailRow(label = "Ora", value = event.time ?: "Tutto il giorno")
                    EventDetailRow(
                        label = "Visibile a",
                        value = if (event.isForAll || recipientNames == null) {
                            "Tutta la classe"
                        } else if (recipientNames.isEmpty()) {
                            "Nessuno (controlla la selezione)"
                        } else {
                            recipientNames.joinToString(", ")
                        }
                    )
                    if (!event.notes.isNullOrBlank()) {
                        Text(
                            text = "Note",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextMuted,
                            modifier = Modifier.padding(bottom = AppTheme.Space4)
                        )
                        Text(text = event.notes, fontSize = 14.sp, color = AppTheme.TextDark)
                    }
                }
            }
            event.circularNumber?.let { number ->
                Spacer(modifier = Modifier.height(AppTheme.Space16))
                circolareplus.design.AilaSecondaryButton(
                    text = "Apri la circolare n. $number",
                    onClick = { onOpenCircular(number) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(AppTheme.Space16))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                    .background(AppTheme.TintRed)
                    .clickable { onDelete() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcons.Trash(modifier = Modifier.size(14.dp), color = AppTheme.TintRedInk)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "Elimina evento",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TintRedInk
                )
            }
        }
    }
}

@Composable
private fun EventDetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = AppTheme.Space12)) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, color = AppTheme.TextDark)
    }
}

private fun eventCategoryTint(category: CalendarEventCategory): Color = when (category) {
    CalendarEventCategory.VERIFICA, CalendarEventCategory.INTERROGAZIONE -> AppTheme.TintBlue
    CalendarEventCategory.PAGAMENTO -> AppTheme.TintAmber
    CalendarEventCategory.USCITA_DIDATTICA -> AppTheme.TintGreen
    CalendarEventCategory.AVVISO -> AppTheme.TintRed
    else -> AppTheme.TintSlate
}

private fun eventCategoryInk(category: CalendarEventCategory): Color = when (category) {
    CalendarEventCategory.VERIFICA, CalendarEventCategory.INTERROGAZIONE -> AppTheme.TintBlueInk
    CalendarEventCategory.PAGAMENTO -> AppTheme.TintAmberInk
    CalendarEventCategory.USCITA_DIDATTICA -> AppTheme.TintGreenInk
    CalendarEventCategory.AVVISO -> AppTheme.TintRedInk
    else -> AppTheme.TintSlateInk
}

/**
 * Card di scelta rapida del foglio "Nuovo evento": "AILA Assistant" (riempimento sfumato) e
 * "Crea manualmente" (contorno), affiancate come nel mockup di riferimento.
 */
@Composable
private fun EventCreationOptionCard(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.CardCornerRadius))
            .then(
                // La card evidenziata e' piena del blu primario in tutti e due gli stili, come il
                // pulsante primario del passo dopo (in Glass era una velatura azzurra pallida,
                // di un colore diverso dal resto). Quella normale: vetro in Glass, bianca col
                // contorno in Material.
                if (highlighted) Modifier
                    .background(AppTheme.PrimaryGradient)
                    .then(
                        if (AppTheme.isGlass) Modifier.border(1.dp, AppTheme.GlassEdge, RoundedCornerShape(AppTheme.CardCornerRadius))
                        else Modifier
                    )
                else if (AppTheme.isGlass) Modifier.ailaGlassSurface(RoundedCornerShape(AppTheme.CardCornerRadius))
                else Modifier
                    .background(AppTheme.SurfaceWhite)
                    .border(1.dp, AppTheme.FieldOutline, RoundedCornerShape(AppTheme.CardCornerRadius))
            )
            .clickable { onClick() }
            .padding(AppTheme.Space16)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .background(if (highlighted) Color.White.copy(alpha = 0.2f) else AppTheme.TintViolet),
            contentAlignment = Alignment.Center
        ) {
            icon(if (highlighted) Color.White else AppTheme.TintVioletInk)
        }
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Color.White else AppTheme.TextDark
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = if (highlighted) Color(0xCCFFFFFF) else AppTheme.TextMuted,
            lineHeight = 14.sp
        )
    }
}

/** Bozza locale di uno slot data mentre il rappresentante compila il modulo di creazione. */
private data class PollSlotDraft(val dateMillis: Long, val capacity: Int)

/**
 * Creazione di un sondaggio interrogazioni (solo Rappresentante). `PollsRepository.createPoll()`
 * esisteva già, ma senza questo modulo non c'era alcun modo di popolarlo dall'app: gli studenti
 * vedevano sempre "Nessun sondaggio disponibile".
 */
/**
 * Intestazione condivisa dei fogli di creazione (evento, sondaggio, proposta): titolo a sinistra,
 * chiudi a destra. Stessa struttura del foglio "Nuovo evento" del Calendario, perché prima
 * ognuna delle tre creazioni aveva un aspetto diverso — questa era ancora un `AlertDialog` con
 * campi Material di default, mentre il Calendario era già passato al foglio.
 */
@Composable
private fun CreationSheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .background(AppTheme.TintSlate)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Close(modifier = Modifier.size(16.dp), color = AppTheme.TextMuted)
        }
    }
}

/**
 * Creazione di un sondaggio interrogazioni (solo Rappresentante). `PollsRepository.createPoll()`
 * esisteva già, ma senza questo modulo non c'era alcun modo di popolarlo dall'app: gli studenti
 * vedevano sempre "Nessun sondaggio disponibile".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePollDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, List<CreatePollSlotRequestDto>, List<String>?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var subject by remember { mutableStateOf("") }
    // null = tutta la classe; altrimenti solo chi deve essere interrogato.
    var audience by remember { mutableStateOf<List<String>?>(null) }
    val slots = remember { mutableStateListOf<PollSlotDraft>() }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pendingDateMillis)
        circolareplus.design.AilaDatePickerDialog(
            state = datePickerState,
            onDismiss = { showDatePicker = false },
            confirmLabel = "Aggiungi"
        ) {
            datePickerState.selectedDateMillis?.let { millis ->
                slots.add(PollSlotDraft(dateMillis = millis, capacity = 3))
            }
            showDatePicker = false
        }
    }

    // Col selettore data aperto (in Glass) il foglio si dissolve: la card e' vetro trasparente e
    // i testi del foglio, dietro, la rendevano illeggibile.
    circolareplus.design.AilaBottomSheet(
        onDismissRequest = onDismiss,
        faded = showDatePicker,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appImePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            CreationSheetHeader(title = "Nuovo sondaggio interrogazioni", onClose = onDismiss)

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Materia") },
                singleLine = true,
                colors = circolareplus.design.ailaFieldColors(),
                shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(0)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space16))

            if (slots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8), modifier = Modifier.ailaSheetReveal(1)) {
                    slots.forEachIndexed { index, slot ->
                        PollSlotDraftRow(
                            slot = slot,
                            onCapacityChange = { newCapacity -> slots[index] = slot.copy(capacity = newCapacity) },
                            onRemove = { slots.removeAt(index) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppTheme.Space12))
            }

            circolareplus.design.AilaSecondaryButton(
                text = "+ Aggiungi data/slot",
                onClick = { showDatePicker = true },
                icon = { color -> AppIcons.Calendar(modifier = Modifier.size(15.dp), color = color) },
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(if (slots.isEmpty()) 1 else 2)
            )

            Spacer(modifier = Modifier.height(AppTheme.Space20))
            circolareplus.ui.screens.PollAudienceSelector(
                selectedIds = audience,
                onSelectionChange = { audience = it },
                modifier = Modifier.ailaSheetReveal(if (slots.isEmpty()) 3 else 4)
            )

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12), modifier = Modifier.ailaSheetReveal(if (slots.isEmpty()) 4 else 5)) {
                circolareplus.design.AilaSecondaryButton(
                    text = "Annulla",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                circolareplus.design.AilaPrimaryButton(
                    text = if (isSubmitting) "Creazione..." else "Crea e pubblica",
                    onClick = {
                        onConfirm(
                            subject.trim(),
                            slots.map { CreatePollSlotRequestDto(epochMillisToIsoDate(it.dateMillis), it.capacity) },
                            audience
                        )
                    },
                    enabled = !isSubmitting && subject.isNotBlank() && slots.isNotEmpty() && audience?.isEmpty() != true,
                    fillMaxWidth = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Una riga di data/slot nel foglio "Nuovo sondaggio", nello stesso stile a card delle altre liste. */
@Composable
private fun PollSlotDraftRow(
    slot: PollSlotDraft,
    onCapacityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintSlate)
            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = epochMillisToIsoDate(slot.dateMillis),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppTheme.SurfaceWhite)
                    .clickable(enabled = slot.capacity > 1) { onCapacityChange(slot.capacity - 1) },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
            }
            Text(
                text = "${slot.capacity}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                modifier = Modifier.padding(horizontal = AppTheme.Space8)
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppTheme.SurfaceWhite)
                    .clickable { onCapacityChange(slot.capacity + 1) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
            }
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .clickable { onRemove() }
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Trash(modifier = Modifier.size(15.dp), color = AppTheme.TintRedInk)
        }
    }
}

/**
 * Creazione di un sondaggio a ordinamento (solo Rappresentante): una domanda e da 2 a 10
 * opzioni che la classe metterà in ordine. Si pubblica subito, come i sondaggi interrogazioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRankingPollDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, List<String>?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    // null = tutta la classe.
    var audience by remember { mutableStateOf<List<String>?>(null) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val hasDuplicates = filled.map { it.lowercase() }.distinct().size != filled.size
    val canSubmit = !isSubmitting && question.isNotBlank() && filled.size >= 2 && !hasDuplicates &&
        audience?.isEmpty() != true

    circolareplus.design.AilaBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appImePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            CreationSheetHeader(title = "Nuovo sondaggio a ordinamento", onClose = onDismiss)

            OutlinedTextField(
                value = question,
                onValueChange = { question = it.take(200) },
                label = { Text("Domanda") },
                placeholder = { Text("Es. Dove andiamo in gita?") },
                colors = circolareplus.design.ailaFieldColors(),
                shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(0)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space16))

            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8), modifier = Modifier.ailaSheetReveal(1)) {
                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { options[index] = it.take(80) },
                            label = { Text("Opzione ${index + 1}") },
                            singleLine = true,
                            colors = circolareplus.design.ailaFieldColors(),
                            shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                            modifier = Modifier.weight(1f)
                        )
                        if (options.size > 2) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                    .clickable { options.removeAt(index) }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Trash(modifier = Modifier.size(16.dp), color = AppTheme.TintRedInk)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppTheme.Space12))

            if (options.size < 10) {
                circolareplus.design.AilaSecondaryButton(
                    text = "Aggiungi opzione",
                    onClick = { options.add("") },
                    icon = { color -> AppIcons.Plus(modifier = Modifier.size(15.dp), color = color) },
                    modifier = Modifier.fillMaxWidth().ailaSheetReveal(2)
                )
            }
            Text(
                text = if (hasDuplicates) "Ci sono opzioni ripetute."
                else "Ognuno le metterà in ordine; la classifica della classe è a punti.",
                fontSize = 11.sp,
                color = if (hasDuplicates) AppTheme.TintRedInk else AppTheme.TextMuted,
                modifier = Modifier.padding(top = AppTheme.Space8).ailaSheetReveal(3)
            )

            Spacer(modifier = Modifier.height(AppTheme.Space20))
            circolareplus.ui.screens.PollAudienceSelector(
                selectedIds = audience,
                onSelectionChange = { audience = it },
                modifier = Modifier.ailaSheetReveal(4)
            )

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12), modifier = Modifier.ailaSheetReveal(5)) {
                circolareplus.design.AilaSecondaryButton(
                    text = "Annulla",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                circolareplus.design.AilaPrimaryButton(
                    text = if (isSubmitting) "Creazione..." else "Crea e pubblica",
                    onClick = { onConfirm(question.trim(), filled, audience) },
                    enabled = canSubmit,
                    fillMaxWidth = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProposalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(false) }

    circolareplus.design.AilaBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appImePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space20)
                .padding(bottom = AppTheme.Space32)
        ) {
            CreationSheetHeader(title = "Nuova proposta", onClose = onDismiss)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titolo") },
                singleLine = true,
                colors = circolareplus.design.ailaFieldColors(),
                shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(0)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione") },
                minLines = 3,
                colors = circolareplus.design.ailaFieldColors(),
                shape = RoundedCornerShape(AppTheme.SmallElementRadius),
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(1)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAnonymous = !isAnonymous }
                    .ailaSheetReveal(2)
            ) {
                Text("Pubblica in forma anonima", fontSize = 13.sp, color = AppTheme.TextDark)
                circolareplus.design.AilaSwitch(
                    checked = isAnonymous,
                    onCheckedChange = { isAnonymous = it }
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12), modifier = Modifier.ailaSheetReveal(3)) {
                circolareplus.design.AilaSecondaryButton(
                    text = "Annulla",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                circolareplus.design.AilaPrimaryButton(
                    text = "Pubblica",
                    onClick = {
                        if (title.isNotBlank() && description.isNotBlank()) {
                            onConfirm(title.trim(), description.trim(), "GENERALE", isAnonymous)
                        }
                    },
                    enabled = title.isNotBlank() && description.isNotBlank(),
                    fillMaxWidth = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Striscia "nessuna connessione", mostrata sopra al contenuto quando l'app è partita senza rete.
 *
 * Esiste perché prima quel caso non aveva alcuna rappresentazione: il ripristino della sessione
 * falliva, l'app cancellava il token e l'utente si ritrovava alla schermata di login senza
 * capire perché. Ora la sessione resta e questa riga dice cosa sta succedendo, con il modo per
 * riprovare quando la rete torna.
 */
@Composable
private fun OfflineBanner(
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.TintAmber)
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcons.Warning(modifier = Modifier.size(18.dp), color = AppTheme.TintAmberInk)
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sei offline",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintAmberInk
            )
            Text(
                text = offlineDataAgeLabel(),
                fontSize = 11.sp,
                color = AppTheme.TintAmberInk,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onRetry() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcons.Refresh(modifier = Modifier.size(14.dp), color = AppTheme.TintAmberInk)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Riprova",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintAmberInk
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDismiss() }
                .semantics { contentDescription = "Chiudi" },
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Close(modifier = Modifier.size(14.dp), color = AppTheme.TintAmberInk.copy(alpha = 0.75f))
        }
    }
}

/**
 * Schermata mostrata quando la sessione è ancora valida ma non c'è né rete né una copia locale
 * del profilo (caso raro: primo avvio dopo l'installazione senza connessione). Non è un login,
 * perché il token c'è ancora: è un'attesa con un pulsante per riprovare. Il logout resta a
 * disposizione, ma è una scelta esplicita e non più una conseguenza automatica dell'assenza di rete.
 */
@Composable
private fun OfflineGateScreen(
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .appSafeDrawingPadding()
            .appContentWidth(MaxFormWidth),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.Space32),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            circolareplus.design.AilaLogoTile(size = 64.dp, glow = true)
            Spacer(modifier = Modifier.height(AppTheme.Space24))
            Text(
                text = "Nessuna connessione",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Text(
                text = "Non riesco a raggiungere il server e non ho ancora una copia del tuo " +
                    "profilo su questo dispositivo. Il tuo accesso è salvo: riprova appena torni online.",
                fontSize = 13.sp,
                color = AppTheme.TextMuted,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(AppTheme.Space24))
            circolareplus.design.AilaPrimaryButton(
                text = "Riprova",
                onClick = onRetry,
                fillMaxWidth = true,
                icon = { tint -> AppIcons.Refresh(modifier = Modifier.size(15.dp), color = tint) }
            )
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            circolareplus.design.AilaSecondaryButton(
                text = "Esci dall'account",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


/** Testo della striscia offline: da quanto sono i dati che si stanno guardando. */
private fun offlineDataAgeLabel(): String {
    val last = AppContainer.settings.lastOnlineSyncMillis
    if (last <= 0L) return "Stai consultando i dati salvati sul telefono. Voti e modifiche tornano con la rete."
    val minutes = ((circolareplus.platform.currentTimeMillis() - last).coerceAtLeast(0L)) / 60_000L
    val age = when {
        minutes < 1 -> "adesso"
        minutes < 60 -> "$minutes min fa"
        minutes < 60 * 24 -> "${minutes / 60} h fa"
        else -> "${minutes / (60 * 24)} g fa"
    }
    return "Dati salvati aggiornati $age. Puoi consultare tutto; voti e modifiche tornano con la rete."
}

/** Chiave dell'origine del container transform del dettaglio circolare. */
private const val DETAIL_TRANSFORM_KEY = "circularDetail"
private const val EVENT_DETAIL_KEY = "eventDetail"
private const val ADD_EVENT_KEY = "addEventSheet"

/** Le schermate a tutto schermo della shell; `depth` decide il verso del push/pop. */
private enum class ShellRoute(val depth: Int) {
    TABS(0),
    PROFILE(1),
    SETTINGS(2),
    BACKGROUND_DEBUG(3),
    CLASS_ROSTER(2),
    ASSISTANT(1),
    SEARCH(1),
    NOTIFICATIONS(1),
    SEATMAP_PROPOSALS(1),
    SEATMAP_EDITOR(2)
}


/**
 * Barra delle tab "flottante": una pillola staccata dai bordi, con ombra morbida, al posto della
 * barra fissa a tutta larghezza. Le voci non selezionate mostrano solo l'icona; quella attiva si
 * allarga in una pillola blu con icona e nome. Cambiando tab la pillola si sposta con una molla
 * leggermente elastica: la voce nuova si allarga e mostra il nome, la vecchia si richiude.
 *
 * Solo icone per le voci spente perche' cinque etichette in una pillola stretta andavano
 * accorciate ("Calend.", "Mappa"); il nome della sezione in cui ci si trova resta sempre scritto.
 */
@Composable
private fun FloatingTabBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    /** Posizione delle pagine mentre si trascina (vedi tabBarDrag), null altrimenti. */
    dragPosition: androidx.compose.runtime.State<Float?>,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppTheme.Space16, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                // Su tablet e iPad la pillola non si allunga per tutto lo schermo.
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(64.dp)
                .tabBarSurface(shape, hazeState)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (AppTheme.isGlass) {
                GlassTabBarContent(selectedTab = selectedTab, onSelect = onSelect, dragPosition = dragPosition.value)
            } else {
                val drag = dragPosition.value
                MainTab.entries.forEachIndexed { index, tab ->
                    FloatingTabItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        // Trascinando: quanto questa voce e' "attiva" dipende da quanto la sua
                        // pagina e' vicina (1 = sua pagina piena, 0 = a una pagina o piu').
                        dragProgress = drag?.let { (1f - kotlin.math.abs(it - index)).coerceIn(0f, 1f) },
                        onClick = { onSelect(tab) }
                    )
                }
            }
        }
    }
}

/**
 * Fondo della barra delle tab, in basso (telefono) o laterale (tablet e iPad): vetro sfocato in
 * Liquid Glass, pillola piena con ombra in Material.
 */
@Composable
private fun Modifier.tabBarSurface(
    shape: androidx.compose.ui.graphics.Shape,
    hazeState: dev.chrisbanes.haze.HazeState?
): Modifier = this.then(
    if (AppTheme.isGlass) {
        // Liquid Glass: vetro traslucido (il contenuto che scorre sotto si
        // intravede), ombra appena accennata e un filo di luce sul bordo, piu'
        // chiaro in alto come un riflesso.
        Modifier
            .shadow(elevation = 10.dp, shape = shape, ambientColor = Color(0x1A1B2E7A), spotColor = Color(0x261B2E7A))
            .clip(shape)
            // Sfocatura vera di quello che scorre sotto (Haze), con un velo
            // bianco (o scuro) sopra: il "vetro smerigliato" di iOS.
            .then(
                if (hazeState != null) Modifier.hazeEffect(
                    state = hazeState,
                    style = dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = AppTheme.GlassBase,
                        // Piu' trasparente: il contenuto sotto si vede sfocato
                        // ma riconoscibile, come la tab bar di iOS 26.
                        tint = dev.chrisbanes.haze.HazeTint(
                            if (AppTheme.isDarkMode) Color(0x14202430) else Color(0x14FFFFFF)
                        ),
                        // Sfocatura leggera: a 32dp la barra sembrava opaca;
                        // cosi' il contenuto sotto si riconosce, semitrasparente.
                        blurRadius = 18.dp,
                        noiseFactor = 0f
                    )
                ) else Modifier.background(
                    if (AppTheme.isDarkMode) Color(0xA6202430) else Color(0xB8FFFFFF)
                )
            )
            // Bordo speculare come gli altri vetri (luce dall'alto a sinistra).
            .border(1.dp, AppTheme.GlassEdge, shape)
    } else {
        // Material Expressive: pillola piena "surface container", senza bordo,
        // con l'ombra bassa delle barre flottanti di M3.
        Modifier
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(if (AppTheme.isDarkMode) Color(0xFF22252C) else Color(0xFFECEDF7))
    }
)

/** Spazio a sinistra occupato dalla barra laterale (margine + barra + distacco dal contenuto). */
private val RailInset = 104.dp

/** Larghezza della lista delle circolari quando la circolare aperta le sta accanto. */
private val CircularsListPaneWidth = 400.dp

/**
 * La barra delle tab su tablet e iPad: una colonna di vetro (o pillola Material) staccata dal
 * bordo sinistro, con icona e nome per ogni voce, come la barra laterale di iPadOS e la
 * navigation rail di Material. Stesso fondo della barra in basso (vedi [tabBarSurface]).
 */
@Composable
private fun FloatingTabRail(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 12.dp)
            .width(80.dp)
            .tabBarSurface(RoundedCornerShape(36.dp), hazeState)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MainTab.entries.forEach { tab ->
            FloatingRailItem(tab = tab, selected = tab == selectedTab, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun FloatingRailItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 520f),
        label = "railItem"
    )
    // Stessi colori delle voci della barra in basso (vedi FloatingTabItem).
    val activeInk = if (AppTheme.isGlass) AppTheme.PrimaryBlue else AppTheme.OnSelection
    val ink = androidx.compose.ui.graphics.lerp(
        if (AppTheme.isGlass) AppTheme.TextDark.copy(alpha = 0.75f) else AppTheme.TextMuted,
        activeInk,
        progress.coerceIn(0f, 1f)
    )
    val tint = if (AppTheme.isGlass) {
        if (AppTheme.isDarkMode) Color(0x33FFFFFF) else Color(0x1A767680)
    } else {
        if (AppTheme.accent != circolareplus.design.AilaAccent.BLUE) AppTheme.AccentContainer
        else if (AppTheme.isDarkMode) Color(0xFF34457A) else Color(0xFFD9E2FF)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = tab.title
                role = Role.Tab
                this.selected = selected
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // L'indicatore della voce attiva sta dietro l'icona, come nella navigation rail di M3.
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .drawBehind { drawRect(tint, alpha = progress.coerceIn(0f, 1f)) },
            contentAlignment = Alignment.Center
        ) {
            val iconModifier = Modifier.size(22.dp).ailaSelectionPop(selected)
            when (tab) {
                MainTab.HOME -> AppIcons.Home(modifier = iconModifier, color = ink)
                MainTab.CALENDAR -> AppIcons.Calendar(modifier = iconModifier, color = ink)
                MainTab.CLASS -> AppIcons.Document(modifier = iconModifier, color = ink)
                MainTab.SEATMAP -> AppIcons.Chair(modifier = iconModifier, color = ink)
                MainTab.POLLS -> AppIcons.Poll(modifier = iconModifier, color = ink)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (tab == MainTab.SEATMAP) "Posti" else tab.title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Pannello a destra della lista delle circolari sugli schermi larghi: la circolare aperta, oppure
 * un invito a sceglierne una. Cambiando circolare la vecchia sfuma nella nuova.
 */
@Composable
private fun CircularDetailPane(
    circular: Circular?,
    modifier: Modifier = Modifier,
    content: @Composable (Circular) -> Unit
) {
    Row(modifier = modifier) {
        // Filo di separazione fra lista e circolare.
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(AppTheme.Hairline))
        // Per numero: se la stessa circolare arriva aggiornata dal server non si ridisegna da capo
        // (il PDF gia' mostrato resterebbe da rifare).
        val latest = androidx.compose.runtime.rememberUpdatedState(circular)
        androidx.compose.animation.Crossfade(
            targetState = circular?.number,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "circularDetailPane"
        ) { number ->
            val shown = remember(number) { latest.value?.takeIf { it.number == number } }
            val current = latest.value?.takeIf { it.number == number } ?: shown
            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(AppTheme.Space24),
                    contentAlignment = Alignment.Center
                ) {
                    circolareplus.design.AilaEmptyState(
                        title = "Nessuna circolare aperta",
                        message = "Scegli una circolare dalla lista: la leggi qui, accanto alle altre."
                    )
                }
            } else {
                content(current)
            }
        }
    }
}

@Composable
private fun RowScope.FloatingTabItem(
    tab: MainTab,
    selected: Boolean,
    dragProgress: Float?,
    onClick: () -> Unit
) {
    // Un solo valore guida larghezza, fondo, colore e nome della voce: 0 spenta, 1 attiva. Al
    // tocco va con la molla elastica (supera appena 1 e ci torna: il piccolo "rimbalzo" della
    // pillola); trascinando le pagine segue il dito ([dragProgress]) e al rilascio la molla
    // riparte esattamente da dove il dito l'aveva lasciata, senza salti.
    val settle = remember { androidx.compose.animation.core.Animatable(if (selected) 1f else 0f) }
    val lastDrag = remember { floatArrayOf(-1f) }
    if (dragProgress != null) lastDrag[0] = dragProgress
    val following = dragProgress != null
    LaunchedEffect(selected, following) {
        if (following) return@LaunchedEffect
        if (lastDrag[0] >= 0f) {
            settle.snapTo(lastDrag[0])
            lastDrag[0] = -1f
        }
        // Material: molla un filo piu' lenta e meno elastica di quella generale, perche' la
        // pillola che rimbalzava attraversando la barra risultava troppo vivace.
        settle.animateTo(
            if (selected) 1f else 0f,
            if (AppTheme.isGlass) ailaSpatialSpring()
            else androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 520f)
        )
    }
    val progress = dragProgress ?: if (lastDrag[0] >= 0f) lastDrag[0] else settle.value
    // Glass: la voce attiva e' una capsula di vetro grigio con icona e nome in blu, come la tab
    // bar di iOS 26. Expressive: l'indicatore "secondary container" di Material, pieno.
    val activeInk = if (AppTheme.isGlass) AppTheme.PrimaryBlue else AppTheme.OnSelection
    val iconColor = androidx.compose.ui.graphics.lerp(
        if (AppTheme.isGlass) AppTheme.TextFaint else AppTheme.TextMuted,
        activeInk,
        progress.coerceIn(0f, 1f)
    )
    val tint = if (AppTheme.isGlass) {
        if (AppTheme.isDarkMode) Color(0x33FFFFFF) else Color(0x1A767680)
    } else {
        if (AppTheme.accent != circolareplus.design.AilaAccent.BLUE) AppTheme.AccentContainer
        else if (AppTheme.isDarkMode) Color(0xFF34457A) else Color(0xFFD9E2FF)
    }
    Row(
        modifier = Modifier
            .weight(1f + 1.8f * progress.coerceAtLeast(0f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .drawBehind { drawRect(tint, alpha = progress.coerceIn(0f, 1f)) }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = tab.title
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // L'icona toccata fa un piccolo "pop" elastico quando diventa attiva (trascinando no:
        // passando a meta' avanti e indietro rimbalzerebbe a ogni passaggio).
        val iconModifier = Modifier.size(22.dp).ailaSelectionPop(selected && !following)
        when (tab) {
            MainTab.HOME -> AppIcons.Home(modifier = iconModifier, color = iconColor)
            MainTab.CALENDAR -> AppIcons.Calendar(modifier = iconModifier, color = iconColor)
            MainTab.CLASS -> AppIcons.Document(modifier = iconModifier, color = iconColor)
            MainTab.SEATMAP -> AppIcons.Chair(modifier = iconModifier, color = iconColor)
            MainTab.POLLS -> AppIcons.Poll(modifier = iconModifier, color = iconColor)
        }
        // Il nome si apre e si chiude con lo stesso valore della pillola (larghezza e dissolvenza),
        // invece di un'animazione sua che partiva solo quando la voce cambiava.
        val labelT = progress.coerceIn(0f, 1f)
        if (labelT > 0.001f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0))
                        val w = (placeable.width * labelT + 0.5f).toInt()
                        layout(w, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                    .graphicsLayer { alpha = ((labelT - 0.35f) / 0.65f).coerceIn(0f, 1f) }
            ) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeInk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}


/**
 * Barra delle tab in Liquid Glass, come iOS 26: cinque voci uguali con icona e nome sotto, e una
 * "lente" di vetro dietro la voce attiva. Cambiando tab la lente si stira come una goccia fra le
 * due voci (il bordo davanti arriva prima, quello dietro lo segue) e si ricompatta, senza i
 * rimbalzi elastici di Material.
 */
@Composable
private fun RowScope.GlassTabBarContent(selectedTab: MainTab, onSelect: (MainTab) -> Unit, dragPosition: Float?) {
    val tabs = MainTab.entries
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
        val itemWidth = maxWidth / tabs.size
        val index = tabs.indexOf(selectedTab)
        val movingRight = remember { arrayOf(true) }
        val lastIndex = remember { arrayOf(index) }
        if (lastIndex[0] != index) {
            movingRight[0] = index > lastIndex[0]
            lastIndex[0] = index
        }
        // Bordi della lente in "voci" (0 = inizio della prima). Al tocco si stirano con due molle
        // diverse; trascinando le pagine la lente segue il dito, compatta, e al rilascio le molle
        // ripartono da li'.
        val leftAnim = remember { androidx.compose.animation.core.Animatable(index.toFloat()) }
        val rightAnim = remember { androidx.compose.animation.core.Animatable(index + 1f) }
        val lastDrag = remember { floatArrayOf(-1f) }
        if (dragPosition != null) lastDrag[0] = dragPosition
        val following = dragPosition != null
        LaunchedEffect(index, following) {
            if (following) return@LaunchedEffect
            if (lastDrag[0] >= 0f) {
                leftAnim.snapTo(lastDrag[0])
                rightAnim.snapTo(lastDrag[0] + 1f)
                lastDrag[0] = -1f
            }
            val fast = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 700f)
            val slow = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 260f)
            launch { leftAnim.animateTo(index.toFloat(), if (movingRight[0]) slow else fast) }
            rightAnim.animateTo(index + 1f, if (movingRight[0]) fast else slow)
        }
        val pendingDrag = if (lastDrag[0] >= 0f) lastDrag[0] else null
        val lensLeft = dragPosition ?: pendingDrag ?: leftAnim.value
        val lensRight = dragPosition?.plus(1f) ?: pendingDrag?.plus(1f) ?: rightAnim.value
        val left = androidx.compose.runtime.rememberUpdatedState(itemWidth * lensLeft)
        val right = androidx.compose.runtime.rememberUpdatedState(itemWidth * lensRight)
        // La lente: vetro un po' piu' chiaro della barra, col suo bordo speculare.
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(left.value.roundToPx(), 0) }
                .layout { measurable, constraints ->
                    val w = (right.value - left.value).roundToPx().coerceAtLeast(0)
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(w, constraints.maxHeight)
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .ailaGlassSurface(
                    RoundedCornerShape(percent = 50),
                    tint = if (AppTheme.isDarkMode) Color(0x1FFFFFFF) else Color(0x59FFFFFF)
                )
        )
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                val animatedInk by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) AppTheme.PrimaryBlue else AppTheme.TextDark.copy(alpha = 0.75f),
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "glassTabInk"
                )
                // Trascinando il colore segue la lente invece di scattare a meta' strada.
                val ink = dragPosition?.let {
                    androidx.compose.ui.graphics.lerp(
                        AppTheme.TextDark.copy(alpha = 0.75f),
                        AppTheme.PrimaryBlue,
                        (1f - kotlin.math.abs(it - tabs.indexOf(tab))).coerceIn(0f, 1f)
                    )
                } ?: animatedInk
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) }
                        .semantics {
                            contentDescription = tab.title
                            role = Role.Tab
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val iconModifier = Modifier.size(22.dp)
                    when (tab) {
                        MainTab.HOME -> AppIcons.Home(modifier = iconModifier, color = ink)
                        MainTab.CALENDAR -> AppIcons.Calendar(modifier = iconModifier, color = ink)
                        MainTab.CLASS -> AppIcons.Document(modifier = iconModifier, color = ink)
                        MainTab.SEATMAP -> AppIcons.Chair(modifier = iconModifier, color = ink)
                        MainTab.POLLS -> AppIcons.Poll(modifier = iconModifier, color = ink)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (tab == MainTab.SEATMAP) "Posti" else tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = ink,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
