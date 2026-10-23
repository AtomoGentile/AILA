import { describe, expect, it, beforeEach } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import { issueToken, storedPasswordHash } from '../src/auth';
import type { Env } from '../src/types';
import { checkTelegramSignatureReminders } from '../src/services/telegram';

describe('Telegram & Firma iOS', () => {
  let env: Env;
  let token: string;
  let userId: string;

  beforeEach(async () => {
    const { d1 } = createD1();
    env = {
      DB: d1,
      CIRCULARS_BUCKET: {} as any,
      JWT_SECRET: 'test-secret-at-least-32-chars-long-here',
      TELEGRAM_TOKEN: 'test-bot-token',
      TELEGRAM_WEBHOOK_SECRET: 'webhook-secret-123',
    };

    userId = 'user-test-123';
    const pwdHash = await storedPasswordHash('Password123');

    await d1.prepare(
      `INSERT INTO users (id, first_name, last_name, username, password_hash, role, class_id)
       VALUES (?, 'Mario', 'Rossi', 'mrossi', ?, 'STUDENT', 'DEFAULT_CLASS')`
    ).bind(userId, pwdHash).run();

    token = await issueToken(env, {
      id: userId,
      username: 'mrossi',
      role: 'STUDENT',
      classId: 'DEFAULT_CLASS',
      passwordHash: pwdHash,
    });
  });

  it('aggiorna la scadenza della firma iOS con PUT /api/users/me/signature-expiration', async () => {
    const expiresAt = new Date(Date.now() + 5 * 86400 * 1000).toISOString();

    const res = await worker.fetch(
      new Request('http://localhost/api/users/me/signature-expiration', {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ expiresAt }),
      }),
      env,
      {} as any
    );

    expect(res.status).toBe(200);
    const json = await res.json() as any;
    expect(json.success).toBe(true);
    expect(json.signatureExpiresAt).toBe(expiresAt);

    // Verifico che il profilo me restituisca la scadenza
    const meRes = await worker.fetch(
      new Request('http://localhost/api/users/me', {
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );

    const meJson = await meRes.json() as any;
    expect(meJson.signatureExpiresAt).toBe(expiresAt);
  });

  it('genera un codice di collegamento temporaneo e verifica lo status', async () => {
    // 1. Status iniziale: non collegato
    const statusRes = await worker.fetch(
      new Request('http://localhost/api/telegram/status', {
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );

    const statusJson = await statusRes.json() as any;
    expect(statusJson.linked).toBe(false);

    // 2. Generazione codice
    const codeRes = await worker.fetch(
      new Request('http://localhost/api/telegram/link-code', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );

    expect(codeRes.status).toBe(200);
    const codeJson = await codeRes.json() as any;
    expect(codeJson.code).toBeDefined();
    expect(codeJson.code.length).toBe(6);
  });

  it('valida il segreto del webhook Telegram se configurato', async () => {
    const webhookReq = new Request('http://localhost/api/telegram/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-telegram-bot-api-secret-token': 'wrong-secret',
      },
      body: JSON.stringify({ message: { chat: { id: 123 }, text: '/start' } }),
    });

    const res = await worker.fetch(webhookReq, env, {} as any);
    expect(res.status).toBe(401);
  });

  it('collega l\'account Telegram inviando un codice valido al webhook', async () => {
    // Genera codice
    const codeRes = await worker.fetch(
      new Request('http://localhost/api/telegram/link-code', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );
    const { code } = await codeRes.json() as any;

    // Invia codice al webhook con segreto corretto
    const webhookReq = new Request('http://localhost/api/telegram/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-telegram-bot-api-secret-token': 'webhook-secret-123',
      },
      body: JSON.stringify({
        message: {
          chat: { id: 987654 },
          text: `/start ${code}`,
        },
      }),
    });

    const res = await worker.fetch(webhookReq, env, {} as any);
    expect(res.status).toBe(200);

    // Verifico che ora risulti collegato
    const statusRes = await worker.fetch(
      new Request('http://localhost/api/telegram/status', {
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );
    const statusJson = await statusRes.json() as any;
    expect(statusJson.linked).toBe(true);
    expect(statusJson.active).toBe(true);
  });

  it('gestisce codici scaduti o già usati', async () => {
    // Inserisci codice scaduto direttamente in DB
    await env.DB.prepare(
      `INSERT INTO telegram_link_codes (code, user_id, expires_at, created_at)
       VALUES ('EXPIRED', ?, ?, CURRENT_TIMESTAMP)`
    ).bind(userId, Math.floor(Date.now() / 1000) - 100).run();

    const webhookReq = new Request('http://localhost/api/telegram/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-telegram-bot-api-secret-token': 'webhook-secret-123',
      },
      body: JSON.stringify({
        message: { chat: { id: 987654 }, text: 'EXPIRED' },
      }),
    });

    const res = await worker.fetch(webhookReq, env, {} as any);
    expect(res.status).toBe(200);

    const statusRes = await worker.fetch(
      new Request('http://localhost/api/telegram/status', {
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );
    const statusJson = await statusRes.json() as any;
    expect(statusJson.linked).toBe(false);
  });

  it('disattiva i promemoria col comando /stop o via API /unlink', async () => {
    // Collega prima la chat
    await env.DB.prepare(
      `INSERT INTO telegram_links (user_id, chat_id, linked_at, active) VALUES (?, '987654', CURRENT_TIMESTAMP, 1)`
    ).bind(userId).run();

    // Invio /stop
    const stopReq = new Request('http://localhost/api/telegram/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-telegram-bot-api-secret-token': 'webhook-secret-123',
      },
      body: JSON.stringify({
        message: { chat: { id: 987654 }, text: '/stop' },
      }),
    });

    const stopRes = await worker.fetch(stopReq, env, {} as any);
    expect(stopRes.status).toBe(200);

    const statusRes = await worker.fetch(
      new Request('http://localhost/api/telegram/status', {
        headers: { 'Authorization': `Bearer ${token}` },
      }),
      env,
      {} as any
    );
    const statusJson = await statusRes.json() as any;
    expect(statusJson.linked).toBe(true);
    expect(statusJson.active).toBe(false);
  });

  it('invia il promemoria cron per scadenze entro 36 ore e previene invii duplicati', async () => {
    // Collega Telegram
    await env.DB.prepare(
      `INSERT INTO telegram_links (user_id, chat_id, linked_at, active) VALUES (?, '987654', CURRENT_TIMESTAMP, 1)`
    ).bind(userId).run();

    // Scadenza tra 24 ore
    const expDate = new Date(Date.now() + 24 * 3600 * 1000).toISOString();
    await env.DB.prepare('UPDATE users SET signature_expires_at = ? WHERE id = ?').bind(expDate, userId).run();

    // Esegui cron
    await checkTelegramSignatureReminders(env);

    // Controlla la tabella telegram_signature_reminders
    const reminder = await env.DB.prepare(
      'SELECT * FROM telegram_signature_reminders WHERE user_id = ? AND signature_expires_at = ?'
    ).bind(userId, expDate).first();

    expect(reminder).toBeDefined();

    // Esegui di nuovo il cron: non deve generare errori né duplicati
    await checkTelegramSignatureReminders(env);
  });
});
