'use strict';
/*
 * Intro di AILA, lunga quanto il trailer: le sei funzioni entrano una alla volta con un numero gigante, il nome
 * e una frase detta come la direbbe uno di classe; poi i dispositivi, i temi, il muro delle 24 versioni, la
 * privacy e il QR code. Strisce di luce sullo sfondo, inquadratura che pompa sulla cassa, cambi d'inquadratura
 * ogni due o tre quarti.
 * Come il trailer: seek(t) disegna il fotogramma al secondo t senza stato. 144 BPM, tempi in intro-timeline.js.
 */
const { BAR, BEAT: Q, DURATION, S: SEC } = INTRO_TL;
const B = n => n * BAR;
const ACC_COL = k => ACC[k][2][0]; // colore dell'accento in tema scuro (più luminoso, sta bene sul nero)
const mixA = (a, b, p) => { const A = hex(a), C = hex(b); return A.map((v, i) => Math.round(lerp(v, C[i], p))); };
const inGroove = t => (t >= B(SEC.DROP) && t < B(SEC.TRUST)) || (t >= B(SEC.OUT) && t < B(SEC.OUTRO));

// =====================================================================================
// ATMOSFERA: nebbia, fasci di luce, strisce in velocità (fuori dal "mondo" che pompa), poi il mondo
// =====================================================================================
const R0 = rng(77);
const STREAKS = [...Array(40)].map(() => ({ y: R0() * 1080, w: 200 + R0() * 900, h: 1 + R0() * 3, f: .5 + R0() * 1.4, x0: R0() * 3000, a: .1 + R0() * .35 }));
const atm = document.createElement('div');
atm.className = 'layer';
atm.innerHTML = `<div class="fog" id="f1"></div><div class="fog" id="f2"></div><div class="fog" id="f3"></div>
  ${[0, 1, 2].map(i => `<div class="beam" id="bm${i}"></div>`).join('')}
  ${STREAKS.map(s => `<div class="a stk" style="left:0;top:${s.y.toFixed(0)}px;width:${s.w.toFixed(0)}px;height:${s.h.toFixed(1)}px;border-radius:2px;
    background:linear-gradient(90deg,transparent,rgba(205,220,255,${s.a.toFixed(2)}) 70%,rgba(255,255,255,${Math.min(1, s.a * 2).toFixed(2)}));"></div>`).join('')}`;
stage.appendChild(atm);
const stk = [...atm.querySelectorAll('.stk')];
const world = document.createElement('div');
world.className = 'layer';
stage.appendChild(world);
const topLayer = document.createElement('div');
topLayer.className = 'layer';
topLayer.style.zIndex = 50;
topLayer.innerHTML = `<div class="flare" id="fl"></div><div id="vig"></div><div class="lbx" id="lb1" style="top:0"></div><div class="lbx" id="lb2" style="bottom:0"></div>
  <div class="a tag" id="hud1" style="left:60px;top:44px;z-index:65">AILA &nbsp;·&nbsp; <b>Anno scolastico 2026/27</b></div>
  <div class="a tag" id="hud3" style="left:60px;bottom:44px;z-index:65"></div>
  <div class="layer" id="flash" style="background:radial-gradient(circle at 50% 45%,rgba(235,240,255,.95),rgba(150,170,255,.35) 45%,transparent 75%);z-index:80"></div>`;
stage.appendChild(topLayer);
// palette della nebbia per sezione: [inizio, tre colori, intensità]
const FOG = [[0, ['#1E293B', '#312E81', '#0F172A'], .35], [B(SEC.DROP), ['#3B82F6', '#8B5CF6', '#06B6D4'], .95]];
// velocità delle strisce (px/s) a tratti: [inizio, fine, velocità]
const SPEED = [[B(SEC.DROP), B(SEC.CARDS), 2200], [B(SEC.CARDS), B(SEC.DEVICES), 1000], [B(SEC.DEVICES), B(SEC.WALL), 1300],
  [B(SEC.OUT), B(SEC.OUTRO), 900], [B(SEC.OUTRO), B(SEC.END), 300]];
