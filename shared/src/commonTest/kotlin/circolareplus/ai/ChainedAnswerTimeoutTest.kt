package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.*

class ChainedAnswerTimeoutTest {
    private class Stub(private val answer: suspend () -> AiTextResult) : AiClassifier {
        var calls = 0
        override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult { calls++; return answer() }
        override suspend fun classifyCircularText(circularNumber: Int, circularTitle: String, pdfText: String, studentContext: String): CircularAiClassification = error("Unexpected classification")
        override suspend fun parseEventPrompt(userPrompt: String): EventDraft = error("Unexpected parser")
        override suspend fun testConfiguration(): String = error("Unexpected probe")
    }
    private val prompt = aiPromptBuilder(AiFeature.CHAT) { AiPrompt("Sistema", "Domanda") }

    @Test fun anAnswerJustAfterThirtySecondsIsNotCancelledByTheOuterDeadline() = runTest {
        val primary = Stub { delay(30_100); AiTextResult.Success("Risposta", "provider") }
        val secondary = Stub { error("The local fallback must not run") }
        val chain = ChainedAiClassifier(primary, secondary, answerAttemptTimeoutMs = ASSISTANT_ANSWER_ATTEMPT_TIMEOUT_MS)
        assertEquals("Risposta", assertIs<AiTextResult.Success>(chain.generateAnswer(prompt)).text)
        assertEquals(0, secondary.calls)
    }

    @Test fun realProviderFailureAfterItsOwnBudgetKeepsItsReason() = runTest {
        val primary = Stub { delay(30_100); AiTextResult.Failure("Google: quota esaurita") }
        val secondary = Stub { AiTextResult.Failure("Modello locale assente") }
        val chain = ChainedAiClassifier(primary, secondary, answerAttemptTimeoutMs = ASSISTANT_ANSWER_ATTEMPT_TIMEOUT_MS)
        val failure = assertIs<AiTextResult.Failure>(chain.generateAnswer(prompt))
        assertTrue("quota esaurita" in failure.reason)
        assertFalse("entro 30" in failure.reason)
    }

    @Test fun callerCancellationStillStopsBeforeFallback() = runTest {
        val primary = Stub { awaitCancellation() }
        val secondary = Stub { error("Cancellation must not start another provider") }
        val chain = ChainedAiClassifier(primary, secondary, answerAttemptTimeoutMs = ASSISTANT_ANSWER_ATTEMPT_TIMEOUT_MS)
        assertNull(withTimeoutOrNull(100) { chain.generateAnswer(prompt) })
        assertEquals(0, secondary.calls)
    }
}
