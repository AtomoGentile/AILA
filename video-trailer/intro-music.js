// Colonna sonora dell'intro, sintetizzata da zero: 144 BPM in re minore. Cassa dritta e basso a sedicesimi nei
// momenti forti (drop, dispositivi, muro, finale), metà tempo sotto le sei funzioni; archi in ostinato, coro,
// colpi orchestrali all'inizio di ogni funzione.
//   node render.js sfx --page=intro && node intro-music.js  -> build/intro/music.wav
const fs = require('fs');
const TL = require('./intro-timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const DUR = TL.DURATION;
const SFX = JSON.parse(fs.readFileSync('sfx-intro.json', 'utf8'));
const { SR, N, mtof, noise, has, put, biquad, kick, clap, hat, crash, bell, piano, sweep, boom, impact, click, blip,
  e808, choirChord, braam, mixChoir, mixSub, taiko, spic, pingPong, finish } = require('./synth')(DUR);
const STEP = BEAT / 4; // sedicesimo

// ---------------- armonia: Dm – B♭ – Gm – A, una battuta ciascuno ----------------
const CH = [
  { root: 38, os: [50, 53, 57, 62], choir: [62, 65, 69, 74] }, // Dm
  { root: 34, os: [46, 50, 53, 58], choir: [58, 62, 65, 70] }, // B♭
  { root: 43, os: [43, 46, 50, 55], choir: [55, 62, 67, 70] }, // Gm
  { root: 45, os: [45, 49, 52, 57], choir: [57, 61, 64, 69] }, // A
];
const D_MAJ = { root: 38, os: [50, 54, 57, 62], choir: [62, 66, 69, 74, 78] };
const chordAt = t => { const bar = Math.floor(t / BAR + 1e-6); return bar >= S.END - 2 ? D_MAJ : CH[((bar % 4) + 4) % 4]; };

// ---------------- arrangiamento (in battute) ----------------
const GROOVE = t => has(t, B(S.DROP), B(S.TRUST)) || has(t, B(S.OUT), B(S.OUTRO));
const HALF = t => has(t, B(S.CARDS), B(S.DEVICES)) || has(t, B(S.THEMES), B(S.WALL)); // metà tempo
const BREAK = t => has(t, B(S.TRUST), B(S.OUT));

// ---------------- strumenti propri dell'intro ----------------
const BASS = new Float32Array(N); // basso a sedicesimi, va sotto il sidechain
function bass16(t0, midi, amp) {
  const i0 = Math.round(t0 * SR), len = Math.round(STEP * .95 * SR), f = mtof(midi), lp = biquad('lp', 1200, 1.2);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    if (k % 16 === 0) lp.set(260 + 1700 * Math.exp(-t * 28), 1.2);
    ph += f / SR; if (ph >= 1) ph -= 1;
    const v = (2 * ph - 1) + .6 * Math.sin(2 * Math.PI * ph);
    const env = Math.min(1, t / .003) * Math.exp(-t * 9) * Math.min(1, (len - k) / SR / .004);
    const i = i0 + k; if (i < N) BASS[i] += lp.p(v) * env * amp;
  }
}

