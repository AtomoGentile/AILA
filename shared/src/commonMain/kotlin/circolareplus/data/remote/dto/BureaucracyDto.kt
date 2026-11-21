package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class BureaucracyFeedDto(
    val canManage: Boolean,
    val practices: List<BureaucracyPracticeDto>
)

@Serializable
internal data class BureaucracyPracticeDto(
    val id: String,
    val title: String,
    val description: String,
    val archivedAt: String?,
    val received: Boolean?,
    val dueDate: String? = null
)

@Serializable
internal data class BureaucracyDeliveriesDto(val deliveries: List<BureaucracyDeliveryDto>)

@Serializable
internal data class BureaucracyDeliveryDto(val studentId: String, val name: String, val received: Boolean)

@Serializable
internal data class CreateBureaucracyPracticeDto(val title: String, val description: String, val dueDate: String? = null)

@Serializable
internal data class BureaucracyDeadlineDto(val dueDate: String?)

@Serializable
internal data class SetBureaucracyDeliveryDto(val received: Boolean)
