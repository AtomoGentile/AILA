package circolareplus.ai

/**
 * Fascia hardware del telefono, decisa dalla RAM totale. Serve a proporre un modello adatto:
 * un modello troppo grande non è "lento", viene ucciso dal sistema a metà generazione.
 */
enum class DeviceTier(val label: String) {
    /** Fino a ~4 GB. */
    LOW("Fino a 4 GB"),
    /** Tra ~4 e ~8 GB. */
    MID("4-8 GB"),
    /** ~8 GB o più. */
    HIGH("8 GB o più"),
    /** RAM non leggibile (iOS, o API di sistema che non risponde). */
    UNKNOWN("Sconosciuta")
}

/**
 * Soglie in MB e non in GB "tondi" perché `ActivityManager.MemoryInfo.totalMem` non restituisce
 * mai la RAM nominale: una parte è riservata al kernel e alla GPU, quindi un telefono venduto
 * come 8 GB si dichiara intorno a 7,3-7,6 GB e uno da 4 GB intorno a 3,6-3,8 GB. Con le soglie a
 * 4096/8192 quei telefoni finirebbero tutti nella fascia sotto.
 */
private const val MID_TIER_MIN_MB = 3_600
private const val HIGH_TIER_MIN_MB = 7_200

fun deviceTierForRam(totalRamMb: Int): DeviceTier = when {
    totalRamMb <= 0 -> DeviceTier.UNKNOWN
    totalRamMb < MID_TIER_MIN_MB -> DeviceTier.LOW
    totalRamMb < HIGH_TIER_MIN_MB -> DeviceTier.MID
    else -> DeviceTier.HIGH
}

/**
 * Un modello scaricabile ed eseguibile sul telefono.
 *
 * Tutti i modelli del catalogo sono ospitati su HuggingFace **senza gating**: il download è un
 * semplice GET, non serve né un account né un token. È il criterio che decide cosa può stare qui
 * dentro, ed è il motivo per cui mancano nomi che ci si aspetterebbe — i repo
 * `litert-community/Gemma3-*` e `litert-community/Llama-3.2-*` sono gated e rispondono 401 a chi
 * non ha accettato la licenza da loggato, quindi l'app non potrebbe scaricarli da sola.
 */
