package circolareplus.ai

import circolareplus.util.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals

class EventDateFutureTest {

    // Martedi' 29 settembre 2026.
    private val today = CivilDate(2026, 9, 29)

    @Test
    fun oggiEFuturoRestanoCosiCome() {
        assertEquals("2026-09-29", futureEventDate("2026-09-29", today))
        assertEquals("2026-10-12", futureEventDate("2026-10-12", today))
    }

    @Test
    fun giornoDellaSettimanaSbagliatoAvanzaAlLaProssimaVolta() {
        // Lunedi' 28, cioe' ieri: il prossimo lunedi' e' il 5 ottobre.
        assertEquals("2026-10-05", futureEventDate("2026-09-28", today))
        // Sabato 19, dieci giorni fa: il prossimo sabato e' il 3 ottobre.
        assertEquals("2026-10-03", futureEventDate("2026-09-19", today))
    }

    @Test
    fun annoSbagliatoDiventaQuelloDiOggi() {
        assertEquals("2026-10-12", futureEventDate("2024-10-12", today))
    }

    @Test
    fun annoSbagliatoConDataGiaPassataQuestAnnoVaAlProssimo() {
        assertEquals("2027-03-05", futureEventDate("2024-03-05", today))
    }

    @Test
    fun ventinoveFebbraioInAnnoNonBisestile() {
        assertEquals("2027-02-28", futureEventDate("2024-02-29", today))
    }

    @Test
    fun formatoNonValidoNonSiTocca() {
        assertEquals("boh", futureEventDate("boh", today))
    }
}
