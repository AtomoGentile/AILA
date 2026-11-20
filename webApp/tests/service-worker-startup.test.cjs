const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const source = readFileSync(join(__dirname, '../src/wasmJsMain/resources/sw.js'), 'utf8');
const origin = 'https://aila.test';
const scriptUrl = origin + '/aila.js';
const oldWasm = origin + '/aaaaaaaaaaaaaaaa.wasm';
const newWasm = origin + '/bbbbbbbbbbbbbbbb.wasm';

function response(body, type, version) {
  return {
    status: 200,
    type: 'basic',
    redirected: false,
    headers: new Headers({ 'content-type': type, etag: version }),
    async text() { return body; },
    clone() { return response(body, type, version); },
  };
}

function worker(cachedEntries, networkEntries) {
  const listeners = {};
  const entries = new Map(cachedEntries);
  const cache = {
    async match(request) { return entries.get(request.url ?? request); },
    async put(request, value) { entries.set(request.url ?? request, value); },
  };
  vm.runInNewContext(source, {
    URL,
    caches: { match: cache.match, open: async () => cache },
    fetch: async request => {
      const url = request.url ?? request;
      if (!networkEntries.has(url)) throw new Error('offline: ' + url);
      return networkEntries.get(url);
    },
    self: {
      registration: { scope: origin + '/' },
      clients: { matchAll: async () => [] },
      addEventListener(type, listener) { listeners[type] = listener; },
    },
  });
  return {
    entries,
    async load(url) {
      const pending = [];
      let result;
      listeners.fetch({
        request: new Request(url),
        waitUntil(promise) { pending.push(promise); },
        respondWith(promise) { result = promise; },
      });
      const value = await result;
      await Promise.all(pending);
      return value;
    },
  };
}

const oldScript = response('const wasm = "aaaaaaaaaaaaaaaa.wasm";', 'application/javascript', 'old');
const newScript = response('const wasm = "bbbbbbbbbbbbbbbb.wasm";', 'application/javascript', 'new');
const wasm = response('wasm', 'application/wasm', 'wasm');
const html = response('<!doctype html>', 'text/html', 'html');

test('a bundle whose Wasm is missing uses the complete online version on the same launch', async () => {
  const app = worker([[scriptUrl, oldScript]], new Map([[scriptUrl, newScript], [newWasm, wasm]]));
  assert.equal(await app.load(scriptUrl), newScript);
  assert.equal(await app.load(newWasm), app.entries.get(newWasm));
});

test('offline startup uses the previous bundle while its Wasm is cached', async () => {
  const app = worker([[scriptUrl, oldScript], [oldWasm, wasm]], new Map());
  assert.equal(await app.load(scriptUrl), oldScript);
});

test('an HTML fallback cached at a Wasm URL is replaced by the binary', async () => {
  const app = worker([[newWasm, html]], new Map([[newWasm, wasm]]));
  assert.equal(await app.load(newWasm), wasm);
  assert.equal(app.entries.get(newWasm).headers.get('content-type'), 'application/wasm');
});

test('an HTML fallback from the server is never saved as immutable Wasm', async () => {
  const app = worker([], new Map([[newWasm, html]]));
  assert.equal(await app.load(newWasm), html);
  assert.equal(app.entries.has(newWasm), false);
});
