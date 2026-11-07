import {beforeEach,describe,expect,it,vi} from 'vitest';
import worker from '../src/index';
import {createD1} from './d1shim';
import {signJWT} from '../src/auth';
import type {Env} from '../src/types';
import {saveAiPreferences,AI_SERVICES} from '../src/services/aiRouter';
import {attachmentExpiry,validateContent,audioDuration} from '../src/services/contentPolicy';
import {cleanupContent} from '../src/services/content';
import {forumResolutionNudges} from '../src/services/forum';
const notifications=vi.hoisted(()=>[] as {ids:string[];body:string}[]);
vi.mock('../src/services/fcm',async original=>({...await original<typeof import('../src/services/fcm')>(),notifyUsers:async(_e:unknown,ids:string[],_title:string,body:string)=>{notifications.push({ids,body});}}));
let env:Env;let db:ReturnType<typeof createD1>['db'];let objects:Map<string,Uint8Array>;let tokens:Record<string,string>;const run=vi.fn();
const ctx={waitUntil() {},passThroughOnException(){}} as never;
beforeEach(async()=>{
 const d=createD1();db=d.db;objects=new Map();tokens={};notifications.length=0;run.mockReset();run.mockResolvedValue({response:'Risposta generata con fonti'});
 env={DB:d.d1,JWT_SECRET:'test-secret',AI:{run},CIRCULARS_BUCKET:{
  put:async(k:string,body:BodyInit)=>{objects.set(k,new Uint8Array(await new Response(body).arrayBuffer()));},
  get:async(k:string)=>{const b=objects.get(k);return b?{body:new Response(b).body,arrayBuffer:async()=>b.buffer,text:async()=>new TextDecoder().decode(b)}:null;},
  head:async(k:string)=>objects.has(k)?{}:null,delete:async(k:string)=>{objects.delete(k);}
 }} as unknown as Env;
 db.exec("INSERT INTO classes(id,label) VALUES ('C1','3 B'),('C2','5 A')");
 for(const [id,role,cls] of [['op','STUDENT','C1'],['peer','STUDENT','C1'],['rep','REPRESENTATIVE','C1'],['other','STUDENT','C2']]){
  db.prepare('INSERT INTO users(id,first_name,last_name,username,password_hash,role,class_id) VALUES (?,?,?,?,?,?,?)').run(id,'Nome',id,id,'unused',role,cls);
  tokens[id]=await signJWT({sub:id,username:id,role:role as 'STUDENT',classId:cls},env.JWT_SECRET);
  await saveAiPreferences(env,id,{aiEnabled:true,acaEnabled:true,holdToOpen:true,services:AI_SERVICES,cloudConsent:false,externalConsent:false,attachmentConsent:false,audioConsent:false,nudgesEnabled:false,advancedEnabled:false});
 }
});
async function call(method:string,path:string,body?:unknown,user='op'){
 const form=body instanceof FormData;
 const response=await worker.fetch(new Request('https://test'+path,{method,headers:{Authorization:'Bearer '+tokens[user],...(!form&&body!==undefined?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:form?body:JSON.stringify(body)}),env,ctx);
 return {status:response.status,json:await response.json().catch(()=>({})) as any,response};
}
const createThread=(anonymous=true)=>call('POST','/api/forum',{title:'Come preparare la verifica?',body:'Quali argomenti dobbiamo studiare?',anonymous,forumConsent:true});
const action=(extra:Record<string,unknown>={})=>({tool:'calendar.create',title:'Esame scritto',date:'2027-03-20',category:'VERIFICA',time:null,notes:null,visibility:'CLASS',circularNumber:null,...(extra.tool==='calendar.update'?{fields:['date']}:{}),...extra});
const preview=(actions=[action()],user='op')=>call('POST','/api/aca/operations',{actions},user);
const confirm=(p:any,user='op')=>call('POST',`/api/aca/operations/${p.id}/confirm`,{confirmed:true,digest:p.digest},user);
async function consent(user='op') {await saveAiPreferences(env,user,{aiEnabled:true,acaEnabled:true,holdToOpen:true,services:AI_SERVICES,cloudConsent:true,externalConsent:false,attachmentConsent:true,audioConsent:true,nudgesEnabled:false,advancedEnabled:false});}

describe('ACA confirmations and atomic calendar tools',()=>{
 it('a preview never writes; only the owning account can explicitly confirm',async()=>{
  const p=await preview();expect(p.status).toBe(201);expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
  expect((await confirm(p.json,'peer')).status).toBe(404);
  expect((await call('POST',`/api/aca/operations/${p.json.id}/confirm`,{confirmed:false,digest:p.json.digest})).status).toBe(400);
  const result=await confirm(p.json);expect(result.status).toBe(200);expect(result.json.results[0].status).toBe('DONE');
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(1);
 });
 it('replays and racing confirmations are idempotent across devices',async()=>{
  const p=(await preview()).json;const responses=await Promise.all([confirm(p),confirm(p)]);expect(responses.every(r=>r.status===200)).toBe(true);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(1);
  const second=(await preview()).json;const result=await confirm(second);expect(result.json.results[0].status).toBe('SKIPPED');
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(1);
 });
 it('cancellation and expiration prevent all writes',async()=>{
  const p=(await preview()).json;await call('POST',`/api/aca/operations/${p.id}/cancel`,{});expect((await confirm(p)).status).toBe(409);
  const q=(await preview()).json;db.prepare("UPDATE aca_operations SET expires_at='2000-01-01' WHERE id=?").run(q.id);expect((await confirm(q)).status).toBe(409);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
 });
 it('two accounts confirming separate previews of the same class event create one copy',async()=>{
  const first=(await preview()).json;
  const second=(await preview([action({title:'  ESAME   SCRITTO  '})],'peer')).json;
  const results=await Promise.all([confirm(first),confirm(second,'peer')]);
  expect(results.map(r=>r.json.results[0].status).sort()).toEqual(['DONE','SKIPPED']);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(1);
 });
 it('identical personal reminders belonging to different accounts remain separate',async()=>{
  const first=(await preview([action({visibility:'SELF'})])).json;
  const second=(await preview([action({visibility:'SELF'})],'peer')).json;
  const results=await Promise.all([confirm(first),confirm(second,'peer')]);
  expect(results.every(r=>r.json.results[0].status==='DONE')).toBe(true);
  const rows=db.prepare('SELECT visible_to_user_ids_json FROM calendar_events').all() as any[];
  expect(rows.map(r=>r.visible_to_user_ids_json).sort()).toEqual(['["op"]','["peer"]']);
 });
 it('validates the whole batch before exposing an executable preview',async()=>{
  expect((await preview([action(),action({date:'2027-02-29'})])).status).toBe(400);
  expect((await preview([action({tool:'database.query'})])).status).toBe(400);
  expect((await preview([action({time:'25:00'})])).status).toBe(400);
  expect(db.prepare('SELECT * FROM aca_operations').all()).toHaveLength(0);
 });
 it('previews and confirms recipients and appended notes for all matching existing dates',async()=>{
  await confirm((await preview([action({title:'First FCE',notes:'Aula 2'}),action({title:'First FCE',date:'2027-03-22',notes:'Aula 2'})])).json);
  const events=db.prepare('SELECT * FROM calendar_events ORDER BY event_date').all() as any[];
  const edit=await preview(events.map(e=>action({tool:'calendar.update',eventId:e.id,fields:['visibility','notes'],visibility:'SPECIFIC',visibleToUserIds:['peer','op'],notes:"Aula 2. Portare l'autorizzazione"})));
  expect(edit.status).toBe(201);
  expect(edit.json.plan.actions.every((a:any)=>a.audienceLabel==='Nome op, Nome peer')).toBe(true);
  expect((db.prepare('SELECT * FROM calendar_events').all() as any[]).every(e=>e.is_for_all===1&&e.notes==='Aula 2')).toBe(true);
  const result=await confirm(edit.json);expect(result.json.results.every((r:any)=>r.status==='DONE')).toBe(true);
  const updated=db.prepare('SELECT * FROM calendar_events ORDER BY event_date').all() as any[];
  expect(updated).toHaveLength(2);expect(updated.map(e=>e.event_date)).toEqual(['2027-03-20','2027-03-22']);
  expect(updated.every(e=>e.title==='First FCE'&&e.is_for_all===0&&e.visible_to_user_ids_json==='["op","peer"]'&&e.notes==="Aula 2. Portare l'autorizzazione")).toBe(true);
 });
 it('rejects unknown, cross-class and empty recipient sets',async()=>{
  for(const ids of [[],['missing'],['other']])expect((await preview([action({visibility:'SPECIFIC',visibleToUserIds:ids})])).status).toBe(400);
  expect(db.prepare('SELECT * FROM aca_operations').all()).toHaveLength(0);
 });
 it('preserves a specific audience when only changing notes',async()=>{
  await confirm((await preview([action({visibility:'SPECIFIC',visibleToUserIds:['peer']})])).json);
  const e=db.prepare('SELECT * FROM calendar_events').get() as any;
  const edit=await preview([action({tool:'calendar.update',eventId:e.id,fields:['notes'],notes:'Portare autorizzazione'})]);
  expect(edit.status).toBe(201);expect(edit.json.plan.actions[0].audienceLabel).toBe('Nome peer');
  await confirm(edit.json);
  expect(db.prepare('SELECT is_for_all,visible_to_user_ids_json,notes FROM calendar_events').get()).toMatchObject({is_for_all:0,visible_to_user_ids_json:'["peer"]',notes:'Portare autorizzazione'});
 });
 it('can edit notes without changing a legacy audience whose member left the class',async()=>{
  await confirm((await preview()).json);const e=db.prepare('SELECT * FROM calendar_events').get() as any;
  db.prepare('UPDATE calendar_events SET is_for_all=0,visible_to_user_ids_json=? WHERE id=?').run('["departed"]',e.id);
  const edit=await preview([action({tool:'calendar.update',eventId:e.id,fields:['notes'],notes:'Autorizzazione'})]);
  expect(edit.status).toBe(201);await confirm(edit.json);
  expect(db.prepare('SELECT notes,visible_to_user_ids_json FROM calendar_events').get()).toMatchObject({notes:'Autorizzazione',visible_to_user_ids_json:'["departed"]'});
 });
 it('changes recipients to self or class only when explicitly requested',async()=>{
  await confirm((await preview([action({visibility:'SPECIFIC',visibleToUserIds:['peer']})])).json);
  const e=db.prepare('SELECT * FROM calendar_events').get() as any;
  for(const visibility of ['SELF','CLASS']){
   const p=await preview([action({tool:'calendar.update',eventId:e.id,fields:['visibility'],visibility})]);
   expect(p.status).toBe(201);expect((await confirm(p.json)).json.results[0].status).toBe('DONE');
   expect(db.prepare('SELECT is_for_all,visible_to_user_ids_json FROM calendar_events').get()).toMatchObject(
    visibility==='SELF'?{is_for_all:0,visible_to_user_ids_json:'["op"]'}:{is_for_all:1,visible_to_user_ids_json:null});
  }
 });
 it('rejects a recipient change when the audience changed after preview',async()=>{
  await confirm((await preview()).json);const e=db.prepare('SELECT * FROM calendar_events').get() as any;
  const edit=(await preview([action({tool:'calendar.update',eventId:e.id,fields:['visibility'],visibility:'SPECIFIC',visibleToUserIds:['peer']})])).json;
  db.prepare('UPDATE calendar_events SET is_for_all=0,visible_to_user_ids_json=? WHERE id=?').run('["op"]',e.id);
  expect((await confirm(edit)).json.results[0].status).toBe('CONFLICT');
  expect((db.prepare('SELECT visible_to_user_ids_json FROM calendar_events').get() as any).visible_to_user_ids_json).toBe('["op"]');
 });
 it('updates and deletes only the content shown in the approved preview',async()=>{
  await confirm((await preview()).json);const event=db.prepare('SELECT * FROM calendar_events').get() as any;
  const edit=(await preview([action({tool:'calendar.update',eventId:event.id,date:'2027-03-21'})])).json;
  const result=await confirm(edit);expect(result.json.results[0].status).toBe('DONE');expect((db.prepare('SELECT event_date FROM calendar_events').get() as any).event_date).toBe('2027-03-21');
  const del=(await preview([action({tool:'calendar.delete',eventId:event.id})])).json;
  expect(del.plan.actions[0].date).toBe('2027-03-21');await confirm(del);expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
 });
 it('detects changes between preview and confirmation without overwriting them',async()=>{
  await confirm((await preview()).json);const event=db.prepare('SELECT * FROM calendar_events').get() as any;
  const edit=(await preview([action({tool:'calendar.update',eventId:event.id,date:'2027-03-21'})])).json;
  db.prepare("UPDATE calendar_events SET title='Titolo aggiornato' WHERE id=?").run(event.id);
  const result=await confirm(edit);expect(result.json.status).toBe('PARTIAL');expect(result.json.results[0].status).toBe('CONFLICT');
  expect((db.prepare('SELECT title FROM calendar_events').get() as any).title).toBe('Titolo aggiornato');
 });
 it('does not modify an event whose audience changed after preview',async()=>{
  await confirm((await preview()).json);const event=db.prepare('SELECT * FROM calendar_events').get() as any;
  const edit=(await preview([action({tool:'calendar.update',eventId:event.id,date:'2027-03-21'})])).json;
  db.prepare('UPDATE calendar_events SET is_for_all=0 WHERE id=?').run(event.id);
  const result=await confirm(edit);expect(result.json.results[0].status).toBe('CONFLICT');
  expect((db.prepare('SELECT event_date FROM calendar_events').get() as any).event_date).toBe('2027-03-20');
 });
 it('never allows guessing private or other-class event IDs',async()=>{
  const p=(await preview([action({visibility:'SELF'})])).json;await confirm(p);const event=db.prepare('SELECT * FROM calendar_events').get() as any;
  expect((await call('POST','/api/aca/operations',{actions:[action({tool:'calendar.delete',eventId:event.id})]},'peer')).status).toBe(403);
  expect((await call('POST','/api/aca/operations',{actions:[action({tool:'calendar.delete',eventId:event.id})]},'other')).status).toBe(403);
 });
});

describe('Educazione tra pere: privacy, identity and lifecycle',()=>{
 it('keeps anonymous pseudonyms stable and scoped; never leaks raw author IDs',async()=>{
  const t=(await createThread()).json;expect(t.posts[0].author.label).toBe('OP');
  await call('POST',`/api/forum/${t.thread.id}/posts`,{body:'Primo intervento',anonymous:true,forumConsent:true},'peer');
  const first=(await call('GET',`/api/forum/${t.thread.id}`)).json;const person=first.participants.find((p:any)=>!p.isOp);expect(person.label).toMatch(/^Pera educata /);
  await call('POST',`/api/forum/${t.thread.id}/posts`,{body:'Secondo intervento',anonymous:false,forumConsent:true},'peer');
  const second=(await call('GET',`/api/forum/${t.thread.id}`)).json;expect(second.participants.find((p:any)=>p.key===person.key).label).toBe(person.label);
  expect(JSON.stringify(second)).not.toContain('author_id');expect(JSON.stringify(second)).not.toContain('user_id');
  expect(second.posts.filter((p:any)=>p.author.key===person.key).every((p:any)=>p.author.anonymous)).toBe(true);
 });
 it('does not allow cross-class read, reply, resolve, or AI access',async()=>{
  const id=(await createThread()).json.thread.id;
  for(const [method,suffix,body] of [['GET','',undefined],['POST','/posts',{body:'x',forumConsent:true}],['PUT','/state',{resolved:true}],['POST','/ai',{}]] as const){
   expect((await call(method,`/api/forum/${id}${suffix}`,body,'other')).status).toBe(404);
  }expect(run).not.toHaveBeenCalled();
 });
 it('keeps solved and featured answer independent, under author/moderator control',async()=>{
  const id=(await createThread()).json.thread.id;const post=(await call('POST',`/api/forum/${id}/posts`,{body:'Risposta utile',forumConsent:true},'peer')).json.id;
  expect((await call('PUT',`/api/forum/${id}/state`,{resolved:true},'peer')).status).toBe(403);
  await call('PUT',`/api/forum/${id}/state`,{featuredPostId:post});let snap=(await call('GET',`/api/forum/${id}`)).json;
  expect(snap.thread.resolved).toBe(false);expect(snap.thread.featuredPostId).toBe(post);
  await call('PUT',`/api/forum/${id}/state`,{resolved:true},'rep');snap=(await call('GET',`/api/forum/${id}`)).json;
  expect(snap.thread.resolved).toBe(true);expect(snap.posts).toHaveLength(2);expect(snap.thread.featuredPostId).toBe(post);
 });
 it('represents mentions as viewer-specific public aliases',async()=>{
  const id=(await createThread()).json.thread.id;await call('POST',`/api/forum/${id}/posts`,{body:'Sono qui',anonymous:true,forumConsent:true},'peer');
  const participant=(await call('GET',`/api/forum/${id}`)).json.participants.find((p:any)=>!p.isOp);
  await call('POST',`/api/forum/${id}/posts`,{body:`Ciao @${participant.label}`,mentions:[participant.key],forumConsent:true});
  const snap=(await call('GET',`/api/forum/${id}`,undefined,'peer')).json;
  expect(snap.posts.find((p:any)=>p.mentions.length).mentions[0].isMe).toBe(true);
 });
 it('only notifies subscribers according to their own thread preference',async()=>{
  const id=(await createThread()).json.thread.id;await call('PUT',`/api/forum/${id}/subscription`,{mode:'NONE'});
  await call('POST',`/api/forum/${id}/posts`,{body:'Risposta',forumConsent:true},'peer');
  await Promise.resolve();expect(notifications.flatMap(n=>n.ids)).not.toContain('op');
 });
 it('nudges once after 3 days, never auto-resolves and resets on a new reply',async()=>{
  const id=(await createThread()).json.thread.id;db.prepare("UPDATE forum_threads SET last_message_at=datetime('now','-4 days') WHERE id=?").run(id);
  await forumResolutionNudges(env);await forumResolutionNudges(env);expect(notifications.filter(n=>n.ids.includes('op'))).toHaveLength(1);
  expect((db.prepare('SELECT resolved FROM forum_threads').get() as any).resolved).toBe(0);
  await call('POST',`/api/forum/${id}/posts`,{body:'Nuovo messaggio',forumConsent:true},'peer');expect((db.prepare('SELECT nudged_at FROM forum_threads').get() as any).nudged_at).toBeNull();
 });
 it('requires consent before inference and excludes private calendar from shared Perabot',async()=>{
  const id=(await createThread()).json.thread.id;expect((await call('POST',`/api/forum/${id}/ai`,{})).status).toBe(403);expect(run).not.toHaveBeenCalled();
  await consent();await confirm((await preview([action({visibility:'SELF',title:'PROMEMORIA_PRIVATO'})])).json);
  const answer=await call('POST',`/api/forum/${id}/ai`,{publish:true,question:'Che impegni abbiamo?'});
  expect(answer.status).toBe(200);expect(JSON.stringify(run.mock.calls)).not.toContain('PROMEMORIA_PRIVATO');
  const snap=(await call('GET',`/api/forum/${id}`)).json;expect(snap.posts.some((p:any)=>p.isAi&&p.generatedAt&&p.provider)).toBe(true);
  expect(snap.thread.resolved).toBe(false);
 });
});

describe('content retention and bounded processing',()=>{
 it('implements the exact 3 MB boundary and 90/7 day retention',()=>{
  const now=Date.UTC(2026,10,5);expect(Date.parse(attachmentExpiry(3*1024*1024,90,now))-now).toBe(90*86400000);
  expect(Date.parse(attachmentExpiry(3*1024*1024+1,90,now))-now).toBe(7*86400000);
 });
 it('rejects active content, MIME spoofing and malformed audio',()=>{
  expect(()=>validateContent('x.svg','image/svg+xml',new TextEncoder().encode('<svg/>'))).toThrow();
  expect(()=>validateContent('x.pdf','application/pdf',new TextEncoder().encode('%PDF- /JavaScript'))).toThrow();
  expect(()=>validateContent('x.mp3','audio/mpeg',new Uint8Array([1,2,3]))).toThrow();
 });
 it('private imports are not readable by classmates and expiry blocks reads before cron',async()=>{
  const form=new FormData();form.set('file',new File(['Appunti riservati'],'appunti.txt',{type:'text/plain'}));
  const created=await call('POST','/api/content',form);expect(created.status).toBe(201);const id=created.json.attachment.id;
  expect((await call('GET',`/api/content/${id}`,undefined,'peer')).status).toBe(404);
  db.prepare("UPDATE content_attachments SET expires_at='2000-01-01' WHERE id=?").run(id);
  expect((await call('GET',`/api/content/${id}/file`)).status).toBe(410);
  await cleanupContent(env);expect(objects.size).toBe(0);expect((db.prepare('SELECT extracted_text FROM content_attachments').get() as any).extracted_text).toBe('');
 });
 it('removing an expired file leaves its discussion and messages intact',async()=>{
  const id=(await createThread()).json.thread.id;const form=new FormData();form.set('file',new File(['Testo'],'nota.txt',{type:'text/plain'}));form.set('threadId',id);
  const attachment=(await call('POST','/api/content',form)).json.attachment;
  await call('POST',`/api/forum/${id}/posts`,{attachmentIds:[attachment.id],forumConsent:true});
  db.prepare("UPDATE content_attachments SET expires_at='2000-01-01'").run();await cleanupContent(env);
  const snap=(await call('GET',`/api/forum/${id}`)).json;expect(snap.posts).toHaveLength(2);expect(snap.posts.find((p:any)=>p.attachments.length).attachments[0].expired).toBe(true);
 });
});

describe('Optional AI, service consent and hard budgets',()=>{
 it('persists the hold shortcut independently of consent and rejects invalid values',async()=>{
  expect((await call('GET','/api/aca/preferences')).json.preferences.holdToOpen).toBe(true);
  expect((await call('PUT','/api/aca/preferences',{holdToOpen:false})).status).toBe(200);
  const p=(await call('GET','/api/aca/preferences')).json.preferences;
  expect(p.holdToOpen).toBe(false);expect(p.aiEnabled).toBe(true);
  expect((await call('PUT','/api/aca/preferences',{holdToOpen:'yes'})).status).toBe(400);
  await call('PUT','/api/aca/preferences',{holdToOpen:true,acaEnabled:false});
  const q=(await call('GET','/api/aca/preferences')).json.preferences;
  expect(q.holdToOpen).toBe(true);expect(q.acaEnabled).toBe(false);
  expect(run).not.toHaveBeenCalled();
 });
 it('defaults to AI and ACA off for a new account without preferences',async()=>{
  db.prepare("DELETE FROM ai_preferences WHERE user_id='op'").run();
  const p=(await call('GET','/api/aca/preferences')).json.preferences;
  expect(p.aiEnabled).toBe(false);expect(p.acaEnabled).toBe(false);
  expect((await preview()).status).toBe(403);
  expect((await call('POST','/api/ai/chat',{messages:[{role:'user',content:'Ciao'}]})).status).toBe(403);
  expect((await call('POST','/api/aca/task',{feature:'DOCUMENT',question:'Riassumi',context:'Testo'})).status).toBe(403);
  expect(run).not.toHaveBeenCalled();
 });
 it('master off blocks models and calendar writes while ordinary forum remains available',async()=>{
  await consent();const plan=(await preview()).json;
  await call('PUT','/api/aca/preferences',{aiEnabled:false});
  expect((await confirm(plan)).status).toBe(403);
  expect((await createThread()).status).toBe(201);
  expect((await call('POST','/api/aca/task',{feature:'CHAT',question:'Ciao',context:''})).status).toBe(403);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);expect(run).not.toHaveBeenCalled();
 });
 it('ACA and each service are independent opt-ins',async()=>{
  await consent();await call('PUT','/api/aca/preferences',{acaEnabled:false});
  expect((await preview()).status).toBe(403);
  expect((await call('POST','/api/aca/task',{feature:'CHAT',question:'Ciao',context:''})).status).toBe(200);
  await call('PUT','/api/aca/preferences',{acaEnabled:true,services:['CHAT']});
  expect((await call('POST','/api/aca/task',{feature:'DOCUMENT',question:'Riassumi',context:'Testo'})).status).toBe(403);
  expect((await preview()).status).toBe(403);expect(run).toHaveBeenCalledTimes(1);
 });
 it('private summaries require forum service and consent; refusal never invokes a provider',async()=>{
  const id=(await createThread()).json.thread.id;
  await consent();await call('PUT','/api/aca/preferences',{services:['CHAT']});
  expect((await call('POST',`/api/forum/${id}/ai`,{})).status).toBe(403);
  expect(run).not.toHaveBeenCalled();
 });
 it('feature request cap and account budget stop calls before invoking a model',async()=>{
  await consent();env.AI_FEATURE_DAILY_REQUESTS='1';
  const req={feature:'DOCUMENT',question:'Riassumi',context:'Un documento'};
  expect((await call('POST','/api/aca/task',req)).status).toBe(200);
  expect((await call('POST','/api/aca/task',req)).status).toBe(429);expect(run).toHaveBeenCalledTimes(1);
  env.AI_DAILY_TOKEN_BUDGET='1';
  expect((await call('POST','/api/aca/task',{...req,feature:'STUDY_PLAN'})).status).toBe(429);expect(run).toHaveBeenCalledTimes(1);
 });
 it('never escalates to advanced or retries another provider after failure',async()=>{
  await consent();env.AI_ADVANCED_PROVIDER='openai';env.AI_ADVANCED_API_KEY='unused-test-key';env.AI_ADVANCED_MODEL='test';
  const request=vi.spyOn(globalThis,'fetch');run.mockRejectedValueOnce(new Error('provider unavailable'));
  expect((await call('POST','/api/aca/task',{feature:'AGENT',question:'Prepara',context:'Contesto'})).status).toBe(502);
  expect(run).toHaveBeenCalledTimes(1);expect(request).not.toHaveBeenCalled();request.mockRestore();
 });
});

describe('Forum nudge preferences',()=>{
 it('honors the author’s NONE subscription for inactivity notifications',async()=>{
  const id=(await createThread()).json.thread.id;
  db.prepare("UPDATE forum_threads SET last_message_at=datetime('now','-4 days') WHERE id=?").run(id);
  await call('PUT',`/api/forum/${id}/subscription`,{mode:'NONE'});
  await forumResolutionNudges(env);expect(notifications).toHaveLength(0);
  expect((db.prepare('SELECT resolved FROM forum_threads WHERE id=?').get(id) as any).resolved).toBe(0);
 });
});

describe('Optional advanced routing',()=>{
 const request={feature:'AGENT',question:'Prepara una proposta',context:'Solo questo contesto autorizzato'};
 async function advanced(externalConsent:boolean){
  await consent();env.AI_ADVANCED_PROVIDER='openai';env.AI_ADVANCED_MODEL='configured-model';env.AI_ADVANCED_API_KEY='test-only-secret';
  await call('PUT','/api/aca/preferences',{externalConsent,advancedEnabled:true});
 }
 it('keeps the baseline provider when external consent is absent',async()=>{
  await advanced(false);const network=vi.spyOn(globalThis,'fetch');
  try{expect((await call('POST','/api/aca/task',request)).status).toBe(200);expect(network).not.toHaveBeenCalled();expect(run).toHaveBeenCalledTimes(1);}
  finally{network.mockRestore();}
 });
 it('invokes exactly the configured advanced provider after explicit consent',async()=>{
  await advanced(true);const network=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({choices:[{message:{content:'Piano proposto, nessuna azione eseguita.'}}]}),{status:200}));
  try{
   const result=await call('POST','/api/aca/task',request);expect(result.status).toBe(200);
   expect(result.json.provider).toBe('openai · configured-model');expect(network).toHaveBeenCalledTimes(1);expect(run).not.toHaveBeenCalled();
   expect(network.mock.calls[0][0]).toBe('https://api.openai.com/v1/chat/completions');
   expect(JSON.parse(String(network.mock.calls[0][1]?.body)).messages[1].content).toContain(request.context);
   expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
  }finally{network.mockRestore();}
 });
 it('does not retry Workers AI after an advanced provider failure',async()=>{
  await advanced(true);const network=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response('Unavailable',{status:503}));
  try{expect((await call('POST','/api/aca/task',request)).status).toBe(502);expect(network).toHaveBeenCalledTimes(1);expect(run).not.toHaveBeenCalled();}
  finally{network.mockRestore();}
 });
});

