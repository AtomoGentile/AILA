// =============================================================================
// CIRCOLARE+ — Shared Types
// =============================================================================

export interface Env {
  DB: D1Database;
  CIRCULARS_BUCKET: R2Bucket;
  JWT_SECRET: string;
  // HTTP v1 API (l'unica ancora attiva da giugno 2024: la "Legacy API" con FCM_SERVER_KEY
  // è stata dismessa da Google e non funzionerebbe su un progetto Firebase creato oggi).
  FCM_PROJECT_ID?: string;
  FCM_SERVICE_ACCOUNT_KEY?: string;
  SPAGGIARI_URL?: string;
  // Codice segreto opzionale: chi lo conosce e lo inserisce in fase di registrazione riceve il
  // ruolo REPRESENTATIVE invece di STUDENT. Se non impostato, la registrazione con codice fallisce
  // esplicitamente (nessun ruolo speciale "silenzioso" senza che il secret esista davvero).
  REPRESENTATIVE_SIGNUP_CODE?: string;
  // Chiave Google AI Studio per il riassunto delle circolari fatto dal server (services/summarizer.ts).
  // Solo come secret (`wrangler secret put GEMINI_API_KEY`), mai in [vars]. Se assente, i riassunti
  // restano ai telefoni come prima.
  GEMINI_API_KEY?: string;
}

export interface JWTPayload {
  sub: string;        // user id
  username: string;
  role: UserRole;
  // Classe dell'utente. Opzionale perché i token emessi prima del multi-classe non ce l'hanno:
  // in quel caso resolveClassId() la rilegge dal database invece di invalidare la sessione di
  // tutti (che significherebbe rifare il login a tutta la classe dopo un aggiornamento).
  classId?: string;
  iat: number;
  exp: number;
}

export type UserRole = 'STUDENT' | 'REPRESENTATIVE' | 'SECURITY_GUARD';

export interface User {
  id: string;
  first_name: string;
  last_name: string;
  username: string;
  password_hash: string;
  role: UserRole;
  class_id: string;
  created_at: string;
}

export interface SchoolClass {
  id: string;
  label: string;
  academic_year: string;
  preferences_open: number;
  created_at: string;
}

export interface StudentProfile {
  user_id: string;
  height_cm: number;
  priority_pass: number;
  notification_board_enabled: number;
}

export interface Circular {
  number: number;
  title: string;
  publish_date: string;
  r2_pdf_key: string;
  original_url: string | null;
  attachments_json: string;
  created_at: string;
}

// Un allegato della colonna "Allegati" di Spaggiari (0, 1 o più per circolare).
// `pdfKey` quando il file è stato scaricato e messo in cache su R2 come il PDF principale,
// `url` quando punta altrove (es. un'altra pagina del sito, non direttamente un PDF) e va
// aperto esternamente. Esattamente uno dei due è valorizzato.
export interface CircularAttachment {
  label: string;
  pdfKey?: string;
  url?: string;
}

export interface CalendarEvent {
  id: string;
  title: string;
  event_date: string;
  start_time: string | null;
  category: EventCategory;
  is_for_all: number;
  is_ai_generated: number;
  created_by: string | null;
  class_id: string;
  created_at: string;
}

export type EventCategory =
  | 'VERIFICA'
  | 'INTERROGAZIONE'
  | 'PAGAMENTO'
  | 'USCITA_DIDATTICA'
  | 'AVVISO'
  | 'ALTRO';

export interface Proposal {
  id: string;
  author_id: string;
  is_anonymous: number;
  title: string;
  description: string;
  category: string;
  status: ProposalStatus;
  modified_by_rep: number;
  edited_at: string | null;
  class_id: string;
  created_at: string;
}

export type ProposalStatus = 'NUOVA' | 'IN_ANALISI' | 'CHIUSA';

export type VoteScore = 50 | 0 | -80 | -300;
