package circolareplus.ai

import circolareplus.platform.AndroidAppContext

/**
 * Tier 1 Android: Gemini Nano di sistema via **AICore** (`com.google.ai.edge.aicore`).
 *
 * **Stato: scheletro non collegato all'SDK reale.** Questo ambiente di sviluppo non ha accesso
 * di rete verso Maven — non è stato possibile risolvere le coordinate/versione reali della
 * dipendenza né verificare la forma esatta dell'API (AICore è un SDK in developer preview, con
 * una storia di requisiti stretti: durante la preview iniziale era limitato a pochi dispositivi
 * Pixel con l'app di sistema "AICore" installata da Play Store, non a "tutti i flagship Android"
 * come genericamente indicato nel piano di partenza). Aggiungere una dipendenza Gradle con
 * coordinate indovinate avrebbe rotto la build per l'intero modulo — un rischio inaccettabile per
 * una funzionalità che poi risulta comunque non disponibile sulla stragrande maggioranza dei
 * telefoni. Questa classe espone quindi la stessa forma che avrà l'integrazione vera, ma
 * `isAvailable()` ritorna sempre `false`: la catena (vedi [LocalLlm.generate]) ricade sempre e
 * comunque sul modello LiteRT-LM già scaricato (Tier 2), senza nessun nuovo punto di rottura.
 *
 * **Per completare l'integrazione vera** (da fare su una macchina con accesso a Maven e un
 * dispositivo Pixel/Android compatibile per il test):
 * 1. Aggiungere `com.google.ai.edge.aicore:aicore:<versione verificata>` in
 *    `gradle/libs.versions.toml` (voce `aicore` in `[versions]`/`[libraries]`, commentata più
 *    sotto) e come `api(libs.aicore.android)` in `shared/build.gradle.kts` (source set
 *    `androidMain`, stesso motivo di `litertlm-android`: `:androidApp` deve vedere le classi a
 *    runtime).
 * 2. In [isAvailable], sostituire lo stub con la verifica reale dello stato del servizio
 *    (`GenerativeModel`/`AICore`, stato `STATUS_AVAILABLE` secondo la documentazione ufficiale al
 *    momento della build — l'API pubblica di AICore è cambiata più volte durante la preview).
 * 3. In [generate], costruire il modello e generare davvero, mantenendo lo stesso contratto
 *    (system+user prompt concatenati: AICore è **stateless**, nessuna sessione con storico, a
 *    differenza di Apple Intelligence che invece la mantiene lato Swift).
 */
internal object AiCoreEngine {

    /**
     * `true` se il servizio di sistema AICore è installato, attivo e pronto a generare su questo
     * dispositivo. Sempre `false` finché il punto 2 sopra non è implementato.
     */
    fun isAvailable(): Boolean {
        if (AndroidAppContext.getOrNull() == null) return false
        // TODO(AICORE): sostituire con la verifica reale (vedi commento di classe).
        return false
    }

    /** Motivo leggibile per cui AICore non è disponibile, mostrato nelle Impostazioni. */
    fun unavailableReason(): String =
        "AICore non è ancora collegato in questa build: integrazione da completare " +
            "(vedi commento in AiCoreEngine.kt). L'AI locale usa il modello scaricabile."

    /**
     * Genera una risposta con Gemini Nano via AICore.
     *
     * @throws IllegalStateException finché il punto 3 sopra non è implementato — [LocalLlm]
     * intercetta l'eccezione e ricade sul modello LiteRT-LM, esattamente come già fa oggi per un
     * fallimento della GPU.
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
        timeoutMillis: Long
    ): String {
        throw IllegalStateException(unavailableReason())
    }
}
