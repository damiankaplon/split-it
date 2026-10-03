package pl.damiankaplon.splitit.project

import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import pl.damiankaplon.splitit.project.invitation.ProjectInvitationEvent

@Component
@Transactional
class ProjectEventHandler(
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
) {

    fun handle(invitationAccepted: ProjectInvitationEvent.ProjectInvitationAccepted) {
        val project = projects.findByIdOrThrow(invitationAccepted.projectId)
        val projectMember = ProjectMember(project, invitationAccepted.userId, invitationAccepted.username)
        projectMembers.save(projectMember)
    }
}