const dist = t => SPEED.reduce((s, [a, b, v]) => s + v * clamp(t - a, 0, b - a), 0);
const speedAt = t => (SPEED.find(([a, b]) => t >= a && t < b) || [0, 0, 0])[2];
const FLASHES = [], FLARES = [];
const hit = (t, a = 1, y = 540) => { FLASHES.push([t, a]); FLARES.push([t, y]); };
let fogOverride = null, hudLabel = '';
function renderAtmo(t) {
  let k = 0; while (k < FOG.length - 1 && t >= FOG[k + 1][0]) k++;
  const prev = FOG[Math.max(0, k - 1)], cur = FOG[k], p = k === 0 ? 1 : P(t, cur[0], Q * 2, E.inOut);
  [['f1', 980 + 260 * Math.sin(t * .23), -380 + 120 * Math.cos(t * .19)], ['f2', -420 + 200 * Math.cos(t * .17), 420 + 120 * Math.sin(t * .21)],
    ['f3', 360 + 260 * Math.sin(t * .11 + 1), 640 + 100 * Math.sin(t * .14)]].forEach(([id, x, y], i) => {
    const c = fogOverride && i < 2 ? hex(fogOverride) : mixA(prev[1][i], cur[1][i], p);
    const el = document.getElementById(id), bgs = `radial-gradient(ellipse at center,rgba(${c.join(',')},.42),transparent 64%)`;
    if (el._bg !== bgs) { el.style.background = bgs; el._bg = bgs; }
    pose(el, { x, y, o: lerp(prev[2], cur[2], p) });
  });
  [0, 1, 2].forEach(i => pose(document.getElementById('bm' + i), { x: 300 + i * 560 + Math.sin(t * .4 + i) * 100, r: -18 + i * 18 + Math.sin(t * .5 + i * 2) * 8,
    o: (.5 + .35 * Math.sin(t * 1.6 + i * 2.1)) * (t < B(SEC.DROP) ? .4 : 1) }));
  // strisce di luce: la distanza percorsa è l'integrale della velocità, così non saltano tra un tratto e l'altro
  const d = dist(t), v = speedAt(t), so = clamp(v / 1200) * (1 - P(t, B(SEC.OUTRO), B(3)));
  stk.forEach((el, i) => { const s = STREAKS[i], span = 1920 + s.w * 2; pose(el, { x: span - ((s.x0 + d * s.f) % span) - s.w, sx: 1 + v / 4000, o: so }); });
  // bande del formato cinema all'inizio
  const h = 140 * clamp(t < B(SEC.DROP) ? 1 - P(t, B(SEC.DROP), .3, E.inOut) : 0);
  document.getElementById('lb1').style.height = h + 'px'; document.getElementById('lb2').style.height = h + 'px';
  let f = 0, shake = 0;
  for (const [tf, a] of FLASHES) if (t >= tf) { f = Math.max(f, a * (1 - P(t, tf, .4, E.out))); shake = Math.max(shake, a * Math.exp(-(t - tf) * 10)); }
  const fe = document.getElementById('flash'); fe.style.opacity = f.toFixed(3); fe.style.display = f > .002 ? '' : 'none';
  let fl = 0, fy = 540; for (const [tf, y] of FLARES) if (t >= tf && t < tf + .8) { const v2 = 1 - P(t, tf, .8, E.out); if (v2 > fl) { fl = v2; fy = y; } }
  pose(document.getElementById('fl'), { y: fy - 540 + 537, sx: lerp(.2, 1.2, 1 - fl), o: fl });
  // il mondo pompa sulla cassa e trema sugli impatti
  let pump = 1;
  if (inGroove(t)) { const b = t / Q, ph = (b - Math.floor(b)) * Q, down = Math.floor(b) % 4 === 0; pump = 1 + (down ? .02 : .01) * Math.exp(-ph * 14); }
  pose(world, { s: pump, x: Math.sin(t * 91) * 12 * shake, y: Math.cos(t * 77) * 8 * shake });
  // scritte in sovrimpressione
  const hp = (t >= B(SEC.DROP) && t < B(SEC.TRUST) ? 1 : 0) * P(t, B(SEC.DROP) + .2, .3) * (1 - P(t, B(SEC.TRUST) - .3, .3));
  ['hud1', 'hud3'].forEach(id => pose(document.getElementById(id), { o: hp }));
  const h3 = document.getElementById('hud3'); if (h3._t !== hudLabel) { h3.innerHTML = hudLabel; h3._t = hudLabel; }
}
/* aberrazione cromatica che si spegne dopo l'impatto */
function chroma(el, t, t0, k = 9) {
  const v = t >= t0 ? Math.exp(-(t - t0) * 7) * k : 0;
  el.style.textShadow = v > .3 ? `${-v}px 0 rgba(255,40,90,.75),${v}px 0 rgba(40,220,255,.75)` : '';
}
/* testo che si adatta alla larghezza disponibile (rimisurato quando cambia il testo, a font caricati) */
function fitText(el, maxW) {
  const key = el.textContent;
  if (el._fit === key || document.fonts.status !== 'loaded') return;
  el.style.fontSize = '';
  const fs = parseFloat(getComputedStyle(el).fontSize), w = el.scrollWidth;
  if (w > maxW) el.style.fontSize = (fs * maxW / w).toFixed(1) + 'px';
  el._fit = key;
}
/* riflesso che attraversa il vetro di un dispositivo */
const SPEC = `<div class="a spec" style="left:0;top:-10%;width:45%;height:120%;z-index:40;pointer-events:none;
  background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);transform:skewX(-18deg)"></div>`;
function spec(root, t, t0, w, d = .7) { const p = P(t, t0, d, E.inOut); pose(root.querySelector('.spec'), { x: lerp(-.6 * w, 1.4 * w, p), o: t >= t0 && p < 1 ? 1 : 0 }); }
/* uscita a frustata: scivola via sfocandosi nell'ultimo mezzo quarto */
function whipOut(t, t1, dir = -1) { const p = P(t, t1 - Q / 2, Q / 2, E.in); return { x: dir * 700 * p, b: p * 30, o: 1 - p * p }; }

