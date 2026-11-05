import type { Env } from '../types';
import { HTTPException } from 'hono/http-exception';

export interface ContentRow {
 id:string;class_id:string;uploaded_by:string|null;thread_id:string|null;post_id:string|null;
 file_name:string;mime:string;byte_size:number;r2_key:string;extracted_text:string;
 transcript:string|null;transcript_generated_at:string|null;duration_seconds:number|null;
 expires_at:string;deleted_at:string|null;created_at:string;
}
export async function authorizedContent(env:Env,classId:string,userId:string,id:string):Promise<ContentRow>{
 const item=await env.DB.prepare('SELECT * FROM content_attachments WHERE id=? AND class_id=?').bind(id,classId).first<ContentRow>();
 if(!item)throw new HTTPException(404,{message:'Allegato non trovato'});
 if(item.thread_id){
  const thread=await env.DB.prepare('SELECT id FROM forum_threads WHERE id=? AND class_id=? AND hidden=0').bind(item.thread_id,classId).first();
  if(!thread)throw new HTTPException(404,{message:'Allegato non trovato'});
  if(!item.post_id&&item.uploaded_by!==userId)throw new HTTPException(404,{message:'Allegato non trovato'});
  if(item.post_id&&!await env.DB.prepare('SELECT id FROM forum_posts WHERE id=? AND hidden=0').bind(item.post_id).first())throw new HTTPException(404,{message:'Allegato non trovato'});
 }else if(item.uploaded_by!==userId)throw new HTTPException(404,{message:'Allegato non trovato'});
 return item;
}
export function contentJson(item:ContentRow){const expired=!!item.deleted_at||item.expires_at<=new Date().toISOString();return {id:item.id,fileName:expired?'Allegato scaduto':item.file_name,
 mime:item.mime,bytes:item.byte_size,expiresAt:item.expires_at,expired,
 durationSeconds:item.duration_seconds,transcript:expired?null:item.transcript,transcriptGeneratedAt:expired?null:item.transcript_generated_at};}
export async function cleanupContent(env:Env){
 const rows=await env.DB.prepare(`SELECT * FROM content_attachments WHERE deleted_at IS NULL AND
 (expires_at<=? OR uploaded_by IS NULL OR (thread_id IS NOT NULL AND post_id IS NULL AND created_at<datetime('now','-1 day'))) LIMIT 100`)
 .bind(new Date().toISOString()).all<ContentRow>();
 for(const item of rows.results){
  await env.CIRCULARS_BUCKET.delete(item.r2_key);
  if(await env.CIRCULARS_BUCKET.head(item.r2_key))continue;
  await env.DB.prepare(`UPDATE content_attachments SET deleted_at=?,file_name='Allegato scaduto',extracted_text='',transcript=NULL,
  transcript_generated_at=NULL WHERE id=?`).bind(new Date().toISOString(),item.id).run();
 }
 await env.DB.prepare(`DELETE FROM ai_usage WHERE day<date('now','-30 days')`).run();
 await env.DB.prepare(`DELETE FROM ai_account_budget WHERE day<date('now','-30 days')`).run();
 await env.DB.prepare(`DELETE FROM aca_operations WHERE created_at<datetime('now','-30 days')`).run();
}
