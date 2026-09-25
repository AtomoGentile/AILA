-- =============================================================================
-- AILA — Migrazione 008: preferenze notifiche per dispositivo
-- =============================================================================
--
-- Su Android il push arriva sempre all'app (messaggio solo `data`), che applica da se' gli
-- interruttori delle Impostazioni. Su iOS, con l'app in background, il banner lo mostra il
-- sistema senza passare dall'app: le categorie silenziate e "Notifiche di sistema" spento
-- venivano ignorati. Il telefono ora manda le sue preferenze insieme al token e il server ne
-- tiene conto quando costruisce il messaggio per iOS.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/008_push_preferences.sql

-- Categorie silenziate, separate da virgola ("circulars,board"). Vuoto = tutte attive.
ALTER TABLE fcm_tokens ADD COLUMN muted_kinds TEXT NOT NULL DEFAULT '';
-- 0 = niente banner: il push arriva silenzioso e l'app lo scrive solo nella campanella.
ALTER TABLE fcm_tokens ADD COLUMN system_notifications INTEGER NOT NULL DEFAULT 1;
