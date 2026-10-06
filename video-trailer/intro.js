'use strict';
/*
 * Intro "presentazione della squadra": le sei funzioni di AILA entrano una alla volta come in una
 * presentazione dei piloti (numero, nome, statistiche), poi dispositivi, livree (temi), griglia di partenza
 * con le 24 combinazioni e il "semaforo" fatto con i sei banchi del logo.
 * Come il trailer: seek(t) disegna il fotogramma al secondo t senza stato. 144 BPM, tempi in intro-timeline.js.
 */
const { BAR, BEAT: Q, DURATION } = INTRO_TL;
const B = n => n * BAR;
const ACC_COL = k => ACC[k][2][0]; // colore dell'accento in tema scuro (più luminoso, sta bene sul nero)

// =====================================================================================
// ATMOSFERA (sempre presente): nebbia colorata, fasci di luce, vignetta, bande cinema, grana
// =====================================================================================
const atm = document.createElement('div');
atm.className = 'layer';
atm.innerHTML = `<div class="fog" id="f1"></div><div class="fog" id="f2"></div><div class="fog" id="f3"></div>
  ${[0, 1, 2].map(i => `<div class="beam" id="bm${i}"></div>`).join('')}`;
stage.appendChild(atm);
const topLayer = document.createElement('div');
topLayer.className = 'layer';
topLayer.style.zIndex = 50;
topLayer.innerHTML = `<div class="flare" id="fl"></div><div id="vig"></div><div class="lbx" id="lb1" style="top:0"></div><div class="lbx" id="lb2" style="bottom:0"></div><div id="grain"></div>
  <div class="layer" id="flash" style="background:radial-gradient(circle at 50% 45%,rgba(235,240,255,.95),rgba(150,170,255,.35) 45%,transparent 75%);z-index:80"></div>`;
stage.appendChild(topLayer);
// grana: quattro fotogrammi di rumore generati una volta sola (stesso seme, stesse immagini a ogni esportazione)
const GRAIN = [0, 1, 2, 3].map(k => {
  const c = document.createElement('canvas'); c.width = 480; c.height = 270;
  const g = c.getContext('2d'), img = g.createImageData(480, 270), R = rng(31 + k);
  for (let i = 0; i < img.data.length; i += 4) { const v = R() * 255; img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; }
  g.putImageData(img, 0, 0); return `url(${c.toDataURL()})`;
});
// palette della nebbia per sezione: [inizio, tre colori, intensità]
const FOG = [[0, ['#1E293B', '#312E81', '#0F172A'], .35], [B(8), ['#3B82F6', '#8B5CF6', '#06B6D4'], .9]];
const FLASHES = [], FLARES = [];
const hit = (t, a = 1, y = 540) => { FLASHES.push([t, a]); FLARES.push([t, y]); };
let fogOverride = null; // colore che una scena impone alla nebbia (livree)
function renderAtmo(t) {
  let k = 0; while (k < FOG.length - 1 && t >= FOG[k + 1][0]) k++;
  const prev = FOG[Math.max(0, k - 1)], cur = FOG[k], p = k === 0 ? 1 : P(t, cur[0], Q * 2, E.inOut);
  const dark = (t >= B(48) && t < B(50)) ? 0 : 1; // semaforo: buio
  [['f1', 980 + 260 * Math.sin(t * .23), -380 + 120 * Math.cos(t * .19)], ['f2', -420 + 200 * Math.cos(t * .17), 420 + 120 * Math.sin(t * .21)],
    ['f3', 360 + 260 * Math.sin(t * .11 + 1), 640 + 100 * Math.sin(t * .14)]].forEach(([id, x, y], i) => {
    const c = fogOverride && i < 2 ? hex(fogOverride) : mixA(prev[1][i], cur[1][i], p);
    const el = document.getElementById(id), bgs = `radial-gradient(ellipse at center,rgba(${c.join(',')},.42),transparent 64%)`;
    if (el._bg !== bgs) { el.style.background = bgs; el._bg = bgs; }
    pose(el, { x, y, o: lerp(prev[2], cur[2], p) * dark });
  });
  [0, 1, 2].forEach(i => pose(document.getElementById('bm' + i), { x: 300 + i * 560 + Math.sin(t * .3 + i) * 60, r: -18 + i * 18 + Math.sin(t * .4 + i * 2) * 6,
    o: (.55 + .35 * Math.sin(t * 1.3 + i * 2.1)) * dark * (t < B(2) ? P(t, .5, B(1.5)) : 1) }));
  // bande del formato cinema: all'inizio e durante il semaforo
  const lb = (t < B(8) ? 1 - P(t, B(8), .5, E.inOut) : 0) + (t >= B(48) - .3 && t < B(50) ? P(t, B(48) - .3, .3, E.inOut) : 0);
  const h = 140 * clamp(lb);
  document.getElementById('lb1').style.height = h + 'px'; document.getElementById('lb2').style.height = h + 'px';
  document.getElementById('grain').style.backgroundImage = GRAIN[Math.floor(t * 24) % 4];
  let f = 0; for (const [tf, a] of FLASHES) if (t >= tf) f = Math.max(f, a * (1 - P(t, tf, .45, E.out)));
  const fe = document.getElementById('flash'); fe.style.opacity = f.toFixed(3); fe.style.display = f > .002 ? '' : 'none';
  let fl = 0, fy = 540; for (const [tf, y] of FLARES) if (t >= tf && t < tf + .9) { const v = 1 - P(t, tf, .9, E.out); if (v > fl) { fl = v; fy = y; } }
  pose(document.getElementById('fl'), { y: fy - 540 + 537, sx: lerp(.2, 1.2, 1 - fl), o: fl });
}
const mixA = (a, b, p) => { const A = hex(a), C = hex(b); return A.map((v, i) => Math.round(lerp(v, C[i], p))); };
/* aberrazione cromatica che si spegne dopo l'impatto */
function chroma(el, t, t0, k = 9) {
  const v = t >= t0 ? Math.exp(-(t - t0) * 7) * k : 0;
  el.style.textShadow = v > .3 ? `${-v}px 0 rgba(255,40,90,.75),${v}px 0 rgba(40,220,255,.75)` : '';
}
/* testo che si adatta alla larghezza disponibile (misurato una volta, a font caricati) */
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
function spec(root, t, t0, w) { const p = P(t, t0, .8, E.inOut); pose(root.querySelector('.spec'), { x: lerp(-.6 * w, 1.4 * w, p), o: t >= t0 && p < 1 ? 1 : 0 }); }

