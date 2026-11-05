import {Hono} from 'hono';
import {bodyLimit} from 'hono/body-limit';
import {authMiddleware,resolveClassId} from '../auth';
import type {Env,JWTPayload} from '../types';
import {AI_FEATURES,AI_SERVICES,aiPreferences,saveAiPreferences,runAiTask,type AiFeature,type AiPreferences} from '../services/aiRouter';
import {searchAuthorized} from '../services/acaSearch';
const aca=new Hono<{Bindings:Env;Variables:{jwtPayload:JWTPayload}}>();aca.use('*',authMiddleware());aca.use('*',bodyLimit({maxSize:256*1024}));
aca.get('/preferences',async c=>c.json({preferences:await aiPreferences(c.env,c.get('jwtPayload').sub),
 capabilities:{cloud:!!c.env.AI,advanced:!!c.env.AI_ADVANCED_API_KEY&&!!c.env.AI_ADVANCED_MODEL&&!!c.env.AI_ADVANCED_PROVIDER,
 advancedProvider:c.env.AI_ADVANCED_API_KEY?c.env.AI_ADVANCED_PROVIDER??null:null,transcription:!!c.env.AI,imageAnalysis:!!c.env.AI,
 localModels:'Disponibilità verificata dal dispositivo; nessun modello nativo nella PWA.'},
 policy:{smallAttachmentDays:90,largeAttachmentDays:7,maxAudioSeconds:600,maxAudioBytes:20*1024*1024}}));
