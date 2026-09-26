-- =============================================================================
-- CIRCOLARE+ / AILA — SCHEMA DATABASE CLOUDFLARE D1 (SQLITE DISTRIBUITO)
-- Versione: 4.0 — Multi-classe
-- =============================================================================
--
-- COSA È CAMBIATO RISPETTO ALLA 3.0
-- La 3.0 assumeva "classe singola implicita server-side": una riga in app_config con
-- class_id = 'DEFAULT_CLASS', e nessuna tabella che sapesse a quale classe appartenesse un
-- utente, un evento o una proposta. Bastava che si registrasse uno studente di un'altra classe
-- per vedere la bacheca, il calendario e la mappa posti altrui.
--
-- Ora ogni utente appartiene a una classe (`users.class_id`) che sceglie in fase di
-- registrazione, e i contenuti di classe portano la stessa colonna. Le circolari restano
-- volutamente FUORI da questa divisione: arrivano da Spaggiari e valgono per tutto l'istituto,
-- quindi duplicarle per classe significherebbe scaricare lo stesso PDF N volte.
--
-- Per un database già popolato non usare questo file: c'è migrations/001_multiclasse.sql, che
-- aggiunge le colonne senza distruggere i dati.

-- 1. CLASSI
-- L'elenco non è precompilato con classi inventate: parte da quella che esiste già e cresce
-- quando qualcuno si registra indicando una classe nuova (la rotta di registrazione valida il
-- formato dell'etichetta prima di crearla, così non nascono voci come "4a csa " e "4^CSA").
CREATE TABLE IF NOT EXISTS classes (
    id TEXT PRIMARY KEY,
    label TEXT UNIQUE NOT NULL,           -- come la scrive uno studente: "4 CSA"
    academic_year TEXT NOT NULL DEFAULT '2026/2027',
    preferences_open BOOLEAN NOT NULL DEFAULT 0,
    -- La Guardia di Sicurezza della classe: terza firma per svelare un autore anonimo. La sceglie
    -- il Rappresentante nella Scheda Classe. Resta uno studente a tutti gli effetti (sondaggi,
    -- preferenze, valutazioni): per questo è una designazione sulla classe e non un ruolo.
    -- Senza REFERENCES: `classes` viene creata prima di `users`, e con le chiavi esterne attive
    -- (come su D1) un riferimento in avanti fa fallire perfino l'INSERT della classe iniziale.
    -- Alla cancellazione di un utente la pulisce users.ts.
    security_guard_id TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- La classe che esisteva prima, per non perdere i dati già inseriti.
INSERT OR IGNORE INTO classes (id, label, preferences_open)
VALUES ('DEFAULT_CLASS', '4 CSA', 0);

-- 1b. CONFIGURAZIONE GLOBALE (retrocompatibilità)
-- Resta per non rompere installazioni esistenti, ma `preferences_open` che conta è ora quello
-- della singola classe: una classe che apre le preferenze non deve aprirle a tutte le altre.
CREATE TABLE IF NOT EXISTS app_config (
    class_id TEXT PRIMARY KEY DEFAULT 'DEFAULT_CLASS',
    class_label TEXT NOT NULL DEFAULT '4^ CSA',
    preferences_open BOOLEAN NOT NULL DEFAULT 0,
    academic_year TEXT NOT NULL DEFAULT '2026/2027',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO app_config (class_id, class_label, preferences_open)
VALUES ('DEFAULT_CLASS', '4^ CSA', 0);

-- 2. TABELLA UTENTI (ognuno appartiene a una classe)
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT CHECK(role IN ('STUDENT', 'REPRESENTATIVE', 'SECURITY_GUARD')) NOT NULL DEFAULT 'STUDENT',
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_class ON users(class_id);

-- 3. PROFILO STUDENTE
-- Altezza salvata una tantum al login (range 140-210 cm, intervalli discreti di 5cm)
CREATE TABLE IF NOT EXISTS student_profiles (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    height_cm INTEGER NOT NULL CHECK(height_cm >= 140 AND height_cm <= 210 AND height_cm % 5 = 0),
    priority_pass BOOLEAN NOT NULL DEFAULT 0,
    notification_board_enabled BOOLEAN NOT NULL DEFAULT 1
);

-- 4. VALUTAZIONI RISERVATE DEL RAPPRESENTANTE (1-5)
CREATE TABLE IF NOT EXISTS representative_ratings (
    student_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    didactic INTEGER NOT NULL CHECK(didactic BETWEEN 1 AND 5),
    behavior INTEGER NOT NULL CHECK(behavior BETWEEN 1 AND 5),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Coppie da separare per disciplina (migrazione 007): segnate dal Rappresentante, con scadenza.
-- L'ottimizzatore penalizza solo quel banco, senza vietarlo.
CREATE TABLE IF NOT EXISTS discipline_pairs (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL,
    student_a TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_b TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at DATE NOT NULL,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(class_id, student_a, student_b)
);

-- 5. PREFERENZE INTERPERSONALI (+2, +1, 0, -1, -2)
-- Attive solo quando preferences_open = 1
CREATE TABLE IF NOT EXISTS social_preferences (
    from_student_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    to_student_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    score INTEGER NOT NULL CHECK(score IN (-2, -1, 0, 1, 2)),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_student_id, to_student_id)
);

-- 6. CIRCOLARI SCOLASTICHE (Deduplicazione Spaggiari & Cache R2)
-- Volutamente NON divise per classe: Spaggiari le pubblica per tutto l'istituto e il PDF è lo
-- stesso per tutti. È l'analisi AI, che gira sul telefono con la chiave dello studente, a dire
-- se una circolare riguarda o no chi la sta leggendo.
CREATE TABLE IF NOT EXISTS circulars (
    number INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    publish_date DATE NOT NULL,
    r2_pdf_key TEXT NOT NULL,
    original_url TEXT,
    -- Allegati della colonna "Allegati" della tabella Spaggiari (0, 1 o piu' per circolare: es.
    -- "Allegato a/b/c/d", oppure un singolo link "Allegati" verso un'altra pagina del sito). JSON
    -- di oggetti { label, pdfKey? , url? }: pdfKey quando il file e' stato scaricato e messo in
    -- cache su R2 come il PDF principale, url quando punta altrove (pagina non-PDF) e si apre
    -- esternamente. Default '[]' additivo: le righe scritte prima di questa colonna restano valide.
    attachments_json TEXT NOT NULL DEFAULT '[]',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 6b. ANALISI AI DELLE CIRCOLARI (Cache condivisa)
-- Una circolare vale per tutto l'istituto e (finora) il contesto studente passato all'AI è
-- sempre lo stesso default per classe, quindi la prima analisi fatta da qualcuno vale per tutti:
-- niente lo rifà da capo consumando quota/tempo per lo stesso risultato. La chiave è solo
-- circular_number: se in futuro l'analisi diventasse per-classe/per-studente, andrà aggiunta una
-- colonna alla chiave primaria invece di riusare questa tabella così com'è.
-- Salva solo l'ESITO dell'analisi (badge, riassunto, scadenze), non il testo estratto dal PDF.
-- `tier` è il livello di qualità (0 ripiego euristico, 1 AI locale, 2 Gemini): il PUT accetta solo
-- un livello uguale o superiore a quello salvato, quindi un riassunto locale non sostituisce mai
-- quello di Gemini. Se è impostato il secret GEMINI_API_KEY, il cron lo produce direttamente sul
-- server (services/summarizer.ts) mandando a Gemini il PDF già in cache su R2.
CREATE TABLE IF NOT EXISTS circular_ai_analysis (
    circular_number INTEGER PRIMARY KEY REFERENCES circulars(number) ON DELETE CASCADE,
    badge TEXT NOT NULL CHECK(badge IN ('RELEVANT', 'POTENTIAL', 'NOT_RELEVANT')),
    summary TEXT NOT NULL,
    deadlines_json TEXT NOT NULL DEFAULT '[]',
    is_fallback BOOLEAN NOT NULL DEFAULT 0,
    model_label TEXT NOT NULL DEFAULT 'Sconosciuto',
    submitted_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    tier INTEGER NOT NULL DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_circular_ai_analysis_updated ON circular_ai_analysis(updated_at);

-- Tentativi del riassunto fatto dal server: una circolare che Gemini non riesce a leggere non
-- viene ritentata all'infinito (vedi services/summarizer.ts).
CREATE TABLE IF NOT EXISTS circular_ai_server_attempts (
    circular_number INTEGER PRIMARY KEY REFERENCES circulars(number) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 7. CALENDARIO DI CLASSE ED EVENTI AI
CREATE TABLE IF NOT EXISTS calendar_events (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    event_date DATE NOT NULL,
    start_time TEXT,
    category TEXT NOT NULL CHECK(category IN ('VERIFICA', 'INTERROGAZIONE', 'PAGAMENTO', 'USCITA_DIDATTICA', 'AVVISO', 'ALTRO')),
    is_for_all BOOLEAN NOT NULL DEFAULT 1,
    is_ai_generated BOOLEAN NOT NULL DEFAULT 0,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    -- Array JSON di id utente (es. '["u1","u2"]'), NULL quando is_for_all = 1.
    visible_to_user_ids_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_calendar_class ON calendar_events(class_id);

-- 8. BACHECA PROPOSTE CON OPZIONE ANONIMATO
CREATE TABLE IF NOT EXISTS proposals (
    id TEXT PRIMARY KEY,
    author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_anonymous BOOLEAN NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'GENERALE',
    status TEXT NOT NULL CHECK(status IN ('NUOVA', 'IN_ANALISI', 'CHIUSA')) DEFAULT 'NUOVA',
    modified_by_rep BOOLEAN NOT NULL DEFAULT 0,
    -- Esito di una proposta chiusa (status = 'CHIUSA'). NULL finché è nuova o in analisi, e per
    -- le proposte chiuse prima che l'esito esistesse. Colonna a parte perché il CHECK su
    -- `status` non si può alterare in SQLite senza ricreare la tabella.
    outcome TEXT CHECK(outcome IN ('ACCETTATA', 'RIFIUTATA')),
    -- Valorizzata a ogni modifica del testo, da chiunque provenga: serve alla dicitura
    -- "Modificato" in bacheca. modified_by_rep resta separata perché distingue il caso in cui a
    -- modificare sia stato il Rappresentante e non l'autore.
    edited_at DATETIME,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proposals_class ON proposals(class_id);

-- Voti favorevoli (+1) e contrari (-1) alle proposte
CREATE TABLE IF NOT EXISTS proposal_votes (
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_type INTEGER NOT NULL CHECK(vote_type IN (-1, 1)),
    PRIMARY KEY (proposal_id, user_id)
);

-- Commenti alle proposte
CREATE TABLE IF NOT EXISTS proposal_comments (
    id TEXT PRIMARY KEY,
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_anonymous BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Audit sblocco identità anonima (Quorum: 2 Rappresentanti + 1 Guardia di Sicurezza)
CREATE TABLE IF NOT EXISTS anonymity_unlock_audits (
    id TEXT PRIMARY KEY,
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    rep_1_id TEXT NOT NULL REFERENCES users(id),
    rep_2_id TEXT NOT NULL REFERENCES users(id),
    security_guard_id TEXT NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    unlocked_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Richieste di svelamento dell'autore (di una proposta o di un commento anonimi).
-- Quorum: 2 Rappresentanti + 1 Guardia di Sicurezza, ognuno con la propria approvazione.
CREATE TABLE IF NOT EXISTS anonymity_unlock_requests (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    -- NULL = si chiede l'autore della proposta; valorizzato = si chiede l'autore del commento.
    comment_id TEXT REFERENCES proposal_comments(id) ON DELETE CASCADE,
    requested_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PENDING', 'APPROVED', 'REJECTED')) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_unlock_requests_class ON anonymity_unlock_requests(class_id, status);
CREATE INDEX IF NOT EXISTS idx_unlock_requests_proposal ON anonymity_unlock_requests(proposal_id);

CREATE TABLE IF NOT EXISTS anonymity_unlock_approvals (
    request_id TEXT NOT NULL REFERENCES anonymity_unlock_requests(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK(role IN ('REPRESENTATIVE', 'SECURITY_GUARD')),
    approved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, user_id)
);

-- 9. SONDAGGI INTERROGAZIONI & SLOT DATE
CREATE TABLE IF NOT EXISTS interrogation_grids (
    id TEXT PRIMARY KEY,
    subject TEXT NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT 0,
    -- Scadenza della compilazione. Passata questa data l'algoritmo può girare anche se manca
    -- qualcuno: senza, bastava un compagno che non votava per bloccare tutta la classe.
    closes_at DATETIME,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_grids_class ON interrogation_grids(class_id);

-- Invio definitivo delle scelte di uno studente per una griglia.
-- Prima l'invio era solo un flag locale sul telefono: il Rappresentante non poteva sapere chi
-- avesse finito, e l'algoritmo non aveva modo di partire "quando hanno votato tutti".
CREATE TABLE IF NOT EXISTS interrogation_submissions (
    grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (grid_id, student_id)
);

CREATE TABLE IF NOT EXISTS interrogation_slots (
    id TEXT PRIMARY KEY,
    grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
    slot_date DATE NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 1,
    teacher_mandatory BOOLEAN NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS interrogation_votes (
    slot_id TEXT NOT NULL REFERENCES interrogation_slots(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_score INTEGER NOT NULL CHECK(vote_score IN (50, 0, -80, -300)),
    PRIMARY KEY (slot_id, student_id)
);

-- Tracciamento Storico Bonus Sacrificio (+100 / +250 pt per materia)
CREATE TABLE IF NOT EXISTS student_sacrifice_bonus (
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject TEXT NOT NULL,
    bonus_points INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (student_id, subject)
);

-- Assegnazioni e scambio posti (Swap)
CREATE TABLE IF NOT EXISTS interrogation_assignments (
    id TEXT PRIMARY KEY,
    slot_id TEXT NOT NULL REFERENCES interrogation_slots(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (slot_id, student_id)
);

-- 9b. SONDAGGI A ORDINAMENTO (ognuno ordina le opzioni, classifica a punti Borda)
CREATE TABLE IF NOT EXISTS ranking_polls (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    question TEXT NOT NULL,
    -- Chiuso dal Rappresentante: le classifiche non si cambiano più.
    is_closed BOOLEAN NOT NULL DEFAULT 0,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ranking_polls_class ON ranking_polls(class_id);

CREATE TABLE IF NOT EXISTS ranking_poll_options (
    id TEXT PRIMARY KEY,
    poll_id TEXT NOT NULL REFERENCES ranking_polls(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    -- Ordine in cui il Rappresentante le ha scritte: è anche l'ordine di partenza per chi vota.
    position INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ranking_options_poll ON ranking_poll_options(poll_id);

-- Una riga per opzione per votante: rank 1 = la preferita.
CREATE TABLE IF NOT EXISTS ranking_poll_answers (
    poll_id TEXT NOT NULL REFERENCES ranking_polls(id) ON DELETE CASCADE,
    option_id TEXT NOT NULL REFERENCES ranking_poll_options(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL,
    PRIMARY KEY (poll_id, user_id, option_id)
);

-- 10. STORICO DISPOSIZIONE MAPPA POSTI (Finestra regressiva su 4 mappe)
CREATE TABLE IF NOT EXISTS seat_map_history (
    id TEXT PRIMARY KEY,
    map_index INTEGER NOT NULL CHECK(map_index BETWEEN 1 AND 4), -- 1 per N-1, 2 per N-2, 3 per N-3, 4 per N-4
    layout_json TEXT NOT NULL,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    published_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_seatmap_class ON seat_map_history(class_id);

-- 11. TOKEN FCM DISPOSITIVI (Notifiche Push)
-- Un token per utente per piattaforma
CREATE TABLE IF NOT EXISTS fcm_tokens (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT NOT NULL CHECK(platform IN ('android', 'ios')),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- Preferenze del dispositivo (migrazione 009), usate per i messaggi iOS: vedi services/fcm.ts.
    muted_kinds TEXT NOT NULL DEFAULT '',
    system_notifications INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, platform)
);

-- 11b. ISCRIZIONI WEB PUSH DELLA PWA (migrazione 010)
CREATE TABLE IF NOT EXISTS web_push_subscriptions (
    endpoint TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    muted_kinds TEXT NOT NULL DEFAULT '',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_push_user ON web_push_subscriptions(user_id);

-- 12. RICHIESTE SCAMBIO POSTO INTERROGAZIONI
CREATE TABLE IF NOT EXISTS swap_requests (
    id TEXT PRIMARY KEY,
    grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
    requester_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK(status IN ('PENDING', 'ACCEPTED', 'REJECTED')) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
