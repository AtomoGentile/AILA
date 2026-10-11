import { chromium, devices } from 'playwright';

const UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...devices['iPhone 13'], userAgent: UA, hasTouch: true });
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

const first = await info();
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
