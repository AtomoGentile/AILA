import { Hono } from 'hono';
import {bodyLimit} from 'hono/body-limit';
import { HTTPException } from 'hono/http-exception';
import type { Env,JWTPayload } from '../types';
import { authMiddleware,newUUID,resolveClassId } from '../auth';
import { normalizeTitle } from './calendar';
import { requireAiService } from '../services/aiRouter';
import { loadForumCalendarProposal,forumCalendarProposalJson,forumCalendarDigest,FORUM_CALENDAR_SOURCE_SQL } from '../services/forumCalendar';

interface Action {tool:'calendar.create'|'calendar.update'|'calendar.delete';title:string;date:string;category:string;
 time:string|null;notes:string|null;visibility:'CLASS'|'SELF'|'SPECIFIC';visibleToUserIds?:string[]|null;circularNumber:number|null;eventId?:string|null;fields?:string[];audienceLabel?:string;previousTitle?:string;previousDate?:string;previousTime?:string|null;}
interface StoredStep {action:Action;eventId:string;before:EventRow|null;claimKey:string|null;}
interface StoredPlan {actions:Action[];clarification:string;requestedByUserId:string;steps:StoredStep[];}
interface Operation {id:string;user_id:string;class_id:string;plan_json:string;status:string;result_json:string|null;expires_at:string;execution_token:string|null;shared_proposal_id:string|null;}
interface EventRow {id:string;title:string;event_date:string;start_time:string|null;category:string;notes:string|null;is_for_all:number;is_ai_generated:number;created_by:string|null;visible_to_user_ids_json:string|null;}
const operations=new Hono<{Bindings:Env;Variables:{jwtPayload:JWTPayload}}>();operations.use('*',authMiddleware());operations.use('*',bodyLimit({maxSize:128*1024}));
const categories=['VERIFICA','INTERROGAZIONE','PAGAMENTO','USCITA_DIDATTICA','ENTRATA','AVVISO','ALTRO'];
export function validAction(raw:unknown):raw is Action {
 if(!raw||typeof raw!=='object')return false;const a=raw as Action;
 if(!['calendar.create','calendar.update','calendar.delete'].includes(a.tool)||typeof a.title!=='string'||!a.title.trim()||a.title.length>200)return false;
 if(!categories.includes(a.category)||!['CLASS','SELF','SPECIFIC'].includes(a.visibility)||typeof a.date!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(a.date))return false;
 const date=new Date(a.date+'T00:00:00Z');if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==a.date||a.date.startsWith('0000'))return false;
 if(a.time!=null&&(a.tool==='calendar.create'||a.tool==='calendar.update'&&Array.isArray(a.fields)&&a.fields.includes('time'))){if(typeof a.time!=='string')return false;
  const range=/^Dalla ([1-9])ª alla ([1-9])ª ora$/.exec(a.time);
  if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(a.time)&&!/^[1-9]ª ora$/.test(a.time)&&!(range&&range[1]<=range[2]))return false;
 }
 if(a.notes!=null&&(a.tool==='calendar.create'||a.tool==='calendar.update'&&Array.isArray(a.fields)&&a.fields.includes('notes'))&&(typeof a.notes!=='string'||a.notes.length>2000))return false;
 if(a.tool==='calendar.update'&&(!Array.isArray(a.fields)||!a.fields.length||!a.fields.every(f=>['title','date','time','category','notes','visibility'].includes(f))))return false;
 if(a.visibility==='SPECIFIC'){
  if(!Array.isArray(a.visibleToUserIds)||!a.visibleToUserIds.length||a.visibleToUserIds.length>100||a.visibleToUserIds.some(id=>typeof id!=='string'||!id.trim()))return false;
 }else if(a.visibleToUserIds!=null&&(!Array.isArray(a.visibleToUserIds)||a.visibleToUserIds.length))return false;
 return a.tool==='calendar.create'||typeof a.eventId==='string'&&a.eventId.length>0&&a.eventId.length<=100;
}
const digest=async(text:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text)))).map(x=>x.toString(16).padStart(2,'0')).join('');
function canSee(event:EventRow,userId:string){
 if(event.is_for_all||event.created_by===userId||!event.visible_to_user_ids_json)return true;
 try{return (JSON.parse(event.visible_to_user_ids_json) as string[]).includes(userId);}catch{return false;}
}
function canEdit(event:EventRow,user:JWTPayload){return canSee(event,user.sub)&&(event.created_by===user.sub||event.is_ai_generated===1||user.role==='REPRESENTATIVE');}
async function ownOperation(env:Env,userId:string,classId:string,id:string){
 const op=await env.DB.prepare('SELECT * FROM aca_operations WHERE id=? AND user_id=? AND class_id=?').bind(id,userId,classId).first<Operation>();
 if(!op)throw new HTTPException(404,{message:'Operazione non trovata'});return op;
}
async function responseFor(env:Env,op:Operation){
 const plan=JSON.parse(op.plan_json) as StoredPlan;
 const steps=await env.DB.prepare('SELECT step,tool,event_id AS eventId,status FROM aca_steps WHERE operation_id=? ORDER BY step').bind(op.id).all<{step:number;tool:string;eventId:string|null;status:string}>();
 const lines=steps.results.map(row=>{
  const a=plan.actions[row.step];const readable=new Intl.DateTimeFormat('it-IT',{dateStyle:'full',timeZone:'Europe/Rome'}).format(new Date(a.date+'T00:00:00Z'));
  const label=`${readable} — ${a.title}`;
  const verb=a.tool==='calendar.delete'?'Eliminato':a.tool==='calendar.update'?'Modificato':'Inserito';
  return row.status==='DONE'?`• ${verb}: ${label}`:row.status==='SKIPPED'?`• Già presente: ${label}`:`• Non eseguito, dati cambiati o permessi non validi: ${label}`;
 });
 return {id:op.id,status:op.status,expiresAt:op.expires_at,digest:await digest(op.plan_json),
  plan:{actions:plan.actions,clarification:plan.clarification,requestedByUserId:op.user_id,operationId:op.id,digest:await digest(op.plan_json),expiresAt:op.expires_at},
  results:steps.results,text:lines.length?(lines.join('\n')+(plan.clarification?'\n'+plan.clarification:'')):'Nessuna modifica eseguita.'};
}
operations.post('/',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);const input=await c.req.json<{actions:unknown[];clarification?:string;sharedProposalId?:string;digest?:string}>();
 await requireAiService(c.env,user.sub,'CALENDAR');
 if(input.sharedProposalId){
  const shared=await loadForumCalendarProposal(c.env,classId,input.sharedProposalId);
  const shown=await forumCalendarProposalJson(c.env,shared);
  if(shown.status!=='PENDING')return c.json({error:shown.status==='ACCEPTED'?'Già aggiunto al calendario':'La conversazione è cambiata: questa proposta non è più attuale.',code:shown.status},409);
  if(input.digest!==await forumCalendarDigest(shared.plan_json))return c.json({error:'La proposta da confermare è cambiata.'},409);
  // Only the server-stored, visible shared plan can be executed, never client replacements.
  Object.assign(input,JSON.parse(shared.plan_json));
 }
 if(!Array.isArray(input.actions)||!input.actions.length||input.actions.length>12||!input.actions.every(validAction))return c.json({error:'Piano non valido (massimo 12 azioni con titolo e data certi)'},400);
 const count=await c.env.DB.prepare("SELECT COUNT(*) AS n FROM aca_operations WHERE user_id=? AND created_at>datetime('now','-1 hour')").bind(user.sub).first<{n:number}>();
 if((count?.n??0)>=30)return c.json({error:'Limite di pianificazione raggiunto: riprova tra un’ora'},429);
 const actions:Action[]=[];const steps:StoredStep[]=[];const targeted=new Set<string>();
 for(const raw of input.actions){
  const action:Action={...raw,title:raw.title.trim(),time:raw.time??null,notes:raw.notes??null,circularNumber:raw.circularNumber??null};
  let before:EventRow|null=null;
  if(action.tool!=='calendar.create'){
   if(targeted.has(action.eventId!))return c.json({error:'Un evento può essere modificato una sola volta nello stesso piano'},400);targeted.add(action.eventId!);
   before=await c.env.DB.prepare('SELECT * FROM calendar_events WHERE id=? AND class_id=?').bind(action.eventId,classId).first<EventRow>();
   if(!before||!canEdit(before,user))return c.json({error:'Evento non disponibile o non modificabile'},403);
   // Preserve the existing audience unless the preview explicitly changes it.
   if(action.tool!=='calendar.update'||!action.fields?.includes('visibility')){
    action.visibility=before.is_for_all?'CLASS':'SPECIFIC';
    action.visibleToUserIds=before.is_for_all?null:JSON.parse(before.visible_to_user_ids_json??'[]');
   }
   action.previousTitle=before.title;action.previousDate=before.event_date;action.previousTime=before.start_time;
   if(action.tool==='calendar.update'){
    const fields=new Set(action.fields);
    if(!fields.has('title'))action.title=before.title;
    if(!fields.has('date'))action.date=before.event_date;
    if(!fields.has('time'))action.time=before.start_time;
    if(!fields.has('category'))action.category=before.category;
    if(!fields.has('notes'))action.notes=before.notes;
   }
   if(action.tool==='calendar.delete')Object.assign(action,{title:before.title,date:before.event_date,time:before.start_time,category:before.category,notes:before.notes});
  }
  if(action.visibility==='SPECIFIC'){
   action.visibleToUserIds=[...new Set(action.visibleToUserIds)].sort();
   const members=await c.env.DB.prepare('SELECT id,first_name,last_name FROM users WHERE class_id=?').bind(classId).all<{id:string;first_name:string;last_name:string}>();
   const changingAudience=action.tool==='calendar.create'||action.tool==='calendar.update'&&action.fields?.includes('visibility');
   if(changingAudience&&(!action.visibleToUserIds.length||action.visibleToUserIds.some(id=>!members.results.some(m=>m.id===id))))return c.json({error:'Destinatari non validi per questa classe'},400);
   action.audienceLabel=action.visibleToUserIds.map(id=>{const m=members.results.find(m=>m.id===id);return m?`${m.first_name} ${m.last_name}`:'Destinatario precedente';}).join(', ')||'Destinatari attuali dell’evento';
  }else{
   action.visibleToUserIds=null;
   action.audienceLabel=action.visibility==='CLASS'?'Tutta la classe':'Solo tu';
  }
  if(action.circularNumber!=null&&!await c.env.DB.prepare('SELECT number FROM circulars WHERE number=?').bind(action.circularNumber).first())return c.json({error:'Circolare non valida'},400);
  const eventId=action.tool==='calendar.create'?newUUID():action.eventId!;
  const visibilityKey=action.visibility==='CLASS'?'CLASS':action.visibility==='SELF'?user.sub:JSON.stringify(action.visibleToUserIds);
  const claimKey=action.tool==='calendar.create'?await digest(JSON.stringify([classId,action.date,action.category,normalizeTitle(action.title),visibilityKey])):null;
  actions.push(action);steps.push({action,eventId,before,claimKey});
 }
 const plan:StoredPlan={actions,steps,clarification:typeof input.clarification==='string'?input.clarification.slice(0,2000):'',requestedByUserId:user.sub};
 const id=newUUID();const expires=new Date(Date.now()+30*60_000).toISOString();
 await c.env.DB.prepare('INSERT INTO aca_operations(id,user_id,class_id,plan_json,expires_at,shared_proposal_id) VALUES (?,?,?,?,?,?)').bind(id,user.sub,classId,JSON.stringify(plan),expires,input.sharedProposalId??null).run();
 return c.json(await responseFor(c.env,(await ownOperation(c.env,user.sub,classId,id))),201);
});
operations.get('/:id',async c=>{
 let op=await ownOperation(c.env,c.get('jwtPayload').sub,await resolveClassId(c),c.req.param('id'));
 if(op.status==='PREVIEW'&&op.expires_at<=new Date().toISOString()){
  await c.env.DB.prepare("UPDATE aca_operations SET status='EXPIRED' WHERE id=? AND status='PREVIEW'").bind(op.id).run();op={...op,status:'EXPIRED'};
 }return c.json(await responseFor(c.env,op));
});
operations.post('/:id/cancel',async c=>{
 const op=await ownOperation(c.env,c.get('jwtPayload').sub,await resolveClassId(c),c.req.param('id'));
 await c.env.DB.prepare("UPDATE aca_operations SET status='CANCELLED' WHERE id=? AND status='PREVIEW'").bind(op.id).run();
 return c.json(await responseFor(c.env,await ownOperation(c.env,op.user_id,op.class_id,op.id)));
});
operations.post('/:id/confirm',async c=>{
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);let op=await ownOperation(c.env,user.sub,classId,c.req.param('id'));
 await requireAiService(c.env,user.sub,'CALENDAR');
 const body=await c.req.json<{confirmed:boolean;digest:string}>();
 if(body.confirmed!==true||body.digest!==await digest(op.plan_json))return c.json({error:'Conferma esplicita dell’anteprima richiesta'},400);
 if(op.status==='COMPLETED'||op.status==='PARTIAL')return c.json(await responseFor(c.env,op));
 if(op.status!=='PREVIEW'||op.expires_at<=new Date().toISOString())return c.json({error:'Anteprima scaduta o annullata. Prepara un nuovo piano.'},409);
 const plan=JSON.parse(op.plan_json) as StoredPlan;const token=newUUID();
 let sharedGuard='';let sharedBindings:(string)[]=[];
 if(op.shared_proposal_id){
  const shared=await loadForumCalendarProposal(c.env,classId,op.shared_proposal_id);
  const shown=await forumCalendarProposalJson(c.env,shared);
  if(shown.status!=='PENDING')return c.json({error:shown.status==='ACCEPTED'?'Già aggiunto al calendario':'Proposta superata dai nuovi messaggi.',code:shown.status},409);
  sharedGuard=` AND EXISTS(SELECT 1 FROM forum_calendar_proposals p JOIN forum_threads t ON t.id=p.thread_id
   WHERE p.id=? AND p.status='PENDING' AND p.expires_at>? AND t.hidden=0
   AND p.source_ids_json=(${FORUM_CALENDAR_SOURCE_SQL}))`;
  sharedBindings=[shared.id,new Date().toISOString(),shared.thread_id];
 }
 const guard='EXISTS (SELECT 1 FROM aca_operations WHERE id=? AND execution_token=? AND status=\'RUNNING\')';
 const sql:D1PreparedStatement[]=[c.env.DB.prepare(`UPDATE aca_operations SET status='RUNNING',execution_token=?,confirmed_at=CURRENT_TIMESTAMP
 WHERE id=? AND user_id=? AND status='PREVIEW' AND expires_at>?${sharedGuard}`).bind(token,op.id,user.sub,new Date().toISOString(),...sharedBindings)];
 for(let i=0;i<plan.steps.length;i++){
  const step=plan.steps[i];const a=step.action;
  sql.push(c.env.DB.prepare(`INSERT OR IGNORE INTO aca_steps(operation_id,step,tool,event_id,status) SELECT ?,?,?,?,'PENDING' WHERE ${guard}`)
   .bind(op.id,i,a.tool,step.eventId,op.id,token));
  if(a.tool==='calendar.create'){
   const visible=a.visibility==='CLASS'?null:JSON.stringify(a.visibleToUserIds??[user.sub]);
   sql.push(c.env.DB.prepare(`DELETE FROM aca_calendar_claims WHERE key=? AND NOT EXISTS
    (SELECT 1 FROM calendar_events WHERE id=aca_calendar_claims.event_id AND event_date=aca_calendar_claims.event_date AND category=aca_calendar_claims.category)
    AND ${guard}`).bind(step.claimKey,op.id,token));
   sql.push(c.env.DB.prepare(`INSERT INTO calendar_events(id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,notes,visible_to_user_ids_json,circular_number)
    SELECT ?,?,?,?,?,?,1,?,?,?,?,? WHERE ${guard} AND NOT EXISTS (SELECT 1 FROM aca_calendar_claims WHERE key=?)
    AND NOT EXISTS (SELECT 1 FROM calendar_events WHERE class_id=? AND event_date=? AND category=? AND lower(trim(title))=lower(trim(?))
    AND is_for_all=? AND visible_to_user_ids_json IS ?)`)
    .bind(step.eventId,a.title,a.date,a.time,a.category,+(a.visibility==='CLASS'),user.sub,classId,a.notes,visible,a.circularNumber,op.id,token,step.claimKey,classId,a.date,a.category,a.title,+(a.visibility==='CLASS'),visible));
   sql.push(c.env.DB.prepare(`INSERT OR IGNORE INTO aca_calendar_claims(key,event_id,event_date,title_key,category)
    SELECT ?,?,?,?,? WHERE EXISTS (SELECT 1 FROM calendar_events WHERE id=?) AND ${guard}`)
    .bind(step.claimKey,step.eventId,a.date,normalizeTitle(a.title),a.category,step.eventId,op.id,token));
   sql.push(c.env.DB.prepare(`UPDATE aca_steps SET status=CASE WHEN EXISTS(SELECT 1 FROM calendar_events WHERE id=?) THEN 'DONE' ELSE 'SKIPPED' END
    WHERE operation_id=? AND step=? AND ${guard}`).bind(step.eventId,op.id,i,op.id,token));
  }else{
   const before=step.before!;
   // Compare-and-swap binds the preview to both content and permissions at execution time.
   const where=`id=? AND class_id=? AND title=? AND event_date=? AND start_time IS ? AND category=? AND notes IS ?
    AND visible_to_user_ids_json IS ? AND is_for_all=? AND (created_by=? OR is_ai_generated=1 OR ?='REPRESENTATIVE') AND ${guard}`;
   const bindings=[step.eventId,classId,before.title,before.event_date,before.start_time,before.category,before.notes,before.visible_to_user_ids_json,before.is_for_all,user.sub,user.role,op.id,token];
   if(a.tool==='calendar.update')sql.push(c.env.DB.prepare(`UPDATE calendar_events SET title=?,event_date=?,start_time=?,category=?,notes=?,is_for_all=?,visible_to_user_ids_json=? WHERE ${where}`)
    .bind(a.title,a.date,a.time,a.category,a.notes,a.fields?.includes('visibility')?+(a.visibility==='CLASS'):before.is_for_all,a.fields?.includes('visibility')?(a.visibility==='CLASS'?null:JSON.stringify(a.visibleToUserIds??[user.sub])):before.visible_to_user_ids_json,...bindings));
   else sql.push(c.env.DB.prepare(`DELETE FROM calendar_events WHERE ${where}`).bind(...bindings));
   // changes() belongs to the preceding mutation in the same database transaction.
   sql.push(c.env.DB.prepare(`UPDATE aca_steps SET status=CASE WHEN changes()=1 THEN 'DONE' ELSE 'CONFLICT' END WHERE operation_id=? AND step=? AND ${guard}`)
    .bind(op.id,i,op.id,token));
  }
 }
 if(op.shared_proposal_id)sql.push(c.env.DB.prepare(`UPDATE forum_calendar_proposals SET status='ACCEPTED',accepted_by=?,accepted_at=CURRENT_TIMESTAMP,operation_id=?
  WHERE id=? AND status='PENDING' AND ${guard} AND NOT EXISTS(SELECT 1 FROM aca_steps WHERE operation_id=? AND status NOT IN ('DONE','SKIPPED'))`)
  .bind(user.sub,op.id,op.shared_proposal_id,op.id,token,op.id));
 sql.push(c.env.DB.prepare(`UPDATE aca_operations SET status=CASE WHEN EXISTS(SELECT 1 FROM aca_steps WHERE operation_id=? AND status='CONFLICT') THEN 'PARTIAL' ELSE 'COMPLETED' END
 WHERE id=? AND execution_token=? AND status='RUNNING'`).bind(op.id,op.id,token));
 await c.env.DB.batch(sql);
 op=await ownOperation(c.env,user.sub,classId,op.id);
 if(op.shared_proposal_id&&op.status==='PREVIEW')return c.json({error:'La proposta è già stata accettata oppure la conversazione è cambiata. Aggiorna la discussione.',code:'PROPOSAL_CHANGED'},409);
 return c.json(await responseFor(c.env,op));
});
export default operations;
