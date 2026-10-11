import { chromium, devices } from 'playwright';

const UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...devices['iPhone 13'], userAgent: UA, hasTouch: true, locale: 'it-IT', timezoneId: 'Europe/Rome' });
const page = await ctx.newPage();
page.on('pageerror', (e) => console.log('PAGEERROR', e.message));
page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE.ERROR', m.text().slice(0, 300)); });

await page.goto('http://localhost:8080/?kbddebug=1', { waitUntil: 'load' });
await page.waitForFunction(() => !document.getElementById('aila-splash'), null, { timeout: 90000 }).catch(() => console.log('splash non rimosso'));
await page.waitForTimeout(3000);

const info = () => page.evaluate(() => {
  const host = [...document.body.querySelectorAll('div')].find((d) => d.shadowRoot);
  const sr = host && host.shadowRoot;
  const boxes = sr ? [...sr.querySelectorAll('[role="textbox"]')].map((b) => { const r = b.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) }; }) : [];
  return { shadow: !!sr, boxes, vv: window.visualViewport ? Math.round(window.visualViewport.height) : null };
});
const state = () => page.evaluate(() => {
  const host = [...document.body.querySelectorAll('div')].find((d) => d.shadowRoot);
  const sr = host && host.shadowRoot;
  const d = document.activeElement;
  const s = sr && sr.activeElement;
  const desc = (e) => e ? e.tagName + (e.getAttribute('role') ? '[' + e.getAttribute('role') + ']' : '') : 'null';
  const backing = sr ? sr.querySelectorAll('textarea,input').length : -1;
  return `doc=${desc(d)} shadow=${desc(s)} campiNascosti=${backing} vv=${window.visualViewport ? Math.round(window.visualViewport.height) : '?'}`;
});

const a11y = () => page.evaluate(() => {
  const host = [...document.body.querySelectorAll('div')].find((d) => d.shadowRoot);
  const sr = host && host.shadowRoot;
  if (!sr) return [];
  return [...sr.querySelectorAll('#cmp_a11y_root *')].map((n) => {
    const r = n.getBoundingClientRect();
    return { role: n.getAttribute('role') || '', t: (n.innerText || '').slice(0, 40).replace(/\n/g, ' / '), x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height), click: !!n.onclick };
  }).filter((n) => n.w > 0 && n.h > 0);
});
// L'app parte da una schermata "Caricamento...": si aspetta che compaiano pulsanti o campi.
for (let i = 0; i < 40; i++) {
  const n = await a11y();
  if (n.some((x) => x.role === 'button' || x.role === 'textbox')) break;
  await page.waitForTimeout(1000);
}
let first = await info();
for (let step = 0; step < 6 && !first.boxes.length; step++) {
  const nodes = await a11y();
  console.log(`NODI (passo ${step}):`, JSON.stringify(nodes.filter((n) => n.t || n.role).slice(0, 25)));
  const target = nodes.find((n) => n.role === 'button' && /accedi|login|continua|inizia|avanti|entra|salta|registr|email|ospite/i.test(n.t))
    || nodes.find((n) => n.role === 'button' && n.t);
  if (!target) { console.log('nessun pulsante da toccare'); break; }
  console.log('PASSO', step, 'tocco il pulsante', JSON.stringify(target));
  await page.touchscreen.tap(target.x + target.w / 2, target.y + target.h / 2);
  await page.waitForTimeout(1500);
  first = await info();
}
console.log('INFO', JSON.stringify(first));
await page.screenshot({ path: 'before.png' });
if (!first.boxes.length) {
  console.log('Nessun campo di testo visibile nella schermata iniziale: provo a toccare in basso/al centro');
}

async function tapAndWatch(label, x, y) {
  console.log(`--- ${label}: tap ${x},${y}`);
  await page.touchscreen.tap(x, y);
  for (const ms of [0, 100, 250, 500, 750, 1000, 1500, 2500]) {
    if (ms) await page.waitForTimeout(ms === 100 ? 100 : ms - ([0, 100, 250, 500, 750, 1000, 1500, 2500][[0, 100, 250, 500, 750, 1000, 1500, 2500].indexOf(ms) - 1]));
    console.log(`  +${ms}ms ${await state()}`);
  }
  const strip = await page.evaluate(() => { const p = document.querySelector('pre'); return p ? p.textContent : '(nessuna striscia di diagnosi)'; });
  console.log(strip);
}

for (const b of first.boxes.slice(0, 2)) {
  await tapAndWatch(`campo ${b.w}x${b.h}`, b.x + b.w / 2, b.y + b.h / 2);
  // secondo tocco sullo stesso campo (spostamento del cursore)
  await tapAndWatch('stesso campo, di nuovo', b.x + b.w / 2, b.y + b.h / 2);
  // tocco fuori
  await tapAndWatch('fuori dal campo', 195, 40);
}
await browser.close();
