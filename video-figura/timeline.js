// Tempi del video, in secondi. Li usano sia la pagina (figura.js) sia l'audio (audio.js), così suoni e immagini
// restano allineati: se sposti un evento qui, si sposta in tutti e due.
(function (g) {
  const DURATION = 60, PH0 = 5, PHD = 7.5;                      // apertura di 5 s, poi sei fasi da 7,5 s e la chiusura
  const T = [0, 1, 2, 3, 4, 5, 6].map(k => PH0 + k * PHD);      // T[0..5] inizio delle fasi, T[6] chiusura
  const TL = {
    DURATION, PH0, PHD, T,
    CO_IN: 1.0,                                                 // la didascalia compare a questo punto di ogni fase
    WINDOWS: 2.7,                                               // si accendono le finestre
    deskIn: r => 1.9 + r * .28,                                 // le quattro file di banchi si disegnano una dopo l'altra
    // 1 · circolari
    shArr: k => T[0] + .9 + k * .26,                            // arrivo di ognuna delle cinque circolari
    shScan: k => T[0] + 2.55 + k * .22,                         // lettura dell'AI (dura mezzo secondo, poi esce la categoria)
    PULSE0: T[0] + 3.9, PULSE_V: 4.4,                           // onda delle notifiche: partenza e metri al secondo
    // 2 · assistant
    TYPE_Q: T[1] + .9, ASK: T[1] + 2.0, SRC: [T[1] + 3.0, T[1] + 3.4], ANSWER: T[1] + 4.2, TYPE_A: T[1] + 5.1,
    // 3 · calendario
    EVT: [1.4, 1.75, 2.1].map(x => T[2] + x), FLY: T[2] + 2.6, LAND: T[2] + 3.6,
    // 4 · interrogazioni
    pinT: (gi, j) => T[3] + 1.7 + gi * 1.05 + j * .16,
    // 5 · mappa posti
    TOKENS: T[4] + .75, MOVE: [T[4] + 2.7, T[4] + 4.75],
    // 6 · bacheca
    CARD: T[5] + 1.0, VOTE: [T[5] + 2.4, 3.0],                  // la proposta nuova, poi i voti che salgono [inizio, durata]
    // chiusura
    NET0: T[6] + 2.6, LOGO: T[6] + 5.3,
  };
  g.TL = TL;
  if (typeof module !== 'undefined') module.exports = TL;
})(typeof window !== 'undefined' ? window : globalThis);