describe('Attachment expiration during an in-flight task',()=>{
 it('cannot restore a transcript after scheduled deletion',async()=>{
  await consent();const bytes=new Uint8Array(44+32000);const view=new DataView(bytes.buffer);
  const tag=(at:number,text:string)=>bytes.set(new TextEncoder().encode(text),at);
  tag(0,'RIFF');view.setUint32(4,bytes.length-8,true);tag(8,'WAVE');tag(12,'fmt ');view.setUint32(16,16,true);
  view.setUint16(20,1,true);view.setUint16(22,1,true);view.setUint32(24,16000,true);view.setUint32(28,32000,true);
  view.setUint16(32,2,true);view.setUint16(34,16,true);tag(36,'data');view.setUint32(40,32000,true);
  const form=new FormData();form.set('file',new File([bytes],'Vocale.wav',{type:'audio/wav'}));
  const uploaded=await call('POST','/api/content',form);expect(uploaded.status).toBe(201);const id=uploaded.json.attachment.id;
  let release:(result:{text:string})=>void=()=>{};let started:()=>void=()=>{};
  const entered=new Promise<void>(r=>{started=r;});
  run.mockImplementationOnce(async()=>{started();return await new Promise<{text:string}>(r=>{release=r;});});
  const pending=call('POST',`/api/content/${id}/ai`,{action:'TRANSCRIBE'});await entered;
  db.prepare("UPDATE content_attachments SET expires_at='2000-01-01' WHERE id=?").run(id);await cleanupContent(env);
  release({text:'Questo testo non deve essere conservato.'});expect((await pending).status).toBe(410);
  const row=db.prepare('SELECT transcript,extracted_text,deleted_at FROM content_attachments WHERE id=?').get(id) as any;
  expect(row.transcript).toBeNull();expect(row.extracted_text).toBe('');expect(row.deleted_at).toBeTruthy();expect(objects.size).toBe(0);
 });
});

