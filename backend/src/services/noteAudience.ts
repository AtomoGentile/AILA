interface Student {id:string;first_name:string;last_name:string;}
const normalize=(value:string)=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const contains=(text:string,name:string)=>!!name && ` ${text} `.includes(` ${name} `);

/** Interpret explicit recipient clauses only, without treating teachers or incidental names as recipients. */
export function noteAudienceIncludes(notes:string|null,roster:Student[],userId:string):boolean {
 if(!notes?.trim())return true;
 const clauses=[...notes.matchAll(/\b(?:(?:student[ie]|alunn[ioe]|interrogat[ioe]|destinatar[io]|partecipanti)(?:\s+(?:coinvolt[ioe]|interessat[ioe]|previsti|selezionati))?\s*:|(?:solo\s+per|riservat[oa]\s+a|rivolt[oa]\s+a|interrogazion[ei]\s+(?:di|per)|per)\s+)\s*([^.;]+)/giu)];
 if(!clauses.length)return true;
 const audience=new Set<string>();
 let restricted=false;
 for(const clause of clauses){
  const text=normalize(clause[1].split(/\n\s*[\p{L} ]+:/u)[0]);
  if(/\b(?:tutt[ai]|intera classe|tutta la classe)\b/.test(text))return true;
  const genericPer=/^per\s+/iu.test(clause[0]);
  if(genericPer && !roster.some(student=>{
   const first=normalize(student.first_name),last=normalize(student.last_name);
   return [`${first} ${last}`,`${last} ${first}`,last].some(name=>!!name && (` ${text} `).startsWith(` ${name} `));
  }))continue;
  const matches=roster.filter(student=>{
   const first=normalize(student.first_name),last=normalize(student.last_name);
   if(contains(text,`${first} ${last}`)||contains(text,`${last} ${first}`))return true;
   return contains(text,last)&&roster.filter(other=>normalize(other.last_name)===last).length===1;
  });
  restricted=true;matches.forEach(student=>audience.add(student.id));
 }
 return !restricted || audience.has(userId);
}
