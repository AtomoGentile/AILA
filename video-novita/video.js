'use strict';
/*
 * AILA — "Novità": la versione web (PWA) e la sezione Gita, raccontate come un filmato di presentazione di prodotto:
 * fondo nero o chiarissimo, titoli grandi, una cosa per volta, movimenti lenti.
 * Le parti comuni (logo, temi dell'app, dispositivi, aiuti per i tempi) arrivano da ../video-trailer/common.js.
 * I dati della gita sono inventati, gli stessi degli altri video (Praga, circolare n. 112): si cambiano in TRIP.
 */
const { BAR, BEAT } = TIMELINE;
const DURATION = TIMELINE.DURATION;
const T = Object.fromEntries(Object.entries(TIMELINE.S).map(([k, v]) => [k, v * BAR])); // inizio delle scene, in secondi
const URLTXT = 'aila-scuola.pages.dev';
const TRIP = {
  city: 'Praga',
  circ: { n: 112, t: "Viaggio d'istruzione a Praga", d: '19 ott' },
  docs: [
    { k: 'd1', t: 'Programma di viaggio', c: 'Programma', d: '19 ott', v: 1, tint: 'b' },
    { k: 'd2', t: "Preventivo dell'agenzia", c: 'Preventivo', d: '21 ott', v: 2, tint: 'g' },
    { k: 'd3', t: 'Regolamento e autorizzazione', c: 'Regolamento', d: '20 ott', v: 1, tint: 'y' },
    { k: 'd4', t: 'Elenco delle stanze', c: 'Altro', d: '22 ott', v: 1, tint: 's' }, // caricato durante il video
  ],
  links: [{ k: 'l1', t: "Sito dell'agenzia", d: '19 ott' }, { k: 'l2', t: 'Hotel a Praga', d: '20 ott' }],
  query: 'prev', match: ['d2'],
  report: 'Manca la tassa di soggiorno.',
  q1: "Quanto costa e quando si paga l'acconto?",
  a1: "380 € in tutto. L'acconto di 150 € va versato entro venerdì 23 ottobre.",
  q2: "C'è il wifi in hotel?",
  a2: 'Non lo trovo nel materiale disponibile. Prova a chiedere ai Rappresentanti.',
};
const TINT = { b: ['rgba(59,130,246,.15)', '#2563EB'], g: ['rgba(16,185,129,.17)', '#047857'], y: ['rgba(245,158,11,.2)', '#B45309'],
  s: ['rgba(100,116,139,.16)', '#475569'], v: ['rgba(139,92,246,.16)', '#6D28D9'], c: ['rgba(14,165,233,.16)', '#0369A1'] };
const ROW_H = 58;
const longDay = d => d.replace(' ott', ' ottobre');

// ---------- aiuti ----------
/* una riga di testo: sale e si mette a fuoco, poi esce verso l'alto */
const line = (el, t, tin, tout, o = {}) => io(el, t, tin, tout, { d: .95, dy: 44, b: 16, e: E.out5, od: .42, ody: -22, ...o });
/* un gruppo di testi: gli elementi .r entrano uno dopo l'altro */
function group(root, t, tin, tout, step = .15, o) {
  if (!root._r) root._r = [...root.querySelectorAll('.r')];
  root._r.forEach((el, i) => line(el, t, tin + i * step, tout == null ? null : tout + i * .03, o));
}
/* centro di un elemento dentro lo schermo del telefono (le trasformazioni non contano) */
function centerIn(el, root) {
  let x = el.offsetWidth / 2, y = el.offsetHeight / 2;
  while (el && el !== root) { x += el.offsetLeft; y += el.offsetTop; el = el.offsetParent; }
  return [x, y];
}
/* tocchi sullo schermo: list = [{t, el}] oppure [{t, x, y}] */
function taps(tapEl, root, list, t) {
  const ev = list.find(e => t >= e.t && t < e.t + .5);
  if (!ev) return pose(tapEl, { o: 0 });
  const [x, y] = ev.el ? centerIn(ev.el, root) : [ev.x, ev.y];
  const p = (t - ev.t) / .5;
  tapEl.style.left = x + 'px'; tapEl.style.top = y + 'px';
  pose(tapEl, { o: (p < .2 ? p / .2 : 1 - (p - .2) / .8) * .95, s: lerp(.4, 1.3, E.out(p)) });
}
/* una riga che si apre o si chiude: l'altezza segue v, il contenuto compare solo nell'ultima parte */
const rowV = (el, v, h = ROW_H) => { el.style.height = (h * v).toFixed(1) + 'px'; el.style.opacity = clamp((v - .45) / .55).toFixed(3); };
const drift = (sc, t, k = .028) => { sc.cam.style.transform = `scale(${(1 + k * clamp((t - sc.start) / (sc.end - sc.start))).toFixed(5)})`; };
function scene(start, end, html, light) {
  const sc = addScene(start, end, `<div class="cam">${html}</div>`);
  sc.cam = sc.el.firstChild;
  if (light) { sc.el.classList.add('lt'); sc.el.style.setProperty('--g', 'var(--gg)'); }
  return sc;
}

// ---------- pezzi dell'app ----------
const ailaIcon = (size, r, id) => `<div style="width:${size}px;height:${size}px;border-radius:${r}px;background:linear-gradient(45deg,#1E3C93,#0A1330);display:grid;place-items:center;overflow:hidden;box-shadow:inset 0 0 0 1px rgba(255,255,255,.1)"><div style="width:${size * .62}px;height:${size * .62}px">${logoSVG(id)}</div></div>`;
const assistantMark = (size, id) => `<svg viewBox="0 0 512 512" width="${size}" height="${size}"><defs><linearGradient id="am${id}" x1="0" y1="0" x2="512" y2="512" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#8B5CF6"/><stop offset=".33" stop-color="#3B82F6"/><stop offset=".67" stop-color="#3B82F6"/><stop offset="1" stop-color="#14B8A6"/></linearGradient></defs><g fill="url(#am${id})"><rect x="31" y="128" width="76.8" height="256" rx="38.4"/><rect x="154.8" y="20.5" width="76.8" height="471" rx="38.4"/><rect x="278.6" y="169" width="76.8" height="174.1" rx="38.4"/><rect x="402.4" y="92.2" width="76.8" height="327.7" rx="38.4"/></g></svg>`;
/* Home di oggi: in alto la lente, il pulmino della Gita e il profilo */
const heroNow = (name, big) => `<div class="hi"><div class="nm"${big ? ' style="font-size:34px"' : ''}>Ciao, ${name}</div><div class="hb">${I('search')}</div><div class="hb busb">${I('bus')}</div><div class="hb av">${name[0]}</div></div>`;
const homeNow = name => `<div class="hero">${SB}${heroNow(name)}
  <div class="qrow">${QUICK.map(([ic, l]) => `<div class="qi"><div class="t">${I(ic)}</div>${l}</div>`).join('')}</div></div>
  <div class="body" style="top:256px"><div><div class="sect">In evidenza</div>${featured}</div>
  <div><div class="sect">Prossimi eventi</div><div class="card">${EVENTS.map(erow).join('')}</div></div></div>${navHTML(2)}`;
/* Home larga, nel browser del computer (senza barra di stato) */
const homeWideNow = name => `<div class="hero" style="padding:30px 36px 28px">${heroNow(name, true)}
  <div class="qrow" style="width:640px">${QUICK.map(([ic, l]) => `<div class="qi" style="font-size:14px"><div class="t" style="height:64px">${I(ic)}</div>${l}</div>`).join('')}</div></div>
  <div style="position:absolute;left:30px;right:30px;top:244px;display:flex;gap:20px">
    <div style="flex:1;display:flex;flex-direction:column;gap:14px"><div><div class="sect">In evidenza</div>${featured}</div>
      <div><div class="sect">Ultime circolari</div><div class="card">
        <div class="erow"><div class="dt">${I('file-text', 'style="width:22px;height:22px"')}</div><div style="flex:1"><div class="et">Assemblea d'istituto di ottobre</div><div class="es">n. 111 · ieri</div></div><span class="bdg g"><i></i>Ti riguarda</span></div>
        <div class="erow"><div class="dt">${I('file-text', 'style="width:22px;height:22px"')}</div><div style="flex:1"><div class="et">Corso di preparazione ai test</div><div class="es">n. 110 · lun</div></div><span class="bdg y"><i></i>Potenziale</span></div>
      </div></div></div>
    <div style="flex:1"><div class="sect">Prossimi eventi</div><div class="card">${EVENTS.map(erow).join('')}${erow(['29', 'OTT', "Assemblea d'istituto", '4ª–6ª ora'])}</div></div>
  </div>${navHTML(2, 'height:76px;padding-bottom:4px')}`;

