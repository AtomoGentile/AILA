// Applica wasm-tail-call-loader.cjs ai moduli generati da Skiko e Kotlin/Wasm per evitare
// la corruzione dei float nelle tail call JS -> Wasm di Safari/WebKit (WebKit bug 329421 / CMP-10986),
// e verifica sul bundle finale (dopo Terser) che nessuna tail call esposta sia sopravvissuta.
;(function configureWebKitWasmTailCallWorkaround(config) {
  const fs = require('fs');
  const path = require('path');

  function findLoader(startDir) {
    let dir = startDir;
    for (let i = 0; i < 10; i++) {
      const candidate = path.join(dir, 'webApp', 'webpack', 'wasm-tail-call-loader.cjs');
      if (fs.existsSync(candidate)) return candidate;
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
    throw new Error('Impossibile trovare webApp/webpack/wasm-tail-call-loader.cjs partendo da ' + startDir);
  }

  const loaderPath = findLoader(__dirname);
  config.module = config.module || {};
  config.module.rules = config.module.rules || [];
  config.module.rules.push({
    test: /(?:^|[\\/])(?:skiko\.mjs|[^\\/]+\.import-object\.mjs|[^\\/]+\.uninstantiated\.mjs)$/,
    use: [{ loader: loaderPath }],
  });

  // Controllo post-minificazione: fallisce la build se Terser o un aggiornamento di Skiko/Kotlin
  // dovesse rimuovere i blocchi try/catch o cambiare la firma dei wrapper senza protezione.
  config.plugins = config.plugins || [];
  config.plugins.push({
    apply(compiler) {
      const { Compilation } = compiler.webpack;
      compiler.hooks.thisCompilation.tap('VerifyWebKitWasmTailCallGuard', (compilation) => {
        compilation.hooks.processAssets.tap(
          { name: 'VerifyWebKitWasmTailCallGuard', stage: Compilation.PROCESS_ASSETS_STAGE_REPORT },
          (assets) => {
            const bundleName = Object.keys(assets).find((name) => /(?:^|\/)aila\.js$/.test(name));
            if (!bundleName) return;
            const code = assets[bundleName].source().toString();

            // Nota: in produzione Webpack/Terser rinomina `loadedWasm` (es. `k._.org_jetbrains_skia_...`),
            // quindi il controllo cerca la proprieta' `._.org_jetbrains_skia_` indipendentemente dal nome mangled.
            const unguardedSkiko =
              code.match(/\(\.\.\.[$\w]+\)\s*=>\s*\(\s*[$\w]+\s*=\s*[$\w]+\._(?:\.|\[["'])org_jetbrains_skia_/g) || [];
            if (unguardedSkiko.length > 0) {
              compilation.errors.push(
                new Error(
                  `[VerifyWebKitWasmTailCallGuard] Trovati ${unguardedSkiko.length} wrapper Skiko in tail-call non protetti in ${bundleName} (WebKit bug 329421).`
                )
              );
            }

            const guardedSkiko =
              code.match(/try\s*\{\s*return\s*\(\s*[$\w]+\s*=\s*[$\w]+\._(?:\.|\[["'])org_jetbrains_skia_/g) || [];
            if (guardedSkiko.length < 100) {
              compilation.errors.push(
                new Error(
                  `[VerifyWebKitWasmTailCallGuard] Attesi almeno 100 wrapper Skiko protetti in ${bundleName}, trovati ${guardedSkiko.length}.`
                )
              );
            }

            const unguardedClosures =
              code.match(/__convertKotlinClosureToJsClosure_\d+["']\s*:\s*\([^)]*\)\s*=>\s*[$\w]+\([^,]+,\s*(?:\([^)]*\)|[$\w]+)\s*=>\s*[$\w]+\(/g) || [];
            if (unguardedClosures.length > 0) {
              compilation.errors.push(
                new Error(
                  `[VerifyWebKitWasmTailCallGuard] Trovati ${unguardedClosures.length} trampolini Kotlin/Wasm in tail-call non protetti in ${bundleName}.`
                )
              );
            }

            const guardedClosures =
              code.match(/__convertKotlinClosureToJsClosure_\d+["']\s*:\s*\([^)]*\)\s*=>\s*[$\w]+\([^,]+,\s*(?:\([^)]*\)|[$\w]+)\s*=>\s*\{\s*try\s*\{\s*return\s+[$\w]+\(/g) || [];
            if (guardedClosures.length < 1) {
              compilation.errors.push(
                new Error(
                  `[VerifyWebKitWasmTailCallGuard] Nessun trampolino Kotlin/Wasm protetto trovato in ${bundleName}.`
                )
              );
            }
          }
        );
      });
    },
  });
})(config);
