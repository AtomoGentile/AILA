package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantGreetingTest {

    @Test
    fun salutoNonVaAlModello() {
        for (q in listOf("ciao", "Ciao!", "buongiorno", "ciao AILA", "ehi, come stai?", "Buona sera")) {
            val reply = assertNotNull(AssistantGreeting.answer(q), q)
            assertTrue(reply.text.startsWith("Ciao!"), reply.text)
            assertTrue(reply.sources.isEmpty())
        }
    }

    @Test
    fun ringraziamento() {
        val reply = assertNotNull(AssistantGreeting.answer("ok grazie mille!"))
        assertTrue(reply.text.startsWith("Di niente"))
    }

    @Test
    fun salutoConDomandaResta() {
        assertNull(AssistantGreeting.answer("ciao, cosa ho domani?"))
        assertNull(AssistantGreeting.answer("ciao, che c'è di nuovo?"))
        assertNull(AssistantGreeting.answer("come stai"))
        assertNull(AssistantGreeting.answer("cosa devo fare questa settimana?"))
    }
}
