package pl.damiankaplon.splitit.project

import java.util.*

sealed interface ProjectEvent {
    val projectId: UUID

    data class ProjectCreated(
        override val projectId: UUID,
    ) : ProjectEvent
}
