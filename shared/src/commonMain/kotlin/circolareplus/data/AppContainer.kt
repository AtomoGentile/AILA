package circolareplus.data

import circolareplus.ai.AiClassifier
import circolareplus.ai.AiCoreCooldown
import circolareplus.ai.assistant.AilaAssistant
import circolareplus.ai.assistant.AssistantKnowledgeLoader
import circolareplus.ai.AiProvider
import circolareplus.ai.AiRoutePlan
import circolareplus.ai.ChainedAiClassifier
import circolareplus.ai.ClientSideAiClassifier
import circolareplus.ai.LocalAiCatalog
import circolareplus.ai.LocalAiClassifier
import circolareplus.ai.LocalAiModel
import circolareplus.ai.LocalLlm
import circolareplus.ai.LocalModelStore
import circolareplus.ai.PdfTextExtractor
import circolareplus.ai.deviceTierForRam
import circolareplus.ai.planAiRoute
import circolareplus.ai.totalDeviceRamMb
import circolareplus.data.local.LocalSettingsManager
import circolareplus.platform.currentTimeMillis
import circolareplus.data.remote.ApiClient
import circolareplus.data.repository.AuthRepository
import circolareplus.data.repository.CalendarRepository
import circolareplus.data.repository.CircularsRepository
import circolareplus.data.repository.FcmRepository
import circolareplus.data.repository.PollsRepository
import circolareplus.data.repository.PreferencesRepository
import circolareplus.data.repository.ProposalsRepository
import circolareplus.data.repository.RankingPollsRepository
import circolareplus.data.repository.RatingsRepository
import circolareplus.data.repository.SeatMapRepository
import circolareplus.data.repository.UsersRepository
import circolareplus.push.PushTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Piccolo service locator condiviso KMP: evita di ricreare ApiClient/repository ad ogni
 * composable e centralizza lo stato di sessione (token, api key AI) in un unico posto.
 * Non è un vero framework di DI (Koin/Hilt) ma è sufficiente per la dimensione di questo
 * progetto ed è facilmente sostituibile in seguito senza toccare le screen, che ricevono
 * sempre dati e callback dall'esterno.
 */
object AppContainer {
    val settings: LocalSettingsManager by lazy { LocalSettingsManager() }
    val api: ApiClient by lazy { ApiClient(settings) }

    /**
     * Lavori brevi che devono finire anche se la schermata che li ha avviati sparisce (es. la
     * rimozione del token push al logout).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authRepository: AuthRepository by lazy { AuthRepository(api, settings) }
    val usersRepository: UsersRepository by lazy { UsersRepository(api) }
    val circularsRepository: CircularsRepository by lazy { CircularsRepository(api) }
    val calendarRepository: CalendarRepository by lazy { CalendarRepository(api) }
    val proposalsRepository: ProposalsRepository by lazy { ProposalsRepository(api) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(api) }
    val ratingsRepository: RatingsRepository by lazy { RatingsRepository(api) }
    val seatMapRepository: SeatMapRepository by lazy { SeatMapRepository(api) }
    val pollsRepository: PollsRepository by lazy { PollsRepository(api) }
    val rankingPollsRepository: RankingPollsRepository by lazy { RankingPollsRepository(api) }
    val fcmRepository: FcmRepository by lazy { FcmRepository(api, settings, pushTokenProvider) }

    val pdfTextExtractor: PdfTextExtractor by lazy { PdfTextExtractor() }
    val pushTokenProvider: PushTokenProvider by lazy { PushTokenProvider() }

    /** Download, verifica e cancellazione dei modelli di AI locale. */
    val localModelStore: LocalModelStore by lazy { LocalModelStore() }

    /**
     * Il motore di inferenza locale è un singleton vero, a differenza dei classificatori:
     * caricare un modello costa secondi e centinaia di MB di memoria, quindi va tenuto caldo fra
     * una circolare e l'altra invece di essere ricostruito a ogni classificazione.
     */
    val localLlm: LocalLlm by lazy { LocalLlm() }

    /**
     * Raccoglie per l'assistente globale i dati che non stanno gia' in memoria nella schermata
     * principale (sondaggi, mappa posti, storico, valutazioni), filtrati per ruolo.
     */
    val assistantKnowledgeLoader: AssistantKnowledgeLoader by lazy {
        AssistantKnowledgeLoader(
            pollsRepository = pollsRepository,
            seatMapRepository = seatMapRepository,
            ratingsRepository = ratingsRepository,
            preferencesRepository = preferencesRepository
        )
    }

    /**
     * L'assistente conversazionale (il pulsante AI della Ricerca).
     *
     * Costruito nuovo a ogni chat, e con una *factory* di classificatori invece che con un
     * classificatore gia' pronto, per lo stesso motivo per cui [newAiClassifier] non e' lazy:
     * provider, chiave e modello locale vanno riletti dalle impostazioni a ogni domanda, cosi'
     * cambiarli durante una conversazione ha effetto dal messaggio successivo.
     */
    fun newAssistant(): AilaAssistant = AilaAssistant(
        classifierFactory = { newAiClassifier(localThinking = settings.assistantThinkingEnabled) },
        circularsRepository = circularsRepository,
        pdfTextExtractor = pdfTextExtractor
    )

    /**
     * Il modello di AI locale scelto dall'utente, oppure il consigliato per la RAM del telefono
     * se non ha ancora scelto.
     */
    fun selectedLocalModel(): LocalAiModel =
        LocalAiCatalog.byId(settings.localAiModelId)
            ?: LocalAiCatalog.recommendedFor(deviceTierForRam(totalDeviceRamMb()))

