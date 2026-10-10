@file:OptIn(ExperimentalWasmJsInterop::class)

package circolareplus.web

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Registrazione del service worker (sw.js, accanto a index.html) e suoi aggiornamenti.
 *
 * Aggiornamenti, scelti per non disturbare chi sta usando l'app:
 * - codice dell'app (aila.js e .wasm): il SW serve la copia in cache e intanto scarica la nuova,
 *   che vale dal prossimo avvio. Niente ricarica a meta' uso; [isWebAppUpdateReady] dice se una
 *   versione nuova e' gia' pronta, [applyWebAppUpdate] la applica subito (per un futuro "Aggiorna");
 * - service worker nuovo: resta in attesa e subentra (SKIP_WAITING) quando la pagina va in
 *   background, oppure subito se lo si trova in attesa all'avvio a freddo. Il SW serve solo cache
 *   e notifiche, quindi il cambio non richiede di ricaricare la pagina.
 */
internal object WebServiceWorker {

    /** Una versione nuova di aila.js e' gia' in cache: si usera' dal prossimo avvio. */
    var updateReady: Boolean = false

    /** Registra il SW e inoltra a [onMessage] i messaggi che manda (JSON). Non lancia mai. */
    suspend fun register(onMessage: (String) -> Unit): Boolean =
        runCatching { jsRegister(onMessage).await<JsBoolean>().toBoolean() }.getOrDefault(false)

    /**
     * Controllo periodico delle circolari (tag 'aila-sync', vedi sw.js). Solo Chrome/Edge con la
     * PWA installata, e solo se il browser concede il permesso (dipende da quanto si usa il sito):
     * altrove non fa nulla e restano i push.
     */
    suspend fun registerPeriodicSync(): Boolean =
        runCatching { jsRegisterPeriodicSync().await<JsBoolean>().toBoolean() }.getOrDefault(false)

    suspend fun unregisterPeriodicSync() {
        runCatching { jsUnregisterPeriodicSync().await<JsAny?>() }
    }

    fun applyUpdate() = jsApplyUpdate()
}

/** true se una nuova versione dell'app e' gia' scaricata e partira' al prossimo avvio. */
fun isWebAppUpdateReady(): Boolean = WebServiceWorker.updateReady

/** Ricarica subito con la versione nuova (SW in attesa compreso). Da un'azione dell'utente. */
fun applyWebAppUpdate() = WebServiceWorker.applyUpdate()

private fun jsRegister(onMessage: (String) -> Unit): Promise<JsBoolean> = js(
    """(async () => {
      if (!('serviceWorker' in navigator) || !window.isSecureContext) return false;
      const sw = navigator.serviceWorker;
      sw.addEventListener('message', (e) => {
        try { onMessage(JSON.stringify(e.data || {})); } catch (err) { console.error(err); }
      });
      sw.startMessages();

      // updateViaCache 'none': il browser ricontrolla sw.js a ogni navigazione, senza la cache HTTP.
      const reg = await sw.register('sw.js', { scope: './', updateViaCache: 'none' });
      const activate = (worker) => { if (worker) worker.postMessage({ type: 'SKIP_WAITING' }); };

      // Avvio a freddo con un SW nuovo gia' in attesa: subentra subito (la pagina sta partendo).
      if (reg.waiting && sw.controller) activate(reg.waiting);
      reg.addEventListener('updatefound', () => {
        const worker = reg.installing;
        if (!worker) return;
        worker.addEventListener('statechange', () => {
          if (worker.state === 'installed' && sw.controller && document.visibilityState === 'hidden') activate(worker);
        });
      });
      // Pagina in uso: il SW nuovo aspetta che vada in background.
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden' && reg.waiting) activate(reg.waiting);
        if (document.visibilityState === 'visible') reg.update().catch(() => {});
      });

      // Primo avvio: la pagina ha caricato font e composeResources prima che il SW la controllasse,
      // quindi non sono in cache. Gli si passa l'elenco, dopo che l'app ha disegnato.
      setTimeout(async () => {
        try {
          const ready = await sw.ready;
          const urls = performance.getEntriesByType('resource')
            .map((r) => r.name)
            .filter((u) => u.startsWith(location.origin));
          urls.push(location.origin + location.pathname);
          if (ready.active) ready.active.postMessage({ type: 'aila-cache-urls', urls });
        } catch (err) { /* si riprova al prossimo avvio */ }
      }, 10000);
      return true;
    })().catch((err) => { console.error('[AILA] service worker non registrato', err); return false; })"""
)

private fun jsRegisterPeriodicSync(): Promise<JsBoolean> = js(
    """(async () => {
      if (!('serviceWorker' in navigator)) return false;
      const reg = await navigator.serviceWorker.ready;
      if (!('periodicSync' in reg) || !navigator.permissions) return false;
      const status = await navigator.permissions.query({ name: 'periodic-background-sync' });
      if (status.state !== 'granted') return false;
      // Il minimo effettivo lo decide il browser (Chrome: circa 12 ore, secondo l'uso del sito).
      await reg.periodicSync.register('aila-sync', { minInterval: 60 * 60 * 1000 });
      return true;
    })().catch(() => false)"""
)

private fun jsUnregisterPeriodicSync(): Promise<JsAny?> = js(
    """(async () => {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg && 'periodicSync' in reg) await reg.periodicSync.unregister('aila-sync');
      return null;
    })().catch(() => null)"""
)

private fun jsApplyUpdate(): Unit = js(
    """{
      const sw = navigator.serviceWorker;
      if (!sw) { location.reload(); return; }
      sw.getRegistration().then((reg) => {
        if (reg && reg.waiting) {
          sw.addEventListener('controllerchange', () => location.reload(), { once: true });
          reg.waiting.postMessage({ type: 'SKIP_WAITING' });
        } else {
          location.reload();
        }
      }, () => location.reload());
    }"""
)
