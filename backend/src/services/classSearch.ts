import type {Env} from '../types';

export interface ClassSearchHit {kind:'CIRCULAR'|'BOARD'|'FORUM';id:string;title:string;text:string;}
const literal=(term:string)=>`%${term.replace(/[\\%_]/g,'\\$&')}%`;
/** Ordinary database search: no model, AI consent or AI quota is involved. */
export async function searchClass(env:Env,classId:string,query:string):Promise<ClassSearchHit[]> {
 const number=/^(?:circolare\s*)?(?:n[.°]?\s*)?(\d+)$/i.exec(query.trim());
 const terms=(number?[number[1]]:query.trim().split(/\s+/)).slice(0,8).map(literal);
 if(!terms.length)return [];
 const match=(fields:string[])=>terms.map(()=>`(${fields.map(field=>`${field} LIKE ? ESCAPE '\\'`).join(' OR ')})`).join(' AND ');
 const bindings=(count:number)=>terms.flatMap(term=>Array(count).fill(term));
 const [circulars,board,forum]=await Promise.all([
  env.DB.prepare(`SELECT number,title,publish_date FROM circulars WHERE ${match(['title','CAST(number AS TEXT)'])} ORDER BY number DESC LIMIT 20`)
   .bind(...bindings(2)).all<{number:number;title:string;publish_date:string}>(),
  env.DB.prepare(`SELECT id,title,description FROM proposals WHERE class_id=? AND ${match(['title','description'])} ORDER BY created_at DESC,id DESC LIMIT 20`)
   .bind(classId,...bindings(2)).all<{id:string;title:string;description:string}>(),
  env.DB.prepare(`SELECT t.id,t.title,t.resolved FROM forum_threads t WHERE t.class_id=? AND t.hidden=0 AND ${terms.map(()=>`(t.title LIKE ? ESCAPE '\\' OR EXISTS(SELECT 1 FROM forum_posts p WHERE p.thread_id=t.id AND p.hidden=0 AND p.body LIKE ? ESCAPE '\\'))`).join(' AND ')} ORDER BY t.last_message_at DESC,t.id DESC LIMIT 20`)
   .bind(classId,...bindings(2)).all<{id:string;title:string;resolved:number}>()
 ]);
 return [
  ...circulars.results.map(c=>({kind:'CIRCULAR' as const,id:String(c.number),title:c.title,text:`Circolare n. ${c.number} · ${c.publish_date}`})),
  ...board.results.map(p=>({kind:'BOARD' as const,id:p.id,title:p.title,text:p.description.slice(0,160)})),
  ...forum.results.map(t=>({kind:'FORUM' as const,id:t.id,title:t.title,text:t.resolved?'Discussione risolta':'Discussione aperta'}))
 ];
}
