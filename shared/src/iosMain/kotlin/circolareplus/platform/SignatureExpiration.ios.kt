package circolareplus.platform

import platform.Foundation.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi

actual fun getIosSignatureExpirationInfo(firstLaunchMillis: Long): SignatureExpirationInfo? {
    val verifiedDate = readEmbeddedMobileProvisionExpiration()
    if (verifiedDate != null) {
        return SignatureExpirationInfo(
            expiresAtIso = verifiedDate,
            isEstimated = false
        )
    }

    val baseMs = if (firstLaunchMillis > 0) firstLaunchMillis else currentTimeMillis()
    val millisIn7Days = 7L * 24 * 3600 * 1000
    val estimatedMs = baseMs + millisIn7Days

    val date = NSDate.dateWithTimeIntervalSince1970(estimatedMs / 1000.0)
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        timeZone = NSTimeZone.timeZoneWithName("UTC")!!
    }
    val isoStr = formatter.stringFromDate(date)

    return SignatureExpirationInfo(
        expiresAtIso = isoStr,
        isEstimated = true
    )
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun readEmbeddedMobileProvisionExpiration(): String? {
    val path = NSBundle.mainBundle.pathForResource("embedded", "mobileprovision") ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null

    val rawStr = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return null

    val regex = Regex("<key>ExpirationDate</key>\\s*<date>([^<]+)</date>", RegexOption.IGNORE_CASE)
    val match = regex.find(rawStr) ?: return null
    val dateStr = match.groupValues[1].trim()

    return if (dateStr.isNotBlank()) dateStr else null
}
