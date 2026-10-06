// Tempi dell'intro "presentazione della squadra": 144 BPM, una battuta = 1,667 s, tagli su ogni quarto.
// Usato sia dal browser (intro.js) sia da Node (intro-music.js).
(function (g) {
  const BPM = 144, BEAT = 60 / BPM, BAR = BEAT * 4;
  const S = { // inizio di ogni sezione, in battute
    OPEN: 0, DROP: 4, CARDS: 6, CARD_LEN: 3, DEVICES: 24, LIVERY: 27, GRID: 30, LIGHTS: 33, OUT: 35, OUTRO: 39, END: 44,
  };
  const T = { BPM, BEAT, BAR, S, bar: n => n * BAR, DURATION: S.END * BAR };
  g.INTRO_TL = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
