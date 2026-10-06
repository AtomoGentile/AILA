// Colonna sonora dell'intro, sintetizzata da zero: atmosfera "aura" / phonk a 144 BPM in re minore.
// 808 lunghi con scivolata, cassa e rullante a metà tempo, campanaccio che fa la melodia, coro cupo,
// un "braam" su ogni funzione presentata, il semaforo e il drop finale. Effetti da sfx-intro.json.
//   node render.js sfx (con PAGE=intro) && node intro-music.js  -> build/intro/music.wav
const fs = require('fs');
const TL = require('./intro-timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const DUR = TL.DURATION;
const SFX = JSON.parse(fs.readFileSync('sfx-intro.json', 'utf8'));
const { SR, N, L, R, VL, VR, mtof, noise, has, put, biquad,
  kick, clap, hat, crash, bell, sweep, boom, impact, click, blip, pingPong, finish } = require('./synth')(DUR);
const STEP = BEAT / 4; // sedicesimo

// ---------------- armonia: Dm – B♭ – Gm – A ----------------
const CH = [
  { root: 38, choir: [50, 57, 62, 65, 69] }, // Dm
  { root: 34, choir: [46, 53, 58, 62, 65] }, // B♭
  { root: 31, choir: [43, 50, 55, 58, 62] }, // Gm
  { root: 33, choir: [45, 52, 57, 61, 64] }, // A
];
const D_MAJ = { root: 38, choir: [50, 57, 62, 66, 69, 74] }; // chiusura in re maggiore
const chordAt = t => { const bar = Math.floor(t / BAR + 1e-6); return bar >= 58 ? D_MAJ : CH[((bar % 4) + 4) % 4]; };

// ---------------- arrangiamento (in battute) ----------------
const CARD_END = [...Array(6)].map((_, i) => B(14 + i * 4)); // fine di ogni scheda: mezzo quarto di silenzio
const STOP = t => has(t, B(7.75), B(8)) || CARD_END.some(e => has(t, e - BEAT / 2, e)) || has(t, B(43.5), B(44));
const GROOVE = t => (has(t, B(8), B(48)) || has(t, B(50), B(54))) && !STOP(t);
const COWBELL = t => (has(t, B(10), B(34)) || has(t, B(40), B(48)) || has(t, B(50), B(54))) && !STOP(t);
const COW_HI = t => has(t, B(40), B(48)) || has(t, B(50), B(54));
const KICK_STEPS = [0, 6, 10];
const BELL_MEL = [ // 16 sedicesimi per battuta, 0 = pausa
  [74, 0, 0, 77, 0, 0, 74, 0, 81, 0, 0, 79, 0, 77, 0, 0],
  [74, 0, 0, 77, 0, 0, 74, 0, 82, 0, 0, 81, 0, 77, 0, 0],
  [74, 0, 0, 79, 0, 0, 74, 0, 82, 0, 0, 79, 0, 77, 0, 0],
  [73, 0, 0, 76, 0, 0, 73, 0, 81, 0, 0, 79, 0, 76, 0, 0],
];

// ---------------- strumenti propri dell'intro ----------------
const SUB = new Float32Array(N);   // 808 (non passa dal riverbero)
const CHL = new Float32Array(N), CHR = new Float32Array(N); // coro grezzo, filtrato dopo
// 808: sinusoide con scivolata iniziale, lunga coda, saturata
function e808(t0, dur, midi, amp, glideFrom = 7) {
  const i0 = Math.round(t0 * SR), len = Math.round((dur + .05) * SR), f = mtof(midi);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const fr = f * Math.pow(2, glideFrom * Math.exp(-t * 28) / 12);
    ph += 2 * Math.PI * fr / SR;
    const env = Math.min(1, t / .004) * Math.exp(-t * .9) * (t < dur ? 1 : Math.exp(-(t - dur) * 60));
    const i = i0 + k; if (i < N) SUB[i] += Math.tanh(Math.sin(ph) * 2.2) * env * amp;
  }
}
// campanaccio intonato (due onde quadre in rapporto 1:1,48 come quello di una drum machine)
function cowbell(t0, midi, amp, pan = 0) {
  const i0 = Math.round(t0 * SR), len = Math.round(.32 * SR), f = mtof(midi), bp = biquad('bp', f * 1.25, 2.2);
  let p1 = 0, p2 = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    p1 += f / SR; p2 += f * 1.48 / SR; p1 %= 1; p2 %= 1;
    const v = (p1 < .5 ? 1 : -1) + (p2 < .5 ? 1 : -1);
    const env = Math.min(1, t / .002) * (Math.exp(-t * 26) * .7 + Math.exp(-t * 7) * .3);
    put(i0 + k, bp.p(v) * env * amp, pan, .3, .25);
  }
}
// coro: tre seghe scordate per nota, inviluppo lento (le vocali "ah" le fanno i filtri nel mix)
function choirChord(t0, dur, notes, amp) {
  const i0 = Math.round(t0 * SR), len = Math.round((dur + .9) * SR);
  notes.forEach((m, ni) => [-11, 0, 11].forEach((c, vi) => {
    const f = mtof(m) * Math.pow(2, (c + 4 * Math.sin(ni * 1.7 + vi)) / 1200), pan = (vi - 1) * .6;
    let ph = (ni * .31 + vi * .17) % 1;
    for (let k = 0; k < len; k++) {
      const t = k / SR, i = i0 + k; if (i >= N) break;
      ph += f * (1 + .003 * Math.sin(2 * Math.PI * 5.2 * t + ni)) / SR; if (ph >= 1) ph -= 1;
      const env = Math.min(1, t / .45) * (t < dur ? 1 : Math.exp(-(t - dur) * 3.5));
      const v = (2 * ph - 1) * env * amp;
      CHL[i] += v * (1 - pan) * .5; CHR[i] += v * (1 + pan) * .5;
    }
  }));
}
// braam: ottoni bassi con filtro che si apre e si richiude
function braam(t0, amp = 1) {
  const len = Math.round(2.2 * SR), i0 = Math.round(t0 * SR), lp = biquad('lp', 200, 1.4);
  const fs_ = [mtof(26), mtof(38), mtof(45), mtof(50)];
  const ph = fs_.map(() => 0);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    if (k % 32 === 0) lp.set(180 + 2200 * Math.exp(-Math.pow((t - .18) / .35, 2)) + 300 * Math.exp(-t * 2), 1.4);
    let v = 0; fs_.forEach((f, j) => { ph[j] += f * (1 + j * .0015) / SR; if (ph[j] >= 1) ph[j] -= 1; v += (2 * ph[j] - 1) * (j ? .6 : 1); });
    const env = Math.min(1, t / .03) * Math.exp(-t * 1.3);
    put(i0 + k, Math.tanh(lp.p(v) * 1.5) * env * .55 * amp, 0, .35);
  }
}
function glitch(t0) { // balbettio digitale a trentaduesimi
  for (let k = 0; k < 6; k++) {
    const ts = t0 + k * BEAT / 8, i0 = Math.round(ts * SR), n = Math.round(BEAT / 10 * SR), f = 1800 - k * 220;
    for (let j = 0; j < n; j++) { const t = j / SR; const v = (Math.sign(Math.sin(2 * Math.PI * f * t)) * .5 + noise() * .5) * Math.exp(-t * 40); put(i0 + j, v * .14, (k % 2 ? .5 : -.5), .2); }
  }
  sweep(t0, t0 + BEAT / 2, 4000, 300, .25, 'up', 1.5);
}
function lightTone(t0, k) { // una luce del semaforo: colpo sordo e nota che sale
  kick(t0, .55, true);
  const m = [62, 64, 65, 67, 69, 74][k], f = mtof(m), i0 = Math.round(t0 * SR), n = Math.round(.42 * SR);
  for (let j = 0; j < n; j++) { const t = j / SR; const v = (Math.sin(2 * Math.PI * f * t) + .3 * Math.sin(6 * Math.PI * f * t)) * Math.exp(-t * 7) * Math.min(1, t / .003); put(i0 + j, v * .2, 0, .5); }
}

