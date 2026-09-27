package circolareplus.ai.assistant

import circolareplus.domain.model.CalendarEventCategory
import circolareplus.util.parseIsoDate
import circolareplus.util.parseTimeToMinutes
import circolareplus.util.plusDays

/**
 * Le domande "elenco" — scadenze, pagamenti, verifiche, cosa c'e' questa settimana — risposte
 * dal codice invece che dal modello.
 *
 * Il motivo e' quello che si vedeva in chat con l'AI locale: a un modello da pochi miliardi di
 * parametri si chiedeva di **ricopiare** una dozzina di righe di date dal contesto, e le
 * ricopiava storpiate ("ore4:0" per "ore 14:00", "mercoi' 4" per "mercoledi' 14"), fuori ordine,
 * con i doppioni e con dentro le vacanze di Natale alla domanda sui pagamenti. Filtrare,
 * ordinare, togliere i doppioni e scrivere una data sono tutte cose che il codice fa senza
 * sbagliare e in un millisecondo; il modello non aggiungeva niente se non errori e attesa.
 *
 * Scatta solo quando la domanda e' davvero un elenco: se contiene anche solo una parola che
 * indica un argomento ("la verifica di *storia*", "quanto costa la *gita a Roma*") la risposta
 * resta al modello, che quella cosa la deve cercare nei riassunti e nei PDF.
 */
internal object AssistantAgenda {

    /**
     * Senza un periodo nella domanda, "in arrivo" vuol dire le prossime due settimane: oltre,
     * e' roba da calendario, non da "cosa mi scade".
     */
    private const val DEFAULT_HORIZON_DAYS = 15

    /** Oltre questo l'elenco in chat diventa illeggibile: il resto e' nel Calendario. */
    private const val MAX_ITEMS = 12

    /** Se nel periodo di default non c'e' niente, quante voci successive mostrare comunque. */
    private const val LATER_ITEMS = 3

    internal enum class Kind(val noun: String) {
        ALL("impegni ed eventi"),
        DEADLINES("scadenze e pagamenti"),
        PAYMENTS("pagamenti"),
        TESTS("verifiche e interrogazioni"),
        TRIPS("gite e uscite didattiche")
    }

    /** Una riga dell'elenco, da calendario o da una scadenza rilevata in una circolare. */
    internal data class Item(
        val date: String,
        val time: String?,
        val title: String,
        val category: CalendarEventCategory?,
        val circularNumber: Int?,
        val fromCalendar: Boolean
    )

    /**
     * La risposta gia' pronta, o `null` se la domanda non e' un elenco e va passata al modello.
     */
    fun answer(knowledge: AssistantKnowledge, question: String): AssistantReply? {
        val kind = kindOf(knowledge, question) ?: return null
        val scope = TimeScopeParser.parse(question, knowledge.todayIso)
        val today = parseIsoDate(knowledge.todayIso.take(10)) ?: return null

        val all = collectItems(knowledge).filter { matches(it, kind) }
        val (from, to) = if (scope != null) {
            scope.from to scope.to
        } else {
            knowledge.todayIso.take(10) to today.plusDays(DEFAULT_HORIZON_DAYS).toIso()
        }
        val inRange = all.filter { it.date in from..to }
        val later = if (scope == null && inRange.isEmpty()) {
            all.filter { it.date > to }.take(LATER_ITEMS)
        } else {
            emptyList()
        }
        val shown = (inRange.ifEmpty { later }).take(MAX_ITEMS)
        val period = scope?.describe() ?: "nei prossimi $DEFAULT_HORIZON_DAYS giorni"

        val text = buildString {
            when {
                inRange.isEmpty() && later.isEmpty() ->
                    append("Non trovo ${kind.noun} $period, ne' in calendario ne' nelle circolari analizzate.")
                inRange.isEmpty() -> {
                    append("Non trovo ${kind.noun} $period. Le prossime:")
                }
                else -> append("${kind.noun.replaceFirstChar { it.uppercase() }} $period:")
            }
            shown.forEach { appendLine().append(line(it, knowledge.todayIso)) }
            val hidden = inRange.size - shown.size
            if (hidden > 0) appendLine().append("…e altre $hidden: le trovi tutte nel Calendario.")
            if (shown.any { it.circularNumber != null } &&
                (kind == Kind.DEADLINES || kind == Kind.PAYMENTS || kind == Kind.TRIPS)
            ) {
                appendLine().appendLine().append("Importi e modalita' sono nella circolare indicata: aprila prima di pagare.")
            }
        }

        return AssistantReply(
            text = text,
            sources = sourcesOf(shown, knowledge),
            modelLabel = "Dal calendario e dalle circolari di AILA"
        )
    }

