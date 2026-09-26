// =============================================================================
// AILA — Lavoro dopo la risposta
// =============================================================================
//
// Le notifiche push partono DOPO aver risposto al telefono: prima ogni rotta aspettava l'invio a
// tutti i dispositivi della classe (token OAuth, una richiesta per token, web push) e il tasto
// premuto (es. "Apri votazione") restava in attesa per secondi. `waitUntil` tiene vivo il Worker
// finché l'invio non finisce, senza far aspettare chi ha premuto.

export function inBackground(c: { executionCtx: { waitUntil(promise: Promise<unknown>): void } }, task: Promise<unknown>): void {
  const guarded = task.catch((err) => console.error('[Background] invio non riuscito', err));
  try {
    c.executionCtx.waitUntil(guarded);
  } catch {
    // Fuori dal runtime dei Worker (test) non c'è ExecutionContext: la promessa gira comunque.
  }
}
