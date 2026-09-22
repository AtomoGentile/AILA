package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassageSelectorTest {

    // Una circolare lunga: intestazione, tante pagine di contorno, e la tabella che serve in mezzo.
    private val longCircular = buildString {
        appendLine("Oggetto: sportelli didattici a.s. 2026/27. Destinatari: studenti di tutte le classi.")
        repeat(60) { appendLine("Paragrafo $it sulle modalita' generali di prenotazione e sulle regole di comportamento in aula.") }
        appendLine("Sportello di scienze naturali: martedi' e giovedi' dalle 14:00 alle 15:00, aula 12.")
        repeat(60) { appendLine("Paragrafo finale $it con indicazioni amministrative e riferimenti normativi vari.") }
    }

    @Test
    fun unTestoCortoPassaIntero() {
        assertEquals("breve", PassageSelector.select("breve", "scienze", 2_000))
    }

    @Test
    fun ilPassaggioInMezzoArrivaAnchePocoSpazio() {
        val selected = PassageSelector.select(longCircular, "che giorni c'e' lo sportello di scienze?", 2_000)
        assertTrue(selected.length <= 2_000)
        assertTrue("martedi' e giovedi'" in selected, selected)
        // L'intestazione resta: dice a chi e' rivolta la circolare.
        assertTrue(selected.startsWith("Oggetto: sportelli didattici"), selected)
        assertTrue("[...]" in selected)
    }

    @Test
    fun senzaParoleUtiliSiTieneLInizio() {
        val selected = PassageSelector.select(longCircular, "ciao", 500)
        assertEquals(longCircular.take(500), selected)
    }

    @Test
    fun iBlocchiRestanoNellOrdineDelDocumento() {
        val selected = PassageSelector.select(longCircular, "scienze amministrative", 3_000)
        val science = selected.indexOf("Sportello di scienze")
        val admin = selected.indexOf("indicazioni amministrative")
        assertTrue(science >= 0 && admin >= 0)
        assertTrue(science < admin)
    }

    @Test
    fun leRigheLunghissimeSiSpezzano() {
        val chunks = PassageSelector.chunk("x".repeat(2_000))
        assertTrue(chunks.all { it.length <= 450 })
        assertFalse(chunks.isEmpty())
    }
}
