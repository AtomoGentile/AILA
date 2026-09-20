package circolareplus.platform

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun localUtcOffsetMillis(atMillis: Long): Long =
    java.util.TimeZone.getDefault().getOffset(atMillis).toLong()
