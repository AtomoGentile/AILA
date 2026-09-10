package circolareplus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CircularRelevanceBadge {
    RELEVANT,      // 🟢 Ti riguarda (Pertinenza Alta: indicazioni dirette e vincolanti per la classe/studente)
    POTENTIAL,     // 🟡 Potenziale interesse (Pertinenza Media: impatto opzionale o corsi integrativi)
    NOT_RELEVANT   // ⚪ Non sembra riguardarti (Pertinenza Bassa: altre classi, docenti, ATA)
}

@Serializable
data class Circular(
    val number: Int,
    val title: String,
    val publishDate: String,
    val r2PdfKey: String,
    val downloadUrl: String? = null,
    val attachments: List<CircularAttachment> = emptyList()
)

/**
 * Un allegato della circolare (colonna "Allegati" di Spaggiari), distinto dal PDF principale.
 * [isPdf] distingue i due modi in cui la schermata di dettaglio lo tratta: `true` quando è in
 * cache su R2 come il PDF principale — le sue pagine vengono disegnate in coda al documento
 * (vedi [circolareplus.pdf.renderPdfPages]) e il suo testo entra nello stesso riassunto AI,
 * usando [pdfKey] per scaricarlo con la stessa rotta del documento principale — `false` quando
 * punta altrove (un'altra pagina del sito, non un PDF diretto) e si apre nel browser di sistema
 * tramite [downloadUrl], senza anteprima né analisi.
 */
@Serializable
data class CircularAttachment(
    val label: String,
    val downloadUrl: String,
    val isPdf: Boolean,
    val pdfKey: String? = null
)

@Serializable
data class CircularAiClassification(
    val circularNumber: Int,
    val badge: CircularRelevanceBadge,
    val personalSummary: String, // Box "Analisi personale AI"
    val detectedDeadlines: List<ExtractedDeadline> = emptyList(),
    /**
     * `true` quando questa classificazione NON viene da un modello ma dall'euristica a parole
     * chiave di riserva, cioè quando il provider AI ha fallito.
     *
     * Serve a `ChainedAiClassifier`: i classificatori non lanciano eccezioni — inghiottono
     * l'errore e restituiscono comunque un risultato — quindi senza questo flag non ci sarebbe
     * modo di distinguere un riassunto vero da uno di ripiego, e non si potrebbe decidere se
     * vale la pena provare l'altro provider. Ha un default perché è additivo: i JSON già
     * salvati senza questo campo continuano a deserializzare.
     */
    val isFallback: Boolean = false,
    /**
     * Chi ha prodotto questa analisi (es. "Google Gemini (gemini-flash-latest)", "AI locale
     * (TinyLlama 1.1B)", "Euristica a parole chiave"). Mostrata in app quando l'analisi viene
     * condivisa fra utenti tramite la cache server, così chi la legge sa quanto fidarsene senza
     * doverla rifare per controllare. Default per compatibilità con classificazioni salvate prima
     * di questo campo.
     */
    val modelLabel: String = "Sconosciuto"
)

@Serializable
data class ExtractedDeadline(
    val title: String,
    val dueDate: String,
    val time: String? = null,
    val category: String = "PAGAMENTO" // Pagamenti, uscite didattiche, avvisi
)

@Serializable
enum class CalendarEventCategory {
    VERIFICA,
    INTERROGAZIONE,
    PAGAMENTO,
    USCITA_DIDATTICA,
    AVVISO,
    ALTRO
}

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val time: String? = null,
    val category: CalendarEventCategory,
    val isForAll: Boolean = true,
    val isAiGenerated: Boolean = false,
    val createdByUserId: String? = null,
    // Destinatari specifici dell'evento (null = tutti, lista = solo questi studenti)
    val visibleToUserIds: List<String>? = null,
    // Note aggiuntive sull'evento
    val notes: String? = null
)

@Serializable
enum class ProposalStatus {
    NUOVA,
    IN_ANALISI,
    CHIUSA
}

@Serializable
data class Proposal(
    val id: String,
    val authorId: String? = null, // null se anonima e non revelata a chi guarda
    val authorName: String, // Mostra "Anonimo" all'utente se isAnonymous = true
    val isAnonymous: Boolean,
    val title: String,
    val description: String,
    val category: String,
    val status: ProposalStatus = ProposalStatus.NUOVA,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val commentsCount: Int = 0,
    /**
     * La proposta è stata modificata dopo la pubblicazione, da chiunque: l'autore che si
     * corregge o il Rappresentante. Si chiamava `modifiedByRep` perché all'inizio solo il
     * Rappresentante poteva modificare le proposte; ora può farlo anche l'autore, e in bacheca
     * quello che conta è che il testo non è più quello di partenza.
     */
    val isEdited: Boolean = false,
    val createdAt: String
)

@Serializable
data class ProposalComment(
    val id: String,
    val proposalId: String,
    val authorName: String,
    val content: String,
    val createdAt: String
)