// =====================================================================================
// 1 · APERTURA
// =====================================================================================
{
  const t0 = T.OPEN;
  const sc = scene(t0, T.WEB, `
    <div class="glow a" id="g" style="left:360px;top:-60px;width:1200px;height:1200px;background:radial-gradient(closest-side,rgba(96,120,255,.30),rgba(150,100,250,.10) 55%,transparent)"></div>
    <div class="a" id="lg" style="left:860px;top:236px;width:200px;height:200px">${logoSVG('o')}</div>
    <div class="a hero-t" id="h" style="left:0;width:1920px;top:492px;text-align:center;font-size:136px">Due novità per <span class="gt">AILA</span>.</div>
    <div class="a sub" id="s" style="left:0;width:1920px;top:676px;text-align:center;font-size:37px">Una arriva nel browser. L'altra ti accompagna in gita.</div>`);
  const [g, lg, h, s] = ['#g', '#lg', '#h', '#s'].map(sc.q);
  sfx(t0 + .75, 'logo'); sfx(t0 + 2.6, 'whoosh');
  sc.render = t => {
    drift(sc, t);
    pose(g, { o: P(t, t0 + .2, 2.6, E.inOut) * (1 - P(t, T.WEB - .7, .6, E.in)), s: lerp(.8, 1.05, P(t, t0, 7, E.lin)) });
    renderLogo(lg, t, t0 + .7, 1.5);
    const m = P(t, t0 + 2.35, 1.3, E.inOut), out = P(t, T.WEB - .75, .5, E.in);
    pose(lg, { o: 1 - out, y: lerp(120, 0, m) - 30 * out, s: lerp(1.38, 1, m), b: 12 * out });
    line(h, t, t0 + 2.75, T.WEB - .7);
    line(s, t, t0 + 3.55, T.WEB - .66);
  };
}

// =====================================================================================
// 2 · AILA SUL WEB: titolo
// =====================================================================================
{
  const t0 = T.WEB;
  const sc = scene(t0, T.BROWSER + .5, `
    <div class="glow a" id="g" style="left:210px;top:-200px;width:1500px;height:1500px;background:radial-gradient(closest-side,rgba(90,120,255,.20),rgba(94,234,212,.05) 60%,transparent)"></div>
    <div class="a" style="left:0;width:1920px;top:318px;text-align:center">
      <div class="eye r" style="color:#8FB2FF">${I('globe')}<span class="gt">AILA sul web</span></div>
      <div class="hero-t" style="margin-top:22px"><span class="ln r">La stessa app.</span><span class="ln r">Ora <span class="gt">nel browser</span>.</span></div>
    </div>`);
  const g = sc.q('#g'), box = sc.q('.cam > div:nth-child(2)');
  sfx(t0 + .25, 'whoosh');
  sc.render = t => {
    drift(sc, t, .035);
    pose(g, { o: P(t, t0, 1.2, E.out) * (1 - P(t, T.BROWSER - .2, .6, E.in)) });
    group(box, t, t0 + .3, T.BROWSER - .35, .3, { od: .5, ody: 0, os: 1.1 });
  };
}

// =====================================================================================
// 3 · IL BROWSER: un indirizzo, poi le stesse schermate su tre dispositivi
// =====================================================================================
{
  const t0 = T.BROWSER, t2 = t0 + 6.0, tOut = T.INSTALL - .55;
  const sW = st('mat', false, 'blue'), sI = st('glass', false, 'blue'), sA = st('mat', true, 'violet');
  const sc = scene(t0, T.INSTALL, `
    <div class="glow a" id="g" style="left:160px;top:60px;width:1600px;height:1200px;background:radial-gradient(closest-side,rgba(80,110,255,.26),rgba(150,100,250,.08) 60%,transparent)"></div>
    <div class="a h c" id="h1" style="left:0;width:1920px;top:98px">Scrivi un indirizzo. <span class="gt">Si apre AILA.</span></div>
    <div class="a h c" id="h2" style="left:0;width:1920px;top:58px">Stesse schermate. <span class="gt">Stesso codice.</span></div>
    <div class="a sub" id="s2" style="left:0;width:1920px;top:168px;text-align:center">Non è un sito che le somiglia: è l'app vera, compilata per il browser.</div>
    <div class="a" id="bw" style="left:340px;top:234px"><div class="bw">
      <div class="tb"><i></i><i></i><i></i></div>
      <div class="url">${I('lock')}<span id="u"></span><span id="uc" style="width:2px;height:20px;background:#6FA8FF;margin-left:-6px"></span></div>
      <div class="prog" id="pg"></div>
      <div class="view"><div id="vw" class="${appCls(sW, 'wide')}" style="${appVars(sW)}">${homeWideNow('Chiara')}</div></div>
    </div></div>
    <div class="a" id="ip" style="left:745px;top:182px">${devHTML('iphone', sI, homeNow('Chiara'))}</div>
    <div class="a" id="dr" style="left:750px;top:182px">${devHTML('droid', sA, homeNow('Marco'))}</div>
    <div class="a cap" id="c1" style="left:76px;width:500px;top:974px"><b>iPhone</b> · Liquid Glass</div>
    <div class="a cap" id="c2" style="left:710px;width:500px;top:974px"><b>Computer</b> · Material</div>
    <div class="a cap" id="c3" style="left:1341px;width:500px;top:974px"><b>Android</b> · Material</div>`);
  const [g, h1, h2, s2, bw, ip, dr, c1, c2, c3, u, uc, pg, vw] = ['#g', '#h1', '#h2', '#s2', '#bw', '#ip', '#dr', '#c1', '#c2', '#c3', '#u', '#uc', '#pg', '#vw'].map(sc.q);
  const tType = t0 + 1.0, CPS = 18, tLoad = tType + URLTXT.length / CPS + .15;
  sfx(t0 + .1, 'whoosh');
  for (let i = 0; i < URLTXT.length; i += 2) sfx(tType + i / CPS, 'key');
  sfx(tLoad + .55, 'open'); sfx(t2 - .1, 'whoosh'); sfx(t2 + .55, 'pop'); sfx(t2 + .75, 'pop');
  sc.render = t => {
    drift(sc, t, .02);
    pose(g, { o: P(t, t0, 1.2, E.out) * (1 - P(t, tOut, .5, E.in)) });
    line(h1, t, t0 + .55, t2 - .45);
    line(h2, t, t2 + .15, tOut); line(s2, t, t2 + .4, tOut + .03);
    // finestra del browser
    const a = P(t, t0 + .05, 1.4, E.out5), m = P(t, t2 - .1, 1.2, E.inOut), out = P(t, tOut, .5, E.in);
    pose(bw, { o: clamp(a * 1.6) * (1 - out), y: lerp(460, 0, a) + lerp(6, 0, m) - 40 * out, s: lerp(.74, .88, a) * lerp(1, .7 / .88, m), rx: lerp(24, 0, a), b: 10 * out });
    typeText(u, t, tType, CPS, URLTXT, null);
    uc.style.opacity = (t > t0 + .9 && t < tLoad && Math.floor(t * 2.4) % 2 === 0) || (t >= tType && t < tLoad - .15) ? 1 : 0;
    const l = P(t, tLoad, .6, E.inOut);
    pg.style.width = (l * 1240) + 'px'; pg.style.opacity = 1 - P(t, tLoad + .6, .25, E.lin);
    const v = P(t, tLoad + .5, .55, E.out);
    vw.style.opacity = v; vw.style.transform = `scale(${lerp(.985, 1, v)})`;
    // i due telefoni
    const pi = P(t, t2 + .2, 1.2, E.out5), pd = P(t, t2 + .4, 1.2, E.out5);
    pose(ip, { o: clamp(pi * 1.5) * (1 - out), x: lerp(-1180, -634, pi), y: 8 - 40 * out, s: .7, ry: lerp(34, 0, pi), b: 10 * out });
    pose(dr, { o: clamp(pd * 1.5) * (1 - out), x: lerp(1180, 631, pd), y: 8 - 40 * out, s: .7, ry: lerp(-34, 0, pd), b: 10 * out });
    [c1, c2, c3].forEach((c, i) => line(c, t, t2 + 1.2 + i * .12, tOut + .02, { dy: 18, b: 6, d: .6 }));
  };
}