// ---------- le sei schermate nel loro stato finale (nel trailer sono animate, qui posano ferme) ----------
const SCR = [scrCircolari, scrAssistant, scrCalendario, scrSondaggi, scrMappaPosti, scrBacheca];
const FIN = [
  r => { r.querySelector('#lst').style.display = 'none'; },
  (r, d) => { ['#sugg', '#think', '#qc', '#ac'].forEach(s => r.querySelector(s).style.display = 'none'); r.querySelector('#at').textContent = d.ATXT; },
  (r, d) => r.querySelectorAll('.dc').forEach(el => { const e = d.EV.find(e => e[0] === +el.dataset.d); if (e) el.querySelector('.edot').style.background = e[3]; }),
  r => { r.querySelectorAll('.ok').forEach(el => el.style.opacity = 1); },
  (r, d) => {
    r.querySelectorAll('.seat').forEach((el, i) => { const [x, y] = d.seatXY(d.LAY[0].indexOf(i)); el.style.transform = `translate(${x}px,${y}px)`; });
    r.querySelectorAll('.pp').forEach((el, i) => el.style.color = i ? 'var(--muted)' : '#fff');
  },
  (r, d) => { r.querySelector('#rk').style.display = 'none'; r.querySelectorAll('.vn').forEach((el, i) => el.textContent = d.PR[i][2]); },
];
// ogni funzione: nome, una frase detta come la direbbe uno di classe, dispositivo e tema
const CARDS = [
  { name: 'Circolari', line: 'Ti dice se una circolare ti riguarda davvero e ti segna le scadenze sul calendario.',
    kind: 'iphone', s: st('glass', false, 'blue'), col: '#5A8CFF', foc: [[150, 300], [200, 610]] },
  { name: 'Assistant', line: 'Gli chiedi le cose come le chiederesti a un compagno, e ti fa vedere da dove prende la risposta.',
    kind: 'droid', s: st('mat', true, 'blue'), col: '#22D3EE', foc: [[200, 200], [150, 330]] },
  { name: 'Calendario', line: 'Le date della scuola e quelle della classe stanno insieme, con l\'ora di lezione giusta.',
    kind: 'iphone', s: st('glass', true, 'violet'), col: '#A78BFA', foc: [[120, 300], [180, 560]] },
  { name: 'Sondaggi', line: 'Le date delle interrogazioni le scegliamo noi, e nessuno può fare il furbo.',
    kind: 'droid', s: st('mat', false, 'green'), col: '#34D399', foc: [[120, 450], [170, 290]] },
  { name: 'Mappa posti', line: 'Il rappresentante sceglie come disporre i banchi tra le proposte che calcola l\'app.',
    kind: 'iphone', s: st('glass', false, 'teal'), col: '#2DD4BF', foc: [[190, 320], [150, 640]] },
  { name: 'Bacheca', line: 'Hai un\'idea per la classe? La proponi e si vota, anche senza metterci il nome.',
    kind: 'droid', s: st('mat', true, 'orange'), col: '#FB923C', foc: [[200, 240], [150, 420]] },
];

// =====================================================================================
// DETTAGLI "MACRO": sei schermate ingrandite, condivise da tutte le scene (copia nitida al centro, sfocata ai bordi)
// =====================================================================================
const macroLayer = document.createElement('div');
macroLayer.className = 'layer';
const MAC = CARDS.map((c, k) => {
  const d = SCR[k](), shot = `<div class="shot" style="width:406px;height:876px"><div class="${appCls(c.s)}" style="${appVars(c.s)}">${d.inner}</div></div>`;
  const m = document.createElement('div');
  m.className = 'macro'; m.innerHTML = `<div class="layer soft">${shot}</div><div class="layer sharp">${shot}</div>`;
  m.querySelectorAll('.shot').forEach(sh => FIN[k](sh, d));
  macroLayer.appendChild(m); m._sh = [...m.querySelectorAll('.shot')];
  return m;
});
let macroShown = -1;
/* mostra la schermata k ingrandita sul punto (fx, fy), con scala e rotazione; p (0..1) muove l'inquadratura */
function macro(k, fx, fy, sc, p, rot = 0, pan = 0) {
  macroShown = k;
  const s = sc * (1 + .1 * (1 - p));
  MAC[k]._sh.forEach(sh => { sh.style.transform = `translate(${960 - fx * s + pan * (1 - p)}px,${540 - fy * s}px) scale(${s}) rotate(${rot}deg)`; });
}

