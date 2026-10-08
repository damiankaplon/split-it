package pl.damiankaplon.splitit.invitation

import java.util.*

sealed interface ProjectInvitationEvent {
    val invitationId: UUID

    data class ProjectInvitationCreated(
        override val invitationId: UUID,
        val token: String,
        val projectId: UUID,
    ) : ProjectInvitationEvent
}