// =====================================================================================
// 4 · INSTALLAZIONE: Condividi → Aggiungi alla schermata Home → si apre come un'app
// =====================================================================================
{
  const t0 = T.INSTALL, tOut = T.BENTO - .55;
  const tShare = t0 + 2.0, tAdd = t0 + 3.9, tHome = t0 + 4.5, tIcon = t0 + 5.55, tOpen = t0 + 7.0;
  const s = st('glass', false, 'blue');
  const HS = [['calendar', '#FF6B5E,#FF3B30', 'Calendario'], ['message-circle', '#5BE584,#20B455', 'Messaggi'], ['map', '#63D2C6,#1F9E96', 'Mappe'], ['image', '#FFC65C,#FF7AB6', 'Foto'],
    ['music', '#FF7A9A,#F0325A', 'Musica'], ['clock', '#3A3F4D,#14171F', 'Orologio'], ['mail', '#6BB6FF,#1F7BFF', 'Mail'], ['cloud-sun', '#7CC3FF,#3A8DEB', 'Meteo'],
    ['notebook-pen', '#FFD95C,#F5A623', 'Note'], ['calculator', '#8D93A3,#565B6B', 'Calcolatrice']];
  const DOCK = [['phone', '#5BE584,#20B455'], ['compass', '#6BB6FF,#1F7BFF'], ['camera', '#8D93A3,#3A3F4D'], ['settings', '#A5ABBA,#6A7080']];
  const slot = i => [31 + (i % 4) * 93.3, 100 + Math.floor(i / 4) * 106];
  const [ax, ay] = slot(10);
  const inner = `
    <div class="layer" id="saf">
      <div class="saf-page"><div class="${appCls(s)}" style="${appVars(s)}">${homeNow('Chiara')}</div></div>
      <div class="saf-bar"><div class="rb">${I('chevron-left')}</div><div class="pill">${I('lock')}${URLTXT}</div><div class="rb" id="shb">${I('share')}</div></div>
      <div class="scrim" id="scr"></div>
      <div class="sheet" id="sheet" style="height:410px"><div class="grab"></div>
        <div style="display:flex;align-items:center;gap:12px;padding:14px 18px 10px">${ailaIcon(46, 11, 'sh')}
          <div style="flex:1"><div style="font-size:16.5px;font-weight:650">AILA</div><div style="font-size:13px;color:#8E8E93;margin-top:1px">${URLTXT}</div></div>
          <div style="width:30px;height:30px;border-radius:50%;background:rgba(118,118,128,.16);display:grid;place-items:center;color:#6B6B70">${I('x', 'style="width:15px;height:15px"')}</div></div>
        <div style="display:flex;justify-content:space-around;padding:8px 8px 16px">${[['copy', 'Copia'], ['bookmark', 'Segnalibro'], ['book-open', 'Elenco lettura'], ['ellipsis', 'Altro']].map(([ic, l]) =>
          `<div style="display:flex;flex-direction:column;align-items:center;gap:6px;font-size:11.5px;color:#3C3C43;width:84px"><div style="width:56px;height:56px;border-radius:50%;background:#fff;display:grid;place-items:center;color:#111">${I(ic, 'style="width:22px;height:22px"')}</div>${l}</div>`).join('')}</div>
        <div style="margin:0 14px;border-radius:18px;background:#fff;overflow:hidden">
          <div class="shrow">Aggiungi ai preferiti${I('star')}</div>
          <div class="shrow">Trova nella pagina${I('search')}</div>
          <div class="shrow" id="ath"><span id="athl" style="position:absolute;inset:0;background:rgba(10,102,232,.13)"></span><span style="position:relative">Aggiungi alla schermata Home</span>${I('square-plus', 'style="color:#0A66E8;position:relative"')}</div>
        </div></div>
    </div>
    <div class="layer" id="hs" style="background:radial-gradient(circle at 18% 12%,#8DB2FF,transparent 55%),radial-gradient(circle at 88% 42%,#B79CFF,transparent 55%),radial-gradient(circle at 36% 96%,#5ED3C8,transparent 58%),#3D5BD9">
      <div class="sb" style="color:#fff"><span>9:41</span><span style="display:flex;gap:7px;align-items:center">${SIG}<span style="font-size:13px">5G</span>${BAT}</span></div>
      ${HS.map(([ic, c, l], i) => { const [x, y] = slot(i); return `<div class="hsi a" style="left:${x}px;top:${y}px;width:64px;height:90px"><div class="hsic" style="background:linear-gradient(160deg,${c})">${I(ic)}</div><div class="hslb" style="top:70px">${l}</div></div>`; }).join('')}
      <div class="a" id="ai" style="left:${ax}px;top:${ay}px;width:64px;height:90px"><div class="a" id="air" style="left:-14px;top:-14px;width:92px;height:92px;border-radius:26px;box-shadow:0 0 0 3px rgba(255,255,255,.9),0 0 40px rgba(255,255,255,.7)"></div>
        <div class="a" style="left:0;top:0;border-radius:16px;box-shadow:0 8px 18px rgba(0,0,0,.28)">${ailaIcon(64, 16, 'hs')}</div><div class="hslb" style="top:70px">AILA</div></div>
      <div class="a" style="left:14px;right:14px;bottom:20px;height:92px;border-radius:34px;background:rgba(255,255,255,.26);border:1px solid rgba(255,255,255,.4)">
        ${DOCK.map(([ic, c], i) => `<div class="hsic" style="left:${17 + i * 93.3}px;top:14px;background:linear-gradient(160deg,${c})">${I(ic)}</div>`).join('')}</div>
    </div>
    <div class="a" id="launch" style="background:#0F1535;display:grid;place-items:center;z-index:50;overflow:hidden"><div id="lmk" style="width:120px;height:120px">${logoSVG('ln')}</div></div>
    <div class="layer" id="full" style="z-index:51"><div class="${appCls(s)}" style="${appVars(s)}">${homeNow('Chiara')}</div></div>
    <div class="tap" id="tp"></div>`;
  const sc = scene(t0, T.BENTO, `
    <div class="glow a" id="g" style="left:820px;top:-40px;width:1180px;height:1180px;background:radial-gradient(closest-side,rgba(110,120,255,.26),rgba(94,234,212,.06) 60%,transparent)"></div>
    <div class="a" id="x1" style="left:150px;top:286px;width:900px">
      <div class="eye r" style="color:#8FB2FF">${I('download')}<span class="gt">Installazione</span></div>
      <div class="h" style="margin-top:20px"><span class="ln r">Si installa</span><span class="ln r"><span class="gt">in un attimo.</span></span></div>
      <div class="sub r" style="margin-top:34px;width:760px">Tocchi Condividi, poi «Aggiungi alla schermata Home». Niente SideStore, niente file da scaricare.</div></div>
    <div class="a" id="x2" style="left:150px;top:344px;width:900px">
      <div class="h"><span class="ln r">Poi si apre</span><span class="ln r"><span class="gt">come un'app.</span></span></div>
      <div class="sub r" style="margin-top:34px;width:620px">A tutto schermo, con la sua icona, senza la barra del browser.</div></div>
    <div class="a" id="ph" style="left:1190px;top:90px">${devHTML('iphone', s, inner)}</div>`);
  const q = sc.q, g = q('#g'), x1 = q('#x1'), x2 = q('#x2'), ph = q('#ph'), app = q('#ph .scr > .app');
  const [saf, scr, sheet, athl, hs, ai, air, launch, lmk, full, tp, shb, ath] = ['#saf', '#scr', '#sheet', '#athl', '#hs', '#ai', '#air', '#launch', '#lmk', '#full', '#tp', '#shb', '#ath'].map(q);
  const hsi = sc.qa('.hsi');
  const TAPS = [{ t: tShare, el: shb }, { t: tAdd, el: ath }, { t: tOpen, x: ax + 32, y: ay + 32 }];
  sfx(t0 + .05, 'whoosh'); sfx(tShare + .08, 'tap'); sfx(tShare + .4, 'sheet'); sfx(tAdd + .08, 'tap'); sfx(tHome + .05, 'whoosh');
  sfx(tIcon, 'chime'); sfx(tOpen + .08, 'tap'); sfx(tOpen + .3, 'open');
  sc.render = t => {
    drift(sc, t, .02);
    const out = P(t, tOut, .5, E.in);
    pose(g, { o: P(t, t0, 1.2, E.out) * (1 - out) });
    group(x1, t, t0 + .5, tOpen - .35);
    group(x2, t, tOpen + .25, tOut);
    const a = P(t, t0 + .05, 1.35, E.out5);
    pose(ph, { o: clamp(a * 1.6) * (1 - out), y: lerp(420, 0, a) - 40 * out, ry: lerp(-22, 0, a), b: 10 * out });
    // Safari e foglio di condivisione
    const sh = P(t, tShare + .35, .6, E.out5) * (1 - P(t, tHome, .35, E.in));
    pose(scr, { o: sh }); pose(sheet, { o: sh > .002 ? 1 : 0, y: (1 - sh) * 430 });
    athl.style.opacity = P(t, tAdd, .12, E.lin) * (1 - P(t, tAdd + .4, .3, E.lin));
    const toHome = P(t, tHome + .05, .55, E.inOut);
    pose(saf, { o: 1 - toHome, s: lerp(1, .9, toHome) });
    // schermata Home: le icone arrivano, poi quella di AILA
    pose(hs, { o: toHome });
    hsi.forEach((el, i) => { const p = P(t, tHome + .15 + i * .025, .5, E.out5); pose(el, { o: clamp(p * 1.5), s: lerp(.6, 1, p) }); });
    const pa = P(t, tIcon, .6, E.back);
    pose(ai, { o: clamp(P(t, tIcon, .2, E.lin)), s: lerp(.2, 1, pa) });
    const ring = P(t, tIcon + .1, 1.1, E.out);
    air.style.opacity = (ring > 0 && ring < 1) ? (1 - ring) * .9 : 0; air.style.transform = `scale(${lerp(.85, 1.5, ring)})`;
    // l'icona si allarga fino a riempire lo schermo, poi c'è l'app
    const lp = P(t, tOpen + .28, .62, E.inOut);
    if (lp <= 0) pose(launch, { o: 0 }); else {
      pose(launch, { o: 1 });
      launch.style.left = lerp(ax, 0, lp) + 'px'; launch.style.top = lerp(ay, 0, lp) + 'px';
      launch.style.width = lerp(64, 406, lp) + 'px'; launch.style.height = lerp(64, 876, lp) + 'px'; launch.style.borderRadius = lerp(16, 57, lp) + 'px';
      lmk.style.transform = `scale(${lerp(.33, 1, lp)})`;
    }
    const fp = P(t, tOpen + 1.2, .5, E.out);
    pose(full, { o: fp, s: lerp(1.04, 1, fp) });
    taps(tp, app, TAPS, t);
  };
}

