package circolareplus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    STUDENT,
    REPRESENTATIVE,
    SECURITY_GUARD
}

@Serializable
data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val username: String,
    val role: UserRole = UserRole.STUDENT
)

@Serializable
data class StudentProfile(
    val userId: String,
    val heightCm: Int, // Discrete 140..210 with step of 5cm
    val className: String = "", // e.g., "4B", "3A", etc.
    val academicYear: String = "", // e.g., "2026/2027"
    val priorityPass: Boolean = false,
    val notificationBoardEnabled: Boolean = true
) {
    init {
        require(heightCm in 140..210) { "L'altezza deve essere compresa tra 140 e 210 cm." }
        require(heightCm % 5 == 0) { "L'altezza deve essere indicata a scaglioni di 5 cm." }
    }
}

@Serializable
data class RepresentativeRating(
    val studentId: String,
    val didactic: Int, // 1 (difficoltà) .. 5 (eccellente)
    val behavior: Int  // 1 (silenzioso) .. 5 (molto chiassoso)
) {
    init {
        require(didactic in 1..5) { "Valutazione didattica deve essere tra 1 e 5." }
        require(behavior in 1..5) { "Valutazione chiasso/comportamento deve essere tra 1 e 5." }
    }
}

@Serializable
enum class SocialPreferenceScore(val value: Int) {
    STRONG_AFFINITY(2),     // +2 (Max 2 selezionabili)
    MEDIUM_AFFINITY(1),     // +1 (Gradimento)
    NEUTRAL(0),             // 0 (Indifferente)
    MILD_REJECTION(-1),     // -1 (Preferirei di no)
    STRONG_REJECTION(-2);   // -2 (Rifiuto assoluto blindato, max 2)

    companion object {
        fun fromValue(v: Int): SocialPreferenceScore =
            entries.firstOrNull { it.value == v } ?: NEUTRAL
    }
}

@Serializable
data class SocialPreference(
    val fromStudentId: String,
    val toStudentId: String,
    val score: SocialPreferenceScore
)
