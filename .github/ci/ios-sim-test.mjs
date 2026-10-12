import { remote } from 'webdriverio';

const udid = process.env.UDID;
const base = process.env.BASE_URL;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log(...a);

const driver = await remote({
  hostname: '127.0.0.1', port: 4723, path: '/', logLevel: 'error', connectionRetryTimeout: 600000,
  capabilities: {
    platformName: 'iOS',
    'appium:automationName': 'XCUITest',
    'appium:udid': udid,
    'appium:platformVersion': process.env.PLATFORM_VERSION,
    'appium:deviceName': process.env.DEVICE_NAME || 'iPhone',
    'appium:showXcodeLog': true,
    'appium:useNewWDA': true,
    'appium:wdaStartupRetries': 1,
    browserName: 'Safari',
    'appium:connectHardwareKeyboard': false,
    'appium:wdaLaunchTimeout': 420000,
    'appium:wdaConnectionTimeout': 420000,
    'appium:simulatorStartupTimeout': 300000,
    'appium:newCommandTimeout': 900,
    'appium:safariAllowPopups': true,
  },
});
log('sessione aperta');

async function webRects() {
  return driver.execute(() => {
    const host = [...document.body.querySelectorAll('div')].find((d) => d.shadowRoot);
    const sr = host && host.shadowRoot;
    if (!sr) return { root: false, nodes: [] };
    const nodes = [...sr.querySelectorAll('#cmp_a11y_root *')].map((n) => {
      const r = n.getBoundingClientRect();
      return { role: n.getAttribute('role') || '', t: (n.innerText || '').slice(0, 30), x: r.left, y: r.top, w: r.width, h: r.height };
    }).filter((n) => n.w > 0 && n.h > 0);
    return { root: true, nodes, iw: window.innerWidth, ih: window.innerHeight, ow: window.outerWidth };
  });
}

async function nativeTap(cssX, cssY) {
  // Coordinate CSS -> punti nativi: si usa il riquadro della web view di Safari.
  await driver.switchContext('NATIVE_APP');
  const wv = await driver.$('-ios predicate string:type == "XCUIElementTypeWebView"');
  const loc = await wv.getLocation();
  const size = await wv.getSize();
  const info = await driver.execute('mobile: deviceScreenInfo').catch(() => null);
  log('webview', JSON.stringify(loc), JSON.stringify(size), info ? JSON.stringify(info) : '');
  await driver.execute('mobile: tap', { x: Math.round(loc.x + cssX), y: Math.round(loc.y + cssY) });
}

async function keyboardUp() {
  await driver.switchContext('NATIVE_APP');
  const k = await driver.$$('-ios predicate string:type == "XCUIElementTypeKeyboard"');
  return k.length > 0;
}

async function toWeb() {
  const ctxs = await driver.getContexts();
  const web = ctxs.find((c) => String(c.id || c).startsWith('WEBVIEW'));
  await driver.switchContext(web ? (web.id || web) : ctxs[ctxs.length - 1]);
}

async function run(label, url) {
  log(`\n===== ${label}: ${url}`);
  await toWeb().catch(() => {});
  await driver.url(url);
  // attesa dell'avvio dell'app
  let ok = false;
  for (let i = 0; i < 90; i++) {
    await toWeb().catch(() => {});
    const r = await webRects().catch(() => ({ root: false, nodes: [] }));
    if (r.root && r.nodes.some((n) => n.role === 'button' || n.role === 'textbox')) { ok = true; break; }
    await sleep(2000);
  }
  log('app avviata:', ok);
  if (!ok) return;
  let r = await webRects();
  for (let step = 0; step < 4 && !r.nodes.some((n) => n.role === 'textbox'); step++) {
    const btn = r.nodes.find((n) => n.role === 'button' && /salta|registr/i.test(n.t)) || r.nodes.find((n) => n.role === 'button' && n.t);
    if (!btn) break;
    log('tocco il pulsante', btn.t);
    await nativeTap(btn.x + btn.w / 2, btn.y + btn.h / 2);
    await sleep(2500);
    await toWeb();
    r = await webRects();
  }
  const box = r.nodes.find((n) => n.role === 'textbox');
  if (!box) { log('nessun campo di testo trovato'); return; }
  log('campo:', JSON.stringify(box));
  await nativeTap(box.x + box.w / 2, box.y + box.h / 2);
  const seen = [];
  for (const ms of [300, 700, 1500, 3000]) {
    await sleep(ms === 300 ? 300 : ms - ({ 700: 300, 1500: 700, 3000: 1500 }[ms]));
    seen.push(`${ms}ms:${await keyboardUp()}`);
  }
  log('TASTIERA', seen.join(' '));
  await toWeb();
  const strip = await driver.execute(() => { const p = document.querySelector('pre'); return p ? p.textContent : '(nessuna striscia)'; });
  log(strip);
  // secondo tocco
  await nativeTap(box.x + box.w / 2, box.y + box.h / 2);
  await sleep(1500);
  log('TASTIERA dopo il secondo tocco:', await keyboardUp());
}

try {
  const caps = await driver.getCapabilities();
  log('piattaforma', caps.platformVersion || JSON.stringify(caps).slice(0, 200));
  await run('SENZA lo script (kbdoff=1)', `${base}?kbddebug=1&kbdoff=1`);
  await run('CON lo script', `${base}?kbddebug=1`);
} catch (e) {
  log('ERRORE', e && e.message ? e.message : e);
  process.exitCode = 1;
} finally {
  await driver.deleteSession().catch(() => {});
}