aca.put('/preferences',async c=>{
 const change=await c.req.json<Partial<AiPreferences>>();
 const body={...await aiPreferences(c.env,c.get('jwtPayload').sub),...change};const keys=['aiEnabled','acaEnabled','holdToOpen','cloudConsent','externalConsent','attachmentConsent','audioConsent','nudgesEnabled','advancedEnabled'] as const;
 if(!keys.every(k=>typeof body[k]==='boolean')||!Array.isArray(body.services)||!body.services.every(s=>AI_SERVICES.includes(s)))return c.json({error:'Preferenze non valide'},400);
 await saveAiPreferences(c.env,c.get('jwtPayload').sub,{...Object.fromEntries(keys.map(k=>[k,body[k]])),services:[...new Set(body.services)]} as unknown as AiPreferences);
 return c.json({preferences:await aiPreferences(c.env,c.get('jwtPayload').sub)});
});
aca.get('/usage',async c=>{
 const usage=await c.env.DB.prepare('SELECT feature,requests,reserved_tokens AS reservedTokens FROM ai_usage WHERE user_id=? AND day=?').bind(c.get('jwtPayload').sub,new Date().toISOString().slice(0,10)).all();
 return c.json({usage:usage.results,limits:{requestsPerFeature:Number(c.env.AI_FEATURE_DAILY_REQUESTS)||30,tokensPerFeature:Number(c.env.AI_USER_DAILY_TOKEN_BUDGET)||40000},resetsAt:'00:00 UTC'});
});
aca.get('/search',async c=>{
 const q=c.req.query('q')?.trim()??'';if(q.length<3||q.length>500)return c.json({error:'Scrivi una ricerca da 3 a 500 caratteri'},400);
 return c.json({hits:await searchAuthorized(c.env,await resolveClassId(c),c.get('jwtPayload').sub,q)});
});
aca.post('/task',async c=>{
 const body=await c.req.json<{feature:AiFeature;question:string;context?:string;localOnly?:boolean;includeAppContext?:boolean}>();
 if(!AI_FEATURES.includes(body.feature)||['TRANSCRIBE','IMAGE','PERABOT'].includes(body.feature)||typeof body.question!=='string'||!body.question.trim())return c.json({error:'Richiesta non valida'},400);
 const user=c.get('jwtPayload');const classId=await resolveClassId(c);let context=typeof body.context==='string'?body.context:'';
 if(body.includeAppContext){const hits=await searchAuthorized(c.env,classId,user.sub,body.question);context+=`\nFONTI AILA:\n${JSON.stringify(hits)}`;}
 if(body.feature==='STUDY_PLAN'){
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(new Date());
  const events=await c.env.DB.prepare(`SELECT id,title,event_date,start_time,category FROM calendar_events WHERE class_id=? AND event_date>=?
  AND (is_for_all=1 OR created_by=? OR EXISTS(SELECT 1 FROM json_each(visible_to_user_ids_json) WHERE value=?)) ORDER BY event_date LIMIT 30`).bind(classId,today,user.sub,user.sub).all();
  context+=`\nOGGI: ${today}\nIMPEGNI REALI:\n${JSON.stringify(events.results)}\nI blocchi di studio sono suggerimenti, non eventi già presenti o creati.`;
 }
 const result=await runAiTask(c.env,user.sub,{feature:body.feature,question:body.question,context:context.slice(0,48_000),attachment:body.feature==='DOCUMENT',localOnly:body.localOnly});return c.json(result);
});
aca.get('/nudges',async c=>{
 const user=c.get('jwtPayload');const pref=await aiPreferences(c.env,user.sub);if(!pref.aiEnabled||!pref.acaEnabled||!pref.services.includes('NOW_NUDGE')||!pref.nudgesEnabled)return c.json({nudges:[]});
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome'}).format(new Date());
 const limit=new Date(Date.parse(today+'T00:00:00Z')+3*86400000).toISOString().slice(0,10);
 const events=await c.env.DB.prepare(`SELECT e.id,e.title,e.event_date,e.category FROM calendar_events e WHERE e.class_id=?
 AND e.event_date>=? AND e.event_date<=? AND (e.is_for_all=1 OR e.created_by=? OR EXISTS(SELECT 1 FROM json_each(e.visible_to_user_ids_json) WHERE value=?))
 AND NOT EXISTS(SELECT 1 FROM aca_nudge_dismissals d WHERE d.user_id=? AND d.event_id=e.id AND d.event_date=e.event_date)
 ORDER BY e.event_date,CASE WHEN e.category IN ('VERIFICA','INTERROGAZIONE','PAGAMENTO') THEN 0 ELSE 1 END LIMIT 3`)
 .bind(await resolveClassId(c),today,limit,user.sub,user.sub,user.sub).all<{id:string;title:string;event_date:string;category:string}>();
 return c.json({nudges:events.results.map(e=>({id:e.id,eventId:e.id,eventDate:e.event_date,title:e.title,
  message:`${e.event_date===today?'Oggi':e.event_date}: ${e.title}`,
  suggestion:['VERIFICA','INTERROGAZIONE'].includes(e.category)?'Vuoi preparare un piano di ripasso?':'Controlla i dettagli nel calendario.',
  canPlanStudy:['VERIFICA','INTERROGAZIONE'].includes(e.category)}))});
});
aca.post('/nudges/:id/dismiss',async c=>{
 const user=c.get('jwtPayload');const row=await c.env.DB.prepare(`SELECT id,event_date FROM calendar_events WHERE id=? AND class_id=?
 AND (is_for_all=1 OR created_by=? OR EXISTS(SELECT 1 FROM json_each(visible_to_user_ids_json) WHERE value=?))`).bind(c.req.param('id'),await resolveClassId(c),user.sub,user.sub).first<{id:string;event_date:string}>();
 if(!row)return c.json({error:'Evento non trovato'},404);
 await c.env.DB.prepare('INSERT OR IGNORE INTO aca_nudge_dismissals(user_id,event_id,event_date) VALUES (?,?,?)').bind(user.sub,row.id,row.event_date).run();return c.json({success:true});
});
aca.get('/experience',c=>{
 const safe=(s:string|undefined)=>{try{const u=new URL(s??'');return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch{return null;}};
 return c.json({id:'aca-pere-2026',title:'La tua classe. Un passo avanti.',subtitle:'AILA ACA ed Educazione tra pere',videoUrl:safe(c.env.HOME_VIDEO_URL),posterUrl:safe(c.env.HOME_VIDEO_POSTER_URL)});
});
export default aca;
