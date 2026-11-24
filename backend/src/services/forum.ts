import type { Env } from '../types';
import { HTTPException } from 'hono/http-exception';
import { newUUID } from '../auth';
import { notifyUsers } from './fcm';
import { contentJson,type ContentRow } from './content';
import { searchAuthorized } from './acaSearch';

export interface ForumThreadRow {id:string;class_id:string;author_id:string|null;title:string;resolved:number;featured_post_id:string|null;created_at:string;last_message_at:string;nudged_at:string|null;hidden:number;}
export interface ForumPostRow {id:string;thread_id:string;author_id:string|null;parent_id:string|null;body:string;mentions_json:string;is_ai:number;generated_at:string|null;provider:string|null;hidden:number;created_at:string;}
interface Participant {user_id:string;public_key:string;ordinal:number;anonymous:number;first_name:string;last_name:string;}
export async function loadForumThread(env:Env,classId:string,id:string,includeHidden=false):Promise<ForumThreadRow>{
 const row=await env.DB.prepare('SELECT * FROM forum_threads WHERE id=? AND class_id=?').bind(id,classId).first<ForumThreadRow>();
 if(!row||row.hidden&&!includeHidden)throw new HTTPException(404,{message:'Discussione non trovata'});return row;
}
export async function joinForumThread(env:Env,threadId:string,userId:string,anonymous:boolean){
 // The chosen identity is immutable within the thread. Changing a checkbox later cannot
 // link a previously anonymous pseudonym to a real name.
 await env.DB.prepare(`INSERT OR IGNORE INTO forum_participants(thread_id,user_id,public_key,ordinal,anonymous)
 SELECT ?,?,?,CASE WHEN (SELECT author_id FROM forum_threads WHERE id=?)=? THEN 0
 WHEN ? THEN COALESCE(MAX(CASE WHEN ordinal>0 THEN ordinal END),0)+1
 ELSE COALESCE(MIN(CASE WHEN ordinal<0 THEN ordinal END),0)-1 END,? FROM forum_participants WHERE thread_id=?`)
 .bind(threadId,userId,newUUID(),threadId,userId,+anonymous,+anonymous,threadId).run();
 await env.DB.prepare(`INSERT OR IGNORE INTO forum_subscriptions(thread_id,user_id,mode) VALUES (?,?,'REPLIES')`).bind(threadId,userId).run();
}
export async function forumSnapshot(env:Env,classId:string,userId:string,threadId:string,before?:string){
 const thread=await loadForumThread(env,classId,threadId);
 const people=await env.DB.prepare(`SELECT p.*,u.first_name,u.last_name FROM forum_participants p JOIN users u ON u.id=p.user_id WHERE p.thread_id=?`).bind(threadId).all<Participant>();
 const alias=(p:Participant)=>p.anonymous?(p.user_id===thread.author_id?'OP':`Pera educata ${p.ordinal}`):`${p.first_name} ${p.last_name}`;
 const participantJson=(p:Participant)=>({key:p.public_key,label:alias(p),isMe:p.user_id===userId,isOp:p.user_id===thread.author_id,anonymous:!!p.anonymous});
 const cut=before?.split('|');const time=cut?.[0]??'9999';const key=cut?.[1]??'~';
 const rows=await env.DB.prepare(`SELECT * FROM forum_posts WHERE thread_id=? AND
 (created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC LIMIT 101`)
 .bind(threadId,time,time,key).all<ForumPostRow>();
 const page=rows.results.slice(0,100).reverse();
 const oldest=page[0];
 if(!before&&!page.some(p=>p.id===thread.id)){
  const root=await env.DB.prepare('SELECT * FROM forum_posts WHERE id=? AND thread_id=?').bind(thread.id,thread.id).first<ForumPostRow>();
  if(root)page.unshift(root);
 }
 const attachments=await env.DB.prepare('SELECT * FROM content_attachments WHERE thread_id=? AND post_id IS NOT NULL').bind(threadId).all<ContentRow>();
 const posts=page.map(post=>{
  const p=people.results.find(p=>p.user_id===post.author_id);
  const author=post.is_ai?{key:'ai',label:'AILA · AI',isMe:false,isOp:false,anonymous:false}:p?participantJson(p):{key:'removed',label:'Partecipante rimosso',isMe:false,isOp:false,anonymous:true};
  const mentions=JSON.parse(post.mentions_json) as string[];
  return {id:post.id,parentId:post.parent_id,body:post.hidden?'Messaggio rimosso dalla moderazione':post.body,
   author,mentions:people.results.filter(p=>mentions.includes(p.public_key)).map(participantJson),
   isAi:!!post.is_ai,generatedAt:post.generated_at,provider:post.provider,hidden:!!post.hidden,createdAt:post.created_at,
   attachments:post.hidden?[]:attachments.results.filter(a=>a.post_id===post.id).map(contentJson)};
 });
 const subscription=await env.DB.prepare('SELECT mode FROM forum_subscriptions WHERE thread_id=? AND user_id=?').bind(threadId,userId).first<{mode:string}>();
 const mine=people.results.find(p=>p.user_id===userId);
 const isResolved=Number(thread.resolved)===1;
 return {thread:{id:thread.id,title:thread.title,resolved:isResolved,featuredPostId:thread.featured_post_id,createdAt:thread.created_at,lastMessageAt:thread.last_message_at,isAuthor:thread.author_id===userId,
  needsResolution:thread.author_id===userId&&!isResolved&&Date.parse(thread.last_message_at.replace(' ','T')+'Z')<Date.now()-3*86400000},
  participants:people.results.map(participantJson),posts,hasMore:rows.results.length>100,
  nextBefore:oldest?`${oldest.created_at}|${oldest.id}`:null,notificationMode:subscription?.mode??'REPLIES',myIdentity:mine?participantJson(mine):null};
}

