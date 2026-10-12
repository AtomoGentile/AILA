package circolareplus.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LessonHoursTest {

    private fun hours(text: String): Pair<Int, Int>? = LessonHours.parse(text)?.let { it.from to it.to }

    @Test
    fun unaSolaOra() {
        assertEquals(3 to 3, hours("Interrogazione di storia lunedì alla 3ª ora"))
        assertEquals(3 to 3, hours("verifica alla terza ora"))
        assertEquals(1 to 1, hours("compito di inglese in prima ora"))
        assertEquals(4 to 4, hours("verifica 4a ora"))
        assertEquals(5 to 5, hours("verifica alla 5 ora"))
    }

    @Test
    fun unIntervalloDiOre() {
        assertEquals(2 to 4, hours("Verifica di matematica dalla 2 alla 4 ora"))
        assertEquals(2 to 4, hours("verifica dalla seconda alla quarta ora"))
        assertEquals(2 to 4, hours("verifica 2ª-4ª ora"))
        assertEquals(2 to 3, hours("verifica alla 2ª e 3ª ora"))
        assertEquals(1 to 2, hours("verifica nelle prime due ore"))
    }

    @Test
    fun unIntervalloAlContrarioSiRimetteInOrdine() {
        assertEquals(2 to 4, hours("dalla 4 alla 2 ora"))
    }

    @Test
    fun ultimaOraESesta() {
        assertEquals(6 to 6, hours("uscita all'ultima ora"))
        assertEquals(6 to 6, hours("sesta ora"))
    }

    @Test
    fun unOrarioDellOrologioNonEUnOraDiLezione() {
        assertNull(LessonHours.parse("Interrogazione di storia lunedì alle 9"))
        assertNull(LessonHours.parse("Verifica venerdì alle 10:30"))
        assertNull(LessonHours.parse("Compito di inglese domani"))
    }

    @Test
    fun oreFuoriDalRangeSiIgnorano() {
        assertNull(LessonHours.parse("verifica alla 7 ora"))
        assertNull(LessonHours.parse("verifica alla 16 ora"))
        assertNull(LessonHours.valid(0))
        assertNull(LessonHours.valid(7))
        assertEquals(6, LessonHours.valid(6))
    }

    @Test
    fun leOreDelModelloSiLeggonoDalJson() {
        val json = """{"title":"Verifica di matematica","subject":"Matematica","category":"VERIFICA",
            "dateIso":null,"fromHour":2,"toHour":4,"timeHm":null,"notes":""}"""
        val draft = assertNotNull(EventGenerationPrompt.parse(json))
        assertEquals(2, draft.fromHour)
        assertEquals(4, draft.toHour)
    }

    @Test
    fun conUnaSolaOraLAltraELaStessa() {
        val json = """{"title":"Interrogazione","category":"INTERROGAZIONE","fromHour":3,"toHour":null}"""
        val draft = assertNotNull(EventGenerationPrompt.parse(json))
        assertEquals(3, draft.fromHour)
        assertEquals(3, draft.toHour)
    }

    @Test
    fun oreInvalideODiTestoSiScartano() {
        val json = """{"title":"Verifica","category":"VERIFICA","fromHour":"9","toHour":12}"""
        val draft = assertNotNull(EventGenerationPrompt.parse(json))
        assertNull(draft.fromHour)
        assertNull(draft.toHour)
    }

    @Test
    fun unOrarioDellOrologioDiventaLOraDiLezione() {
        assertEquals(1, LessonHours.fromClock("08:00"))
        assertEquals(2, LessonHours.fromClock("09:00"))
        assertEquals(3, LessonHours.fromClock("10:30"))
        assertEquals(6, LessonHours.fromClock("13:59"))
    }

    @Test
    fun fuoriDalleOreDiLezioneNonSiTraduce() {
        assertNull(LessonHours.fromClock("07:59"))
        assertNull(LessonHours.fromClock("14:00"))
        assertNull(LessonHours.fromClock("15:30"))
        assertNull(LessonHours.fromClock("abc"))
        assertNull(LessonHours.fromClock(null))
    }

    @Test
    fun unaClasseDaCinqueOreNonHaLaSesta() {
        assertEquals(5 to 5, LessonHours.parse("uscita all'ultima ora", maxHours = 5)?.let { it.from to it.to })
        assertEquals(6 to 6, LessonHours.parse("uscita all'ultima ora", maxHours = 6)?.let { it.from to it.to })
        assertNull(LessonHours.parse("verifica alla sesta ora", maxHours = 5))
        assertNull(LessonHours.parse("verifica dalla 4 alla 6 ora", maxHours = 5))
        assertEquals(4 to 5, LessonHours.parse("verifica dalla 4 alla 5 ora", maxHours = 5)?.let { it.from to it.to })
    }

    @Test
    fun conCinqueOreLOrologioFinisceAlleTredici() {
        assertEquals(5, LessonHours.fromClock("12:59", maxHours = 5))
        assertNull(LessonHours.fromClock("13:00", maxHours = 5))
        assertEquals(6, LessonHours.fromClock("13:30", maxHours = 6))
        assertNull(LessonHours.fromClock("14:00", maxHours = 6))
    }
}
