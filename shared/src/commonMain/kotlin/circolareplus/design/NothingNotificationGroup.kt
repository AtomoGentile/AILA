package circolareplus.design

/** Stable channel identities, independent from translated display names. */
enum class NothingNotificationGroup(val key: String, val label: String) {
    CIRCULARS("circulars", "Circolari"), BOARD("board", "Bacheca"), FORUM("forum", "Pere"),
    POLLS("polls", "Sondaggi"), CALENDAR("calendar", "Calendario"), CLASS("class", "Posti e classe");
    companion object {
        fun fromCategory(category: String) = when(category) {
            "circulars" -> CIRCULARS
            "board" -> BOARD
            "forum" -> FORUM
            "polls", "ranking_polls" -> POLLS
            "calendar" -> CALENDAR
            else -> CLASS
        }
    }
}

internal fun nothingBarChannels(fraction: Float, segments: Int): List<Int> {
    if(!fraction.isFinite() || segments !in 1..6) return emptyList()
    val lit = kotlin.math.ceil(fraction.coerceIn(0f,1f)*segments).toInt()
    return (0 until lit).map { segments - 1 - it }
}
