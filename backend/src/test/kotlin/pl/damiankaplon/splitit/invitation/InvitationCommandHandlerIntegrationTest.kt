package pl.damiankaplon.splitit.invitation

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectEvent
import pl.damiankaplon.splitit.project.ProjectMember
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
        projects.save(Project("Trip to Rome", ProjectMember(ownerId, "owner"), PLN))

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

        val event = handler.handle(InvitationCommandHandler.CreateInvitation(project.id, project.ownerId))

        assertEquals(project.id, event.projectId)
        val stored = invitations.findByProjectIdAndTokenOrThrow(project.id, event.token)
        assertEquals(event.invitationId, stored.id)
        assertNull(stored.acceptedBy)
        assertTrue(stored.expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `non-owner cannot create an invitation`() {
        val project = newProject()

        assertThatThrownBy { handler.handle(InvitationCommandHandler.CreateInvitation(project.id, "someone-else")) }
            .isNotNull
    }

    @Test
    fun `accepting an invitation marks it accepted and adds the user to the project`() {
        val project = newProject()
        val invitation = newInvitation(project)

        val event = handler.handle(
            InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token)
        )

        assertEquals(ProjectEvent.MemberJoined(project.id, "guest-id"), event)
        assertEquals("guest-id", invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
        assertTrue(projects.existsByIdAndMemberUserId(project.id, "guest-id"))
    }

    @Test
    fun `an invitation cannot be accepted twice`() {
        val project = newProject()
        val invitation = newInvitation(project)
        val accept = InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token)
        handler.handle(accept)

        assertThrows<IllegalArgumentException> { handler.handle(accept.copy(userId = "other-id", username = "other")) }

        assertEquals("guest-id", invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
        assertFalse(projects.existsByIdAndMemberUserId(project.id, "other-id"))
    }

    @Test
    fun `an expired invitation cannot be accepted`() {
        val project = newProject()
        val invitation = newInvitation(project, expiresAt = Instant.now().minusSeconds(60))

        assertThrows<IllegalArgumentException> {
            handler.handle(InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, invitation.token))
        }

        assertNull(invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
        assertFalse(projects.existsByIdAndMemberUserId(project.id, "guest-id"))
    }

    @Test
    fun `an unknown token cannot be accepted`() {
        val project = newProject()

        assertThrows<JpaObjectRetrievalFailureException> {
            handler.handle(InvitationCommandHandler.AcceptInvitation("guest-id", "guest", project.id, "no-such-token"))
        }
    }

    @Test
    fun `an existing member cannot accept an invitation and it stays unused`() {
        val owner = "owner-id"
        val project = newProject(owner)
        val invitation = newInvitation(project)

        assertThrows<IllegalArgumentException> {
            handler.handle(InvitationCommandHandler.AcceptInvitation(owner, "owner", project.id, invitation.token))
        }

        assertNull(invitations.findByProjectIdAndTokenOrThrow(project.id, invitation.token).acceptedBy)
    }
}
