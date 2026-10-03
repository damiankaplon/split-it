package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ProjectEndpointsIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
) {

	@Test
	fun `projects endpoint requires authentication`() {
		mockMvc.get("/projects").andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `creates and reads a project`() {
		val ownerId = UUID.randomUUID()
		val location = mockMvc.post("/projects") {
			with(jwt())
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerId":"$ownerId","name":"Trip to Rome"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.name") { value("Trip to Rome") }
			jsonPath("$.ownerId") { value(ownerId.toString()) }
		}.andReturn().response.getHeader("Location")!!

		mockMvc.get(location) { with(jwt()) }
			.andExpect {
				status { isOk() }
				jsonPath("$.name") { value("Trip to Rome") }
			}
	}
}
