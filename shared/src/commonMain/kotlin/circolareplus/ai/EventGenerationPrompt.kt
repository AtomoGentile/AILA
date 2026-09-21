package circolareplus.ai

import circolareplus.util.ITALIAN_WEEKDAYS
import circolareplus.util.today
import circolareplus.util.weekdayOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bozza di evento ricavata dal testo libero dell'utente: e' il tipo di ritorno di
 * [AiClassifier.parseEventPrompt], quindi **pubblico e di primo livello**.
 *
 * Prima stava dentro [EventGenerationPrompt], che e' `internal` perche' contiene i prompt: una
 * funzione pubblica dell'interfaccia finiva cosi' per esporre un tipo interno, ed era l'errore
 * "'public' function exposes its 'internal' return type" ripetuto su tutti e quattro i
 * classificatori. La divisione ricalca quella gia' usata per le circolari: il prompt resta
 * interno, il risultato e' un tipo di dominio a se'.
 */
data class EventDraft(
    val title: String,
    val subject: String,
    val category: String,
    val dateIso: String?,
    val timeHm: String?,
    val notes: String?
)

/**
 * Prompt per la generazione di eventi calendario dal testo libero scritto dall'utente.
 * Ritorna titolo, materia, categoria, data e ora parsati dal testo in italiano.
 */
internal object EventGenerationPrompt {

    private val json = Json { ignoreUnknownKeys = true }

    const val SYSTEM_PROMPT =
        "Sei un assistente scolastico. Rispondi SEMPRE e SOLO con un oggetto JSON valido, " +
            "senza commenti, senza markdown, senza testo prima o dopo."

    private const val CALENDAR_CATEGORIES =
        "VERIFICA|INTERROGAZIONE|PAGAMENTO|USCITA_DIDATTICA|AVVISO|ALTRO"

    fun buildUserPrompt(userPrompt: String): String {
        // Senza dire al modello che giorno è oggi, "domani" o "lunedì" sono indovinati alla
        // cieca: e' la causa piu' diretta delle date (e degli orari dedotti da esse, es. "stasera")
        // sbagliate viste con l'AI locale.
        val civilToday = today()
        val todayIso = civilToday.toIso()
        val todayWeekday = ITALIAN_WEEKDAYS.getOrElse(weekdayOf(civilToday)) { "" }

        return """
            Oggi è $todayWeekday $todayIso.

            Estrai da questo testo gli elementi per creare un evento di calendario:
            "$userPrompt"

            Rispondi con questo JSON:
            {"title":"","subject":"","category":"$CALENDAR_CATEGORIES","dateIso":"YYYY-MM-DD","timeHm":"HH:MM","notes":""}

            Regole:
            - "title": titolo breve dell'evento (es. "Verifica di matematica"). Obbligatorio.
            - "subject": la materia/argomento (es. "Matematica"). Può essere vuoto se non specificato.
            - "category": deve essere uno di: VERIFICA (test, scritti), INTERROGAZIONE (interrogazioni),
              PAGAMENTO (pagamenti, versamenti), USCITA_DIDATTICA (gite, uscite), AVVISO (avvisi generici),
              ALTRO (se non rientra in nessun'altro). Obbligatorio.
            - "dateIso": la data in formato YYYY-MM-DD se presente o deducibile (es. "domani", "lunedì
              prossimo"), calcolata rispetto a oggi ($todayIso). Altrimenti null.
            - "timeHm": l'ora in formato HH:MM 24 ore se presente nel testo (es. "alle 9" -> "09:00",
              "alle 14:30" -> "14:30"). Altrimenti null. Non inventare un orario se il testo non lo dice.
            - "notes": eventuali dettagli aggiuntivi del testo che non rientrano in titolo/materia/data/ora
              (es. luogo, cosa portare, argomenti specifici, chi è coinvolto). Stringa vuota se non c'è
              nient'altro da riportare.
            - Se la data non è specificata nel testo, usa la data di oggi ($todayIso).
            - Non inventare dati che non sono nel testo.
        """.trimIndent()
    }

    /** Ripulisce/ripara l'output prima di leggerlo come JSON. Vedi [ModelJsonExtractor]. */
    fun extractJsonObject(raw: String): String? = ModelJsonExtractor.extractJsonObject(raw)

    private val VALID_CATEGORIES = CALENDAR_CATEGORIES.split("|").toSet()

    private fun isIsoDate(value: String?): Boolean {
        if (value == null) return false
        return value.length == 10 &&
            value[4] == '-' && value[7] == '-' &&
            value.filterIndexed { i, _ -> i != 4 && i != 7 }.all { it.isDigit() } &&
            value.substring(5, 7).toInt() in 1..12 &&
            value.substring(8, 10).toInt() in 1..31
    }

    private fun isClockTime(value: String?): Boolean {
        if (value == null) return false
        return value.length == 5 && value[2] == ':' &&
            value.substring(0, 2).toIntOrNull()?.let { it in 0..23 } == true &&
            value.substring(3, 5).toIntOrNull()?.let { it in 0..59 } == true
    }

    /** Legge evento dal JSON del modello. `null` se il JSON non è leggibile o invalido. */
    fun parse(jsonText: String): EventDraft? {
        val obj = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            return null
        }

        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null

        val category = obj["category"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?.takeIf { it in VALID_CATEGORIES } ?: return null

        val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: ""
        val dateIso = obj["dateIso"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { isIsoDate(it) }
        val timeHm = obj["timeHm"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { isClockTime(it) }
        val notes = obj["notes"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

        return EventDraft(
            title = title.take(120),
            subject = subject.take(60),
            category = category,
            dateIso = dateIso,
            timeHm = timeHm,
            notes = notes?.take(500)
        )
    }
}
