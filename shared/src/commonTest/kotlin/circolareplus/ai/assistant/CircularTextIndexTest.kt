package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CircularTextIndexTest {

    private val index = CircularTextIndex().apply {
        put(214, "Corso ICDL per le classi terze.\nIscrizioni entro venerdi' presso la segreteria.")
        put(215, "Gita di istruzione a Firenze.\nQuota 45 euro da versare entro il 20 ottobre.")
        put(216, "Sportelli di recupero: scienze il lunedi' e il martedi'.\nIl testo ripete scienze.")
    }

    @Test
    fun laParolaTrovaSoloLaCircolareGiusta() {
        val hits = index.search(listOf("icdl"))
        assertEquals(listOf(214), hits.map { it.number })
    }

    @Test
    fun laRicercaPerPrefissoTrovaLeFormeFlesse() {
        // "sportelli" e "sportello" hanno radici diverse: il prefisso comune le unisce.
        assertEquals(listOf(216), index.search(listOf("sportelli")).map { it.number })
        assertEquals(listOf(216), index.search(listOf("sportello")).map { it.number })
    }

    @Test
    fun paroleCheNonCompaionoNonDanno() {
        assertTrue(index.search(listOf("matematica")).isEmpty())
    }

    @Test
    fun paroleCortissimeNonCercano() {
        assertTrue(index.search(listOf("di", "a")).isEmpty())
    }

    @Test
    fun laCircolareConPiuOccorrenzeEPrimaANeleParitaDiParole() {
        val hits = index.search(listOf("scienze"))
        assertEquals(216, hits.first().number)
        assertTrue(AssistantContext.stemOf("scienze") in hits.first().matchedTerms)
    }

    @Test
    fun ilPassaggioContieneLaParolaCercata() {
        val text = index.passages(214, listOf("icdl"), maxChars = 500)
        assertTrue("ICDL" in text)
    }

    @Test
    fun laSostituzioneDiUnaCircolareToglieLeVecchieParole() {
        index.put(215, "Testo nuovo senza parole di prima.")
        assertTrue(index.search(listOf("firenze")).isEmpty())
        assertEquals(listOf(215), index.search(listOf("nuovo")).map { it.number })
    }
}
