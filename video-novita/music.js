// Colonna sonora del video delle novità, sintetizzata da zero (nessun campione esterno): pulita e leggera,
// da presentazione di prodotto. 96 BPM, re maggiore. Tappeto morbido, marimba in ottavi, accordi di pianoforte,
// batteria appena accennata e sub; nei titoli resta solo il tappeto. La parte sulla Gita gira su Sol – Re – Mi m – La.
// Gli effetti sonori arrivano da sfx.json (node render.js sfx), già nei tempi del video.
//   node music.js  -> build/music.wav
const fs = require('fs');
const TL = require('./timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const DUR = TL.DURATION;
const SFX = JSON.parse(fs.readFileSync('sfx.json', 'utf8'));
const { SR, N, L: LL, has, put, mtof, kick, clap, hat, bell, piano, sweep, boom, click, blip, e808, mixSub, finish } = require('../video-trailer/synth')(DUR);
const STEP = BEAT / 4, TAU = Math.PI * 2;
// ONLY=pad,drums,arp,bass,piano,fx esporta solo quelle parti (per controllare i livelli); LEVELS=1 stampa il volume per battuta
const ON = g => !process.env.ONLY || process.env.ONLY.split(',').includes(g);

// ---------------- strumenti in più ----------------
/* tappeto: ogni nota ha due voci leggermente scordate, una per lato; attacco e rilascio lenti */
function pad(t0, t1, notes, amp = 1) {
  const att = 1.5, rel = 2.2, a0 = t0 - att * .4, i0 = Math.round(a0 * SR), i1 = Math.round((t1 + rel) * SR);
  notes.forEach((m, n) => [-1, 1].forEach(side => {
    const f = mtof(m) * Math.pow(2, side * 5 / 1200), lfo = .13 + n * .041, ph0 = n * 1.7 + side;
    for (let i = Math.max(0, i0); i < Math.min(N, i1); i++) {
      const t = i / SR, x = TAU * f * t;
      const env = Math.min(1, Math.max(0, (t - a0) / att)) * Math.min(1, Math.max(0, (t1 + rel - t) / rel));
      const v = (Math.sin(x) + .3 * Math.sin(2 * x + ph0) + .09 * Math.sin(3 * x)) * (.8 + .2 * Math.sin(TAU * lfo * t + ph0));
      put(i, v * env * env * .024 * amp, side * .7, .6);
    }
  }));
}
/* marimba: fondamentale più un parziale acuto che si spegne subito */
function mar(t0, m, amp = 1, pan = 0) {
  const f = mtof(m), i0 = Math.round(t0 * SR), len = Math.round(.9 * SR);
  for (let k = 0; k < len; k++) {
    const t = k / SR, a = Math.min(1, t / .002);
    const v = Math.sin(TAU * f * t) * Math.exp(-t * 7) + .45 * Math.sin(TAU * f * 3.93 * t) * Math.exp(-t * 34) + .12 * Math.sin(TAU * f * 9.2 * t) * Math.exp(-t * 70);
    put(i0 + k, v * a * amp, pan, .4);
  }
}
/* schiocco di dita al posto del rullante */
function snap(t0, amp = 1) { clap(t0, .2 * amp); click(t0, 2100, .1 * amp, .02); }

// ---------------- armonia ----------------
const CH = {
  D: { sub: 38, pad: [57, 62, 64, 66], arp: [62, 66, 69, 74, 76] },      // Re add9
  Bm: { sub: 35, pad: [57, 59, 62, 66], arp: [59, 62, 66, 69, 74] },     // Si m7
  G: { sub: 31, pad: [55, 59, 62, 66], arp: [62, 66, 67, 71, 74] },      // Sol maj7
  A: { sub: 33, pad: [57, 61, 64, 69], arp: [61, 64, 69, 73, 76] },      // La
  As: { sub: 33, pad: [57, 62, 64, 69], arp: [62, 64, 69, 74, 76] },     // La sus4
  DF: { sub: 30, pad: [54, 57, 62, 66], arp: [62, 66, 69, 74, 78] },     // Re/Fa#
  Em: { sub: 28, pad: [55, 59, 62, 64], arp: [59, 64, 67, 71, 74] },     // Mi m7
  Dend: { sub: 26, pad: [50, 57, 62, 64, 66, 69], arp: [62, 66, 69, 74, 76] },
};
const SEQ = ['D', 'Bm', 'G',                                               // 0–3   apertura
  'D', 'As',                                                               // 3–5   titolo web
  'D', 'Bm', 'G', 'A', 'D', 'Bm', 'G', 'A', 'D', 'As',                     // 5–15  browser e installazione
  'D', 'Bm', 'G', 'A',                                                     // 15–19 in breve
  'G', 'DF',                                                               // 19–21 titolo gita
  'G', 'DF', 'Em', 'A', 'G', 'A',                                          // 21–27 documenti
  'G', 'DF', 'Em', 'A',                                                    // 27–31 storico e segnalazioni
  'G', 'DF', 'Em', 'As', 'A',                                              // 31–36 assistente
  'Dend', 'Dend', 'Dend', 'Dend'];                                         // 36–40 finale
const chordAt = t => CH[SEQ[Math.min(SEQ.length - 1, Math.max(0, Math.floor(t / BAR + 1e-6)))]];

// ---------------- arrangiamento (in battute) ----------------
const STOP = t => has(t, B(14.5), B(15)) || has(t, B(18.5), B(19)) || has(t, B(35.5), B(36));
const DRUMS = t => (has(t, B(5), B(19)) || has(t, B(23), B(31)) || has(t, B(32), B(36))) && !STOP(t);
const LIGHT = t => has(t, B(21), B(23));                                   // solo cassa e schiocco
const ARP = t => has(t, B(4), B(19)) || has(t, B(21), B(37));
const SHIMMER = t => has(t, B(15), B(18.5)) || has(t, B(27), B(31)) || has(t, B(33), B(35.5));
const BASS = t => has(t, B(5), B(19)) || has(t, B(21), B(36));
const ARP_IDX = [0, 2, 1, 3, 2, 4, 3, 1];

// tappeto: un accordo per battuta, quelli uguali di fila si legano
for (let b = 0; b < SEQ.length;) {
  let e = b; while (e + 1 < SEQ.length && SEQ[e + 1] === SEQ[b]) e++;
  if (ON('pad')) pad(B(b), B(e + 1), CH[SEQ[b]].pad, b >= 36 ? 1.1 : (b < 5 || b === 19 || b === 20) ? 1.3 : 1);
  b = e + 1;
}
for (let s = 0; s * STEP < DUR - 1; s++) {
  const t = s * STEP, st = s % 16, bar = Math.floor(s / 16), ch = chordAt(t);
  if (ON('drums') && (DRUMS(t) || LIGHT(t))) {
    if (st === 0) kick(t, .55, true);
    if (st === 10) kick(t, .42, true);
    if (st === 7 && bar % 2 === 1) kick(t, .26, true);
    if (st === 4 || st === 12) snap(t, LIGHT(t) ? .7 : 1);
  }
  if (ON('drums') && DRUMS(t)) {
    if (st % 2 === 0) hat(t, st % 4 === 2 ? .2 : .11);
    else if (st === 15 && bar % 2 === 1) hat(t, .1);
  }
  if (ON('arp') && ARP(t) && st % 2 === 0 && !STOP(t)) {
    const e = (st / 2) | 0, soft = t < B(5) ? .55 : t >= B(36) ? Math.max(0, 1 - (t - B(36)) / BAR) * .7 : has(t, B(31), B(32)) ? .7 : 1;
    mar(t, ch.arp[ARP_IDX[e]], (e % 4 === 0 ? .17 : .115) * soft, e % 2 ? .35 : -.35);
    if (SHIMMER(t) && e % 2 === 0) mar(t + STEP, ch.arp[ARP_IDX[(e + 3) % 8]] + 12, .05, e % 4 ? -.6 : .6);
  }
  if (ON('bass') && BASS(t) && !STOP(t)) {
    if (st === 0) e808(t, BEAT * 2.2, ch.sub, .5, 0);
    if (st === 10) e808(t, BEAT * 1.2, ch.sub, .36, 0);
  }
}
// pianoforte: l'accordo "strappato" a inizio battuta, più una risposta in levare
for (let b = 0; b < SEQ.length; b++) {
  const t = B(b), ch = CH[SEQ[b]], quiet = b < 3 || b === 19 || b === 20;
  if (b === 0 || STOP(t) || !ON('piano')) continue;
  const amp = b >= 36 ? .2 : quiet ? .12 : .095;
  if (b < 36 || b === 36) ch.pad.forEach((m, i) => piano(t + i * .022, m, amp * (i === ch.pad.length - 1 ? 1.15 : .85), (i - 1.5) * .22));
  if (b >= 5 && b < 36 && !quiet && b % 2 === 1 && !STOP(t + BEAT * 2.5)) [ch.pad[2], ch.pad[3]].forEach((m, i) => piano(t + BEAT * 2.5 + i * .02, m + 12, .06, .2));
}
// motivo: poche note sopra i titoli e nel finale
const motif = (b, notes, amp = .13) => ON('piano') && notes.forEach(([o, m]) => piano(B(b) + o * BEAT, m, amp, .1));
motif(1, [[0, 78], [1.5, 74], [3, 76]]); motif(2, [[0, 74], [2, 78], [3, 81]]);
motif(3, [[1, 81], [2.5, 78]], .11); motif(4, [[0, 76], [1.5, 81], [3, 85]], .11);
motif(19, [[.5, 78], [2, 83], [3, 81]]); motif(20, [[0, 78], [1.5, 81], [3, 86]]);
motif(36, [[2, 78], [3.5, 81]], .15); motif(37, [[1, 86], [2.5, 85], [3.5, 81]], .15); motif(38, [[1, 78], [3, 74]], .12);
// salite prima degli stacchi
if (ON('fx')) {
  sweep(B(4.4), B(5), 300, 7000, .16, 'up', 1.4);
  sweep(B(14.5), B(15), 300, 7000, .14, 'up', 1.4);
  sweep(B(18.4), B(19), 250, 8000, .2, 'up', 1.4);
  sweep(B(35.4), B(36), 250, 8000, .22, 'up', 1.4);
  boom(B(36), .14, 2.2);
}

// ---------------- effetti sonori, nei tempi del video ----------------
let kk = 0;
for (const { t, type } of ON('fx') ? SFX : []) {
  switch (type) {
    case 'logo': bell(t, 74, .1, 0, 2); bell(t + .14, 81, .085, -.2, 2); bell(t + .3, 86, .07, .2, 2); bell(t + .5, 90, .05, 0, 2.4); break;
    case 'whoosh': sweep(t - .25, t + .5, 300, 4200, .13, 'arc', 1.1); break;
    case 'rise': sweep(t - .1, t + .95, 260, 5200, .17, 'arc', 1.1); break;
    case 'gather': sweep(t, t + .95, 5000, 500, .12, 'up', 1.2); break;
    case 'sheet': sweep(t - .1, t + .32, 500, 2400, .07, 'arc', 1.2); break;
    case 'tap': click(t, 1500, .13, .018); break;
    case 'key': click(t, 3000 + (kk++ % 3) * 260, .045, .007); break;
    case 'tick': click(t, 2300, .07, .01); break;
    case 'pop': blip(t, .07, 700, 1500); break;
    case 'open': blip(t, .06, 520, 980); sweep(t - .05, t + .35, 600, 3000, .06, 'arc', 1.2); break;
    case 'answer': blip(t, .05, 900, 1300); break;
    case 'chime': bell(t, 86, .075, .2, 6); bell(t + .09, 93, .065, .2, 6); break;
    case 'done': bell(t, 81, .07, .25, 5); bell(t + .1, 86, .07, .25, 5); bell(t + .2, 93, .06, .25, 5); break;
  }
}

mixSub(.3);
if (process.env.LEVELS) { // volume del mix asciutto, una cifra (in dB) per battuta
  let out = '';
  for (let b = 0; b < SEQ.length; b++) { let e = 0, pk = 0; const i0 = Math.round(B(b) * SR), i1 = Math.round(B(b + 1) * SR);
    for (let i = i0; i < i1 && i < N; i++) { e += LL[i] * LL[i]; pk = Math.max(pk, Math.abs(LL[i])); }
    out += ` ${(10 * Math.log10(e / (i1 - i0) + 1e-12)).toFixed(0)}/${(20 * Math.log10(pk + 1e-9)).toFixed(0)}`; }
  console.log((process.env.ONLY || 'tutto').padEnd(6), out);
}
finish('build/music.wav', { revWet: .36, fadeIn: .5, fadeOut: 2.4, drive: 1.5 });
