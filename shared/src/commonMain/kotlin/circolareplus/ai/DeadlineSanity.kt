package circolareplus.ai

import circolareplus.domain.model.ExtractedDeadline
import circolareplus.util.CivilDate
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.daysInMonth
import circolareplus.util.parseIsoDate
import circolareplus.util.today
import kotlin.math.abs

/**
 * Controlli deterministici sulle scadenze estratte da un modello locale.
 *
 * Nascono dai modelli piccoli (Phi-4 mini in campo): sbagliano le date in modo che sembrano
 * plausibili — "2023" al posto di "2026", mese o giorno sfasati — e da qui escono eventi veri
 * nel calendario di classe. Un prompt piu' chiaro aiuta ma non garantisce niente, un confronto con
 * il testo del PDF si': una data che nel documento non compare, in nessuna forma, e' inventata.
 */
internal object DeadlineSanity {

    /** Un anno oltre questa distanza da oggi non e' una scadenza: e' un errore di lettura. */
    private const val MAX_YEAR_DISTANCE = 1

    /** Anno in cui e' iniziato l'anno scolastico in corso: da settembre in poi e' quello attuale. */
    fun schoolYearStart(now: CivilDate): Int = if (now.month >= 9) now.year else now.year - 1

    /** Anno da dare a una data senza anno: settembre-dicembre / gennaio-agosto dell'anno scolastico. */
    private fun yearWithinSchoolYear(month: Int, now: CivilDate): Int {
        val start = schoolYearStart(now)
        return if (month >= 9) start else start + 1
    }

    /**
     * Toglie le scadenze che il PDF non giustifica e corregge gli anni assurdi.
     *
     * - giorno e mese devono comparire nel testo (vedi [appearsIn]); altrimenti la voce si scarta:
     *   meglio una scadenza in meno che un evento sbagliato in calendario;
     * - un anno lontano piu' di [MAX_YEAR_DISTANCE] da quello corrente si sostituisce con quello
     *   dell'anno scolastico in corso. Un anno vicino si lascia com'e': una circolare vecchia puo'
     *   legittimamente parlare del passato.
     */
    fun sanitize(
        deadlines: List<ExtractedDeadline>,
        sourceText: String,
        now: CivilDate = today()
    ): List<ExtractedDeadline> {
        val text = sourceText.lowercase()
        return deadlines.mapNotNull { deadline ->
            val date = parseIsoDate(deadline.dueDate) ?: return@mapNotNull null
            if (!appearsIn(date, text)) return@mapNotNull null

            val year = if (abs(date.year - now.year) > MAX_YEAR_DISTANCE) {
                yearWithinSchoolYear(date.month, now)
            } else {
                date.year
            }
            // Cambiare anno puo' rendere impossibile il giorno (29 febbraio).
            if (date.day > daysInMonth(year, date.month)) return@mapNotNull null

            if (year == date.year) {
                deadline
            } else {
                deadline.copy(dueDate = CivilDate(year, date.month, date.day).toIso())
            }
        }
    }

    private val NUMERIC_DATE = Regex("""(\d{1,2})\s*[/.\-]\s*(\d{1,2})""")
    private val NUMBER = Regex("""\d+""")
    private val WORD = Regex("""[a-zàèéìòù]+""")

    /**
     * `true` se [date] e' scritta nel [lowercaseText], in una delle forme in cui compare nelle
     * circolari: "2026-10-20", "20/10", "20.10.2026", oppure il mese per nome ("ottobre" o
     * l'abbreviazione di tre lettere "ott") con il giorno come numero a se' stante, anche lontano:
     * "dal 20 al 24 ottobre" giustifica il 20 pur non scrivendo il mese accanto.
     *
     * La forma per nome e' volutamente larga (qualunque numero uguale al giorno): serve a
     * scartare le date inventate, non a certificare quelle giuste.
     */
    fun appearsIn(date: CivilDate, lowercaseText: String): Boolean {
        if (lowercaseText.contains(date.toIso())) return true

        val numeric = NUMERIC_DATE.findAll(lowercaseText).any {
            it.groupValues[1].toInt() == date.day && it.groupValues[2].toInt() == date.month
        }
        if (numeric) return true

        val monthName = ITALIAN_MONTHS.getOrNull(date.month)?.lowercase() ?: return false
        val monthMentioned = WORD.findAll(lowercaseText).any {
            it.value == monthName || (it.value.length == 3 && monthName.startsWith(it.value))
        }
        if (!monthMentioned) return false
        return NUMBER.findAll(lowercaseText).any { it.value.trimStart('0') == date.day.toString() }
    }
}
