package circolareplus.util

import circolareplus.platform.currentTimeMillis
import circolareplus.platform.localUtcOffsetMillis

/**
 * Aritmetica delle date, senza dipendenze esterne e identica su Android e iOS.
 *
 * Serve al calendario vero (griglia del mese): fino ad ora l'app non sapeva nemmeno quanti giorni
 * ha un mese o su che giorno della settimana cade il primo — la "striscia dei giorni" era una
 * lista fissa da 1 a 15 scollegata dal calendario reale. La conversione da millisecondi usa
 * l'algoritmo civil_from_days di Howard Hinnant, lo stesso già usato in MainAppShell (che ora
 * chiama queste funzioni invece di tenersene una copia privata).
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) {
    /** Formato "AAAA-MM-GG", quello usato dal backend. */
    fun toIso(): String {
        fun pad(n: Int, width: Int) = n.toString().padStart(width, '0')
        return "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}"
    }
}

/** Nomi dei mesi in italiano, indicizzati da 1 a 12. */
val ITALIAN_MONTHS = listOf(
    "", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
)

/** Iniziali dei giorni, da lunedì a domenica (la settimana italiana inizia di lunedì). */
val ITALIAN_WEEKDAY_INITIALS = listOf("L", "M", "M", "G", "V", "S", "D")

/** Nomi per esteso dei giorni, da lunedì a domenica. */
val ITALIAN_WEEKDAYS = listOf(
    "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"
)

/** Da millisecondi epoch (UTC) a data civile. */
fun civilFromEpochMillis(millis: Long): CivilDate {
    val z = millis / 86_400_000L + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return CivilDate(year.toInt(), m.toInt(), d.toInt())
}

/** L'istante corrente spostato sul fuso locale: la parte "data" e' quella di oggi per l'utente. */
private fun localNowMillis(): Long {
    val now = currentTimeMillis()
    return now + localUtcOffsetMillis(now)
}

/** La data di oggi secondo l'orologio e il fuso orario del dispositivo. */
fun today(): CivilDate = civilFromEpochMillis(localNowMillis())

/** Minuti trascorsi dalla mezzanotte locale (0..1439). */
fun nowMinutesOfDay(): Int = ((localNowMillis() % 86_400_000L) / 60_000L).toInt()

/** "HH:MM" (anche con secondi) in minuti dalla mezzanotte; null se il formato non e' quello atteso. */
fun parseTimeToMinutes(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].take(2).toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Da "AAAA-MM-GG" a data civile; null se il formato non è quello atteso. */
fun parseIsoDate(iso: String): CivilDate? {
    val parts = iso.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].take(2).toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return CivilDate(year, month, day)
}

fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

/**
 * Giorno della settimana del primo del mese, 0 = lunedì … 6 = domenica.
 * Algoritmo di Sakamoto: tabella di scarti per mese, nessuna libreria di calendario.
 */
fun firstWeekdayOfMonth(year: Int, month: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    var y = year
    if (month < 3) y -= 1
    // 0 = domenica secondo Sakamoto; qui si ruota per far iniziare la settimana di lunedì.
    val sundayBased = (y + y / 4 - y / 100 + y / 400 + t[month - 1] + 1) % 7
    return (sundayBased + 6) % 7
}

/** Mese precedente, gestendo il cambio d'anno. */
fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 1) (year - 1) to 12 else year to (month - 1)

/** Mese successivo, gestendo il cambio d'anno. */
fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 12) (year + 1) to 1 else year to (month + 1)

/**
 * Giorno della settimana di una data, 0 = lunedì … 6 = domenica. Stesso algoritmo di
 * [firstWeekdayOfMonth], applicato al giorno indicato invece che al primo del mese.
 */
fun weekdayOf(date: CivilDate): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    var y = date.year
    if (date.month < 3) y -= 1
    val sundayBased = (y + y / 4 - y / 100 + y / 400 + t[date.month - 1] + date.day) % 7
    return (sundayBased + 6) % 7
}

/** Nome del giorno della settimana, es. "Giovedì". Stringa vuota se la data non è valida. */
fun weekdayName(iso: String): String {
    val date = parseIsoDate(iso) ?: return ""
    return ITALIAN_WEEKDAYS.getOrElse(weekdayOf(date)) { "" }
}

/**
 * "2026-03-12T18:00:00Z" oppure "2026-03-12 18:00:00" -> "12 marzo".
 * Stringa vuota se la data non è leggibile. Usata per le scadenze dei sondaggi.
 */
fun formatDayMonth(isoDateTime: String): String {
    val datePart = isoDateTime.take(10)
    val date = parseIsoDate(datePart) ?: return ""
    val month = ITALIAN_MONTHS.getOrNull(date.month)?.lowercase() ?: return ""
    return "${date.day} $month"
}
