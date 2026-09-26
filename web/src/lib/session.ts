// Sessione: stesso login e stesso JWT dell'app nativa (POST /api/auth/login).
import { useEffect, useState } from 'preact/hooks';
import type { LoginResponse, UserDto } from '@worker/contracts';
import { API_CACHE, PDF_CACHE } from '../config';
import { api, setToken, setUnauthorizedHandler } from './api';
import { kv } from './db';
import { disablePush } from './push';

export interface Session {
  token: string;
  user: UserDto;
}

let current: Session | null = null;
let ready = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

// Scadenza letta dal JWT (senza verificarlo: la verifica la fa il Worker).
function isExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

export async function restoreSession(): Promise<void> {
  const token = await kv.get<string>('token').catch(() => undefined);
  const user = await kv.get<UserDto>('user').catch(() => undefined);
  if (token && user && !isExpired(token)) {
    current = { token, user };
    setToken(token);
  }
  ready = true;
  emit();
}

export async function login(username: string, password: string): Promise<void> {
  const res = await api<LoginResponse>('/api/auth/login', { method: 'POST', body: { username: username.trim(), password } });
  await kv.set('token', res.token);
  await kv.set('user', res.user);
  setToken(res.token);
  current = { token: res.token, user: res.user };
  emit();
}

/** Logout: iscrizione push, cache /api/* del service worker e IndexedDB. */
let loggingOut = false;

export async function logout(): Promise<void> {
  if (loggingOut) return;
  loggingOut = true;
  await disablePush();
  if ('caches' in window) {
    await Promise.all([caches.delete(API_CACHE), caches.delete(PDF_CACHE)]).catch(() => {});
  }
  await kv.clear().catch(() => {});
  setToken(null);
  current = null;
  emit();
  // Ricarica per non lasciare dati dell'utente in memoria.
  location.replace('/');
}

// Token scaduto o revocato: si esce come in un logout normale.
setUnauthorizedHandler(() => {
  void logout();
});

export function getSession(): Session | null {
  return current;
}

export function useSession(): { session: Session | null; ready: boolean } {
  const [, force] = useState(0);
  useEffect(() => {
    const l = () => force((n) => n + 1);
    listeners.add(l);
    // La sessione può essere stata ripristinata prima dell'iscrizione.
    l();
    return () => listeners.delete(l);
  }, []);
  return { session: current, ready };
}

// Contesto studente per il prompt, come nell'app ("classe 4^ CSA" di default).
export function studentContext(): string {
  const label = current?.user.classLabel?.replace(/^(\d)\s+/, '$1^ ');
  return `Studente di scuola superiore, classe ${label ?? '4^ CSA'}`;
}
