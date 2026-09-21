package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTextCleaningTest {

    @Test
    fun toglieControlloFormatoEUsoPrivato() {
        val dirty = "Festa\u0000 di\u00AD sport\uF0B7 \u200B2026"
        assertEquals("Festa di sport 2026", cleanPdfTextForAi(dirty))
    }

    @Test
    fun riduceSpaziERigheVuote() {
        assertEquals(
            "Riga uno\n\nRiga due",
            cleanPdfTextForAi("  Riga   uno \n\n\n\n  Riga\tdue  ")
        )
    }

    @Test
    fun tieneLettereAccentateEPunteggiatura() {
        val text = "Il 13 settembre, è previsto un talk: “LA FORZA DELL’INATTESO”."
        assertEquals(text, cleanPdfTextForAi(text))
    }

    @Test
    fun ilMarcatoreDegliAllegatiSopravvive() {
        val text = "Corpo del documento" + ATTACHMENT_TEXT_MARKER + "modulo.pdf ---\n\nTesto allegato"
        assertTrue(cleanPdfTextForAi(text).contains(ATTACHMENT_TEXT_MARKER))
    }
}