// ---------- stato finale delle sei schermate (nel trailer sono animate, qui posano ferme) ----------
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

// =====================================================================================
// 1 · APERTURA (battute 0–8): linea di luce, dettagli "macro" dell'app, tre parole
// =====================================================================================
{
  const SHOTS = [ // [schermata, tema, punto a fuoco nello schermo, scala, spostamento]
    [0, st('glass', false, 'blue'), [150, 300], 3.0, 70], [2, st('glass', true, 'violet'), [120, 300], 3.1, -70],
    [3, st('mat', false, 'green'), [120, 450], 3.2, 60], [4, st('glass', false, 'teal'), [190, 320], 2.7, -60],
  ];
  const SCR = [scrCircolari, scrAssistant, scrCalendario, scrSondaggi, scrMappaPosti, scrBacheca];
  const shotHTML = (k, s) => `<div class="shot" style="width:406px;height:876px"><div class="${appCls(s)}" style="${appVars(s)}">${SCR[k]().inner}</div></div>`;
  const S = addScene(0, B(8) + .05, `
    <div class="a" id="line" style="left:260px;top:538px;width:1400px;height:4px;border-radius:2px;background:linear-gradient(90deg,transparent,#DCE6FF 30%,#fff 50%,#DCE6FF 70%,transparent);box-shadow:0 0 30px 6px rgba(120,160,255,.55)"></div>
    <div class="a center cap" id="pres" style="left:0;width:1920px;top:470px">AILA presenta</div>
    ${SHOTS.map(([k, s]) => `<div class="macro mc"><div class="layer soft">${shotHTML(k, s)}</div><div class="layer sharp">${shotHTML(k, s)}</div></div>`).join('')}
    <div class="a center slam w3" style="left:0;width:1920px;top:380px">Quest'anno</div>
    <div class="a center slam w3" style="left:0;width:1920px;top:380px">si cambia</div>
    <div class="a center slam w3" style="left:0;width:1920px;top:380px;font-size:260px"><em class="grad">passo.</em></div>`);
  const mcs = S.qa('.mc'), w3 = S.qa('.w3');
  // le schermate dei dettagli nel loro stato finale
  mcs.forEach((m, i) => m.querySelectorAll('.shot').forEach(sh => {
    const d = SCR[SHOTS[i][0]](); FIN[SHOTS[i][0]](sh, d);
  }));
  sfx(0, 'swell');
  [2, 3, 4, 5].forEach((b, i) => { sfx(B(b), 'hit'); hit(B(b), .5, 300 + i * 160); });
  [6, 6.5, 7].forEach(b => sfx(B(b), 'slam'));
  sfx(B(7.75), 'suck');
  S.render = t => {
    const pl = P(t, .2, B(1), E.inOut);
    pose(S.q('#line'), { sx: pl, o: clamp(pl * 3) * (1 - P(t, B(2) - .4, .35)) * (.8 + .2 * Math.sin(t * 9)) });
    io(S.q('#pres'), t, B(1), B(2) - .25, { dy: 0, b: 12, d: .9, e: E.out });
    S.q('#pres').style.letterSpacing = lerp(.6, .42, P(t, B(1), B(1))) + 'em';
    mcs.forEach((m, i) => {
      const [, , [fx, fy], sc, pan] = SHOTS[i], t0 = B(2 + i), on = t >= t0 && t < t0 + BAR;
      if (!on) { pose(m, { o: 0 }); return; }
      const p = (t - t0) / BAR, s = sc * (1 + .07 * p);
      pose(m, { o: 1 });
      m.querySelectorAll('.shot').forEach(sh => { sh.style.transform = `translate(${960 - fx * s + pan * (p - .5)}px,${540 - fy * s}px) scale(${s})`; });
    });
    // tre parole sbattute, poi tutto viene risucchiato
    w3.forEach((el, i) => {
      const tin = B([6, 6.5, 7][i]), tout = i < 2 ? B([6.5, 7][i]) - .06 : B(7.75);
      slam(el, t, tin, tout, i === 2 ? { os: 3 } : {});
      chroma(el, t, tin, 12);
    });
  };
}

