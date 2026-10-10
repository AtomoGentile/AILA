import { API_BASE } from '../config';

// Token in memoria per le chiamate; la copia persistente sta in IndexedDB (session.ts).
let token: string | null = null;
let onUnauthorized: () => void = () => {};

export function setToken(t: string | null) {
  token = t;
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: init.method ?? 'GET',
      headers,
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Sei offline o il server non risponde.');
  }

  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) throw new ApiError(res.status, (data as { error?: string }).error ?? `Errore ${res.status}`);
  return data as T;
}

/**
 * Invio multipart (file + campi): il browser mette lui il Content-Type con il confine, quindi qui
 * l'header non si imposta. Usato dai documenti della Gita.
 */
export async function apiForm<T>(path: string, form: FormData, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { method, headers, body: form });
  } catch {
    throw new ApiError(0, 'Sei offline o il server non risponde.');
  }
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) throw new ApiError(res.status, (data as { error?: string }).error ?? `Errore ${res.status}`);
  return data as T;
}

/** File binario con login (PDF della Gita): niente JSON. */
export async function apiBlob(path: string): Promise<Blob> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { headers });
  } catch {
    throw new ApiError(0, 'Sei offline: questo file non è ancora disponibile.');
  }
  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) throw new ApiError(res.status, `Impossibile aprire il file (${res.status})`);
  return res.blob();
}

// URL del PDF servito dal Worker (R2), mai da Spaggiari: così niente problemi di CORS.
export function pdfUrl(pdfKey: string): string {
  return `${API_BASE}/api/circulars/pdf/${pdfKey.split('/').map(encodeURIComponent).join('/')}`;
}