// ---------------- scrittura degli eventi ----------------
const kicks = [];
for (let s = 0; s * STEP < DUR; s++) {
  const t = s * STEP, st = s % 16, ch = chordAt(t);
  if (GROOVE(t)) {
    if (KICK_STEPS.includes(st)) { kick(t, .9); kicks.push(t); const nxt = KICK_STEPS.find(x => x > st) ?? 16; e808(t, (nxt - st) * STEP - .02, ch.root, .62); }
    if (st === 8) clap(t, 1);
    if (st % 2 === 0) hat(t, st % 4 === 0 ? 1.2 : .8);
    // rullate di charleston a terzine sull'ultimo quarto di ogni due battute
    if (st === 12 && Math.floor(s / 16) % 2 === 1) for (let k = 1; k < 6; k++) hat(t + k * BEAT / 6, .45 + k * .08);
  } else if (has(t, B(4), B(7.75)) && st % 2 === 0) hat(t, .3);
  if (COWBELL(t)) {
    const n = BELL_MEL[Math.floor(t / BAR + 1e-6) % 4][st];
    if (n) { cowbell(t, n, .46, st % 2 ? .25 : -.25); if (COW_HI(t)) cowbell(t, n + 12, .18, st % 2 ? -.4 : .4); }
  }
}
// battito del cuore nell'apertura e prima del semaforo
for (let b = 0; B(2) + b * BEAT * 2 < B(6); b++) { const t = B(2) + b * BEAT * 2; kick(t, .5, true); kick(t + .2, .3, true); }
for (let b = 0; b < 4; b++) { const t = B(49.5) + b * BEAT / 2; kick(t, .35 + b * .1, true); }
// 808 lunghi nei momenti senza batteria
for (let bar = 0; bar < S.END; bar++) {
  const t = B(bar);
  if (has(t, B(0), B(7.75)) || has(t, B(54), B(59))) e808(t, BAR - .05, chordAt(t).root, has(t, B(54), B(60)) ? .5 * Math.max(.3, 1 - (t - B(54)) / B(6)) : .4, 2);
}
e808(B(59), BAR * .9, 38, .35, 0);
// rullate prima dei drop
function roll(t0, t1, a0, a1) { for (let t = t0, step = BEAT / 2; t < t1 - .02; t += step, step = Math.max(BEAT / 8, step * .86)) clap(t, a0 + (a1 - a0) * (t - t0) / (t1 - t0)); }
roll(B(7), B(7.75), .2, .9); roll(B(47), B(48) - .05, .25, 1);
[B(8), B(10), B(34), B(40), B(44), B(50)].forEach(t => crash(t, 1.1));
// coro: un accordo per battuta per tutto il pezzo, tranne il semaforo
for (let bar = 0; bar < S.END; bar++) {
  const t = B(bar);
  if (has(t, B(48), B(50))) continue;
  const lvl = t < B(2) ? 1.3 : t < B(8) ? 1 : has(t, B(44), B(48)) || t >= B(50) ? 1.1 : .7;
  choirChord(t, bar === S.END - 2 ? BAR * 2 : BAR, chordAt(t + .01).choir, .011 * lvl);
  if (bar === S.END - 2) break;
}
// campanelli lenti nella coda
[[0, 74], [2, 77], [4, 81], [6, 79], [8, 77], [10, 76], [12, 74], [16, 78], [18, 81], [20, 86]].forEach(([o, m]) => bell(B(54) + o * BEAT, m, .1, 0, 2));
// risers
sweep(0, B(2), 150, 3000, .35, 'up', 1.2);
sweep(B(6), B(7.75), 200, 9000, .5, 'up', 1.4);
sweep(B(46), B(48), 250, 8000, .45, 'up', 1.4);
sweep(B(48), B(50) - .05, 60, 900, .35, 'up', 3);