// =====================================================================================
// 5 · IN BREVE: la griglia riassuntiva della versione web
// =====================================================================================
{
  const t0 = T.BENTO, tEnd = T.GITA + .95;
  const X = [120, 545, 970, 1395], Y = [180, 450, 720], W1 = 405, W2 = 830, H1 = 250, H2 = 520;
  const icon = (n, c) => `<span class="ico tic" style="color:${c}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[n]}</svg></span>`;
  const small = (x, y, ic, c, tt, ts) => `<div class="tile" style="left:${x}px;top:${y}px;width:${W1}px;height:${H1}px"><div class="a" style="left:34px;top:32px">${icon(ic, c)}</div>
    <div class="a" style="left:34px;right:28px;bottom:30px"><div class="tt" style="font-size:33px">${tt}</div><div class="ts" style="font-size:19.5px">${ts}</div></div></div>`;
  const sc = scene(t0, tEnd, `
    <div class="a h" id="hd" style="left:122px;top:72px;font-size:62px;white-space:nowrap">AILA sul web. <span style="color:var(--mut)">In breve.</span></div>
    <div class="tile" style="left:${X[0]}px;top:${Y[0]}px;width:${W2}px;height:${H2}px">
      <div class="a gt" style="left:44px;top:26px;font-size:450px;font-weight:700;letter-spacing:-.06em;line-height:1">1</div>
      <div class="a" style="left:318px;right:48px;top:150px"><div class="tt" style="font-size:58px">Un solo codice.</div>
        <div class="ts" style="font-size:25px;margin-top:16px">Android, iPhone e adesso il browser: le schermate nascono dallo stesso progetto, compilato in WebAssembly.</div></div></div>
    <div class="tile" style="left:${X[2]}px;top:${Y[0]}px;width:${W1}px;height:${H1}px">
      <div class="a" style="left:34px;top:32px;display:flex;gap:14px">${icon('smartphone', '#8FB2FF')}${icon('tablet', '#A99BFF')}${icon('monitor', '#7FE3D2')}</div>
      <div class="a" style="left:34px;right:28px;bottom:30px"><div class="tt" style="font-size:33px">iPhone, Android, computer</div><div class="ts" style="font-size:19.5px">Safari, Chrome, Edge, Firefox.</div></div></div>
    ${small(X[3], Y[0], 'wifi-off', '#7FE3D2', 'Anche offline', 'Resta una copia dei dati sul dispositivo.')}
    <div class="tile" style="left:${X[2]}px;top:${Y[1]}px;width:${W2}px;height:${H1}px">
      <div class="a" style="left:38px;top:50%;width:420px;transform:translateY(-50%)"><div class="tt" style="font-size:34px">Liquid Glass su iPhone e iPad. <span style="color:#A1A1A6">Material su Android e computer.</span></div></div>
      <div class="a" style="right:214px;top:42px;width:166px;height:166px;border-radius:40px;background:radial-gradient(circle at 20% 20%,#8DB2FF,transparent 60%),radial-gradient(circle at 85% 80%,#67C6D8,transparent 60%),#C9B8FF">
        <div class="a" style="left:22px;right:22px;top:26px;height:54px;border-radius:20px;background:linear-gradient(180deg,rgba(255,255,255,.72),rgba(255,255,255,.36));box-shadow:inset 0 1px 0 #fff,0 8px 18px rgba(30,40,90,.16)"></div>
        <div class="a" style="left:22px;right:22px;bottom:24px;height:44px;border-radius:22px;background:linear-gradient(180deg,rgba(255,255,255,.72),rgba(255,255,255,.36));box-shadow:inset 0 1px 0 #fff,0 8px 18px rgba(30,40,90,.16)"></div></div>
      <div class="a" style="right:36px;top:42px;width:166px;height:166px;border-radius:40px;background:#111318">
        <div class="a" style="left:22px;right:22px;top:26px;height:54px;border-radius:20px;background:#3E2A7A"></div>
        <div class="a" style="left:22px;right:62px;bottom:24px;height:44px;border-radius:22px;background:#9A7DFF"></div></div></div>
    ${small(X[0], Y[2], 'bell-ring', '#8FB2FF', 'Notifiche push', 'Arrivano anche dalla versione web.')}
    ${small(X[1], Y[2], 'file-text', '#A99BFF', 'PDF integrati', 'Circolari e documenti si aprono dentro AILA.')}
    <div class="tile" style="left:${X[2]}px;top:${Y[2]}px;width:${W2}px;height:${H1}px">
      <div class="a" style="left:38px;top:36px"><div class="tt" style="font-size:40px">Si installa da un link.</div><div class="ts" style="font-size:22px">Niente SideStore, niente APK.</div></div>
      <div class="a urlp" style="left:38px;bottom:34px;font-size:27px;padding:13px 26px">${I('globe')}<span class="gt" style="font-weight:620">${URLTXT}</span></div></div>`);
  const hd = sc.q('#hd'), tiles = sc.qa('.tile'), ORDER = [0, 1, 2, 3, 4, 5, 6];
  sfx(t0 + .1, 'whoosh'); ORDER.forEach(i => sfx(t0 + .55 + i * .13, 'tick'));
  sc.render = t => {
    const cover = P(t, T.GITA, .9, E.inOut); // la tenda chiara ci passa sopra: la griglia arretra appena
    sc.cam.style.transform = `scale(${(1 + .02 * clamp((t - t0) / 10) - .05 * cover).toFixed(5)}) translateY(${(-50 * cover).toFixed(1)}px)`;
    line(hd, t, t0 + .25, null);
    tiles.forEach((el, i) => io(el, t, t0 + .5 + ORDER.indexOf(i) * .13, null, { d: .9, dy: 70, s0: .93, b: 12, e: E.out5 }));
  };
}