export async function notifyForumReply(env:Env,thread:ForumThreadRow,post:ForumPostRow){
 const subs=await env.DB.prepare('SELECT user_id,mode FROM forum_subscriptions WHERE thread_id=? AND user_id<>?').bind(thread.id,post.author_id??'').all<{user_id:string;mode:string}>();
 const parent=post.parent_id?await env.DB.prepare('SELECT author_id FROM forum_posts WHERE id=? AND thread_id=?').bind(post.parent_id,thread.id).first<{author_id:string|null}>():null;
 const mentioned=await env.DB.prepare('SELECT user_id,public_key FROM forum_participants WHERE thread_id=?').bind(thread.id).all<{user_id:string;public_key:string}>();
 const mentions=JSON.parse(post.mentions_json) as string[];
 const direct=new Set(mentioned.results.filter(p=>mentions.includes(p.public_key)).map(p=>p.user_id));
 if(parent?.author_id)direct.add(parent.author_id);
 const ids=subs.results.filter(s=>s.mode==='ALL'||s.mode==='REPLIES'&&direct.has(s.user_id)).map(s=>s.user_id);
 await notifyUsers(env,ids,'Revisione tra pere',`Nuova risposta: ${thread.title}`,{action:'forum_reply',thread_id:thread.id,post_id:post.id});
}
export async function forumResolutionNudges(env:Env){
 const rows=await env.DB.prepare(`SELECT * FROM forum_threads WHERE resolved=0 AND hidden=0 AND author_id IS NOT NULL
 AND last_message_at<datetime('now','-3 days') AND (nudged_at IS NULL OR nudged_at<last_message_at)
 AND NOT EXISTS(SELECT 1 FROM forum_subscriptions s WHERE s.thread_id=forum_threads.id AND s.user_id=forum_threads.author_id AND s.mode='NONE') LIMIT 100`).all<ForumThreadRow>();
 for(const thread of rows.results){
  const claimed=await env.DB.prepare(`UPDATE forum_threads SET nudged_at=CURRENT_TIMESTAMP WHERE id=? AND
  (nudged_at IS NULL OR nudged_at<last_message_at) RETURNING id`).bind(thread.id).first();
  if(claimed)await notifyUsers(env,[thread.author_id!],'Sei ancora qui?',`«${thread.title}» è risolta? Puoi segnarla tu: resterà disponibile.`,{action:'forum_nudge',thread_id:thread.id});
 }
}

