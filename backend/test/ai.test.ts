// Rotta /api/ai (chat di AILA Assistant) su D1 finto e binding Workers AI simulato.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import type { Env } from '../src/types';

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
const AI_RUN = vi.fn();
let env: Env;

beforeEach(() => {
  AI_RUN.mockReset();
  const { d1 } = createD1();
  env = { DB: d1, JWT_SECRET: 'test-secret', AI: { run: AI_RUN } } as unknown as Env;
});

async function call(method: string, path: string, body?: unknown, token?: string) {
  const res = await worker.fetch(
    new Request(`https://api.test${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
    env,
    ctx
  );
  const json = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  return { status: res.status, json };
}

async function register(username: string): Promise<string> {
  const res = await worker.fetch(
    new Request('https://api.test/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        firstName: 'Nome',
        lastName: username,
        username,
        password: 'password123',
        heightCm: 170,
        classLabel: '3 B',
      }),
    }),
    env,
    ctx
  );
  const json = (await res.json()) as { token?: string };
  expect(res.status).toBe(201);
  return json.token as string;
}

describe('rotta /api/ai', () => {
  it('chiede l\'autenticazione', async () => {
    const res = await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
    });
    expect(res.status).toBe(401);
  });

  it('senza binding AI risponde 503 con ragione chiara', async () => {
    const token = await register('studente_ai');
    delete (env as { AI?: unknown }).AI;
    const res = await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
    }, token);
    expect(res.status).toBe(503);
    expect(res.json.reason).toBe('SERVICE_NOT_CONFIGURED');

    // Anche /status lo dice, senza generare nulla.
    const status = await call('GET', '/api/ai/status', undefined, token);
    expect(status.json.configured).toBe(false);
  });

  it('Instrada su Gemma 4 26B A4B con i parametri di chat', async () => {
    const token = await register('studente_ai2');
    AI_RUN.mockResolvedValue({ response: '  La lezione è alle due. ' });
    const res = await call('POST', '/api/ai/chat', {
      messages: [
        { role: 'system', content: 'Sei AILA Assistant.' },
        { role: 'user', content: 'A che ora è la lezione?' },
      ],
    }, token);
    expect(res.status).toBe(200);
    expect(res.json.text).toBe('La lezione è alle due.');

    expect(AI_RUN).toHaveBeenCalledTimes(1);
    const [model, input] = AI_RUN.mock.calls[0] as [string, Record<string, unknown>];
    expect(model).toBe('@cf/google/gemma-4-26b-a4b-it');
    expect(input.temperature).toBe(0.2);
    expect(input.max_completion_tokens).toBe(2048);
    // Il ragionamento va spento di default in chat (enable_thinking false), esattamente
    // come il thinkingBudget 0 che la chat usa oggi col client Gemini.
    expect((input.chat_template_kwargs as Record<string, unknown>).enable_thinking).toBe(false);
    // Senza "json: true" niente response_format: in chat la risposta e' testo libero.
    expect(input.response_format).toBeUndefined();
  });

  it('con json: true chiede un oggetto JSON (stesure evento)', async () => {
    const token = await register('studente_ai3');
    AI_RUN.mockResolvedValue({ response: '{"title":"Verifica","category":"VERIFICA"}' });
    const res = await call('POST', '/api/ai/chat', {
      messages: [
        { role: 'system', content: 'Rispondi solo con JSON.' },
        { role: 'user', content: 'il docente ha annunciato una verifica' },
      ],
      json: true,
      maxTokens: 400,
    }, token);
    expect(res.status).toBe(200);
    const [, input] = AI_RUN.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(input.response_format).toEqual({ type: 'json_object' });
    expect(input.max_completion_tokens).toBe(400);
  });

  it('il tetto di token richiesti si aggancia ai limiti noti', async () => {
    const token = await register('studente_ai4');
    AI_RUN.mockResolvedValue({ response: 'ok' });
    await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
      maxTokens: 100000,
    }, token);
    let [, input] = AI_RUN.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(input.max_completion_tokens).toBe(2048);
    await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
      maxTokens: 3,
    }, token);
    [, input] = AI_RUN.mock.calls[1] as unknown as [string, Record<string, unknown>];
    expect(input.max_completion_tokens).toBe(64);
  });

  it('fine quota dei neuron di Cloudflare (4006) diventa 429 con ragione QUOTA_EXHAUSTED', async () => {
    const token = await register('studente_ai5');
    const quotaError = Object.assign(
      new Error('AiError: 4006: you have used up your daily free allocation of 10,000 neurons, ' +
        'please upgrade to Cloudflare\'s Workers Paid plan if you would like to continue usage.'),
      { code: 4006 }
    );
    AI_RUN.mockRejectedValue(quotaError);
    const res = await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
    }, token);
    expect(res.status).toBe(429);
    expect(res.json.reason).toBe('QUOTA_EXHAUSTED');
    expect(String(res.json.error)).toContain('quota giornaliera');
  });

  it('un errore non di quota resta 502 e non si fa passare per fine quota', async () => {
    const token = await register('studente_ai6');
    AI_RUN.mockRejectedValue(new Error('AiError: 3032: model overbooked per un caso di test'));
    const res = await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
    }, token);
    expect(res.status).toBe(502);
    expect(res.json.reason).toBe('AI_ERROR');
  });

  it('rifiuta messaggi senza testo, ruoli estranei e prompt troppo lunghi', async () => {
    const token = await register('studente_ai7');
    expect((await call('POST', '/api/ai/chat', {}, token)).status).toBe(400);
    expect((await call('POST', '/api/ai/chat', { messages: [] }, token)).status).toBe(400);
    expect((await call('POST', '/api/ai/chat', {
      messages: [{ role: 'tool', content: 'x' }],
    }, token)).status).toBe(400);
    expect((await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: '' }],
    }, token)).status).toBe(400);
    expect((await call('POST', '/api/ai/chat', {
      messages: Array.from({ length: 33 }, (_v, i) => ({ role: 'user', content: `m${i}` })),
    }, token)).status).toBe(400);
    expect((await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'x'.repeat(60_001) }],
    }, token)).status).toBe(413);
    // La risposta vuota fa 502, non 429: non e' fine quota, e' solo una risposta svuotata
    // dal modello.
    AI_RUN.mockResolvedValue({ response: '' });
    expect((await call('POST', '/api/ai/chat', {
      messages: [{ role: 'user', content: 'ciao' }],
    }, token)).status).toBe(502);
  });
});
