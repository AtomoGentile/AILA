import {beforeEach,describe,it,expect,vi} from 'vitest';
import worker from '../src/index';
import {createD1} from './d1shim';
import {signJWT} from '../src/auth';
import type {Env} from '../src/types';
let env:Env;let db:ReturnType<typeof createD1>['db'];let token:string;const run=vi.fn();
const ctx={waitUntil(){},passThroughOnException(){}} as never;
beforeEach(async()=>{
 const d=createD1();db=d.db;env={DB:d.d1,JWT_SECRET:'test',AI:{run}} as unknown as Env;run.mockReset();
 db.exec("INSERT INTO classes(id,label) VALUES ('C1','3 A'),('C2','3 B')");
 for(const [id,cls] of [['me','C1'],['peer','C1'],['other','C2']])db.prepare("INSERT INTO users(id,first_name,last_name,username,password_hash,role,class_id) VALUES (?, 'Nome', ?, ?, 'unused','STUDENT',?)").run(id,id,id,cls);
 token=await signJWT({sub:'me',username:'me',role:'STUDENT',classId:'C1'},'test');
});
async function call(path:string,auth=true){
 const res=await worker.fetch(new Request('https://test'+path,{headers:auth?{Authorization:`Bearer ${token}`}:{}}),env,ctx);
 return {status:res.status,json:await res.json() as any};
}
function thread(id:string,author='peer',resolved=0,cls='C1',title='Domanda di storia'){
 db.prepare('INSERT INTO forum_threads(id,class_id,author_id,title,resolved) VALUES (?,?,?,?,?)').run(id,cls,author,title,resolved);
 db.prepare('INSERT INTO forum_posts(id,thread_id,author_id,body) VALUES (?,?,?,?)').run(id,id,author,'Ripasso della verifica');
}
describe('class search and forum filters',()=>{
 it('searches all three sources without AI consent or quota and keeps class content isolated',async()=>{
  db.exec("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (61,'Verifica di storia','2026-11-07','61.pdf'); INSERT INTO proposals(id,author_id,title,description,class_id,is_anonymous,status) VALUES ('board','peer','Gruppo di studio','Prepariamo la verifica di storia','C1',1,'CHIUSA'),('secret','other','Verifica di storia','Altra classe','C2',1,'NUOVA')");
  thread('thread');thread('other-thread','other',0,'C2');thread('hidden');db.exec("UPDATE forum_threads SET hidden=1 WHERE id='hidden'");
  const response=await call('/api/aca/class-search?q=verifica%20storia');
  expect(response.status).toBe(200);
  expect(new Set(response.json.hits.map((h:any)=>h.id))).toEqual(new Set(['61','board','thread']));
  expect(response.json.hits.find((h:any)=>h.kind==='FORUM').id).toBe('thread');
  expect(response.json.hits.every((h:any)=>!('author_id' in h)&&!('authorName' in h))).toBe(true);
  expect(run).not.toHaveBeenCalled();
 });
 it('matches circular numbers, multiple terms and literal wildcard characters',async()=>{
  db.exec("INSERT INTO circulars(number,title,publish_date,r2_pdf_key) VALUES (61,'Verifica di storia','2026-11-07','61.pdf'),(7,'Avviso','2026-11-07','7.pdf')");
  expect((await call('/api/aca/class-search?q=circolare%20n.%2061')).json.hits.map((h:any)=>h.id)).toEqual(['61']);
  expect((await call('/api/aca/class-search?q=7')).json.hits.map((h:any)=>h.id)).toEqual(['7']);
  expect((await call('/api/aca/class-search?q=storia%20verifica')).json.hits.map((h:any)=>h.id)).toEqual(['61']);
  expect((await call('/api/aca/class-search?q=%25_')).json.hits).toEqual([]);
 });
 it('does not search hidden replies and validates queries and authentication',async()=>{
  thread('visible','peer',0,'C1','Domanda');
  db.exec("INSERT INTO forum_posts(id,thread_id,author_id,body,hidden) VALUES ('hidden-reply','visible','peer','Segretissimo',1)");
  expect((await call('/api/aca/class-search?q=Segretissimo')).json.hits).toEqual([]);
  expect((await call('/api/aca/class-search?q=a')).status).toBe(400);
  expect((await call('/api/aca/class-search?q='+ 'x'.repeat(121))).status).toBe(400);
  expect((await call('/api/aca/class-search?q=storia',false)).status).toBe(401);
 });
 it('applies discussion filters before pagination and excludes other classes and hidden threads',async()=>{
  for(let i=0;i<53;i++)thread(`closed-${i}`,'peer',1);
  thread('open-mine','me');thread('open-peer');thread('other','other',0,'C2');thread('hidden');db.exec("UPDATE forum_threads SET hidden=1 WHERE id='hidden'");
  const open=await call('/api/forum?filter=OPEN');
  expect(new Set(open.json.threads.map((t:any)=>t.id))).toEqual(new Set(['open-mine','open-peer']));
  expect(open.json.hasMore).toBe(false);
  expect((await call('/api/forum?filter=MINE')).json.threads.map((t:any)=>t.id)).toEqual(['open-mine']);
  const resolved=await call('/api/forum?filter=RESOLVED');
  expect(resolved.json.threads).toHaveLength(50);expect(resolved.json.hasMore).toBe(true);
  expect((await call('/api/forum?filter=RESOLVED&offset=50')).json.threads).toHaveLength(3);
  expect((await call('/api/forum?filter=WRONG')).status).toBe(400);
 });
});
