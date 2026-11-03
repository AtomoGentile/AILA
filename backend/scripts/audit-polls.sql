WITH chosen AS (
  SELECT g.* FROM interrogation_grids g
  WHERE EXISTS (SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = g.id)
  ORDER BY g.created_at DESC LIMIT 50
)
SELECT 'grid' AS kind, json_object('id', g.id, 'subject', g.subject, 'createdAt', g.created_at) AS data FROM chosen g
UNION ALL SELECT 'slot', json_object('id', s.id, 'gridId', s.grid_id, 'date', s.slot_date, 'capacity', s.capacity) FROM interrogation_slots s JOIN chosen g ON g.id = s.grid_id
UNION ALL SELECT 'vote', json_object('studentId', v.student_id, 'slotId', v.slot_id, 'score', v.vote_score) FROM interrogation_votes v JOIN interrogation_slots s ON s.id = v.slot_id JOIN chosen g ON g.id = s.grid_id
UNION ALL SELECT 'assignment', json_object('studentId', a.student_id, 'slotId', a.slot_id) FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id JOIN chosen g ON g.id = s.grid_id
UNION ALL SELECT 'swap', json_object('gridId', r.grid_id) FROM swap_requests r JOIN chosen g ON g.id = r.grid_id WHERE r.status = 'ACCEPTED';
