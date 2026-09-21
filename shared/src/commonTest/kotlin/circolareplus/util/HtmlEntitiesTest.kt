package circolareplus.util

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntitiesTest {

    @Test
    fun spazioNumericoDelTitoloVistoInApp() {
        assertEquals("Festa di sport - 2026", decodeHtmlEntities("Festa di sport - &#160;2026"))
    }

    @Test
    fun doppiaCodificaSiRisolve() {
        assertEquals("Festa di sport - 2026", decodeHtmlEntities("Festa di sport - &amp;#160;2026"))
        assertEquals("Mostra & concerto", decodeHtmlEntities("Mostra &amp;amp; concerto"))
    }

    @Test
    fun entitaNominaliENumeriche() {
        assertEquals("L'orario \"nuovo\" <5>", decodeHtmlEntities("L&#39;orario &quot;nuovo&quot; &lt;5&gt;"))
        assertEquals("L'ora", decodeHtmlEntities("L&#x27;ora"))
        assertEquals("caffè", decodeHtmlEntities("caff&#232;"))
    }

    @Test
    fun testoSenzaEntitaResta() {
        assertEquals("Circolare n. 3", decodeHtmlEntities("Circolare n. 3"))
    }

    @Test
    fun ecommercialeNonEntitaNonSiRompe() {
        assertEquals("Ricerca & sviluppo", decodeHtmlEntities("Ricerca & sviluppo"))
    }
}
