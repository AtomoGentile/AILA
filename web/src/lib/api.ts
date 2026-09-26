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

// URL del PDF servito dal Worker (R2), mai da Spaggiari: così niente problemi di CORS.
export function pdfUrl(pdfKey: string): string {
  return `${API_BASE}/api/circulars/pdf/${pdfKey.split('/').map(encodeURIComponent).join('/')}`;
}
