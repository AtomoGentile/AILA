import type {Env} from '../types';
import {noteAudienceIncludes} from './noteAudience';
import {studyAudience} from './studyAudience';
import {circularBadgeForClass,isRelevantCircular} from './classAnalysis';

export type BriefKind='EVENT'|'CIRCULAR'|'POLL'|'RANKING_POLL'|'FORUM'|'PROPOSAL'|'SEATMAP';
export interface BriefItem {
 id:string;revision:string;kind:BriefKind;sourceId:string;title:string;message:string;
 actionLabel:string;date:string|null;canPlanStudy:boolean;isResult:boolean;
}
interface Candidate extends BriefItem {priority:number;sortKey:string;}
const short=(text:string,max=220)=>text.replace(/\s+/g,' ').trim().slice(0,max);
async function candidate(kind:BriefKind,sourceId:string,title:string,message:string,actionLabel:string,date:string|null,priority:number,version:string,canPlanStudy=false,isResult=false):Promise<Candidate>{
 const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify([kind,sourceId,title,message,actionLabel,date,version,canPlanStudy,isResult])));
 const revision=Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
 const sortKey=version.match(/\d{4}-\d\d-\d\d(?:T| \d\d:\d\d:\d\d)/)?.[0]??version.match(/\d{1,10}/)?.[0]?.padStart(10,'0')??version;
 return {id:`${kind}:${sourceId}`,revision,kind,sourceId,title:short(title,240),message:short(message),actionLabel,date,canPlanStudy,isResult,priority,sortKey};
}
const group=(kind:BriefKind)=>kind==='RANKING_POLL'?'POLL':kind;
const order=(a:Candidate,b:Candidate)=>b.priority-a.priority||b.sortKey.localeCompare(a.sortKey)||a.id.localeCompare(b.id);
/** Keep urgent items first and leave space for more than one source. */
export function selectBriefItems(items:Candidate[],dismissed:Set<string>,limit=Number.POSITIVE_INFINITY):BriefItem[]{
 const sorted=items.filter(i=>!dismissed.has(`${i.id}|${i.revision}`)).sort(order);
 const selected:Candidate[]=[];const counts=new Map<string,number>();
 for(const item of sorted){const key=group(item.kind);if((counts.get(key)??0)>=1)continue;selected.push(item);counts.set(key,(counts.get(key)??0)+1);if(selected.length===limit)break;}
 for(const item of sorted){if(selected.length>=limit)break;if(!selected.includes(item))selected.push(item);}
 return selected.sort(order).map(({priority,sortKey,...item})=>item);
}

