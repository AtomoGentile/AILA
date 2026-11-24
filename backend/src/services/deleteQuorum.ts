import type { Env } from '../types';
import { notifyUsers } from './fcm';

export type DeleteScope = 'BOARD_PROPOSAL' | 'BOARD_COMMENT' | 'FORUM_THREAD' | 'FORUM_POST';
export type SignerRole = 'REPRESENTATIVE' | 'SECURITY_GUARD';

export const DELETE_QUORUM_REPS = 2;
export const DELETE_QUORUM_GUARDS = 1;

export async function getSignerRole(env: Env, userId: string, classId: string): Promise<SignerRole | null> {
  const row = await env.DB.prepare(
    `SELECT u.role, c.security_guard_id
     FROM users u
     JOIN classes c ON c.id = ?
     WHERE u.id = ?`
  ).bind(classId, userId).first<{ role: string; security_guard_id: string | null }>();
  if (!row) return null;
  if (row.role === 'REPRESENTATIVE') return 'REPRESENTATIVE';
  if (row.security_guard_id && row.security_guard_id === userId) return 'SECURITY_GUARD';
  return null;
}

export async function getClassSigners(env: Env, classId: string): Promise<string[]> {
  const reps = await env.DB.prepare(
    "SELECT id FROM users WHERE class_id = ? AND role = 'REPRESENTATIVE'"
  ).bind(classId).all<{ id: string }>();
  const cls = await env.DB.prepare(
    'SELECT security_guard_id FROM classes WHERE id = ?'
  ).bind(classId).first<{ security_guard_id: string | null }>();
  const ids = new Set(reps.results.map((r) => r.id));
  if (cls?.security_guard_id) ids.add(cls.security_guard_id);
  return [...ids];
}

export interface DeleteRequestJson {
  id: string;
  scope: DeleteScope;
  targetId: string;
  targetPreview: string;
  reason: string;
  status: string;
  requestedByName: string;
  createdAt: string;
  repApprovals: number;
  repRequired: number;
  guardApprovals: number;
  guardRequired: number;
  myApproved: boolean;
}

export async function listDeleteRequests(
  env: Env,
  classId: string,
  scopes: DeleteScope[],
  viewerId: string
): Promise<DeleteRequestJson[]> {
  const placeholders = scopes.map(() => '?').join(',');
  const rows = await env.DB.prepare(
    `SELECT r.id, r.scope, r.target_id, r.target_preview, r.reason, r.status, r.created_at,
            u.first_name || ' ' || u.last_name AS requester_name
     FROM content_delete_requests r
     JOIN users u ON u.id = r.requested_by
     WHERE r.class_id = ? AND r.scope IN (${placeholders}) AND r.status = 'PENDING'
     ORDER BY r.created_at DESC
     LIMIT 40`
  ).bind(classId, ...scopes).all<{
    id: string; scope: DeleteScope; target_id: string; target_preview: string;
    reason: string; status: string; created_at: string; requester_name: string;
  }>();

  if (!rows.results.length) return [];

  const reqIds = rows.results.map((r) => r.id);
  const appRows = await env.DB.prepare(
    `SELECT request_id, user_id, role FROM content_delete_approvals
     WHERE request_id IN (${reqIds.map(() => '?').join(',')})`
  ).bind(...reqIds).all<{ request_id: string; user_id: string; role: SignerRole }>();

  const byReq = new Map<string, { reps: number; guards: number; mine: boolean }>();
  for (const a of appRows.results) {
    const entry = byReq.get(a.request_id) ?? { reps: 0, guards: 0, mine: false };
    if (a.role === 'REPRESENTATIVE') entry.reps++;
    else if (a.role === 'SECURITY_GUARD') entry.guards++;
    if (a.user_id === viewerId) entry.mine = true;
    byReq.set(a.request_id, entry);
  }

  return rows.results.map((r) => {
    const counts = byReq.get(r.id) ?? { reps: 0, guards: 0, mine: false };
    return {
      id: r.id,
      scope: r.scope,
      targetId: r.target_id,
      targetPreview: r.target_preview,
      reason: r.reason,
      status: r.status,
      requestedByName: r.requester_name,
      createdAt: r.created_at,
      repApprovals: counts.reps,
      repRequired: DELETE_QUORUM_REPS,
      guardApprovals: counts.guards,
      guardRequired: DELETE_QUORUM_GUARDS,
      myApproved: counts.mine,
    };
  });
}

