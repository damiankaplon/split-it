package pl.damiankaplon.splitit.project

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.subjectOrThrow
import pl.damiankaplon.splitit.usernameOrThrow
import java.util.*

@RestController
class ProjectController(
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
) {

    data class CreateProjectRequest(
        @field:NotBlank val name: String,
    )

    data class ProjectResponse(val id: UUID, val name: String, val ownerId: String)
    data class ProjectMemberResponse(val memberId: String, val name: String)

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ProjectResponse {
        val userId = jwt.subjectOrThrow()
        val project = projects.save(Project(ownerId = userId, name = request.name))
        projectMembers.save(ProjectMember(project, userId, jwt.usernameOrThrow()))
        return ProjectResponse(project.id, project.name, project.ownerId)
    }

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
    ): Set<ProjectResponse> {
        return projectMembers.findByUserId(jwt.subjectOrThrow())
            .map { it.project }
            .mapTo(linkedSetOf()) { ProjectResponse(it.id, it.name, it.ownerId) }
    }

    @GetMapping("/projects/{projectId}")
    @Transactional(readOnly = true)
    fun listMembers(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Set<ProjectMemberResponse> {
        val project = projects.findByIdOrThrow(projectId)
        val projectMembers = projectMembers.findByProject(project)
        val isProjectMember = projectMembers.map(ProjectMember::userId).contains(jwt.subjectOrThrow())
        if (isProjectMember) {
            return projectMembers.mapTo(linkedSetOf()) { ProjectMemberResponse(it.userId, it.username) }
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=${project.id} member")
    }
}