/** Shared bot context must be visible to every class member, never the requester's private calendar. */
export async function forumAiContext(env:Env,classId:string,userId:string,threadId:string,question=''){
 const snapshot=await forumSnapshot(env,classId,userId,threadId);
 const posts=snapshot.posts.filter(p=>!p.hidden);
 const attachmentRows=await env.DB.prepare(
  'SELECT * FROM content_attachments WHERE thread_id=? AND post_id IS NOT NULL AND deleted_at IS NULL'
 ).bind(threadId).all<ContentRow>();
 const byPost=new Map<string,ContentRow[]>();
 for(const a of attachmentRows.results){
  if(!a.post_id)continue;
  const list=byPost.get(a.post_id)??[];list.push(a);byPost.set(a.post_id,list);
 }
 // Descrivi fino a 2 immagini leggere (<= 512 KB) non scadute via Workers AI Vision così @ai vede gli allegati visivi
 // senza rischiare OOM/crash del Worker su foto multi-megabyte.
 const imageNotes=new Map<string,string>();
 const nowIso=new Date().toISOString();
 const recentImages=attachmentRows.results
  .filter(a=>a.mime.startsWith('image/')&&a.r2_key&&a.expires_at>nowIso&&a.byte_size>0&&a.byte_size<=512*1024)
  .slice(-2);
 for(const img of recentImages){
  try{
   const obj=await env.CIRCULARS_BUCKET.get(img.r2_key);
   if(obj&&env.AI){
    const bytes=new Uint8Array(await obj.arrayBuffer());
    if(bytes.byteLength>0&&bytes.byteLength<=512*1024){
     let timer:ReturnType<typeof setTimeout>|undefined;
     try{
      const res=await Promise.race([
       (env.AI as {run:(m:string,i:unknown)=>Promise<{description?:string;response?:string}>})
        .run('@cf/llava-hf/llava-1.5-7b-hf',{image:Array.from(bytes),prompt:'Descrivi in italiano in modo preciso il contenuto di questa immagine scolastica (testo, formule, esercizi o appunti visibili).',max_tokens:300}),
       new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(new Error('VISION_TIMEOUT')),10_000);})
      ]);
      const desc=(res.description??res.response??'').trim();
      if(desc)imageNotes.set(img.id,desc.slice(0,1200));
     }finally{if(timer)clearTimeout(timer);}
    }
   }
  }catch{/* Vision fallback non bloccante */}
 }
 const text=posts.map(p=>{
  const atts=(byPost.get(p.id)??[]).map(a=>{
   if(a.extracted_text)return `[allegato:${a.file_name} (${a.mime}) — testo]: ${a.extracted_text.slice(0,3000)}`;
   if(a.transcript)return `[vocale:${a.file_name} — trascrizione]: ${a.transcript.slice(0,3000)}`;
   if(imageNotes.has(a.id))return `[immagine:${a.file_name} — contenuto visivo]: ${imageNotes.get(a.id)}`;
   return `[allegato:${a.file_name} (${a.mime})]`;
  }).join('\n');
  return `[messaggio:${p.id}] ${p.author.label}: ${p.body}${atts?`\n${atts}`:''}`;
 }).join('\n');
 const events=await env.DB.prepare('SELECT id,title,event_date,start_time FROM calendar_events WHERE class_id=? AND is_for_all=1 AND event_date>=? ORDER BY event_date LIMIT 30')
  .bind(classId,new Date().toISOString().slice(0,10)).all<{id:string;title:string;event_date:string;start_time:string|null}>()
  .catch(()=>({results:[] as {id:string;title:string;event_date:string;start_time:string|null}[]}));
 const circulars=await env.DB.prepare(`SELECT c.number,c.title,c.publish_date FROM circulars c ORDER BY c.number DESC LIMIT 30`)
  .all<{number:number;title:string;publish_date:string}>()
  .catch(()=>({results:[] as {number:number;title:string;publish_date:string}[]}));
 const searched=await searchAuthorized(env,classId,userId,question??'',true).catch(()=>[]);
 const statusLine=snapshot.thread.resolved
  ?'STATO DISCUSSIONE: RISOLTA (contrassegnata risolta dall’autore/moderatore)'
  :'STATO DISCUSSIONE: APERTA (NON RISOLTA — non dichiarare mai la domanda o discussione risolta o chiusa)';
 return {context:`FONTI AILA PERTINENTI:\n${JSON.stringify(searched)}\nDISCUSSIONE: ${snapshot.thread.title}\n${statusLine}\n${snapshot.hasMore?'ATTENZIONE: sono inclusi solo gli ultimi 100 messaggi.\n':''}${text}\nCALENDARIO CONDIVISO:\n${JSON.stringify(events.results)}\nINDICE CIRCOLARI PUBBLICHE:\n${JSON.stringify(circulars.results)}`.slice(0,46_000),
  sources:posts.map(p=>({postId:p.id,label:p.author.label})).slice(-100)};
}
