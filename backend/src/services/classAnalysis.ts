// =============================================================================
// AILA — Analisi delle circolari per classe
// =============================================================================
//
// Una circolare si riassume con UNA sola chiamata a Gemini per tutto l'istituto, non una per
// classe. Nella stessa risposta il modello dà:
// - un riassunto comune e un badge "generico";
// - per ogni classe registrata un badge e una nota breve con quello che riguarda solo lei
//   (es. data e orario del suo consiglio di classe, preso da una tabella);
// - le scadenze etichettate con le classi a cui valgono (nessuna classe = valgono per tutti).
// Chi legge l'analisi riceve la versione della propria classe (vedi resolveForClass), con la
// stessa forma di prima: il client non deve sapere nulla delle altre classi.

import type { Env } from '../types';

export interface ClassInfo {
  id: string;
  label: string;
}

export interface ClassNote {
  badge: string;
  note: string;
}

/** Oltre questo numero di classi il prompt e la risposta diventano troppo lunghi. */
const MAX_CLASSES_IN_PROMPT = 40;

/** Le classi che entrano nel prompt: le stesse di [MISSING_CLASS_SQL], nello stesso ordine. */
const PROMPT_CLASSES_SQL = `SELECT id, label FROM classes ORDER BY label ASC LIMIT ${MAX_CLASSES_IN_PROMPT}`;

export async function loadClasses(env: Env): Promise<ClassInfo[]> {
  const rows = await env.DB.prepare(PROMPT_CLASSES_SQL).all<ClassInfo>();
  return rows.results;
}

/**
 * Condizione SQL (su `a` = circular_ai_analysis): l'analisi del server non ha la parte di almeno
 * una classe registrata, per esempio perché la classe si è iscritta dopo. Le analisi senza dati
 * per classe (NULL, fatte prima dell'analisi per classe) contano come "mancanti" solo se le
 * classi sono più di una: con una classe sola erano già fatte per lei.
 */
export const MISSING_CLASS_SQL = `(
  (a.per_class_json IS NULL AND (SELECT COUNT(*) FROM classes) > 1)
  OR (a.per_class_json IS NOT NULL AND EXISTS (
    SELECT 1 FROM (${PROMPT_CLASSES_SQL}) cl
    WHERE json_type(a.per_class_json, '$."' || cl.id || '"') IS NULL
  ))
)`;

/** "4 CSA" (come è salvata) -> "4^CSA" (come si scrive nelle circolari e nell'app). */
export function displayClassLabel(label: string): string {
  return label.trim().replace(/^(\d)\s*[\^°ª]?\s*/, '$1^').replace(/\s+/g, '');
}

/** Chiave di confronto: "4^ CSA", "4 CSA", "4ª csa" -> "4CSA". */
function classKey(label: string): string {
  return label.toUpperCase().replace(/[^0-9A-Z]/g, '');
}

/** Mappa un'etichetta scritta dal modello sull'id della classe registrata, se c'è. */
export function classIdForLabel(label: unknown, classes: ClassInfo[]): string | null {
  if (typeof label !== 'string') return null;
  const key = classKey(label);
  if (!key) return null;
  return classes.find((c) => classKey(c.label) === key)?.id ?? null;
}

export function parsePerClass(json: string | null | undefined): Record<string, ClassNote> | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

interface StoredDeadline {
  title: string;
  dueDate: string;
  time: string | null;
  category: string;
  /** Id delle classi a cui vale; assente o vuoto = tutte. */
  classes?: string[];
}

/**
 * La versione dell'analisi per una classe: badge della classe (se c'è), riassunto comune più la
 * nota della classe, solo le scadenze che la riguardano. Senza dati per classe (analisi vecchie
 * o fatte da un telefono) si restituisce l'analisi così com'è.
 */
export function resolveForClass(
  analysis: { badge: string; summary: string; deadlines: StoredDeadline[]; perClass: Record<string, ClassNote> | null },
  classId: string
): { badge: string; summary: string; deadlines: Omit<StoredDeadline, 'classes'>[] } {
  const own = analysis.perClass?.[classId];
  const note = own?.note?.trim();
  const deadlines = analysis.deadlines
    .filter((d) => !d.classes || d.classes.length === 0 || d.classes.includes(classId))
    .map(({ classes: _classes, ...rest }) => rest);
  return {
    badge: own?.badge ?? analysis.badge,
    summary: note ? `${analysis.summary}\n${note}` : analysis.summary,
    deadlines,
  };
}
