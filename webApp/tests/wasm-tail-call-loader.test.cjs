const { test } = require('node:test');
const assert = require('node:assert/strict');
const { guardWasmTailCalls } = require('../webpack/wasm-tail-call-loader.cjs');

test('protegge tutti i wrapper esportati di skiko.mjs dalla tail-call optimization WebKit', () => {
  const input = [
    'export const loadedWasm = { _: {} };',
    'export let org_jetbrains_skia_Shader__1nMakeRadialGradient = (...a) => (org_jetbrains_skia_Shader__1nMakeRadialGradient = loadedWasm._["org_jetbrains_skia_Shader__1nMakeRadialGradient"])(...a)',
    "export let malloc = (...args) => (malloc = loadedWasm._['malloc'])(...args);",
    'export let skia_memSetFloat = (...e) => (skia_memSetFloat = loadedWasm._.skia_memSetFloat)(...e)',
  ].join('\n');

  const out = guardWasmTailCalls(input);
  assert.match(
    out,
    /export let org_jetbrains_skia_Shader__1nMakeRadialGradient = \(\.\.\.a\) => \{ try \{ return \(org_jetbrains_skia_Shader__1nMakeRadialGradient = loadedWasm\._\["org_jetbrains_skia_Shader__1nMakeRadialGradient"\]\)\(\.\.\.a\); \} catch \(e\) \{ throw e; \} \}/
  );
  assert.match(
    out,
    /export let malloc = \(\.\.\.args\) => \{ try \{ return \(malloc = loadedWasm\._\['malloc'\]\)\(\.\.\.args\); \} catch \(e\) \{ throw e; \} \};/
  );
  assert.match(
    out,
    /export let skia_memSetFloat = \(\.\.\.e\) => \{ try \{ return \(skia_memSetFloat = loadedWasm\._\["skia_memSetFloat"\]\)\(\.\.\.e\); \} catch \(e\) \{ throw e; \} \}/
  );
  assert.equal(/\(\.\.\.\w+\)\s*=>\s*\(\w+\s*=\s*loadedWasm\._/.test(out), false);
});

test('protegge i trampolini delle closure Kotlin/Wasm e __callJsClosure in import-object.mjs', () => {
  const input = [
    "    'kotlin.js.__convertKotlinClosureToJsClosure_0' : (f, trampoline) => getCachedJsObject(f, () => trampoline(f, )),",
    "    'kotlin.js.__convertKotlinClosureToJsClosure_1' : (f, trampoline) => getCachedJsObject(f, (p0) => trampoline(f, p0)),",
    "    'org.w3c.dom.__convertKotlinClosureToJsClosure_5' : (f, trampoline) => getCachedJsObject(f, (p0, p1, p2, p3, p4) => trampoline(f, p0, p1, p2, p3, p4)),",
    "    'kotlin.js.__callJsClosure_((Js)->Unit)' : (f, p0) => f(p0),",
  ].join('\n');

  const out = guardWasmTailCalls(input);
  assert.match(out, /getCachedJsObject\(f, \(\) => \{ try \{ return trampoline\(f, \); \} catch \(e\) \{ throw e; \} \}\)/);
  assert.match(out, /getCachedJsObject\(f, \(p0\) => \{ try \{ return trampoline\(f, p0\); \} catch \(e\) \{ throw e; \} \}\)/);
  assert.match(out, /getCachedJsObject\(f, \(p0, p1, p2, p3, p4\) => \{ try \{ return trampoline\(f, p0, p1, p2, p3, p4\); \} catch \(e\) \{ throw e; \} \}\)/);
  assert.match(out, /'kotlin\.js\.__callJsClosure_\(\(Js\)->Unit\)' : \(f, p0\) => \{ try \{ return f\(p0\); \} catch \(e\) \{ throw e; \} \},/);
});

test('mantiene invariato il comportamento dei wrapper protetti (valori ed eccezioni)', () => {
  const wrapped = guardWasmTailCalls(
    'let foo = (...a) => (foo = loadedWasm._["foo"])(...a)'
      .replace(/^let /, 'export let ')
  ).replace(/^export /, '');

  const calls = [];
  const fn = new Function(
    'loadedWasm',
    `${wrapped}; return { call: (...x) => foo(...x), getRef: () => foo };`
  );
  const target = (a, b) => {
    calls.push([a, b]);
    if (a < 0) throw new RangeError('neg');
    return a + b;
  };
  const api = fn({ _: { foo: target } });
  assert.equal(api.call(1.5, 2.25), 3.75);
  assert.equal(api.getRef(), target);
  assert.throws(() => api.call(-1, 2), RangeError);
  assert.deepEqual(calls, [[1.5, 2.25], [-1, 2]]);
});