// =====================================================================================
// 2 · DROP (battute 8–10): logo e titolo della stagione
// =====================================================================================
{
  const S = addScene(B(8), B(10) + .05, `<div class="layer" id="g">
    <div class="a" id="logo" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('I')}</div>
    <div class="a center" id="wm" style="left:0;width:1920px;top:480px;font-size:250px;font-weight:800;letter-spacing:.04em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
    <div class="a center cap" id="ss" style="left:0;width:1920px;top:780px">La squadra · Stagione 2026/27</div></div>`);
  const wm = S.q('#wm'); wm._w = [...wm.querySelectorAll('.w')];
  sfx(B(8), 'impact'); hit(B(8), 1);
  S.render = t => {
    renderLogo(S.q('#logo'), t, B(8) - .05, .55);
    wm._w.forEach((w, i) => io(w, t, B(8) + .2 + i * Q / 4, null, { dy: 0, s0: 1.9, b: 26, d: .28 }));
    chroma(wm, t, B(8) + .2, 10);
    io(S.q('#ss'), t, B(9), null, { dy: 0, b: 10, d: .6 });
    const po = P(t, B(10) - Q, Q, E.in);
    pose(S.q('#g'), { o: 1 - po, s: lerp(1, 1.25, po), b: po * 18, x: Math.sin(t * 80) * 14 * po });
  };
}