    /**
     * Che elenco chiede la domanda, o `null` se non e' una domanda-elenco.
     *
     * Ogni parola significativa della domanda deve essere una parola "da agenda" (scadenze,
     * pagamenti, prossima settimana...): basta una sola parola d'argomento per lasciare la
     * risposta al modello.
     */
    internal fun kindOf(knowledge: AssistantKnowledge, question: String): Kind? {
        if (AssistantContext.circularNumbersIn(question).isNotEmpty()) return null
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        val terms = AssistantContext.tokenize(question)
        if (terms.any { term -> AGENDA_WORDS.none { term.startsWith(it) } }) return null

        fun has(vararg stems: String) = words.any { word -> stems.any { word.startsWith(it) } }
        val payments = has("pagament", "pagare", "versament", "quote")
        val deadlines = has("scadenz", "scade", "termin", "consegn")
        val tests = has("verific", "interrog")
        val trips = has("gita", "gite", "uscit")
        val general = has("impegn", "evento", "eventi", "calendario", "agenda", "programma", "succede") ||
            phraseIn(words, "devo fare") || phraseIn(words, "cosa ho") || phraseIn(words, "che ho")

        return when {
            deadlines -> Kind.DEADLINES
            payments -> Kind.PAYMENTS
            tests && !trips -> Kind.TESTS
            trips && !tests -> Kind.TRIPS
            general || tests -> Kind.ALL
            TimeScopeParser.parse(question, knowledge.todayIso) != null && terms.isNotEmpty() -> Kind.ALL
            else -> null
        }
    }

    private fun phraseIn(words: List<String>, phrase: String): Boolean =
        (" " + words.joinToString(" ") + " ").contains(" $phrase ")

