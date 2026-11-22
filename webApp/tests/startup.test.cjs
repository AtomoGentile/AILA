const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const kotlin = readFileSync(join(__dirname,
  '../../shared/src/wasmJsMain/kotlin/circolareplus/platform/OfflineStore.wasmJs.kt'), 'utf8');

// Esegue i corpi JS effettivamente usati dall'interop Wasm, con un IndexedDB controllato.
function interop(name, globals) {
  const body = kotlin.match(new RegExp('private fun ' + name + '\\([^]*?= js\\(\\s*"""([^]*?)"""'))[1];
  const timers = new Map();
  let id = 0;
  const result = vm.runInNewContext(body, {
    setTimeout(fn) { timers.set(++id, fn); return id; },
    clearTimeout(key) { timers.delete(key); }, ...globals,
  });
  return { result, expire() { for (const fn of [...timers.values()]) fn(); }, timers };
}

test('IndexedDB sospeso: avvio sbloccato, connessione tardiva chiusa', async () => {
  const request = {};
  let closed = false;
  const s = interop('idbOpen', { indexedDB: { open: () => request } });
  s.expire();
  assert.equal(await s.result, null);
  request.result = { close() { closed = true; } };
  request.onsuccess();
  assert.equal(closed, true);
});

test('IndexedDB disponibile: restituisce il database e cancella il timeout', async () => {
  const request = { result: {} };
  const s = interop('idbOpen', { indexedDB: { open: () => request } });
  request.onsuccess();
  assert.equal(await s.result, request.result);
  assert.equal(s.timers.size, 0);
});

test('IndexedDB non disponibile: prosegue senza cache persistente', async () => {
  const s = interop('idbOpen', { indexedDB: { open() { throw Error('unavailable'); } } });
  assert.equal(await s.result, null);
  assert.equal(s.timers.size, 0);
});

test('lettura bloccata: abortisce la transazione e ignora risultati tardivi', async () => {
  let aborted = false;
  const cursor = {};
  const tx = { objectStore: () => ({ openCursor: () => cursor }), abort() { aborted = true; } };
  const s = interop('idbReadAll', { db: { transaction: () => tx } });
  s.expire();
  assert.equal(await s.result, null);
  assert.equal(aborted, true);
  cursor.result = { continue() { assert.fail('lettura tardiva'); } };
  cursor.onsuccess();
});

test('lettura riuscita: conserva i dati offline', async () => {
  const cursor = {};
  const tx = { objectStore: () => ({ openCursor: () => cursor }) };
  const s = interop('idbReadAll', { db: { transaction: () => tx } });
  cursor.result = { key: 'document.pdf', value: 'YWlsYQ==', continue() {} };
  cursor.onsuccess();
  tx.oncomplete();
  assert.equal(JSON.stringify(await s.result), '[["document.pdf","YWlsYQ=="]]');
  assert.equal(s.timers.size, 0);
});

test('transazione abortita: non restituisce una cache incompleta', async () => {
  const tx = { objectStore: () => ({ openCursor: () => ({}) }) };
  const s = interop('idbReadAll', { db: { transaction: () => tx } });
  tx.onabort();
  assert.equal(await s.result, null);
  assert.equal(s.timers.size, 0);
});

function startup({ userAgent = '', search = '' } = {}) {
  let timeout, replaced, reloaded = false;
  const classes = new Set();
  const storage = new Map();
  const documentListeners = {};
  const windowListeners = {};
  const nodes = {
    'aila-splash': { classList: { add: name => classes.add(name) } },
    'aila-startup-error': { hidden: true },
    'diagnosi-avvio': { hasAttribute() { return this.kotlinStarted; } },
  };
  const window = { addEventListener: (type, handler) => { windowListeners[type] = handler; } };
  vm.runInNewContext(readFileSync(join(__dirname, '../src/wasmJsMain/resources/startup.js'), 'utf8'), {
    window, document: {
      getElementById: id => nodes[id],
      addEventListener: (type, handler) => { documentListeners[type] = handler; },
    },
    URL, URLSearchParams, navigator: { userAgent }, localStorage: { setItem: storage.set.bind(storage) },
    location: {
      search,
      href: 'https://aila.test/' + search + '#diagnosi-avvio',
      reload() { reloaded = true; },
      replace(url) { replaced = url; },
    },
    setTimeout(fn) { timeout = fn; return 1; }, clearTimeout() { timeout = null; },
  });
  return {
    window, nodes, classes, windowListeners, storage,
    expire() { timeout?.(); },
    retry() { documentListeners.click({ target: { id: 'aila-startup-retry' }, preventDefault() {} }); },
    get reloaded() { return reloaded; },
    get replaced() { return replaced; },
  };
}

