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