// =====================================================================================
// 3 · LA SQUADRA (battute 10–34): sei funzioni, quattro battute ciascuna
// =====================================================================================
const CARDS = [
  { name: 'Circolari', desc: "Spiegate dall'AI", kind: 'iphone', s: st('glass', false, 'blue'), scr: scrCircolari, col: '#5A8CFF',
    stats: [["15'", 'controllo del registro'], ['3', 'categorie per la tua classe'], ['AI', 'riassunto e scadenze']] },
  { name: 'Assistant', desc: 'AILA', kind: 'droid', s: st('mat', true, 'blue'), scr: scrAssistant, col: '#22D3EE',
    stats: [['IT', 'chiedi in italiano'], ['FONTI', 'sempre da aprire'], ['STORICO', 'conversazioni salvate']] },
  { name: 'Calendario', desc: 'Ogni scadenza', kind: 'iphone', s: st('glass', true, 'violet'), scr: scrCalendario, col: '#A78BFA',
    stats: [['1ª–6ª', 'ore di lezione'], ['AUTO', 'scadenze dalle circolari'], ['CLASSE', 'eventi condivisi']] },
  { name: 'Sondaggi', desc: 'Interrogazioni', kind: 'droid', s: st('mat', false, 'green'), scr: scrSondaggi, col: '#34D399',
    stats: [['EQUO', 'sistema di voti'], ['ANTI-FURBI', 'pensato per non essere aggirato'], ['10', 'opzioni nei sondaggi a classifica']] },
  { name: 'Mappa posti', desc: 'Il posto giusto', kind: 'iphone', s: st('glass', false, 'teal'), scr: scrMappaPosti, col: '#2DD4BF',
    stats: [['3', 'disposizioni dei banchi'], ['4', 'affinità, aiuto, rumore, altezza'], ['SU MISURA', 'file e posti per fila']] },
  { name: 'Bacheca', desc: 'La tua voce', kind: 'droid', s: st('mat', true, 'orange'), scr: scrBacheca, col: '#FB923C',
    stats: [['ANONIMA', 'se vuoi'], ['VOTI', 'e commenti'], ['TUTELA', 'anonimato svelato solo se c\'è un abuso']] },
];
CARDS.forEach((c, i) => {
  const t0 = B(10 + i * 4), t1 = t0 + B(4), R = i % 2 === 0; // R: telefono a destra
  const d = c.scr();
  FOG.push([t0, [c.col, '#1E1B4B', c.col], .85]);
  const S = addScene(t0 - .05, t1 + .02, `<div class="layer" id="cam">
    <div class="a" id="glow" style="left:${R ? 900 : -60}px;top:-40px;width:1100px;height:1100px;border-radius:50%;background:radial-gradient(circle,${c.col}55,transparent 62%)"></div>
    <div class="a num" id="num" style="${R ? 'left:470px' : 'left:640px'};top:120px"><span class="sk">0${i + 1}</span></div>
    <div class="a" id="ph" style="left:${R ? 1250 : 240}px;top:90px">${devHTML(c.kind, c.s, d.inner + SPEC)}</div>
    <div class="a tag" id="tg" style="${R ? 'left:110px' : 'right:110px'};top:150px">N° <b>0${i + 1}</b> &nbsp;·&nbsp; Stagione 2026/27</div>
    <div class="a${R ? '' : ' right'}" style="${R ? 'left:110px' : 'left:800px;width:1010px;text-align:right'};top:500px">
      <div class="desc" id="ds">${c.desc}</div>
      <div class="name" id="nm" style="margin-top:20px"><span class="sk">${c.name}</span></div>
      <div class="stripe" id="sp" style="${R ? 'left:0' : 'right:0'};top:262px;width:620px;background:linear-gradient(90deg,${c.col},${c.col}00)${R ? '' : ';transform-origin:100% 50%'}"></div>
      <div class="stats" style="margin-top:104px">${c.stats.map(([v, l]) => `<div class="stat"><div class="v" style="color:${c.col}">${v}</div><div class="l">${l}</div></div>`).join('')}</div>
    </div></div>`);
  FIN[i](S.el, d);
  const st_ = S.qa('.stat');
  if (!R) S.q('#sp').style.transformOrigin = '100% 50%'; else S.q('#sp').style.transformOrigin = '0 50%';
  sfx(t0 - .2, 'whoosh'); sfx(t0, 'braam'); hit(t0, .55, R ? 820 : 300);
  sfx(t0 + Q * 3, 'slam'); st_.forEach((_, k) => sfx(t0 + Q * (6 + k), 'tick'));
  sfx(t1 - Q / 2, 'glitch');
  S.render = t => {
    const pin = P(t, t0, Q * 3, E.out5), pout = P(t, t1 - Q / 2, Q / 2, E.in);
    // macchina da presa: lenta spinta in avanti, uscita con scatto e sfocatura
    pose(S.q('#cam'), { s: 1 + .035 * (t - t0) / B(4), o: 1 - pout, b: pout * 22, x: pout * (R ? -160 : 160) + (pout > 0 ? Math.sin(t * 120) * 18 * pout : 0) });
    pose(S.q('#glow'), { o: P(t, t0, Q * 2) * (.85 + .15 * Math.sin(t * 2.4)), s: lerp(.6, 1, pin) });
    const pn = P(t, t0, .35, E.out5);
    pose(S.q('#num'), { o: clamp(pn * 2), s: lerp(1.3, 1, pn), b: (1 - pn) * 20, x: -(t - t0) * 6 * (R ? 1 : -1) });
    const idle = Math.sin((t - t0) * 1.1) * 6;
    pose(S.q('#ph'), { y: (1 - pin) * 300 + idle, ry: (R ? -1 : 1) * lerp(38, 10, pin), rx: lerp(14, 3, pin), o: clamp(pin * 1.6), s: lerp(.86, .96, pin) });
    spec(S.q('#ph'), t, t0 + Q * 1.5, 430); if (t > t0 + B(2.5)) spec(S.q('#ph'), t, t0 + B(2.5), 430);
    io(S.q('#tg'), t, t0 + Q, null, { dx: R ? -40 : 40, dy: 0, b: 6 });
    // descrizione: lampeggia come un'insegna che si accende
    const fd = t >= t0 + Q * 2 ? (t < t0 + Q * 2.6 ? (Math.floor(t * 30) % 2 ? .25 : 1) : 1) : 0;
    pose(S.q('#ds'), { o: fd }); S.q('#ds').style.letterSpacing = lerp(.6, .34, P(t, t0 + Q * 2, Q * 2)) + 'em';
    const nm = S.q('#nm'); fitText(nm, 1000);
    slam(nm, t, t0 + Q * 3, null, { s0: 1.35, d: .2 });
    chroma(nm, t, t0 + Q * 3, 14);
    if (t > t0 + Q * 3 && nm._vis === 1) { const k = Math.exp(-(t - t0 - Q * 3) * 12) * 16; nm.style.transform += ` translate(${Math.sin(t * 97) * k}px,${Math.cos(t * 83) * k}px)`; }
    pose(S.q('#sp'), { sx: P(t, t0 + Q * 3, .35, E.out5), o: t >= t0 + Q * 3 ? 1 : 0 });
    st_.forEach((el, k) => io(el, t, t0 + Q * (6 + k), null, { dy: 24, d: .3, b: 8 }));
  };
});