// =====================================================================================
// 6 · SEZIONE GITA: titolo (da qui il fondo è chiaro)
// =====================================================================================
{
  const t0 = T.GITA, tOut = T.DOCS - .5;
  const PILLS = [['file-text', 'programma.pdf', 310, 205, -6], ['file-text', 'preventivo_v2.pdf', 1585, 190, 5], ['message-circle', '«Ma quanto costa?»', 215, 560, 4],
    ['link', "link dell'agenzia", 1715, 585, -4], ['file-text', 'Circolare n. 112', 440, 895, 3], ['message-circle', '«Dov\'è il programma?»', 1470, 890, -5]];
  const sc = scene(t0, T.DOCS + .5, `
    <div class="curtain" id="cu" style="background:#F5F5F7;border-radius:70px 70px 0 0"></div>
    <div class="glow a" id="g" style="left:310px;top:-110px;width:1300px;height:1300px;background:radial-gradient(closest-side,rgba(255,150,60,.20),rgba(240,69,122,.08) 55%,transparent)"></div>
    ${PILLS.map(([ic, l, x, y]) => `<div class="a pl fcard" style="left:${x}px;top:${y}px;transform-origin:50% 50%;margin:-32px 0 0 -140px;width:280px;height:64px;border-radius:32px;display:flex;align-items:center;justify-content:center;gap:10px;font-size:22px;font-weight:560;color:#6E6E73;white-space:nowrap">${I(ic, 'style="width:22px;height:22px;color:#A1A1A6"')}${l}</div>`).join('')}
    <div class="a" id="bx" style="left:0;width:1920px;top:318px;text-align:center">
      <div class="eye r" style="color:#FF7A1A">${I('bus')}<span class="gt">Sezione Gita</span></div>
      <div class="hero-t" style="margin-top:22px"><span class="ln r">Tutta la gita.</span><span class="ln r">In <span class="gt">un posto solo</span>.</span></div>
    </div>`, true);
  const cu = sc.q('#cu'), g = sc.q('#g'), bx = sc.q('#bx'), pl = sc.qa('.pl');
  sfx(t0 - .05, 'rise'); sfx(t0 + 3.3, 'gather');
  sc.render = t => {
    drift(sc, t, .03);
    cu.style.transform = `translateY(${((1 - P(t, t0, .95, E.inOut)) * 1100).toFixed(1)}px)`;
    pose(g, { o: P(t, t0 + .6, 1.2, E.out) * (1 - P(t, tOut, .5, E.in)) });
    group(bx, t, t0 + .8, tOut, .28, { od: .45 });
    // i pezzi sparsi: galleggiano ai margini, poi convergono al centro e spariscono
    pl.forEach((el, i) => {
      const [, , x, y, r] = PILLS[i], a = P(t, t0 + 1.0 + i * .1, .8, E.out5), c = P(t, t0 + 3.25 + i * .05, .85, E.in);
      const fx = Math.sin(t * .9 + i * 1.7) * 9, fy = Math.cos(t * .8 + i * 2.3) * 8;
      pose(el, { o: clamp(a * 1.4) * (1 - c), x: fx + (960 - x) * c * .82, y: fy + (1 - a) * 40 + (560 - y) * c * .82, s: lerp(.85, 1, a) * lerp(1, .5, c), r: r * (1 - c), b: 8 * c });
    });
  };
}

