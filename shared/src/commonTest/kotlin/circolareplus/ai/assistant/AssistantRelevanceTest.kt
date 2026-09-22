package circolareplus.ai.assistant

import circolareplus.domain.model.Circular
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantRelevanceTest {

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-22",
        user = null,
        profile = null,
        circulars = listOf(
            Circular(214, "Iscrizioni ai corsi pomeridiani", "2026-06-01", "k214"),
            Circular(9, "Assemblea di istituto", "2026-09-20", "k9"),
            Circular(8, "Sportelli didattici di materie scientifiche", "2026-09-18", "k8"),
            Circular(7, "Uscita didattica al museo", "2026-09-15", "k7")
        )
    )

    @Test
    fun unSalutoNonHaCircolariNeFonti() {
        assertTrue(AssistantContext.isSmallTalk(knowledge, "Ciao"))
        assertTrue(AssistantContext.isSmallTalk(knowledge, "grazie!"))
        assertEquals(emptyList(), AssistantContext.mostRelevantCirculars(knowledge, "Ciao", 2))
    }

    @Test
    fun laRadiceTrovaParoleDellaStessaFamiglia() {
        // "scienze" nel titolo e' "scientifiche", "sportelli" e' al plurale in entrambi.
        assertEquals(
            listOf(8),
            AssistantContext.mostRelevantCirculars(knowledge, "che giorni trovo gli sportelli di scienze?", 2)
        )
        assertFalse(AssistantContext.isSmallTalk(knowledge, "che giorni trovo gli sportelli di scienze?"))
    }

    @Test
    fun ilNumeroCitatoVinceSulResto() {
        assertEquals(listOf(9), AssistantContext.mostRelevantCirculars(knowledge, "cosa dice la circolare 9?", 2))
    }

    @Test
    fun unaDomandaSuUnPeriodoNonLeggePdf() {
        assertEquals(
            emptyList(),
            AssistantContext.mostRelevantCirculars(knowledge, "cosa devo fare questa settimana?", 2)
        )
    }

    @Test
    fun alMassimoDueCircolari() {
        val found = AssistantContext.mostRelevantCirculars(knowledge, "uscita didattica, sportelli e assemblea", 2)
        assertEquals(2, found.size)
    }
}
