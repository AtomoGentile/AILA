package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Il prompt di classificazione e la lettura della risposta, in un posto solo.
 *
 * [ClientSideAiClassifier] ha il suo prompt scritto in linea perché deve anche negoziare il
 * modello con Google; qui c'è la versione per i modelli locali, che sono molto più piccoli e
 * hanno bisogno di istruzioni più corte e più rigide — un modello da 0.6B che riceve il prompt
 * lungo di Gemini si mette a scrivere prosa invece di JSON.
 */
internal object CircularClassificationPrompt {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Quanti caratteri di PDF passare al modello.
     *
     * Era stato dimezzato a 1.800 per il tempo di prefill su CPU, ma a quella soglia il modello
     * vede solo l'incipit della circolare: tabelle, elenchi di destinatari e scadenze verso la
     * fine della pagina restavano fuori, ed e' la causa piu' diretta di riassunti che omettono
     * date o nomi (non li hanno mai letti). Portato poi a 3.200 come compromesso col tempo di
     * prefill su CPU — ma da quando il testo passato qui include anche gli allegati PDF (vedi
     * [truncatePdfTextForAi], che divide questo budget fra documento principale e allegati), a
     * 3.200 caratteri in due-tre pezzi ne restava troppo poco per essere utile a nessuno dei due.
     * 6.000 lascia margine reale a un allegato breve senza un prefill così lento da rendere
     * l'attesa sul telefono fastidiosa.
     */
    const val MAX_PDF_CHARS = 6_000

    const val SYSTEM_PROMPT =
        "Sei un assistente scolastico. Rispondi SEMPRE e SOLO con un oggetto JSON valido, " +
            "senza commenti, senza markdown, senza testo prima o dopo."

    /**
     * Le categorie che il backend accetta davvero per un evento di calendario.
     *
     * Vanno tenute allineate a `CalendarEventCategory`: un evento con una categoria inventata dal
     * modello verrebbe rifiutato dal server, e la parte agentica fallirebbe in silenzio proprio
     * quando ha capito bene la circolare.
     */
    private const val CALENDAR_CATEGORIES =
        "VERIFICA|INTERROGAZIONE|PAGAMENTO|USCITA_DIDATTICA|AVVISO|ALTRO"

    /**
     * [askForCalendarActions] a `false` toglie dal prompt la parte sulle azioni.
     *
     * Serve per i modelli non addestrati al tool calling: chiedere anche le azioni li manda fuori
     * strada e finiscono per rovinare pure il riassunto, che invece riuscirebbero a fare.
     */
    fun buildUserPrompt(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String,
        askForCalendarActions: Boolean = true
    ): String {
        val trimmed = truncatePdfTextForAi(pdfText, MAX_PDF_CHARS)
        val actionsField = if (askForCalendarActions) {
            ",\"deadlines\":[{\"title\":\"\",\"dueDate\":\"YYYY-MM-DD\",\"time\":null,\"category\":\"$CALENDAR_CATEGORIES\"}]"
        } else {
            ""
        }
        val actionRules = if (askForCalendarActions) {
            """
            - In "deadlines" metti SOLO date che lo studente deve segnare in agenda: consegne,
              pagamenti, adesioni entro una data, uscite, incontri a cui deve presentarsi.
              Ogni voce diventera' un evento nel suo calendario, quindi non inventare nulla:
              se una data non c'e' scritta nel testo, non metterla.
            - Non mettere in "deadlines" la data di pubblicazione della circolare.
            - "dueDate" sempre in formato YYYY-MM-DD; "time" in HH:MM oppure null.
            - "category" deve essere uno di: VERIFICA (test, scritti), INTERROGAZIONE (interrogazioni),
              PAGAMENTO (pagamenti, versamenti), USCITA_DIDATTICA (gite, uscite), AVVISO (avvisi generici),
              ALTRO (se non rientra in nessun'altro).
            - Lascia "deadlines" vuoto se non c'e' nessuna data da segnare.
            """.trimIndent()
        } else {
            ""
        }

        return """
            Studente: $studentContext

            CIRCOLARE N. $circularNumber: $circularTitle
            TESTO:
            $trimmed

            Rispondi con questo JSON:
            {"badge":"RELEVANT|POTENTIAL|NOT_RELEVANT","summary":"5-6 righe in italiano"$actionsField}

            Regole:
            - RELEVANT: obblighi, uscite o pagamenti per la sua classe o per tutti gli studenti.
            - POTENTIAL: attivita' facoltative, corsi, gare, borse di studio, open day.
            - NOT_RELEVANT: riservata ad altre classi, ai docenti o al personale ATA.
            - "summary" deve avere 5-6 righe, non una frase sola. Deve riportare, se presenti nel
              testo: chi e' il destinatario esatto, tutte le date citate (con giorno/mese), nomi
              di persone/enti coinvolti (relatori, associazioni, uffici), e l'obiettivo concreto
              della circolare (cosa deve fare lo studente, entro quando, con quali modalita').
              Non riassumere in modo generico se il testo contiene questi dettagli: riportali.
            $actionRules
        """.trimIndent()
    }

