import type {Env} from '../types';
import {studyAudience} from './studyAudience';
import {parsePerClass,resolveForClass} from './classAnalysis';

export type BriefKind='EVENT'|'CIRCULAR'|'POLL'|'RANKING_POLL'|'FORUM'|'PROPOSAL';
export interface BriefItem {
 id:string;revision:string;kind:BriefKind;sourceId:string;title:string;message:string;
 actionLabel:string;date:string|null;canPlanStudy:boolean;
}
interface Candidate extends BriefItem {priority:number;}
const short=(text:string,max=220)=>text.replace(/\s+/g,' ').trim().slice(0,max);
async function candidate(kind:BriefKind,sourceId:string,title:string,message:string,actionLabel:string,date:string|null,priority:number,version:string,canPlanStudy=false):Promise<Candidate>{
 const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify([kind,sourceId,title,message,actionLabel,date,version,canPlanStudy])));
 const revision=Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
 return {id:`${kind}:${sourceId}`,revision,kind,sourceId,title:short(title,240),message:short(message),actionLabel,date,canPlanStudy,priority};
}
const group=(kind:BriefKind)=>kind==='RANKING_POLL'?'POLL':kind;
/** Keep urgent items first and leave space for more than one source. */
export function selectBriefItems(items:Candidate[],dismissed:Set<string>,limit=4):BriefItem[]{
 const sorted=items.filter(i=>!dismissed.has(`${i.id}|${i.revision}`)).sort((a,b)=>b.priority-a.priority||a.id.localeCompare(b.id));
 const selected:Candidate[]=[];const counts=new Map<string,number>();
 for(const item of sorted){const key=group(item.kind);if((counts.get(key)??0)>=1)continue;selected.push(item);counts.set(key,(counts.get(key)??0)+1);if(selected.length===limit)break;}
 for(const item of sorted){if(selected.length>=limit)break;if(!selected.includes(item))selected.push(item);}
 return selected.sort((a,b)=>b.priority-a.priority||a.id.localeCompare(b.id)).map(({priority,...item})=>item);
}

