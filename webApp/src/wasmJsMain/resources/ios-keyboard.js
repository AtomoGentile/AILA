// Tastiera su iOS (Safari, Chrome/Firefox/Edge per iOS e PWA installata).
//
// Compose mette a fuoco il suo campo di testo nascosto DOPO aver elaborato il tocco (al
// fotogramma successivo), non dentro il gesto. Safari in una scheda lo tollera; i WKWebView
// (Chrome iOS, app installata) aprono la tastiera solo se focus() parte durante il tocco, e
// altrimenti il campo prende il fuoco ma la tastiera resta chiusa.
//
// Rimedio: su un tocco rapido sopra un campo di testo, al touchend (dentro il gesto) si mette a fuoco
// il campo nascosto di Compose (o, se non esiste ancora, un <input> di appoggio). La tastiera si apre; quando Compose mette a fuoco il suo campo
// la trova gia' aperta e la tiene. Il campo di Compose si riconosce dall'albero di accessibilita'
// (ruolo "textbox"): sugli altri tocchi non succede nulla, quindi nessuna tastiera a ogni pulsante.
// CAUSA TROVATA (iOS/Safari 27, WebKit 64ee64b): ogni nuova richiesta focus() SOSTITUISCE la precedente
// e conta come "senza interazione dell'utente" se non parte da un gesto. Compose chiama focus() sul
// campo nascosto anche dopo (requestAnimationFrame, a ogni updateState): quella chiamata annulla il
// fuoco dato nel gesto e la tastiera non compare. Compose 1.12 lo corregge (PR #3446); qui, restando
// alla 1.11, si ignorano le focus() su un campo che ha gia' il fuoco.
(function () {
  'use strict';

  var ua = navigator.userAgent || '';
  var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  if (!isIOS) return;

  // ?kbdoff=1: lo script non fa nulla (serve ai test automatici per confrontare con e senza).
  if (/[?&]kbdoff=1/.test(location.search)) return;

  var origFocus = HTMLElement.prototype.focus;
  HTMLElement.prototype.focus = function () {
    try {
      var tag = this.tagName;
      if ((tag === 'INPUT' || tag === 'TEXTAREA') && this.getRootNode().activeElement === this) {
        return;
      }
    } catch (err) { /* si prosegue con la focus() vera */ }
    return origFocus.apply(this, arguments);
  };

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

  // Il campo di testo nascosto di Compose ha user-select: none (inline) e sta dentro una radice
  // shadow con :host { -webkit-user-select: none }. Su iOS un campo cosi' prende il fuoco ma non e'
  // modificabile: compare il fumetto "Incolla" e la tastiera non si apre, o si chiude dopo mezzo
  // secondo. Il CSS della pagina non entra nella radice shadow: la regola va messa li' dentro, con
  // !important perche' lo stile inline di Compose non e' important.
  function patchRoot(r) {
    if (!r || r.__ailaKbdStyle) return;
    var st = document.createElement('style');
    st.textContent = 'input, textarea { -webkit-user-select: text !important; user-select: text !important; ' +
      '-webkit-touch-callout: default !important; }';
    r.appendChild(st);
    r.__ailaKbdStyle = true;
  }

  // Radice shadow di Compose (contiene canvas, albero di accessibilita' e campo nascosto).
  function getRoot() {
    if (root && root.host.isConnected) return root;
    root = null;
    var divs = document.body.querySelectorAll('div');
    for (var i = 0; i < divs.length; i++) {
      if (divs[i].shadowRoot) { root = divs[i].shadowRoot; break; }
    }
    patchRoot(root);
    return root;
  }

  // Compose crea la radice shadow dopo l'avvio (asincrono): la si cerca finche' non c'e', cosi' la
  // regola e' gia' attiva quando nasce il campo nascosto al primo tocco.
  (function waitForRoot() {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (getRoot() || tries > 120) clearInterval(timer);
    }, 500);
  })();

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

  function keyboardVisible() {
    var v = window.visualViewport;
    return !!v && (window.innerHeight - v.height) > 120;
  }

  function isEditable(el) {
    return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
  }

  var pending = null;

  function arm(box) {
    var el = getDecoy();
    if (!el.isConnected) document.body.appendChild(el);
    // Vicino al campo vero, cosi' iOS scorre la pagina come farebbe per quello.
    el.style.left = Math.max(0, box.left) + 'px';
    el.style.top = Math.max(0, box.top) + 'px';
    el.style.height = Math.max(1, box.height) + 'px';
    try { el.focus({ preventScroll: true }); } catch (err) { el.focus(); }

    // Manteniamo l'appoggio vivo abbastanza a lungo per non far chiudere la tastiera prima che
    // Compose abbia creato e preso il fuoco sul campo nascosto. Se Compose non interviene entro
    // qualche secondo, l'appoggio si rimuove come fallback ma senza il timeout da 0,5s che
    // causava la chiusura immediata su login/registrazione.
    var composeTookFocus = false;
    var rr = getRoot();
    function onComposeFocus() {
      var r = getRoot();
      if (r && isEditable(r.activeElement)) {
        composeTookFocus = true;
        if (document.activeElement === el) el.blur();
        try { if (r && r.removeEventListener) r.removeEventListener('focusin', onComposeFocus, true); } catch (err) {}
      }
    }
    try { if (rr && rr.addEventListener) rr.addEventListener('focusin', onComposeFocus, true); } catch (err) {}

    setTimeout(function () {
      if (composeTookFocus) return;
      if (document.activeElement !== el) return;
      var rr2 = getRoot();
      if (rr2 && isEditable(rr2.activeElement)) return;
      try { if (rr2 && rr2.removeEventListener) rr2.removeEventListener('focusin', onComposeFocus, true); } catch (err) {}
      el.blur();
    }, 5000);
  }

  // Dopo pointerup il browser manda gli eventi mouse "di compatibilita'" (mousedown, click): il
  // mousedown sposta il fuoco sull'elemento toccato, cioe' sul canvas di Compose (tabindex=0), e
  // butta via sia l'appoggio sia il campo nascosto di Compose, chiudendo la tastiera. E' il
  // difetto che Compose corregge dalla 1.12 (CMP-10079) annullando il touchend: qui lo si fa solo
  // per i tocchi che cadono su un campo di testo. Il touchend e' anche un evento di gesto sicuro
  // dove rimettere a fuoco l'appoggio.
  document.addEventListener('touchend', function (e) {
    var p = pending;
    pending = null;
    if (!p || Date.now() - p.t > 800) return;
    if (e.cancelable) e.preventDefault();
    var r = getRoot();
    var bk = r && r.querySelector('input, textarea');
    if (bk) {
      // Il campo nascosto di Compose esiste gia' (lo crea durante il pointerup): il fuoco lo si da'
      // qui, dentro il gesto. Le focus() successive di Compose vengono ignorate (vedi in cima).
      if (r.activeElement === bk) bk.blur();
      try { origFocus.call(bk, { preventScroll: true }); } catch (err) { origFocus.call(bk); }
      return;
    }
    // Compose non ha ancora creato il campo: l'appoggio apre la tastiera, poi subentrera' Compose.
    arm(p.box);
  }, { capture: true, passive: false });

  // Ripiego se il touchend non c'e' stato (altri dispositivi di puntamento).
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
    if (isEditable(active)) {
      var ar = active.getBoundingClientRect();
      var cx = ar.left + ar.width / 2;
      var cy = ar.top + ar.height / 2;
      // Il campo nascosto di Compose sta dentro il campo vero: se il suo centro e' nel riquadro
      // toccato, e' lo stesso campo (spostare il cursore) e non si ruba il fuoco.
      if (contains(box, cx, cy, 2) || contains(ar, e.clientX, e.clientY, 2)) {
        if (keyboardVisible()) return;
        // Tastiera non visibile: si riprova ad aprirla (pending qui sotto).
      }
    }

    pending = { box: box, t: Date.now() };
  }, { capture: true, passive: true });
})();
