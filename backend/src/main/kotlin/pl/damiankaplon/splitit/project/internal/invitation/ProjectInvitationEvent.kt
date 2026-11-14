package pl.damiankaplon.splitit.project.internal.invitation

import java.util.UUID

sealed interface ProjectInvitationEvent {
    val invitationId: UUID

    data class ProjectInvitationCreated(
        override val invitationId: UUID,
        val token: String,
        val projectId: UUID,
    ) : ProjectInvitationEvent
}
