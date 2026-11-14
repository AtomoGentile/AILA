// =============================================================================
// CIRCOLARE+ — Cloudflare Worker Entry Point
// Hono v4 Router + Cron Trigger Handler
// =============================================================================

import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import { HTTPException } from 'hono/http-exception';

import type { Env } from './types';
import { summarizePendingCirculars } from './services/summarizer';
import { notifySummarizedCirculars, syncSpaggiariCirculars } from './services/spaggiari';
import { checkTelegramSignatureReminders } from './services/telegram';
import { pruneAttempts } from './rateLimit';

// Routes
import authRoutes from './routes/auth';
import usersRoutes from './routes/users';
import circularsRoutes from './routes/circulars';
import gitaRoutes from './routes/gita';
import calendarRoutes from './routes/calendar';
import proposalsRoutes from './routes/proposals';
import preferencesRoutes from './routes/preferences';
import ratingsRoutes from './routes/ratings';
import seatmapRoutes from './routes/seatmap';
import pollsRoutes from './routes/polls';
import rankingPollsRoutes from './routes/rankingPolls';
import fcmRoutes from './routes/fcm';
import webPushRoutes from './routes/webpush';
import telegramRoutes from './routes/telegram';
import adminRoutes from './routes/admin';
import aiRoutes from './routes/ai';
import forumRoutes from './routes/forum';
import forumCalendarRoutes from './routes/forumCalendar';
import contentRoutes from './routes/content';
import acaOperations from './routes/acaOperations';
import acaRoutes from './routes/aca';
import { cleanupContent } from './services/content';
import { forumResolutionNudges } from './services/forum';
import { adminPage } from './routes/adminPage';

// ---------------------------------------------------------------------------
// App Setup
// ---------------------------------------------------------------------------
const app = new Hono<{ Bindings: Env }>();

// Global middleware
app.use('*', logger());
// CORS solo per le PWA e per lo sviluppo in locale. L'app nativa non manda l'header Origin,
// quindi non ne è toccata. La beta vive sul branch preview dedicato di Cloudflare Pages.
const LOCALHOST_ORIGIN = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
const BETA_PWA_ORIGIN = 'https://beta.aila-scuola.pages.dev';
app.use('*', async (c, next) => {
  const allowed = (c.env.WEB_ORIGINS ?? '').split(',').map((o) => o.trim()).filter(Boolean);
  return cors({
    origin: (origin) => (allowed.includes(origin) || origin === BETA_PWA_ORIGIN || LOCALHOST_ORIGIN.test(origin) ? origin : null),
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization'],
    maxAge: 86400,
  })(c, next);
});

// ---------------------------------------------------------------------------
// Health check
// ---------------------------------------------------------------------------
app.get('/', (c) => c.json({ status: 'online', service: 'Circolare+ API', version: '3.0.0' }));
app.get('/health', (c) => c.json({ status: 'ok', timestamp: new Date().toISOString() }));

// ---------------------------------------------------------------------------
// API Routes
// ---------------------------------------------------------------------------
app.route('/api/auth', authRoutes);
app.route('/api/users', usersRoutes);
app.route('/api/circulars', circularsRoutes);
app.route('/api/gita', gitaRoutes);
app.route('/api/calendar', calendarRoutes);
app.route('/api/proposals', proposalsRoutes);

// Preferences: mount /api/preferences for both vote and config sub-paths
// The preferences router handles: /config (GET/POST) and /vote, /my, /summary, /matrix
app.route('/api/preferences', preferencesRoutes);

app.route('/api/ratings', ratingsRoutes);
app.route('/api/seat-map', seatmapRoutes);
app.route('/api/polls', pollsRoutes);
app.route('/api/ranking-polls', rankingPollsRoutes);
app.route('/api/fcm', fcmRoutes);
app.route('/api/webpush', webPushRoutes);
app.route('/api/telegram', telegramRoutes);
app.route('/api/admin', adminRoutes);
// Chat di AILA Assistant inclusa per tutti (v1.5.0): proxy verso Workers AI con Gemma.
app.route('/api/ai', aiRoutes);
app.route('/api/forum', forumCalendarRoutes);
app.route('/api/forum', forumRoutes);
app.route('/api/content', contentRoutes);
app.route('/api/aca/operations', acaOperations);
app.route('/api/aca', acaRoutes);
// Pagina per emettere i codici Rappresentante dal browser (routes/adminPage.ts).
app.get('/admin', adminPage);

// ---------------------------------------------------------------------------
// 404 fallback
// ---------------------------------------------------------------------------
app.notFound((c) => c.json({ error: 'Endpoint non trovato' }, 404));

// ---------------------------------------------------------------------------
// Error handler
// ---------------------------------------------------------------------------
app.onError((err, c) => {
  if (err instanceof HTTPException) return c.json({ error: err.message }, err.status);
  // Corpo della richiesta che non e' JSON: e' un errore di chi chiama, non del server.
  if (err instanceof SyntaxError) {
    return c.json({ error: 'Richiesta non valida' }, 400);
  }
  console.error('[Worker Error]', err);
  return c.json({ error: 'Errore interno del server' }, 500);
});

// ---------------------------------------------------------------------------
// Worker Export
// ---------------------------------------------------------------------------
export default {
  // HTTP handler
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    return app.fetch(request, env, ctx);
  },

  // Cron handler — Spaggiari sync ogni 15 minuti + Promemoria Telegram
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(Promise.allSettled([cleanupContent(env), forumResolutionNudges(env)]).then(() => undefined));
    console.log(`[Cron] Avvio sync Spaggiari: ${new Date().toISOString()}`);
    // I riassunti e i promemoria Telegram partono dopo la sync. Un errore Telegram non blocca
    // le altre attività del cron né la pulizia dei rate limit.
    ctx.waitUntil(
      syncSpaggiariCirculars(env)
        .then(() => summarizePendingCirculars(env))
        .then(() => notifySummarizedCirculars(env))
        .then(() => checkTelegramSignatureReminders(env).catch((err) => console.error('[Cron Telegram Error]', err)))
        .finally(() => pruneAttempts(env))
    );
  },
};
