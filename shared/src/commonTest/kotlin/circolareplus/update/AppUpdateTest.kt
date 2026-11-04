package circolareplus.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {

    // Versione installata come la scrive la CI (vedi androidApp/build.gradle.kts).
    private val installed = "1.0.0 (build 57, a1b2c3d)"

    @Test
    fun laVersioneSiLeggeAncheConIlSuffissoDellaBuild() {
        assertEquals(listOf(1, 0, 0), AppVersion.parse(installed))
        assertEquals(listOf(1, 2, 10), AppVersion.parse("v1.2.10"))
        assertNull(AppVersion.parse("?"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun soloUnaVersionePiuAltaEUnAggiornamento() {
        assertTrue(AppVersion.isNewer("v1.0.1", installed))
        assertTrue(AppVersion.isNewer("1.1", installed))
        assertTrue(AppVersion.isNewer("2.0.0", installed))
        assertFalse(AppVersion.isNewer("v1.0.0", installed))
        assertFalse(AppVersion.isNewer("1.0", installed))
        assertFalse(AppVersion.isNewer("0.0.1", installed))
    }

    @Test
    fun ilConfrontoESuiNumeriNonSuTesto() {
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.0"))
        assertFalse(AppVersion.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun conVersioniIllegibiliNonSiProponeNulla() {
        assertFalse(AppVersion.isNewer("latest", installed))
        assertFalse(AppVersion.isNewer("v1.0.1", "?"))
    }

    private fun release(
        tag: String = "v1.0.1",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """{"name":"AILA.apk","size":98765432,"state":"uploaded",
            "digest":"sha256:${"a".repeat(64)}",
            "browser_download_url":"https://github.com/AtomoGentile/AILA/releases/download/v1.0.1/AILA.apk"}"""
    ) = """{"tag_name":"$tag","name":"AILA 1.0.1","body":"Novita","draft":$draft,"prerelease":$prerelease,
        "html_url":"https://github.com/AtomoGentile/AILA/releases/tag/$tag","assets":[$assets]}"""

    @Test
    fun laReleaseConApkDiventaUnAggiornamento() {
        val update = assertNotNull(AppUpdateParser.parseLatestRelease(release()))
        assertEquals("1.0.1", update.version)
        assertEquals("v1.0.1", update.tag)
        assertEquals(98765432L, update.apkSize)
        assertEquals("a".repeat(64), update.sha256)
        assertTrue(update.apkUrl.endsWith("/AILA.apk"))
    }

    @Test
    fun senzaApkOConApkInCaricamentoNonSiProponeNulla() {
        assertNull(AppUpdateParser.parseLatestRelease(release(assets = "")))
        // La CI allega l'APK dopo la pubblicazione: l'IPA da solo non basta.
        assertNull(AppUpdateParser.parseLatestRelease(release(assets = """{"name":"AILA.ipa","size":1,"browser_download_url":"https://x/AILA.ipa"}""")))
        assertNull(
            AppUpdateParser.parseLatestRelease(
                release(assets = """{"name":"AILA.apk","size":1,"state":"open","browser_download_url":"https://x/AILA.apk"}""")
            )
        )
    }

    @Test
    fun bozzeEPreReleaseSiIgnorano() {
        assertNull(AppUpdateParser.parseLatestRelease(release(draft = true)))
        assertNull(AppUpdateParser.parseLatestRelease(release(prerelease = true)))
    }

    @Test
    fun senzaDigestOConDigestStranoNonSiVerificaIlContenuto() {
        val noDigest = """{"name":"AILA.apk","size":10,"browser_download_url":"https://x/AILA.apk"}"""
        assertNull(AppUpdateParser.parseLatestRelease(release(assets = noDigest))?.sha256)
        val odd = """{"name":"AILA.apk","size":10,"digest":"sha1:abc","browser_download_url":"https://x/AILA.apk"}"""
        assertNull(AppUpdateParser.parseLatestRelease(release(assets = odd))?.sha256)
    }

    @Test
    fun leNoteSenzaLaSezioneInstallazione() {
        val body = "Prima versione stabile.\n\n## Installazione\n\n- **Android**: scarica AILA.apk\n\n## Novità\n\n- Più veloce\n- **Nuovo** tema"
        assertEquals("Prima versione stabile.\n\nNovità\n\n• Più veloce\n• Nuovo tema", AppUpdateParser.cleanNotes(body))
        assertEquals("", AppUpdateParser.cleanNotes("## Installazione\n\n- solo istruzioni"))
    }
    @Test
    fun leBetaSiOrdinanoNumericamenteELaStabileSuperaLaPropriaBeta() {
        assertTrue(AppVersion.isNewer("1.6.3-beta.10", "1.6.3-beta.2 (build 166, abcdef0)"))
        assertTrue(AppVersion.isNewer("1.6.3", "1.6.3-beta.10"))
        assertFalse(AppVersion.isNewer("1.6.3-beta.20", "1.6.3"))
        assertFalse(AppVersion.isNewer("1.6.3-beta.2", "1.6.3-beta.10"))
        assertFalse(AppVersion.isNewer("1.6.2", "1.6.3-beta.1"))
        assertTrue(AppVersion.isNewer("1.6.4-beta.1", "1.6.3"))
        assertTrue(AppVersion.isNewer("1.6.3", "1.6.3-dev"))
        assertFalse(AppVersion.isNewer("1.6.3+build2", "1.6.3+build1"))
    }

    @Test
    fun tagBetaEFlagDevonoConcordare() {
        assertNull(AppUpdateParser.parseLatestRelease(release(tag = "v1.6.3-beta.1", prerelease = false)))
        assertNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.6.3-beta.1", prerelease = false)}]", "1.6.2-beta.1"))
        assertNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.6.3", prerelease = true)}]", "1.6.2-beta.1"))
        assertNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.7.0-rc.1", prerelease = true)}]", "1.6.3-beta.1"))
    }

    @Test
    fun unaStabileNonPassaAutomaticamenteAllaBeta() {
        val feed = "[${release(tag = "v1.7.0-beta.1", prerelease = true)},${release(tag = "v1.6.3")}]"
        assertEquals("1.6.3", AppUpdateParser.selectLatestRelease(feed, "1.6.2")?.version)
        assertNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.7.0-beta.1", prerelease = true)}]", "1.6.3"))
    }

    @Test
    fun preferenzaEsplicitaConsenteBetaDallaStabileEDisattivaBetaSullaBeta() {
        val feed = "[${release(tag = "v1.7.0-beta.1", prerelease = true)},${release(tag = "v1.6.4")}]"
        assertEquals("1.7.0-beta.1", AppUpdateParser.selectLatestRelease(feed, "1.6.3", allowBeta = true)?.version)
        assertEquals("1.6.4", AppUpdateParser.selectLatestRelease(feed, "1.6.3-beta.5", allowBeta = false)?.version)
        assertNull(AppUpdateParser.selectLatestRelease(feed, "1.7.0-beta.2", allowBeta = false))
    }

    @Test
    fun unaBetaRiceveLaBetaPiuRecenteAncheSeLeReleaseNonSonoOrdinate() {
        val feed = "[${release(tag = "v1.6.3-beta.2", prerelease = true)},${release(tag = "v1.6.2")},${release(tag = "v1.6.3-beta.10", prerelease = true)}]"
        assertEquals("1.6.3-beta.10", AppUpdateParser.selectLatestRelease(feed, "1.6.3-beta.1")?.version)
        assertNull(AppUpdateParser.selectLatestRelease(feed, "1.6.3-beta.10"))
    }

    @Test
    fun unaBetaPassaAllaStabileSenzaDowngrade() {
        val feed = "[${release(tag = "v1.6.3-beta.10", prerelease = true)},${release(tag = "v1.6.3")}]"
        assertEquals("1.6.3", AppUpdateParser.selectLatestRelease(feed, "1.6.3-beta.10")?.version)
        assertNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.6.2")}]", "1.6.3-beta.1"))
        assertNull(AppUpdateParser.selectLatestRelease(feed, "1.7.0-beta.1"))
    }

    @Test
    fun laBetaPiuAltaSenzaApkNonNascondeUnaBetaInstallabile() {
        val feed = "[${release(tag = "v1.6.3-beta.3", prerelease = true, assets = "")},${release(tag = "v1.6.3-beta.2", prerelease = true)}]"
        assertEquals("1.6.3-beta.2", AppUpdateParser.selectLatestRelease(feed, "1.6.3-beta.1")?.version)
    }

    @Test
    fun betaEStabileDellaStessaDimensioneNonRiutilizzanoLoStessoApk() {
        val stable = assertNotNull(AppUpdateParser.parseLatestRelease(release(tag = "v1.6.3")))
        val beta = assertNotNull(AppUpdateParser.selectLatestRelease("[${release(tag = "v1.6.3-beta.1", prerelease = true)}]", "1.6.2-beta.1"))
        assertEquals(stable.apkSize, beta.apkSize)
        assertFalse(stable.downloadIdentity() == beta.downloadIdentity())
        assertFalse(stable.downloadIdentity() == stable.copy(sha256 = "b".repeat(64)).downloadIdentity())
    }

}
