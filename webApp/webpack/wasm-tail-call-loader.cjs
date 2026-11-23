'use strict';

// Workaround per WebKit bug 329421 / Compose Multiplatform CMP-10986:
// su iOS/iPadOS (Safari e WKWebView) JavaScriptCore applica la Proper Tail Call optimization
// alle funzioni arrow in strict mode che invocano direttamente un export WebAssembly, corrompendo
// gli argomenti in virgola mobile (float/double).
//
// In Skiko tutti i ~1000 wrapper esportati da skiko.mjs hanno forma:
//   export let foo = (...a) => (foo = loadedWasm._["foo"])(...a)
// e in Kotlin/Wasm i trampolini delle closure JS->Wasm hanno forma:
//   getCachedJsObject(f, (p0, ...) => trampoline(f, p0, ...))
//
// Poiche' aila.wasm importa i wrapper di skiko.mjs per valore prima che awaitSkiko risolva,
// OGNI chiamata Skiko dal modulo Wasm passa da questi wrapper per tutta la durata della sessione.
// Inserire la chiamata dentro `try { return ...; } catch (e) { throw e; }` disabilita la tail-call
// position secondo ECMA-262 (HasCallInTailPosition su TryStatement e' sempre false).

const SKIKO_EXPORT_TAIL_CALL =
  /export\s+let\s+([$\w]+)\s*=\s*\(\s*\.\.\.([$\w]+)\s*\)\s*=>\s*\(\s*\1\s*=\s*loadedWasm\._(?:\[\s*(["'])\1\3\s*\]|\.\1)\s*\)\s*\(\s*\.\.\.\2\s*\)/g;

const KOTLIN_CLOSURE_TAIL_CALL =
  /getCachedJsObject\(\s*([$\w]+)\s*,\s*(\([^)]*\))\s*=>\s*(trampoline\([^)]*\))\s*\)/g;

const JS_CLOSURE_CALL_TAIL_CALL =
  /(['"][^'"]*__callJsClosure_[^'"]*['"]\s*:\s*)(\([^)]*\))\s*=>\s*([$\w]+\([^)]*\))/g;

function guardWasmTailCalls(source) {
  let out = source;
  out = out.replace(
    SKIKO_EXPORT_TAIL_CALL,
    (_, name, args, quote) => {
      const q = quote || '"';
      return `export let ${name} = (...${args}) => { try { return (${name} = loadedWasm._[${q}${name}${q}])(...${args}); } catch (e) { throw e; } }`;
    }
  );
  out = out.replace(
    KOTLIN_CLOSURE_TAIL_CALL,
    (_, fnVar, params, call) =>
      `getCachedJsObject(${fnVar}, ${params} => { try { return ${call}; } catch (e) { throw e; } })`
  );
  out = out.replace(
    JS_CLOSURE_CALL_TAIL_CALL,
    (_, prefix, params, call) =>
      `${prefix}${params} => { try { return ${call}; } catch (e) { throw e; } }`
  );
  return out;
}

module.exports = function wasmTailCallLoader(source) {
  return guardWasmTailCalls(source);
};
module.exports.guardWasmTailCalls = guardWasmTailCalls;