describe('Automatic-download inbox respects publication and authorization',()=>{
 it('includes only published, visible, unexpired attachments of the current class',async()=>{
  const id=(await createThread()).json.thread.id;
  async function upload(threadId?:string){const form=new FormData();form.set('file',new File(['Testo'],'nota.txt',{type:'text/plain'}));if(threadId)form.set('threadId',threadId);return(await call('POST','/api/content',form)).json.attachment;}
  const draft=await upload(id);await upload();
  const before=await call('GET','/api/content/inbox');expect(before.status).toBe(200);expect(before.json.attachments).toHaveLength(0);
  await call('POST',`/api/forum/${id}/posts`,{attachmentIds:[draft.id],forumConsent:true});
  const delivered=await call('GET','/api/content/inbox',undefined,'peer');expect(delivered.json.attachments).toHaveLength(1);
  expect(delivered.json.attachments[0].attachment.id).toBe(draft.id);
  expect((await call('GET','/api/content/inbox',undefined,'other')).json.attachments).toHaveLength(0);
  const cursor=delivered.json.attachments[0].cursor;
  expect((await call('GET',`/api/content/inbox?cursor=${encodeURIComponent(cursor)}`)).json.attachments).toHaveLength(0);
  db.prepare("UPDATE content_attachments SET expires_at='2000-01-01' WHERE id=?").run(draft.id);
  expect((await call('GET','/api/content/inbox')).json.attachments).toHaveLength(0);
 });
 it('does not expose files on hidden posts or accept malformed cursors',async()=>{
  const id=(await createThread()).json.thread.id;const form=new FormData();form.set('file',new File(['Testo'],'nota.txt',{type:'text/plain'}));form.set('threadId',id);
  const attachment=(await call('POST','/api/content',form)).json.attachment;
  const post=(await call('POST',`/api/forum/${id}/posts`,{attachmentIds:[attachment.id],forumConsent:true})).json.id;
  await call('PUT',`/api/forum/${id}/moderation`,{postId:post,hidden:true},'rep');
  expect((await call('GET','/api/content/inbox')).json.attachments).toHaveLength(0);
  expect((await call('GET','/api/content/inbox?cursor=invalid')).status).toBe(400);
 });
});