// =====================================================================================
// 1 · APERTURA (battute 0–6): linea di luce, poi dettagli dell'app che arrivano sempre più fitti
// =====================================================================================
const FR = []; // [istante, schermata, fuoco, scala, rotazione]
{
  let k = 0;
  const add = tt => { const kind = k % 6, foc = CARDS[kind].foc[Math.floor(k / 6) % 2]; FR.push([tt, kind, foc, 2.3 + (k % 3) * .4, (k % 2 ? -3 : 3)]); k++; };
  for (let i = 0; i < 8; i++) add(B(2) + i * Q);
  for (let i = 0; i < 8; i++) add(B(4) + i * Q / 2);
  for (let i = 0; i < 6; i++) add(B(5) + i * Q / 2);
}
{
  const S = addScene(0, B(SEC.DROP) + .02, `
    <div class="a" id="line" style="left:260px;top:538px;width:1400px;height:4px;border-radius:2px;background:linear-gradient(90deg,transparent,#DCE6FF 30%,#fff 50%,#DCE6FF 70%,transparent);box-shadow:0 0 30px 6px rgba(120,160,255,.55)"></div>
    <div class="a center cap" id="pres" style="left:0;width:1920px;top:470px">Anno scolastico 2026/27</div>`, world);
  sfx(0, 'swell');
  FR.forEach(([tt]) => sfx(tt, 'cut'));
  FR.filter(f => f[0] < B(4)).forEach(f => hit(f[0], .25, 200 + f[1] * 120));
  S.render = t => {
    const pl = P(t, .2, B(1.2), E.inOut);
    pose(S.q('#line'), { sx: pl, o: clamp(pl * 3) * (1 - P(t, B(2) - .3, .3)) * (.85 + .15 * Math.sin(t * 6)) });
    io(S.q('#pres'), t, B(.8), B(2) - .2, { dy: 0, b: 12, d: .8, e: E.out });
    S.q('#pres').style.letterSpacing = lerp(.6, .42, P(t, B(.8), B(1.2))) + 'em';
    let i = -1; while (i < FR.length - 1 && t >= FR[i + 1][0]) i++;
    if (i >= 0 && t < B(5.75)) {
      const [tt, kind, foc, sc, rot] = FR[i], next = i < FR.length - 1 ? FR[i + 1][0] : B(5.75), p = clamp((t - tt) / (next - tt));
      macro(kind, foc[0], foc[1], sc, p, rot, 140 * (i % 2 ? 1 : -1));
    }
  };
}

// =====================================================================================
// 2 · DROP (battute 6–9): logo
// =====================================================================================
{
  const t0 = B(SEC.DROP), t1 = B(SEC.CARDS);
  const S = addScene(t0, t1 + .02, `<div class="layer" id="g">
    <div class="a" id="logo" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('I')}</div>
    <div class="a center" id="wm" style="left:0;width:1920px;top:480px;font-size:250px;font-weight:800;letter-spacing:.04em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
    <div class="a center" id="ss" style="left:0;width:1920px;top:790px;font-size:44px;font-weight:500;color:#DCE3FF">L'app per la vita di classe</div></div>`, world);
  const wm = S.q('#wm'); wm._w = [...wm.querySelectorAll('.w')];
  sfx(t0, 'impact'); hit(t0, 1);
  S.render = t => {
    renderLogo(S.q('#logo'), t, t0 - .05, .55);
    pose(S.q('#logo'), { y: Math.sin(t * 1.4) * 6 });
    wm._w.forEach((w, i) => io(w, t, t0 + .15 + i * Q / 4, null, { dy: 0, s0: 1.9, b: 24, d: .26 }));
    chroma(wm, t, t0 + .15, 12);
    io(S.q('#ss'), t, t0 + BAR, null, { dy: 30, d: .6 });
    pose(S.q('#g'), { ...whipOut(t, t1), s: 1 + (t - t0) * .02 });
  };
}

