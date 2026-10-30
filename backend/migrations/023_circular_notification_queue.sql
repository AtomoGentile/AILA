-- Le nuove circolari restano in coda finché il riassunto server è pronto.
-- Le circolari esistenti non generano notifiche retroattive.
ALTER TABLE circulars ADD COLUMN notification_pending INTEGER NOT NULL DEFAULT 0;
