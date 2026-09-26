// CORS: solo la PWA (WEB_ORIGINS) e localhost; l'app nativa (senza Origin) non cambia.
import { describe, expect, it } from 'vitest';
import worker from '../src/index';

const env = { WEB_ORIGINS: 'https://aila-scuola.pages.dev' } as never;
const ctx = { waitUntil() {}, passThroughOnException() {} } as never;

async function preflight(origin?: string) {
  const headers: Record<string, string> = { 'Access-Control-Request-Method': 'GET' };
  if (origin) headers.Origin = origin;
  return worker.fetch(new Request('https://api.test/health', { method: 'OPTIONS', headers }), env, ctx);
}

describe('CORS', () => {
  it('ammette la PWA', async () => {
    const res = await preflight('https://aila-scuola.pages.dev');
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('https://aila-scuola.pages.dev');
  });

  it('ammette localhost in sviluppo', async () => {
    const res = await preflight('http://localhost:5173');
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://localhost:5173');
  });

  it('rifiuta gli altri siti', async () => {
    for (const origin of ['https://evil.example', 'https://aila-scuola.pages.dev.evil.example', 'http://localhost.evil.example']) {
      const res = await preflight(origin);
      expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull();
    }
  });

  it("le richieste senza Origin (app nativa) rispondono come prima", async () => {
    const res = await worker.fetch(new Request('https://api.test/health'), env, ctx);
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ status: 'ok' });
  });
});
