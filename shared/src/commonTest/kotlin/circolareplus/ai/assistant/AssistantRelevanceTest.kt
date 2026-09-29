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

    private fun msg(author: AssistantAuthor, text: String, sources: List<AssistantSource> = emptyList()) =
        AssistantMessage("id-${text.hashCode()}", author, text, sources)

    private val palestraHistory = listOf(
        msg(AssistantAuthor.USER, "Cosa e' vietato fare in palestra?"),
        msg(
            AssistantAuthor.ASSISTANT,
            "La palestra e' usabile da tre classi.",
            listOf(AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. 9", 9))
        )
    )

    @Test
    fun unSeguitoSenzaArgomentoRestaAgganciatoAllaDomandaPrecedente() {
        val query = AssistantContext.searchQuery("dimmi qualcosa in piu'", palestraHistory)
        assertTrue("palestra" in AssistantContext.tokenize(query))
        assertEquals(setOf(9), AssistantContext.circularNumbersIn(query))
    }

    @Test
    fun ilSeguitoDiApprofondimentoNonPerdeLaCircolare() {
        // Prima "leggi", "intera" e "circolare" contavano come argomento: niente seguito, niente PDF.
        val query = AssistantContext.searchQuery(
            "leggi l'intera circolare e dimmi qualcosa in piu'",
            palestraHistory + msg(AssistantAuthor.USER, "sulla palestra dicevo")
        )
        assertEquals(listOf(9), AssistantContext.mostRelevantCirculars(knowledge, query, 2))
    }

    @Test
    fun unaDomandaConUnArgomentoNonSiMescolaAllaCronologia() {
        val question = "quali sportelli di scienze e matematica ci sono?"
        assertEquals(question, AssistantContext.searchQuery(question, palestraHistory))
    }

    @Test
    fun vietatoTrovaIlDivietoDellaCircolare() {
        val text = (List(30) { "Riga generica numero $it della capienza degli ambienti scolastici." } +
            "E' fatto divieto di consumare cibi e bevande in palestra." +
            List(30) { "Altra riga generica $it sulle aule." }).joinToString("\n")
        val selected = PassageSelector.select(text, "Cosa e' vietato?", 700)
        assertTrue("divieto" in selected)
    }
}
