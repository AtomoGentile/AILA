// Tastiera su iOS (Safari, Chrome/Firefox/Edge per iOS e PWA installata).
//
// Compose mette a fuoco il suo campo di testo nascosto DOPO aver elaborato il tocco (al
// fotogramma successivo), non dentro il gesto. Safari in una scheda lo tollera; i WKWebView
// (Chrome iOS, app installata) aprono la tastiera solo se focus() parte durante il tocco, e
// altrimenti il campo prende il fuoco ma la tastiera resta chiusa.
//
// Rimedio: su un tocco rapido sopra un campo di testo si mette a fuoco, subito e dentro il
// gesto, un <input> di appoggio. La tastiera si apre; quando Compose mette a fuoco il suo campo
// la trova gia' aperta e la tiene. Il campo di Compose si riconosce dall'albero di accessibilita'
// (ruolo "textbox"): sugli altri tocchi non succede nulla, quindi nessuna tastiera a ogni pulsante.
(function () {
  'use strict';

  var ua = navigator.userAgent || '';
  var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  if (!isIOS) return;

  var TAP_SLOP_PX = 12;
  var TAP_MAX_MS = 600;
  var decoy = null;
  var root = null;
  var down = null;


  // Diagnosi: aprire l'app con ?kbddebug=1 (una volta sola: poi resta attiva finche' non si apre
  // con ?kbddebug=0). Scrive in alto cosa succede a ogni tocco su un campo di testo.
  var debug = false;
  try {
    var q = /[?&]kbddebug=([01])/.exec(location.search);
    if (q) localStorage.setItem('aila-kbddebug', q[1]);
    debug = localStorage.getItem('aila-kbddebug') === '1';
  } catch (err) { debug = !!/[?&]kbddebug=1/.test(location.search); }

  var logEl = null;
  var logLines = [];
  function log(msg) {
    if (!debug) return;
    if (!logEl) {
      logEl = document.createElement('pre');
      var ls = logEl.style;
      ls.position = 'fixed'; ls.left = '0'; ls.top = '0'; ls.right = '0'; ls.margin = '0';
      ls.padding = 'env(safe-area-inset-top) 6px 4px'; ls.zIndex = '2147483647';
      ls.pointerEvents = 'none'; ls.font = '10px/1.25 ui-monospace, Menlo, monospace';
      ls.color = '#0f0'; ls.background = 'rgba(0,0,0,0.78)'; ls.whiteSpace = 'pre-wrap';
      document.body.appendChild(logEl);
    }
    var t = new Date();
    logLines.push(('0' + t.getMinutes()).slice(-2) + ':' + ('0' + t.getSeconds()).slice(-2) + '.' +
      ('00' + t.getMilliseconds()).slice(-3) + ' ' + msg);
    if (logLines.length > 18) logLines.shift();
    logEl.textContent = logLines.join('\n');
  }
  function who(el) {
    if (!el) return 'null';
    return el.tagName + (el.getAttribute && el.getAttribute('role') ? '[' + el.getAttribute('role') + ']' : '');
  }
  function vv() {
    var v = window.visualViewport;
    return v ? Math.round(v.height) + 'x' + Math.round(v.width) : '?';
  }
  function snapshot(label) {
    var r = getRoot();
    log(label + ' doc=' + who(document.activeElement) + ' sr=' + (r ? who(r.activeElement) : 'noroot') + ' vv=' + vv());
  }
  if (debug) {
    window.addEventListener('DOMContentLoaded', function () {
      log('debug ok; ua=' + (/CriOS/.test(ua) ? 'Chrome' : /FxiOS/.test(ua) ? 'Firefox' : 'Safari/PWA') +
        ' standalone=' + (navigator.standalone === true) + ' vv=' + vv());
    });
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', function () { log('viewport resize vv=' + vv()); });
    }
    document.addEventListener('focusin', function (e) { log('focusin ' + who(e.target)); }, true);
    document.addEventListener('focusout', function (e) { log('focusout ' + who(e.target)); }, true);
  }

  function getDecoy() {
    if (decoy) return decoy;
    decoy = document.createElement('input');
    decoy.type = 'text';
    decoy.setAttribute('aria-hidden', 'true');
    decoy.setAttribute('tabindex', '-1');
    decoy.setAttribute('autocomplete', 'off');
    decoy.setAttribute('autocorrect', 'off');
    decoy.setAttribute('autocapitalize', 'off');
    decoy.setAttribute('spellcheck', 'false');
    var s = decoy.style;
    s.position = 'fixed';
    s.width = '1px';
    s.padding = '0';
    s.border = 'none';
    s.outline = 'none';
    s.background = 'transparent';
    s.color = 'transparent';
    s.caretColor = 'transparent';
    s.pointerEvents = 'none';
    s.zIndex = '-1';
    // 16px o piu': sotto, iOS ingrandisce la pagina al fuoco.
    s.fontSize = '20px';
    document.body.appendChild(decoy);
    return decoy;
  }

  // Radice shadow di Compose (contiene canvas, albero di accessibilita' e campo nascosto).
  function getRoot() {
    if (root && root.host.isConnected) return root;
    root = null;
    var divs = document.body.querySelectorAll('div');
    for (var i = 0; i < divs.length; i++) {
      if (divs[i].shadowRoot) { root = divs[i].shadowRoot; break; }
    }
    return root;
  }

  function contains(rect, x, y, slop) {
    return x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop && y <= rect.bottom + slop;
  }

  function textboxAt(r, x, y) {
    var boxes = r.querySelectorAll('[role="textbox"]');
    var best = null;
    var bestArea = Infinity;
    for (var i = 0; i < boxes.length; i++) {
      var rect = boxes[i].getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1 || !contains(rect, x, y, 0)) continue;
      var area = rect.width * rect.height;
      if (area < bestArea) { best = rect; bestArea = area; }
    }
    return best;
  }

  function isEditable(el) {
    return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
  }

  var pending = null;

  function arm(box) {
    var el = getDecoy();
    // Vicino al campo vero, cosi' iOS scorre la pagina come farebbe per quello.
    el.style.left = Math.max(0, box.left) + 'px';
    el.style.top = Math.max(0, box.top) + 'px';
    el.style.height = Math.max(1, box.height) + 'px';
    try { el.focus({ preventScroll: true }); } catch (err) { el.focus(); }
    snapshot('appoggio a fuoco ->');
    if (debug) {
      [100, 300, 700, 1500].forEach(function (ms) { setTimeout(function () { snapshot('+' + ms + 'ms'); }, ms); });
    }

    // Se Compose non ha preso il fuoco (tocco che non apre la tastiera), l'appoggio si toglie.
    setTimeout(function () {
      if (document.activeElement !== el) return;
      var rr = getRoot();
      if (rr && isEditable(rr.activeElement)) return;
      el.blur();
    }, 600);
  }

  // Dopo pointerup il browser sposta il fuoco sull'elemento toccato (il canvas di Compose) con
  // gli eventi mouse "di compatibilita'" e butterebbe via l'appoggio: il click arriva per ultimo,
  // sempre dentro lo stesso gesto, e lo rimette a fuoco.
  document.addEventListener('click', function () {
    var p = pending;
    pending = null;
    if (!p || Date.now() - p.t > 1000) return;
    var r = getRoot();
    if (r && isEditable(r.activeElement)) return;
    arm(p.box);
  }, { capture: true, passive: true });

  document.addEventListener('pointerdown', function (e) {
    down = e.pointerType === 'mouse' ? null : { x: e.clientX, y: e.clientY, t: Date.now() };
  }, { capture: true, passive: true });

  document.addEventListener('pointerup', function (e) {
    var d = down;
    down = null;
    if (!d || e.pointerType === 'mouse') return;
    if (Date.now() - d.t > TAP_MAX_MS) return;
    if (Math.abs(e.clientX - d.x) > TAP_SLOP_PX || Math.abs(e.clientY - d.y) > TAP_SLOP_PX) return;

    var r = getRoot();
    if (!r) { log('tap: nessuna radice shadow di Compose'); return; }
    var box = textboxAt(r, e.clientX, e.clientY);
    if (debug) {
      log('tap ' + Math.round(e.clientX) + ',' + Math.round(e.clientY) + ' textbox nel DOM=' +
        r.querySelectorAll('[role="textbox"]').length + ' sotto il dito=' + !!box);
    }
    if (!box) return;

    // Gia' in modifica proprio su questo campo (spostare il cursore): non si ruba il fuoco.
    var active = r.activeElement;
    if (isEditable(active) && contains(active.getBoundingClientRect(), e.clientX, e.clientY, 2)) return;

    pending = { box: box, t: Date.now() };
    arm(box);
  }, { capture: true, passive: true });
})();