describe('Shared proactive calendar proposals',()=>{
 async function proposal(){
  const thread=(await createThread()).json.thread.id;
  await call('POST',`/api/forum/${thread}/posts`,{body:'Confermata verifica di matematica il 20 marzo 2027.',forumConsent:true},'peer');
  const snap=(await call('GET',`/api/forum/${thread}`)).json;
  const post=snap.posts.find((p:any)=>p.author.isMe===false&&p.body.includes('Confermata'));
  run.mockResolvedValue({response:JSON.stringify({events:[{title:'Matematica: verifica',date:'2027-03-20',category:'VERIFICA',time:null,notes:null,sourcePostIds:[post.id]}]})});
  await consent();
  const result=await call('POST',`/api/forum/${thread}/calendar-proposals/analyze`,{});
  expect(result.status).toBe(200);return {thread,p:result.json.proposals[0]};
 }
 const prepare=(p:any,user='op',extra={})=>call('POST','/api/aca/operations',{sharedProposalId:p.id,digest:p.digest,...extra},user);
 it('is visible to the class without personal keys, while disabled users cannot confirm',async()=>{
  const {thread,p}=await proposal();expect(p.status).toBe('PENDING');
  await call('PUT','/api/aca/preferences',{aiEnabled:false},'peer');
  expect((await call('GET',`/api/forum/${thread}/calendar-proposals`,undefined,'peer')).json.proposals[0].id).toBe(p.id);
  expect((await prepare(p,'peer')).status).toBe(403);
  expect((await call('GET',`/api/forum/${thread}/calendar-proposals`,undefined,'other')).status).toBe(404);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
 });
 it('accepts once across two users and exposes one shared accepted state',async()=>{
  const {thread,p}=await proposal();
  const a=(await prepare(p)).json;const b=(await prepare(p,'peer')).json;
  const results=await Promise.all([confirm(a),confirm(b,'peer')]);
  expect(results.map(r=>r.status).sort()).toEqual([200,409]);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(1);
  const states=await Promise.all(['op','peer'].map(u=>call('GET',`/api/forum/${thread}/calendar-proposals`,undefined,u)));
  expect(states.every(r=>r.json.proposals[0].status==='ACCEPTED')).toBe(true);
  expect((await prepare(p,'rep')).status).toBe(409);
 });
 it('executes the stored shared plan, ignoring injected replacement actions',async()=>{
  const {p}=await proposal();const op=(await prepare(p,'op',{actions:[action({title:'Injected',date:'2030-01-01'})]})).json;
  await confirm(op);expect((db.prepare('SELECT title FROM calendar_events').get() as any).title).toBe('Matematica: verifica');
 });
 it('rejects a preview when new conversation messages invalidate its sources',async()=>{
  const {thread,p}=await proposal();const op=(await prepare(p)).json;
  await call('POST',`/api/forum/${thread}/posts`,{body:'Correzione: la verifica non è più il 20 marzo.',forumConsent:true},'peer');
  expect((await confirm(op)).status).toBe(409);
  expect((await call('GET',`/api/forum/${thread}/calendar-proposals`)).json.proposals[0].status).toBe('STALE');
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
 });
 it('refuses hidden source messages and hidden discussions at confirmation',async()=>{
  const {thread,p}=await proposal();const op=(await prepare(p)).json;
  const sourceId=p.sourcePostIds.find((id:string)=>id!==thread);
  await call('PUT',`/api/forum/${thread}/moderation`,{postId:sourceId,hidden:true},'rep');
  expect((await confirm(op)).status).toBe(409);
  await call('PUT',`/api/forum/${thread}/moderation`,{hidden:true},'rep');
  expect((await prepare(p)).status).toBe(404);
 });
 it('deduplicates automatic scans and never invokes a model when the opt-in is off',async()=>{
  const {thread}=await proposal();expect(run).toHaveBeenCalledTimes(1);
  await Promise.all(['op','peer'].map(u=>call('POST',`/api/forum/${thread}/calendar-proposals/analyze`,{},u)));
  expect(run).toHaveBeenCalledTimes(1);
  await call('PUT','/api/aca/preferences',{services:['FORUM','CALENDAR']});
  expect((await call('POST',`/api/forum/${thread}/calendar-proposals/analyze`,{})).status).toBe(403);
  expect(run).toHaveBeenCalledTimes(1);
 });
 it('avoids inference for ordinary conversation and never invents missing dates',async()=>{
  const thread=(await createThread()).json.thread.id;await consent();
  expect((await call('POST',`/api/forum/${thread}/calendar-proposals/analyze`,{})).json.proposals).toEqual([]);
  expect(run).not.toHaveBeenCalled();
 });
 it('checks conversation freshness inside the same transaction as the calendar writes',async()=>{
  const {thread,p}=await proposal();const op=(await prepare(p)).json;
  const batch=env.DB.batch.bind(env.DB);
  env.DB.batch=async statements=>{
   db.prepare("INSERT INTO forum_posts(id,thread_id,parent_id,author_id,body) VALUES ('late-correction',?,?,?,?)")
    .run(thread,thread,'peer','La data è stata annullata.');
   return batch(statements);
  };
  expect((await confirm(op)).status).toBe(409);
  expect(db.prepare('SELECT * FROM calendar_events').all()).toHaveLength(0);
  expect((db.prepare('SELECT status FROM forum_calendar_proposals WHERE id=?').get(p.id) as any).status).toBe('PENDING');
 });
});

