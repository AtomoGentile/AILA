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
 it('filters class-wide events using recipient notes and keeps explicit recipients authoritative',async()=>{
  for(const [id,notes,recipients] of [['peer-note','Interrogati: Nome peer',null],['mine-note','Studenti: Nome u',null],['generic','Ripassare il capitolo 2',null],['explicit','Studenti: Nome peer','["u"]']] as const){
   event(id,recipients);db.prepare('UPDATE calendar_events SET notes=? WHERE id=?').run(notes,id);
  }
  db.exec("INSERT INTO interrogation_grids(id,subject,class_id) VALUES ('grid','Storia','C1'); UPDATE calendar_events SET poll_grid_id='grid' WHERE id='explicit'");
  expect(new Set((await call()).json.items.map((i:any)=>i.sourceId))).toEqual(new Set(['mine-note','generic','explicit']));
 });
 it('uses class-specific circular badges and excludes fallback analyses',async()=>{
  for(const [n,per,fallback] of [[1,null,0],[2,JSON.stringify({C1:{badge:'NOT_RELEVANT',note:''}}),0],[3,null,1]] as const){
   db.prepare("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (?,?,'2026-11-06',?)").run(n,`Circolare ${n}`,`${n}.pdf`);
   db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary,per_class_json,is_fallback) VALUES (?,'RELEVANT','Riguarda la classe',?,?)").run(n,per,fallback);
  }
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['1']);
 });
 it('includes open and completed polls, including answered ones, while respecting the audience',async()=>{
  for(const id of ['open','submitted','expired','draft','not-mine','calculated'])db.prepare("INSERT INTO interrogation_grids(id,subject,is_published,class_id,created_at) VALUES (?,?,1,'C1','2026-11-06 06:00:00')").run(id,id);
  db.exec("INSERT INTO interrogation_submissions(grid_id,student_id) VALUES ('submitted','u'); UPDATE interrogation_grids SET closes_at='2026-11-05T12:00:00Z' WHERE id='expired'; UPDATE interrogation_grids SET is_published=0 WHERE id='draft'; UPDATE interrogation_grids SET audience_json='[\"peer\"]' WHERE id='not-mine'; INSERT INTO interrogation_calculations(grid_id,run_id,algorithm_version) VALUES ('calculated','run','v1')");
  ranking('answered');db.exec("INSERT INTO ranking_poll_options(id,poll_id,label,position) VALUES ('option','answered','Opzione',0); INSERT INTO ranking_poll_answers(poll_id,option_id,user_id,rank) VALUES ('answered','option','u',1)");
  const items=(await call()).json.items;
  expect(new Set(items.map((i:any)=>i.sourceId))).toEqual(new Set(['open','submitted','calculated','answered']));
  expect(items.find((i:any)=>i.sourceId==='calculated').isResult).toBe(true);
  expect(items.find((i:any)=>i.sourceId==='submitted').isResult).toBe(false);
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
 it('keeps open board proposals after voting and excludes closed proposals',async()=>{
  for(const id of ['new','voted','closed'])db.prepare("INSERT INTO proposals(id,author_id,title,description,class_id,created_at) VALUES (?,'peer',?,'Descrizione','C1','2026-11-06 06:00:00')").run(id,id);
  db.exec("INSERT INTO proposal_votes(proposal_id,user_id,vote_type) VALUES ('voted','u',1); UPDATE proposals SET status='CHIUSA' WHERE id='closed'");
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['new','voted']);
 });
 it('leaves room for other sources when several events are urgent',async()=>{
  for(let i=0;i<4;i++)event(`e${i}`);ranking('rank');
  const items=selectBriefItems(await briefCandidates(env,'C1','u'),new Set(),4);expect(items).toHaveLength(4);expect(items.some(i=>i.kind==='RANKING_POLL')).toBe(true);
 });
 it('shows every unseen relevant or potential circular, with no summary, and acknowledges a section visit per account',async()=>{
  for(let n=10;n<19;n++){
   db.prepare("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (?,?,'2025-01-01',?)").run(n,`Titolo ${n}`,`${n}.pdf`);
   db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary) VALUES (?,?,'Riassunto da non mostrare')").run(n,n===18?'NOT_RELEVANT':n%2?'POTENTIAL':'RELEVANT');
  }
  event('keep');
  let items=(await call()).json.items;
  expect(items.filter((i:any)=>i.kind==='CIRCULAR')).toHaveLength(8);
  expect(items.find((i:any)=>i.sourceId==='10')).toMatchObject({title:'Titolo 10',message:'',actionLabel:'',date:null});
  expect((await call('/api/aca/brief/circulars/seen',{})).status).toBe(200);
  expect((await call()).json.items.map((i:any)=>i.sourceId)).toEqual(['keep']);
  expect((await call('/api/aca/brief',undefined,'peer')).json.items.filter((i:any)=>i.kind==='CIRCULAR')).toHaveLength(8);
  db.exec("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (19,'Nuova','2026-11-06','19.pdf'); INSERT INTO circular_ai_analysis(circular_number,badge,summary) VALUES (19,'POTENTIAL','Nuova analisi')");
  items=(await call()).json.items;expect(items.some((i:any)=>i.sourceId==='19')).toBe(true);
  const sql=readFileSync(new URL('../migrations/031_brief_sources.sql',import.meta.url),'utf8');db.exec(sql);db.exec(sql);
 });
 it('uses the same class filter for foreground and background circular notifications',async()=>{
  for(let n=1;n<=55;n++){
   db.prepare("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (?,?,'2026-11-06',?)").run(n,`Titolo ${n}`,`${n}.pdf`);
   db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary,tier,per_class_json) VALUES (?,'RELEVANT','Riassunto',2,?)").run(n,JSON.stringify({C1:{badge:n===55?'POTENTIAL':'NOT_RELEVANT',note:''},C2:{badge:'NOT_RELEVANT',note:''}}));
  }
  const newer=(await call('/api/circulars/newer?after=0')).json.circulars;
  expect(newer.map((c:any)=>c.number)).toEqual([55]);
  expect((await call('/api/circulars/newer?after=0',undefined,'other')).json.circulars).toEqual([]);
  const list=(await call('/api/circulars?limit=100')).json.circulars;
  expect(list.filter((c:any)=>c.notificationEligible).map((c:any)=>c.number)).toEqual([55]);
 });
 it('includes closed ranking results, current seats and preferences, and public new discussions',async()=>{
  ranking('closed');db.exec("UPDATE ranking_polls SET is_closed=1 WHERE id='closed'; INSERT INTO seat_map_history(id,map_index,layout_json,class_id) VALUES ('map',1,'[]','C1'),('other-map',1,'[]','C2'); UPDATE classes SET preferences_open=1 WHERE id='C1'; INSERT INTO forum_threads(id,class_id,author_id,title) VALUES ('news','C1','peer','Discussione nuova'); INSERT INTO forum_posts(id,thread_id,author_id,body) VALUES ('news','news','peer','Testo privato non nel Brief')");
  const items=(await call()).json.items;
  expect(items.find((i:any)=>i.sourceId==='closed')).toMatchObject({kind:'RANKING_POLL',isResult:true,actionLabel:'Vedi i risultati'});
  expect(items.filter((i:any)=>i.kind==='SEATMAP').map((i:any)=>i.sourceId).sort()).toEqual(['map','preferences:C1']);
  expect(items.find((i:any)=>i.sourceId==='news')).toMatchObject({kind:'FORUM',title:'Discussione nuova'});
  db.exec("INSERT INTO forum_subscriptions(thread_id,user_id,mode) VALUES ('news','u','NONE')");
  expect((await call()).json.items.some((i:any)=>i.sourceId==='news')).toBe(false);
 });
 it('is optional and its migration is additive and repeatable',async()=>{
  const sql=readFileSync(new URL('../migrations/030_aila_brief.sql',import.meta.url),'utf8');db.exec(sql);db.exec(sql);
  db.exec("UPDATE ai_preferences SET nudges_enabled=0 WHERE user_id='u'");event('e');expect((await call()).json.items).toEqual([]);expect(run).not.toHaveBeenCalled();
 });
});
