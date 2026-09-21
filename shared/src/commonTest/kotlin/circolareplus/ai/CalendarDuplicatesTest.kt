package circolareplus.ai

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.ExtractedDeadline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarDuplicatesTest {

    private fun event(id: String, title: String, date: String) =
        CalendarEvent(id, title, date, null, CalendarEventCategory.AVVISO)

    private fun deadline(title: String, date: String) =
        ExtractedDeadline(title = title, dueDate = date, time = null, category = "AVVISO")

    @Test
    fun stessoGiornoEStessoTitoloEDoppione() {
        val events = listOf(event("e1", "Iscrizione sportelli didattici", "2026-09-23"))
        assertEquals("e1", CalendarDuplicates.findExisting(deadline("Iscrizione sportelli didattici", "2026-09-23"), events)?.id)
    }

    @Test
    fun titoloContenutoOParolaSignificativaInComune() {
        val events = listOf(event("e1", "Festa di sport", "2026-09-13"))
        assertEquals("e1", CalendarDuplicates.findExisting(deadline("Festa di sport e attivita connesse", "2026-09-13"), events)?.id)
        assertEquals("e1", CalendarDuplicates.findExisting(deadline("Adesioni Festa sportiva", "2026-09-13"), events)?.id)
    }

    @Test
    fun oraNellaDataDelloEventoNonImpedisceIlConfronto() {
        val events = listOf(event("e1", "Festa di sport", "2026-09-13T00:00:00Z"))
        assertEquals("e1", CalendarDuplicates.findExisting(deadline("Festa di sport", "2026-09-13"), events)?.id)
    }

    @Test
    fun giornoDiversoNonEDoppione() {
        val events = listOf(event("e1", "Festa di sport", "2026-09-14"))
        assertNull(CalendarDuplicates.findExisting(deadline("Festa di sport", "2026-09-13"), events))
    }

    @Test
    fun stessoGiornoMaCosaDiversaNonEDoppione() {
        val events = listOf(event("e1", "Verifica di matematica", "2026-09-23"))
        assertNull(CalendarDuplicates.findExisting(deadline("Pagamento gita", "2026-09-23"), events))
    }

    @Test
    fun calendarioVuotoNonHaDoppioni() {
        assertNull(CalendarDuplicates.findExisting(deadline("Festa di sport", "2026-09-13"), emptyList()))
    }
}