/** Reads existing authorized facts only: never runs a model or spends an AI quota. */
export async function briefCandidates(env:Env,classId:string,userId:string,now=new Date()):Promise<Candidate[]>{
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(now);
 const after=(days:number)=>new Date(Date.parse(`${today}T00:00:00Z`)+days*86400000).toISOString().slice(0,10);
 const timestamp=now.toISOString();
 const [events,circulars,polls,rankings,replies,proposals]=await Promise.all([
  env.DB.prepare(`SELECT e.id,e.title,e.event_date,e.category FROM calendar_events e WHERE e.class_id=? AND e.event_date BETWEEN ? AND ?
   AND ${studyAudience('e')} AND NOT EXISTS(SELECT 1 FROM aca_nudge_dismissals d WHERE d.user_id=? AND d.event_id=e.id AND d.event_date=e.event_date)
   ORDER BY e.event_date,e.id LIMIT 8`).bind(classId,today,after(3),userId,userId,userId,userId).all<{id:string;title:string;event_date:string;category:string}>(),
  env.DB.prepare(`SELECT c.number,c.title,c.publish_date,a.summary,a.badge,a.per_class_json FROM circulars c JOIN circular_ai_analysis a ON a.circular_number=c.number
   WHERE c.publish_date BETWEEN ? AND ? AND a.is_fallback=0 ORDER BY c.number DESC LIMIT 30`).bind(after(-7),today).all<{number:number;title:string;publish_date:string;summary:string;badge:string;per_class_json:string|null}>(),
  env.DB.prepare(`SELECT g.id,g.subject,g.closes_at,g.created_at FROM interrogation_grids g WHERE g.class_id=? AND g.is_published=1
   AND (g.audience_json IS NULL OR EXISTS(SELECT 1 FROM json_each(g.audience_json) WHERE value=?))
   AND (g.closes_at IS NULL OR datetime(g.closes_at)>datetime(?))
   AND NOT EXISTS(SELECT 1 FROM interrogation_submissions s WHERE s.grid_id=g.id AND s.student_id=?)
   AND NOT EXISTS(SELECT 1 FROM interrogation_calculations r WHERE r.grid_id=g.id)
   AND NOT EXISTS(SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id=a.slot_id WHERE s.grid_id=g.id)
   ORDER BY g.created_at DESC,g.id LIMIT 6`).bind(classId,userId,timestamp,userId).all<{id:string;subject:string;closes_at:string|null;created_at:string}>(),
  env.DB.prepare(`SELECT p.id,p.question,p.created_at FROM ranking_polls p WHERE p.class_id=? AND p.is_closed=0
   AND (p.audience_json IS NULL OR EXISTS(SELECT 1 FROM json_each(p.audience_json) WHERE value=?))
   AND NOT EXISTS(SELECT 1 FROM ranking_poll_answers a WHERE a.poll_id=p.id AND a.user_id=?)
   ORDER BY p.created_at DESC,p.id LIMIT 6`).bind(classId,userId,userId).all<{id:string;question:string;created_at:string}>(),
  env.DB.prepare(`SELECT t.id,t.title,MAX(p.created_at||'|'||p.id) AS version FROM forum_posts p
   JOIN forum_threads t ON t.id=p.thread_id JOIN forum_subscriptions s ON s.thread_id=t.id AND s.user_id=?
   LEFT JOIN forum_posts parent ON parent.id=p.parent_id AND parent.thread_id=t.id
   LEFT JOIN forum_participants me ON me.thread_id=t.id AND me.user_id=s.user_id
   WHERE t.class_id=? AND t.hidden=0 AND p.hidden=0 AND p.is_ai=0 AND p.author_id<>? AND datetime(p.created_at)>=datetime(?) AND s.mode<>'NONE'
   AND (s.mode='ALL' OR (s.mode='REPLIES' AND (parent.author_id=? OR EXISTS(SELECT 1 FROM json_each(p.mentions_json) WHERE value=me.public_key))))
   GROUP BY t.id,t.title ORDER BY version DESC LIMIT 6`).bind(userId,classId,userId,after(-3),userId).all<{id:string;title:string;version:string}>(),
  env.DB.prepare(`SELECT p.id,p.title,p.status,p.created_at,p.edited_at FROM proposals p WHERE p.class_id=? AND p.status IN ('NUOVA','IN_ANALISI')
   AND p.author_id<>? AND datetime(p.created_at)>=datetime(?) AND NOT EXISTS(SELECT 1 FROM proposal_votes v WHERE v.proposal_id=p.id AND v.user_id=?)
   ORDER BY p.created_at DESC,p.id LIMIT 6`).bind(classId,userId,after(-7),userId).all<{id:string;title:string;status:string;created_at:string;edited_at:string|null}>()
 ]);
 const pending:Promise<Candidate>[]=[];
 for(const e of events.results){const days=Math.round((Date.parse(e.event_date)-Date.parse(today))/86400000);
  pending.push(candidate('EVENT',e.id,e.title,days===0?'È un tuo impegno di oggi.':days===1?'È un tuo impegno di domani.':`È un tuo impegno tra ${days} giorni.`,'Apri il calendario',e.event_date,100-days*10,e.event_date,['VERIFICA','INTERROGAZIONE'].includes(e.category)));}
 for(const c of circulars.results){const resolved=resolveForClass({badge:c.badge,summary:c.summary,deadlines:[],perClass:parsePerClass(c.per_class_json)},classId);
  if(resolved.badge==='RELEVANT')pending.push(candidate('CIRCULAR',String(c.number),c.title,resolved.summary,'Leggi la circolare',c.publish_date,c.publish_date===today?86:76,String(c.number)));}
 for(const p of polls.results)pending.push(candidate('POLL',p.id,p.subject,'Devi ancora inviare le tue preferenze per le interrogazioni.','Compila il sondaggio',p.closes_at?.slice(0,10)??null,p.closes_at&&Date.parse(p.closes_at.includes('T')?p.closes_at:p.closes_at.replace(' ','T')+'Z')-now.getTime()<=86400000?105:85,p.closes_at??p.created_at));
 for(const p of rankings.results)pending.push(candidate('RANKING_POLL',p.id,p.question,'Questo sondaggio è aperto e non hai ancora inviato la tua classifica.','Metti in ordine le opzioni',null,82,p.created_at));
 for(const t of replies.results)pending.push(candidate('FORUM',t.id,t.title,'Una risposta recente o una menzione in una discussione che segui.','Apri la discussione',null,88,t.version));
 for(const p of proposals.results)pending.push(candidate('PROPOSAL',p.id,p.title,'Una proposta aperta della classe su cui non hai ancora votato.','Valuta la proposta',null,65,`${p.status}|${p.edited_at??p.created_at}`));
 return Promise.all(pending);
}

export async function loadBrief(env:Env,classId:string,userId:string){
 const now=new Date().toISOString();
 await env.DB.prepare('DELETE FROM aca_brief_dismissals WHERE user_id=? AND expires_at<=?').bind(userId,now).run();
 const [items,dismissals]=await Promise.all([briefCandidates(env,classId,userId),env.DB.prepare('SELECT item_id,revision FROM aca_brief_dismissals WHERE user_id=? AND expires_at>?').bind(userId,now).all<{item_id:string;revision:string}>()]);
 return selectBriefItems(items,new Set(dismissals.results.map(d=>`${d.item_id}|${d.revision}`)));
}
