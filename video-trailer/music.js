// Colonna sonora del trailer, sintetizzata da zero (nessun campione esterno): stile "trailer cinematografico".
// 128 BPM, la minore (Am – F – C – G, una battuta per accordo): taiko e batteria, archi in ostinato,
// coro, colpi di ottoni ("braam"), un motivo di pianoforte; il finale si apre in La maggiore.
// Gli effetti sonori arrivano da sfx.json (node render.js sfx), già nei tempi del video.
//   node music.js  -> build/music.wav
const fs = require('fs');
const TL = require('./timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const DUR = TL.DURATION;
const SFX = JSON.parse(fs.readFileSync('sfx.json', 'utf8'));
const { SR, N, has, kick, clap, hat, crash, bell, piano, sweep, boom, impact, click, blip, buzz,
  e808, choirChord, braam, mixChoir, mixSub, taiko, spic, pingPong, finish } = require('./synth')(DUR);
const STEP = BEAT / 4; // sedicesimo

// ---------------- armonia ----------------
const CH = [ // [note dell'ostinato: fondamentale, terza, quinta, ottava], coro, sub
  { os: [45, 48, 52, 57], choir: [57, 60, 64, 69, 72], sub: 33 }, // Am
  { os: [41, 45, 48, 53], choir: [53, 57, 60, 65, 69], sub: 29 }, // F
  { os: [48, 52, 55, 60], choir: [55, 60, 64, 67, 72], sub: 36 }, // C
  { os: [43, 47, 50, 55], choir: [55, 59, 62, 67, 71], sub: 31 }, // G
];
const A_MAJ = { os: [45, 49, 52, 57], choir: [57, 61, 64, 69, 73, 76], sub: 33 };
function chordAt(t) {
  const bar = Math.floor(t / BAR + 1e-6);
  if (bar >= 65) return A_MAJ;
  if (bar === 63) return CH[1];
  if (bar === 64) return CH[3];
  return CH[((bar % 4) + 4) % 4];
}

// ---------------- arrangiamento (in battute) ----------------
const STOP = t => has(t, B(5.75), B(6)) || has(t, B(7.75), B(8)) || has(t, B(37.75), B(38));
const DRUMS = t => (has(t, B(8), B(36)) || has(t, B(38), B(56)) || has(t, B(59), B(63))) && !STOP(t);
const HALF = t => has(t, B(56), B(58.5));
const OSTINATO = t => (has(t, B(4), B(5.75)) || has(t, B(8), B(36)) || has(t, B(38), B(58.5)) || has(t, B(59), B(63))) && !STOP(t);
const SHIMMER = t => has(t, B(46), B(56)) || has(t, B(59), B(63)); // archi un'ottava sopra
const MOTIF = t => has(t, B(8), B(12)) || has(t, B(28), B(36)) || has(t, B(38), B(46)) || has(t, B(52), B(56)) || has(t, B(59), B(63));
const KICK = [0, 7, 8, 10], SNARE = [4, 12];
const OST_IDX = [0, 0, 2, 0, 3, 0, 2, 1, 0, 0, 2, 0, 3, 2, 1, 2]; // indici delle note dell'accordo
const OST_ACC = [1, 0, 0, .8, 0, 0, .8, 0, 1, 0, 0, .8, 0, 0, .8, 0]; // accenti 3-3-2

// ---------------- batteria, archi, sub ----------------
const kicks = [];
for (let s = 0; s * STEP < DUR; s++) {
  const t = s * STEP, st = s % 16, bar = Math.floor(s / 16), ch = chordAt(t);
  if (DRUMS(t)) {
    if (KICK.includes(st)) { kick(t, st === 0 ? 1 : .8); kicks.push(t); }
    if (SNARE.includes(st)) { clap(t, 1); taiko(t, .35, 150); }
    if (st === 0) taiko(t, .7, 58);
    if (st === 14 && bar % 2 === 1) taiko(t, .55, 70);
    if (st % 2 === 0) hat(t, st % 4 === 2 ? .7 : .45); else hat(t, .22);
    // stacco di taiko alla fine di ogni frase di quattro battute
    if (bar % 4 === 3 && st >= 12) taiko(t, .4 + (st - 12) * .12, 92 - (st - 12) * 8);
  } else if (HALF(t)) {
    if (st === 0) { taiko(t, 1, 56); kick(t, .8); kicks.push(t); }
    if (st === 8) taiko(t, .6, 64);
  }
  if (OSTINATO(t)) {
    const soft = t < B(8) ? .45 : HALF(t) ? .7 : 1;
    const n = ch.os[OST_IDX[st]], acc = OST_ACC[st];
    spic(t, STEP * .7, n, (.09 + .09 * acc) * soft, st % 2 ? .3 : -.3, t < B(8) ? 1200 : 2600);
    spic(t, STEP * .7, n - 12, (.045 + .04 * acc) * soft, 0, 1100);
    if (SHIMMER(t)) spic(t, STEP * .6, n + 12, .045 + .035 * acc, st % 2 ? -.5 : .5, 4200);
  }
}
// orologio e battito del cuore nell'apertura
for (let b = 0; b * BEAT < B(5.75); b++) click(b * BEAT, b % 2 ? 1700 : 2300, b < 8 ? .14 : .18, .012);
for (let b = 0; B(2) + b * BEAT * 2 < B(5.75); b++) { const t = B(2) + b * BEAT * 2; kick(t, .45 + b * .03, true); kick(t + .19, .28, true); }
// sub: una nota lunga per battuta
for (let bar = 0; bar < S.END; bar++) {
  const t = B(bar);
  if (STOP(t)) continue;
  const fade = t >= B(63) ? Math.max(.25, 1 - (t - B(63)) / B(3)) : 1;
  e808(t, bar >= S.END - 1 ? BAR * .9 : BAR - .03, chordAt(t).sub + 12, (t < B(8) ? .3 : .42) * fade, 0);
}
// rullate di taiko e rullante prima dei drop
function roll(t0, t1, a0, a1) {
  for (let t = t0, step = BEAT / 2; t < t1 - .02; t += step, step = Math.max(BEAT / 8, step * .87)) {
    const p = (t - t0) / (t1 - t0); clap(t, (a0 + (a1 - a0) * p) * .7); taiko(t, a0 + (a1 - a0) * p, 60 + p * 40);
  }
}
roll(B(7), B(7.75), .2, .9); roll(B(37), B(37.75), .25, .9); roll(B(58.5), B(59) - .02, .4, 1);
for (let k = 0; k < 8; k++) taiko(B(52) - BEAT * 2 + k * BEAT / 4, .3 + k * .08, 70 + k * 6);
[B(8), B(38), B(46), B(52), B(59)].forEach(t => crash(t, 1.1));
// braam: sui drop e all'inizio di ogni funzione; sul muro delle combinazioni, uno per battuta
[B(8), B(38), B(46), B(59)].forEach(t => braam(t, 1.1));
for (let f = 1; f < 6; f++) braam(B(12 + f * 4), .55);
for (let k = 0; k < 4; k++) braam(B(52 + k), .7 + k * .1);
// coro: un accordo per battuta, più presente nei momenti grandi
for (let bar = 2; bar < S.END - 1; bar++) {
  const t = B(bar);
  const lvl = t < B(8) ? .7 : has(t, B(52), B(56)) || t >= B(59) ? 1.25 : has(t, B(36), B(38)) || HALF(t) ? 1.1 : .75;
  choirChord(t, bar === S.END - 2 ? BAR * 2 : BAR, chordAt(t + .01).choir, .0105 * lvl);
}
// pianoforte: motivo di quattro battute, ottavi; 0 = pausa
const MOT = [[76, 0, 0, 0, 74, 0, 72, 0], [72, 0, 0, 0, 69, 0, 0, 0], [76, 0, 0, 0, 79, 0, 76, 0], [74, 0, 0, 0, 0, 0, 71, 0]];
for (let bar = 0; bar < S.END; bar++) {
  const t0 = B(bar); if (!MOTIF(t0)) continue;
  MOT[bar % 4].forEach((n, e) => { if (!n) return; const t = t0 + e * BEAT / 2; if (STOP(t)) return; piano(t, n, .36, .15); piano(t, n - 12, .15, -.15); });
}
// la domanda prima del drop: poche note di pianoforte
[[0, 76], [2, 72], [4, 69], [6, 71], [8, 72], [10, 74]].forEach(([o, m]) => piano(B(6) + o * BEAT / 2, m, .22, 0));
// finale: arpeggio di pianoforte e campanelli, chiusura in La maggiore
const UPDOWN = [0, 1, 2, 3, 2, 1, 3, 2];
for (let s = 0; B(63) + s * BEAT / 2 < DUR - 1.2; s++) { const t = B(63) + s * BEAT / 2, ch = chordAt(t); piano(t, ch.os[UPDOWN[s % 8]] + 12, .2, s % 2 ? .3 : -.3); }
[57, 61, 64, 69].forEach((m, i) => piano(B(65), m, .2, (i - 1.5) * .2));
[[0, 81], [1.5, 79], [3, 76], [4, 77], [5.5, 79], [7, 81], [7.5, 85]].forEach(([o, m]) => bell(B(63) + o * BEAT, m, .09, 0, 2.2));
// risers
sweep(B(6), B(7.75), 200, 9000, .5, 'up', 1.4);
sweep(B(36.5), B(37.75), 250, 8000, .45, 'up', 1.4);
sweep(B(50), B(52), 300, 7000, .3, 'up', 1.6);

// ---------------- effetti delle scene ----------------
for (const { t, type } of SFX) {
  switch (type) {
    case 'ping': bell(t, 88, .1, -.3, 6); bell(t + .07, 93, .07, -.3, 6); break;
    case 'ping2': bell(t, 81, .08, .3, 9); bell(t + .05, 76, .06, .3, 9); break;
    case 'buzz': buzz(t); break;
    case 'success': bell(t, 86, .1, 0); bell(t + .09, 93, .1, 0); break;
    case 'impact': impact(t, 1); break;
    case 'impactS': boom(t, .5, 1.8); taiko(t, .7, 55); break;
    case 'slam': taiko(t, .9, 60); boom(t, .3, .8); click(t, 300, .2, .05); break;
    case 'hit': taiko(t, .8, 66); boom(t, .3, 1.2); break;
    case 'suck': sweep(t - .45, t, 6000, 150, .45, 'up', 1.2); break;
    case 'riser': sweep(t - .05, t + BAR / 2 - .05, 400, 9000, .4, 'up', 1.3); break;
    case 'riserS': sweep(t - .05, t + BAR / 2 - .05, 400, 7000, .3, 'up', 1.3); break;
    case 'blip': blip(t, .14); break;
    case 'pop': blip(t, .12, 700, 2100); break;
    case 'drop': blip(t, .14, 1800, 700); taiko(t, .25, 90); break;
    case 'key': click(t, 3400, .07, .007); break;
    case 'tick': click(t, 2000, .09, .01); break;
    case 'stamp': taiko(t, .5, 80); click(t, 900, .18, .03); break;
    case 'shuffle': for (let k = 0; k < 6; k++) click(t + k * .05, 1500 + k * 120, .07, .01); sweep(t - .1, t + .5, 500, 3500, .2, 'arc', 1.2); break;
    case 'send': sweep(t - .1, t + .3, 600, 4500, .2, 'arc', 1.2); break;
    case 'swoosh': sweep(t - .15, t + .4, 400, 3200, .24, 'arc', 1.2); break;
    case 'whoosh': sweep(t - .25, t + .45, 300, 6000, .34, 'arc', 1.1); break;
    case 'crash': crash(t, .7); break;
    case 'switch': click(t, 2600, .09, .008); bell(t, 98, .04, .2, 8); break;
    case 'switchBig': sweep(t - .12, t + .3, 800, 6000, .18, 'arc', 1.2); click(t, 2200, .1, .01); break;
  }
}

// ---------------- mix ----------------
const duck = new Float32Array(N).fill(1); // sidechain leggero del coro sulla cassa
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.3 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .35 * Math.exp(-k / SR / .1));
}
mixChoir(duck, 1, .7);
mixSub(.32);
pingPong(BEAT * .75, .3, .3);
finish('build/music.wav', { revWet: .34, drive: 2.3 });
