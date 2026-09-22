-- =============================================================================
-- AILA — Migrazione 006: livello di qualita' dell'analisi AI
-- =============================================================================
--
-- Prima chiunque poteva sovrascrivere l'analisi salvata, qualunque fosse la qualita': un
-- riassunto dell'AI locale (o il ripiego euristico a parole chiave) arrivato dopo cancellava
-- quello di Gemini. Ora ogni riga ha un livello e il server accetta solo un livello uguale o
-- superiore a quello gia' salvato:
--   0 = ripiego euristico (non viene piu' salvato, restano solo le righe vecchie)
--   1 = AI locale sul telefono
--   2 = Google Gemini (dal telefono con la chiave personale, o dal server)
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/006_analysis_tier.sql
--
-- ALTER TABLE ADD COLUMN non e' rieseguibile: se la colonna esiste gia' il comando fallisce,
-- e va bene cosi' (vuol dire che la migrazione e' gia' stata applicata).

ALTER TABLE circular_ai_analysis ADD COLUMN tier INTEGER NOT NULL DEFAULT 1;

UPDATE circular_ai_analysis SET tier = CASE
    WHEN is_fallback = 1 THEN 0
    WHEN model_label LIKE 'Google Gemini%' THEN 2
    ELSE 1
END;

-- Per GET /api/circulars/analyses?since=... (aggiornamento dei riassunti mentre l'app e' aperta).
CREATE INDEX IF NOT EXISTS idx_circular_ai_analysis_updated ON circular_ai_analysis(updated_at);

-- Tentativi del riassunto fatto dal server (services/summarizer.ts): senza questo conteggio una
-- circolare che Gemini non riesce a leggere verrebbe ritentata a ogni giro del cron (96 volte al
-- giorno), consumando la quota della chiave per niente.
CREATE TABLE IF NOT EXISTS circular_ai_server_attempts (
    circular_number INTEGER PRIMARY KEY REFERENCES circulars(number) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
