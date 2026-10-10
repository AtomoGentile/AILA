package circolareplus.platform

private fun dateNow(): Double = js("Date.now()")

// getTimezoneOffset() e' in minuti e col segno opposto (UTC meno locale): +1 ora a est di Greenwich
// vale -60. Si passa dal timestamp perche' l'ora legale cambia l'offset durante l'anno.
private fun timezoneOffsetMinutes(atMillis: Double): Double = js("new Date(atMillis).getTimezoneOffset()")

actual fun currentTimeMillis(): Long = dateNow().toLong()

actual fun localUtcOffsetMillis(atMillis: Long): Long = (-timezoneOffsetMinutes(atMillis.toDouble()) * 60_000.0).toLong()
