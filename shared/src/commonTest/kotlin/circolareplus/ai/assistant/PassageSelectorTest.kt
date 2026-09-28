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

    // Il caso visto in app con "Sportelli di scienze, quando?": "sportelli" compare ovunque, e
    // l'unico blocco con entrambe le parole e' quello che dice cosa NON c'e'. L'orario di scienze
    // sta in una tabella piu' avanti, dove la parola "sportello" non c'e'.
    private val sportelliCircular = buildString {
        appendLine("Oggetto: sportelli didattici permanenti 2026-27.")
        appendLine("Non sono previsti sportelli di discipline sportive, scienze motorie, diritto ed economia dello sport.")
        repeat(40) { appendLine("Lo sportello $it si prenota dal registro elettronico entro le 12 del giorno prima.") }
        repeat(20) { appendLine("Riga $it di note generali sulla frequenza e sulle assenze.") }
        appendLine("Tabella orari: Scienze naturali, prof. Rossi, martedi' dalle 14:30 alle 15:30, aula 21.")
        repeat(20) { appendLine("Riga finale $it con riferimenti normativi e firme.") }
    }

    @Test
    fun laParolaRaraContaPiuDiQuellaOvunque() {
        val selected = PassageSelector.select(sportelliCircular, "Sportelli di scienze, quando?", 2_000)
        assertTrue(selected.length <= 2_000)
        assertTrue("martedi' dalle 14:30" in selected, selected)
    }

    @Test
    fun leRigheLunghissimeSiSpezzano() {
        val chunks = PassageSelector.chunk("x".repeat(2_000))
        assertTrue(chunks.all { it.length <= 450 })
        assertFalse(chunks.isEmpty())
    }
}
