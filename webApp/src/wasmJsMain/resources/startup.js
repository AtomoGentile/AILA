// Resta operativo anche se il runtime WebAssembly non arriva a eseguire main().
(function () {
  'use strict';
  var ready = false;
  var timer = setTimeout(function () {
    if (ready) return;
    var splash = document.getElementById('aila-splash');
    var recovery = document.getElementById('aila-startup-recovery');
    if (!splash || !recovery) return;
    splash.classList.add('startup-slow');
    recovery.hidden = false;
  }, 20000);

  window.__ailaStartupReady = function () {
    ready = true;
    clearTimeout(timer);
  };

  document.getElementById('aila-startup-retry').addEventListener('click', function () {
    // Nessuna cancellazione di sessione, PDF o altri dati locali.
    location.reload();
  });
})();
