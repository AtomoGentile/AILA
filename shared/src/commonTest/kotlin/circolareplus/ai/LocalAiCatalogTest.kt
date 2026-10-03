package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coerenza del catalogo dei modelli locali della piattaforma su cui girano i test (sulla CI:
 * Android). Un errore qui non si vedrebbe altrimenti fino al telefono: un id doppio fa scegliere
 * il modello sbagliato dalle Impostazioni, un nome di file doppio fa cancellare come "orfano" un
 * modello buono.
 */
class LocalAiCatalogTest {

    private val all = LocalAiCatalog.all

    @Test
    fun catalogoNonVuoto() {
        assertTrue(all.isNotEmpty())
    }

    @Test
    fun idENomiDiFileUnivoci() {
        assertEquals(all.size, all.map { it.id }.toSet().size, "Id doppi: ${all.map { it.id }}")
        assertEquals(all.size, all.map { it.fileName }.toSet().size, "File doppi: ${all.map { it.fileName }}")
        assertEquals(all.map { it.fileName }.toSet(), LocalAiCatalog.knownFileNames())
    }

    @Test
    fun byIdRitrovaOgniModello() {
        all.forEach { assertEquals(it, LocalAiCatalog.byId(it.id)) }
        assertEquals(null, LocalAiCatalog.byId("modello-che-non-esiste"))
    }

    @Test
    fun limitiDiTokenCoerenti() {
        all.forEach { model ->
            assertTrue(model.maxOutputTokens > 0, "${model.id}: maxOutputTokens")
            // La risposta deve stare nella finestra insieme a un prompt utile.
            assertTrue(
                model.maxInputTokens > model.maxOutputTokens,
                "${model.id}: maxInputTokens (${model.maxInputTokens}) <= maxOutputTokens"
            )
            assertEquals(model.maxInputTokens * 2, model.maxPromptChars, "${model.id}: maxPromptChars")
            // Sotto questa soglia il classificatore non avrebbe spazio nemmeno per il PDF ridotto.
            assertTrue(model.maxPromptChars >= 4_000, "${model.id}: maxPromptChars troppo basso")
        }
    }

    @Test
    fun modelliScaricabiliEDiSistemaBenDistinti() {
        all.forEach { model ->
            if (model.isSystemModel) {
                assertEquals(0L, model.approxSizeBytes, "${model.id}: un modello di sistema non si scarica")
            } else {
                assertTrue(model.downloadUrl.startsWith("https://"), "${model.id}: URL non https")
                assertTrue(model.downloadUrl.endsWith(model.fileName), "${model.id}: URL e file diversi")
                assertTrue(model.approxSizeBytes > 0, "${model.id}: dimensione mancante")
            }
        }
    }

    @Test
    fun unModelloConsigliatoPerOgniFascia() {
        DeviceTier.entries.forEach { tier ->
            val recommended = LocalAiCatalog.recommendedFor(tier)
            assertTrue(recommended in all, "Il consigliato per $tier non e' nel catalogo")
        }
    }

    @Test
    fun ilConsigliatoEInCimaAllaScelta() {
        listOf(0, 3_000, 5_000, 12_000).forEach { ram ->
            val selectable = LocalAiCatalog.selectableFor(ram)
            assertEquals(LocalAiCatalog.recommendedFor(deviceTierForRam(ram)), selectable.first(), "RAM $ram MB")
            assertEquals(all.toSet(), selectable.toSet(), "RAM $ram MB: la scelta deve contenere tutto il catalogo")
        }
    }
}
