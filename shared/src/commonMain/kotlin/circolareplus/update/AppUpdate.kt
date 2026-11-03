package circolareplus.update

import circolareplus.data.remote.apiJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * Una versione piu' recente di AILA pubblicata su GitHub, pronta da scaricare.
 *
 * [version] e' il numero senza la "v" del tag ("1.0.1"); [apkUrl] e' l'asset `AILA.apk` che la CI
 * allega alla release (vedi .github/workflows/android-build.yml); [sha256] c'e' solo se GitHub lo
 * dichiara per l'asset (campo `digest`), e in quel caso l'APK scaricato viene verificato.
 */
data class AppUpdate(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val pageUrl: String
)

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    val digest: String? = null,
    val state: String = "uploaded",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

/** Nome dell'asset che la CI allega alle release: e' l'unico che l'app sa installare. */
const val APK_ASSET_NAME = "AILA.apk"

object AppUpdateParser {
    /**
     * Legge la risposta di `GET /repos/{owner}/{repo}/releases/latest`. Restituisce `null` se la
     * release non e' installabile: bozza, pre-release, senza `AILA.apk` (la CI lo allega qualche
     * minuto DOPO la pubblicazione della release, quindi finche' non c'e' non si propone nulla) o
     * con un'APK ancora in caricamento.
     */
    fun parseLatestRelease(json: String): AppUpdate? =
        parseRelease(apiJson.decodeFromString<GitHubReleaseDto>(json), allowBeta = false)

    /** Una stabile resta nel canale stabile; chi ha installato una beta riceve beta e stabili più recenti. */
    fun selectLatestRelease(json: String, installedVersion: String): AppUpdate? =
        apiJson.decodeFromString<List<GitHubReleaseDto>>(json)
            .mapNotNull { parseRelease(it, allowBeta = AppVersion.isBeta(installedVersion)) }
            .filter { AppVersion.isNewer(it.version, installedVersion) }
            .maxWithOrNull { a, b -> AppVersion.compare(a.version, b.version) ?: 0 }

    private fun parseRelease(release: GitHubReleaseDto, allowBeta: Boolean): AppUpdate? {
        if (release.draft) return null
        val version = release.tagName.trim().removePrefix("v").removePrefix("V")
        val prerelease = AppVersion.isPrerelease(version) ?: return null
        // Tag e flag devono concordare: una beta marcata per errore stabile non deve passare.
        if (release.prerelease != prerelease) return null
        if (prerelease && (!allowBeta || !AppVersion.isBeta(version))) return null
        val asset = release.assets.firstOrNull {
            it.name == APK_ASSET_NAME && it.state == "uploaded" && it.size > 0 && it.browserDownloadUrl.startsWith("https://")
        } ?: return null
        return AppUpdate(
            tag = release.tagName, version = version,
            title = release.name?.takeIf { it.isNotBlank() } ?: "AILA $version",
            notes = release.body.orEmpty().trim(), apkUrl = asset.browserDownloadUrl, apkSize = asset.size,
            sha256 = asset.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { it.length == 64 },
            pageUrl = release.htmlUrl
        )
    }

    /**
     * Note della release come testo semplice per la finestra di aggiornamento: toglie i simboli
     * Markdown piu' comuni e la sezione "Installazione", che spiega come scaricare l'app a chi
     * ancora non ce l'ha e a chi sta gia' aggiornando non serve.
     */
    fun cleanNotes(notes: String): String {
        val out = mutableListOf<String>()
        var skipping = false
        for (raw in notes.lines()) {
            val line = raw.trim()
            if (line.startsWith("#")) {
                val heading = line.trimStart('#').trim()
                skipping = heading.contains("install", ignoreCase = true)
                if (!skipping) out += heading
                continue
            }
            if (skipping) continue
            out += line.replace("**", "").replace(Regex("^[-*]\\s+"), "• ")
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

object AppVersion {
    private data class Version(val core: List<Int>, val pre: List<String>)
    private val pattern = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""")

    private fun read(text: String): Version? {
        val match = pattern.matchEntire(text.trim().substringBefore(" (build ")) ?: return null
        val core = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
        val pre = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
        if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
        return Version(core, pre)
    }

    /** Compatibilità con le versioni installate che includono il suffisso della build CI. */
    fun parse(text: String): List<Int>? = read(text)?.core
    fun isPrerelease(text: String): Boolean? = read(text)?.pre?.isNotEmpty()
    fun isBeta(text: String): Boolean {
        val pre = read(text)?.pre ?: return false
        return pre.size == 2 && pre[0] == "beta" && pre[1].all(Char::isDigit) && pre[1] != "0"
    }

    /** Confronto SemVer: beta.10 > beta.2, ma la stabile è successiva alla propria beta. */
    fun compare(candidate: String, installed: String): Int? {
        val a = read(candidate) ?: return null
        val b = read(installed) ?: return null
        for (i in 0 until maxOf(a.core.size, b.core.size)) {
            val difference = a.core.getOrElse(i) { 0 }.compareTo(b.core.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        if (a.pre.isEmpty() || b.pre.isEmpty()) return when {
            a.pre.isEmpty() && b.pre.isEmpty() -> 0
            a.pre.isEmpty() -> 1
            else -> -1
        }
        for (i in 0 until minOf(a.pre.size, b.pre.size)) {
            val x = a.pre[i]; val y = b.pre[i]
            val xn = x.all(Char::isDigit); val yn = y.all(Char::isDigit)
            val difference = when {
                xn && yn -> if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
                xn != yn -> if (xn) -1 else 1
                else -> x.compareTo(y)
            }
            if (difference != 0) return difference
        }
        return a.pre.size.compareTo(b.pre.size)
    }

    fun isNewer(candidate: String, installed: String): Boolean = (compare(candidate, installed) ?: 0) > 0
}

/** Identifica l'asset, non solo il numero di byte: beta e stabile possono avere la stessa dimensione. */
fun AppUpdate.downloadIdentity(): String = listOf(tag, apkUrl, apkSize.toString(), sha256.orEmpty()).joinToString("\n")