// =====================================================================================
// 3 · LE SEI FUNZIONI (battute 9–45): sei battute ciascuna
// =====================================================================================
CARDS.forEach((c, i) => {
  const t0 = B(SEC.CARDS + i * SEC.CARD_LEN), t1 = t0 + B(SEC.CARD_LEN), R = i % 2 === 0; // R: telefono a destra
  const d = SCR[i]();
  FOG.push([t0, [c.col, '#1E1B4B', c.col], .95]);
  const S = addScene(t0 - .02, t1 + .02, `
    <div class="layer" id="fbg" style="background:radial-gradient(ellipse at center,${c.col}66,transparent 70%)"></div>
    <div class="a center" id="fn" style="left:0;width:1920px;top:40px;font-size:1000px;font-weight:800;line-height:1;letter-spacing:-.07em;color:${c.col};-webkit-text-stroke:4px #fff"><span class="sk">0${i + 1}</span></div>
    <div class="layer" id="cam">
      <div class="a" id="glow" style="left:${R ? 900 : -60}px;top:-40px;width:1100px;height:1100px;border-radius:50%;background:radial-gradient(circle,${c.col}55,transparent 62%)"></div>
      <div class="a num" id="num" style="${R ? 'left:470px' : 'left:640px'};top:120px"><span class="sk">0${i + 1}</span></div>
      <div class="a" id="ph" style="left:${R ? 1250 : 240}px;top:90px">${devHTML(c.kind, c.s, d.inner + SPEC)}</div>
      <div class="a${R ? '' : ' right'}" style="${R ? 'left:110px' : 'left:800px;width:1010px;text-align:right'};top:470px">
        <div class="name" id="nm"><span class="sk">${c.name}</span></div>
        <div class="stripe" id="sp" style="${R ? 'left:0' : 'right:0'};top:205px;width:620px;background:linear-gradient(90deg,${c.col},${c.col}00);transform-origin:${R ? '0' : '100%'} 50%"></div>
        <div id="ln" style="margin-top:96px;font-size:38px;line-height:1.4;font-weight:500;color:#DCE3FF;max-width:900px;${R ? '' : 'margin-left:auto'}">${c.line}</div>
      </div></div>`, world);
  FIN[i](S.el, d);
  sfx(t0, 'num'); hit(t0, .6, R ? 300 : 800);
  sfx(t0 + Q, 'whoosh'); sfx(t0 + Q * 3, 'slam'); hit(t0 + Q * 3, .25, 760);
  sfx(t0 + Q * 13, 'punch'); hit(t0 + Q * 14, .2, 300);
  sfx(t1 - Q / 2, 'whip');
  S.render = t => {
    hudLabel = `<b>0${i + 1}</b> &nbsp;/&nbsp; 06 &nbsp;·&nbsp; ${c.name}`;
    const b = (t - t0) / Q; // quarti dall'inizio della funzione
    // quarto 0: numero a tutto schermo
    pose(S.q('#fn'), { o: b < 1 ? 1 : 0, s: lerp(1.3, 1, P(t, t0, .25, E.out5)), x: -(t - t0) * 90 });
    pose(S.q('#fbg'), { o: b < 1 ? 1 : .35 * (1 - P(t, t0 + Q, Q * 3)) });
    // quarti 1–3 e 13–14: dettaglio dello schermo
    const mac1 = b >= 1 && b < 3, mac2 = b >= 13 && b < 14;
    if (mac1) macro(i, c.foc[0][0], c.foc[0][1], 2.4, (b - 1) / 2, R ? 2 : -2, R ? 240 : -240);
    else if (mac2) macro(i, c.foc[1][0], c.foc[1][1], 3, b - 13, R ? -2 : 2, 0);
    // il resto: telefono, nome e frase
    const mainOn = b >= 3 && !mac2;
    pose(S.q('#cam'), mainOn ? { ...whipOut(t, t1, R ? -1 : 1), s: 1 + .04 * (t - t0) / B(SEC.CARD_LEN) } : { o: 0 });
    pose(S.q('#glow'), { o: .85 + .15 * Math.sin(t * 3) });
    pose(S.q('#num'), { x: -(t - t0 - Q * 3) * 30 * (R ? 1 : -1), s: lerp(1.15, 1, P(t, t0 + Q * 3, .35, E.out5)) });
    const pin = P(t, t0 + Q * 3, .5, E.out5);
    pose(S.q('#ph'), { x: (1 - pin) * 220 * (R ? 1 : -1) - (t - t0) * 6 * (R ? 1 : -1), y: Math.sin((t - t0) * 1.5) * 6, ry: (R ? -1 : 1) * lerp(30, 10, pin), rx: 3, s: lerp(.9, .96, pin) });
    spec(S.q('#ph'), t, t0 + Q * 3.3, 430); if (b > 14) spec(S.q('#ph'), t, t0 + Q * 14.2, 430);
    const nm = S.q('#nm'); fitText(nm, 1000);
    io(nm, t, t0 + Q * 3, null, { dx: R ? 280 : -280, dy: 0, s0: 1.15, b: 26, d: .3, e: E.out5 });
    chroma(nm, t, t0 + Q * 3, 12);
    pose(S.q('#sp'), { sx: P(t, t0 + Q * 3, .4, E.out5), o: t >= t0 + Q * 3 ? 1 : 0 });
    io(S.q('#ln'), t, t0 + Q * 5, null, { dy: 24, d: .6, b: 8 });
  };
});

// =====================================================================================
// 4 · DISPOSITIVI (battute 45–51): una frase, poi un dispositivo per battuta, poi tutti insieme
// =====================================================================================
{
  const T0 = B(SEC.DEVICES), T1 = B(SEC.THEMES);
  const DEV = [ // [tipo, tema, schermata, nome, scala da protagonista, posizione in fila, scala in fila]
    ['droid', st('mat', false, 'blue'), homePhone('Chiara'), 'Android', .9, [800, 640], .56],
    ['iphone', st('glass', false, 'violet'), homePhone('Marco'), 'iPhone', .9, [1120, 640], .56],
    ['tab', st('mat', true, 'green'), homeWide('Luca', false), 'Tablet', .78, [500, 440], .6],
    ['ipad', st('glass', true, 'blue'), homeWide('Sara', true), 'iPadOS', .8, [1420, 430], .6],
  ];
  const W = { droid: [420, 900], iphone: [430, 900], ipad: [1180, 830], tab: [1240, 790] };
  FOG.push([T0, ['#3B82F6', '#8B5CF6', '#06B6D4'], .85]);
  const TIN = DEV.map((_, i) => T0 + B(1 + i)), TALL = T0 + B(5);
  const S = addScene(T0, T1 + .02, `
    <div class="a center" id="cap" style="left:260px;width:1400px;top:390px;font-size:76px;font-weight:700;line-height:1.2;letter-spacing:-.02em">Va sul telefono e sul tablet, <em class="grad">Android o Apple che sia.</em></div>
    ${DEV.map(d => `<div class="a num dn" style="-webkit-text-stroke-color:rgba(255,255,255,.26);left:0;width:1920px;text-align:center;top:${d[0] === 'droid' || d[0] === 'iphone' ? 250 : 120}px;font-size:${d[0] === 'droid' || d[0] === 'iphone' ? 420 : 380}px"><span class="sk">${d[3]}</span></div>`).join('')}
    ${DEV.map(d => `<div class="a dv" style="left:${960 - W[d[0]][0] / 2}px;top:${540 - W[d[0]][1] / 2}px">${devHTML(d[0], d[1], d[2] + SPEC)}</div>`).join('')}`, world);
  const dn = S.qa('.dn'), dv = S.qa('.dv');
  sfx(T0, 'slam'); hit(T0, .4);
  TIN.forEach((tt, i) => { sfx(tt, 'hit'); hit(tt, .4, 200 + i * 220); });
  sfx(TALL, 'impactS'); hit(TALL, .5); DEV.forEach((_, i) => sfx(TALL + i * Q / 2, 'pop'));
  S.render = t => {
    hudLabel = 'Dispositivi';
    io(S.q('#cap'), t, T0, TIN[0] - .25, { dy: 30, d: .5, b: 10 });
    DEV.forEach((d, i) => {
      const t0 = TIN[i], hero = t >= t0 && t < t0 + BAR;
      const pin = P(t, t0, .45, E.out5), pex = P(t, t0 + BAR - .2, .2, E.in);
      if (hero) {
        pose(dv[i], { s: d[4] * lerp(.82, 1, pin), ry: lerp(70, -8, pin) + (t - t0) * 3, rx: lerp(10, 3, pin), o: clamp(pin * 3) * (1 - pex), b: pex * 14, x: -(t - t0) * 30 - pex * 200 });
        spec(dv[i], t, t0 + .2, W[d[0]][0]);
      }
      pose(dn[i], { o: hero ? clamp(pin * 3) * (1 - pex) : 0, s: lerp(1.25, 1, pin), x: -(t - t0) * 60 });
      chroma(dn[i], t, t0, 8);
      if (t >= TALL) {
        const pl = P(t, TALL + i * Q / 2, .4, E.back);
        pose(dv[i], { x: d[5][0] - 960, y: d[5][1] - 540 + Math.sin(t * 1.5 + i) * 6, s: d[6] * pl, o: clamp(pl * 2) * (1 - P(t, T1 - .2, .2)), ry: (i % 2 ? -1 : 1) * 6 });
        dv[i].style.zIndex = d[0] === 'droid' || d[0] === 'iphone' ? 3 : 1;
      } else if (!hero) pose(dv[i], { o: 0 });
    });
  };
}