    /**
     * Ripulisce l'output di un modello piccolo prima di provare a leggerlo come JSON.
     *
     * I modelli locali sporcano quasi sempre la risposta: la incapsulano in un blocco ```json,
     * la fanno precedere da "Ecco il risultato:", lasciano virgolette non scappate quando citano
     * per esteso un nome fra virgolette del documento originale, oppure — le varianti "thinking"
     * di Qwen — antepongono un blocco `<think>…</think>`. Nessuna di queste cose è un errore del
     * modello, quindi si ripara/taglia invece di dichiarare fallita la classificazione. Vedi
     * [ModelJsonExtractor] per i dettagli (condiviso con [EventGenerationPrompt], stesso bug).
     */
    fun extractJsonObject(raw: String): String? = ModelJsonExtractor.extractJsonObject(raw)

    private val VALID_CATEGORIES = CALENDAR_CATEGORIES.split("|").toSet()

    /** "2026-10-20" e nient'altro. */
    private fun isIsoDate(value: String): Boolean =
        value.length == 10 &&
            value[4] == '-' && value[7] == '-' &&
            value.filterIndexed { i, _ -> i != 4 && i != 7 }.all { it.isDigit() } &&
            value.substring(5, 7).toInt() in 1..12 &&
            value.substring(8, 10).toInt() in 1..31

    /** "08:30" e nient'altro. */
    private fun isClockTime(value: String): Boolean =
        value.length == 5 && value[2] == ':' &&
            value.substring(0, 2).toIntOrNull()?.let { it in 0..23 } == true &&
            value.substring(3, 5).toIntOrNull()?.let { it in 0..59 } == true

    /** Legge badge, riassunto e azioni dal JSON del modello. `null` se il JSON non è leggibile. */
    fun parse(circularNumber: Int, jsonText: String): CircularAiClassification? {
        val obj = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            return null
        }

        // Un valore di "badge" che non e' uno dei tre validi (il modello scrive "ALTA", "SI",
        // un booleano...) non e' un dettaglio da correggere in silenzio con un default: se il
        // modello ha detto una cosa e qui si fa finta che abbia detto POTENTIAL, l'utente vede una
        // classificazione che sembra vera ma non corrisponde a niente che l'AI abbia davvero
        // deciso. Meglio dichiarare il parsing fallito e lasciare che il chiamante mostri il
        // fallback con il motivo, invece di una classificazione silenziosamente inventata.
        val rawBadge = obj["badge"]?.jsonPrimitive?.contentOrNull?.uppercase()
        val badge = rawBadge?.let { value ->
            CircularRelevanceBadge.values().firstOrNull { it.name == value }
        } ?: return null

        val summary = obj["summary"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null

        // Le voci malformate si scartano invece di passarle avanti. Non e' pignoleria: da qui
        // escono eventi veri sul calendario di classe, e un modello piccolo ogni tanto scrive
        // "dueDate": "entro venerdi" o si inventa una categoria. Meglio una scadenza in meno che
        // una riga incomprensibile in calendario, o una POST che il server rifiuta.
        val deadlines = obj["deadlines"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val dueDate = item["dueDate"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { isIsoDate(it) } ?: return@mapNotNull null
            ExtractedDeadline(
                title = title.take(120),
                dueDate = dueDate,
                time = item["time"]?.jsonPrimitive?.contentOrNull?.takeIf { isClockTime(it) },
                category = item["category"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    ?.takeIf { it in VALID_CATEGORIES } ?: "ALTRO"
            )
        }

        return CircularAiClassification(
            circularNumber = circularNumber,
            badge = badge,
            personalSummary = summary,
            detectedDeadlines = deadlines
        )
    }
}
