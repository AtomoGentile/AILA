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
    fun parseLatestRelease(json: String): AppUpdate? {
        val release = apiJson.decodeFromString<GitHubReleaseDto>(json)
        if (release.draft || release.prerelease) return null
        val asset = release.assets.firstOrNull {
            it.name == APK_ASSET_NAME && it.state == "uploaded" && it.browserDownloadUrl.isNotBlank()
        } ?: return null
        val version = release.tagName.trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty()) return null
        return AppUpdate(
            tag = release.tagName,
            version = version,
            title = release.name?.takeIf { it.isNotBlank() } ?: "AILA $version",
            notes = release.body.orEmpty().trim(),
            apkUrl = asset.browserDownloadUrl,
            apkSize = asset.size,
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
    /**
     * Numeri iniziali di una versione: "v1.2.3" -> [1,2,3]; "1.0.0 (build 57, a1b2c3d)" -> [1,0,0].
     * `null` se non comincia con un numero (es. "?" quando la versione non si legge).
     */
    fun parse(text: String): List<Int>? {
        val head = text.trim().removePrefix("v").removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }
            .trim('.')
        if (head.isEmpty()) return null
        val parts = head.split('.')
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** Vero se [candidate] e' strettamente piu' alta di [installed]; con versioni illeggibili, falso. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}