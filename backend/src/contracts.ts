// =============================================================================
// AILA — Contratto JSON delle API, condiviso con la PWA (web/)
//
// Solo tipi, nessuna dipendenza dal runtime dei Worker: la PWA li importa con
// `import type` (alias @worker/contracts). Descrivono le risposte che le rotte già danno:
// cambiare una rotta senza aggiornare qui fa fallire il typecheck della PWA, non dell'app.
// =============================================================================

export type UserRole = 'STUDENT' | 'REPRESENTATIVE' | 'SECURITY_GUARD';

export type EventCategory =
  | 'VERIFICA'
  | 'INTERROGAZIONE'
  | 'PAGAMENTO'
  | 'USCITA_DIDATTICA'
  | 'AVVISO'
  | 'ALTRO';

export type ProposalStatus = 'NUOVA' | 'IN_ANALISI' | 'CHIUSA';

export type ProposalOutcome = 'ACCETTATA' | 'RIFIUTATA';

export type RelevanceBadge = 'RELEVANT' | 'POTENTIAL' | 'NOT_RELEVANT';

// Categorie delle preferenze notifiche (Impostazioni dell'app e della PWA).
export type NotificationKind = 'circulars' | 'calendar' | 'board' | 'seatmap' | 'polls';

// --- Auth / utenti ----------------------------------------------------------

export interface UserDto {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  role: UserRole;
  classId: string;
  classLabel: string | null;
  heightCm: number | null;
  priorityPass: boolean;
  notificationBoardEnabled: boolean;
}

export interface LoginResponse {
  success: boolean;
  token: string;
  user: UserDto;
}

export interface ClassmateDto {
  id: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  createdAt: string;
}

export interface ClassmatesResponse {
  users: ClassmateDto[];
}

// --- Circolari --------------------------------------------------------------

export interface CircularAttachmentDto {
  label: string;
  pdfKey?: string;
  url?: string;
}

export interface CircularDto {
  number: number;
  title: string;
  publishDate: string;
  pdfKey: string;
  attachments: CircularAttachmentDto[];
  createdAt: string;
}

export interface CircularsResponse {
  circulars: CircularDto[];
  total: number;
  limit: number;
  offset: number;
}

export interface DeadlineDto {
  title: string;
  dueDate: string;
  time: string | null;
  category: string;
}

export interface CircularAnalysisDto {
  circularNumber: number;
  badge: RelevanceBadge;
  summary: string;
  deadlines: DeadlineDto[];
  isFallback: boolean;
  modelLabel: string;
  tier: number;
  updatedAt: string;
}

export interface SaveAnalysisRequest {
  badge: RelevanceBadge;
  summary: string;
  deadlines: DeadlineDto[];
  isFallback: boolean;
  modelLabel: string;
}

export interface SaveAnalysisResponse {
  success: boolean;
  stored: boolean;
  current?: CircularAnalysisDto | null;
}

// --- Calendario -------------------------------------------------------------

export interface CalendarEventDto {
  id: string;
  title: string;
  eventDate: string;
  startTime: string | null;
  category: EventCategory;
  isForAll: boolean;
  isAiGenerated: boolean;
  createdBy: string | null;
  createdAt: string;
  notes: string | null;
  visibleToUserIds: string[] | null;
}

export interface CalendarResponse {
  events: CalendarEventDto[];
}

export interface CreateEventRequest {
  title: string;
  eventDate: string;
  startTime?: string;
  category: EventCategory;
  isAiGenerated?: boolean;
  notes?: string;
}

// Con un possibile doppione il server non crea l'evento e risponde con `warning`.
export interface CreateEventResponse {
  success?: boolean;
  id?: string;
  warning?: string;
  existingEventId?: string;
}

// --- Bacheca ----------------------------------------------------------------

export interface ProposalDto {
  id: string;
  title: string;
  description: string;
  category: string;
  status: ProposalStatus;
  outcome: ProposalOutcome | null;
  isAnonymous: boolean;
  authorId: string | null;
  authorName: string | null;
  identityRevealed: boolean;
  modifiedByRep: boolean;
  editedAt: string | null;
  upVotes: number;
  downVotes: number;
  commentCount: number;
  myVote: 1 | -1 | null;
  createdAt: string;
}

export interface ProposalsResponse {
  proposals: ProposalDto[];
}

export interface CommentDto {
  id: string;
  userId: string;
  authorName: string;
  content: string;
  createdAt: string;
  isAnonymous: boolean;
  isMine: boolean;
  identityRevealed: boolean;
}

export interface CommentsResponse {
  comments: CommentDto[];
}

export interface VoteResponse {
  success: boolean;
  upVotes: number;
  downVotes: number;
}

// --- Mappa posti ------------------------------------------------------------

// Un banco della mappa pubblicata (DeskAssignment nell'app).
export interface DeskAssignmentDto {
  row: number;
  column: number;
  studentAId: string | null;
  studentBId: string | null;
  studentCId?: string | null;
  seats?: number;
}

export interface CurrentSeatMapResponse {
  seatMap: { id: string; layout: DeskAssignmentDto[]; publishedAt: string } | null;
}

// --- Web push (PWA) ---------------------------------------------------------

export interface WebPushSubscriptionRequest {
  subscription: { endpoint: string; keys: { p256dh: string; auth: string } };
  mutedKinds: NotificationKind[];
}

export interface WebPushPreferencesRequest {
  endpoint: string;
  mutedKinds: NotificationKind[];
}

// Contenuto (cifrato) di ogni messaggio web push.
export interface WebPushPayload {
  title: string;
  body: string;
  kind: NotificationKind | '';
  data: Record<string, string>;
}