// ---------------- scrittura degli eventi ----------------
const kicks = [];
const OST = [0, 2, 1, 3, 2, 1, 3, 2, 0, 2, 1, 3, 2, 3, 1, 2];
for (let s = 0; s * STEP < DUR; s++) {
  const t = s * STEP, st = s % 16, bar = Math.floor(s / 16), ch = chordAt(t);
  if (GROOVE(t)) {
    const half = HALF(t);
    if (half ? (st === 0 || st === 10) : st % 4 === 0) { kick(t, st === 0 ? 1 : .9); kicks.push(t); }
    if (half ? st === 8 : (st === 4 || st === 12)) { clap(t, 1); taiko(t, .3, 160); }
    if (half) { if (st % 2 === 0) hat(t, st % 4 === 2 ? .7 : .45); }
    else if (st % 4 === 2) hat(t, 1, true); else hat(t, st % 2 ? .3 : .5);
    if (half ? st % 2 === 0 && st % 8 !== 0 : st % 4 !== 0) bass16(t, ch.root - 12 + (st % 4 === 3 || st === 14 ? 12 : 0), st % 4 === 2 ? .5 : .38);
    const n = ch.os[OST[st]];
    if (!half || st % 2 === 0) spic(t, STEP * (half ? 1.4 : .7), n, st % 4 === 0 ? .2 : .13, st % 2 ? .3 : -.3, 3000);
    if (!half) spic(t, STEP * .6, n + 12, .09, st % 2 ? -.5 : .5, 4500);
    // stacco di taiko sull'ultimo quarto di ogni funzione (sei battute)
    if (st >= 12 && bar >= S.CARDS && bar < S.DEVICES && (bar - S.CARDS) % S.CARD_LEN === S.CARD_LEN - 1) taiko(t, .45 + (st - 12) * .12, 95 - (st - 12) * 9);
  } else if (has(t, B(2), B(5.75))) {
    // apertura: gli archi crescono, prima a ottavi e poi a sedicesimi
    const n = ch.os[OST[st]], p = (t - B(2)) / B(3.75);
    if (t >= B(4) || st % 2 === 0) spic(t, STEP * .7, n, .05 + .06 * p, st % 2 ? .3 : -.3, 1500 + 1500 * p);
  }
}
// battito del cuore: apertura e semaforo
for (let b = 0; b * BEAT * 2 < B(2); b++) { const t = b * BEAT * 2; kick(t, .45, true); kick(t + .19, .28, true); }
// sub lungo dove manca il basso a sedicesimi
for (let bar = 0; bar < S.END; bar++) {
  const t = B(bar);
  if (has(t, B(0), B(5.75)) || BREAK(t)) e808(t, BAR - .05, chordAt(t).root, .35, 0);
  if (has(t, B(S.OUTRO), B(S.END))) e808(t, bar === S.END - 1 ? BAR * .9 : BAR - .05, chordAt(t).root, .45 * Math.max(.3, 1 - (t - B(S.OUTRO)) / B(5)), 0);
}
// rullate e piatti
function roll(t0, t1, a0, a1) {
  for (let t = t0, step = BEAT / 2; t < t1 - .02; t += step, step = Math.max(BEAT / 8, step * .86)) {
    const p = (t - t0) / (t1 - t0); clap(t, (a0 + (a1 - a0) * p) * .7); taiko(t, a0 + (a1 - a0) * p, 70 + p * 40);
  }
}
roll(B(5), B(5.75), .3, 1);
roll(B(S.OUT - 1), B(S.OUT) - .05, .25, 1);
[S.DROP, S.CARDS, S.DEVICES, S.THEMES, S.WALL, S.OUT].forEach(b => crash(B(b), 1.1));
[S.DROP, S.DEVICES, S.WALL, S.OUT, S.OUT + 1, S.OUT + 2, S.OUT + 3].forEach((b, i) => braam(B(b), i > 3 ? .7 : 1.1));
// coro: un accordo per battuta, più alto nel drop finale
for (let bar = 0; bar < S.END - 1; bar++) {
  const t = B(bar);
  const lvl = t < B(S.DROP) ? .8 : t >= B(S.OUT) || BREAK(t) ? 1.3 : has(t, B(S.DEVICES), B(S.TRUST)) ? 1 : .8;
  choirChord(t, bar === S.END - 2 ? BAR * 2 : BAR, chordAt(t + .01).choir, .0105 * lvl);
}
// coda: pianoforte e campanelli, chiusura in re maggiore
for (let s = 0; B(S.OUTRO) + s * BEAT / 2 < DUR - 1.2; s++) { const t = B(S.OUTRO) + s * BEAT / 2, ch = chordAt(t); piano(t, ch.os[[0, 1, 2, 3, 2, 1, 3, 2][s % 8]] + 12, .2, s % 2 ? .3 : -.3); }
[[0, 74], [2, 77], [4, 81], [6, 79], [8, 77], [10, 76], [12, 74], [14, 78], [16, 81], [18, 86]].forEach(([o, m]) => bell(B(S.OUTRO) + o * BEAT, m, .09, 0, 2));
// risers
sweep(0, B(2), 150, 2500, .3, 'up', 1.2);
sweep(B(3.5), B(5.75), 300, 9000, .45, 'up', 1.4);
sweep(B(S.WALL - 2), B(S.WALL), 250, 7000, .3, 'up', 1.4);
sweep(B(S.OUT - 1.5), B(S.OUT) - .05, 200, 9000, .5, 'up', 1.5);

// ---------------- effetti delle scene ----------------
for (const { t, type } of SFX) {
  switch (type) {
    case 'swell': break; // è il riser iniziale
    case 'cut': taiko(t, t < B(2) ? .6 : t < B(3) ? .45 : .3, 70); click(t, 2400, .1, .01); break;
    case 'impact': impact(t, 1.15); break;
    case 'impactS': boom(t, .6, 1.8); taiko(t, .8, 55); break;
    case 'num': taiko(t, 1, 52); boom(t, .45, 1.4); break;
    case 'slam': taiko(t, .7, 64); click(t, 300, .22, .05); break;
    case 'hit': taiko(t, .8, 60); boom(t, .3, 1); break;
    case 'whoosh': sweep(t - .2, t + .3, 300, 6000, .3, 'arc', 1.1); break;
    case 'whip': sweep(t - .1, t + BEAT / 2, 500, 7000, .32, 'up', 1.2); break;
    case 'punch': boom(t, .3, .5); sweep(t - .05, t + .2, 1000, 5000, .18, 'arc', 1.2); break;
    case 'tick': click(t, 2600, .14, .012); blip(t, .06, 1800, 2600); break;
    case 'pop': blip(t, .14, 700, 2100); break;
    case 'switch': click(t, 2600, .12, .008); bell(t, 98, .05, .2, 8); taiko(t, .3, 120); break;
  }
}

// ---------------- mix ----------------
const duck = new Float32Array(N).fill(1); // sidechain: basso e coro si abbassano a ogni cassa
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.3 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .7 * Math.exp(-k / SR / .08));
}
{ const hp = biquad('hp', 35); for (let i = 0; i < N; i++) put(i, hp.p(BASS[i]) * duck[i] * .5); }
mixChoir(duck, 1, .6);
mixSub(.4);
pingPong(BEAT * .75, .3, .3);
finish('build/intro/music.wav', { revWet: .32, fadeOut: 2.4, drive: 2.2 });