// =====================================================================================
// 7–9 · LA GITA NEL TELEFONO: elenco, ricerca, caricamento; storico e segnalazioni; assistente
// =====================================================================================
{
  const t0 = T.DOCS, tH = T.VERSIONS, tI = T.ASSISTANT, tEnd = T.FINALE;
  const s = st('glass', false, 'blue');
  const row = (k, ic, tint, t1, t2) => `<div class="grow" data-k="${k}"><div class="ti" style="background:${TINT[tint][0]};color:${TINT[tint][1]}">${I(ic)}</div><div><div class="t1">${t1}</div><div class="t2">${t2}</div></div><div class="chev">${I('chevron-right')}</div></div>`;
  const sec = (id, title, n, rows, empty) => `<div class="gsec" id="${id}"><div class="hd">${title}<span class="cnt">${n}</span></div><div class="card" style="overflow:hidden">${rows}${empty ? `<div class="gempty" style="height:0;opacity:0">${empty}</div>` : ''}</div></div>`;
  const C = TRIP.circ, D = TRIP.docs;
  const bubU = 'align-self:flex-end;max-width:292px;background:var(--acc);color:#fff;font-size:15.5px;line-height:1.4;padding:12px 15px;border-radius:21px 21px 6px 21px;font-weight:500';
  const bubA = 'align-self:flex-start;width:330px;background:var(--card);border:1px solid var(--cardB);box-shadow:var(--cardSh);font-size:15.5px;line-height:1.48;padding:13px 15px;border-radius:21px 21px 21px 6px;min-height:50px';
  const dots = `<div class="think" style="position:absolute;left:0;top:0;display:flex;gap:6px;padding:17px 16px;border-radius:21px;background:var(--card);border:1px solid var(--cardB)">${[0, 1, 2].map(() => '<i class="td" style="width:8px;height:8px;border-radius:50%;background:var(--muted);display:block"></i>').join('')}</div>`;
  const srcChip = (ic, l) => `<span class="src" style="display:inline-flex;align-items:center;gap:6px;font-size:12.5px;font-weight:650;padding:6px 11px;border-radius:11px;background:color-mix(in srgb,var(--acc) 15%,transparent);color:var(--onCont);white-space:nowrap">${I(ic, 'style="width:14px;height:14px"')}${l}</span>`;
  const inner = `
    <div class="layer" id="home">${homeNow('Chiara')}</div>
    <div class="layer" id="gita" style="background:var(--bg)">${SB}
      <div class="bar"><div class="rbtn">${I('chevron-left')}</div><div class="tt">Gita</div><div class="rbtn pri" id="plus">${I('plus')}</div></div>
      <div class="srch" id="srch">${I('search')}<span id="sph">Cerca nella gita…</span><b id="sq"></b><i id="sc" style="width:2px;height:20px;background:var(--acc);margin-left:-8px;display:block"></i><span id="sx" style="margin-left:auto;width:22px;height:22px;border-radius:50%;background:rgba(118,118,128,.2);display:grid;place-items:center;color:#6B6B70">${I('x', 'style="width:12px;height:12px"')}</span></div>
      <div class="a seg" style="left:16px;top:172px"><span class="on">Tutti</span><span>Circolari</span><span>Documenti</span><span>Link</span></div>
      <div class="notice" id="nt">${I('circle-check')}<span id="ntt"></span></div>
      <div class="glist" id="gl">
        ${sec('s1', 'Circolari della gita', 1, row('c1', 'file-text', 'c', `Circolare n. ${C.n}`, `${C.t} · ${C.d}`), 'Nessuna circolare trovata.')}
        ${sec('s2', 'Documenti', 3, D.map(d => row(d.k, 'file-text', d.tint, d.t, `${d.c} · ${d.d} · v${d.v}`)).join(''))}
        ${sec('s3', 'Link utili', 2, TRIP.links.map(l => row(l.k, 'external-link', 'v', l.t, `Link · aggiornato il ${l.d}`)).join(''), 'Nessun link trovato.')}
      </div>
      <div class="fab" id="fab">${assistantMark(25, 'f')}</div>
      <div class="scrim" id="scrim"></div>
      <div class="asheet" id="addsh" style="height:282px"><div class="grab"></div>
        <div style="padding:16px 20px 4px;font-size:21px;font-weight:720;letter-spacing:-.02em">Aggiungi alla gita</div>
        <div style="padding:0 20px 12px;font-size:14.5px;color:var(--faint)">Che cosa vuoi aggiungere?</div>
        <div class="box" style="margin:0 14px">
          <div class="arow" id="opdf" style="height:70px"><div class="ti" style="background:${TINT.b[0]};color:${TINT.b[1]}">${I('file-up')}</div><div><div class="t1">Carica un PDF</div><div class="t2">Programma, preventivo, scadenze, regolamento</div></div></div>
          <div class="arow" style="height:70px"><div class="ti" style="background:${TINT.v[0]};color:${TINT.v[1]}">${I('link')}</div><div><div class="t1">Aggiungi un link</div><div class="t2">Sito dell'agenzia o altre pagine</div></div></div>
        </div></div>
      <div class="asheet" id="detsh" style="height:338px"><div class="grab"></div>
        <div class="a pg" id="pgA" style="left:0;right:0;top:22px">
          <div style="padding:8px 20px 0;font-size:21px;font-weight:720;letter-spacing:-.02em">${D[1].t}</div>
          <div style="padding:5px 20px 14px;font-size:14px;color:var(--faint)">Aggiornato il ${D[1].d} · versione ${D[1].v}</div>
          <div class="abtn" style="margin:0 14px">${I('file-text')}Apri il PDF</div>
          <div class="box" style="margin:14px 14px 0">
            <div class="arow" id="ohist"><div class="ti" style="background:${TINT.b[0]};color:${TINT.b[1]}">${I('history')}</div><div><div class="t1">Storico versioni</div></div><div class="chev" style="margin-left:auto;color:var(--faint)">${I('chevron-right', 'style="width:16px;height:16px"')}</div></div>
            <div class="arow" id="orep"><div class="ti" style="background:${TINT.y[0]};color:${TINT.y[1]}">${I('triangle-alert')}</div><div><div class="t1">Segnala un errore</div><div class="t2">Dati sbagliati o non aggiornati</div></div><div class="chev" style="margin-left:auto;color:var(--faint)">${I('chevron-right', 'style="width:16px;height:16px"')}</div></div>
          </div></div>
        <div class="a pg" id="pgB" style="left:0;right:0;top:22px">
          <div style="display:flex;align-items:center;gap:10px;padding:6px 14px 12px"><div class="rbtn" id="oback" style="width:38px;height:38px;background:rgba(118,118,128,.12);box-shadow:none;border:0">${I('chevron-left')}</div><div style="font-size:21px;font-weight:720;letter-spacing:-.02em">Storico</div></div>
          <div class="box" style="margin:0 14px">
            <div class="arow" style="height:68px"><div class="ti" style="background:${TINT.g[0]};color:${TINT.g[1]}">${I('file-text')}</div><div><div class="t1">Versione 2 (attuale)</div><div class="t2">Caricata il 21 ott</div></div></div>
            <div class="arow" style="height:68px"><div class="ti" style="background:${TINT.s[0]};color:${TINT.s[1]}">${I('file-text')}</div><div><div class="t1">Versione 1</div><div class="t2">Caricata il 19 ott</div></div></div>
          </div></div>
        <div class="a pg" id="pgC" style="left:0;right:0;top:22px">
          <div style="display:flex;align-items:center;gap:10px;padding:6px 14px 6px"><div class="rbtn" style="width:38px;height:38px;background:rgba(118,118,128,.12);box-shadow:none;border:0">${I('chevron-left')}</div><div style="font-size:21px;font-weight:720;letter-spacing:-.02em">Segnala un errore</div></div>
          <div style="padding:2px 20px 12px;font-size:14px;color:var(--faint)">Il Rappresentante la vede e tu ricevi lo stato.</div>
          <div class="box" style="margin:0 14px;height:108px;padding:13px 16px;font-size:15.5px;line-height:1.4"><span id="rph" style="color:var(--faint)">Cosa non va?</span><span id="rq"></span><span id="rc" style="display:inline-block;width:2px;height:1.1em;background:var(--acc);vertical-align:-3px;margin-left:1px"></span></div>
          <div class="abtn" id="osend" style="margin:14px 14px 0">Invia segnalazione</div></div>
      </div>
    </div>
    <div class="layer" id="chat" style="background:var(--bg)">${SB}
      <div class="a" style="left:16px;right:16px;top:58px;display:flex;align-items:center;gap:11px"><div class="rbtn">${I('chevron-left')}</div>
        <div style="width:42px;height:42px;border-radius:50%;background:var(--card);border:1px solid var(--cardB);display:grid;place-items:center">${assistantMark(21, 'c')}</div>
        <div><div style="font-size:19px;font-weight:720;letter-spacing:-.02em">AILA Assistant</div><div style="font-size:12.5px;color:var(--muted);margin-top:1px">Risponde con i dati della tua classe</div></div></div>
      <div class="a" id="msgs" style="left:16px;right:16px;top:128px;display:flex;flex-direction:column;gap:11px">
        <div id="u1" style="${bubU}">${TRIP.q1}</div>
        <div id="w1" style="position:relative;align-self:flex-start">${dots}<div id="a1" style="${bubA}"><span id="a1t"></span>
          <div id="a1s" style="display:none;gap:7px;margin-top:11px;flex-wrap:wrap">${srcChip('file-text', `${D[1].t} · v${D[1].v}`)}${srcChip('file-text', `Circolare n. ${C.n}`)}</div></div></div>
        <div id="u2" style="${bubU}">${TRIP.q2}</div>
        <div id="w2" style="position:relative;align-self:flex-start">${dots}<div id="a2" style="${bubA}"><span id="a2t"></span></div></div>
      </div>
      <div class="a" style="left:14px;right:14px;bottom:26px;display:flex;gap:10px;align-items:center">
        <div style="flex:1;min-height:54px;border-radius:27px;background:var(--card);border:1px solid var(--cardB);box-shadow:var(--cardSh);padding:16px 18px;font-size:15px;color:var(--ink);white-space:nowrap;overflow:hidden"><span id="cq"></span><span id="cc" style="display:inline-block;width:2px;height:1.1em;background:var(--acc);vertical-align:-3px"></span><span id="cph" style="color:var(--faint)">Chiedi qualsiasi cosa…</span></div>
        <div id="csend" style="width:54px;height:54px;border-radius:50%;background:var(--acc);display:grid;place-items:center;color:#fff;flex:none">${I('arrow-up', 'style="width:23px;height:23px"')}</div></div>
    </div>
    <div class="tap" id="tp"></div>`;
  const txt = (id, x, top, html) => `<div class="a" id="${id}" style="left:${x}px;top:${top}px;width:900px">${html}</div>`;
  const H2 = (a, b) => `<div class="h" style="font-size:80px"><span class="ln r">${a}</span><span class="ln r"><span class="gt">${b}</span></span></div>`;
  const SUB = (s2, w = 760) => `<div class="sub r" style="margin-top:32px;width:${w}px">${s2}</div>`;
  const fcard = (id, top, h, ic, tint, t1, t2, extra = '') => `<div class="fcard" id="${id}" style="left:1000px;top:${top}px;width:660px;height:${h}px"><div class="a" style="left:26px;top:${(h - 68) / 2}px;width:68px;height:68px;border-radius:20px;background:${TINT[tint][0]};color:${TINT[tint][1]};display:grid;place-items:center">${I(ic, 'style="width:34px;height:34px"')}</div>
    <div class="a" style="left:116px;right:24px;top:50%;transform:translateY(-50%)"><div class="ft">${t1}</div><div class="fs">${t2}</div></div>${extra}</div>`;
  const sc = scene(t0, tEnd + .95, `
    <div class="glow a" id="gb" style="left:830px;top:-10px;width:1100px;height:1100px;background:radial-gradient(closest-side,rgba(255,150,60,.24),rgba(240,69,122,.10) 55%,transparent)"></div>
    <div class="glow a" id="gb2" style="left:980px;top:330px;width:900px;height:900px;background:radial-gradient(closest-side,rgba(124,77,255,.15),transparent)"></div>
    ${txt('g1', 150, 330, H2('Ogni documento.', 'Al suo posto.') + SUB('Programma, preventivo e regolamento, con le circolari della gita e i link utili. Quello che era sparso tra chat e PDF sta in una schermata.'))}
    ${txt('g2', 150, 356, H2('Cerchi una parola.', 'Trovi il documento.') + SUB("La ricerca filtra l'elenco mentre scrivi."))}
    ${txt('g3', 150, 318, `<div class="eye r" style="color:#6E6E73;font-size:26px">${I('badge-check', 'style="width:28px;height:28px;color:#FF7A1A"')}Vista del Rappresentante</div><div style="height:18px"></div>` + H2('Caricano i Rappresentanti.', 'Leggono tutti.') + SUB('Così in classe gira una sola versione, quella giusta.'))}
    ${txt('h1', 1000, 206, H2('Il preventivo cambia?', 'Quello vecchio resta.') + SUB('Ogni documento conserva lo storico delle versioni, con la data di caricamento.', 690))}
    ${txt('h2', 1000, 206, H2('Un dato non torna?', 'Lo segnali.') + SUB('Il Rappresentante vede la segnalazione e tu ne segui lo stato.', 640))}
    ${txt('i1', 1000, 128, `<div class="eye r" style="font-size:27px;color:#6E6E73">${assistantMark(30, 'e')}AILA Assistant</div><div style="height:18px"></div>` + H2('Fai una domanda.', 'Risponde con le fonti.') + SUB('Legge i documenti e le circolari della gita, e ti dice da quale arriva ogni risposta.'))}
    ${fcard('v2', 584, 124, 'file-text', 'g', 'Versione 2 <span style="color:#047857">· attuale</span>', 'Caricata il 21 ottobre')}
    ${fcard('v1', 728, 124, 'file-text', 's', 'Versione 1', 'Caricata il 19 ottobre')}
    ${fcard('rp', 584, 150, 'triangle-alert', 'y', `«${TRIP.report}»`, D[1].t, `<div class="a" style="right:24px;bottom:34px;height:38px;width:150px"><span class="pillc a" id="st1" style="right:0;top:0;background:rgba(245,158,11,.2);color:#B45309">In attesa</span><span class="pillc a" id="st2" style="right:0;top:0;background:rgba(16,185,129,.18);color:#047857">${I('check', 'style="width:19px;height:19px"')}Risolta</span></div>`)}
    ${fcard('f1', 590, 112, 'file-text', 'g', D[1].t, `versione ${D[1].v} · ${longDay(D[1].d)}`)}
    ${fcard('f2', 720, 112, 'file-text', 'c', `Circolare n. ${C.n}`, `${C.t} · ${longDay(C.d)}`)}
    <div class="a sub" id="nf" style="left:1000px;top:866px;width:800px;font-size:29px"><b style="color:var(--tx);font-weight:620">E se un'informazione non c'è, lo dice.</b> Non inventa.</div>
    <div class="a" id="ph" style="left:1165px;top:90px">${devHTML('iphone', s, inner)}</div>`, true);
  const q = sc.q, app = q('#ph .scr > .app'), ph = q('#ph');
  const el = Object.fromEntries(['gb', 'gb2', 'g1', 'g2', 'g3', 'h1', 'h2', 'i1', 'v2', 'v1', 'rp', 'st1', 'st2', 'f1', 'f2', 'nf', 'home', 'gita', 'plus', 'srch', 'sph', 'sq', 'sc', 'sx', 'nt', 'ntt', 'gl',
    'fab', 'scrim', 'addsh', 'detsh', 'pgA', 'pgB', 'pgC', 'opdf', 'ohist', 'orep', 'oback', 'osend', 'rph', 'rq', 'rc', 'chat', 'u1', 'a1', 'a1t', 'a1s', 'u2', 'a2', 'a2t', 'cq', 'cc', 'cph', 'csend', 'tp'].map(k => [k, q('#' + k)]));
  const secs = sc.qa('.gsec'), rows = Object.fromEntries(sc.qa('.grow').map(r => [r.dataset.k, r]));
  const cnt = sc.qa('.gsec .cnt'), empties = sc.qa('.gempty'), thinks = sc.qa('.think'), busb = q('#home .busb'), srcs = sc.qa('#a1s .src');

  // tempi
  const tBus = t0 + 1.2, tGo = t0 + 1.5;
  const tS = t0 + 6.0, tType = tS + .45, QCPS = 5, tF = tType + 3 / QCPS, tClear = t0 + 10.1;
  const tPlus = t0 + 11.0, tTapPlus = t0 + 11.55, tPdf = t0 + 12.7, tNew = t0 + 13.3;
  const tRow = tH + 1.0, tHist = tH + 2.3, tBack = tH + 5.0, tRep = tH + 5.55, tRType = tH + 6.2, RCPS = 24, tSend = tH + 7.6, tSent = tH + 8.05, tSolved = tH + 9.0;
  const tFab = tI + .35, CCPS = 26, tQ1 = tI + 1.15, tSend1 = tQ1 + TRIP.q1.length / CCPS + .25, tA1 = tSend1 + 1.15, ACPS = 38, tSrc = tA1 + TRIP.a1.length / ACPS + .15;
  const tQ2 = tSrc + 1.3, tSend2 = tQ2 + TRIP.q2.length / CCPS + .25, tA2 = tSend2 + 1.0;
  const TAPS = [{ t: tBus, el: busb }, { t: tS, el: el.srch }, { t: tTapPlus, el: el.plus }, { t: tPdf, el: el.opdf }, { t: tRow, el: rows.d2 }, { t: tHist, el: el.ohist },
    { t: tBack, el: el.oback }, { t: tRep, el: el.orep }, { t: tSend, el: el.osend }, { t: tFab, el: el.fab }, { t: tSend1, el: el.csend }, { t: tSend2, el: el.csend }];
  sfx(t0 + .05, 'whoosh'); TAPS.forEach(e => sfx(e.t + .08, 'tap'));
  sfx(tGo, 'open'); for (let i = 0; i < TRIP.query.length; i++) sfx(tType + i / QCPS, 'key');
  sfx(tF + .1, 'tick'); sfx(tClear + .05, 'tick'); sfx(tTapPlus + .4, 'sheet'); sfx(tNew + .1, 'chime');
  sfx(tH - .1, 'whoosh'); sfx(tRow + .35, 'sheet'); sfx(tH + 2.9, 'pop'); sfx(tH + 3.05, 'pop');
  for (let i = 0; i < TRIP.report.length; i += 3) sfx(tRType + i / RCPS, 'key');
  sfx(tSent + .05, 'chime'); sfx(tSolved, 'done');
  sfx(tFab + .25, 'open');
  for (let i = 0; i < TRIP.q1.length; i += 3) sfx(tQ1 + i / CCPS, 'key');
  for (let i = 0; i < TRIP.q2.length; i += 3) sfx(tQ2 + i / CCPS, 'key');
  sfx(tA1, 'answer'); sfx(tSrc + .1, 'pop'); sfx(tSrc + .25, 'pop'); sfx(tA2, 'answer');

  sc.render = t => {
    drift(sc, t, .015);
    // ---- testi
    group(el.g1, t, t0 + .7, tS - .35);
    group(el.g2, t, tS + .2, tClear + .15);
    group(el.g3, t, tPlus - .15, tH - .4);
    group(el.h1, t, tH + .55, tBack - .1);
    group(el.h2, t, tRep + .1, tI - .4);
    group(el.i1, t, tI + .5, null);
    // ---- telefono
    const a = P(t, t0 + .05, 1.3, E.out5), mv = P(t, tH - .2, 1.2, E.inOut);
    const z = P(t, tS - .25, 1.0, E.inOut) * (1 - P(t, tClear - .1, 1.0, E.inOut));
    const px = -860 * mv - 40 * z;
    pose(ph, { o: clamp(a * 1.6), x: px, y: lerp(440, 0, a) + 168 * z, s: 1 + .34 * z, ry: lerp(-20, 0, a) + 9 * Math.sin(Math.PI * mv) });
    pose(el.gb, { o: P(t, t0, 1.4, E.out), x: -860 * mv }); pose(el.gb2, { o: P(t, t0, 1.4, E.out), x: -860 * mv });
    // Home → Gita
    const go = P(t, tGo, .6, E.out5);
    pose(el.home, { o: go >= 1 ? 0 : 1 - .45 * go, x: -120 * go });
    pose(el.gita, { o: go > 0 ? 1 : 0, x: 406 * (1 - go) });
    secs.forEach((sx, i) => io(sx, t, tGo + .22 + i * .11, null, { dy: 26, b: 5, d: .55 }));
    // ricerca
    const typing = t >= tType && t < tClear;
    el.sph.style.display = typing ? 'none' : '';
    typeText(el.sq, typing ? t : -1, tType, QCPS, TRIP.query, null);
    el.sc.style.display = (t >= tS + .2 && t < tClear && (t < tType + 1 || Math.floor(t * 2.4) % 2 === 0)) ? 'block' : 'none';
    el.sx.style.display = typing ? 'grid' : 'none';
    el.srch.style.borderColor = (t >= tS + .2 && t < tClear) ? 'var(--acc)' : '';
    const f = P(t, tF, .5, E.inOut) * (1 - P(t, tClear + .05, .5, E.inOut));
    const v4 = P(t, tNew + .15, .55, E.inOut);
    Object.entries(rows).forEach(([k, r]) => rowV(r, (TRIP.match.includes(k) ? 1 : 1 - f) * (k === 'd4' ? v4 : 1)));
    empties.forEach(e => rowV(e, f, 44));
    const filt = f > .5, nd = v4 > .5 ? 4 : 3;
    setText(cnt[0], filt ? '0' : '1'); setText(cnt[1], filt ? '1' : String(nd)); setText(cnt[2], filt ? '0' : '2');
    // il Rappresentante aggiunge un documento
    const pp = P(t, tPlus, .5, E.back);
    pose(el.plus, { o: clamp(P(t, tPlus, .15, E.lin)), s: pp });
    const aS = P(t, tTapPlus + .35, .55, E.out5) * (1 - P(t, tPdf + .3, .35, E.in));
    pose(el.addsh, { o: aS > .002 ? 1 : 0, y: (1 - aS) * 300 });
    const n1 = P(t, tNew, .45, E.inOut) * (1 - P(t, tH + .1, .4, E.inOut)), n2 = P(t, tSent, .45, E.inOut) * (1 - P(t, tI + .05, .3, E.inOut)), nn = Math.max(n1, n2);
    setText(el.ntt, t < tH + 4 ? 'Documento caricato.' : 'Segnalazione inviata: riceverai lo stato.');
    pose(el.nt, { o: nn, y: -14 * (1 - nn) }); el.gl.style.top = (222 + 55 * nn).toFixed(1) + 'px';
    // scheda del documento: dettaglio, storico, segnalazione
    const dS = P(t, tRow + .3, .55, E.out5) * (1 - P(t, tSend + .3, .35, E.in));
    pose(el.detsh, { o: dS > .002 ? 1 : 0, y: (1 - dS) * 356 });
    pose(el.scrim, { o: Math.max(aS, dS) });
    const toB = P(t, tHist + .25, .32, E.inOut), backA = P(t, tBack + .22, .32, E.inOut), toC = P(t, tRep + .25, .32, E.inOut);
    const oA = (1 - toB) + backA * (1 - toC), oB = toB * (1 - backA);
    pose(el.pgA, { o: clamp(oA), x: -34 * (1 - clamp(oA)) }); pose(el.pgB, { o: oB, x: 34 * (1 - oB) }); pose(el.pgC, { o: toC, x: 34 * (1 - toC) });
    const rTyping = t >= tRType;
    el.rph.style.display = rTyping ? 'none' : '';
    typeText(el.rq, t, tRType, RCPS, TRIP.report, el.rc);
    // schede grandi accanto al telefono
    io(el.v2, t, tH + 2.9, tBack - .05, { dx: -80, dy: 0, s0: .9, b: 10, d: .7 });
    io(el.v1, t, tH + 3.05, tBack, { dx: -80, dy: 0, s0: .9, b: 10, d: .7 });
    io(el.rp, t, tRType + .2, tI - .35, { dx: -80, dy: 0, s0: .9, b: 10, d: .7 });
    const sv = P(t, tSolved, .4, E.out5);
    pose(el.st1, { o: 1 - sv, s: lerp(1, .8, sv) }); pose(el.st2, { o: sv, s: lerp(.7, 1, P(t, tSolved, .45, E.back)) });
    // ---- assistente
    const cR = P(t, tFab + .25, .75, E.inOut);
    pose(el.chat, { o: cR > 0 ? 1 : 0 });
    el.chat.style.clipPath = cR >= 1 ? '' : `circle(${(cR * 1060).toFixed(1)}px at 361px 823px)`;
    const typing1 = t >= tQ1 && t < tSend1 + .12, typing2 = t >= tQ2 && t < tSend2 + .12;
    el.cph.style.display = (typing1 || typing2) ? 'none' : '';
    const cur = typing1 ? [tQ1, TRIP.q1] : typing2 ? [tQ2, TRIP.q2] : null;
    setText(el.cq, cur ? cur[1].slice(0, Math.floor(clamp((t - cur[0]) * CCPS, 0, cur[1].length))) : '');
    el.cc.style.opacity = (typing1 || typing2) ? 1 : 0;
    const bub = (u, a2, at, think, ts, ta, text) => {
      io(u, t, ts + .12, null, { dy: 22, s0: .92, b: 4, d: .4 });
      const th = t >= ts + .5 && t < ta;
      pose(think, { o: th ? 1 : 0 });
      if (th) [...think.children].forEach((d, i) => { d.style.transform = `translateY(${(-4 * Math.max(0, Math.sin((t - ts) * 9 - i * .9))).toFixed(1)}px)`; });
      pose(a2, { o: t >= ta ? clamp((t - ta) / .2) : 0 });
      typeText(at, t, ta, ACPS, text, null);
    };
    bub(el.u1, el.a1, el.a1t, thinks[0], tSend1, tA1, TRIP.a1);
    bub(el.u2, el.a2, el.a2t, thinks[1], tSend2, tA2, TRIP.a2);
    el.a1s.style.display = t >= tSrc ? 'flex' : 'none';
    srcs.forEach((s2, i) => io(s2, t, tSrc + i * .14, null, { dy: 10, s0: .8, b: 3, d: .4, e: E.back }));
    io(el.f1, t, tSrc + .35, null, { dx: -120, dy: 0, s0: .85, b: 10, d: .8 });
    io(el.f2, t, tSrc + .5, null, { dx: -120, dy: 0, s0: .85, b: 10, d: .8 });
    line(el.nf, t, tA2 + .9, null, { dy: 26 });
    taps(el.tp, app, TAPS, t);
  };
}

