package circolareplus.ai.assistant

import circolareplus.ai.AiClassifier
import circolareplus.ai.AiPromptBuilder
import circolareplus.ai.AiTextResult
import circolareplus.ai.EventDraft
import circolareplus.ai.openAiCompatibleBody
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantEffortTest {
    private val user = User("u1", "Simone", "Bianchin", "simone")
    private val sampleKnowledge = AssistantKnowledge("2026-11-25", user, null, classLabel = "5A")

    @Test
    fun promptGuidanceChangesAcrossEffortLevels() {
        val fast = AssistantPrompt.build(60_000, sampleKnowledge, emptyList(), "Spiega il secondo principio della termodinamica", emptyMap(), effortLevel = 0)
        val medium = AssistantPrompt.build(60_000, sampleKnowledge, emptyList(), "Spiega il secondo principio della termodinamica", emptyMap(), effortLevel = 1)
        val high = AssistantPrompt.build(60_000, sampleKnowledge, emptyList(), "Spiega il secondo principio della termodinamica", emptyMap(), effortLevel = 2)
        val max = AssistantPrompt.build(60_000, sampleKnowledge, emptyList(), "Spiega il secondo principio della termodinamica", emptyMap(), effortLevel = 3)

        assertTrue(fast.systemPrompt.contains("LIVELLO DI RAGIONAMENTO: RAPIDO"))
        assertFalse(medium.systemPrompt.contains("LIVELLO DI RAGIONAMENTO:"))
        assertTrue(high.systemPrompt.contains("LIVELLO DI RAGIONAMENTO: ALTO"))
        assertTrue(max.systemPrompt.contains("LIVELLO DI RAGIONAMENTO: MASSIMO"))
    }

    @Test
    fun rapidEffortSkipsSecondReviewRoundWhileHighRunsVerification() = runTest {
        var fastCalls = 0
        val fastAssistant = AilaAssistant(
            classifierFactory = {
                object : AiClassifier {
                    override suspend fun classifyCircularText(circularNumber: Int, circularTitle: String, pdfText: String, studentContext: String): CircularAiClassification = error("unused")
                    override suspend fun parseEventPrompt(userPrompt: String): EventDraft = error("unused")
                    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
                        fastCalls++
                        return AiTextResult.Success("""{"answer":"Risposta rapida","sources":[],"needsCircularText":[],"searches":[],"calendarQueries":[],"actionRequest":""}""", "test")
                    }
                    override suspend fun testConfiguration() = "ok"
                }
            },
            downloadPdf = { ByteArray(0) },
            extractPdf = { "" },
            effortLevelProvider = { 0 }
        )
        val fastReply = fastAssistant.ask("Spiega l'entropia in breve", emptyList(), sampleKnowledge)
        assertEquals("Risposta rapida", fastReply.text)
        assertEquals(1, fastCalls, "Su Rapido non deve fare il secondo giro di verifica")

        var highCalls = 0
        val highAssistant = AilaAssistant(
            classifierFactory = {
                object : AiClassifier {
                    override suspend fun classifyCircularText(circularNumber: Int, circularTitle: String, pdfText: String, studentContext: String): CircularAiClassification = error("unused")
                    override suspend fun parseEventPrompt(userPrompt: String): EventDraft = error("unused")
                    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
                        highCalls++
                        return if (highCalls == 1) {
                            AiTextResult.Success("""{"answer":"Prima bozza","sources":[],"needsCircularText":[],"searches":[],"calendarQueries":[],"actionRequest":""}""", "test")
                        } else {
                            AiTextResult.Success("""{"answer":"Risposta verificata e approfondita","verified":true,"sources":[],"needsCircularText":[],"searches":[],"calendarQueries":[],"actionRequest":""}""", "test")
                        }
                    }
                    override suspend fun testConfiguration() = "ok"
                }
            },
            downloadPdf = { ByteArray(0) },
            extractPdf = { "" },
            effortLevelProvider = { 3 }
        )
        val highReply = highAssistant.ask("Spiega l'entropia in dettaglio", emptyList(), sampleKnowledge)
        assertEquals("Risposta verificata e approfondita", highReply.text)
        assertEquals(2, highCalls, "Su Massimo deve eseguire la verifica completa")
    }

    @Test
    fun openAiReasoningModelsIncludeReasoningEffort() {
        val bodyHigh = openAiCompatibleBody(
            endpoint = "https://api.openai.com/v1/chat/completions",
            model = "o3-mini",
            system = "Sei AILA",
            user = "Calcola",
            maxTokens = 4096,
            reasoningEffort = "high"
        )
        assertEquals("high", bodyHigh["reasoning_effort"]?.jsonPrimitive?.content)

        val bodyNonReasoning = openAiCompatibleBody(
            endpoint = "https://api.openai.com/v1/chat/completions",
            model = "gpt-4o-mini",
            system = "Sei AILA",
            user = "Calcola",
            maxTokens = 1500,
            reasoningEffort = "high"
        )
        assertFalse("reasoning_effort" in bodyNonReasoning)
    }
}
