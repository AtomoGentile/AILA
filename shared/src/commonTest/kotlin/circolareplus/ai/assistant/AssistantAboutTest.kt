package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantAboutTest {

    private fun reply(text: String, label: String? = null) =
        AssistantMessage(id = "r", author = AssistantAuthor.ASSISTANT, text = text, modelLabel = label)

    @Test
    fun cosEAilaDaLaDescrizioneVera() {
        // La domanda vista in app: Gemini Nano rispondeva "AILA e' l'app scolastica."
        val reply = assertNotNull(AssistantAbout.answer("Che cos'è AILA?", emptyList()))
        assertTrue("Circolari" in reply.text && "Calendario" in reply.text, reply.text)
    }

    @Test
    fun ilSeguitoECosaFaNonRipeteLaStessaFrase() {
        val history = listOf(reply("AILA è l'app scolastica."))
        val reply = assertNotNull(AssistantAbout.answer("E cosa fa?", history))
        assertTrue("Mappa posti" in reply.text, reply.text)
    }

    @Test
    fun cosaSaiFareElencaEsempi() {
        val reply = assertNotNull(AssistantAbout.answer("cosa sai fare?", emptyList()))
        assertTrue("settimana" in reply.text, reply.text)
    }

    @Test
    fun domandeSuAltroRestanoAlModello() {
        assertNull(AssistantAbout.answer("cosa fa il Rappresentante di classe?", emptyList()))
        assertNull(AssistantAbout.answer("cosa fa?", emptyList()))
        assertNull(AssistantAbout.answer("che cos'è la fotosintesi?", emptyList()))
        assertNull(AssistantAbout.answer("ciao", emptyList()))
    }
}
