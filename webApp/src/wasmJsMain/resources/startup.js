// Segue l'avvio del runtime. Il recupero in HTML/CSS resta disponibile anche senza questo script.
(function () {
  'use strict';
  var ready = false;
  var slow = false;
  var errorMessage = '';
  var phase = 'Script di avvio caricato. Attendo il bundle.';
  var trace;
  var restoreStreaming;

  if (new URLSearchParams(location.search).get('avvio') === 'compatibile') {
    var phases = [];
    trace = window.__ailaStartupTrace = function (message) {
      phases.push(message);
      try {
        localStorage.setItem('aila-startup-trace', JSON.stringify(phases.slice(-16)));
      } catch (err) { /* Il messaggio resta comunque visibile nella schermata di avvio. */ }
    };
    trace(phase);

    // In questo avvio separiamo la compilazione dall'istanziazione. È lo stesso modulo
    // verificato dalla pagina di diagnosi, con gli import forniti dal bundle di produzione.
    var wasm = window.WebAssembly;
    if (wasm && typeof wasm.compileStreaming === 'function' && typeof wasm.instantiateStreaming === 'function') {
      var originalStreaming = wasm.instantiateStreaming;
      var compatibleStreaming = async function (source, imports, options) {
        var response = await source;
        var file = new URL(response.url, location.href).pathname.split('/').pop() || 'WebAssembly';
        trace('Compilazione: ' + file);
        var module = await wasm.compileStreaming(response, options);
        trace('Istanziazione: ' + file);
        var instance = await wasm.instantiate(module, imports);
        trace('Modulo avviato: ' + file);
        return { module: module, instance: instance };
      };
      wasm.instantiateStreaming = compatibleStreaming;
      restoreStreaming = function () {
        if (wasm.instantiateStreaming === compatibleStreaming) wasm.instantiateStreaming = originalStreaming;
      };
    }
  }

  function render() {
    if (ready) return;
    var stage = document.getElementById('diagnosi-avvio');
    if (stage && !stage.hasAttribute('data-kotlin')) stage.textContent = phase;
    var splash = document.getElementById('aila-splash');
    if (splash && slow) splash.classList.add('startup-slow');
    var error = document.getElementById('aila-startup-error');
    if (error && errorMessage) {
      error.textContent = errorMessage;
      error.hidden = false;
    }
  }

  function fail(message) {
    if (ready) return;
    slow = true;
    errorMessage = message;
    if (trace) trace(message);
    render();
  }

  var timer = setTimeout(function () {
    slow = true;
    render();
  }, 20000);

  window.__ailaStartupReady = function () {
    ready = true;
    clearTimeout(timer);
    if (trace) trace('Interfaccia avviata.');
    if (restoreStreaming) restoreStreaming();
  };

  window.addEventListener('load', function (event) {
    if (event.target.id !== 'aila-app-script') return;
    phase = 'Bundle caricato. Attendo WebAssembly.';
    if (trace) trace(phase);
    render();
  }, true);
  window.addEventListener('error', function (event) {
    if (event.target.id === 'aila-app-script') {
      fail('Il browser non ha caricato il bundle.');
    } else if (event.filename && /\/aila\.js(?:[?#]|$)/.test(event.filename)) {
      runtimeFailure(event.error);
    }
  }, true);
  window.addEventListener('unhandledrejection', function (event) {
    runtimeFailure(event.reason);
  });

  function runtimeFailure(error) {
    // Riporta solo la categoria locale. I messaggi delle eccezioni possono contenere URL o dati.
    var name = error && error.name;
    var category = typeof name === 'string' && /^[A-Za-z]{1,32}Error$/.test(name) ? ' (' + name + ')' : '';
    fail('Errore durante l’avvio' + category + '.');
  }

  document.addEventListener('click', function (event) {
    if (event.target.id !== 'aila-startup-retry') return;
    event.preventDefault();
    location.reload();
  });
  document.addEventListener('DOMContentLoaded', render);
  render();
})();