// =====================================================================================
// 10 · FINALE
// =====================================================================================
{
  const t0 = T.FINALE;
  const sc = scene(t0, DURATION + .1, `
    <div class="curtain" id="cu" style="background:#000;border-radius:70px 70px 0 0"></div>
    <div class="glow a" id="g" style="left:310px;top:-150px;width:1300px;height:1300px;background:radial-gradient(closest-side,rgba(96,120,255,.30),rgba(150,100,250,.10) 55%,transparent)"></div>
    <div class="a" id="lg" style="left:865px;top:196px;width:190px;height:190px">${logoSVG('fin')}</div>
    <div class="a word" id="w" style="left:0;width:1920px;top:430px;text-align:center">AILA</div>
    <div class="a h c" id="l1" style="left:0;width:1920px;top:604px;font-size:54px;letter-spacing:-.035em">Ora anche nel browser. <span class="gt">E con tutta la gita dentro.</span></div>
    <div class="a" id="u" style="left:0;width:1920px;top:724px;text-align:center"><span class="urlp">${I('globe')}${URLTXT}</span></div>`);
  const [cu, g, lg, w, l1, u] = ['#cu', '#g', '#lg', '#w', '#l1', '#u'].map(sc.q);
  sfx(t0 - .05, 'rise'); sfx(t0 + 1.25, 'logo');
  sc.render = t => {
    drift(sc, t, .035);
    cu.style.transform = `translateY(${((1 - P(t, t0, .95, E.inOut)) * 1100).toFixed(1)}px)`;
    pose(g, { o: P(t, t0 + .8, 2.2, E.inOut), s: lerp(.85, 1.05, P(t, t0, 10, E.lin)) });
    pose(lg, { o: t >= t0 + .9 ? 1 : 0 });
    renderLogo(lg, t, t0 + 1.2, 1.5);
    line(w, t, t0 + 2.25, null, { dy: 30, s0: 1.08 });
    line(l1, t, t0 + 3.2, null);
    line(u, t, t0 + 4.1, null, { dy: 26 });
  };
}

