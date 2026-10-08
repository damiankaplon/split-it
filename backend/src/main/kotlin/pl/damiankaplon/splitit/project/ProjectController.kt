package pl.damiankaplon.splitit.project

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.balancing.SaldoProjectEventHandler
import pl.damiankaplon.splitit.subjectOrThrow
import pl.damiankaplon.splitit.usernameOrThrow
import java.util.*

@RestController
class ProjectController(
    private val projects: ProjectRepository,
    private val saldoProjectEventHandler: SaldoProjectEventHandler,
) {

    data class CreateProjectRequest(
        @field:NotBlank val name: String,
        @field:NotBlank @field:IsoCurrency val currency: String,
    ) {
        val currencyCode: Currency by lazy { Currency.getInstance(currency.trim().uppercase()) }
    }

    data class CurrencyResponse(
        val code: String,
        val minorUnits: Int,
    )

    data class ProjectResponse(
        val id: UUID,
        val name: String,
        val ownerId: String,
        val currency: CurrencyResponse,
    )

    data class ProjectMemberResponse(
        val memberId: String,
        val name: String,
    )

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ProjectResponse {
        val owner = ProjectMember(userId = jwt.subjectOrThrow(), username = jwt.usernameOrThrow())
        val project = Project(owner = owner, name = request.name, currency = request.currencyCode)
        projects.save(project)
        saldoProjectEventHandler.handle(ProjectEvent.ProjectCreated(project.id, owner.userId))
        return project.toResponse()
    }

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
    ): Set<ProjectResponse> {
        return projects.findByMemberUserId(jwt.subjectOrThrow())
            .mapTo(linkedSetOf()) { it.toResponse() }
    }

    @GetMapping("/projects/{projectId}")
    @Transactional(readOnly = true)
    fun listMembers(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Set<ProjectMemberResponse> {
        val project = projects.findByIdOrThrow(projectId)
        if (project.hasMember(jwt.subjectOrThrow())) {
            return project.members.mapTo(linkedSetOf()) { ProjectMemberResponse(it.userId, it.username) }
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=${project.id} member")
    }

    private fun Project.toResponse() = ProjectResponse(
        id = id,
        name = name,
        ownerId = ownerId,
        currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
    )
}
