-- =============================================================================
-- AILA — Migrazione 015: da quale circolare nasce un evento del calendario
-- =============================================================================
--
-- Gli eventi inseriti da AILA Assistant partono da una scadenza riconosciuta in una circolare.
-- `circular_number` ricorda quale, cosi' dal dettaglio dell'evento si riapre la circolare.
-- NULL per gli eventi scritti a mano e per quelli creati prima di questa migrazione.
--
-- Contiene un ALTER TABLE: non va lanciata due volte (la seconda fallirebbe, senza danni).
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/015_calendar_circular.sql

ALTER TABLE calendar_events ADD COLUMN circular_number INTEGER;
