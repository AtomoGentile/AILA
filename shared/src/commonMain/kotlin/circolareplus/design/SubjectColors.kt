package circolareplus.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Local subject color rules shared by the timetable and Brief. */
object SubjectColors {
    data class Subject(val key: String, val label: String, val defaultColor: String)
    data class Choice(val key: String, val label: String, val colors: List<Color>) {
        val color: Color get() = colors.first()
        private val gradientColors: List<Color> get() = if (colors.size == 1) listOf(colors.first(), colors.first()) else colors
        val brush: Brush get() = Brush.horizontalGradient(gradientColors)

    }

    val subjects = listOf(
        Subject("matematica", "Matematica", "red"),
        Subject("italiano", "Italiano", "blue"),
        Subject("fisica", "Fisica", "yellow"),
        Subject("chimica", "Chimica", "green"),
        Subject("biologia", "Biologia", "green"),
        Subject("scienze", "Scienze", "green"),
        Subject("inglese", "Inglese", "white"),
        Subject("storia", "Storia", "orange"),
        Subject("motoria", "Motoria", "blue"),
        Subject("filosofia", "Filosofia", "black"),
        Subject("religione", "Religione", "fuchsia"),
        Subject("informatica", "Informatica", "lightblue"),
        Subject("educazione_civica", "Educazione civica", "rainbow")
    )

    val choices = listOf(
        Choice("red", "Rosso", listOf(Color(0xFFE53935))),
        Choice("blue", "Blu", listOf(Color(0xFF1976D2))),
        Choice("yellow", "Giallo", listOf(Color(0xFFF9A825))),
        Choice("green", "Verde", listOf(Color(0xFF2E7D32))),
        Choice("white", "Bianco", listOf(Color(0xFFFFFFFF))),
        Choice("orange", "Arancione", listOf(Color(0xFFEF6C00))),
        Choice("black", "Nero", listOf(Color(0xFF212121))),
        Choice("fuchsia", "Fucsia", listOf(Color(0xFFD81B60))),
        Choice("lightblue", "Azzurro", listOf(Color(0xFF29B6F6))),
        Choice("rainbow", "Arcobaleno", listOf(
            Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B),
            Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA)
        ))
    )

    fun choice(key: String?): Choice? = choices.firstOrNull { it.key == key }

    fun subjectKey(text: String): String? {
        val normalized = text.lowercase()
            .replace('à', 'a').replace('è', 'e').replace('é', 'e')
            .replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')
        return when {
            "educazione civica" in normalized || "ed. civica" in normalized || "ed civica" in normalized -> "educazione_civica"
            "matematica" in normalized || "mat." in normalized -> "matematica"
            "italiano" in normalized || "lettere" in normalized -> "italiano"
            "fisica" in normalized -> "fisica"
            "chimica" in normalized -> "chimica"
            "biologia" in normalized -> "biologia"
            "scienze" in normalized -> "scienze"
            "inglese" in normalized || "english" in normalized -> "inglese"
            "storia" in normalized -> "storia"
            "motoria" in normalized || "educazione fisica" in normalized -> "motoria"
            "filosofia" in normalized -> "filosofia"
            "religione" in normalized -> "religione"
            "informatica" in normalized -> "informatica"
            else -> null
        }
    }

    fun choiceForSubject(text: String, overrides: Map<String, String>): Choice? {
        val key = subjectKey(text) ?: return null
        val subject = subjects.firstOrNull { it.key == key } ?: return null
        return choice(overrides[key] ?: subject.defaultColor)
    }
}
