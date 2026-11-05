import type { Env } from '../types';
import { HTTPException } from 'hono/http-exception';
import { loadForumThread } from './forum';

export interface ForumCalendarProposal {
 id:string;thread_id:string;class_id:string;source_ids_json:string;plan_json:string;
 status:'PENDING'|'ACCEPTED';provider:string;created_at:string;expires_at:string;
 accepted_at:string|null;operation_id:string|null;
}
// Identical ordering in the read path and the transactional execution guard.
export const FORUM_CALENDAR_SOURCE_SQL = `SELECT json_group_array(id) FROM
 (SELECT id FROM forum_posts WHERE thread_id=? AND hidden=0 AND is_ai=0 ORDER BY rowid DESC LIMIT 50)`;
export async function forumCalendarSource(env:Env,classId:string,threadId:string) {
 const thread=await loadForumThread(env,classId,threadId);
 const posts=await env.DB.prepare(`SELECT id,body,created_at FROM forum_posts WHERE thread_id=? AND hidden=0 AND is_ai=0 ORDER BY rowid DESC LIMIT 50`)
  .bind(threadId).all<{id:string;body:string;created_at:string}>();
 return {thread,posts:posts.results,ids:JSON.stringify(posts.results.map(p=>p.id))};
}
export const forumCalendarDigest=async(text:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))))
 .map(x=>x.toString(16).padStart(2,'0')).join('');
export async function loadForumCalendarProposal(env:Env,classId:string,id:string) {
 const p=await env.DB.prepare('SELECT * FROM forum_calendar_proposals WHERE id=? AND class_id=?').bind(id,classId).first<ForumCalendarProposal>();
 if(!p)throw new HTTPException(404,{message:'Proposta non trovata'});
 await loadForumThread(env,classId,p.thread_id);return p;
}
export async function forumCalendarProposalJson(env:Env,p:ForumCalendarProposal,sourceIds?:string) {
 const current=sourceIds??(await forumCalendarSource(env,p.class_id,p.thread_id)).ids;
 const stale=p.status!=='ACCEPTED'&&(current!==p.source_ids_json||p.expires_at<=new Date().toISOString());
 const plan=JSON.parse(p.plan_json) as {actions:{sourcePostIds?:string[]}[]};
 const cited=[...new Set(plan.actions.flatMap(a=>a.sourcePostIds??[]))];
 return {id:p.id,threadId:p.thread_id,status:stale?'STALE':p.status,plan,digest:await forumCalendarDigest(p.plan_json),
  sourcePostIds:cited.length?cited:JSON.parse(p.source_ids_json) as string[],provider:p.provider,createdAt:p.created_at,expiresAt:p.expires_at,acceptedAt:p.accepted_at};
}