// =====================================================================================
// 4 · DISPOSITIVI (battute 34–40)
// =====================================================================================
{
  const DEV = [ // [tipo, tema, schermata, nome, scala da protagonista, posizione in fila, scala in fila]
    ['droid', st('mat', false, 'blue'), homePhone('Chiara'), 'Android', .9, [800, 640], .56],
    ['iphone', st('glass', false, 'violet'), homePhone('Marco'), 'iPhone', .9, [1120, 640], .56],
    ['tab', st('mat', true, 'green'), homeWide('Luca', false), 'Tablet', .78, [500, 440], .6],
    ['ipad', st('glass', true, 'blue'), homeWide('Sara', true), 'iPadOS', .8, [1420, 430], .6],
  ];
  const W = { droid: [420, 900], iphone: [430, 900], ipad: [1180, 830], tab: [1240, 790] };
  FOG.push([B(34), ['#3B82F6', '#8B5CF6', '#06B6D4'], .7]);
  const S = addScene(B(34), B(40) + .05, `
    <div class="a center slam" id="s1" style="left:0;width:1920px;top:400px">Su ogni</div>
    <div class="a center slam" id="s2" style="left:0;width:1920px;top:400px"><em class="grad">schermo.</em></div>
    ${DEV.map(d => `<div class="a num dn" style="-webkit-text-stroke-color:rgba(255,255,255,.26);"left:0;width:1920px;text-align:center;top:${d[0] === 'droid' || d[0] === 'iphone' ? 250 : 120}px;font-size:${d[0] === 'droid' || d[0] === 'iphone' ? 420 : 380}px;text-transform:none"><span class="sk">${d[3]}</span></div>`).join('')}
    ${DEV.map(d => `<div class="a dv" style="left:${960 - W[d[0]][0] / 2}px;top:${540 - W[d[0]][1] / 2}px">${devHTML(d[0], d[1], d[2] + SPEC)}</div>`).join('')}
    ${DEV.map((d, i) => `<div class="a tag dlab" style="left:0;width:1920px;text-align:center;top:${d[0] === 'droid' || d[0] === 'iphone' ? 1000 : 1010}px">Dispositivo <b>0${i + 1}</b> / 04</div>`).join('')}
    <div class="a center" id="ttl" style="left:0;width:1920px;top:70px;font-size:66px;font-weight:800;letter-spacing:-.03em;text-transform:uppercase">Stessa app. <em class="grad">Ovunque.</em></div>`);
  const dn = S.qa('.dn'), dv = S.qa('.dv'), dlab = S.qa(".dlab");
  sfx(B(34), 'slam'); sfx(B(34.5), 'slam');
  DEV.forEach((_, i) => { sfx(B(35 + i), 'hit'); hit(B(35 + i), .45, 200 + i * 220); });
  sfx(B(39), 'impactS'); DEV.forEach((_, i) => sfx(B(39) + i * Q / 2, 'pop'));
  S.render = t => {
    slam(S.q('#s1'), t, B(34), B(34.5) - .05); chroma(S.q('#s1'), t, B(34), 12);
    slam(S.q('#s2'), t, B(34.5), B(35) - .1); chroma(S.q('#s2'), t, B(34.5), 12);
    DEV.forEach((d, i) => {
      const t0 = B(35 + i), hero = t >= t0 && t < t0 + BAR;
      const pin = P(t, t0, .55, E.out5), pex = P(t, t0 + BAR - .2, .2, E.in);
      // da protagonista: entra ruotando, il nome gigante dietro
      if (hero) {
        pose(dv[i], { s: d[4] * lerp(.8, 1, pin), ry: lerp(75, -8, pin) + (t - t0) * 2, rx: lerp(10, 3, pin), o: clamp(pin * 2) * (1 - pex), b: pex * 16, x: -pex * 300, y: Math.sin(t) * 6 });
        spec(dv[i], t, t0 + .25, W[d[0]][0]);
      }
      pose(dn[i], { o: hero ? clamp(pin * 2) * (1 - pex) : 0, s: lerp(1.25, 1, pin), x: -(t - t0) * 40 });
      io(dlab[i], t, t0 + Q, t0 + BAR - .2, { dy: 0, b: 6 });
      // tutti in fila, a scatti sugli ottavi
      if (t >= B(39)) {
        const pl = P(t, B(39) + i * Q / 2, .4, E.back);
        pose(dv[i], { x: d[5][0] - 960, y: d[5][1] - 540 + Math.sin(t * 1.2 + i) * 6, s: d[6] * pl, o: clamp(pl * 2) * (1 - P(t, B(40) - .2, .2)), ry: (i % 2 ? -1 : 1) * 6 });
        dv[i].style.zIndex = d[0] === 'droid' || d[0] === 'iphone' ? 3 : 1;
      } else if (!hero) pose(dv[i], { o: 0 });
    });
    io(S.q('#ttl'), t, B(39) + Q, B(40) - .2, { dy: -20, b: 8 });
  };
}

// =====================================================================================
// 5 · LIVREE (battute 40–44): stile, tema e colore, come una vernice nuova
// =====================================================================================
{
  const SEQ = [ // [categoria, nome, stato]
    ['Stile', 'Liquid Glass', st('glass', false, 'blue')], ['Stile', 'Material', st('mat', false, 'blue')],
    ['Tema', 'Chiaro', st('glass', false, 'blue')], ['Tema', 'Scuro', st('glass', true, 'blue')],
    ...ACC_KEYS.map((k, i) => ['Colore', ACC[k][0], st(i % 2 ? 'mat' : 'glass', i % 3 !== 2, k)]),
  ];
  const TT = SEQ.map((_, i) => i < 4 ? B(40) + i * Q * 2 : B(42) + (i - 4) * Q);
  const S = addScene(B(40), B(44) + .05, `
    <div class="a" id="ph" style="left:1180px;top:90px">${devHTML('iphone', SEQ[0][2], `<div class="app" id="la">${homePhone('Chiara')}</div><div class="app" id="lb">${homePhone('Chiara')}</div>${SPEC}`)}</div>
    <div class="a" style="left:120px;top:400px;width:1000px">
      <div class="desc" id="cat"></div>
      <div class="name" id="val" style="margin-top:22px;font-size:180px"><span class="sk" id="vt"></span></div>
      <div class="stripe" id="sp" style="left:0;top:250px;width:560px;transform-origin:0 50%"></div></div>
    <div class="a center slam" id="l24" style="left:0;width:1920px;top:400px">24 livree.</div>`);
  const la = S.q('#la'), lb = S.q('#lb');
  TT.forEach((tt, i) => { sfx(tt, i < 4 ? 'slam' : 'switch'); if (i < 4) hit(tt, .3, 760); });
  sfx(B(43.5), 'slam');
  S.render = t => {
    let k = 0; while (k < SEQ.length - 1 && t >= TT[k + 1]) k++;
    const [cat, name, cur] = SEQ[k], prev = SEQ[Math.max(0, k - 1)][2], p = k ? P(t, TT[k], .12) : 1;
    fogOverride = t < B(43.5) && k >= 4 ? ACC_COL(cur.acc) : null;
    applyState(la, prev); applyState(lb, cur);
    la.style.display = p < 1 ? '' : 'none'; lb.style.opacity = p < 1 ? p.toFixed(3) : '';
    const pin = P(t, B(40), .6, E.out5), pout = P(t, B(43.5) - .15, .3, E.in);
    pose(S.q('#ph'), { y: (1 - pin) * 260 + Math.sin(t * 1.3) * 6, ry: -14 + Math.sin(t * .9) * 8, rx: 3, s: .96 * (1 + (k ? Math.exp(-(t - TT[k]) * 10) * .03 : 0)), o: clamp(pin * 2) * (1 - pout), b: pout * 14 });
    spec(S.q('#ph'), t, TT[k], 430);
    setText(S.q('#cat'), cat); setText(S.q('#vt'), name);
    const v = S.q('#val'); fitText(v, 960); slam(v, t, TT[k], B(43.5) - .1, { d: .16, s0: 1.25 }); chroma(v, t, TT[k], 10);
    pose(S.q('#cat'), { o: 1 - pout });
    const sp = S.q('#sp'); sp.style.background = `linear-gradient(90deg,${ACC_COL(cur.acc)},${ACC_COL(cur.acc)}00)`;
    pose(sp, { sx: P(t, TT[k], .3, E.out5), o: 1 - pout });
    slam(S.q('#l24'), t, B(43.5), B(44) - .05, { os: 2.4 }); chroma(S.q('#l24'), t, B(43.5), 12);
  };
}

// =====================================================================================
// 6 · GRIGLIA DI PARTENZA (battute 44–48): le 24 combinazioni schierate
// =====================================================================================
{
  const ROWS = [st('glass', false), st('glass', true), st('mat', false), st('mat', true)];
  const combos = [];
  ACC_KEYS.forEach((a, ci) => ROWS.forEach((r, ri) => combos.push([ri, st(r.style, r.dark, a), ci])));
  const NM = ['Chiara', 'Marco', 'Sara', 'Luca', 'Anna', 'Leo'];
  FOG.push([B(44), ['#3B82F6', '#8B5CF6', '#06B6D4'], .8]);
  const S = addScene(B(44), B(48) + .05, `<div class="a" id="floor" style="left:960px;top:560px;width:0;height:0">
    ${combos.map(([ri, s, ci], g) => { const col = g % 2, row = Math.floor(g / 2), x = col ? 170 : -470, y = row * 760 + (col ? 380 : 0);
      return `<div class="a gc" style="left:${x}px;top:${y}px">
        <div class="slot" style="left:0;top:660px"></div><div class="slotn" style="left:${col ? 320 : -120}px;top:560px">${String(g + 1).padStart(2, '0')}</div>
        <div class="a" style="left:0;top:0;transform:scale(.7);transform-origin:0 0">${devHTML(ri < 2 ? 'iphone' : 'droid', s, homePhone(NM[ci]))}</div></div>`; }).join('')}</div>
    <div class="layer" style="background:linear-gradient(180deg,rgba(4,5,8,.92) 0%,rgba(4,5,8,.4) 32%,transparent 55%)"></div>
    <div class="a center slam" id="g1" style="left:0;width:1920px;top:80px;font-size:120px">24 combinazioni.</div>
    <div class="a center slam" id="g2" style="left:0;width:1920px;top:80px;font-size:120px"><em class="grad">La griglia è completa.</em></div>`);
  const gc = S.qa('.gc');
  sfx(B(44), 'impactS'); hit(B(44), .6, 300); sfx(B(45), 'slam'); sfx(B(46.5), 'slam');
  S.render = t => {
    const p = (t - B(44)) / B(4);
    // la macchina da presa scorre sopra la griglia, dalle ultime file verso la prima
    S.q('#floor').style.transform = `perspective(1300px) rotateX(${lerp(62, 54, p)}deg) translateY(${lerp(-7200, -3000, E.inOut(clamp(p)))}px) rotateZ(${lerp(-6, 4, p)}deg) scale(.82)`;
    gc.forEach((el, g) => { const pg = P(t, B(44) + (23 - g) * .035, .35, E.back); pose(el, { o: clamp(pg * 2), s: lerp(.7, 1, pg) }); });
    slam(S.q('#g1'), t, B(45), B(46.5) - .08); chroma(S.q('#g1'), t, B(45), 10);
    slam(S.q('#g2'), t, B(46.5), B(48) - .3); chroma(S.q('#g2'), t, B(46.5), 10);
    S.el.style.opacity = (1 - P(t, B(48) - .35, .3)).toFixed(3);
  };
}

// =====================================================================================
// 7 · SEMAFORO (battute 48–50): i sei banchi del logo si accendono, poi si spengono tutti
// =====================================================================================
{
  const POS = [[0, 0], [0, 1], [1, 0], [1, 1], [2, 0], [2, 1]]; // ordine di accensione: colonna per colonna
  const S = addScene(B(48), B(50) + .02, `${POS.map(([c, r]) => `<div class="lamp" style="left:${960 - 95 + (c - 1) * 250}px;top:${200 + r * 200}px"><i></i></div>`).join('')}`);
  const lamps = S.qa('.lamp i');
  POS.forEach((_, k) => sfx(B(48) + k * Q, 'light' + k));
  S.render = t => {
    lamps.forEach((el, k) => { const tk = B(48) + k * Q, on = t >= tk && t < B(50); pose(el, { o: on ? 1 : 0, s: on ? lerp(1.12, 1, P(t, tk, .15)) : 1 }); });
  };
}

// =====================================================================================
// 8 · VIA! (battute 50–60): logo, QR code e piattaforme
// =====================================================================================
{
  const q = QR, cell = 12, size = q.n * cell;
  const qrSVG = `<svg width="${size}" height="${size}" viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges"><path fill="#0A1330" d="${q.cells.map(([x, y]) => `M${x} ${y}h1v1h-1z`).join('')}"/></svg>`;
  FOG.push([B(50), ['#3B82F6', '#8B5CF6', '#06B6D4'], 1]);
  const S = addScene(B(50), DURATION + 1, `
    <div class="layer" id="lg">
      <div class="a" id="logo2" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('F')}</div>
      <div class="a center" id="wm2" style="left:0;width:1920px;top:470px;font-size:210px;font-weight:800;letter-spacing:.05em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
      <div class="a center" id="tag2" style="left:0;width:1920px;top:710px;font-size:46px;font-weight:500;color:#DCE3FF">La tua scuola, <em class="grad" style="font-weight:700">sincronizzata.</em></div>
      <div class="a" id="plat" style="left:0;width:1920px;top:820px;display:flex;justify-content:center;gap:16px">
        ${[['smartphone', 'Android'], ['smartphone', 'iPhone'], ['tablet', 'Tablet'], ['tablet', 'iPadOS']].map(([ic, l]) => `<span class="chip pl">${I(ic, 'style="color:#A5B8FF"')}${l}</span>`).join('')}</div>
    </div>
    <div class="a" id="qr" style="left:1210px;top:190px;width:520px">
      <div class="center" style="font-size:54px;font-weight:800;letter-spacing:-.02em;text-transform:uppercase">Installala <em class="grad">ora.</em></div>
      <div class="qrbox" style="margin:30px auto 0;width:${size + 80}px">${qrSVG}</div>
      <div class="center" style="font-size:27px;font-weight:600;color:#DCE3FF;margin-top:30px">${I('scan-line', 'style="width:30px;height:30px;vertical-align:-7px;color:#9DB6FF"')}&nbsp; Inquadra il codice</div>
      <div class="center" style="font-size:22px;color:#A9B5DA;margin-top:10px">Ti serve solo il codice della tua classe.</div>
    </div>
    <div class="a center" id="cred" style="left:0;width:1920px;top:1000px;font-size:23px;color:#6E7AA3">Un progetto di Simone Bianchin</div>`);
  const wm = S.q('#wm2'); wm._w = [...wm.querySelectorAll('.w')];
  const pl = S.qa('.pl');
  sfx(B(50), 'impact'); hit(B(50), 1);
  sfx(B(51.5), 'whoosh'); pl.forEach((_, i) => sfx(B(52) + Q * 2 + i * Q / 2, 'pop'));
  S.render = t => {
    const t0 = B(50);
    renderLogo(S.q('#logo2'), t, t0 - .05, .5);
    pose(S.q('#logo2'), { y: Math.sin(t * 1.4) * 6 });
    wm._w.forEach((w, i) => io(w, t, t0 + .15 + i * Q / 4, null, { dy: 0, s0: 1.9, b: 26, d: .28 }));
    chroma(wm, t, t0 + .15, 12);
    io(S.q('#tag2'), t, B(51), null, { dy: 30 });
    const pm = P(t, B(51.5), .8, E.inOut);
    pose(S.q('#lg'), { x: -380 * pm, y: 40 * pm, s: lerp(1, .86, pm) });
    io(S.q('#qr'), t, B(51.5) + .15, null, { dx: 260, dy: 0, d: .7, b: 16, s0: .92 });
    pl.forEach((el, i) => io(el, t, B(52) + Q * 2 + i * Q / 2, null, { dy: 24, s0: .6, e: E.back, d: .35, b: 4 }));
    io(S.q('#cred'), t, B(53.5), null, { dy: 14 });
  };
}

// ---------- motore ----------
FOG.sort((a, b) => a[0] - b[0]);
function seek(t) {
  fogOverride = null;
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
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
