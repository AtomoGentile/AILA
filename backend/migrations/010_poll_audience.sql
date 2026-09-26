-- =============================================================================
-- AILA — Migrazione 010: destinatari dei sondaggi
-- =============================================================================
--
-- Il Rappresentante può rivolgere un sondaggio (a ordinamento o interrogazioni) solo ad alcune
-- persone della classe invece che a tutti: per esempio le interrogazioni solo per chi non ha
-- ancora il voto, o una domanda solo per chi partecipa a un'attività.
--
-- `audience_json`: elenco JSON degli id utente destinatari. NULL = tutta la classe (com'era prima).
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/010_poll_audience.sql

ALTER TABLE ranking_polls ADD COLUMN audience_json TEXT;
ALTER TABLE interrogation_grids ADD COLUMN audience_json TEXT;
