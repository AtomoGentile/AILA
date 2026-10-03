# Web (HTML/CSS, React, Vue, Svelte, Preact, Tailwind)

## Vederla
```js
// screenshot.mjs — node screenshot.mjs http://localhost:5173/percorso
import { chromium } from 'playwright';
const url = process.argv[2];
const browser = await chromium.launch();
for (const [name, viewport] of [['mobile', { width: 390, height: 844 }], ['desktop', { width: 1280, height: 800 }]]) {
  for (const colorScheme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport, colorScheme, reducedMotion: 'reduce' });
    await page.goto(url, { waitUntil: 'networkidle' });
    await page.screenshot({ path: `shot-${name}-${colorScheme}.png`, fullPage: true });
    await page.close();
  }
}
await browser.close();
```
Salva gli screenshot in una cartella temporanea, non nel repo. Se servono login o dati, usa i
dati di sviluppo del progetto o intercetta le API con `page.route`.

Controlli automatici utili, se disponibili: `npx @axe-core/cli <url>` o `@axe-core/playwright`
per contrasto e nomi accessibili; Lighthouse per accessibilità e prestazioni percepite.

## Token e stili
- Variabili CSS in `:root` con la variante scura in `@media (prefers-color-scheme: dark)` e/o
  `[data-theme="dark"]`; con Tailwind, valori nel config/`@theme` invece di valori arbitrari
  `[#3b82f6]` sparsi.
- Cerca valori scritti a mano: `rg -n '#[0-9a-fA-F]{3,8}\b|rgba?\(|\b\d+px\b' src --glob '!*.svg'`
  e valuta quali dovrebbero essere token.
- Se c'è una CSP con `style-src 'self'`, niente stili inline: classi nel CSS.

## Dettagli che fanno la differenza
- `:focus-visible` con un anello ben visibile; mai `outline: none` senza sostituto.
- `@media (prefers-reduced-motion: reduce)` che spegne transizioni e animazioni non essenziali.
- `min-height: 100dvh` e `env(safe-area-inset-*)` su mobile; `-webkit-tap-highlight-color`.
- `font-variant-numeric: tabular-nums` su tabelle, contatori, orari.
- `text-wrap: balance` sui titoli, `text-wrap: pretty` sui paragrafi; `hyphens: auto` con `lang`.
- Immagini con `width`/`height` o `aspect-ratio` (niente salti di layout), `alt` sensato.
- Pulsanti veri (`<button>`) per le azioni, link (`<a href>`) per la navigazione; niente `div`
  cliccabili. Un solo `<h1>` per pagina, gerarchia dei titoli senza salti.
- Moduli: `<label for>`, `autocomplete`, `inputmode`, `type` corretti; errori collegati con
  `aria-describedby`; dialoghi con focus intrappolato e chiusura con Esc.
- Hover solo come aggiunta: tutto deve funzionare al tocco.
