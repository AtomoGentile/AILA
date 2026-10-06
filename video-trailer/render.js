// Esporta il trailer fotogramma per fotogramma con Chromium headless.
//   node render.js snap 5 17.5 30     -> snaps/t_5.png ... (controllo veloce di singoli istanti)
//   node render.js sfx                -> sfx.json (tempi degli effetti sonori, letti da music.js)
//   node render.js frames             -> build/video.mp4 (muto) + sfx.json; se interrotto, riparte dai blocchi mancanti
// Con --page=intro (o PAGE=intro) lavora su intro.html: sfx-intro.json, build/intro/video.mp4, snaps/intro_*.png.
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
const PAGE = (process.argv.find(a => a.startsWith('--page=')) || '').slice(7) || process.env.PAGE || 'index';
const URL = pathToFileURL(path.join(__dirname, PAGE + '.html')).href + '?capture';
const SFX_FILE = PAGE === 'index' ? 'sfx.json' : `sfx-${PAGE}.json`;
const PREFIX = PAGE === 'index' ? 't' : PAGE;

async function openPage(browser) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', e => console.error('ERRORE pagina:', e.message));
  page.on('console', m => m.type() === 'error' && console.error('console:', m.text()));
  await page.goto(URL);
  await page.evaluate(() => document.fonts.ready);
  return page;
}

(async () => {
  const [mode, ...args] = process.argv.slice(2).filter(a => !a.startsWith('--page='));
  const browser = await chromium.launch({ executablePath: CHROME, args: ['--force-color-profile=srgb', '--hide-scrollbars'] });
  if (mode === 'sfx') {
    const page = await openPage(browser);
    fs.writeFileSync(SFX_FILE, JSON.stringify(await page.evaluate(() => window.SFX)));
  } else if (mode === 'snap') {
    const page = await openPage(browser);
    fs.mkdirSync('snaps', { recursive: true });
    for (const a of args) {
      await page.evaluate(t => seek(t), +a);
      await page.screenshot({ path: `snaps/${PREFIX}_${a}.png` });
    }
  } else if (mode === 'frames') {
    // il video è diviso in blocchi da 150 fotogrammi (5 s), ognuno codificato in H.264 a parte: le pagine
    // prendono il blocco successivo libero, un blocco finito non si rifà (si può interrompere e riprendere),
    // alla fine i blocchi vengono uniti senza ricodifica
    const dir = process.env.OUT || (PAGE === 'index' ? 'build' : `build/${PAGE}`);
    const CHUNK = 150, cdir = `${dir}/chunks`;
    fs.mkdirSync(cdir, { recursive: true });
    const probe = await openPage(browser);
    const duration = await probe.evaluate(() => window.DURATION);
    fs.writeFileSync(SFX_FILE, JSON.stringify(await probe.evaluate(() => window.SFX)));
    await probe.close();
    const total = Math.round(duration * FPS), nChunks = Math.ceil(total / CHUNK);
    const name = k => `${cdir}/c${String(k).padStart(4, '0')}.mp4`;
    const todo = [...Array(nChunks).keys()].filter(k => !fs.existsSync(name(k)));
    let done = (nChunks - todo.length) * CHUNK; const t0 = Date.now();
    console.log(`${nChunks - todo.length}/${nChunks} blocchi già pronti`);
    await Promise.all([...Array(WORKERS)].map(async () => {
      const page = await openPage(browser);
      for (let k; (k = todo.shift()) !== undefined;) {
        const tmp = name(k).replace('.mp4', '.part.mp4');
        const enc = spawn(FFMPEG, ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
          '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(FPS), tmp],
          { stdio: ['pipe', 'inherit', 'inherit'] });
        const closed = new Promise(r => enc.on('close', r));
        for (let f = k * CHUNK; f < Math.min(total, (k + 1) * CHUNK); f++) {
          await page.evaluate(t => seek(t), f / FPS);
          const img = await page.screenshot({ type: 'jpeg', quality: 95 });
          if (!enc.stdin.write(img)) await new Promise(r => enc.stdin.once('drain', r));
          if (++done % 150 === 0) console.log(`${done}/${total} fotogrammi, ${((Date.now() - t0) / 1000).toFixed(0)} s`);
        }
        enc.stdin.end();
        if (await closed !== 0) throw new Error('ffmpeg non ha chiuso il blocco ' + k);
        fs.renameSync(tmp, name(k));
      }
    }));
    fs.writeFileSync(`${dir}/segments.txt`, [...Array(nChunks).keys()].map(k => `file 'chunks/c${String(k).padStart(4, '0')}.mp4'`).join('\n'));
    execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', `${dir}/segments.txt`, '-c', 'copy', `${dir}/video.mp4`]);
    console.log('fatto:', total, 'fotogrammi ->', `${dir}/video.mp4`);
  }
  await browser.close();
})();
