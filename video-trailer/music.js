// Colonna sonora del trailer, sintetizzata da zero (nessun campione esterno).
// 128 BPM, la minore: Am – F – C – G, una battuta per accordo. Due drop (logo e dispositivi), un muro
// di suono sulle 24 combinazioni, finale che si apre in La maggiore.
// Gli effetti sonori arrivano da sfx.json (node render.js sfx), già nei tempi del video.
//   node music.js  -> build/music.wav
const fs = require('fs');
const TL = require('./timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const DUR = TL.DURATION;
const SFX = JSON.parse(fs.readFileSync('sfx.json', 'utf8'));
const { SR, N, L, R, VL, VR, PL, PR, BS, mtof, has, biquad,
  kick, clap, hat, crash, pluck, bassNote, leadNote, bell, piano, sweep, boom, impact, click, blip, buzz, pingPong, finish } = require('./synth')(DUR);


// ---------------- armonia ----------------
const CHORDS = [ // [pad, fondamentale del basso, arpeggio]
  { pad: [57, 60, 64, 69, 71], bass: 45, arp: [69, 72, 76, 81] },  // Am(add9)
  { pad: [53, 57, 60, 64, 67], bass: 41, arp: [65, 69, 72, 77] },  // Fmaj9
  { pad: [55, 60, 64, 67, 74], bass: 48, arp: [67, 72, 76, 79] },  // C(add9)
  { pad: [55, 59, 62, 67, 69], bass: 43, arp: [67, 71, 74, 79] },  // G(add9)
];
const A_MAJ = { pad: [57, 61, 64, 69, 71, 76], bass: 45, arp: [69, 73, 76, 81] }; // l'accordo che chiude
function chordAt(t) {
  const bar = Math.floor(t / BAR + 1e-6);
  if (bar >= 65) return A_MAJ;          // ultima battuta: La maggiore
  if (bar === 63) return CHORDS[1];     // F
  if (bar === 64) return CHORDS[3];     // G
  return CHORDS[((bar % 4) + 4) % 4];
}

// ---------------- arrangiamento (in battute) ----------------
const GROOVE = t => has(t, B(8), B(36)) || has(t, B(38), B(56)) || has(t, B(59), B(63));
const HALF = t => has(t, B(56), B(58.5));                                     // fiducia: metà tempo
const STOP = t => has(t, B(5.75), B(6)) || has(t, B(7.75), B(8)) || has(t, B(37.75), B(38)); // silenzio prima dei drop
const kickOn = t => (GROOVE(t) || (HALF(t) && Math.round(t / BEAT) % 2 === 0)) && !STOP(t);
const pulseOn = t => has(t, B(2), B(5.75));                                  // battito sordo nel caos
const clapOn = t => GROOVE(t) && !STOP(t);
const hatOn = t => (GROOVE(t) || has(t, B(0), B(5.75)) || HALF(t)) && !STOP(t);
const arpOn = t => (has(t, B(8), B(36)) || has(t, B(38), B(58.5)) || has(t, B(59), B(64))) && !STOP(t);
const arpHi = t => has(t, B(28), B(36)) || has(t, B(46), B(56)) || has(t, B(59), B(63));
const leadOn = t => has(t, B(8), B(12)) || has(t, B(28), B(36)) || has(t, B(38), B(46)) || has(t, B(52), B(56)) || has(t, B(59), B(63));
function bassMode(t) {
  if (STOP(t)) return null;
  if (GROOVE(t)) return 'pump';
  if (has(t, B(6), B(7.75)) || has(t, B(36), B(37.75)) || HALF(t) || has(t, B(63), DUR - .5)) return 'long';
  return null;
}
function padCut(t) {
  if (t < B(6)) return 380 + 500 * t / B(6);
  if (t < B(8)) return 900 + 5000 * Math.pow((t - B(6)) / B(2), 2.2);
  if (has(t, B(36), B(38))) return 700 + 4500 * Math.pow((t - B(36)) / B(2), 2);
  if (has(t, B(56), B(59))) return 1200 + 4000 * Math.pow((t - B(56)) / B(3), 2);
  if (has(t, B(52), B(56))) return 4200;
  if (t >= B(63)) return 3200;
  return 3000;
}
function padLvl(t) {
  if (STOP(t)) return 0;
  if (t < B(6)) return .6;
  if (has(t, B(52), B(56))) return 1.05;
  return .85;
}


// ---------------- scrittura degli eventi ----------------
const kicks = [];
for (let b = 0; b * BEAT < DUR; b++) {
  const t = b * BEAT;
  if (kickOn(t)) { kick(t); kicks.push(t); }
  else if (pulseOn(t) && (b % 2 === 0 || t >= B(4))) kick(t, .25 + .5 * (t - B(2)) / B(3.75), true);
  if (clapOn(t) && b % 2 === 1) clap(t);
  if (HALF(t) && b % 4 === 2) clap(t, .9);
  if (hatOn(t)) {
    const intro = t < B(8);
    if (intro) { // ticchettio d'orologio, sempre più fitto
      hat(t, .3, false);
      if (t >= B(2)) hat(t + BEAT / 2, .35);
      if (t >= B(4)) { hat(t + BEAT / 4, .22); hat(t + 3 * BEAT / 4, .22); }
    } else {
      hat(t + BEAT / 2, 1, true);
      hat(t + BEAT / 4, .45); hat(t + 3 * BEAT / 4, .45);
    }
  }
}
// rullate: prima dei drop (sedicesimi che accelerano) e brevi stacchi a ogni cambio di funzione
function roll(t0, t1, a0, a1) {
  for (let t = t0, step = BEAT / 2; t < t1 - .02; t += step, step = Math.max(BEAT / 8, step * .88))
    clap(t, a0 + (a1 - a0) * (t - t0) / (t1 - t0));
}
roll(B(7), B(7.75), .25, .9);
roll(B(37), B(37.75), .25, .9);
roll(B(58.5), B(59) - .02, .4, 1);
for (let f = 1; f < 6; f++) { const tf = B(12 + f * 4); for (let k = 0; k < 4; k++) clap(tf - BEAT + k * BEAT / 4, .35 + k * .12); }
for (let k = 0; k < 4; k++) clap(B(46) - BEAT + k * BEAT / 4, .35 + k * .12);
for (let k = 0; k < 8; k++) clap(B(52) - BEAT * 2 + k * BEAT / 4, .3 + k * .08);
// piatti sugli attacchi delle sezioni
[B(8), B(38), B(46), B(52), B(59)].forEach(t => crash(t, 1.2));

// arpeggio a sedicesimi
const ARP_PATTERN = [0, 1, 2, 3, 2, 1, 3, 1];
for (let s = 0; s * BEAT / 4 < DUR; s++) {
  const t = s * BEAT / 4;
  if (!arpOn(t)) continue;
  const ch = chordAt(t), n = ch.arp[ARP_PATTERN[s % 8]];
  const outro = t >= B(63) ? Math.max(0, 1 - (t - B(63)) / B(1)) : 1;
  const half = HALF(t) ? .7 : 1;
  pluck(t, n, .18 * outro * half, s % 2 ? .35 : -.35);
  if (arpHi(t) && s % 2 === 0) pluck(t + BEAT / 8, n + 12, .05, s % 4 ? -.6 : .6);
}
// melodia: 8 ottavi per battuta, 0 = pausa, -1 = tiene la nota precedente. Frase A e frase B.
const MEL = [
  [76, -1, 76, -1, 74, 76, -1, 81], [79, -1, 77, -1, 76, -1, 72, -1], [76, -1, 76, -1, 74, 76, -1, 79], [74, -1, -1, 71, -1, -1, 74, 0],
  [81, -1, 79, -1, 76, -1, 74, 76], [72, -1, -1, -1, 69, -1, 72, -1], [76, -1, 79, -1, 81, -1, 79, 76], [74, -1, -1, -1, -1, -1, 0, 0],
];
for (let bar = 0; bar * BAR < DUR; bar++) {
  const t0 = B(bar);
  if (!leadOn(t0)) continue;
  const phr = MEL[(bar - 8 + 800) % 8];
  for (let e = 0; e < 8; e++) {
    const n = phr[e]; if (n <= 0) continue;
    let len = 1; while (e + len < 8 && phr[e + len] === -1) len++;
    const t = t0 + e * BEAT / 2;
    if (STOP(t)) continue;
    leadNote(t, len * BEAT / 2 - .03, n, has(t0, B(52), B(56)) ? .12 : .1);
  }
}
// basso
for (let e = 0; e * BEAT / 2 < DUR; e++) {
  const t = e * BEAT / 2, m = bassMode(t), root = chordAt(t).bass;
  if (m === 'pump') bassNote(t, BEAT / 2 - .04, root - 12 + (e % 2 ? 12 : 0), e % 2 ? .42 : .3);
  else if (m === 'long' && e % 8 === 0) bassNote(t, BAR - .1, root - 12, .3 * (t >= B(63) ? Math.max(.2, 1 - (t - B(63)) / B(3)) : 1));
}
// finale: pianoforte e campanelli sopra l'ultimo giro, chiusura in La maggiore
const UPDOWN = [0, 1, 2, 3, 2, 1, 3, 2];
for (let s = 0; B(63) + s * BEAT / 2 < DUR - 1.2; s++) {
  const t = B(63) + s * BEAT / 2, ch = chordAt(t);
  piano(t, ch.arp[UPDOWN[s % 8]] - 12, .24, s % 2 ? .3 : -.3);
}
piano(B(65), 57, .22, -.2); piano(B(65), 61, .2, 0); piano(B(65), 64, .2, .2); piano(B(65), 69, .18, 0);
[[0, 81], [1.5, 79], [3, 76], [4, 77], [5.5, 79], [7, 81], [7.5, 85]].forEach(([o, m]) => bell(B(63) + o * BEAT * 1, m, .11, 0, 2.2));
// intro: tre note di carillon sulla domanda, prima del drop
[[0, 76], [1, 72], [2, 69], [3, 71], [4, 72], [5, 74], [6, 76]].forEach(([o, m]) => bell(B(6) + o * BEAT / 2 * 1.6, m, .09, 0, 3));

// pad: supersaw (5 voci per nota), scritto grezzo e filtrato dopo con taglio variabile
for (let bar = 0; bar * BAR < DUR; bar++) {
  const t0 = B(bar), ch = chordAt(t0 + .01);
  const last = bar === S.END - 1;
  const i0 = Math.round(t0 * SR), len = Math.round((last ? BAR + 1 : BAR + 1.2) * SR);
  ch.pad.forEach((m, ni) => {
    [-14, -7, 0, 7, 14].forEach((c, vi) => {
      const f = mtof(m) * Math.pow(2, c / 1200); let ph = (ni * .37 + vi * .21) % 1;
      const pan = (vi - 2) / 2.5, dt = f / SR;
      for (let k = 0; k < len; k++) {
        const t = k / SR, i = i0 + k; if (i >= N) break;
        ph += dt; if (ph >= 1) ph -= 1;
        let v = 2 * ph - 1; // dente di sega polyBLEP
        if (ph < dt) { const x = ph / dt; v -= x + x - x * x - 1; } else if (ph > 1 - dt) { const x = (ph - 1) / dt; v -= x * x + x + x + 1; }
        const env = Math.min(1, t / .25) * (t < BAR ? 1 : Math.exp(-(t - BAR) * 4));
        const g = v * env * .027;
        PL[i] += g * (1 - pan) * .5; PR[i] += g * (1 + pan) * .5;
      }
    });
  });
}

// risers e effetti sincronizzati con le scene
sweep(B(6), B(7.75), 200, 9000, .55, 'up', 1.4);
sweep(B(36.5), B(37.75), 250, 8000, .5, 'up', 1.4);
sweep(B(50), B(52), 300, 7000, .3, 'up', 1.6);
for (const { t, type } of SFX) {
  switch (type) {
    case 'ping': bell(t, 88, .12, -.3, 6); bell(t + .07, 93, .09, -.3, 6); break;
    case 'ping2': bell(t, 81, .1, .3, 9); bell(t + .05, 76, .08, .3, 9); break;
    case 'buzz': buzz(t); break;
    case 'notify': bell(t, 84, .14, 0); bell(t + .11, 88, .14, 0); bell(t + .22, 91, .14, 0); break;
    case 'success': bell(t, 86, .12, 0); bell(t + .09, 93, .12, 0); break;
    case 'impact': impact(t, 1); break;
    case 'impactS': boom(t, .55, 1.8); break;
    case 'slam': kick(t, .7, true); boom(t, .3, .8); click(t, 300, .25, .05); break;
    case 'hit': kick(t, .6, true); boom(t, .35, 1.2); break;
    case 'suck': sweep(t - .45, t, 6000, 150, .5, 'up', 1.2); break;
    case 'riser': sweep(t - .05, t + BAR / 2 - .05, 400, 9000, .45, 'up', 1.3); break;
    case 'riserS': sweep(t - .05, t + BAR / 2 - .05, 400, 7000, .35, 'up', 1.3); break;
    case 'blip': blip(t); break;
    case 'pop': blip(t, .16, 700, 2100); break;
    case 'drop': blip(t, .2, 1800, 700); kick(t, .2, true); break;
    case 'key': click(t, 3400, .08, .007); break;
    case 'tick': click(t, 2000, .1, .01); break;
    case 'stamp': kick(t, .45, true); click(t, 900, .2, .03); break;
    case 'shuffle': for (let k = 0; k < 6; k++) click(t + k * .05, 1500 + k * 120, .08, .01); sweep(t - .1, t + .5, 500, 3500, .22, 'arc', 1.2); break;
    case 'send': sweep(t - .1, t + .3, 600, 4500, .22, 'arc', 1.2); break;
    case 'swoosh': sweep(t - .15, t + .4, 400, 3200, .26, 'arc', 1.2); break;
    case 'whoosh': sweep(t - .25, t + .45, 300, 6000, .36, 'arc', 1.1); break;
    case 'crash': crash(t, .8); break;
    case 'switch': click(t, 2600, .1, .008); bell(t, 98, .05, .2, 8); break;
    case 'switchBig': sweep(t - .12, t + .3, 800, 6000, .2, 'arc', 1.2); click(t, 2200, .12, .01); break;
  }
}

// ---------------- mix ----------------
// sidechain: pad e basso si abbassano a ogni cassa ("pompa")
const duck = new Float32Array(N).fill(1);
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.38 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .68 * Math.exp(-k / SR / .1));
}
{
  const fl = [biquad('lp', 800, .6), biquad('lp', 800, .6)], fr = [biquad('lp', 800, .6), biquad('lp', 800, .6)];
  for (let i = 0; i < N; i++) {
    const t = i / SR;
    if (i % 64 === 0) { const c = padCut(t); fl.forEach(f => f.set(c, .6)); fr.forEach(f => f.set(c, .6)); }
    const lv = padLvl(t) * duck[i];
    const l = fl[1].p(fl[0].p(PL[i])) * lv, r = fr[1].p(fr[0].p(PR[i])) * lv;
    L[i] += l; R[i] += r; VL[i] += l * .3; VR[i] += r * .3;
  }
  const hp = biquad('hp', 32);
  for (let i = 0; i < N; i++) { const v = hp.p(BS[i]) * duck[i] * .28 * (STOP(i / SR) ? 0 : 1); L[i] += v; R[i] += v; }
}
// delay ping-pong a 3/16 sul lead, poi riverbero e mastering
pingPong(BEAT * .75);
finish('build/music.wav');
