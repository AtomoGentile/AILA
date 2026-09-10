-- =============================================================================
-- AILA — Migrazione 004: note e destinatari specifici degli eventi calendario
-- =============================================================================
--
-- L'app (creazione manuale e assistente AI) manda da tempo "notes" e "visibleToUserIds" a
-- POST /api/calendar, ma la tabella non aveva le colonne per salvarli: la route li ignorava
-- silenziosamente, quindi non venivano né persistiti né restituiti da GET /api/calendar. Da qui
-- il dettaglio evento in app mostrava sempre "Tutta la classe" e nessuna nota, anche per eventi
-- creati con note o destinatari specifici.
--
-- visible_to_user_ids_json è testo JSON (un array di id utente, es. '["u1","u2"]'), NULL quando
-- l'evento è per tutta la classe — stesso pattern di attachments_json in 003.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/004_calendar_notes_visibility.sql
--
-- Non ripetibile — vedi nota sugli ALTER TABLE in 001_multiclasse.sql: al secondo giro
-- "duplicate column name" è innocuo, vuol dire che è già applicata.

ALTER TABLE calendar_events ADD COLUMN notes TEXT;
ALTER TABLE calendar_events ADD COLUMN visible_to_user_ids_json TEXT;
