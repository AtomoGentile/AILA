package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiCoreStatusTest {

    // --- Stato di checkStatus() ---

    @Test
    fun disponibilePermetteDiGenerare() {
        val state = AiCoreStatusMapper.fromFeatureStatus(AiCoreFeatureStatus.AVAILABLE)
        assertEquals(AiCoreState.Available, state)
        assertTrue(AiCoreStatusMapper.canGenerate(state))
        assertFalse(AiCoreStatusMapper.needsDownload(state))
    }

    @Test
    fun daScaricareChiedeIlDownload() {
        val state = AiCoreStatusMapper.fromFeatureStatus(AiCoreFeatureStatus.DOWNLOADABLE)
        assertEquals(AiCoreState.Downloadable, state)
        assertFalse(AiCoreStatusMapper.canGenerate(state))
        assertTrue(AiCoreStatusMapper.needsDownload(state))
    }

    @Test
    fun inScaricamentoSegueIlDownload() {
        val state = AiCoreStatusMapper.fromFeatureStatus(AiCoreFeatureStatus.DOWNLOADING)
        assertEquals(AiCoreState.Downloading, state)
        assertFalse(AiCoreStatusMapper.canGenerate(state))
        assertTrue(AiCoreStatusMapper.needsDownload(state))
    }

    @Test
    fun nonDisponibileSpiegaEMetteInPausa() {
        val state = AiCoreStatusMapper.fromFeatureStatus(AiCoreFeatureStatus.UNAVAILABLE)
        assertIs<AiCoreState.Unavailable>(state)
        assertFalse(AiCoreStatusMapper.canGenerate(state))
        assertFalse(AiCoreStatusMapper.needsDownload(state))
        assertTrue(
            AiCoreCooldown.isPausingFailure(state.message),
            "Il messaggio deve far scattare la pausa di AICore: ${state.message}"
        )
    }

    @Test
    fun statoSconosciutoEUnErrorePasseggero() {
        val state = AiCoreStatusMapper.fromFeatureStatus(AiCoreFeatureStatus.UNKNOWN)
        assertIs<AiCoreState.Failed>(state)
        assertTrue(state.failure.isTransient)
        assertFalse(AiCoreStatusMapper.canGenerate(state))
    }

    // --- Codici d'errore ---

    @Test
    fun ogniCodiceNotoHaLaSuaCategoria() {
        val expected = mapOf(
            AiCoreErrorCode.BUSY to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.IPC_ERROR to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.INTERNAL_ERROR to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.REQUEST_PROCESSING_ERROR to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.RESPONSE_GENERATION_ERROR to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.RESPONSE_PROCESSING_ERROR to AiCoreFailureKind.TRANSIENT,
            AiCoreErrorCode.REQUEST_TOO_LARGE to AiCoreFailureKind.PROMPT,
            AiCoreErrorCode.REQUEST_TOO_SMALL to AiCoreFailureKind.PROMPT,
            AiCoreErrorCode.NOT_AVAILABLE to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.FEATURE_NOT_FOUND to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.NEEDS_SYSTEM_UPDATE to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.NOT_ENOUGH_DISK_SPACE to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.BACKGROUND_USE_BLOCKED to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.PRIVATE_MODE to AiCoreFailureKind.PERMANENT,
            AiCoreErrorCode.CANCELLED to AiCoreFailureKind.PERMANENT
        )
        assertEquals(AiCoreErrorCode.entries.toSet(), expected.keys, "Ogni codice va coperto dal test")
        expected.forEach { (code, kind) ->
            val failure = AiCoreStatusMapper.classifyError(code, "dettaglio")
            assertEquals(kind, failure.kind, "Categoria sbagliata per $code")
            assertTrue(code.name in failure.message, "Il nome del codice deve restare nel messaggio: $code")
        }
    }

    @Test
    fun codiceLettoDalTestoDiAiCore() {
        assertEquals(
            AiCoreErrorCode.FEATURE_NOT_FOUND,
            AiCoreStatusMapper.errorCodeFromMessage(
                "GenAiException: [ErrorCode 606] AICore failed with error type 3-PREPARATION_ERROR " +
                    "and error code 606-FEATURE_NOT_FOUND: Feature 648 is not available."
            )
        )
        assertEquals(
            AiCoreErrorCode.NOT_AVAILABLE,
            AiCoreStatusMapper.errorCodeFromMessage(
                "AICore failed with error type 2-INFERENCE_ERROR and error code 8-NOT_AVAILABLE: " +
                    "Required LLM feature not found"
            )
        )
        assertEquals(
            AiCoreErrorCode.BUSY,
            AiCoreStatusMapper.errorCodeFromMessage("error type 1-INTERNAL_ERROR and error code 9-BUSY"),
            "Conta il codice, non il tipo d'errore"
        )
        assertEquals(
            AiCoreErrorCode.RESPONSE_PROCESSING_ERROR,
            AiCoreStatusMapper.errorCodeFromMessage("error code 11-RESPONSE_PROCESSING_ERROR")
        )
    }

    @Test
    fun nomiDellaVecchiaLibreriaRiconosciuti() {
        assertEquals(
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            AiCoreStatusMapper.errorCodeFromMessage("error code 12-QUOTA")
        )
        assertEquals(
            AiCoreErrorCode.BACKGROUND_USE_BLOCKED,
            AiCoreStatusMapper.errorCodeFromMessage("error code BACKGROUND")
        )
        assertEquals(
            AiCoreErrorCode.IPC_ERROR,
            AiCoreStatusMapper.errorCodeFromMessage("IPC failure")
        )
        assertEquals(
            AiCoreErrorCode.FEATURE_NOT_FOUND,
            AiCoreStatusMapper.errorCodeFromMessage("Required LLM feature not found")
        )
    }

    @Test
    fun erroreSenzaCodiceEPasseggero() {
        assertNull(AiCoreStatusMapper.errorCodeFromMessage("qualcosa e' andato storto"))
        val failure = AiCoreStatusMapper.classifyError(null, "qualcosa e' andato storto")
        assertNull(failure.code)
        assertTrue(failure.isTransient)
        assertTrue("Riprova" in failure.message)
    }

    @Test
    fun ilCodiceDellSdkVinceSulTesto() {
        val failure = AiCoreStatusMapper.classifyError(AiCoreErrorCode.BUSY, "error code 8-NOT_AVAILABLE")
        assertEquals(AiCoreErrorCode.BUSY, failure.code)
    }

    @Test
    fun messaggiInItalianoConIlDettaglioGrezzo() {
        val failure = AiCoreStatusMapper.classifyError(
            AiCoreErrorCode.BACKGROUND_USE_BLOCKED,
            "GenAiException: background"
        )
        assertTrue("primo piano" in failure.message, failure.message)
        assertTrue("GenAiException: background" in failure.message, failure.message)
        assertFalse("Riprova" in failure.message, "Un errore permanente non invita a riprovare subito")
    }

    // --- Ritentare o arrendersi ---

    @Test
    fun errorePasseggeroSiRitentaFinoAlTetto() {
        val failure = AiCoreStatusMapper.classifyError(AiCoreErrorCode.BUSY, "")
        assertTrue(AiCoreStatusMapper.shouldRetry(failure, attempt = 1))
        assertTrue(AiCoreStatusMapper.shouldRetry(failure, attempt = AiCoreStatusMapper.TRANSIENT_ATTEMPTS - 1))
        assertFalse(
            AiCoreStatusMapper.shouldRetry(failure, attempt = AiCoreStatusMapper.TRANSIENT_ATTEMPTS),
            "All'ultimo tentativo ci si arrende"
        )
    }

    @Test
    fun errorePermanenteODelPromptNonSiRitenta() {
        listOf(
            AiCoreErrorCode.NOT_AVAILABLE,
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            AiCoreErrorCode.REQUEST_TOO_LARGE
        ).forEach { code ->
            val failure = AiCoreStatusMapper.classifyError(code, "")
            assertFalse(AiCoreStatusMapper.shouldRetry(failure, attempt = 1), "Non va ritentato: $code")
        }
    }

    @Test
    fun laPausaFraITentativiCresce() {
        assertTrue(AiCoreStatusMapper.retryDelayMillis(2) > AiCoreStatusMapper.retryDelayMillis(1))
    }

    @Test
    fun soloIlCollegamentoCadutoRicreaIlClient() {
        assertTrue(AiCoreStatusMapper.needsReconnect(AiCoreStatusMapper.classifyError(null, "IPC failure")))
        assertFalse(AiCoreStatusMapper.needsReconnect(AiCoreStatusMapper.classifyError(AiCoreErrorCode.BUSY, "")))
    }

    // --- Risposta vuota ---

    @Test
    fun rispostaVuotaRiconosciutaDalClassificatore() {
        val message = AiCoreStatusMapper.emptyAnswerMessage(finishReason = "STOP", parts = 1, promptChars = 4_200)
        assertTrue(AiCoreStatusMapper.isEmptyAnswer(message))
        assertTrue(message.startsWith("testo vuoto"), "LocalAiClassifier cerca proprio \"testo vuoto\"")
        assertTrue("4200" in message)
        assertFalse(AiCoreCooldown.isPausingFailure(message), "Una risposta vuota non mette in pausa AICore")
        assertFalse(AiCoreStatusMapper.isEmptyAnswer("error code 9-BUSY"))
    }

    @Test
    fun richiestaTroppoLungaResaRiconoscibile() {
        val failure = AiCoreStatusMapper.classifyError(AiCoreErrorCode.REQUEST_TOO_LARGE, "")
        // LocalAiClassifier.isPromptTooLong cerca "request_too_large" (minuscolo) nel messaggio.
        assertTrue("request_too_large" in failure.message.lowercase())
    }

    @Test
    fun codiciCheMettonoInPausaRestanoNelMessaggio() {
        listOf(
            AiCoreErrorCode.BUSY,
            AiCoreErrorCode.NOT_AVAILABLE,
            AiCoreErrorCode.FEATURE_NOT_FOUND,
            AiCoreErrorCode.NEEDS_SYSTEM_UPDATE,
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            AiCoreErrorCode.BACKGROUND_USE_BLOCKED
        ).forEach { code ->
            val failure = AiCoreStatusMapper.classifyError(code, "")
            assertTrue(AiCoreCooldown.isPausingFailure(failure.message), "Deve mettere in pausa: $code")
        }
        listOf(
            AiCoreErrorCode.RESPONSE_PROCESSING_ERROR,
            AiCoreErrorCode.REQUEST_TOO_LARGE,
            AiCoreErrorCode.IPC_ERROR
        ).forEach { code ->
            val failure = AiCoreStatusMapper.classifyError(code, "")
            assertFalse(AiCoreCooldown.isPausingFailure(failure.message), "Non deve mettere in pausa: $code")
        }
    }

    // --- Download di Gemini Nano che non parte o si ferma ---

    private class Clock(var now: Long = 0L)

    @Test
    fun downloadMaiPartitoSiDichiaraFermo() {
        val clock = Clock()
        val watch = AiCoreDownloadWatch(now = { clock.now }, startTimeoutMillis = 60_000, stallTimeoutMillis = 120_000)
        clock.now = 60_000
        assertFalse(watch.isStalled(), "Fino al tetto si aspetta")
        clock.now = 60_001
        assertTrue(watch.isStalled(), "Senza nessun evento dopo il tetto si smette di aspettare")
        val message = watch.stalledMessage(AiCoreState.Downloadable.statusName())
        assertTrue("DOWNLOADABLE" in message, message)
        assertTrue("non ha avviato" in message, message)
    }

    @Test
    fun downloadCheAvanzaNonEFermo() {
        val clock = Clock()
        val watch = AiCoreDownloadWatch(now = { clock.now }, startTimeoutMillis = 60_000, stallTimeoutMillis = 120_000)
        clock.now = 50_000
        watch.onStarted(1_000L)
        clock.now = 150_000
        assertFalse(watch.isStalled(), "Partito: vale il tetto piu' lungo")
        watch.onProgress(400L)
        clock.now = 260_000
        assertFalse(watch.isStalled(), "Ogni avanzamento sposta il tetto")
        clock.now = 270_001
        assertTrue(watch.isStalled())
        assertTrue("non avanza" in watch.stalledMessage("DOWNLOADING"))
    }

    @Test
    fun totaleDelDownloadTenutoFraGliEventi() {
        val watch = AiCoreDownloadWatch(now = { 0L })
        watch.onStarted(1_000L)
        watch.onProgress(250L)
        assertEquals(1_000L, watch.totalBytes, "Il totale arriva solo all'inizio e va ricordato")
        assertEquals(250L, watch.downloadedBytes)
        watch.onProgress(1_200L)
        assertEquals(1_200L, watch.totalBytes, "Mai oltre il 100%")
    }

    @Test
    fun totaleIgnotoSenzaPartenza() {
        val watch = AiCoreDownloadWatch(now = { 0L })
        watch.onStarted(0L)
        assertEquals(0L, watch.totalBytes)
        assertTrue(watch.started)
    }

    @Test
    fun nomiDegliStatiPerLaDiagnosi() {
        assertEquals("AVAILABLE", AiCoreState.Available.statusName())
        assertEquals("DOWNLOADING", AiCoreState.Downloading.statusName())
        assertEquals("UNAVAILABLE", AiCoreState.Unavailable("x").statusName())
        assertEquals(
            "BUSY",
            AiCoreState.Failed(AiCoreStatusMapper.classifyError(AiCoreErrorCode.BUSY, "")).statusName()
        )
    }
}
