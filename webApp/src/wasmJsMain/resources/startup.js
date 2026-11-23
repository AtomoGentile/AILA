// Segue l'avvio del runtime. Il recupero in HTML/CSS resta disponibile anche senza questo script.
(function () {
  'use strict';
  var ready = false;
  var slow = false;
  var errorMessage = '';
  var phase = 'Script di avvio caricato. Attendo il bundle.';
  var trace;
  var restoreStreaming;
  var params = new URLSearchParams(location.search);
  var compatible = params.get('avvio') === 'compatibile';
  var ua = navigator.userAgent || '';
  var ios = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);

  // Ripulisce il vecchio flag di emergenza (DPR 1) salvato dalla build precedente.
  try { localStorage.removeItem('aila-grafica-ridotta'); } catch (_) {}

  // WebKit può completare il layout del viewport dopo l'avvio del runtime, per esempio
  // al rientro nella PWA installata o durante una rotazione. Skiko alloca il framebuffer
  // appena si monta Compose: attendiamo due misure valide e uguali prima di montarlo.
  window.__ailaStartViewport = function (start) {
    if (!ios) {
      start();
      return;
    }
    var viewport = document.getElementById('aila-viewport');
    if (!viewport) {
      start();
      return;
    }
    var lastWidth = 0;
    var lastHeight = 0;
    function check() {
      var width = viewport.clientWidth;
      var height = viewport.clientHeight;
      if (!document.hidden && width > 0 && height > 0 && width === lastWidth && height === lastHeight) {
        start();
        return;
      }
      lastWidth = document.hidden ? 0 : width;
      lastHeight = document.hidden ? 0 : height;
      requestAnimationFrame(check);
    }
    requestAnimationFrame(check);
  };

  if (compatible) {
    var phases = [];
    trace = window.__ailaStartupTrace = function (message) {
      phases.push(message);
      try {
        localStorage.setItem('aila-startup-trace', JSON.stringify(phases.slice(-16)));
      } catch (err) { /* Il messaggio resta comunque visibile nella schermata di avvio. */ }
    };
    trace(phase);

    var wasm = window.WebAssembly;
    if (wasm && typeof wasm.instantiateStreaming === 'function' && typeof wasm.compileStreaming === 'function') {
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
    if (errorMessage && message === 'Errore durante l’avvio.') return;
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
      runtimeFailure(event.error, event.message);
    }
  }, true);
  window.addEventListener('unhandledrejection', function (event) {
    runtimeFailure(event.reason);
  });

  function errorDetail(value) {
    if (typeof value !== 'string') return '';
    return value.split(/[\r\n]/)[0]
      .replace(/\b(?:https?:\/\/|blob:|data:|file:\/\/)[^\s"'\x60]+/gi, '[indirizzo]')
      .replace(/\b[A-Za-z0-9_-]{24,}(?:\.[A-Za-z0-9_-]+)*/g, '[valore]')
      .trim()
      .slice(0, 180);
  }

  function runtimeFailure(error, eventMessage) {
    var name = error && error.name;
    var category = typeof name === 'string' && /^(?:Error|[A-Za-z]{1,32}(?:Error|Exception))$/.test(name)
      ? ' (' + name + ')' : '';
    var detail = errorDetail(error && error.message) || errorDetail(eventMessage) || errorDetail(error);
    var cause = errorDetail(error && error.cause && error.cause.message);
    if (cause && cause !== detail) detail += (detail ? ' · ' : '') + cause;
    if (/can't wrap nullptr/i.test(detail)) {
      var stack = error && typeof error.stack === 'string' ? error.stack : '';
      var frames = Array.from(stack.matchAll(/wasm[- ]function\[(\d+)\]/gi), function (match) { return match[1]; });
      if (frames.length) detail += ' · Wasm ' + frames.slice(0, 6).join('/');
    }
    fail('Errore durante l’avvio' + category + (detail ? ': ' + detail : '.'));
  }

  document.addEventListener('click', function (event) {
    if (event.target.id !== 'aila-startup-retry') return;
    event.preventDefault();
    if (!compatible && ios) {
      var retryUrl = new URL(location.href);
      retryUrl.searchParams.set('avvio', 'compatibile');
      location.replace(retryUrl.href);
    } else {
      location.reload();
    }
  });
  document.addEventListener('DOMContentLoaded', render);
  render();
})();