describe('Classic chatbot attachment consent',()=>{
 const request=(attachmentServices:string[])=>({messages:[{role:'user',content:'Riassumi il testo allegato'}],attachmentServices});
 it('blocks file text before invoking any provider when ACA or document consent is off',async()=>{
  await consent();await call('PUT','/api/aca/preferences',{attachmentConsent:false});
  expect((await call('POST','/api/ai/chat',request(['DOCUMENTS']))).status).toBe(403);
  await call('PUT','/api/aca/preferences',{attachmentConsent:true,acaEnabled:false});
  expect((await call('POST','/api/ai/chat',request(['DOCUMENTS']))).status).toBe(403);
  expect(run).not.toHaveBeenCalled();
 });
 it('requires the individual audio service and audio consent for transcripts in classic chat',async()=>{
  await consent();await call('PUT','/api/aca/preferences',{audioConsent:false});
  expect((await call('POST','/api/ai/chat',request(['AUDIO']))).status).toBe(403);
  await call('PUT','/api/aca/preferences',{audioConsent:true,services:['CHAT','DOCUMENTS']});
  expect((await call('POST','/api/ai/chat',request(['AUDIO']))).status).toBe(403);
  expect(run).not.toHaveBeenCalled();
 });
 it('accepts permitted file text without a personal key and rejects forged service names',async()=>{
  await consent();
  expect((await call('POST','/api/ai/chat',request(['DOCUMENTS']))).status).toBe(200);
  expect(run).toHaveBeenCalledTimes(1);
  expect((await call('POST','/api/ai/chat',request(['ADMIN']))).status).toBe(400);
  expect(run).toHaveBeenCalledTimes(1);
 });
});

