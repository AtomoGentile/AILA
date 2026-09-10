package circolareplus.data.remote

/**
 * Configurazione dell'endpoint del Cloudflare Worker.
 *
 * IMPORTANTE: BASE_URL va sostituito con l'URL reale del Worker dopo il deploy
 * (es. "https://circolare-plus-worker.<tuo-subdominio>.workers.dev" oppure un
 * dominio personalizzato). Lo trovi con `wrangler deploy` (viene stampato in
 * console) o nella dashboard Cloudflare -> Workers & Pages -> circolare-plus-worker.
 *
 * Se preferisci non ricompilare per cambiarlo, puoi impostarlo anche a runtime
 * da ProfileScreen/impostazioni sviluppatore tramite [circolareplus.data.local.LocalSettingsManager].
 */
object ApiConfig {
    const val DEFAULT_BASE_URL: String = "https://circolare-plus-worker.circolareclass.workers.dev"
}
