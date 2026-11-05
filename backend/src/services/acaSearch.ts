import type {Env} from '../types';
import {parsePerClass,resolveForClass} from './classAnalysis';
export interface AcaSearchHit {kind:string;id:string;title:string;text:string;}
/** All filtering is server-side. Shared Perabot never sees personal calendar/content. */
export async function searchAuthorized(env:Env,classId:string,userId:string,query:string,shared=false):Promise<AcaSearchHit[]>{
 const terms=query.toLowerCase().replace(/[^\p{L}\p{N} ]/gu,' ').split(/\s+/).filter(w=>w.length>=4).slice(0,5);
 if(!terms.length)return [];
 const pattern='%'+terms[0].replace(/[%_\\]/g,'')+'%';const hits:AcaSearchHit[]=[];
 const circulars=await env.DB.prepare(`SELECT c.number,c.title,a.summary,a.badge,a.deadlines_json,a.per_class_json
 FROM circulars c LEFT JOIN circular_ai_analysis a ON a.circular_number=c.number
 WHERE c.title LIKE ? OR a.summary LIKE ? ORDER BY c.number DESC LIMIT 6`).bind(pattern,pattern)
 .all<{number:number;title:string;summary:string|null;badge:string;deadlines_json:string;per_class_json:string|null}>();
 for(const c of circulars.results){const resolved=c.summary?resolveForClass({badge:c.badge,summary:c.summary,deadlines:JSON.parse(c.deadlines_json||'[]'),perClass:parsePerClass(c.per_class_json)},classId):null;
  hits.push({kind:'CIRCULAR',id:String(c.number),title:c.title,text:(resolved?.summary??'Testo non ancora analizzato').slice(0,2000)});}
 const calendar=await env.DB.prepare(`SELECT id,title,event_date,start_time,notes FROM calendar_events WHERE class_id=? AND
 (${shared?'is_for_all=1':"is_for_all=1 OR created_by=? OR EXISTS(SELECT 1 FROM json_each(visible_to_user_ids_json) WHERE value=?)"})
 AND (title LIKE ? OR notes LIKE ?) ORDER BY event_date DESC LIMIT 6`)
 .bind(classId,...(shared?[]:[userId,userId]),pattern,pattern).all<{id:string;title:string;event_date:string;start_time:string|null;notes:string|null}>();
 calendar.results.forEach(e=>hits.push({kind:'CALENDAR',id:e.id,title:e.title,text:`${e.event_date} ${e.start_time??''} ${e.notes??''}`.slice(0,2000)}));
 const posts=await env.DB.prepare(`SELECT p.id,p.body,t.id thread_id,t.title FROM forum_posts p JOIN forum_threads t ON t.id=p.thread_id
 WHERE t.class_id=? AND t.hidden=0 AND p.hidden=0 AND (p.body LIKE ? OR t.title LIKE ?) ORDER BY p.created_at DESC LIMIT 6`)
 .bind(classId,pattern,pattern).all<{id:string;thread_id:string;title:string;body:string}>();
 posts.results.forEach(p=>hits.push({kind:'FORUM',id:`${p.thread_id}/${p.id}`,title:p.title,text:p.body.slice(0,1800)}));
 const board=await env.DB.prepare('SELECT id,title,description FROM proposals WHERE class_id=? AND (title LIKE ? OR description LIKE ?) ORDER BY created_at DESC LIMIT 4')
 .bind(classId,pattern,pattern).all<{id:string;title:string;description:string}>();
 board.results.forEach(p=>hits.push({kind:'BOARD',id:p.id,title:p.title,text:p.description.slice(0,1800)}));
 return hits.slice(0,20);
}
