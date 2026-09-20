package circolareplus.platform

/** Epoch millis corrente. Serve per marcare data/ora delle notifiche salvate localmente. */
expect fun currentTimeMillis(): Long

/**
 * Scarto del fuso orario locale rispetto a UTC, in millisecondi, all'istante di [atMillis].
 *
 * Serve a sapere che giorno e che ora sono **per chi ha il telefono in mano**: l'epoch e' in UTC e
 * in Italia, fra mezzanotte e le 1-2 di notte, "oggi" in UTC e' ancora ieri.
 */
expect fun localUtcOffsetMillis(atMillis: Long): Long
