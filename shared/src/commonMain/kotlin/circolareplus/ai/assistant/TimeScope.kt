package circolareplus.ai.assistant

import circolareplus.util.CivilDate
import circolareplus.util.daysInMonth
import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.nextMonth
import circolareplus.util.parseIsoDate
import circolareplus.util.plusDays
import circolareplus.util.weekdayOf

/**
 * L'intervallo di tempo a cui si riferisce una domanda ("questa settimana", "domani"...), con gli
 * estremi inclusi in formato AAAA-MM-GG.
 *
 * Esiste perche' un modello piccolo, a cui si da il calendario di tutto l'anno e si chiede "cosa
 * devo fare questa settimana", risponde elencando eventi di dicembre e di giugno: sa che giorno e'
 * oggi, ma non applica il limite. Il filtro lo fa il codice, che sulle date non sbaglia, e al
 * modello arriva solo quello che e' dentro l'intervallo.
 */
internal data class TimeScope(val from: String, val to: String, val label: String) {

    /** `true` se la data ISO (anche con l'ora dopo la data) cade nell'intervallo. */
    fun contains(isoDate: String): Boolean = isoDate.take(10) in from..to

    /**
     * Frase per il modello: etichetta ed estremi con giorno della settimana, senza AAAA-MM-GG —
     * gli estremi finiscono spesso nella prima riga della risposta, e il modello li ricopiava
     * storpiati ("dal lunedi' 219 settembre").
     */
    fun describe(): String {
        val first = formatItalianDateWithWeekday(from)
        return if (from == to) {
            "$label ($first)"
        } else {
            "$label (da $first a ${formatItalianDateWithWeekday(to)})"
        }
    }
}

internal object TimeScopeParser {

    /**
     * L'intervallo indicato dalla domanda, o `null` se non ne indica nessuno.
     *
     * Con piu' indicazioni ("oggi e domani", "questa settimana e la prossima") si prende
     * l'intervallo che le copre tutte. Le espressioni sono cercate a parole intere sul testo
     * normalizzato: "dopodomani" non deve far scattare anche "domani".
     */
    fun parse(question: String, todayIso: String): TimeScope? {
        val today = parseIsoDate(todayIso) ?: return null
        val words = " " + AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
            .joinToString(" ") + " "

        fun has(phrase: String) = words.contains(" $phrase ")

        val found = mutableListOf<TimeScope>()
        fun add(from: CivilDate, to: CivilDate, label: String) {
            found += TimeScope(from.toIso(), to.toIso(), label)
        }

        val monday = today.plusDays(-weekdayOf(today))

        if (has("dopodomani")) today.plusDays(2).let { add(it, it, "dopodomani") }
        if (has("domani")) today.plusDays(1).let { add(it, it, "domani") }
        if (has("oggi")) add(today, today, "oggi")
        if (has("prossimi giorni") || has("prossimi giorno")) add(today, today.plusDays(6), "nei prossimi giorni")

        if (has("weekend") || has("week end") || has("fine settimana")) {
            // Sabato e domenica di questa settimana; se oggi e' gia' il fine settimana, quello in corso.
            add(monday.plusDays(5), monday.plusDays(6), "il fine settimana")
        }

        // "settimana" e "prossima"/"scorsa" anche non adiacenti: "questa settimana e la prossima"
        // sottintende la seconda "settimana".
        val nextWeek = has("prossima settimana") || has("settimana prossima") ||
            has("settimana successiva") || has("settimana dopo") ||
            (has("settimana") && has("prossima"))
        val lastWeek = has("scorsa settimana") || has("settimana scorsa") ||
            (has("settimana") && has("scorsa"))
        val thisWeek = has("questa settimana") || has("settimana corrente") || has("in settimana")
        if (nextWeek) add(monday.plusDays(7), monday.plusDays(13), "la prossima settimana")
        if (lastWeek) add(monday.plusDays(-7), monday.plusDays(-1), "la settimana scorsa")
        if (thisWeek) add(monday, monday.plusDays(6), "questa settimana")

        // "tra due settimane", "fra 3 giorni": la settimana (lunedi'-domenica) che cade N
        // settimane dopo questa, o il giorno N giorni dopo oggi. Prima non si riconoscevano, e
        // "a che evento devo partecipare tra due settimane?" diventava una ricerca per parole
        // ("evento", "due") che pescava una circolare a caso.
        Regex("""\b(?:tra|fra)\s+(\w+)\s+(settimane|settimana|giorni|giorno)\b""")
            .findAll(words)
            .forEach { match ->
                val count = numberWord(match.groupValues[1]) ?: return@forEach
                if (match.groupValues[2].startsWith("settiman")) {
                    val start = monday.plusDays(7 * count)
                    add(start, start.plusDays(6), "tra ${match.groupValues[1]} ${match.groupValues[2]}")
                } else {
                    today.plusDays(count).let { add(it, it, "tra ${match.groupValues[1]} ${match.groupValues[2]}") }
                }
            }

        if (has("questo mese") || has("mese corrente")) {
            add(
                CivilDate(today.year, today.month, 1),
                CivilDate(today.year, today.month, daysInMonth(today.year, today.month)),
                "questo mese"
            )
        }
        if (has("prossimo mese") || has("mese prossimo")) {
            val (year, month) = nextMonth(today.year, today.month)
            add(CivilDate(year, month, 1), CivilDate(year, month, daysInMonth(year, month)), "il mese prossimo")
        }

        if (found.isEmpty()) return null
        return TimeScope(
            from = found.minOf { it.from },
            to = found.maxOf { it.to },
            label = found.joinToString(" + ") { it.label }
        )
    }

    /** "due" -> 2, "3" -> 3, "una" -> 1; `null` per tutto il resto. */
    private fun numberWord(word: String): Int? = word.toIntOrNull()?.takeIf { it in 1..52 } ?: when (word) {
        "un", "una", "uno" -> 1
        "due" -> 2
        "tre" -> 3
        "quattro" -> 4
        "cinque" -> 5
        "sei" -> 6
        "sette" -> 7
        "otto" -> 8
        "nove" -> 9
        "dieci" -> 10
        else -> null
    }
}
