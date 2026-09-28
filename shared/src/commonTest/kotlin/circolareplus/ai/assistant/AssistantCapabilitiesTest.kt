package circolareplus.ai.assistant

import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantCapabilitiesTest {

    private fun knowledge(role: UserRole) = AssistantKnowledge(
        todayIso = "2026-09-28",
        user = User(id = "u1", firstName = "Simone", lastName = "B", username = "simone", role = role),
        profile = null
    )

    @Test
    fun sondaggioStudenteRimandaAlRappresentante() {
        // La domanda vista in app, a cui Gemini Nano rispondeva "Non risulta".
        val reply = assertNotNull(AssistantCapabilities.answer(knowledge(UserRole.STUDENT), "puoi creare un sondaggio?"))
        assertTrue("Rappresentante" in reply.text, reply.text)
        assertTrue("Sondaggi" in reply.text, reply.text)
    }

    @Test
    fun sondaggioRappresentanteIndicaIlPulsante() {
        val reply = assertNotNull(AssistantCapabilities.answer(knowledge(UserRole.REPRESENTATIVE), "mi fai un sondaggio?"))
        assertTrue("Nuovo sondaggio" in reply.text, reply.text)
    }

    @Test
    fun eventoEPropostaDiconoDoveSiFa() {
        val event = assertNotNull(AssistantCapabilities.answer(knowledge(UserRole.STUDENT), "come aggiungo un evento?"))
        assertTrue("Aggiungi evento" in event.text, event.text)
        val proposal = assertNotNull(AssistantCapabilities.answer(knowledge(UserRole.STUDENT), "puoi creare una proposta in bacheca?"))
        assertTrue("Bacheca" in proposal.text, proposal.text)
    }

    @Test
    fun leAltreDomandeRestanoAlModello() {
        val k = knowledge(UserRole.STUDENT)
        assertNull(AssistantCapabilities.answer(k, "cosa devo fare per l'evento di sabato?"))
        assertNull(AssistantCapabilities.answer(k, "c'e' un nuovo sondaggio?"))
        assertNull(AssistantCapabilities.answer(k, "come faccio a votare il sondaggio?"))
        assertNull(AssistantCapabilities.answer(k, "crea un riassunto della circolare 8"))
    }
}