// =====================================================================================
// 5 · TEMI (battute 51–57): stile, tema e colore, un cambio ogni due quarti
// =====================================================================================
{
  const T0 = B(SEC.THEMES), T1 = B(SEC.WALL);
  const SEQ = [ // [impostazione, valore, stato]
    ['Stile', 'Liquid Glass', st('glass', false, 'blue')], ['Stile', 'Material', st('mat', false, 'blue')],
    ['Tema', 'Chiaro', st('glass', false, 'blue')], ['Tema', 'Scuro', st('glass', true, 'blue')],
    ...ACC_KEYS.map((k, i) => ['Colore', ACC[k][0], st(i % 2 ? 'mat' : 'glass', i % 3 !== 2, k)]),
  ];
  const TT = SEQ.map((_, i) => T0 + B(.5) + i * Q * 2), TOUT = T1 - Q;
  const S = addScene(T0, T1 + .02, `
    <div class="a" id="ph" style="left:1180px;top:90px">${devHTML('iphone', SEQ[0][2], `<div class="app" id="la">${homePhone('Chiara')}</div><div class="app" id="lb">${homePhone('Chiara')}</div>${SPEC}`)}</div>
    <div class="a" id="intro" style="left:120px;top:250px;width:980px;font-size:40px;font-weight:600;line-height:1.35;color:#DCE3FF">Te la imposti come ti piace, da Impostazioni › Aspetto.</div>
    <div class="a" style="left:120px;top:440px;width:1000px">
      <div class="desc" id="cat"></div>
      <div class="name" id="val" style="margin-top:22px;font-size:180px"><span class="sk" id="vt"></span></div>
      <div class="stripe" id="sp" style="left:0;top:250px;width:560px;transform-origin:0 50%"></div></div>`, world);
  const la = S.q('#la'), lb = S.q('#lb');
  TT.forEach(tt => { sfx(tt, 'switch'); hit(tt, .18, 760); });
  S.render = t => {
    hudLabel = 'Temi';
    let k = 0; while (k < SEQ.length - 1 && t >= TT[k + 1]) k++;
    const [cat, name, cur] = SEQ[k], prev = SEQ[Math.max(0, k - 1)][2], p = k ? P(t, TT[k], .12) : 1;
    fogOverride = t < TOUT && k >= 4 ? ACC_COL(cur.acc) : null;
    applyState(la, prev); applyState(lb, cur);
    la.style.display = p < 1 ? '' : 'none'; lb.style.opacity = p < 1 ? p.toFixed(3) : '';
    const pin = P(t, T0, .5, E.out5), pout = P(t, TOUT, Q, E.in);
    pose(S.q('#ph'), { y: (1 - pin) * 260 + Math.sin(t * 1.4) * 6, ry: -14 + Math.sin(t * 1.1) * 8, rx: 3, s: .96 * (1 + Math.exp(-(t - TT[k]) * 10) * .03), o: clamp(pin * 2) * (1 - pout), b: pout * 14 });
    spec(S.q('#ph'), t, TT[k], 430, .5);
    io(S.q('#intro'), t, T0, TOUT, { dy: 24, d: .6 });
    setText(S.q('#cat'), cat); setText(S.q('#vt'), name);
    const v = S.q('#val'); fitText(v, 960);
    if (t >= TT[0]) { slam(v, t, TT[k], TOUT, { d: .16, s0: 1.2 }); chroma(v, t, TT[k], 8); } else pose(v, { o: 0 });
    pose(S.q('#cat'), { o: t >= TT[0] ? 1 - pout : 0 });
    const sp = S.q('#sp'); sp.style.background = `linear-gradient(90deg,${ACC_COL(cur.acc)},${ACC_COL(cur.acc)}00)`;
    pose(sp, { sx: P(t, TT[k], .3, E.out5), o: t >= TT[0] ? 1 - pout : 0 });
  };
}