// ---------- fondo e dissolvenza finale ----------
// Un velo di rumore finissimo (pochi livelli su 255) sopra tutto: non si vede, ma spezza le "righe" che le
// sfumature larghe farebbero nel video compresso.
{
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const x = c.getContext('2d'), im = x.createImageData(256, 256), R = rng(2026);
  for (let i = 0; i < im.data.length; i += 4) { const v = R(); im.data[i] = im.data[i + 1] = im.data[i + 2] = v < .5 ? 0 : 255; im.data[i + 3] = Math.round(R() * 4); }
  x.putImageData(im, 0, 0);
  const d = document.createElement('div');
  d.style.cssText = `position:absolute;inset:0;z-index:90;pointer-events:none;background:url(${c.toDataURL()})`;
  stage.appendChild(d);
}
const fade = document.createElement('div');
fade.style.cssText = 'position:absolute;inset:0;background:#000;z-index:100;pointer-events:none';
stage.appendChild(fade);
function renderBg(t) {
  stage.style.background = (t >= T.GITA + .9 && t < T.FINALE + .9) ? '#F5F5F7' : '#000';
  const f = Math.max(1 - P(t, 0, .7, E.out), P(t, DURATION - 1.1, 1.0, E.inOut));
  fade.style.opacity = f.toFixed(3); fade.style.display = f > .002 ? '' : 'none';
}

// ---------- motore ----------
function seek(t) {
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
  renderBg(t);
}
window.seek = seek; window.DURATION = DURATION;
window.SFX = SFX.slice().sort((a, b) => a.t - b.t);

// ---------- anteprima nel browser ----------
const capture = location.search.includes('capture');
if (capture) document.body.classList.add('capture');
function fit() {
  if (capture) return;
  const k = Math.min(innerWidth / 1920, (innerHeight - 56) / 1080);
  stage.style.transform = `scale(${k})`;
  stage.style.left = ((innerWidth - 1920 * k) / 2) + 'px';
}
addEventListener('resize', fit); fit();
seek(0);
if (!capture) {
  const m = location.hash.match(/t=([\d.]+)/);
  let tStart = m ? +m[1] : 0, playing = true, base = performance.now() - tStart * 1000, cur = tStart;
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
