import {beforeEach,afterEach,describe,expect,it,vi} from 'vitest';
import {readFileSync} from 'node:fs';
import worker from '../src/index';
import {createD1} from './d1shim';
import {signJWT} from '../src/auth';
import {saveAiPreferences,AI_SERVICES} from '../src/services/aiRouter';
import {briefCandidates,selectBriefItems} from '../src/services/brief';
import type {Env} from '../src/types';
let env:Env;let db:ReturnType<typeof createD1>['db'];let tokens:Record<string,string>;const run=vi.fn();
const ctx={waitUntil(){},passThroughOnException(){}} as never;
beforeEach(async()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-06T08:00:00Z'));run.mockReset();
 const d=createD1();db=d.db;env={DB:d.d1,JWT_SECRET:'test',AI:{run}} as unknown as Env;tokens={};
 db.exec("INSERT INTO classes(id,label) VALUES ('C1','3 B'),('C2','5 A')");
 for(const [id,cls] of [['u','C1'],['peer','C1'],['other','C2']]){
  db.prepare('INSERT INTO users(id,first_name,last_name,username,password_hash,role,class_id) VALUES (?,?,?,?,?,?,?)').run(id,'Nome',id,id,'unused','STUDENT',cls);
  tokens[id]=await signJWT({sub:id,username:id,role:'STUDENT',classId:cls},'test');
  await saveAiPreferences(env,id,{aiEnabled:true,acaEnabled:true,holdToOpen:true,services:AI_SERVICES,cloudConsent:false,externalConsent:false,attachmentConsent:false,audioConsent:false,nudgesEnabled:true,advancedEnabled:false});
 }
});
afterEach(()=>vi.useRealTimers());
async function call(path='/api/aca/brief',body?:unknown,user='u'){
 const response=await worker.fetch(new Request('https://test'+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+tokens[user],'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)}),env,ctx);
 return {status:response.status,json:await response.json() as any};
}
function event(id:string,recipients:string|null=null){db.prepare("INSERT INTO calendar_events(id,title,event_date,category,class_id,is_for_all,visible_to_user_ids_json) VALUES (?,?,'2026-11-06','VERIFICA','C1',1,?)").run(id,id,recipients);}
function ranking(id:string,audience:string|null=null){db.prepare("INSERT INTO ranking_polls(id,class_id,question,created_at,audience_json) VALUES (?,'C1',?,'2026-11-06 07:00:00',?)").run(id,id,audience);}
function thread(){
 db.exec("INSERT INTO forum_threads(id,class_id,author_id,title) VALUES ('t','C1','peer','Aiuto con i limiti'); INSERT INTO forum_participants(thread_id,user_id,public_key,ordinal,anonymous) VALUES ('t','u','alias-u',1,1); INSERT INTO forum_subscriptions(thread_id,user_id,mode) VALUES ('t','u','REPLIES')");
 db.exec("INSERT INTO forum_posts(id,thread_id,author_id,body,created_at) VALUES ('mine','t','u','Domanda','2026-11-06 06:00:00'); INSERT INTO forum_posts(id,thread_id,author_id,parent_id,body,created_at) VALUES ('r1','t','peer','mine','Testo della risposta','2026-11-06 07:00:00')");
}
describe('AILA brief',()=>{
 it('includes different sources without running a model',async()=>{
  event('e1');event('e2');event('e3');ranking('rank');thread();
  db.exec("INSERT INTO proposals(id,author_id,title,description,class_id,created_at) VALUES ('prop','peer','Nuova proposta','Descrizione','C1','2026-11-06 06:00:00')");
  const result=await call();expect(result.status).toBe(200);
  expect(new Set(result.json.items.map((i:any)=>i.kind))).toEqual(new Set(['EVENT','RANKING_POLL','FORUM','PROPOSAL']));
  expect(result.json.items.find((i:any)=>i.kind==='FORUM').message).not.toContain('Testo della risposta');expect(run).not.toHaveBeenCalled();
 });
 it('respects class and personal event recipients',async()=>{
  event('mine','["u"]');event('peer-event','["peer"]');ranking('not-mine','["peer"]');
  db.exec("INSERT INTO proposals(id,author_id,title,description,class_id,created_at) VALUES ('other-prop','other','Altra classe','Segreto','C2','2026-11-06 06:00:00')");
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['mine']);
 });
 it('uses class-specific circular badges and excludes fallback analyses',async()=>{
  for(const [n,per,fallback] of [[1,null,0],[2,JSON.stringify({C1:{badge:'NOT_RELEVANT',note:''}}),0],[3,null,1]] as const){
   db.prepare("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (?,?,'2026-11-06',?)").run(n,`Circolare ${n}`,`${n}.pdf`);
   db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary,per_class_json,is_fallback) VALUES (?,'RELEVANT','Riguarda la classe',?,?)").run(n,per,fallback);
  }
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['1']);
 });
 it('excludes completed, expired, unpublished and non-target polls',async()=>{
  for(const id of ['open','submitted','expired','draft','not-mine','calculated'])db.prepare("INSERT INTO interrogation_grids(id,subject,is_published,class_id,created_at) VALUES (?,?,1,'C1','2026-11-06 06:00:00')").run(id,id);
  db.exec("INSERT INTO interrogation_submissions(grid_id,student_id) VALUES ('submitted','u'); UPDATE interrogation_grids SET closes_at='2026-11-05T12:00:00Z' WHERE id='expired'; UPDATE interrogation_grids SET is_published=0 WHERE id='draft'; UPDATE interrogation_grids SET audience_json='[\"peer\"]' WHERE id='not-mine'; INSERT INTO interrogation_calculations(grid_id,run_id,algorithm_version) VALUES ('calculated','run','v1')");
  ranking('answered');db.exec("INSERT INTO ranking_poll_options(id,poll_id,label,position) VALUES ('option','answered','Opzione',0); INSERT INTO ranking_poll_answers(poll_id,option_id,user_id,rank) VALUES ('answered','option','u',1)");
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['open']);
 });
 it('honors subscriptions, direct replies, mentions and moderation',async()=>{
  thread();expect((await call()).json.items).toHaveLength(1);
  db.exec("UPDATE forum_subscriptions SET mode='NONE'");expect((await call()).json.items).toEqual([]);
  db.exec("UPDATE forum_subscriptions SET mode='REPLIES'; UPDATE forum_posts SET hidden=1 WHERE id='r1'");expect((await call()).json.items).toEqual([]);
  db.exec("UPDATE forum_posts SET hidden=0,parent_id=NULL WHERE id='r1'");expect((await call()).json.items).toEqual([]);
  db.exec("UPDATE forum_posts SET mentions_json='[\"alias-u\"]' WHERE id='r1'");expect((await call()).json.items).toHaveLength(1);
  db.exec("UPDATE forum_threads SET hidden=1");expect((await call()).json.items).toEqual([]);
 });
 it('dismisses only for the owner and resurfaces new source revisions',async()=>{
  thread();const first=(await call()).json.items[0];const path=`/api/aca/brief/${encodeURIComponent(first.id)}/dismiss`;
  expect((await call(path,{revision:first.revision},'other')).status).toBe(404);
  expect((await call(path,{revision:'forged'})).status).toBe(400);
  expect((await call(path,{revision:first.revision})).status).toBe(200);expect((await call(path,{revision:first.revision})).status).toBe(200);
  expect((await call()).json.items).toEqual([]);
  db.exec("INSERT INTO forum_posts(id,thread_id,author_id,parent_id,body,created_at) VALUES ('r2','t','peer','mine','Nuova risposta','2026-11-06 07:30:00')");
  const next=(await call()).json.items[0];expect(next.id).toBe(first.id);expect(next.revision).not.toBe(first.revision);
 });
 it('keeps event dismissal compatible with older clients and private to the owner',async()=>{
  event('shared');const item=(await call()).json.items[0];
  expect((await call(`/api/aca/brief/${encodeURIComponent(item.id)}/dismiss`,{revision:item.revision})).status).toBe(200);
  expect((await call('/api/aca/nudges')).json.nudges).toEqual([]);
  expect((await call('/api/aca/brief',undefined,'peer')).json.items.map((i:any)=>i.sourceId)).toEqual(['shared']);
 });
 it('excludes proposals already voted on or closed',async()=>{
  for(const id of ['new','voted','closed'])db.prepare("INSERT INTO proposals(id,author_id,title,description,class_id,created_at) VALUES (?,'peer',?,'Descrizione','C1','2026-11-06 06:00:00')").run(id,id);
  db.exec("INSERT INTO proposal_votes(proposal_id,user_id,vote_type) VALUES ('voted','u',1); UPDATE proposals SET status='CHIUSA' WHERE id='closed'");
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['new']);
 });
 it('leaves room for other sources when several events are urgent',async()=>{
  for(let i=0;i<4;i++)event(`e${i}`);ranking('rank');
  const items=selectBriefItems(await briefCandidates(env,'C1','u'),new Set());expect(items).toHaveLength(4);expect(items.some(i=>i.kind==='RANKING_POLL')).toBe(true);
 });
 it('is optional and its migration is additive and repeatable',async()=>{
  const sql=readFileSync(new URL('../migrations/030_aila_brief.sql',import.meta.url),'utf8');db.exec(sql);db.exec(sql);
  db.exec("UPDATE ai_preferences SET nudges_enabled=0 WHERE user_id='u'");event('e');expect((await call()).json.items).toEqual([]);expect(run).not.toHaveBeenCalled();
 });
});
