package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantSearchesParseTest {

    @Test
    fun leParoleDaCercareSiLeggonoDalJson() {
        val parsed = AssistantPrompt.parse("""{"answer":"Quota da versare.","sources":[],"needsCircularText":[],"searches":["ICDL","gita Firenze"]}""")
        assertEquals(listOf("ICDL", "gita Firenze"), parsed.searches)
    }

    @Test
    fun laListaDiRicercheHaUnTetto() {
        val parsed = AssistantPrompt.parse("""{"answer":"Ok.","sources":[],"needsCircularText":[],"searches":["alfa","beta","gamma"]}""")
        assertEquals(2, parsed.searches.size)
    }

    @Test
    fun terminiTroppoCortiOVuotiSiScartano() {
        val parsed = AssistantPrompt.parse("""{"answer":"Ok.","sources":[],"needsCircularText":[],"searches":["di","","ICDL"]}""")
        assertEquals(listOf("ICDL"), parsed.searches)
    }

    @Test
    fun senzaCampoSearchesLaListaEVuota() {
        val parsed = AssistantPrompt.parse("""{"answer":"Ciao.","sources":[],"needsCircularText":[]}""")
        assertEquals(emptyList(), parsed.searches)
    }
}
