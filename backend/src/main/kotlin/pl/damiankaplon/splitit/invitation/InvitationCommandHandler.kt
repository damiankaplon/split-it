package pl.damiankaplon.splitit.invitation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.Time
import pl.damiankaplon.splitit.project.ProjectEvent
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.security.SecureRandom
import java.time.Duration
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
@Transactional
class InvitationCommandHandler(
    private val projects: ProjectRepository,
    private val invitations: ProjectInvitationRepository,
    private val time: Time,
) {

    private val random = SecureRandom()

    data class CreateInvitation(
        val projectId: UUID,
        val userId: String,
    )

    data class AcceptInvitation(
        val userId: String,
        val username: String,
        val projectId: UUID,
        val token: String,
    )

    fun handle(command: CreateInvitation): ProjectInvitationEvent.ProjectInvitationCreated {
        logger.info { "Received command: $command" }
            val project = projects.findByIdOrThrow(command.projectId)
            require(project.ownerId == command.userId)
            fun newToken(): String {
                val bytes = ByteArray(32).also { random.nextBytes(it) }
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            }

            val invitation = Invitation(
                projectId = command.projectId,
                token = newToken(),
                expiresAt = time.now().plus(Duration.ofDays(5)),
                createdAt = time.now(),
            ).also(invitations::save)
        return ProjectInvitationEvent.ProjectInvitationCreated(
                invitation.id,
                invitation.token,
                invitation.projectId
            )
    }

    fun handle(command: AcceptInvitation): ProjectEvent.MemberJoined {
        logger.info { "Received command: $command" }
        val invitation = invitations.findByProjectIdAndTokenOrThrow(command.projectId, command.token)
        require(invitation.isAccepted.not()) { "$invitation is already accepted" }
        require(time.now().isBefore(invitation.expiresAt)) { "$invitation is expired" }
        invitation.acceptedBy = command.userId
        val project = projects.findByIdOrThrow(invitation.projectId)
        project.add(ProjectMember(command.userId, command.username))
        return ProjectEvent.MemberJoined(command.projectId, command.userId)
    }
}
