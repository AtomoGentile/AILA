-- =============================================================================
-- AILA — Migrazione 012: analisi delle circolari per classe
-- =============================================================================
--
-- Il riassunto di Gemini resta uno per circolare (una sola chiamata per tutto l'istituto), ma ora
-- contiene anche badge e nota per ciascuna classe registrata, e le scadenze dicono a quali classi
-- valgono. Ognuno riceve la versione della propria classe (services/classAnalysis.ts).
--
-- `per_class_json`: {"<class_id>": {"badge": "...", "note": "..."}}. NULL = analisi senza dati per
-- classe (fatta da un telefono o prima di questa migrazione): vale uguale per tutti.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/012_analysis_per_class.sql

ALTER TABLE circular_ai_analysis ADD COLUMN per_class_json TEXT;
