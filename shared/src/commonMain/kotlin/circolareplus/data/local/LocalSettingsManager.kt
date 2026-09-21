package circolareplus.data.local

import circolareplus.ai.assistant.AssistantConversation
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.NotificationLogEntry
import circolareplus.platform.currentTimeMillis
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Gestione dello storage locale delle impostazioni utente su Android e iOS
 * (API Key personale per AI locale, silenziare notifiche bacheca, credenziali locali,
 * storico notifiche ricevute)
 */
class LocalSettingsManager(
    private val settings: Settings = Settings()
) {
    companion object {
        private const val KEY_USER_AI_API_KEY = "user_ai_api_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_LOCAL_AI_MODEL = "local_ai_model_id"
        private const val KEY_ASSISTANT_THINKING = "assistant_thinking_enabled"
        private const val KEY_BOARD_NOTIFICATIONS_ENABLED = "board_notifications_enabled"
        private const val KEY_SYSTEM_NOTIFICATIONS_ENABLED = "system_notifications_enabled"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_CACHED_USER = "cached_user_json"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_NOTIFICATION_LOG = "notification_log_json"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_SUBMITTED_POLLS = "submitted_polls"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_MUTED_NOTIFICATIONS = "muted_notification_kinds"
        private const val KEY_LAST_SEEN_CIRCULAR = "last_seen_circular_number"
        private const val KEY_LAST_SEEN_PROPOSAL = "last_seen_proposal_id"
        private const val KEY_LAST_SEEN_SEATMAP = "last_seen_seatmap_signature"
        private const val KEY_LAST_SEEN_PREFERENCES_OPEN = "last_seen_preferences_open"
        private const val KEY_LAST_SEEN_POLL_ID = "last_seen_poll_id"
        private const val KEY_ASSISTANT_HISTORY = "assistant_history_json"
        private const val KEY_CIRCULAR_ANALYSES = "circular_analyses_json"

        /** Quante analisi di circolari tenere sul telefono: le piu' recenti, per numero. */
        private const val MAX_CACHED_ANALYSES = 100
        /**
         * Quante conversazioni di AILA Assistant tenere, e quanti messaggi per conversazione.
         *
         * Ci sono dei tetti perche' sotto c'e' SharedPreferences (Android) / NSUserDefaults
         * (iOS): vengono caricati interi in memoria all'avvio, quindi non sono il posto dove far
         * crescere senza limite delle trascrizioni. Venti conversazioni da quaranta messaggi
         * sono qualche centinaio di KB nel caso peggiore, e coprono largamente il "ma cosa mi
         * aveva detto l'altra volta?" che e' il motivo per cui la cronologia esiste.
         */
        private const val MAX_ASSISTANT_CONVERSATIONS = 20
        private const val MAX_ASSISTANT_MESSAGES = 40
        /** Quante ricerche recenti tenere: quante ne mostra la schermata di ricerca. */
        private const val MAX_RECENT_SEARCHES = 5

        /** Le notifiche più vecchie di così vengono scartate automaticamente ad ogni lettura. */
        private const val NOTIFICATION_RETENTION_DAYS = 7
        private const val NOTIFICATION_RETENTION_MILLIS = NOTIFICATION_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** URL del Worker Cloudflare. Vuoto = usa [circolareplus.data.remote.ApiConfig.DEFAULT_BASE_URL]. */
    var apiBaseUrlOverride: String
        get() = settings.getString(KEY_API_BASE_URL, "")
        set(value) = settings.putString(KEY_API_BASE_URL, value)

    var userAiApiKey: String
        get() = settings.getString(KEY_USER_AI_API_KEY, "")
        set(value) = settings.putString(KEY_USER_AI_API_KEY, value)

    /**
     * Provider AI scelto: uno degli `id` di [circolareplus.ai.AiProvider].
     *
     * Le installazioni aggiornate da una versione precedente possono avere ancora
     * `"GITHUB_MODELS"` salvato qui. Non serve una migrazione: `AiProvider.fromId` non riconosce
     * quel valore e ricade su Google AI Studio, che è esattamente il provider che quelle
     * installazioni stavano già usando davvero (GitHub Models non era mai stato collegato alla
     * classificazione).
     */
    var aiProvider: String
        get() = settings.getString(KEY_AI_PROVIDER, "GOOGLE_AI_STUDIO")
        set(value) = settings.putString(KEY_AI_PROVIDER, value)

    /**
     * `id` del modello di AI locale scelto (vedi `LocalAiCatalog`). Vuoto = nessuna scelta
     * ancora fatta, quindi vale il consigliato per la fascia di RAM del telefono.
     *
     * Si salva l'id e non il percorso del file: il percorso cambia a ogni reinstallazione
     * dell'app, l'id no.
     */
    var localAiModelId: String
        get() = settings.getString(KEY_LOCAL_AI_MODEL, "")
        set(value) = settings.putString(KEY_LOCAL_AI_MODEL, value)

    /**
     * Se l'assistente, quando gira sul modello locale, puo' "ragionare" prima di rispondere
     * (modalita' thinking di Qwen/Gemma). Spento di default: le risposte sono molto piu' rapide;
     * acceso sono piu' ponderate ma su un telefono possono voler dire minuti.
     */
    var assistantThinkingEnabled: Boolean
        get() = settings.getBoolean(KEY_ASSISTANT_THINKING, false)
        set(value) = settings.putBoolean(KEY_ASSISTANT_THINKING, value)

    var isBoardNotificationEnabled: Boolean
        get() = settings.getBoolean(KEY_BOARD_NOTIFICATIONS_ENABLED, true)
        set(value) = settings.putBoolean(KEY_BOARD_NOTIFICATIONS_ENABLED, value)

    var isSystemNotificationsEnabled: Boolean
        get() = settings.getBoolean(KEY_SYSTEM_NOTIFICATIONS_ENABLED, true)
        set(value) = settings.putBoolean(KEY_SYSTEM_NOTIFICATIONS_ENABLED, value)

    /**
     * Onboarding a 3 schermate già visto: mostrato una sola volta al primo avvio, prima del
     * login. Si azzera solo svuotando i dati dell'app (o con [clear], cioè al logout).
     */
    var hasSeenOnboarding: Boolean
        get() = settings.getBoolean(KEY_ONBOARDING_SEEN, false)
        set(value) = settings.putBoolean(KEY_ONBOARDING_SEEN, value)

    /**
     * Ultime ricerche fatte nella ricerca globale, più recente per prima. Salvate in locale e
     * mai inviate al server: servono solo a ripresentare le voci nella schermata di ricerca.
     */
    val recentSearches: List<String>
        get() = settings.getStringOrNull(KEY_RECENT_SEARCHES)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Registra una ricerca in cima all'elenco, senza duplicati e tenendone al massimo cinque. */
    fun rememberSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + recentSearches.filter { !it.equals(clean, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        settings.putString(KEY_RECENT_SEARCHES, updated.joinToString("\n"))
    }

    fun clearRecentSearches() {
        settings.remove(KEY_RECENT_SEARCHES)
    }

    /**
     * Sondaggi per cui lo studente ha premuto "Invia le mie scelte".
     *
     * **Per ora è solo locale.** Perché l'algoritmo possa partire "quando tutti hanno finito"
     * serve che l'invio arrivi al server: una colonna `submitted_at` su `interrogation_votes` (o
     * una tabella a parte) e un endpoint che la valorizzi. Finché non c'è, questo flag serve a
     * chiudere il flusso lato studente — sa di aver finito e non tocca più i voti per sbaglio —
     * ma il Rappresentante non può vedere chi ha inviato.
     */
    fun isPollSubmitted(pollId: String): Boolean =
        pollId in (settings.getStringOrNull(KEY_SUBMITTED_POLLS)?.split("\n").orEmpty())

    fun setPollSubmitted(pollId: String, submitted: Boolean) {
        val current = settings.getStringOrNull(KEY_SUBMITTED_POLLS)?.split("\n")
            ?.filter { it.isNotBlank() }.orEmpty()
        val updated = if (submitted) (current + pollId).distinct() else current.filter { it != pollId }
        settings.putString(KEY_SUBMITTED_POLLS, updated.joinToString("\n"))
    }

    // --- Aspetto -----------------------------------------------------------------------------

    var isDarkMode: Boolean
        get() = settings.getBoolean(KEY_DARK_MODE, false)
        set(value) = settings.putBoolean(KEY_DARK_MODE, value)

    // --- Filtri delle notifiche ----------------------------------------------------------------

    /**
     * Categorie di notifiche silenziate. Si memorizzano quelle SPENTE e non quelle accese, così
     * una categoria nuova aggiunta in futuro parte attiva senza bisogno di migrazioni.
     */
    fun isNotificationKindEnabled(kind: String): Boolean =
        kind !in (settings.getStringOrNull(KEY_MUTED_NOTIFICATIONS)?.split("\n").orEmpty())

    fun setNotificationKindEnabled(kind: String, enabled: Boolean) {
        val muted = settings.getStringOrNull(KEY_MUTED_NOTIFICATIONS)?.split("\n")
            ?.filter { it.isNotBlank() }.orEmpty()
        val updated = if (enabled) muted.filter { it != kind } else (muted + kind).distinct()
        settings.putString(KEY_MUTED_NOTIFICATIONS, updated.joinToString("\n"))
    }

    // --- Rilevamento novità --------------------------------------------------------------------
    // Servono a capire cosa è cambiato dall'ultima volta che l'utente ha guardato, per poter
    // scrivere una notifica locale. Il push vero (FCM) resta, ma copre solo i casi in cui il
    // server manda davvero un messaggio: tutto il resto (nuova circolare vista al primo
    // caricamento, nuova proposta, nuova disposizione dei banchi) prima non lasciava traccia.

    var lastSeenCircularNumber: Int
        get() = settings.getInt(KEY_LAST_SEEN_CIRCULAR, 0)
        set(value) = settings.putInt(KEY_LAST_SEEN_CIRCULAR, value)

    var lastSeenProposalId: String
        get() = settings.getString(KEY_LAST_SEEN_PROPOSAL, "")
        set(value) = settings.putString(KEY_LAST_SEEN_PROPOSAL, value)

    var lastSeenSeatMapSignature: String
        get() = settings.getString(KEY_LAST_SEEN_SEATMAP, "")
        set(value) = settings.putString(KEY_LAST_SEEN_SEATMAP, value)

    var lastSeenPreferencesOpen: Boolean
        get() = settings.getBoolean(KEY_LAST_SEEN_PREFERENCES_OPEN, false)
        set(value) = settings.putBoolean(KEY_LAST_SEEN_PREFERENCES_OPEN, value)

    var lastSeenPollId: String
        get() = settings.getString(KEY_LAST_SEEN_POLL_ID, "")
        set(value) = settings.putString(KEY_LAST_SEEN_POLL_ID, value)

    var authToken: String
        get() = settings.getString(KEY_AUTH_TOKEN, "")
        set(value) = settings.putString(KEY_AUTH_TOKEN, value)

    var currentUserId: String
        get() = settings.getString(KEY_USER_ID, "")
        set(value) = settings.putString(KEY_USER_ID, value)

    /**
     * Ultimo profilo utente ricevuto dal server, in JSON.
     *
     * Serve a una cosa sola: poter entrare nell'app senza rete. Prima, all'avvio in modalità
     * aereo, il ripristino della sessione falliva e l'app cancellava il token — cioè faceva
     * uscire dall'account invece di mostrare gli stati "nessuna connessione". Con questa copia,
     * il token resta e si entra con l'ultimo profilo conosciuto. Viene svuotata al logout.
     */
    var cachedUserJson: String
        get() = settings.getString(KEY_CACHED_USER, "")
        set(value) = settings.putString(KEY_CACHED_USER, value)

    /**
     * Aggiunge una notifica al log locale (mai inviata al server) e scarta quelle più vecchie
     * della soglia di conservazione. Va chiamata dal lato piattaforma che riceve davvero il push
     * (es. il FirebaseMessagingService su Android), non dal solo invio.
     */
    fun addNotification(title: String, body: String, category: String = "", id: String? = null) {
        val now = currentTimeMillis()
        val entry = NotificationLogEntry(
            id = id ?: "$now-${(0..999999).random()}",
            title = title,
            body = body,
            receivedAtMillis = now,
            category = category
        )
        val updated = (readNotifications(now) + entry).sortedByDescending { it.receivedAtMillis }
        writeNotifications(updated)
    }

    /**
     * Punto unico con cui Android e iOS registrano un push appena ricevuto: applica gli
     * interruttori dell'utente, evita i doppioni e scrive nella campanella. Ritorna `true` se
     * la piattaforma deve anche mostrare la notifica di sistema (banner).
     *
     * - Categoria silenziata in Impostazioni: né campanella né banner.
     * - "Notifiche di sistema" spento: solo campanella, niente banner.
     * - [messageId] già visto (es. iOS: prima `willPresent`, poi il tocco `didReceive` sullo
     *   stesso messaggio): non si scrive di nuovo, la campanella non ha doppioni.
     *
     * [messageId] deve essere l'id del messaggio FCM; se assente si registra senza dedup.
     */
    fun onPushReceived(messageId: String?, title: String, body: String, category: String): Boolean {
        val kind = if (category == "seatmap_preferences") "seatmap" else category
        if (kind.isNotBlank() && !isNotificationKindEnabled(kind)) return false

        val id = messageId?.takeIf { it.isNotBlank() }?.let { "push-$it" }
        val alreadyLogged = id != null && readNotifications(currentTimeMillis()).any { it.id == id }
        if (!alreadyLogged) addNotification(title, body, category, id)
        return isSystemNotificationsEnabled
    }

    /** Elenco notifiche non scadute, più recenti prima. Scarta e ripulisce quelle scadute. */
    fun listNotifications(): List<NotificationLogEntry> {
        val fresh = readNotifications(currentTimeMillis())
        writeNotifications(fresh) // ripulisce lo storage se qualcosa era scaduto
        return fresh.sortedByDescending { it.receivedAtMillis }
    }

    fun markAllNotificationsRead() {
        writeNotifications(readNotifications(currentTimeMillis()).map { it.copy(read = true) })
    }

    fun clearNotifications() {
        settings.remove(KEY_NOTIFICATION_LOG)
    }

    /**
     * Le conversazioni archiviate di AILA Assistant, la piu' recente prima.
     *
     * Stanno sul dispositivo e non sul server di proposito: una trascrizione della chat contiene
     * le analisi personali delle circolari, le scadenze di chi ha chiesto e i dati della mappa
     * posti, cioe' esattamente quello che l'app promette di non far uscire dal telefono (vedi
     * README, "Privacy & AI"). Il prezzo accettato e' che la cronologia non si sincronizza fra
     * dispositivi e si perde a reinstallazione — e che il logout la cancella, perche'
     * [clear] svuota le impostazioni.
     */
    fun listAssistantConversations(): List<AssistantConversation> =
        readAssistantConversations().sortedByDescending { it.updatedAtMillis }

    /**
     * Salva una conversazione, o aggiorna quella con lo stesso `id` se c'e' gia'.
     *
     * Si chiama a ogni messaggio e non all'uscita dalla schermata: l'app puo' essere chiusa dal
     * sistema mentre il modello sta ancora rispondendo, e una cronologia che perde proprio
     * l'ultima conversazione e' quella che serve meno.
     */
    fun saveAssistantConversation(conversation: AssistantConversation) {
        if (conversation.messages.isEmpty()) return
        val trimmed = conversation.copy(
            messages = conversation.messages.takeLast(MAX_ASSISTANT_MESSAGES)
        )
        val others = readAssistantConversations().filter { it.id != trimmed.id }
        val updated = (listOf(trimmed) + others)
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_ASSISTANT_CONVERSATIONS)
        writeAssistantConversations(updated)
    }

    fun deleteAssistantConversation(id: String) {
        writeAssistantConversations(readAssistantConversations().filter { it.id != id })
    }

    /**
     * Le analisi AI delle circolari gia' prodotte (o scaricate dalla cache del server), per numero.
     *
     * Stavano solo in memoria: a ogni riavvio dell'app le spiegazioni sparivano dalla lista e
     * tornavano una per volta, quando tornavano — con l'AI locale, solo aprendo ciascuna
     * circolare. Non si salvano i ripieghi euristici ([CircularAiClassification.isFallback]): non
     * sono spiegazioni, e tenerli bloccherebbe una nuova analisi.
     */
    fun readClassificationCache(): Map<Int, CircularAiClassification> {
        val raw = settings.getStringOrNull(KEY_CIRCULAR_ANALYSES) ?: return emptyMap()
        return try {
            json.decodeFromString<List<CircularAiClassification>>(raw).associateBy { it.circularNumber }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveClassification(classification: CircularAiClassification) {
        if (classification.isFallback) return
        val updated = (readClassificationCache() + (classification.circularNumber to classification))
            .values
            .sortedByDescending { it.circularNumber }
            .take(MAX_CACHED_ANALYSES)
        settings.putString(KEY_CIRCULAR_ANALYSES, json.encodeToString(updated))
    }

    fun clearAssistantHistory() {
        settings.remove(KEY_ASSISTANT_HISTORY)
    }

    private fun readAssistantConversations(): List<AssistantConversation> {
        val raw = settings.getStringOrNull(KEY_ASSISTANT_HISTORY) ?: return emptyList()
        // Stesso criterio del log notifiche: se il JSON e' di un formato vecchio o corrotto si
        // riparte da vuoto, invece di far crashare l'apertura della chat per una cronologia.
        return try {
            json.decodeFromString<List<AssistantConversation>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAssistantConversations(conversations: List<AssistantConversation>) {
        settings.putString(KEY_ASSISTANT_HISTORY, json.encodeToString(conversations))
    }

    private fun readNotifications(now: Long): List<NotificationLogEntry> {
        val raw = settings.getStringOrNull(KEY_NOTIFICATION_LOG) ?: return emptyList()
        val all = try {
            json.decodeFromString<List<NotificationLogEntry>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
        return all.filter { now - it.receivedAtMillis <= NOTIFICATION_RETENTION_MILLIS }
    }

    private fun writeNotifications(entries: List<NotificationLogEntry>) {
        settings.putString(KEY_NOTIFICATION_LOG, json.encodeToString(entries))
    }

    /**
     * Svuota le impostazioni (usato al logout) tenendo però il flag dell'onboarding: dopo un
     * logout si torna al login, non alle schermate di presentazione — quelle si vedono una volta
     * sola per installazione.
     */
    fun clear() {
        val onboardingSeen = hasSeenOnboarding
        settings.clear()
        hasSeenOnboarding = onboardingSeen
    }
}
