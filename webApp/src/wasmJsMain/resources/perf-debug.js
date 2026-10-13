// Fluidita' della PWA: limite alla risoluzione del disegno, accessibilita' DOM spenta e, su
// richiesta, la diagnosi (fps e blocchi del thread).
//
// ?perf=1  mostra in alto a destra i fotogrammi al secondo, il fotogramma piu' lungo dell'ultimo
//          secondo e quanto tempo il thread principale e' rimasto bloccato (attivita' > 50 ms).
//          Resta attiva finche' non si apre con ?perf=0.
// ?dpr=2   limita la risoluzione del disegno a 2 pixel per punto (sugli iPhone sono 3): meno pixel
//          da riempire a ogni fotogramma, testo appena meno nitido. ?dpr=0 toglie il limite.
//          Senza scelta esplicita il limite e' 2 (vedi DEFAULT_DPR_CAP): e' il rimedio piu' efficace
//          contro gli scatti sugli iPhone, che hanno 3 pixel per punto = 2,25 volte i pixel.
//          Va letta prima di aila.js: per questo lo script e' caricato senza "defer".
// ?a11y=1  riaccende la copia DOM dell'albero di accessibilita' (spenta di default perche' costa
//          a ogni fotogramma, vedi main.kt). ?a11y=0 la rispegne. Resta salvata sul dispositivo.
(function () {
  'use strict';
  var DEFAULT_DPR_CAP = 2;
  var perf = false;
  var dprCap = DEFAULT_DPR_CAP;
  window.__ailaA11y = false;
  try {
    var a = /[?&]a11y=([01])/.exec(location.search);
    if (a) localStorage.setItem('aila-a11y', a[1]);
    window.__ailaA11y = localStorage.getItem('aila-a11y') === '1';
  } catch (err) {
    window.__ailaA11y = /[?&]a11y=1/.test(location.search);
  }
  try {
    var q = /[?&]perf=([01])/.exec(location.search);
    if (q) localStorage.setItem('aila-perf', q[1]);
    perf = localStorage.getItem('aila-perf') === '1';
    var d = /[?&]dpr=([0-9.]+)/.exec(location.search);
    if (d) localStorage.setItem('aila-dpr', d[1]);
    var stored = localStorage.getItem('aila-dpr');
    if (stored !== null) dprCap = parseFloat(stored) || 0;
  } catch (err) {
    perf = /[?&]perf=1/.test(location.search);
  }

  if (dprCap > 0) {
    var real = window.devicePixelRatio || 1;
    if (real > dprCap) {
      try {
        Object.defineProperty(window, 'devicePixelRatio', { configurable: true, get: function () { return dprCap; } });
      } catch (err) { /* il browser non lo permette: si resta alla risoluzione piena */ }
    }
  }

  if (!perf) return;

  var frames = 0;
  var worst = 0;
  var blocked = 0;
  var last = performance.now();
  var windowStart = last;
  var el = null;

  function ensure() {
    if (el && el.isConnected) return;
    el = document.createElement('div');
    var s = el.style;
    s.position = 'fixed'; s.right = '0'; s.top = '0'; s.zIndex = '2147483647';
    s.padding = 'env(safe-area-inset-top) 6px 3px'; s.pointerEvents = 'none';
    s.font = '10px/1.3 ui-monospace, Menlo, monospace'; s.color = '#0f0';
    s.background = 'rgba(0,0,0,0.78)'; s.whiteSpace = 'pre';
    (document.body || document.documentElement).appendChild(el);
  }

  function tick(now) {
    var dt = now - last;
    last = now;
    frames++;
    if (dt > worst) worst = dt;
    if (now - windowStart >= 1000) {
      ensure();
      var dpr = window.devicePixelRatio || 1;
      el.textContent = Math.round(frames * 1000 / (now - windowStart)) + ' fps  max ' + Math.round(worst) +
        ' ms\nbloccato ' + Math.round(blocked) + ' ms  dpr ' + dpr;
      frames = 0; worst = 0; blocked = 0; windowStart = now;
    }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);

  try {
    new PerformanceObserver(function (list) {
      list.getEntries().forEach(function (e) { blocked += e.duration; });
    }).observe({ entryTypes: ['longtask'] });
  } catch (err) { /* longtask non esiste su Safari: restano fps e fotogramma piu' lungo */ }
})();