test('runtime bloccato: mostra un recupero e ricarica solo su richiesta', () => {
  const s = startup();
  s.expire();
  assert.equal(s.classes.has('startup-slow'), true);
  assert.equal(s.reloaded, false);
  s.retry();
  assert.equal(s.reloaded, true);
});

test('avvio riuscito: non compare la schermata di recupero', () => {
  const s = startup();
  s.window.__ailaStartupReady();
  s.expire();
  assert.equal(s.classes.has('startup-slow'), false);
});

test('bundle assente: mostra subito l’errore e il recupero', () => {
  const s = startup();
  s.windowListeners.error({ target: { id: 'aila-app-script' } });
  assert.equal(s.nodes['aila-startup-error'].hidden, false);
  assert.match(s.nodes['aila-startup-error'].textContent, /non ha caricato il bundle/);
  assert.equal(s.classes.has('startup-slow'), true);
});

test('un errore Wasm conserva la fase raggiunta e oscura gli indirizzi', () => {
  const s = startup();
  const stage = s.nodes['diagnosi-avvio'];
  stage.kotlinStarted = true;
  stage.textContent = 'Carico i dati offline.';
  s.windowListeners.load({ target: { id: 'aila-app-script' } });
  s.windowListeners.unhandledrejection({ reason: new WebAssembly.RuntimeError('https://private.test') });
  assert.equal(stage.textContent, 'Carico i dati offline.');
  assert.equal(s.nodes['aila-startup-error'].textContent, 'Errore durante l’avvio (RuntimeError): [indirizzo]');
});

test('un errore generico conserva la descrizione e la causa locale', () => {
  const s = startup();
  const error = new Error('Impossibile aprire il canvas.', { cause: new Error('Contesto WebGL perso.') });
  s.windowListeners.unhandledrejection({ reason: error });
  s.windowListeners.unhandledrejection({ reason: undefined });
  assert.equal(s.nodes['aila-startup-error'].textContent,
    'Errore durante l’avvio (Error): Impossibile aprire il canvas. · Contesto WebGL perso.');
});

test('Riprova su iPhone passa una volta all’avvio compatibile e conserva il link', () => {
  const s = startup({ userAgent: 'iPhone', search: '?diagnosi=1' });
  s.retry();
  assert.equal(s.replaced, 'https://aila.test/?diagnosi=1&avvio=compatibile#diagnosi-avvio');
  assert.equal(s.reloaded, false);
  const second = startup({ userAgent: 'iPhone', search: '?avvio=compatibile' });
  second.retry();
  assert.equal(second.reloaded, true);
  assert.equal(second.replaced, undefined);
});

test('un errore di Skiko su iOS riparte a grafica ridotta e la conserva soltanto dopo il disegno', () => {
  const error = { reason: new Error("Can't wrap nullptr") };
  const first = startup({ userAgent: 'iPad', search: '?avvio=compatibile' });
  first.windowListeners.unhandledrejection(error);
  assert.equal(first.replaced, 'https://aila.test/?avvio=compatibile&grafica=ridotta#diagnosi-avvio');
  assert.equal(first.storage.has('aila-grafica-ridotta'), false);

  const second = startup({ userAgent: 'iPad', search: '?grafica=ridotta' });
  second.windowListeners.unhandledrejection(error);
  assert.equal(second.replaced, undefined);
  assert.equal(second.storage.has('aila-grafica-ridotta'), false);
  second.window.__ailaStartupReady();
  assert.equal(second.storage.get('aila-grafica-ridotta'), '1');
});
