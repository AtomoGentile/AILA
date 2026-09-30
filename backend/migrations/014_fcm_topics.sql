-- =============================================================================
-- AILA — Migrazione 014: registro dei telefoni iscritti ai topic FCM
-- =============================================================================
--
-- Le notifiche di classe e le circolari vanno a un topic FCM (una richiesta per notifica,
-- qualunque sia il numero di telefoni) invece che a un messaggio per token: il piano Free di
-- Cloudflare concede 50 richieste esterne per esecuzione. L'app si iscrive ai topic e poi lo
-- dice al server (POST /api/fcm/topics/subscribed); qui si ricorda chi lo ha fatto, cosi' i
-- telefoni con l'APK vecchio (non iscritti) continuano a ricevere il messaggio per token.
--
-- Solo CREATE ... IF NOT EXISTS: si puo' rilanciare senza errori.

CREATE TABLE IF NOT EXISTS fcm_topic_devices (
    token TEXT PRIMARY KEY,
    subscribed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
