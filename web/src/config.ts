// Indirizzo del Worker: pubblico, non è un segreto. Sovrascrivibile con VITE_API_BASE
// (se cambia, va aggiornato anche connect-src in public/_headers).
export const API_BASE: string = (import.meta.env.VITE_API_BASE || 'https://circolare-plus-worker.circolareclass.workers.dev').replace(/\/$/, '');

// Nomi delle cache del service worker con dati /api/* (svuotate al logout).
export const API_CACHE = 'aila-api';
export const PDF_CACHE = 'aila-pdf';
