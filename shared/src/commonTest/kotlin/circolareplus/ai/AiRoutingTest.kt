package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiRoutingTest {

    private val testModel = LocalAiModel(
        id = "test-model",
        displayName = "Test Model",
        fileName = "test-model.litertlm",
        downloadUrl = "https://example.invalid/test-model.litertlm",
        approxSizeBytes = 1_000_000,
        tier = DeviceTier.MID,
        recommendedRamMb = 4_000,
        preferGpu = false,
        supportsActions = true,
        maxOutputTokens = 900,
        maxInputTokens = 100, // maxPromptChars = 200
        description = "Modello finto usato solo per testare la soglia di instradamento."
    )

    @Test
    fun testoSottoSogliaNonAnteponeIlCloud() {
        assertFalse(
            shouldPreferCloudForLength(textLength = 150, localModel = testModel, hasCloudKey = true),
            "Sotto maxPromptChars il locale deve restare la prima scelta"
        )
    }

    @Test
    fun testoSopraSogliaConChiaveAnteponeIlCloud() {
        assertTrue(
            shouldPreferCloudForLength(textLength = 500, localModel = testModel, hasCloudKey = true),
            "Sopra maxPromptChars, con una chiave cloud configurata, va anteposto il cloud"
        )
    }

    @Test
    fun testoSopraSogliaSenzaChiaveNonAnteponeIlCloud() {
        assertFalse(
            shouldPreferCloudForLength(textLength = 500, localModel = testModel, hasCloudKey = false),
            "Senza una chiave cloud configurata non ha senso anteporre un provider che fallirebbe comunque"
        )
    }

    @Test
    fun nessunModelloLocaleNonAnteponeIlCloud() {
        assertFalse(
            shouldPreferCloudForLength(textLength = 500, localModel = null, hasCloudKey = true),
            "Senza un modello locale selezionato non c'è nulla da evitare di troncare"
        )
    }

    // --- Pausa di AICore e scelta del motore ---

    private val aiCore = testModel.copy(
        id = AICORE_MODEL_ID,
        displayName = "AICore",
        fileName = AICORE_MODEL_ID,
        downloadUrl = "",
        approxSizeBytes = 0
    )

    private val liteRt = testModel.copy(id = "gemma-test", displayName = "Gemma di prova")

    /** Orologio finto: il tempo avanza solo quando lo dice il test. */
    private class FakeClock(var now: Long = 1_000_000L)

    private fun cooldown(clock: FakeClock, pauseMillis: Long = 60_000L) =
        AiCoreCooldown(now = { clock.now }, pauseMillis = pauseMillis)

    @Test
    fun codiciCheRiprovareNonRisolveMettonoInPausa() {
        listOf(
            "AICore failed with error type 2-INFERENCE_ERROR and error code 8-NOT_AVAILABLE",
            "error code 9-BUSY",
            "error code QUOTA",
            "error code BACKGROUND",
            "error code NEEDS_SYSTEM_UPDATE",
            "[ErrorCode 606] error code 606-FEATURE_NOT_FOUND: Feature 648 is not available.",
            "PER_APP_BATTERY_USE_QUOTA_EXCEEDED",
            "BACKGROUND_USE_BLOCKED"
        ).forEach { message ->
            val clock = FakeClock()
            val pause = cooldown(clock)
            assertTrue(pause.recordFailure(message), "Deve mettere in pausa: $message")
            assertTrue(pause.isPaused(), "AICore deve risultare in pausa dopo: $message")
        }
    }

    @Test
    fun erroriPasseggeriODelPromptNonMettonoInPausa() {
        listOf(
            "error code 11-RESPONSE_PROCESSING_ERROR",
            "Il collegamento con AICore si e' interrotto (IPC)",
            "error code REQUEST_TOO_LARGE",
            "testo vuoto da AICore (finish=STOP, parti=0, 4000 car.)",
            "AICore non ha risposto entro 90 secondi."
        ).forEach { message ->
            val pause = cooldown(FakeClock())
            assertFalse(pause.recordFailure(message), "Non deve mettere in pausa: $message")
            assertFalse(pause.isPaused(), "AICore non deve risultare in pausa dopo: $message")
        }
    }

    @Test
    fun laPausaScadeDaSola() {
        val clock = FakeClock()
        val pause = cooldown(clock, pauseMillis = 60_000L)
        pause.recordFailure("error code 9-BUSY")
        clock.now += 59_999L
        assertTrue(pause.isPaused(), "Un millisecondo prima della scadenza e' ancora in pausa")
        assertEquals(1L, pause.remainingMillis())
        clock.now += 1L
        assertFalse(pause.isPaused(), "Alla scadenza AICore torna utilizzabile")
        assertEquals(0L, pause.remainingMillis())
    }

    @Test
    fun unaGenerazioneRiuscitaChiudeLaPausa() {
        val pause = cooldown(FakeClock())
        pause.recordFailure("error code QUOTA")
        pause.recordSuccess()
        assertFalse(pause.isPaused())
        assertNull(pause.lastReason)
    }

    @Test
    fun aiCoreInPausaPassaAlModelloLiteRtScaricato() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = aiCore,
            installedModels = listOf(aiCore, liteRt),
            hasCloudKey = true,
            aiCorePaused = true
        )
        assertEquals(liteRt, plan.localModel, "Il primo motore dopo AICore e' LiteRT-LM")
        assertFalse(plan.cloudFirst, "Con un modello LiteRT-LM il cloud resta la riserva")
        assertTrue(plan.aiCoreSkipped)
    }

    @Test
    fun aiCoreInPausaSenzaLiteRtPassaAlCloud() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = aiCore,
            installedModels = listOf(aiCore),
            hasCloudKey = true,
            aiCorePaused = true
        )
        assertNull(plan.localModel, "AICore in pausa non va riprovato")
        assertTrue(plan.cloudFirst, "Senza LiteRT-LM il cloud diventa il primo motore")
    }

    @Test
    fun aiCoreInPausaSenzaLiteRtNeChiaveNonRiprovaAiCore() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = aiCore,
            installedModels = listOf(aiCore),
            hasCloudKey = false,
            aiCorePaused = true
        )
        assertNull(plan.localModel, "Senza alternative si spiega la pausa invece di riprovare AICore")
        assertFalse(plan.cloudFirst, "Senza chiave non ha senso anteporre il cloud")
        assertTrue(plan.aiCoreSkipped, "Il motivo della pausa deve arrivare all'utente")
    }

    @Test
    fun aiCoreNonInPausaRestaIlPrimo() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = aiCore,
            installedModels = listOf(aiCore, liteRt),
            hasCloudKey = true,
            aiCorePaused = false
        )
        assertEquals(aiCore, plan.localModel)
        assertFalse(plan.cloudFirst)
        assertFalse(plan.aiCoreSkipped)
    }

    @Test
    fun laPausaNonToccaUnModelloLiteRtScelto() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = liteRt,
            installedModels = listOf(aiCore, liteRt),
            hasCloudKey = false,
            aiCorePaused = true
        )
        assertEquals(liteRt, plan.localModel)
        assertFalse(plan.aiCoreSkipped)
    }

    @Test
    fun nessunModelloLocaleLasciaLOrdineDiPrima() {
        val plan = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = null,
            installedModels = emptyList(),
            hasCloudKey = true,
            aiCorePaused = true
        )
        assertNull(plan.localModel)
        assertFalse(plan.cloudFirst, "La pausa conta solo se il modello scelto e' AICore")
        assertFalse(plan.aiCoreSkipped)
    }

    @Test
    fun providerCloudConAiCoreInPausaUsaLiteRtComeRiserva() {
        val plan = planAiRoute(
            provider = AiProvider.GOOGLE_AI_STUDIO,
            selectedModel = aiCore,
            installedModels = listOf(aiCore, liteRt),
            hasCloudKey = true,
            aiCorePaused = true
        )
        assertTrue(plan.cloudFirst)
        assertEquals(liteRt, plan.localModel, "Anche come riserva AICore in pausa si salta")
    }

    @Test
    fun testoLungoAnteponeIlCloudSoloSeRichiesto() {
        val base = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = testModel,
            installedModels = emptyList(),
            hasCloudKey = true,
            aiCorePaused = false,
            textLength = 500
        )
        assertFalse(base.cloudFirst, "Con l'anteposizione spenta il locale resta il primo")
        val longText = planAiRoute(
            provider = AiProvider.ON_DEVICE,
            selectedModel = testModel,
            installedModels = emptyList(),
            hasCloudKey = true,
            aiCorePaused = false,
            textLength = 500,
            preferCloudForLongText = true
        )
        assertTrue(longText.cloudFirst)
    }

    @Test
    fun ilMotivoDellaPausaArrivaNelMessaggio() {
        val clock = FakeClock()
        val pause = cooldown(clock, pauseMillis = 5L * 60_000L)
        pause.recordFailure("error code QUOTA")
        val reason = pause.skipReason()
        assertTrue("5 min" in reason, reason)
        assertTrue("QUOTA" in reason, reason)
    }
}
