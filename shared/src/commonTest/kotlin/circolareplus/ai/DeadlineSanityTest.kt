package circolareplus.ai

import circolareplus.domain.model.ExtractedDeadline
import circolareplus.util.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadlineSanityTest {

    private val now = CivilDate(2026, 9, 21)

    private fun deadline(date: String) =
        ExtractedDeadline(title = "Iscrizione", dueDate = date, time = null, category = "AVVISO")

    private fun sanitize(text: String, vararg dates: String) =
        DeadlineSanity.sanitize(dates.map { deadline(it) }, text, now).map { it.dueDate }

    @Test
    fun dataNelTestoCoerenteResta() {
        assertEquals(
            listOf("2026-10-20"),
            sanitize("Iscrizioni entro il 20 ottobre presso la segreteria.", "2026-10-20")
        )
    }

    @Test
    fun annoAssurdoDiventaQuelloDellAnnoScolastico() {
        assertEquals(
            listOf("2026-10-20"),
            sanitize("Iscrizioni entro il 20 ottobre.", "2023-10-20")
        )
        // Gennaio-agosto cade nell'anno solare successivo a settembre-dicembre.
        assertEquals(
            listOf("2027-03-12"),
            sanitize("Uscita il 12 marzo.", "2023-03-12")
        )
    }

    @Test
    fun dataChePdfNonContieneVieneScartata() {
        // Mese sfasato: nel testo c'e' ottobre, non novembre.
        assertEquals(emptyList(), sanitize("Iscrizioni entro il 20 ottobre.", "2026-11-20"))
        // Giorno sfasato: il 20 c'e', il 27 no.
        assertEquals(emptyList(), sanitize("Iscrizioni entro il 20 ottobre.", "2026-10-27"))
    }

    @Test
    fun formeNumericheEAbbreviate() {
        assertEquals(listOf("2026-10-20"), sanitize("Scadenza 20/10/2026.", "2026-10-20"))
        assertEquals(listOf("2026-10-20"), sanitize("Scadenza 20.10.26.", "2026-10-20"))
        assertEquals(listOf("2026-10-20"), sanitize("Scadenza 20 ott.", "2026-10-20"))
        assertEquals(listOf("2026-10-20"), sanitize("Scadenza 2026-10-20.", "2026-10-20"))
    }

    @Test
    fun intervalloGiustificaIlGiornoDIniziolontanoDalMese() {
        assertEquals(listOf("2026-10-20"), sanitize("Dal 20 al 24 ottobre.", "2026-10-20"))
    }

    @Test
    fun annoVicinoNonSiTocca() {
        assertEquals(listOf("2025-10-20"), sanitize("Entro il 20 ottobre.", "2025-10-20"))
    }

    @Test
    fun ventinoveFebbraioImpossibileDopoCorrezioneAnnoVieneScartato() {
        // 2024 e' bisestile ma lontano da oggi: diventa 2027, che non lo e'.
        assertTrue(sanitize("Il 29 febbraio.", "2024-02-29").isEmpty())
    }
}
