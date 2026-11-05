import { Hono } from 'hono';
import { bodyLimit } from 'hono/body-limit';
import { HTTPException } from 'hono/http-exception';
import { authMiddleware,newUUID,resolveClassId } from '../auth';
import type { Env,JWTPayload } from '../types';
import { attachmentExpiry,CONTENT_POLICY,validateContent } from '../services/contentPolicy';
import { authorizedContent,contentJson,type ContentRow } from '../services/content';
import { runAiTask } from '../services/aiRouter';
const content=new Hono<{Bindings:Env;Variables:{jwtPayload:JWTPayload}}>();
content.use('*',authMiddleware());
content.get('/policy',c=>c.json({...CONTENT_POLICY,smallDays:90,checks:'Formato, firma binaria, limiti e contenuti attivi; download autenticato con nosniff.'}));
content.get('/inbox',async c=>{
 const classId=await resolveClassId(c);
 const raw=c.req.query('cursor')??'';const parts=raw?raw.split(':').map(Number):[0,0];
 if(parts.length!==2||parts.some(n=>!Number.isSafeInteger(n)||n<0))return c.json({error:'Cursore non valido'},400);
 const rows=await c.env.DB.prepare(`SELECT a.*,p.rowid AS post_sequence,a.rowid AS attachment_sequence
 FROM content_attachments a JOIN forum_posts p ON p.id=a.post_id JOIN forum_threads t ON t.id=p.thread_id
 WHERE a.class_id=? AND t.class_id=? AND p.hidden=0 AND t.hidden=0 AND a.deleted_at IS NULL AND a.expires_at>?
 AND (p.rowid>? OR (p.rowid=? AND a.rowid>?)) ORDER BY p.rowid,a.rowid LIMIT 21`)
 .bind(classId,classId,new Date().toISOString(),parts[0],parts[0],parts[1]).all<ContentRow&{post_sequence:number;attachment_sequence:number}>();
 c.header('Cache-Control','private, no-store');
 return c.json({classId,attachments:rows.results.slice(0,20).map(a=>({attachment:contentJson(a),cursor:`${a.post_sequence}:${a.attachment_sequence}`})),hasMore:rows.results.length>20});
});
content.post('/',bodyLimit({maxSize:22*1024*1024,onError:c=>c.json({error:'File troppo grande'},413)}),async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);
 if(Number(c.req.header('Content-Length')??0)>22*1024*1024)return c.json({error:'File troppo grande'},413);
 const form=await c.req.parseBody();const file=form.file;const threadId=typeof form.threadId==='string'?form.threadId:null;
 if(!(file instanceof File)||file.size>20*1024*1024)return c.json({error:'Scegli un file fino a 20 MB'},400);
 if(threadId&&!await c.env.DB.prepare('SELECT id FROM forum_threads WHERE id=? AND class_id=? AND hidden=0').bind(threadId,classId).first())return c.json({error:'Discussione non trovata'},404);
 const checked=validateContent(file.name,file.type,new Uint8Array(await file.arrayBuffer()));
 const day=new Date().toISOString().slice(0,10);
 const reservation=await c.env.DB.prepare(`INSERT INTO ai_usage(user_id,day,feature,requests,reserved_tokens) VALUES (?,?,'UPLOAD',1,?)
 ON CONFLICT(user_id,day,feature) DO UPDATE SET requests=requests+1,reserved_tokens=reserved_tokens+excluded.reserved_tokens
 WHERE requests<30 AND reserved_tokens+excluded.reserved_tokens<=104857600 RETURNING requests`).bind(user.sub,day,file.size).first();
 if(!reservation)return c.json({error:'Limite giornaliero allegati raggiunto (30 file, 100 MB)'},429);
 const id=newUUID();const key=`content/${classId}/${user.sub}/${id}`;const expiry=attachmentExpiry(file.size,90);
 const extracted=(checked.text||typeof form.text==='string'&&checked.mime==='application/pdf'?checked.text||String(form.text):'').slice(0,100_000);
 await c.env.CIRCULARS_BUCKET.put(key,file.stream(),{httpMetadata:{contentType:checked.mime}});
 try{await c.env.DB.prepare(`INSERT INTO content_attachments(id,class_id,uploaded_by,thread_id,file_name,mime,byte_size,r2_key,extracted_text,duration_seconds,expires_at)
 VALUES (?,?,?,?,?,?,?,?,?,?,?)`).bind(id,classId,user.sub,threadId,file.name.replace(/[\r\n/\\]/g,'_').slice(0,160),checked.mime,file.size,key,extracted,checked.duration,expiry).run();}
 catch(e){await c.env.CIRCULARS_BUCKET.delete(key);throw e;}
 const row=await authorizedContent(c.env,classId,user.sub,id);return c.json({attachment:contentJson(row)},201);
});
content.get('/:id',async c=>c.json({attachment:contentJson(await authorizedContent(c.env,await resolveClassId(c),c.get('jwtPayload').sub,c.req.param('id')))}));
content.get('/:id/text',async c=>{
 const item=await authorizedContent(c.env,await resolveClassId(c),c.get('jwtPayload').sub,c.req.param('id'));
 if(item.deleted_at||item.expires_at<=new Date().toISOString())return c.json({error:'Allegato scaduto'},410);
 return c.json({text:item.transcript??item.extracted_text,isTranscript:item.transcript!==null,generatedAt:item.transcript_generated_at});
});
content.get('/:id/file',async c=>{
 const item=await authorizedContent(c.env,await resolveClassId(c),c.get('jwtPayload').sub,c.req.param('id'));
 if(item.deleted_at||item.expires_at<=new Date().toISOString())return c.json({error:'Allegato scaduto. Il messaggio resta disponibile.'},410);
 const object=await c.env.CIRCULARS_BUCKET.get(item.r2_key);if(!object)return c.json({error:'File non disponibile'},410);
 return new Response(object.body,{headers:{'Content-Type':item.mime,'Content-Disposition':`attachment; filename*=UTF-8''${encodeURIComponent(item.file_name)}`,
 'X-Content-Type-Options':'nosniff','Cache-Control':'private, no-store','Content-Security-Policy':"default-src 'none'; sandbox"}});
});
content.delete('/:id',async c=>{
 const user=c.get('jwtPayload');const item=await authorizedContent(c.env,await resolveClassId(c),user.sub,c.req.param('id'));
 if(item.uploaded_by!==user.sub&&!(item.thread_id&&user.role==='REPRESENTATIVE'))return c.json({error:'Non puoi eliminare questo allegato'},403);
 await c.env.CIRCULARS_BUCKET.delete(item.r2_key);
 if(await c.env.CIRCULARS_BUCKET.head(item.r2_key))throw new HTTPException(502,{message:'Eliminazione non verificata: riprova'});
 await c.env.DB.prepare(`UPDATE content_attachments SET deleted_at=?,file_name='Allegato scaduto',extracted_text='',transcript=NULL,transcript_generated_at=NULL WHERE id=?`).bind(new Date().toISOString(),item.id).run();
 return c.json({success:true});
});
content.post('/:id/ai',async c=>{
 const user=c.get('jwtPayload');const item=await authorizedContent(c.env,await resolveClassId(c),user.sub,c.req.param('id'));
 if(item.deleted_at||item.expires_at<=new Date().toISOString())return c.json({error:'Allegato scaduto'},410);
 const body=await c.req.json<{action?:string;question?:string;localOnly?:boolean}>();
 const action=body.action??'SUMMARIZE';if(!['TRANSCRIBE','SUMMARIZE','EXPLAIN','EXTRACT_DATES','KEY_POINTS','REVIEW_QUESTIONS','COMPARE','ASK'].includes(action))return c.json({error:'Azione non supportata'},400);
 const questions:Record<string,string>={SUMMARIZE:'Riassumi il contenuto.',EXPLAIN:'Spiega il contenuto con parole semplici.',EXTRACT_DATES:'Elenca soltanto le attività e le date certe presenti nel contenuto. Indica separatamente le date da definire; non creare eventi.',KEY_POINTS:'Estrai i punti chiave.',REVIEW_QUESTIONS:'Crea domande di ripasso basate sul contenuto.',COMPARE:'Confronta le sezioni evidenziando differenze e riferimenti.',ASK:body.question?.slice(0,8000)??'Spiega questo contenuto.'};
 let result;
 if(action==='TRANSCRIBE'){
  if(!item.mime.startsWith('audio/'))return c.json({error:'Questo allegato non è un audio'},400);
  const obj=await c.env.CIRCULARS_BUCKET.get(item.r2_key);if(!obj)return c.json({error:'File non disponibile'},410);
  result=await runAiTask(c.env,user.sub,{feature:'TRANSCRIBE',question:'Trascrivi fedelmente, senza aggiungere parole.',context:'',attachment:true,localOnly:body.localOnly,
   audio:new Uint8Array(await obj.arrayBuffer()),audioSeconds:item.duration_seconds??600});
  const stored=await c.env.DB.prepare('UPDATE content_attachments SET transcript=?,transcript_generated_at=? WHERE id=? AND deleted_at IS NULL AND expires_at>? RETURNING id')
   .bind(result.text,result.generatedAt,item.id,new Date().toISOString()).first();
  if(!stored)return c.json({error:'Allegato scaduto o eliminato durante la trascrizione.'},410);
 }else if(item.mime.startsWith('image/')){
  const obj=await c.env.CIRCULARS_BUCKET.get(item.r2_key);if(!obj)return c.json({error:'File non disponibile'},410);
  result=await runAiTask(c.env,user.sub,{feature:'IMAGE',question:questions[action],context:'',attachment:true,localOnly:body.localOnly,image:new Uint8Array(await obj.arrayBuffer())});
 }else{
  const text=item.transcript??item.extracted_text;
  if(!text.trim())return c.json({error:item.mime.startsWith('audio/')?'Trascrivi prima l’audio.':'Il documento non contiene testo leggibile. Puoi selezionare o copiare il passaggio da analizzare.'},422);
  result=await runAiTask(c.env,user.sub,{feature:'DOCUMENT',question:questions[action],context:`FONTE: ${item.file_name}\n${text.slice(0,46_000)}`,attachment:true,localOnly:body.localOnly});
 }
 return c.json({...result,source:{attachmentId:item.id,fileName:item.file_name},attachment:contentJson(await authorizedContent(c.env,await resolveClassId(c),user.sub,item.id))});
});
export default content;
