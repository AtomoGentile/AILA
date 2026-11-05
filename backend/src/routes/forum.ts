import { Hono } from 'hono';
import { bodyLimit } from 'hono/body-limit';
import { HTTPException } from 'hono/http-exception';
import type { Env,JWTPayload } from '../types';
import { authMiddleware,newUUID,resolveClassId } from '../auth';
import { inBackground } from '../services/background';
import { aiPreferences,runAiTask } from '../services/aiRouter';
import { loadForumThread,joinForumThread,forumSnapshot,notifyForumReply,forumAiContext,type ForumThreadRow,type ForumPostRow } from '../services/forum';
const forum=new Hono<{Bindings:Env;Variables:{jwtPayload:JWTPayload}}>();forum.use('*',authMiddleware());forum.use('*',bodyLimit({maxSize:256*1024}));
async function reserveForumWrite(env:Env,userId:string,feature:string,max:number) {
 const day=new Date().toISOString().slice(0,10);
 const row=await env.DB.prepare(`INSERT INTO ai_usage(user_id,day,feature,requests,reserved_tokens) VALUES (?,?,?,1,0)
 ON CONFLICT(user_id,day,feature) DO UPDATE SET requests=requests+1 WHERE requests<? RETURNING requests`)
 .bind(userId,day,feature,max).first();
 if(!row)throw new HTTPException(429,{message:'Hai raggiunto il limite giornaliero. Riprova domani.'});
}
const clean=(v:unknown,max:number)=>typeof v==='string'&&v.trim().length>0&&v.trim().length<=max?v.trim():null;
forum.get('/',async c=>{
 const offset=Math.min(Math.max(Number(c.req.query('offset'))||0,0),10000);const classId=await resolveClassId(c);
 const rows=await c.env.DB.prepare(`SELECT t.*,(SELECT COUNT(*)-1 FROM forum_posts p WHERE p.thread_id=t.id AND p.hidden=0) replies
 FROM forum_threads t WHERE class_id=? AND hidden=0 ORDER BY last_message_at DESC,id DESC LIMIT 51 OFFSET ?`).bind(classId,offset).all<ForumThreadRow&{replies:number}>();
 return c.json({threads:rows.results.slice(0,50).map(t=>({id:t.id,title:t.title,resolved:!!t.resolved,featuredPostId:t.featured_post_id,replies:Math.max(t.replies,0),
 createdAt:t.created_at,lastMessageAt:t.last_message_at,isAuthor:t.author_id===c.get('jwtPayload').sub,
 needsResolution:t.author_id===c.get('jwtPayload').sub&&!t.resolved&&Date.parse(t.last_message_at.replace(' ','T')+'Z')<Date.now()-3*86400000})),hasMore:rows.results.length>50});
});
forum.post('/',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);
 const body=await c.req.json<{title?:unknown;body?:unknown;anonymous?:boolean;forumConsent?:boolean}>();
 const title=clean(body.title,180);const text=clean(body.body,12000);
 if(!title||!text)return c.json({error:'Titolo (180 caratteri) e domanda (12000 caratteri) sono obbligatori'},400);
 if(body.forumConsent!==true)return c.json({error:'Conferma le regole del forum e l’informativa prima di pubblicare'},403);
 await reserveForumWrite(c.env,user.sub,'FORUM_THREADS',20);
 const id=newUUID();await c.env.DB.batch([
  c.env.DB.prepare('INSERT INTO forum_threads(id,class_id,author_id,title) VALUES (?,?,?,?)').bind(id,classId,user.sub,title),
  c.env.DB.prepare('INSERT INTO forum_posts(id,thread_id,author_id,body) VALUES (?,?,?,?)').bind(id,id,user.sub,text)
 ]);
 await joinForumThread(c.env,id,user.sub,body.anonymous===true);
 return c.json(await forumSnapshot(c.env,classId,user.sub,id),201);
});
forum.get('/:id',async c=>c.json(await forumSnapshot(c.env,await resolveClassId(c),c.get('jwtPayload').sub,c.req.param('id'),c.req.query('before'))));
forum.post('/:id/posts',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);const thread=await loadForumThread(c.env,classId,c.req.param('id'));
 const body=await c.req.json<{body?:unknown;anonymous?:boolean;parentId?:string;mentions?:string[];attachmentIds?:string[];forumConsent?:boolean}>();
 const text=clean(body.body,12000);const attachments=Array.isArray(body.attachmentIds)?[...new Set(body.attachmentIds)]:[];
 if((!text&&!attachments.length)||attachments.length>5)return c.json({error:'Scrivi una risposta o allega fino a 5 file'},400);
 if(body.forumConsent!==true)return c.json({error:'Accetta le regole prima di pubblicare'},403);
 const parentId=body.parentId??thread.id;
 if(!await c.env.DB.prepare('SELECT id FROM forum_posts WHERE id=? AND thread_id=? AND hidden=0').bind(parentId,thread.id).first())return c.json({error:'Messaggio a cui rispondere non trovato'},400);
 await joinForumThread(c.env,thread.id,user.sub,body.anonymous===true);
 const members=await c.env.DB.prepare('SELECT public_key FROM forum_participants WHERE thread_id=?').bind(thread.id).all<{public_key:string}>();
 const mentions=Array.isArray(body.mentions)?[...new Set(body.mentions)].filter(k=>members.results.some(p=>p.public_key===k)).slice(0,20):[];
 for(const id of attachments){
  if(!await c.env.DB.prepare('SELECT id FROM content_attachments WHERE id=? AND thread_id=? AND uploaded_by=? AND post_id IS NULL AND deleted_at IS NULL AND expires_at>?')
   .bind(id,thread.id,user.sub,new Date().toISOString()).first())return c.json({error:'Allegato non valido o scaduto'},400);
 }
 await reserveForumWrite(c.env,user.sub,'FORUM_POSTS',100);
 const id=newUUID();await c.env.DB.batch([
  c.env.DB.prepare('INSERT INTO forum_posts(id,thread_id,author_id,parent_id,body,mentions_json) VALUES (?,?,?,?,?,?)').bind(id,thread.id,user.sub,parentId,text??'',JSON.stringify(mentions)),
  c.env.DB.prepare('UPDATE forum_threads SET last_message_at=CURRENT_TIMESTAMP,nudged_at=NULL WHERE id=?').bind(thread.id),
  ...attachments.map(a=>c.env.DB.prepare('UPDATE content_attachments SET post_id=? WHERE id=? AND uploaded_by=? AND post_id IS NULL').bind(id,a,user.sub))
 ]);
 const post=await c.env.DB.prepare('SELECT * FROM forum_posts WHERE id=?').bind(id).first<ForumPostRow>();
 if(post)inBackground(c,notifyForumReply(c.env,thread,post));
 return c.json({id,invokePerabot:/@perabot\b/i.test(text??'')},201);
});
forum.put('/:id/subscription',async c=>{
 const user=c.get('jwtPayload');const thread=await loadForumThread(c.env,await resolveClassId(c),c.req.param('id'));
 const {mode}=await c.req.json<{mode:string}>();if(!['REPLIES','ALL','NONE'].includes(mode))return c.json({error:'Preferenza non valida'},400);
 await c.env.DB.prepare('INSERT INTO forum_subscriptions(thread_id,user_id,mode) VALUES (?,?,?) ON CONFLICT(thread_id,user_id) DO UPDATE SET mode=excluded.mode').bind(thread.id,user.sub,mode).run();return c.json({success:true});
});
forum.put('/:id/state',async c=>{
 const user=c.get('jwtPayload');const thread=await loadForumThread(c.env,await resolveClassId(c),c.req.param('id'));
 if(thread.author_id!==user.sub&&user.role!=='REPRESENTATIVE')return c.json({error:'Solo autore o moderatore può cambiare lo stato'},403);
 const body=await c.req.json<{resolved?:boolean;featuredPostId?:string|null}>();
 if(body.resolved!==undefined&&typeof body.resolved!=='boolean')return c.json({error:'Stato non valido'},400);
 if(body.featuredPostId!=null&&!await c.env.DB.prepare('SELECT id FROM forum_posts WHERE id=? AND thread_id=? AND hidden=0').bind(body.featuredPostId,thread.id).first())return c.json({error:'Risposta non trovata'},400);
 await c.env.DB.prepare('UPDATE forum_threads SET resolved=?,featured_post_id=? WHERE id=?')
 .bind(body.resolved===undefined?thread.resolved:+body.resolved,'featuredPostId' in body?body.featuredPostId:thread.featured_post_id,thread.id).run();return c.json({success:true});
});
forum.post('/:id/reports',async c=>{
 const user=c.get('jwtPayload');const thread=await loadForumThread(c.env,await resolveClassId(c),c.req.param('id'));
 const body=await c.req.json<{reason:string;postId?:string}>();const reason=clean(body.reason,1000);if(!reason)return c.json({error:'Indica il motivo'},400);
 if(body.postId&&!await c.env.DB.prepare('SELECT id FROM forum_posts WHERE id=? AND thread_id=?').bind(body.postId,thread.id).first())return c.json({error:'Messaggio non trovato'},404);
 await reserveForumWrite(c.env,user.sub,'FORUM_REPORTS',20);
 const id=newUUID();await c.env.DB.prepare('INSERT INTO forum_reports(id,thread_id,post_id,reported_by,reason) VALUES (?,?,?,?,?)').bind(id,thread.id,body.postId??null,user.sub,reason).run();return c.json({id},201);
});
forum.get('/:id/reports',async c=>{
 if(c.get('jwtPayload').role!=='REPRESENTATIVE')return c.json({error:'Solo moderatori'},403);
 const thread=await loadForumThread(c.env,await resolveClassId(c),c.req.param('id'),true);
 const reports=await c.env.DB.prepare('SELECT id,post_id AS postId,reason,status,created_at AS createdAt FROM forum_reports WHERE thread_id=? ORDER BY created_at DESC').bind(thread.id).all();return c.json({reports:reports.results});
});
forum.put('/:id/moderation',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);const thread=await loadForumThread(c.env,classId,c.req.param('id'),true);
 if(user.role!=='REPRESENTATIVE')return c.json({error:'Solo moderatori'},403);
 const body=await c.req.json<{postId?:string;hidden:boolean;reportId?:string}>();if(typeof body.hidden!=='boolean')return c.json({error:'Stato non valido'},400);
 if(body.postId){
  const changed=await c.env.DB.prepare('UPDATE forum_posts SET hidden=? WHERE id=? AND thread_id=? RETURNING id').bind(+body.hidden,body.postId,thread.id).first();
  if(!changed)return c.json({error:'Messaggio non trovato'},404);
  if(body.hidden)await c.env.DB.prepare('UPDATE forum_threads SET featured_post_id=NULL WHERE id=? AND featured_post_id=?').bind(thread.id,body.postId).run();
 }else await c.env.DB.prepare('UPDATE forum_threads SET hidden=? WHERE id=?').bind(+body.hidden,thread.id).run();
 if(body.reportId)await c.env.DB.prepare("UPDATE forum_reports SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=? AND thread_id=?").bind(body.reportId,thread.id).run();
 await c.env.DB.prepare('INSERT INTO moderation_audit(id,class_id,actor_id,target_id,action) VALUES (?,?,?,?,?)').bind(newUUID(),classId,user.sub,body.postId??thread.id,body.hidden?'HIDE':'RESTORE').run();return c.json({success:true});
});
forum.post('/:id/ai',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);const thread=await loadForumThread(c.env,classId,c.req.param('id'));
 const body=await c.req.json<{question?:string;publish?:boolean;replyToId?:string;localOnly?:boolean}>();
 if(body.replyToId&&!await c.env.DB.prepare('SELECT id FROM forum_posts WHERE id=? AND thread_id=? AND hidden=0').bind(body.replyToId,thread.id).first())return c.json({error:'Messaggio non trovato'},404);
 const source=await forumAiContext(c.env,classId,user.sub,thread.id,body.question);
 const result=await runAiTask(c.env,user.sub,{feature:body.publish?'PERABOT':'SUMMARIZE',service:'FORUM',question:(body.question??'Riassumi domanda, risposte, punti certi e questioni aperte. Non segnare la discussione come risolta.').slice(0,8000),context:source.context,localOnly:body.localOnly});
 let postId:string|null=null;
 if(body.publish===true){
  postId=newUUID();await c.env.DB.batch([
   c.env.DB.prepare('INSERT INTO forum_posts(id,thread_id,parent_id,body,is_ai,generated_at,provider) VALUES (?,?,?,?,1,?,?)').bind(postId,thread.id,body.replyToId??thread.id,result.text,result.generatedAt,result.provider),
   c.env.DB.prepare('UPDATE forum_threads SET last_message_at=CURRENT_TIMESTAMP,nudged_at=NULL WHERE id=?').bind(thread.id)
  ]);
  const post=await c.env.DB.prepare('SELECT * FROM forum_posts WHERE id=?').bind(postId).first<ForumPostRow>();if(post)inBackground(c,notifyForumReply(c.env,thread,post));
 }
 return c.json({...result,sources:source.sources,postId});
});
export default forum;
