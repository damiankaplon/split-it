package pl.damiankaplon.splitit.project

import java.util.UUID

sealed interface ProjectEvent {
    val projectId: UUID

    data class ProjectCreated(
        override val projectId: UUID,
        val userId: String,
    ) : ProjectEvent

    data class MemberJoined(
        override val projectId: UUID,
        val userId: String,
    ) : ProjectEvent
}
