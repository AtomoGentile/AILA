// Tastiera su iOS fuori da Safari (Chrome/Firefox/Edge per iOS e PWA installata).
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
  var standalone = navigator.standalone === true ||
    (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);
  var otherBrowser = /CriOS|FxiOS|EdgiOS|OPiOS|GSA\//.test(ua);
  // Safari in una scheda funziona gia' cosi' com'e': non si tocca.
  if (!standalone && !otherBrowser) return;

  var TAP_SLOP_PX = 12;
  var TAP_MAX_MS = 600;
  var decoy = null;
  var root = null;
  var down = null;

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
    if (!r) return;
    var box = textboxAt(r, e.clientX, e.clientY);
    if (!box) return;

    // Gia' in modifica proprio su questo campo (spostare il cursore): non si ruba il fuoco.
    var active = r.activeElement;
    if (isEditable(active) && contains(active.getBoundingClientRect(), e.clientX, e.clientY, 2)) return;

    pending = { box: box, t: Date.now() };
    arm(box);
  }, { capture: true, passive: true });
})();
