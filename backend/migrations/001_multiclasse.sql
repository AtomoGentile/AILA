-- =============================================================================
-- AILA — Migrazione 001: da classe unica a multi-classe (+ invio e scadenza sondaggi)
-- =============================================================================
--
-- Da usare su un database D1 GIÀ POPOLATO. schema.sql crea tutto da zero e cancellerebbe
-- il senso di questa migrazione: se stai partendo da un database vuoto usa quello.
--
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/001_multiclasse.sql
--
-- È scritta per poter essere rieseguita: le CREATE TABLE sono IF NOT EXISTS e le INSERT sono
-- OR IGNORE. Gli ALTER TABLE invece NON sono ripetibili — SQLite non ha "ADD COLUMN IF NOT
-- EXISTS" — quindi al secondo giro danno "duplicate column name": è un errore innocuo, vuol
-- dire che quel pezzo era già stato applicato.
--
-- Tutti i dati esistenti finiscono nella classe 'DEFAULT_CLASS', che è esattamente quello che
-- erano prima: la classe unica.

-- 1. Elenco delle classi ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS classes (
    id TEXT PRIMARY KEY,
    label TEXT UNIQUE NOT NULL,
    academic_year TEXT NOT NULL DEFAULT '2026/2027',
    preferences_open BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- La classe che c'era prima. L'etichetta la prende da app_config, se esiste, così non si
-- perde il nome vero già configurato.
INSERT OR IGNORE INTO classes (id, label, preferences_open)
SELECT 'DEFAULT_CLASS',
       COALESCE((SELECT class_label FROM app_config WHERE class_id = 'DEFAULT_CLASS'), '4 CSA'),
       COALESCE((SELECT preferences_open FROM app_config WHERE class_id = 'DEFAULT_CLASS'), 0);

-- Rete di sicurezza se app_config non esisteva affatto.
INSERT OR IGNORE INTO classes (id, label) VALUES ('DEFAULT_CLASS', '4 CSA');

-- 2. Colonna classe sulle tabelle di classe ------------------------------------------------
ALTER TABLE users ADD COLUMN class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS';
ALTER TABLE calendar_events ADD COLUMN class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS';
ALTER TABLE proposals ADD COLUMN class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS';
ALTER TABLE interrogation_grids ADD COLUMN class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS';
ALTER TABLE seat_map_history ADD COLUMN class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS';

-- Le righe già presenti hanno preso il DEFAULT, ma se qualcuna fosse rimasta NULL
-- (colonna aggiunta a mano in precedenza) la si sistema qui.
UPDATE users SET class_id = 'DEFAULT_CLASS' WHERE class_id IS NULL OR class_id = '';
UPDATE calendar_events SET class_id = 'DEFAULT_CLASS' WHERE class_id IS NULL OR class_id = '';
UPDATE proposals SET class_id = 'DEFAULT_CLASS' WHERE class_id IS NULL OR class_id = '';
UPDATE interrogation_grids SET class_id = 'DEFAULT_CLASS' WHERE class_id IS NULL OR class_id = '';
UPDATE seat_map_history SET class_id = 'DEFAULT_CLASS' WHERE class_id IS NULL OR class_id = '';

CREATE INDEX IF NOT EXISTS idx_users_class ON users(class_id);
CREATE INDEX IF NOT EXISTS idx_calendar_class ON calendar_events(class_id);
CREATE INDEX IF NOT EXISTS idx_proposals_class ON proposals(class_id);
CREATE INDEX IF NOT EXISTS idx_grids_class ON interrogation_grids(class_id);
CREATE INDEX IF NOT EXISTS idx_seatmap_class ON seat_map_history(class_id);

-- 3. Bacheca: dicitura "Modificato" ---------------------------------------------------------
-- modified_by_rep esisteva già ma si accendeva solo per le modifiche del Rappresentante e non
-- diceva quando. edited_at serve a mostrare "Modificato" anche quando è l'autore a correggersi.
ALTER TABLE proposals ADD COLUMN edited_at DATETIME;

-- 4. Sondaggi: invio definitivo e scadenza ---------------------------------------------------
ALTER TABLE interrogation_grids ADD COLUMN closes_at DATETIME;

CREATE TABLE IF NOT EXISTS interrogation_submissions (
    grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (grid_id, student_id)
);
