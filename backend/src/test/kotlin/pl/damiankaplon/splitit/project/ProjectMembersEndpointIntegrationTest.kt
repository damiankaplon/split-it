package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import pl.damiankaplon.splitit.RollbackIntegrationTest

class ProjectMembersEndpointIntegrationTest : RollbackIntegrationTest() {

    @Test
    fun `project members endpoint returns the members stored in the repository`() {
        val project = testData.createProject("alice-id", ownerName = "alice")
        testData.addMember(project, "bob-id", "bob")

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
        val project = testData.createProject("alice-id", ownerName = "alice")

        mockMvc.get("/projects/${project.id}") { with(asUser("stranger-id")) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `project list contains only projects the user is a member of`() {
        val joined = testData.createProject("alice-id", name = "Joined", ownerName = "alice")
        testData.createProject("alice-id", name = "Other", ownerName = "alice")
        testData.addMember(joined, "carol-id", "carol")

        mockMvc.get("/projects") { with(asUser("carol-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(joined.id.toString()) }
            }
    }
}
