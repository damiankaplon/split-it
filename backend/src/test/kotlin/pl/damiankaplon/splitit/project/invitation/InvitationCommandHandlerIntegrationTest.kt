package pl.damiankaplon.splitit.project.invitation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.Instant
import java.util.*

@SpringBootTest
@Import(PostgreTestContainerConfig::class)
class InvitationCommandHandlerIntegrationTest @Autowired constructor(
    private val handler: InvitationCommandHandler,
    private val projects: ProjectRepository,
    private val invitations: ProjectInvitationRepository,
) {

    private fun newProject(ownerId: String = UUID.randomUUID().toString()) =
        projects.save(Project(ownerId, "Trip to Rome"))

    private fun newInvitation(project: Project, expiresAt: Instant = Instant.now().plusSeconds(3600)) =
        invitations.save(
            Invitation(
                projectId = project.id,
                token = UUID.randomUUID().toString(),
                expiresAt = expiresAt,
                createdAt = Instant.now(),
            )
        )

    @Test
    fun `owner creates an invitation`() {
        val project = newProject()

        val event = handler.handle(InvitationCommandHandler.CreateInvitation(project.id, project.ownerId)).getOrThrow()

        assertEquals(project.id, event.projectId)
        val stored = invitations.findByProjectIdAndTokenOrThrow(project.id, event.token)
        assertEquals(event.invitationId, stored.id)
        assertNull(stored.acceptedBy)
        assertTrue(stored.expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `non-owner cannot create an invitation`() {
        val project = newProject()

        val result = handler.handle(InvitationCommandHandler.CreateInvitation(project.id, "someone-else"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `accepting an invitation marks it accepted and emits an event`() {
        val project = newProject()
        val invitation = newInvitation(project)

        val event = handler.handle(
            InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token)
        ).getOrThrow()

        assertEquals(invitation.id, event.invitationId)
        assertEquals("guest-id", event.userId)
        assertEquals("guest", event.username)
        assertEquals(project.id, event.projectId)
        assertEquals("guest-id", invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
    }

    @Test
    fun `an invitation cannot be accepted twice`() {
        val project = newProject()
        val invitation = newInvitation(project)
        val accept = InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token)
        handler.handle(accept).getOrThrow()

        val result = handler.handle(accept.copy(userId = "other-id", username = "other"))

        assertTrue(result.isFailure)
        assertEquals("guest-id", invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
    }

    @Test
    fun `an expired invitation cannot be accepted`() {
        val project = newProject()
        val invitation = newInvitation(project, expiresAt = Instant.now().minusSeconds(60))

        val result = handler.handle(
            InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token)
        )

        assertTrue(result.isFailure)
        assertNull(invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
    }

    @Test
    fun `an unknown token cannot be accepted`() {
        val project = newProject()

        val result = handler.handle(
            InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, "no-such-token")
        )

        assertTrue(result.isFailure)
    }
}
