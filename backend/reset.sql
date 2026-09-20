-- Cancella tutte le tabelle esistenti sul database D1 remoto, incluse quelle rimaste da uno
-- schema precedente e incompatibile (es. "app_config" senza la colonna "class_label", causa
-- dell'errore "table app_config has no column named class_label"). Da eseguire una tantum prima
-- di riapplicare schema.sql, dato che CREATE TABLE IF NOT EXISTS non altera una tabella già
-- esistente con una struttura diversa/vecchia.
DROP TABLE IF EXISTS swap_requests;
DROP TABLE IF EXISTS fcm_tokens;
DROP TABLE IF EXISTS seat_map_history;
DROP TABLE IF EXISTS interrogation_assignments;
DROP TABLE IF EXISTS student_sacrifice_bonus;
DROP TABLE IF EXISTS interrogation_votes;
DROP TABLE IF EXISTS interrogation_slots;
DROP TABLE IF EXISTS interrogation_grids;
DROP TABLE IF EXISTS anonymity_unlock_approvals;
DROP TABLE IF EXISTS anonymity_unlock_requests;
DROP TABLE IF EXISTS anonymity_unlock_audits;
DROP TABLE IF EXISTS proposal_comments;
DROP TABLE IF EXISTS proposal_votes;
DROP TABLE IF EXISTS proposals;
DROP TABLE IF EXISTS calendar_events;
DROP TABLE IF EXISTS circulars;
DROP TABLE IF EXISTS social_preferences;
DROP TABLE IF EXISTS representative_ratings;
DROP TABLE IF EXISTS student_profiles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS app_config;