// =====================================================================================
// 6 · IL MURO (battute 57–61): le 24 versioni, a onda, inclinate in 3D
// =====================================================================================
{
  const T0 = B(SEC.WALL), T1 = B(SEC.TRUST);
  const ROWS = [st('glass', false), st('glass', true), st('mat', false), st('mat', true)];
  const NM = ['Chiara', 'Marco', 'Sara', 'Luca', 'Anna', 'Leo'];
  FOG.push([T0, ['#3B82F6', '#8B5CF6', '#06B6D4'], .85]);
  const S = addScene(T0, T1 + .02, `<div id="wg" class="a" style="left:0;top:0;width:1920px;height:1080px">${ROWS.map((r, ri) => ACC_KEYS.map((a, ci) =>
    `<div class="a wc" style="left:${960 - 215 + (ci - 2.5) * 262}px;top:${540 - 450 + (ri - 1.5) * 262}px" data-r="${ri}" data-c="${ci}">
      ${devHTML(ri < 2 ? 'iphone' : 'droid', st(r.style, r.dark, a), homePhone(NM[ci]))}</div>`).join('')).join('')}</div>
    <div class="layer" id="wdim" style="background:radial-gradient(ellipse at center,rgba(4,5,8,.9) 22%,rgba(4,5,8,.2) 70%)"></div>
    <div class="a center" id="n24" style="left:0;width:1920px;top:290px;font-size:330px;font-weight:800;letter-spacing:-.06em;line-height:1"><em class="grad">24</em></div>
    <div class="a center" id="w1" style="left:0;width:1920px;top:640px;font-size:56px;font-weight:600;color:#DCE3FF">versioni diverse, e una è quella che usi tu</div>`, world);
  const wc = S.qa('.wc');
  for (let d = 0; d < 9; d++) sfx(T0 + d * Q / 2, 'pop');
  sfx(T0, 'impactS'); hit(T0, .5); sfx(T0 + B(1), 'slam');
  S.render = t => {
    hudLabel = 'Temi';
    const pz = (t - T0) / (T1 - T0);
    pose(S.q('#wg'), { rx: 14 - 6 * pz, ry: -10 + 8 * pz, s: lerp(.92, 1.02, pz), y: -20 * pz, o: 1 - P(t, T1 - .3, .3) });
    wc.forEach(el => { const r = +el.dataset.r, c = +el.dataset.c, pi = P(t, T0 + (r + c) * Q / 2, .45, E.back); pose(el, { s: .3 * pi, o: clamp(pi * 3) }); });
    pose(S.q('#wdim'), { o: P(t, T0 + B(1) - .2, .3) * (1 - P(t, T1 - .4, .4)) });
    if (t >= T0 + B(1)) setText(S.q('#n24 em'), String(Math.round(lerp(1, 24, P(t, T0 + B(1), Q * 2, E.out)))));
    slam(S.q('#n24'), t, T0 + B(1), T1 - .3);
    io(S.q('#w1'), t, T0 + B(1.5), T1 - .3, { dy: 30, d: .5 });
  };
}

// =====================================================================================
// 7 · PRIVACY (battute 61–64): una frase, poi il salto verso il finale
// =====================================================================================
{
  const T0 = B(SEC.TRUST), T1 = B(SEC.OUT);
  const S = addScene(T0, T1 + .02, `<div class="layer" id="tg">
    <div class="a center" id="p1" style="left:210px;width:1500px;top:360px;font-size:70px;font-weight:700;line-height:1.25;letter-spacing:-.02em">L'AI legge solo le circolari della scuola, <em class="grad">i tuoi dati personali non li vede mai.</em></div>
    <div class="a center" id="p2" style="left:0;width:1920px;top:640px;font-size:38px;font-weight:500;color:#C9D3FF">E se preferisci, la fai girare direttamente sul tuo telefono.</div></div>`, world);
  sfx(T0, 'slam');
  S.render = t => {
    hudLabel = '';
    io(S.q('#p1'), t, T0, null, { dy: 36, d: .7, b: 12 });
    io(S.q('#p2'), t, T0 + B(1), null, { dy: 24, d: .6 });
    const pz = P(t, T1 - B(.5), B(.5), E.in);
    pose(S.q('#tg'), { s: lerp(1, .05, pz), o: 1 - pz * .8, b: pz * 8 });
  };
}

