-- =============================================================================
-- AILA — Migrazione 020: ore di lezione della classe (5 o 6)
-- =============================================================================
--
-- Alcune classi fanno 5 ore (8:00-13:00), altre 6 (8:00-14:00). L'app propone nel calendario solo
-- le ore che la classe ha, e traduce "alle 9" nell'ora di lezione giusta. Lo sceglie il
-- Rappresentante alla registrazione (la classe nasce con lui) e lo puo' cambiare dalla Scheda
-- Classe (PUT /api/preferences/lesson-hours).
--
-- Le classi gia' esistenti partono da 6, il valore di prima: il loro Rappresentante lo imposta a 5
-- dalla Scheda Classe se serve. ALTER TABLE ... ADD COLUMN non ha IF NOT EXISTS: va lanciata UNA
-- volta sola (una seconda volta SQLite risponde "duplicate column name", senza danni).
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/020_class_lesson_hours.sql

ALTER TABLE classes ADD COLUMN lesson_hours INTEGER NOT NULL DEFAULT 6 CHECK (lesson_hours IN (5, 6));
