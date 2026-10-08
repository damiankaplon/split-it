package pl.damiankaplon.splitit.invitation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.balancing.SaldoProjectEventHandler
import pl.damiankaplon.splitit.project.ProjectRepository
import pl.damiankaplon.splitit.subjectOrThrow
import pl.damiankaplon.splitit.usernameOrThrow
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
class InvitationController(
    private val invitationCommandHandler: InvitationCommandHandler,
    private val invitations: ProjectInvitationRepository,
    private val projects: ProjectRepository,
    private val saldoProjectEventHandler: SaldoProjectEventHandler,
) {

    data class InvitationResponse(
        val projectId: UUID,
        val token: String,
    )

    @PostMapping("/projects/{projectId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): InvitationResponse {
        val command = InvitationCommandHandler.CreateInvitation(projectId, jwt.subjectOrThrow())
        return try {
            invitationCommandHandler.handle(command)
                .let { created -> InvitationResponse(created.projectId, created.token) }
        } catch (t: Throwable) {
            throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Domain error", t)
        }
    }

    @PostMapping("/invitations")
    @Transactional
    fun join(
        @RequestParam("projectId") projectId: UUID,
        @RequestParam("token") token: String,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        val command = InvitationCommandHandler.AcceptInvitation(
            jwt.subjectOrThrow(),
            jwt.usernameOrThrow(),
            projectId,
            token
        )
        try {
            val memberJoined = invitationCommandHandler.handle(command)
            saldoProjectEventHandler.handle(memberJoined)
        } catch (t: Throwable) {
            throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Domain error", t)
        }
    }

    data class InvitationPreview(
        val projectId: UUID,
        val projectName: String,
    )

    @GetMapping("/invitations")
    @Transactional(readOnly = true)
    fun preview(
        @RequestParam("projectId") projectId: UUID,
        @RequestParam("token") token: String,
    ): InvitationPreview {
        invitations.findByProjectIdAndTokenOrThrow(projectId, token)
        val project = projects.findByIdOrThrow(projectId)
        return InvitationPreview(projectId, project.name)
    }
}
