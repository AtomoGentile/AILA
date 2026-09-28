package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantTextSearchTest {

    private val texts = mapOf(
        301 to "Attivita' pomeridiane a.s. 2026/27. Il corso di teatro inizia lunedi' 12 ottobre alle 14:30 in aula magna.",
        302 to "Corso di aggiornamento per i docenti sulla sicurezza.",
        303 to "Sciopero del personale. Gli studenti entreranno alla seconda ora.",
        304 to "Corso di recupero di matematica per le classi prime."
    )

    @Test
    fun ilCorsoSiTrovaNelTestoAncheSeNonENelTitolo() {
        val ranked = AssistantContext.rankByText(texts, "Quando inizia il corso di teatro?")
        assertEquals(301, ranked.first())
    }

    @Test
    fun leParoleComuniDaSoleNonBastano() {
        // "corso" sta in tre testi su quattro: da solo non indica nessuna circolare.
        assertTrue(AssistantContext.rankByText(texts, "Che corsi ci sono?").isEmpty())
    }

    @Test
    fun domandeGeneraliNonTrovanoNiente() {
        assertTrue(AssistantContext.rankByText(texts, "Spiegami la fotosintesi").isEmpty())
    }

    @Test
    fun cacheTieneLUltimoTesto() {
        CircularTextCache.put(9001, "a")
        CircularTextCache.put(9001, "b")
        assertEquals("b", CircularTextCache.get(9001))
        CircularTextCache.put(9002, "   ")
        assertNull(CircularTextCache.get(9002))
    }
}
