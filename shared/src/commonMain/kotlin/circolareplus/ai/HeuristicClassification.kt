package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge

/**
 * Classificazione di riserva a parole chiave, usata quando nessun provider AI risponde.
 *
 * Stava duplicata dentro ciascun classificatore, con elenchi di parole leggermente diversi.
 * Averla in un posto solo non è solo ordine: le regole di prima erano **sbagliate** e lo erano in
 * due copie.
 *
 * Il caso che l'ha smascherata è la circolare "Festa di sport", indirizzata testualmente "A tutti
 * gli studenti dell'istituto", che veniva marcata "Destinata esclusivamente a docenti o personale
 * ATA". Due errori sommati:
 *
 * 1. Fra le parole che indicavano "roba per il personale" c'erano `consiglio di istituto` e
 *    `collegio dei docenti`. Sono organi della scuola, e una circolare per gli studenti li cita di
 *    continuo — "ai sensi della delibera del Consiglio di Istituto n. 16" — senza che questo la
 *    renda per il personale. Erano di fatto una condanna a caso.
 * 2. Il controllo sul personale veniva prima di quello sugli studenti, quindi una circolare che
 *    diceva sia "a tutti gli studenti" sia "consiglio di istituto" finiva comunque a "non ti
 *    riguarda".
 *
 * Ora si guarda prima a chi è indirizzata: se nomina esplicitamente gli studenti o le famiglie,
 * non può essere solo per il personale, punto. E le parole per il personale sono espressioni di
 * destinatario vero ("ai soli docenti", "riservata al personale ATA"), non nomi di organi.
 */
internal object HeuristicClassification {

    /**
     * Quanto tenere del motivo tecnico di un fallimento nel riassunto mostrato in app.
     *
     * Serve un tetto perché senza il testo dell'eccezione finisce in schermata per intero: un
     * errore del motore locale porta con sé il trace del codice C++ (`llm_engine.cc:2580`, i
     * `type.googleapis.com/mediapipe.StatusList` con i byte grezzi), e l'utente si ritrova mezzo
     * schermo di roba illeggibile al posto del riassunto. La prima riga dice già di che errore si
     * tratta; il resto è per chi legge un log, e in app non serve.
     */
    private const val MAX_REASON_CHARS = 140

    /** Riduce un motivo tecnico a una riga leggibile. */
    fun shortenReason(reason: String): String {
        val firstLine = reason.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine.length <= MAX_REASON_CHARS) {
            firstLine
        } else {
            firstLine.take(MAX_REASON_CHARS).trimEnd() + "…"
        }
    }

    private val STUDENT_ADDRESSED = listOf(
        "agli studenti",
        "a tutti gli studenti",
        "agli alunni",
        "a tutti gli alunni",
        "alle famiglie",
        "ai genitori",
        "studenti e ai loro genitori",
        "studenti e alle famiglie"
    )

    private val CLASS_ADDRESSED = listOf(
        "4csa", "4 csa", "4^csa", "4^ csa",
        "classi quarte",
        "tutte le classi",
        "tutti gli studenti",
        "tutte le componenti"
    )

    /**
     * Espressioni che indicano davvero un destinatario ristretto al personale. Niente nomi di
     * organi collegiali qui dentro: vedi la nota in cima alla classe.
     */
    private val STAFF_ONLY_ADDRESSED = listOf(
        "solo ai docenti",
        "ai soli docenti",
        "soli docenti",
        "riservata ai docenti",
        "esclusivamente ai docenti",
        "solo docenti",
        "ai soli assistenti",
        "riservata al personale",
        "esclusivamente al personale",
        "solo al personale ata",
        "ai soli docenti e al personale ata"
    )

    private val OPTIONAL_ACTIVITY = listOf(
        "facoltativ",
        "corso pomeridiano",
        "adesione volontaria",
        "adesione facoltativa",
        "chi fosse interessato",
        "per gli studenti interessati",
        "olimpiadi",
        "open day",
        "borsa di studio",
        "borse di studio"
    )

    /**
     * Costruisce la classificazione di riserva. È sempre marcata
     * [CircularAiClassification.isFallback] così [ChainedAiClassifier] sa che può valere la pena
     * provare l'altro provider.
     *
     * [failureReason] è `null` quando il provider non era proprio configurato (nessuna chiave,
     * nessun modello scaricato): in quel caso il messaggio invita a configurarlo, invece di
     * mostrare un errore che non c'è stato.
     */
    fun classify(
        circularNumber: Int,
        title: String,
        text: String,
        failureReason: String?,
        notConfiguredMessage: String
    ): CircularAiClassification {
        val lower = "$title $text".lowercase()

        val mentionsStudents = STUDENT_ADDRESSED.any { lower.contains(it) }
        val mentionsClass = CLASS_ADDRESSED.any { lower.contains(it) }
        val staffOnly = STAFF_ONLY_ADDRESSED.any { lower.contains(it) }
        val optional = OPTIONAL_ACTIVITY.any { lower.contains(it) }

        val (badge, summary) = when {
            // Un fallimento AI reale va sempre mostrato per primo: altrimenti un testo che
            // sembra un'analisi vera (ma è solo una coincidenza lessicale con le liste qui sotto)
            // nasconde l'errore effettivo (quota esaurita, timeout, chiave non valida, rete).
            failureReason != null ->
                CircularRelevanceBadge.POTENTIAL to
                    "Analisi AI non riuscita (${shortenReason(failureReason)}) — risultato di riserva, apri il PDF per controllare."
            // Prima gli studenti: se la circolare li nomina fra i destinatari, non è "solo per il
            // personale" nemmeno se più avanti cita un organo o si rivolge anche ai docenti.
            mentionsClass || mentionsStudents ->
                CircularRelevanceBadge.RELEVANT to
                    "Rivolta agli studenti o a tutte le classi: contiene comunicazioni che ti riguardano."
            staffOnly ->
                CircularRelevanceBadge.NOT_RELEVANT to
                    "Indirizzata soltanto ai docenti o al personale della scuola."
            optional ->
                CircularRelevanceBadge.POTENTIAL to
                    "Attività o iniziativa ad adesione facoltativa."
            else ->
                CircularRelevanceBadge.POTENTIAL to notConfiguredMessage
        }

        return CircularAiClassification(
            circularNumber = circularNumber,
            badge = badge,
            personalSummary = summary,
            detectedDeadlines = emptyList(),
            isFallback = true,
            modelLabel = "Euristica a parole chiave"
        )
    }
}