// =====================================================================================
// 8 · FINALE (battute 64–74): logo, QR code e piattaforme
// =====================================================================================
{
  const q = QR, cell = 12, size = q.n * cell, T0 = B(SEC.OUT);
  const qrSVG = `<svg width="${size}" height="${size}" viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges"><path fill="#0A1330" d="${q.cells.map(([x, y]) => `M${x} ${y}h1v1h-1z`).join('')}"/></svg>`;
  FOG.push([T0, ['#3B82F6', '#8B5CF6', '#06B6D4'], 1]);
  const S = addScene(T0, DURATION + 1, `
    <div class="layer" id="lg">
      <div class="a" id="logo2" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('F')}</div>
      <div class="a center" id="wm2" style="left:0;width:1920px;top:470px;font-size:210px;font-weight:800;letter-spacing:.05em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
      <div class="a center" id="tag2" style="left:0;width:1920px;top:710px;font-size:46px;font-weight:500;color:#DCE3FF">La tua scuola, <em class="grad" style="font-weight:700">sincronizzata.</em></div>
      <div class="a" id="plat" style="left:0;width:1920px;top:820px;display:flex;justify-content:center;gap:16px">
        ${[['smartphone', 'Android'], ['smartphone', 'iPhone'], ['tablet', 'Tablet'], ['tablet', 'iPadOS']].map(([ic, l]) => `<span class="chip pl">${I(ic, 'style="color:#A5B8FF"')}${l}</span>`).join('')}</div>
    </div>
    <div class="a" id="qr" style="left:1210px;top:190px;width:520px">
      <div class="center" style="font-size:44px;font-weight:700;letter-spacing:-.01em;line-height:1.2">Inquadra il codice<br>e <em class="grad">scaricala</em></div>
      <div class="qrbox" style="margin:30px auto 0;width:${size + 80}px">${qrSVG}</div>
      <div class="center" style="font-size:24px;color:#C9D3FF;margin-top:28px;line-height:1.4">Per registrarti ti serve<br>il codice della tua classe.</div>
    </div>
    <div class="a center" id="cred" style="left:0;width:1920px;top:1000px;font-size:23px;color:#6E7AA3">Un progetto di Simone Bianchin</div>`, world);
  const wm = S.q('#wm2'); wm._w = [...wm.querySelectorAll('.w')];
  const pl = S.qa('.pl');
  sfx(T0, 'impact'); hit(T0, 1);
  sfx(T0 + B(1.5), 'whoosh'); pl.forEach((_, i) => sfx(T0 + B(2) + Q * 2 + i * Q / 2, 'pop'));
  S.render = t => {
    renderLogo(S.q('#logo2'), t, T0 - .05, .55);
    pose(S.q('#logo2'), { y: Math.sin(t * 1.4) * 6 });
    wm._w.forEach((w, i) => io(w, t, T0 + .15 + i * Q / 4, null, { dy: 0, s0: 1.9, b: 24, d: .26 }));
    chroma(wm, t, T0 + .15, 12);
    io(S.q('#tag2'), t, T0 + B(1), null, { dy: 30 });
    const pm = P(t, T0 + B(1.5), .8, E.inOut);
    pose(S.q('#lg'), { x: -380 * pm, y: 40 * pm, s: lerp(1, .86, pm) });
    io(S.q('#qr'), t, T0 + B(1.5) + .15, null, { dx: 260, dy: 0, d: .7, b: 16, s0: .92 });
    pl.forEach((el, i) => io(el, t, T0 + B(2) + Q * 2 + i * Q / 2, null, { dy: 24, s0: .6, e: E.back, d: .35, b: 4 }));
    io(S.q('#cred'), t, T0 + B(3.5), null, { dy: 14 });
  };
}
world.appendChild(macroLayer); // sopra a tutte le scene

// ---------- motore ----------
FOG.sort((a, b) => a[0] - b[0]);
function seek(t) {
  fogOverride = null; hudLabel = ''; macroShown = -1;
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
  MAC.forEach((m, k) => { const on = k === macroShown; if (m._on !== on) { m.style.display = on ? 'block' : 'none'; m._on = on; } });
  renderAtmo(t);
}
window.seek = seek; window.DURATION = DURATION;
window.SFX = SFX.slice().sort((a, b) => a.t - b.t);

// ---------- anteprima nel browser ----------
const capture = location.search.includes('capture');
if (capture) document.body.classList.add('capture');
function fit() {
  if (capture) return;
  const s = Math.min(innerWidth / 1920, (innerHeight - 56) / 1080);
  stage.style.transform = `scale(${s})`;
  stage.style.left = ((innerWidth - 1920 * s) / 2) + 'px';
}
addEventListener('resize', fit); fit();
seek(0);
if (!capture) {
  const m = location.hash.match(/t=([\d.]+)/);
  let t0 = m ? +m[1] : 0, playing = true, base = performance.now() - t0 * 1000, cur = t0;
  const playBtn = document.getElementById('play'), range = document.getElementById('seek'), tm = document.getElementById('tm');
  range.max = DURATION;
  playBtn.onclick = () => { playing = !playing; playBtn.textContent = playing ? 'Pausa' : 'Play'; base = performance.now() - cur * 1000; };
  range.oninput = () => { cur = +range.value; base = performance.now() - cur * 1000; seek(cur); };
  document.fonts.ready.then(() => {
    const loop = () => {
      if (playing) { cur = (performance.now() - base) / 1000; if (cur > DURATION) { cur = 0; base = performance.now(); } seek(cur); range.value = cur; }
      tm.textContent = cur.toFixed(1) + ' s';
      requestAnimationFrame(loop);
    };
    loop();
  });
}
