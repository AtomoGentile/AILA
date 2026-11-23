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

function startup({ userAgent = '', search = '', maxTouchPoints = 0, wasm, saved = [] } = {}) {
  let timeout, replaced, reloaded = false;
  const classes = new Set();
  const storage = new Map(saved);
  const frames = [];
  const documentListeners = {};
  const windowListeners = {};
  const nodes = {
    'aila-splash': { classList: { add: name => classes.add(name) } },
    'aila-viewport': { clientWidth: 0, clientHeight: 0 },
    'aila-startup-error': { hidden: true },
    'diagnosi-avvio': { hasAttribute() { return this.kotlinStarted; } },
  };
  const window = { WebAssembly: wasm, addEventListener: (type, handler) => { windowListeners[type] = handler; } };
  const document = {
    hidden: false,
    getElementById: id => nodes[id],
    addEventListener: (type, handler) => { documentListeners[type] = handler; },
  };
  vm.runInNewContext(readFileSync(join(__dirname, '../src/wasmJsMain/resources/startup.js'), 'utf8'), {
    window, document, requestAnimationFrame: fn => frames.push(fn),
    URL, URLSearchParams, navigator: { userAgent, maxTouchPoints },
    localStorage: {
      setItem: storage.set.bind(storage),
      getItem: key => storage.get(key) ?? null,
      removeItem: storage.delete.bind(storage),
    },
    location: {
      search,
      href: 'https://aila.test/' + search + '#diagnosi-avvio',
      reload() { reloaded = true; },
      replace(url) { replaced = url; },
    },
    setTimeout(fn) { timeout = fn; return 1; }, clearTimeout() { timeout = null; },
  });
  return {
    window, document, nodes, classes, windowListeners, storage,
    frame() { frames.shift()?.(); },
    expire() { timeout?.(); },
    retry() { documentListeners.click({ target: { id: 'aila-startup-retry' }, preventDefault() {} }); },
    get reloaded() { return reloaded; },
    get replaced() { return replaced; },
  };
}

test('il viewport di iPadOS deve essere visibile, avere dimensioni e stabilizzarsi prima di montare Compose', () => {
  const s = startup({ userAgent: 'Macintosh', maxTouchPoints: 5 });
  const viewport = s.nodes['aila-viewport'];
  let starts = 0;
  s.window.__ailaStartViewport(() => starts++);
  s.frame();
  s.document.hidden = true;
  viewport.clientWidth = 834;
  viewport.clientHeight = 1194;
  s.frame();
  s.frame();
  assert.equal(starts, 0);
  s.document.hidden = false;
  s.frame();
  viewport.clientHeight = 1100;
  s.frame();
  assert.equal(starts, 0);
  s.frame();
  s.frame();
  assert.equal(starts, 1);
});

test('i browser non iOS avviano Compose subito', () => {
  const s = startup({ userAgent: 'Macintosh' });
  let started = false;
  s.window.__ailaStartViewport(() => { started = true; });
  assert.equal(started, true);
});

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

test('ripulisce il vecchio flag aila-grafica-ridotta senza ridirigere la pagina', () => {
  const s = startup({ userAgent: 'iPhone', saved: [['aila-grafica-ridotta', '1']] });
  assert.equal(s.storage.has('aila-grafica-ridotta'), false);
  s.windowListeners.unhandledrejection({ reason: new Error("Can't wrap nullptr") });
  assert.equal(s.replaced, undefined);
  assert.match(s.nodes['aila-startup-error'].textContent, /Can't wrap nullptr/);
});

test('avvio compatibile separa compileStreaming e instantiate e ripristina al termine', async () => {
  const calls = [];
  const wasm = {
    async instantiateStreaming() { calls.push('streaming'); },
    async compileStreaming() { calls.push('compile'); return 'mod'; },
    async instantiate(mod, imports) { calls.push(['instantiate', mod, imports]); return 'inst'; },
  };
  const original = wasm.instantiateStreaming;
  const s = startup({ userAgent: 'iPad', search: '?avvio=compatibile', wasm });
  const res = await wasm.instantiateStreaming(Promise.resolve({ url: 'https://aila.test/abc.wasm' }), { imp: 1 });
  assert.equal(res.module, 'mod');
  assert.equal(res.instance, 'inst');
  assert.equal(calls[0], 'compile');
  assert.equal(calls[1][0], 'instantiate');
  s.window.__ailaStartupReady();
  assert.equal(wasm.instantiateStreaming, original);
});
