package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
