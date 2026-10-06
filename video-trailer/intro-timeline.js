// Tempi dell'intro "presentazione della squadra": 144 BPM, una battuta = 1,667 s.
// Usato sia dal browser (intro.js) sia da Node (intro-music.js).
(function (g) {
  const BPM = 144, BEAT = 60 / BPM, BAR = BEAT * 4;
  const S = { // inizio di ogni sezione, in battute
    OPEN: 0, MACRO: 2, TEASE: 6, DROP: 8, CARDS: 10, DEVICES: 34, LIVERY: 40, GRID: 44, LIGHTS: 48, OUT: 50, OUTRO: 54, END: 60,
  };
  const T = { BPM, BEAT, BAR, S, bar: n => n * BAR, DURATION: S.END * BAR };
  g.INTRO_TL = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