export async function createDeleteRequest(
  env: Env,
  params: {
    classId: string;
    scope: DeleteScope;
    targetId: string;
    targetPreview: string;
    reason: string;
    requesterId: string;
    requesterRole: SignerRole;
  }
): Promise<{ id?: string; error?: string; status?: number }> {
  const cls = await env.DB.prepare(
    'SELECT security_guard_id FROM classes WHERE id = ?'
  ).bind(params.classId).first<{ security_guard_id: string | null }>();
  if (!cls?.security_guard_id) {
    return {
      error: 'Prima va nominata la Guardia di Sicurezza nella scheda Classe → Gestione.',
      status: 409,
    };
  }

  const existing = await env.DB.prepare(
    `SELECT id FROM content_delete_requests
     WHERE class_id = ? AND scope = ? AND target_id = ? AND status = 'PENDING'`
  ).bind(params.classId, params.scope, params.targetId).first<{ id: string }>();
  if (existing) {
    return { error: 'Esiste già una richiesta di eliminazione in attesa per questo contenuto.', status: 409 };
  }

  const id = crypto.randomUUID();
  const preview = params.targetPreview.trim().slice(0, 140);
  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO content_delete_requests (id, class_id, scope, target_id, target_preview, requested_by, reason)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(id, params.classId, params.scope, params.targetId, preview, params.requesterId, params.reason),
    env.DB.prepare(
      `INSERT INTO content_delete_approvals (request_id, user_id, role) VALUES (?, ?, ?)`
    ).bind(id, params.requesterId, params.requesterRole),
  ]);

  const signers = (await getClassSigners(env, params.classId)).filter((u) => u !== params.requesterId);
  if (signers.length) {
    const isForum = params.scope.startsWith('FORUM_');
    await notifyUsers(
      env,
      signers,
      isForum ? 'Richiesta eliminazione (Pere)' : 'Richiesta eliminazione (Bacheca)',
      `Serve la tua firma: "${preview || 'contenuto'}"`,
      {
        action: isForum ? 'forum_moderation' : 'board_moderation',
        target_id: params.targetId,
      }
    ).catch(() => {});
  }

  return { id };
}

async function executeTargetDeletion(env: Env, classId: string, scope: DeleteScope, targetId: string): Promise<void> {
  if (scope === 'BOARD_PROPOSAL') {
    await env.DB.prepare('DELETE FROM proposals WHERE id = ? AND class_id = ?').bind(targetId, classId).run();
  } else if (scope === 'BOARD_COMMENT') {
    await env.DB.prepare(
      `DELETE FROM proposal_comments WHERE id = ? AND proposal_id IN (SELECT id FROM proposals WHERE class_id = ?)`
    ).bind(targetId, classId).run();
  } else if (scope === 'FORUM_THREAD') {
    await env.DB.prepare('DELETE FROM forum_threads WHERE id = ? AND class_id = ?').bind(targetId, classId).run();
  } else if (scope === 'FORUM_POST') {
    const post = await env.DB.prepare(
      'SELECT id, thread_id FROM forum_posts WHERE id = ? AND class_id = ?'
    ).bind(targetId, classId).first<{ id: string; thread_id: string }>();
    if (post) {
      if (post.id === post.thread_id) {
        await env.DB.prepare('DELETE FROM forum_threads WHERE id = ? AND class_id = ?').bind(post.thread_id, classId).run();
      } else {
        await env.DB.prepare('DELETE FROM forum_posts WHERE id = ? AND class_id = ?').bind(targetId, classId).run();
      }
    }
  }
}

export async function approveDeleteRequest(
  env: Env,
  requestId: string,
  classId: string,
  userId: string,
  role: SignerRole
): Promise<{ executed?: boolean; error?: string; status?: number }> {
  const req = await env.DB.prepare(
    `SELECT id, scope, target_id, status FROM content_delete_requests WHERE id = ? AND class_id = ?`
  ).bind(requestId, classId).first<{ id: string; scope: DeleteScope; target_id: string; status: string }>();
  if (!req) return { error: 'Richiesta non trovata', status: 404 };
  if (req.status !== 'PENDING') return { error: 'Richiesta già chiusa', status: 409 };

  await env.DB.prepare(
    `INSERT OR IGNORE INTO content_delete_approvals (request_id, user_id, role) VALUES (?, ?, ?)`
  ).bind(requestId, userId, role).run();

  const approvals = await env.DB.prepare(
    'SELECT role FROM content_delete_approvals WHERE request_id = ?'
  ).bind(requestId).all<{ role: SignerRole }>();

  const reps = approvals.results.filter((a) => a.role === 'REPRESENTATIVE').length;
  const guards = approvals.results.filter((a) => a.role === 'SECURITY_GUARD').length;

  if (reps >= DELETE_QUORUM_REPS && guards >= DELETE_QUORUM_GUARDS) {
    const updated = await env.DB.prepare(
      `UPDATE content_delete_requests SET status = 'APPROVED', resolved_at = CURRENT_TIMESTAMP
       WHERE id = ? AND status = 'PENDING'`
    ).bind(requestId).run();
    if ((updated.meta.changes ?? 0) > 0) {
      await executeTargetDeletion(env, classId, req.scope, req.target_id);
    }
    return { executed: true };
  }

  return { executed: false };
}

export async function rejectDeleteRequest(
  env: Env,
  requestId: string,
  classId: string
): Promise<{ ok?: boolean; error?: string; status?: number }> {
  const res = await env.DB.prepare(
    `UPDATE content_delete_requests SET status = 'REJECTED', resolved_at = CURRENT_TIMESTAMP
     WHERE id = ? AND class_id = ? AND status = 'PENDING'`
  ).bind(requestId, classId).run();
  if ((res.meta.changes ?? 0) === 0) return { error: 'Richiesta non trovata o già chiusa', status: 404 };
  return { ok: true };
}