for (const { t, type } of SFX) {
  if (type.startsWith('light')) { lightTone(t, +type.slice(5)); continue; }
  switch (type) {
    case 'swell': break; // è il riser iniziale
    case 'hit': boom(t, .6, 1.4); sweep(t - .3, t + .1, 400, 5000, .25, 'up', 1.2); kick(t, .6, true); break;
    case 'slam': kick(t, .75, true); boom(t, .35, .9); click(t, 300, .25, .05); break;
    case 'suck': sweep(t - .4, t, 6000, 150, .45, 'up', 1.2); break;
    case 'impact': impact(t, 1.1); break;
    case 'impactS': boom(t, .6, 1.8); break;
    case 'braam': braam(t); break;
    case 'whoosh': sweep(t - .25, t + .45, 300, 6000, .34, 'arc', 1.1); break;
    case 'glitch': glitch(t); break;
    case 'tick': click(t, 2400, .12, .012); blip(t, .05, 1800, 2400); break;
    case 'pop': blip(t, .14, 700, 2100); break;
    case 'switch': click(t, 2600, .12, .008); bell(t, 98, .05, .2, 8); boom(t, .15, .4); break;
  }
}

// ---------------- mix ----------------
const duck = new Float32Array(N).fill(1);
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.3 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .5 * Math.exp(-k / SR / .09));
}
{ // coro: due formanti di "ah" più un passa-basso, con un filo di riverbero in più
  const fl = [biquad('bp', 720, 3.5), biquad('bp', 1180, 4.5), biquad('lp', 2600, .7)], fr = [biquad('bp', 720, 3.5), biquad('bp', 1180, 4.5), biquad('lp', 2600, .7)];
  const body = [biquad('lp', 500, .7), biquad('lp', 500, .7)];
  for (let i = 0; i < N; i++) {
    const l = (fl[0].p(CHL[i]) * 1.6 + fl[1].p(CHL[i]) + body[0].p(CHL[i]) * .5), r = (fr[0].p(CHR[i]) * 1.6 + fr[1].p(CHR[i]) + body[1].p(CHR[i]) * .5);
    const vl = fl[2].p(l) * duck[i], vr = fr[2].p(r) * duck[i];
    L[i] += vl; R[i] += vr; VL[i] += vl * .6; VR[i] += vr * .6;
  }
  const hp = biquad('hp', 28);
  for (let i = 0; i < N; i++) { const v = hp.p(SUB[i]) * .42; L[i] += v; R[i] += v; }
}
pingPong(BEAT * .75, .35, .35);
finish('build/intro/music.wav', { revWet: .36, fadeOut: 2.4 });
