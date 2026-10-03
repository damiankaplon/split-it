package pl.damiankaplon.splitit.project.invitation

import java.util.*

sealed interface ProjectInvitationEvent {
    val invitationId: UUID

    data class ProjectInvitationCreated(
        override val invitationId: UUID,
        val token: String,
        val projectId: UUID,
    ) : ProjectInvitationEvent

    data class ProjectInvitationAccepted(
        override val invitationId: UUID,
        val userId: String,
        val username: String,
        val projectId: UUID,
    ) : ProjectInvitationEvent
}