describe('Android voice containers and personal brief audience',()=>{
 it('reads M4A duration after a 64-bit mdat box',()=>{
  const bytes=new Uint8Array(64);const v=new DataView(bytes.buffer);
  const name=(at:number,s:string)=>bytes.set(new TextEncoder().encode(s),at);
  v.setUint32(0,1);name(4,'mdat');v.setBigUint64(8,16n);
  v.setUint32(16,48);name(20,'moov');v.setUint32(24,40);name(28,'mvhd');
  v.setUint32(44,1000);v.setUint32(48,5000);
  expect(audioDuration(bytes,'audio/mp4')).toBe(5);
  v.setBigUint64(8,99999n);expect(audioDuration(bytes,'audio/mp4')).toBe(null);
 });
 it('excludes events created for other recipients from personal study and brief',async()=>{
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(new Date());
  await consent();await call('PUT','/api/aca/preferences',{nudgesEnabled:true});
  db.prepare('INSERT INTO calendar_events(id,title,event_date,category,is_for_all,created_by,class_id,visible_to_user_ids_json) VALUES (?,?,?,?,?,?,?,?)')
   .run('others-exam','Interrogazione di un compagno',today,'INTERROGAZIONE',0,'op','C1','["peer"]');
  expect((await call('GET','/api/aca/nudges')).json.nudges).toEqual([]);
  await call('POST','/api/aca/task',{feature:'STUDY_PLAN',question:'Organizza il mio ripasso'});
  expect(JSON.stringify(run.mock.calls)).not.toContain('Interrogazione di un compagno');
 });
});

