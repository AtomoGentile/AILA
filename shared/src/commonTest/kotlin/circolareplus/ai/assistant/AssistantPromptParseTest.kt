package circolareplus.ai.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantPromptParseTest {

    /** Risposta reale di Gemini Nano (AICore): fonti come stringhe con JSON non escapato. */
    private val geminiNanoRaw = """{"answer":"Le ultime circolari che ti riguardano sono: Circolare n. 7 pubblicata venerdì 18 settembre, riguardante le elezioni studentesche nella Consulta provinciale, e Circolare n. 4 pubblicata venerdì 4 settembre, riguardante la riapertura del servizio bar.","sources":["{"kind":"CIRCULAR","label":"Circolare n. 7","circularNumber":7}","{"kind":"CIRCULAR","label":"Circolare n. 4","circularNumber":4}","{"kind":"CALENDAR","label":"Assemblee ed elezioni dei rappresentanti di classe e di Istituto","circularNumber":0}"]}"""

    @Test
    fun jsonRottoDiGeminiNanoNonFinisceInChat() {
        val parsed = AssistantPrompt.parse(geminiNanoRaw)
        assertFalse(parsed.answer.contains("\"sources\""), parsed.answer)
        assertFalse(parsed.answer.startsWith("{"), parsed.answer)
        assertTrue(parsed.answer.startsWith("Le ultime circolari"))
        assertTrue(parsed.answer.endsWith("servizio bar."))
        assertEquals(listOf(7, 4), parsed.sources.mapNotNull { it.circularNumber })
        val calendar = parsed.sources.single { it.kind == AssistantSourceKind.CALENDAR }
        assertEquals(null, calendar.circularNumber)
    }

    @Test
    fun fontiComeNumeriDelPromptCompatto() {
        val parsed = AssistantPrompt.parse("""{"answer":"Ciao\nriga due","sources":[7,4],"needsCircularText":[12]}""")
        assertEquals("Ciao\nriga due", parsed.answer)
        assertEquals(listOf(7, 4), parsed.sources.map { it.circularNumber })
        assertTrue(parsed.sources.all { it.kind == AssistantSourceKind.CIRCULAR })
        assertEquals(listOf(12), parsed.needsCircularText)
    }

    @Test
    fun fontiComeStringheEscapateCorrettamente() {
        val raw = """{"answer":"ok","sources":["{\"kind\":\"CIRCULAR\",\"label\":\"Circolare n. 9\",\"circularNumber\":9}","Circolare n. 3"]}"""
        val parsed = AssistantPrompt.parse(raw)
        assertEquals("ok", parsed.answer)
        assertEquals(listOf(9, 3), parsed.sources.map { it.circularNumber })
    }

    @Test
    fun formatoCompletoInvariato() {
        val raw = """```json
{"answer":"Risposta","sources":[{"kind":"CIRCULAR","label":"Circolare n. 5 - Gita","circularNumber":5}],"needsCircularText":[]}
```"""
        val parsed = AssistantPrompt.parse(raw)
        assertEquals("Risposta", parsed.answer)
        assertEquals(5, parsed.sources.single().circularNumber)
        assertTrue(parsed.needsCircularText.isEmpty())
    }

    @Test
    fun rispostaTroncataTieneLaRisposta() {
        val parsed = AssistantPrompt.parse("""{"answer":"Il pullman parte alle 7:30.","sources":[{"kind":"CIRC""")
        assertEquals("Il pullman parte alle 7:30.", parsed.answer)
    }

    @Test
    fun testoLiberoRestaTestoLibero() {
        val parsed = AssistantPrompt.parse("Ciao! Come posso aiutarti?")
        assertEquals("Ciao! Come posso aiutarti?", parsed.answer)
        assertTrue(parsed.sources.isEmpty())
    }
}
