package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import pl.damiankaplon.splitit.PostgreTestContainerConfig

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ProjectMembersEndpointIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", id) }

    @Test
    fun `project members endpoint returns the members stored in the repository`() {
        val project = projects.save(Project("alice-id", "Trip to Rome"))
        projectMembers.save(ProjectMember(project, "alice-id", "alice"))
        projectMembers.save(ProjectMember(project, "bob-id", "bob"))

        mockMvc.get("/projects/${project.id}") { with(asUser("bob-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[?(@.memberId == 'alice-id')].name") { value("alice") }
                jsonPath("$[?(@.memberId == 'bob-id')].name") { value("bob") }
            }
    }

    @Test
    fun `project members endpoint is forbidden for non-members`() {
        val project = projects.save(Project("alice-id", "Trip to Rome"))
        projectMembers.save(ProjectMember(project, "alice-id", "alice"))

        mockMvc.get("/projects/${project.id}") { with(asUser("stranger-id")) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `project list contains only projects the user is a member of`() {
        val joined = projects.save(Project("alice-id", "Joined"))
        val other = projects.save(Project("alice-id", "Other"))
        projectMembers.save(ProjectMember(joined, "carol-id", "carol"))
        projectMembers.save(ProjectMember(other, "alice-id", "alice"))

        mockMvc.get("/projects") { with(asUser("carol-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(joined.id.toString()) }
            }
    }
}