    /**
     * `true` quando la classificazione parte dal modello sul telefono.
     *
     * Serve a chi deve dosare il lavoro: una classificazione locale non e' una chiamata di rete
     * da un secondo, e' da mezzo minuto di CPU o GPU a pieno regime. Le parti dell'app che
     * classificano in anticipo o in sottofondo si comportano di conseguenza.
     */
    fun isUsingLocalAiFirst(): Boolean {
        if (AiProvider.fromId(settings.aiProvider) != AiProvider.ON_DEVICE) return false
        val plan = currentRoute(AiProvider.ON_DEVICE, pdfTextLength = null)
        val model = plan.localModel ?: return false
        return !plan.cloudFirst && localModelStore.isInstalled(model)
    }

    /**
     * Pausa di AICore dopo un errore che riprovare subito non risolve: una sola per processo,
     * cosi' tutte le analisi in coda la vedono. Dopo un riavvio dell'app si riparte da AICore.
     */
    val aiCoreCooldown: AiCoreCooldown by lazy { AiCoreCooldown(now = ::currentTimeMillis) }

    private fun currentRoute(provider: AiProvider, pdfTextLength: Int?): AiRoutePlan {
        val paused = aiCoreCooldown.isPaused()
        return planAiRoute(
            provider = provider,
            selectedModel = selectedLocalModel(),
            // Servono solo per ripiegare da AICore in pausa: altrimenti non si guarda il disco.
            installedModels = if (paused) localModelStore.installedModels() else emptyList(),
            hasCloudKey = settings.userAiApiKey.isNotBlank(),
            aiCorePaused = paused,
            textLength = pdfTextLength,
            preferCloudForLongText = PREFER_CLOUD_FOR_LONG_TEXT
        )
    }

    /**
     * Se, con una chiave cloud configurata, le circolari lunghe scavalcano il provider "AI
     * locale" scelto dall'utente (vedi [shouldPreferCloudForLength]).
     *
     * Spento: la soglia e' `maxPromptChars` (8.192 caratteri per AICore, 5.600 per Phi), ma il
     * classificatore locale tronca comunque a 6.000 caratteri di PDF, quindi la maggior parte
     * delle circolari con allegati la superava e andava a Gemini senza che l'utente lo sapesse:
     * in campo AICore sembrava "non funzionare" e partiva Gemini Flash, mentre senza chiave
     * funzionava. Una scelta esplicita del provider vale piu' di un'ottimizzazione silenziosa.
     * Se il locale fallisce davvero, la catena passa comunque al cloud.
     */
    private const val PREFER_CLOUD_FOR_LONG_TEXT = false

    /**
     * Nuova istanza ad ogni chiamata (non lazy/singleton) perché rilegge ogni volta provider,
     * chiave AI e modello locale dalle impostazioni: se l'utente li cambia, la classificazione
     * successiva deve usare subito i valori aggiornati.
     *
     * I due provider vengono sempre incatenati, con quello scelto per primo: falliscono in
     * situazioni opposte — Google senza rete o con la quota finita, il locale se il modello non
     * è stato scaricato — quindi chi ha configurato entrambi ottiene un riassunto vero anche
     * quando uno dei due non è utilizzabile, invece dell'euristica a parole chiave.
     *
     * [allowLocalFallback] a `false` disattiva l'escalation al modello locale quando il primario
     * e' il cloud e fallisce — vedi il commento su [ChainedAiClassifier.escalateToSecondary].
     * Non ha effetto quando il provider scelto e' gia' l'AI locale: li' non c'e' nessuna
     * escalation "pesante" da evitare, il locale e' gia' il primario.
     *
     * [pdfTextLength] e' la lunghezza del testo da classificare, se già nota a chi chiama (vedi
     * [circolareplus.ui.MainAppShell.classifyCircularIfNeeded]). Quando supera
     * [circolareplus.ai.LocalAiModel.maxPromptChars] del modello locale selezionato e c'e' una
     * chiave cloud configurata, il provider "AI locale" scelto dall'utente viene anteposto dal
     * cloud solo per questa chiamata — vedi [shouldPreferCloudForLength] — invece di lasciar
     * troncare silenziosamente una circolare che il cloud potrebbe leggere per intero. Oggi
     * l'anteposizione e' spenta: vedi [PREFER_CLOUD_FOR_LONG_TEXT].
     */
    fun newAiClassifier(
        allowLocalFallback: Boolean = true,
        pdfTextLength: Int? = null,
        /** Ragionamento del modello locale: lo passa solo l'assistente, vedi [LocalAiClassifier]. */
        localThinking: Boolean = false
    ): AiClassifier {
        val cloud = ClientSideAiClassifier(userApiKey = settings.userAiApiKey)

        val provider = AiProvider.fromId(settings.aiProvider)
        // AICore in pausa (vedi AiCoreCooldown) si salta: al suo posto un modello LiteRT-LM gia'
        // scaricato, o il cloud se non ce n'e'.
        val plan = currentRoute(provider, pdfTextLength)
        val model = plan.localModel
        val local = LocalAiClassifier(
            model = model,
            modelPath = model?.let { localModelStore.installedPath(it) },
            llm = localLlm,
            enableThinking = localThinking,
            aiCoreCooldown = aiCoreCooldown,
            noModelReason = if (plan.aiCoreSkipped && model == null) aiCoreCooldown.skipReason() else null
        )

        return when {
            provider == AiProvider.ON_DEVICE && !plan.cloudFirst ->
                ChainedAiClassifier(primary = local, secondary = cloud)
            else -> ChainedAiClassifier(
                primary = cloud,
                secondary = local,
                escalateToSecondary = allowLocalFallback
            )
        }
    }
}
