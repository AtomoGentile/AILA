/** Class visibility is not personal relevance: published oral-exam calendars are public. */
export function studyAudience(alias:string, includeNotes=true):string {
 return `(CASE
  WHEN ${alias}.visible_to_user_ids_json IS NOT NULL THEN EXISTS(SELECT 1 FROM json_each(${alias}.visible_to_user_ids_json) WHERE value=?)
  WHEN ${alias}.poll_grid_id IS NOT NULL THEN EXISTS(
   SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id=a.slot_id
   WHERE s.grid_id=${alias}.poll_grid_id AND s.slot_date=${alias}.event_date AND a.student_id=?)
  WHEN ${includeNotes ? '1' : '0'} AND ${alias}.category='INTERROGAZIONE' AND ${alias}.notes LIKE 'Studenti: %' THEN EXISTS(
   SELECT 1 FROM users u WHERE u.id=? AND instr(', '||lower(substr(${alias}.notes,11))||', ', ', '||lower(trim(u.first_name)||' '||trim(u.last_name))||', ')>0
   AND (SELECT COUNT(*) FROM users same WHERE same.class_id=${alias}.class_id AND lower(trim(same.first_name)||' '||trim(same.last_name))=lower(trim(u.first_name)||' '||trim(u.last_name)))=1)
  ELSE (${alias}.is_for_all=1) END)`;
}