describe('Visible class oral-exam calendars are not everyone’s personal obligations',()=>{
 it('uses poll assignments and annotates public events without hiding them from the class',async()=>{
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(new Date());
  const tomorrow=new Date(Date.parse(today+'T00:00:00Z')+86400000).toISOString().slice(0,10);
  db.prepare("INSERT INTO interrogation_grids(id,subject,is_published,class_id) VALUES ('study-grid','Storia',1,'C1')").run();
  db.prepare('INSERT INTO interrogation_slots(id,grid_id,slot_date) VALUES (?,?,?)').run('peer-slot','study-grid',today);
  db.prepare('INSERT INTO interrogation_slots(id,grid_id,slot_date) VALUES (?,?,?)').run('my-slot','study-grid',tomorrow);
  db.prepare('INSERT INTO interrogation_assignments(id,slot_id,student_id) VALUES (?,?,?)').run('a-peer','peer-slot','peer');
  db.prepare('INSERT INTO interrogation_assignments(id,slot_id,student_id) VALUES (?,?,?)').run('a-me','my-slot','op');
  const insert=db.prepare('INSERT INTO calendar_events(id,title,event_date,category,is_for_all,created_by,class_id,notes,poll_grid_id) VALUES (?,?,?,?,?,?,?,?,?)');
  insert.run('public-peer','INTERROGAZIONE_ALTRUI_PUBBLICA',today,'INTERROGAZIONE',1,'op','C1','Studenti: Nome peer','study-grid');
  insert.run('public-mine','MIA_INTERROGAZIONE_PUBBLICA',tomorrow,'INTERROGAZIONE',1,'rep','C1','Studenti: Nome op','study-grid');
  insert.run('legacy-peer','INTERROGAZIONE_ALTRUI_LEGACY',today,'INTERROGAZIONE',1,'rep','C1','Studenti: Nome peer',null);
  const events=(await call('GET','/api/calendar')).json.events;
  expect(events).toHaveLength(3);
  expect(events.find((e:any)=>e.id==='public-peer').isRelevantToMe).toBe(false);
  expect(events.find((e:any)=>e.id==='public-mine').isRelevantToMe).toBe(true);
  expect(events.find((e:any)=>e.id==='legacy-peer').isRelevantToMe).toBe(false);
  await consent();await call('PUT','/api/aca/preferences',{nudgesEnabled:true});
  const nudges=(await call('GET','/api/aca/nudges')).json.nudges;
  expect(nudges.map((n:any)=>n.eventId)).toEqual(['public-mine']);
  await call('POST','/api/aca/task',{feature:'STUDY_PLAN',question:'Organizza il mio ripasso'});
  expect(JSON.stringify(run.mock.calls)).toContain('MIA_INTERROGAZIONE_PUBBLICA');
  expect(JSON.stringify(run.mock.calls)).not.toContain('INTERROGAZIONE_ALTRUI');
 });
});