data class LocalAiModel(
    val id: String,
    val displayName: String,
    /** Nome del file su disco, uguale a quello su HuggingFace. */
    val fileName: String,
    val downloadUrl: String,
    /** Dimensione del download in byte, per la barra di avanzamento e il controllo spazio. */
    val approxSizeBytes: Long,
    /** Fascia per cui questo modello è il consigliato. */
    val tier: DeviceTier,
    /**
     * RAM totale in MB sotto la quale il modello, in esecuzione, rischia seriamente di far
     * chiudere l'app dal sistema.
     *
     * Non è la dimensione del file: il `.litertlm` viene letto in memory mapping, quindi non
     * finisce tutto in RAM. I valori vengono dalle misure che Google pubblica nei model card
     * (colonna "CPU Memory" delle tabelle Android), sommate ai 2-2,5 GB che Android occupa già
     * con sé stesso. Si usa la misura su CPU e non quella su GPU, molto più bassa, perché la CPU
     * è il caso peggiore: è dove si finisce se l'accelerazione non parte.
     */
    val recommendedRamMb: Int,
    /**
     * Se provare prima l'accelerazione GPU. Se la GPU non parte, [LocalLlm] ripiega sulla CPU.
     *
     * Nel catalogo non ci sono più i file con il suffisso `-gpu`: con loro quel ripiego non
     * funziona — contengono solo i pesi preconfezionati per la GPU — e sui telefoni provati non
     * si avviavano affatto.
     */
    val preferGpu: Boolean,
    /**
     * Se il modello è addestrato a produrre azioni strutturate (tool / function calling).
     *
     * È il requisito della parte agentica: non basta che il modello riassuma, deve saper dire
     * "qui c'è un pagamento entro il 20 ottobre" in un JSON che l'app trasforma in un evento del
     * calendario, e sbagliarlo di rado. I modelli addestrati sul tool calling lo fanno molto
     * meglio degli altri a parità di dimensione, ed è per questo che è un criterio a sé e non una
     * conseguenza del numero di parametri.
     */
    val supportsActions: Boolean,
    /**
     * Tetto ai token che il modello può generare in una risposta.
     *
     * Prima qui c'era la finestra di contesto, che il runtime precedente pretendeva fosse
     * dichiarata a mano e che, se sbagliata, faceva fallire il caricamento. LiteRT-LM la ricava
     * dal file da solo, quindi resta solo la cosa che serve davvero: un limite alla lunghezza
     * della risposta, perché un modello che sbaglia il formato continuerebbe a scrivere per
     * minuti. Era 512 quando il prompt chiedeva "una frase": ora che il riassunto deve avere
     * 5-6 righe con date/nomi/obiettivi, 512 token lo tagliava a metà. 900 lasciano spazio al
     * riassunto piu' lungo restando ben sotto un timeout.
     */
    val maxOutputTokens: Int,
    /**
     * Tetto ai token che il modello accetta **in ingresso**.
     *
     * Il runtime non lo dichiara e non c'e' modo di chiederglielo prima di provare: si scopre
     * sbattendoci contro, con `Input token ids are too long. Exceeding the maximum number of
     * tokens allowed: 4471 >= 4096`. 4096 e' il valore con cui sono compilati i `.litertlm` del
     * catalogo, ed e' il default proprio perche' e' quello che si e' visto sul campo: meglio un
     * numero prudente valido per tutti che uno ottimistico per modello che fa fallire la
     * generazione invece di accorciare il prompt.
     */
    val maxInputTokens: Int = 4_096,
    val description: String
) {
    /** "2,0 GB" / "963 MB" — per le etichette dei pulsanti di download. */
    val readableSize: String
        get() {
            val mb = approxSizeBytes / 1_000_000
            return if (mb >= 1000) {
                val tenthsOfGb = mb / 100
                "${tenthsOfGb / 10},${tenthsOfGb % 10} GB"
            } else {
                "$mb MB"
            }
        }

    /**
     * `true` se su questo telefono il modello ha davvero lo spazio in memoria per girare.
     *
     * La schermata Impostazioni lo usa per avvisare **prima** del download invece di lasciare
     * scoprire il problema dopo aver scaricato qualche giga: quando è `false` il download resta
     * possibile — il calcolo è una stima, non un divieto — ma con un avviso esplicito.
     */
    fun fitsComfortablyIn(totalRamMb: Int): Boolean =
        totalRamMb <= 0 || totalRamMb >= recommendedRamMb

    /**
     * Quanti caratteri di prompt (istruzioni + richiesta) stanno in [maxInputTokens].
     *
     * Il rapporto e' 2 caratteri per token, molto piu' prudente dei 3,5-4 che si citano di
     * solito per l'italiano. Due motivi. Il contenuto: il contesto dell'assistente e' fatto in
     * buona parte di date, numeri di circolare e identificativi, e su quella roba i tokenizer
     * vanno malissimo — "2026-09-22" da solo sono dieci caratteri e sette token. E l'asimmetria
     * del costo dell'errore: stare stretti costa qualche circolare in meno nel contesto,
     * sforare costa una generazione intera buttata (mezzo minuto di CPU) prima di accorgersene.
     * Il ritentativo a meta' budget in [circolareplus.ai.LocalAiClassifier] resta come rete,
     * non come strada normale.
     */
    val maxPromptChars: Int
        get() = maxInputTokens * 2
}

/**
 * Catalogo dei modelli eseguibili sul telefono.
 *
 * Su Android: tre Qwen3.5 e due Gemma 4.
 * Su iOS: solo Apple Intelligence (modello di sistema, nessun download).
 *
 * Definito come `expect object` per avere implementazioni platform-specific:
 * - `LocalAiModels.android.kt` → 5 modelli scaricabili
 * - `LocalAiModels.ios.kt` → 1 modello di sistema
 */
expect object LocalAiCatalog {
    /** Catalogo di modelli disponibili per questa piattaforma. */
    val all: List<LocalAiModel>

    /** Cerca un modello per ID, null se non trovato. */
    fun byId(id: String?): LocalAiModel?

    /** Il modello consigliato per una fascia di RAM. */
    fun recommendedFor(tier: DeviceTier): LocalAiModel

    /** Tutti i modelli ordinati con il consigliato in cima. */
    fun selectableFor(totalRamMb: Int): List<LocalAiModel>

    /** Nomi di file di tutti i modelli conosciuti (serve per riconoscere orfani). */
    fun knownFileNames(): Set<String>
}
