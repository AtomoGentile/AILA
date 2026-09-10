// =============================================================================
// CIRCOLARE+ — Cloudflare Worker Entry Point
// Hono v4 Router + Cron Trigger Handler
// =============================================================================

import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';

import type { Env } from './types';
import { syncSpaggiariCirculars } from './services/spaggiari';

// Routes
import authRoutes from './routes/auth';
import usersRoutes from './routes/users';
import circularsRoutes from './routes/circulars';
import calendarRoutes from './routes/calendar';
import proposalsRoutes from './routes/proposals';
import preferencesRoutes from './routes/preferences';
import ratingsRoutes from './routes/ratings';
import seatmapRoutes from './routes/seatmap';
import pollsRoutes from './routes/polls';
import fcmRoutes from './routes/fcm';

// ---------------------------------------------------------------------------
// App Setup
// ---------------------------------------------------------------------------
const app = new Hono<{ Bindings: Env }>();

// Global middleware
app.use('*', logger());
app.use(
  '*',
  cors({
    origin: '*', // Restrict in production to your app domain / bundle ID
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization'],
    maxAge: 86400,
  })
);

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
app.route('/api/calendar', calendarRoutes);
app.route('/api/proposals', proposalsRoutes);

// Preferences: mount /api/preferences for both vote and config sub-paths
// The preferences router handles: /config (GET/POST) and /vote, /my, /summary, /matrix
app.route('/api/preferences', preferencesRoutes);

app.route('/api/ratings', ratingsRoutes);
app.route('/api/seat-map', seatmapRoutes);
app.route('/api/polls', pollsRoutes);
app.route('/api/fcm', fcmRoutes);

// ---------------------------------------------------------------------------
// 404 fallback
// ---------------------------------------------------------------------------
app.notFound((c) => c.json({ error: 'Endpoint non trovato' }, 404));

// ---------------------------------------------------------------------------
// Error handler
// ---------------------------------------------------------------------------
app.onError((err, c) => {
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

  // Cron handler — Spaggiari sync ogni 15 minuti
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    console.log(`[Cron] Avvio sync Spaggiari: ${new Date().toISOString()}`);
    ctx.waitUntil(syncSpaggiariCirculars(env));
  },
};
