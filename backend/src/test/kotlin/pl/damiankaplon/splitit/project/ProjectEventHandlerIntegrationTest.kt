package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.project.invitation.ProjectInvitationEvent
import java.util.*

@SpringBootTest
@Import(PostgreTestContainerConfig::class)
class ProjectEventHandlerIntegrationTest @Autowired constructor(
    private val handler: ProjectEventHandler,
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
) {

    @Test
    fun `accepted invitation adds the user as a project member`() {
        val project = projects.save(Project("owner-id", "Trip to Rome", PLN))

        handler.handle(
            ProjectInvitationEvent.ProjectInvitationAccepted(UUID.randomUUID(), "guest-id", "guest", project.id)
        )

        val member = projectMembers.findByProjectId(project.id).single()
        assertEquals("guest-id", member.userId)
        assertEquals("guest", member.username)
    }
}
