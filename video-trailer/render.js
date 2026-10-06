// Esporta il trailer fotogramma per fotogramma con Chromium headless.
//   node render.js snap 5 17.5 30     -> snaps/t_5.png ... (controllo veloce di singoli istanti)
//   node render.js sfx                -> sfx.json (tempi degli effetti sonori, letti da music.js)
//   node render.js frames             -> build/video.mp4 (muto) + sfx.json
// Variabili: CHROME (eseguibile di Chrome/Chromium), FFMPEG (default "ffmpeg"), WORKERS (pagine in parallelo).
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { spawn, execFileSync } = require('child_process');

const FPS = 30;
const WORKERS = +(process.env.WORKERS || 4);
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
const CHROME = process.env.CHROME || [
  '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].find(p => fs.existsSync(p));
const URL = pathToFileURL(path.join(__dirname, 'index.html')).href + '?capture';

async function openPage(browser) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', e => console.error('ERRORE pagina:', e.message));
  page.on('console', m => m.type() === 'error' && console.error('console:', m.text()));
  await page.goto(URL);
  await page.evaluate(() => document.fonts.ready);
  return page;
}

(async () => {
  const [mode, ...args] = process.argv.slice(2);
  const browser = await chromium.launch({ executablePath: CHROME, args: ['--force-color-profile=srgb', '--hide-scrollbars'] });
  if (mode === 'sfx') {
    const page = await openPage(browser);
    fs.writeFileSync('sfx.json', JSON.stringify(await page.evaluate(() => window.SFX)));
  } else if (mode === 'snap') {
    const page = await openPage(browser);
    fs.mkdirSync('snaps', { recursive: true });
    for (const a of args) {
      await page.evaluate(t => seek(t), +a);
      await page.screenshot({ path: `snaps/t_${a}.png` });
    }
  } else if (mode === 'frames') {
    // ogni pagina codifica un pezzo contiguo del video in un segmento H.264 (niente PNG su disco),
    // poi i segmenti vengono uniti senza ricodifica
    const dir = process.env.OUT || 'build';
    fs.mkdirSync(dir, { recursive: true });
    const probe = await openPage(browser);
    const duration = await probe.evaluate(() => window.DURATION);
    fs.writeFileSync('sfx.json', JSON.stringify(await probe.evaluate(() => window.SFX)));
    await probe.close();
    const total = Math.round(duration * FPS);
    const per = Math.ceil(total / WORKERS);
    let done = 0; const t0 = Date.now();
    await Promise.all([...Array(WORKERS)].map(async (_, w) => {
      const page = await openPage(browser);
      const enc = spawn(FFMPEG, ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
        '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(FPS), `${dir}/seg${w}.mp4`],
        { stdio: ['pipe', 'inherit', 'inherit'] });
      const closed = new Promise(r => enc.on('close', r));
      for (let f = w * per; f < Math.min(total, (w + 1) * per); f++) {
        await page.evaluate(t => seek(t), f / FPS);
        const png = await page.screenshot({ type: 'png' });
        if (!enc.stdin.write(png)) await new Promise(r => enc.stdin.once('drain', r));
        if (++done % 150 === 0) console.log(`${done}/${total} fotogrammi, ${((Date.now() - t0) / 1000).toFixed(0)} s`);
      }
      enc.stdin.end(); await closed;
    }));
    fs.writeFileSync(`${dir}/segments.txt`, [...Array(WORKERS)].map((_, w) => `file 'seg${w}.mp4'`).join('\n'));
    execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', `${dir}/segments.txt`, '-c', 'copy', `${dir}/video.mp4`]);
    console.log('fatto:', total, 'fotogrammi ->', `${dir}/video.mp4`);
  }
  await browser.close();
})();
