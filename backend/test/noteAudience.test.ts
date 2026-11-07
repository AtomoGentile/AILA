import {describe,it,expect} from 'vitest';
import {noteAudienceIncludes} from '../src/services/noteAudience';
const roster=[{id:'me',first_name:'Nicolò',last_name:'D’Amico'},{id:'peer',first_name:'Giulia',last_name:'Rossi'}];
describe('personal audience in event notes',()=>{
 it.each(['Studenti: Giulia Rossi','Interrogati: ROSSI Giulia','Solo per Rossi','Per Giulia Rossi','Alunni coinvolti: Giulia Rossi','Interrogazione di Giulia Rossi','Studenti:\n- Giulia Rossi\nNote: capitolo 4'])('excludes classmates from %s',notes=>{
  expect(noteAudienceIncludes(notes,roster,'me')).toBe(false);
  expect(noteAudienceIncludes(notes,roster,'peer')).toBe(true);
 });
 it('normalizes accents, apostrophes and spacing',()=>{
  expect(noteAudienceIncludes('Destinatari: NICOLO D AMICO, Giulia Rossi',roster,'me')).toBe(true);
 });
 it('keeps generic notes and incidental names',()=>{
  for(const notes of [null,'Ripassare il capitolo 5','Per il ripasso portare il libro','Chiedere gli appunti a Giulia Rossi','Per il ripasso confrontarsi con Giulia Rossi'])expect(noteAudienceIncludes(notes,roster,'me')).toBe(true);
 });
 it('avoids assigning ambiguous surnames',()=>{
  const people=[...roster,{id:'other',first_name:'Luca',last_name:'Rossi'}];
  expect(noteAudienceIncludes('Studenti: Rossi',people,'peer')).toBe(false);
  expect(noteAudienceIncludes('Studenti: Giulia Rossi',people,'peer')).toBe(true);
  expect(noteAudienceIncludes('Studenti: Giulia Rossi',people,'other')).toBe(false);
 });
 it('keeps explicit whole-class events',()=>expect(noteAudienceIncludes('Studenti: tutta la classe',roster,'me')).toBe(true));
});
