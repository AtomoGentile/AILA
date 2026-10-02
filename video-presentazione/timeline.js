// Tempi del video. Le scene in video.js sono scritte su un tempo "interno" (TB); il video vero dura
// di più e ogni scena viene distesa sulla sua durata finale (NB). Tutti i confini sono multipli di
// 2 s (una battuta a 120 BPM), così i cambi scena restano a tempo con la musica.
// Usato sia dal browser (video.js) sia da Node (music.js).
(function (g) {
  const TB = [0, 12, 16, 20, 38, 50, 58, 76, 96, 106, 113, 122];  // interni
  const NB = [0, 12, 16, 22, 44, 60, 70, 92, 116, 130, 138, 148]; // nel video
  const map = (x, from, to) => {
    let i = 0;
    while (i < from.length - 2 && x > from[i + 1]) i++;
    return to[i] + (x - from[i]) * (to[i + 1] - to[i]) / (from[i + 1] - from[i]);
  };
  const T = {
    DURATION: NB[NB.length - 1],
    warp: v => map(v, NB, TB),   // tempo del video -> tempo interno
    unwarp: t => map(t, TB, NB), // tempo interno -> tempo del video
  };
  g.TIMELINE = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