    /**
     * Calendario piu' scadenze delle circolari, senza doppioni e in ordine di data e ora.
     *
     * Il doppione tipico e' la scadenza di una circolare aggiunta al calendario: stesso giorno,
     * titolo simile. Resta la voce del calendario, che si porta dietro il numero di circolare.
     */
    internal fun collectItems(knowledge: AssistantKnowledge): List<Item> {
        val myId = knowledge.user?.id
        val calendar = knowledge.calendarEvents
            .filter { event ->
                val visible = event.visibleToUserIds
                event.isForAll || visible.isNullOrEmpty() || myId == null || myId in visible
            }
            .map { Item(it.date.take(10), it.time, it.title, it.category, null, fromCalendar = true) }

        val fromCirculars = knowledge.classifications.values
            .filter { !it.isFallback }
            .flatMap { analysis ->
                analysis.detectedDeadlines.map { deadline ->
                    Item(
                        date = deadline.dueDate.take(10),
                        time = deadline.time,
                        title = deadline.title,
                        category = categoryOf(deadline.category),
                        circularNumber = analysis.circularNumber,
                        fromCalendar = false
                    )
                }
            }

        val merged = mutableListOf<Item>()
        (calendar + fromCirculars).filter { parseIsoDate(it.date) != null }.forEach { item ->
            val index = merged.indexOfFirst { sameThing(it, item) }
            if (index < 0) {
                merged += item
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    time = existing.time?.takeIf { it.isNotBlank() } ?: item.time,
                    circularNumber = existing.circularNumber ?: item.circularNumber,
                    category = existing.category ?: item.category
                )
            }
        }
        return merged.sortedWith(compareBy({ it.date }, { it.time?.let(::parseTimeToMinutes) ?: -1 }))
    }

    private fun sameThing(a: Item, b: Item): Boolean {
        if (a.date != b.date) return false
        val x = AssistantContext.normalize(a.title).trim().replace(Regex("\\s+"), " ")
        val y = AssistantContext.normalize(b.title).trim().replace(Regex("\\s+"), " ")
        if (x.isEmpty() || y.isEmpty()) return x == y
        if (x == y || x.contains(y) || y.contains(x)) return true
        val wx = x.split(' ').filter { it.length >= 5 }.toSet()
        val wy = y.split(' ').filter { it.length >= 5 }.toSet()
        // Due parole lunghe in comune, o tutte quelle del titolo piu' corto: "Ponte Immacolata"
        // e "Ponte per l'Immacolata Concezione" sono la stessa cosa, "Verifica di storia" e
        // "Verifica di fisica" no.
        val common = wx.intersect(wy).size
        return common >= 2 || (common >= 1 && common == minOf(wx.size, wy.size))
    }

    private fun matches(item: Item, kind: Kind): Boolean {
        val title = AssistantContext.normalize(item.title)
        fun titleHas(vararg stems: String) = title.split(' ').any { w -> stems.any { w.startsWith(it) } }
        return when (kind) {
            Kind.ALL -> true
            Kind.PAYMENTS -> item.category == CalendarEventCategory.PAGAMENTO ||
                titleHas("pagament", "versament", "quota", "contribut", "erogazion")
            Kind.DEADLINES -> item.category == CalendarEventCategory.PAGAMENTO ||
                titleHas(
                    "scadenz", "termin", "entro", "iscrizion", "adesion", "domand", "consegn",
                    "prenotazion", "autorizzazion", "modul", "pagament", "versament", "quota",
                    "contribut", "erogazion", "candidatur", "presentazion"
                )
            Kind.TESTS -> item.category == CalendarEventCategory.VERIFICA ||
                item.category == CalendarEventCategory.INTERROGAZIONE
            Kind.TRIPS -> item.category == CalendarEventCategory.USCITA_DIDATTICA ||
                titleHas("gita", "gite", "uscit", "viaggi")
        }
    }

    private fun line(item: Item, todayIso: String): String = buildString {
        append("• ").append(AssistantContext.readableDate(item.date, todayIso))
        cleanTime(item.time)?.let { append(", ore ").append(it) }
        append(" — ").append(item.title.trim())
        readable(item.category)?.let { append(" (").append(it).append(")") }
        item.circularNumber?.let { append(" · circolare n. ").append(it) }
    }

    /** "08:00:00", "8:00" o "08:00" diventano tutti "8:00"; un orario illeggibile sparisce. */
    private fun cleanTime(time: String?): String? {
        val minutes = time?.let(::parseTimeToMinutes) ?: return null
        return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }

    private fun categoryOf(raw: String?): CalendarEventCategory? =
        CalendarEventCategory.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

    private fun readable(category: CalendarEventCategory?): String? = when (category) {
        CalendarEventCategory.VERIFICA -> "verifica"
        CalendarEventCategory.INTERROGAZIONE -> "interrogazione"
        CalendarEventCategory.PAGAMENTO -> "pagamento"
        CalendarEventCategory.USCITA_DIDATTICA -> "uscita didattica"
        CalendarEventCategory.AVVISO, CalendarEventCategory.ALTRO, null -> null
    }

    private fun sourcesOf(items: List<Item>, knowledge: AssistantKnowledge): List<AssistantSource> {
        val circulars = items.mapNotNull { it.circularNumber }.distinct().mapNotNull { number ->
            val circular = knowledge.circulars.firstOrNull { it.number == number } ?: return@mapNotNull null
            AssistantSource(
                kind = AssistantSourceKind.CIRCULAR,
                label = "Circolare n. ${circular.number} — ${circular.title}",
                circularNumber = circular.number
            )
        }
        val calendar = if (items.any { it.fromCalendar }) {
            listOf(AssistantSource(AssistantSourceKind.CALENDAR, "Calendario di classe"))
        } else {
            emptyList()
        }
        return (calendar + circulars).take(6)
    }

    /**
     * Le radici delle parole ammesse in una domanda-elenco, oltre alle stopword. Tutto quello
     * che non comincia con una di queste e' un argomento, e la domanda va al modello.
     */
    private val AGENDA_WORDS = listOf(
        // cosa
        "scadenz", "scade", "termin", "consegn", "pagament", "pagare", "versament", "quote",
        "verific", "interrog", "gita", "gite", "uscit", "impegn", "evento", "eventi",
        "calendario", "agenda", "programma", "succede", "attivita",
        // quando
        "arrivo", "prossim", "imminent", "vicin", "settiman", "oggi", "domani", "dopodomani",
        "weekend", "week", "end", "fine", "mese", "corrente", "scorsa", "successiv", "dopo",
        "previst", "futur",
        // come lo si chiede
        "elenc", "mostr", "quali", "ricord", "segnal", "urgent", "nessun", "qualche",
        "altre", "altri", "abbiamo", "avete", "hanno", "classe", "scuola", "tutt", "tipo"
    )
}
