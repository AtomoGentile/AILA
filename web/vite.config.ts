import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';
import { VitePWA } from 'vite-plugin-pwa';
import { fileURLToPath } from 'node:url';

// PWA "AILA": service worker scritto a mano (src/sw.ts) con Workbox, manifest generato qui.
export default defineConfig({
  resolve: {
    alias: {
      '@worker/contracts': fileURLToPath(new URL('../backend/src/contracts.ts', import.meta.url)),
    },
  },
  build: { target: 'es2022', sourcemap: false },
  plugins: [
    preact(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      // Registrazione fatta da noi in main.tsx: niente script inline (CSP).
      injectRegister: false,
      registerType: 'prompt',
      injectManifest: {
        globPatterns: ['**/*.{js,mjs,css,html,svg,png,woff2,webmanifest}'],
        // Il worker di pdf.js è grande: sta comunque nella shell offline.
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
      manifest: {
        id: '/',
        name: 'AILA',
        short_name: 'AILA',
        description: 'Circolari, bacheca, calendario e mappa posti della classe.',
        lang: 'it',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0F1535',
        theme_color: '#16204A',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  test: {
    include: ['test/**/*.test.ts'],
  },
});