/** Reads existing authorized facts only: never runs a model or spends an AI quota. */
export async function briefCandidates(env:Env,classId:string,userId:string,now=new Date()):Promise<Candidate[]>{
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(now);
 const after=(days:number)=>new Date(Date.parse(`${today}T00:00:00Z`)+days*86400000).toISOString().slice(0,10);
 const timestamp=now.toISOString();
 const pollResults=`(EXISTS(SELECT 1 FROM interrogation_calculations r WHERE r.grid_id=g.id) OR EXISTS(
   SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id=a.slot_id WHERE s.grid_id=g.id))`;
 const [events,circulars,polls,rankings,replies,proposals,roster,seatmaps,seatPreferences]=await Promise.all([
  env.DB.prepare(`SELECT e.id,e.title,e.event_date,e.category,e.notes,e.poll_grid_id,e.visible_to_user_ids_json FROM calendar_events e WHERE e.class_id=? AND e.event_date BETWEEN ? AND ?
   AND ${studyAudience('e',false)} AND NOT EXISTS(SELECT 1 FROM aca_nudge_dismissals d WHERE d.user_id=? AND d.event_id=e.id AND d.event_date=e.event_date)
   ORDER BY e.event_date,e.id`).bind(classId,today,after(7),userId,userId,userId,userId).all<{id:string;title:string;event_date:string;category:string;notes:string|null;poll_grid_id:string|null;visible_to_user_ids_json:string|null}>(),
  env.DB.prepare(`SELECT c.number,c.title,c.publish_date,a.badge,a.per_class_json FROM circulars c
   JOIN circular_ai_analysis a ON a.circular_number=c.number WHERE a.is_fallback=0 AND a.tier>0
   AND NOT EXISTS(SELECT 1 FROM aca_brief_circular_reads seen WHERE seen.user_id=? AND seen.class_id=? AND seen.circular_number=c.number)
   ORDER BY c.number DESC`).bind(userId,classId).all<{number:number;title:string;publish_date:string;badge:string;per_class_json:string|null}>(),
  env.DB.prepare(`SELECT g.id,g.subject,g.closes_at,g.created_at,${pollResults} AS has_results,
   EXISTS(SELECT 1 FROM interrogation_submissions sub WHERE sub.grid_id=g.id AND sub.student_id=?) AS submitted,
   (SELECT GROUP_CONCAT(a.id||':'||a.slot_id) FROM interrogation_assignments a JOIN interrogation_slots s ON s.id=a.slot_id WHERE s.grid_id=g.id) AS result_version
   FROM interrogation_grids g WHERE g.class_id=? AND g.is_published=1
   AND (g.audience_json IS NULL OR EXISTS(SELECT 1 FROM json_each(g.audience_json) WHERE value=?))
   AND ((${pollResults} AND datetime(g.created_at)>=datetime(?)) OR (NOT ${pollResults} AND (g.closes_at IS NULL OR datetime(g.closes_at)>datetime(?))))
   ORDER BY g.created_at DESC,g.id`).bind(userId,classId,userId,after(-30),timestamp)
   .all<{id:string;subject:string;closes_at:string|null;created_at:string;has_results:number;submitted:number;result_version:string|null}>(),
  env.DB.prepare(`SELECT p.id,p.question,p.created_at,p.is_closed,
   EXISTS(SELECT 1 FROM ranking_poll_answers a WHERE a.poll_id=p.id AND a.user_id=?) AS answered
   FROM ranking_polls p WHERE p.class_id=? AND (p.is_closed=0 OR datetime(p.created_at)>=datetime(?))
   AND (p.audience_json IS NULL OR EXISTS(SELECT 1 FROM json_each(p.audience_json) WHERE value=?))
   ORDER BY p.created_at DESC,p.id`).bind(userId,classId,after(-30),userId)
   .all<{id:string;question:string;created_at:string;is_closed:number;answered:number}>(),
  env.DB.prepare(`SELECT t.id,t.title,MAX(p.created_at||'|'||p.id) AS version FROM forum_posts p
   JOIN forum_threads t ON t.id=p.thread_id LEFT JOIN forum_subscriptions s ON s.thread_id=t.id AND s.user_id=?
   LEFT JOIN forum_posts parent ON parent.id=p.parent_id AND parent.thread_id=t.id
   LEFT JOIN forum_participants me ON me.thread_id=t.id AND me.user_id=s.user_id
   WHERE t.class_id=? AND t.hidden=0 AND p.hidden=0 AND p.is_ai=0 AND p.author_id<>? AND datetime(p.created_at)>=datetime(?) AND (s.mode IS NULL OR s.mode<>'NONE')
   AND (p.id=t.id OR s.mode='ALL' OR (s.mode='REPLIES' AND (parent.author_id=? OR EXISTS(SELECT 1 FROM json_each(p.mentions_json) WHERE value=me.public_key))))
   GROUP BY t.id,t.title ORDER BY version DESC LIMIT 6`).bind(userId,classId,userId,after(-3),userId).all<{id:string;title:string;version:string}>(),
  env.DB.prepare(`SELECT p.id,p.title,p.status,p.created_at,p.edited_at FROM proposals p WHERE p.class_id=? AND p.status IN ('NUOVA','IN_ANALISI')
   AND p.author_id<>? AND datetime(p.created_at)>=datetime(?)
   ORDER BY p.created_at DESC,p.id LIMIT 6`).bind(classId,userId,after(-7)).all<{id:string;title:string;status:string;created_at:string;edited_at:string|null}>(),
  env.DB.prepare('SELECT id,first_name,last_name FROM users WHERE class_id=?').bind(classId).all<{id:string;first_name:string;last_name:string}>(),
  env.DB.prepare('SELECT id,published_at FROM seat_map_history WHERE class_id=? ORDER BY map_index ASC LIMIT 1').bind(classId).all<{id:string;published_at:string}>(),
  env.DB.prepare('SELECT preferences_open FROM classes WHERE id=?').bind(classId).first<{preferences_open:number}>()
 ]);
 const pending:Promise<Candidate>[]=[];
 for(const e of events.results){
  // Structured recipients and poll assignments take precedence over legacy free-text notes.
  if(!e.poll_grid_id && e.visible_to_user_ids_json===null && !noteAudienceIncludes(e.notes,roster.results,userId))continue;
  const days=Math.round((Date.parse(e.event_date)-Date.parse(today))/86400000);
  pending.push(candidate('EVENT',e.id,e.title,days===0?'È un tuo impegno di oggi.':days===1?'È un tuo impegno di domani.':`È un tuo impegno tra ${days} giorni.`,'Apri il calendario',e.event_date,120-days*10,e.event_date,['VERIFICA','INTERROGAZIONE'].includes(e.category)));}
 for(const c of circulars.results){
  if(isRelevantCircular(circularBadgeForClass(c.badge,c.per_class_json,classId)))
   pending.push(candidate('CIRCULAR',String(c.number),c.title,'','',null,100,String(c.number)));}
 for(const p of polls.results){const result=!!p.has_results;
  pending.push(candidate('POLL',p.id,p.subject,result?'Le date delle interrogazioni sono pronte.':p.submitted?'Hai già inviato le tue preferenze. Il sondaggio è ancora aperto.':'Questo sondaggio è aperto: puoi inviare le tue preferenze.',
   result?'Vedi i risultati':p.submitted?'Apri il sondaggio':'Compila il sondaggio',result?null:p.closes_at?.slice(0,10)??null,result?97:96,`${p.created_at}|${p.result_version??''}`,false,result));}
 for(const p of rankings.results){const result=!!p.is_closed;
  pending.push(candidate('RANKING_POLL',p.id,p.question,result?'Il sondaggio è concluso: scopri la classifica.':p.answered?'Hai già risposto. Puoi vedere la classifica del sondaggio aperto.':'Questo sondaggio è aperto: puoi inviare la tua classifica.',
   result?'Vedi i risultati':p.answered?'Apri il sondaggio':'Metti in ordine le opzioni',null,result?95:94,p.created_at,false,result));}
 for(const map of seatmaps.results)pending.push(candidate('SEATMAP',map.id,'La disposizione dei posti','La mappa dei posti della tua classe è disponibile.','Vedi i posti',null,92,map.published_at));
 if(seatPreferences?.preferences_open)pending.push(candidate('SEATMAP',`preferences:${classId}`,'Preferenze per i posti','La raccolta delle preferenze per la disposizione è aperta.','Esprimi le preferenze',null,93,seatmaps.results[0]?.id??classId));
 for(const t of replies.results)pending.push(candidate('FORUM',t.id,t.title,'Una nuova discussione, una risposta o una menzione nella tua classe.','Apri la discussione',null,98,t.version));
 for(const p of proposals.results)pending.push(candidate('PROPOSAL',p.id,p.title,'Una proposta aperta nella bacheca della tua classe.','Valuta la proposta',null,91,`${p.status}|${p.edited_at??p.created_at}`));
 return Promise.all(pending);
}

export async function loadBrief(env:Env,classId:string,userId:string){
 const now=new Date().toISOString();
 await env.DB.prepare('DELETE FROM aca_brief_dismissals WHERE user_id=? AND expires_at<=?').bind(userId,now).run();
 const [items,dismissals]=await Promise.all([briefCandidates(env,classId,userId),env.DB.prepare('SELECT item_id,revision FROM aca_brief_dismissals WHERE user_id=? AND expires_at>?').bind(userId,now).all<{item_id:string;revision:string}>()]);
 return selectBriefItems(items,new Set(dismissals.results.map(d=>`${d.item_id}|${d.revision}`)));
}
