package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ProjectEndpointsIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
    private val projectMembers: ProjectMemberRepository,
) {

	@Test
    fun `creating a project requires authentication`() {
        mockMvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Trip to Rome"}"""
        }.andExpect { status { isUnauthorized() } }
	}

	@Test
    fun `creator becomes the owner and a member of the new project`() {
        val userId = UUID.randomUUID().toString()
        val response = mockMvc.post("/projects") {
            with(jwt().jwt { it.subject(userId).claim("preferred_username", "alice") })
			contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Trip to Rome"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.name") { value("Trip to Rome") }
            jsonPath("$.ownerId") { value(userId) }
        }.andReturn().response.contentAsString

        val projectId = UUID.fromString(Regex("\"id\":\"([^\"]+)\"").find(response)!!.groupValues[1])
        val members = projectMembers.findByProjectId(projectId)
        assertEquals(1, members.size)
        assertEquals(userId, members.single().userId)
        assertEquals("alice", members.single().username)
	}
}
