(function () {
  'use strict';
  const state = document.getElementById('stato');
  const button = document.getElementById('verifica');
  const results = document.getElementById('risultati');
  const bundle = 'aila.js?boot=3';

  async function check(label, run) {
    const item = document.createElement('li');
    item.textContent = label + ' — in corso';
    results.append(item);
    let timer;
    try {
      // Lascia al browser il tempo di mostrare la fase prima della prova successiva.
      await new Promise(resolve => setTimeout(resolve, 100));
      const controller = new AbortController();
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => {
          reject(new DOMException('Tempo scaduto', 'TimeoutError'));
          controller.abort();
        }, 25000);
      });
      const value = await Promise.race([run(controller.signal), timeout]);
      item.textContent = label + ' — riuscito';
      return value;
    } catch (error) {
      const name = error && error.name;
      const category = typeof name === 'string' && /^[A-Za-z]{1,32}Error$/.test(name) ? ' (' + name + ')' : '';
      item.textContent = label + ' — errore' + category;
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  async function fetchAsset(path, mime, signal) {
    const response = await fetch(path, { cache: 'no-store', signal });
    if (!response.ok) throw new DOMException('Risposta HTTP ' + response.status, 'NetworkError');
    if ((response.headers.get('content-type') || '').split(';')[0].trim() !== mime) {
      throw new DOMException('Tipo di risposta inatteso', 'DataError');
    }
    return response;
  }

  button.addEventListener('click', async () => {
    button.disabled = true;
    results.replaceChildren();
    state.textContent = 'Verifica in corso.';
    try {
      const modules = await check('Download del programma AILA', async signal => {
        const source = await (await fetchAsset(bundle, 'application/javascript', signal)).text();
        const matches = [...source.matchAll(/["'`]([0-9a-f]{16,}\.wasm)["'`]/g)];
        const names = [...new Set(matches.map(match => match[1]))];
        if (!names.length) throw new DOMException('Moduli WebAssembly assenti', 'DataError');
        return names;
      });
      for (const [index, name] of modules.entries()) {
        await check('WebAssembly ' + (index + 1) + '/' + modules.length, async signal => {
          const wasm = await fetchAsset(name, 'application/wasm', signal);
          await WebAssembly.compileStreaming(wasm, { builtins: ['js-string'], importedStringConstants: "'" });
        });
      }
      await check('Grafica WebGL', () => {
        const canvas = document.createElement('canvas');
        const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
        if (!gl) throw new DOMException('WebGL non disponibile', 'NotSupportedError');
        gl.getExtension('WEBGL_lose_context')?.loseContext();
      });
      await check('Archivio locale IndexedDB', () => indexedDB.databases());
      state.textContent = 'Verifica completata.';
    } catch (_) {
      state.textContent = 'Verifica interrotta. L’ultima riga indica la fase che si è fermata.';
    } finally {
      button.disabled = false;
    }
  });

  state.textContent = 'JavaScript pronto. Tocca «Esegui la verifica».';
  button.disabled = false;
})();
