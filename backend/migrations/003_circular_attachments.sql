-- =============================================================================
-- AILA — Migrazione 003: allegati delle circolari (colonna "Allegati" di Spaggiari)
-- =============================================================================
--
-- Lo scraper leggeva solo la colonna "PDF" di ogni riga; la colonna "Allegati" (es. "Allegato
-- a/b/c/d", o un singolo link "Allegati" verso un'altra pagina) veniva ignorata e quei documenti
-- non comparivano mai in app.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/003_circular_attachments.sql
--
-- Non ripetibile — vedi nota sugli ALTER TABLE in 001_multiclasse.sql: al secondo giro
-- "duplicate column name" è innocuo, vuol dire che è già applicata.

ALTER TABLE circulars ADD COLUMN attachments_json TEXT NOT NULL DEFAULT '[]';
