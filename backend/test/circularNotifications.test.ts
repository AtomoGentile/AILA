import { beforeEach, expect, it, vi } from 'vitest';
import { createD1 } from './d1shim';
import { notifySummarizedCirculars } from '../src/services/spaggiari';
import type { Env } from '../src/types';
const send = vi.hoisted(() => vi.fn());
vi.mock('../src/services/fcm', () => ({ notifyClass: send }));
beforeEach(() => { send.mockReset(); });
it('attende il riassunto server, conserva i fallimenti e non ripete notifiche inviate', async () => {
  const { db, d1 } = createD1();
  const env = { DB: d1 } as Env;
  db.exec("INSERT INTO circulars (number,title,publish_date,r2_pdf_key,notification_pending) VALUES (1,'Titolo','2026-10-30','circulars/1.pdf',1)");
  await notifySummarizedCirculars(env);
  expect(send).not.toHaveBeenCalled();
  db.exec("INSERT INTO circular_ai_analysis (circular_number,badge,summary,tier) VALUES (1,'RELEVANT','Riassunto locale',1)");
  await notifySummarizedCirculars(env);
  expect(send).not.toHaveBeenCalled();
  db.exec("UPDATE circular_ai_analysis SET tier=2, summary='Riassunto server' WHERE circular_number=1");
  send.mockRejectedValueOnce(new Error('Rete indisponibile'));
  await notifySummarizedCirculars(env);
  expect(db.prepare('SELECT notification_pending FROM circulars').get()?.notification_pending).toBe(1);
  send.mockResolvedValue(undefined);
  await notifySummarizedCirculars(env);
  expect(db.prepare('SELECT notification_pending FROM circulars').get()?.notification_pending).toBe(0);
  expect(send).toHaveBeenCalledTimes(2);
  await notifySummarizedCirculars(env);
  expect(send).toHaveBeenCalledTimes(2);
  db.close();
});
it('notifica solo le classi per cui le circolari sono riguardanti o potenziali', async () => {
  const {db,d1}=createD1();const env={DB:d1} as Env;
  db.exec("INSERT INTO classes(id,label) VALUES ('C1','3 A'),('C2','3 B')");
  for(const [n,per] of [[1,{C1:{badge:'RELEVANT'},C2:{badge:'NOT_RELEVANT'}}],[2,{C1:{badge:'NOT_RELEVANT'},C2:{badge:'POTENTIAL'}}],[3,{}]] as const){
    db.prepare("INSERT INTO circulars(number,title,publish_date,r2_pdf_key,notification_pending) VALUES (?,?,'2026-11-06',?,1)").run(n,`Titolo ${n}`,`${n}.pdf`);
    db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary,tier,per_class_json) VALUES (?,'NOT_RELEVANT','Riassunto',2,?)").run(n,JSON.stringify(per));
  }
  await notifySummarizedCirculars(env);
  expect(send.mock.calls.map(args=>({classId:args[4],body:args[2],number:args[3].circular_number}))).toEqual([
    {classId:'C1',body:'Circolare n. 1: Titolo 1',number:'1'},
    {classId:'C2',body:'Circolare n. 2: Titolo 2',number:'2'}
  ]);
  expect(db.prepare('SELECT COUNT(*) AS n FROM circulars WHERE notification_pending=1').get()?.n).toBe(0);
  await notifySummarizedCirculars(env);expect(send).toHaveBeenCalledTimes(2);
  db.close();
});
it('un retry per una classe non ripete notifiche già consegnate alle altre', async () => {
  const {db,d1}=createD1();const env={DB:d1} as Env;
  db.exec("INSERT INTO classes(id,label) VALUES ('C1','3 A'),('C2','3 B'); INSERT INTO circulars(number,title,publish_date,r2_pdf_key,notification_pending) VALUES (1,'Titolo','2026-11-06','1.pdf',1)");
  db.prepare("INSERT INTO circular_ai_analysis(circular_number,badge,summary,tier,per_class_json) VALUES (1,'NOT_RELEVANT','Riassunto',2,?)").run(JSON.stringify({C1:{badge:'RELEVANT'},C2:{badge:'POTENTIAL'}}));
  let fail=true;send.mockImplementation(async(...args)=>{if(args[4]==='C2'&&fail){fail=false;throw new Error('Rete');}});
  await notifySummarizedCirculars(env);
  expect(db.prepare('SELECT notification_pending FROM circulars').get()?.notification_pending).toBe(1);
  await notifySummarizedCirculars(env);
  expect(send.mock.calls.filter(args=>args[4]==='C1')).toHaveLength(1);
  expect(send.mock.calls.filter(args=>args[4]==='C2')).toHaveLength(2);
  expect(db.prepare('SELECT notification_pending FROM circulars').get()?.notification_pending).toBe(0);
  db.close();
});
