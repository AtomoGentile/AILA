// Segue l'avvio del runtime. Il recupero in HTML/CSS resta disponibile anche senza questo script.
(function () {
  'use strict';
  var ready = false;
  var slow = false;
  var errorMessage = '';
  var phase = 'Script di avvio caricato. Attendo il bundle.';

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
    slow = true;
    errorMessage = message;
    render();
  }

  var timer = setTimeout(function () {
    slow = true;
    render();
  }, 20000);

  window.__ailaStartupReady = function () {
    ready = true;
    clearTimeout(timer);
  };

  window.addEventListener('load', function (event) {
    if (event.target.id !== 'aila-app-script') return;
    phase = 'Bundle caricato. Attendo WebAssembly.';
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
