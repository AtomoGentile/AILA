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

function startup() {
  let timeout, retry, reloaded = false;
  const classes = new Set();
  const nodes = {
    'aila-splash': { classList: { add: name => classes.add(name) } },
    'aila-startup-recovery': { hidden: true },
    'aila-startup-retry': { addEventListener(type, fn) { retry = fn; } },
  };
  const window = {};
  vm.runInNewContext(readFileSync(join(__dirname, '../src/wasmJsMain/resources/startup.js'), 'utf8'), {
    window, document: { getElementById: id => nodes[id] },
    location: { reload() { reloaded = true; } },
    setTimeout(fn) { timeout = fn; return 1; }, clearTimeout() { timeout = null; },
  });
  return { window, nodes, classes, expire() { timeout?.(); }, retry() { retry(); }, get reloaded() { return reloaded; } };
}

test('runtime bloccato: mostra un recupero e ricarica solo su richiesta', () => {
  const s = startup();
  s.expire();
  assert.equal(s.nodes['aila-startup-recovery'].hidden, false);
  assert.equal(s.classes.has('startup-slow'), true);
  assert.equal(s.reloaded, false);
  s.retry();
  assert.equal(s.reloaded, true);
});

test('avvio riuscito: non compare la schermata di recupero', () => {
  const s = startup();
  s.window.__ailaStartupReady();
  s.expire();
  assert.equal(s.nodes['aila-startup-recovery'].hidden, true);
});
