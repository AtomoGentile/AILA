package circolareplus.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingPasswordResetTest {

    @Test
    fun buildsNormalizedResetLinkForStableAndBetaBaseUrls() {
        assertEquals(
            "https://aila-scuola.pages.dev/?reset=ABCD1234&u=mario.rossi",
            buildPasswordResetLink("https://aila-scuola.pages.dev/", " Mario.Rossi ", "abcd-1234")
        )
        assertEquals(
            "https://beta.aila-scuola.pages.dev/?reset=XY99ZZ&u=anna_b",
            buildPasswordResetLink("https://beta.aila-scuola.pages.dev", "Anna_B", " xy 99-zz ")
        )
        assertEquals(
            "https://aila-scuola.pages.dev/?reset=ABCD1234",
            buildPasswordResetLink("", null, "abcd1234")
        )
    }

    @Test
    fun parsesAndSanitizesResetQueryParams() {
        assertEquals(
            PendingPasswordReset(username = "mario.rossi", code = "ABCD1234"),
            parsePasswordResetQuery(" abcd-1234 ", " Mario.Rossi ")
        )
        assertEquals(
            PendingPasswordReset(username = "", code = "ABCD1234"),
            parsePasswordResetQuery("abcd1234", null)
        )
        assertNull(parsePasswordResetQuery(null, "mario"))
        assertNull(parsePasswordResetQuery("   ---   ", "mario"))
        assertNull(parsePasswordResetQuery("A".repeat(25), "mario"))
    }
}
