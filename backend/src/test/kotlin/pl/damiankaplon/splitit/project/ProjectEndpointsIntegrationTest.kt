package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import pl.damiankaplon.splitit.balancing.SaldoRepository
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ProjectEndpointsIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
    private val saldos: SaldoRepository,
    private val projects: ProjectRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", "alice") }

	@Test
    fun `creating a project requires authentication`() {
        mockMvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Trip to Rome","currency":"EUR"}"""
        }.andExpect { status { isUnauthorized() } }
	}

	@Test
    @org.springframework.transaction.annotation.Transactional
    fun `creator becomes the owner and a member of the new project`() {
        val userId = UUID.randomUUID().toString()
        val response = mockMvc.post("/projects") {
            with(asUser(userId))
			contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Trip to Rome","currency":"EUR"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.name") { value("Trip to Rome") }
            jsonPath("$.ownerId") { value(userId) }
        }.andReturn().response.contentAsString

        val projectId = UUID.fromString(Regex("\"id\":\"([^\"]+)\"").find(response)!!.groupValues[1])
        val members = projects.findByIdOrThrow(projectId).members
        assertEquals(1, members.size)
        assertEquals(userId, members.single().userId)
        assertEquals("alice", members.single().username)
        assertTrue(saldos.existsById(projectId))
	}

    @Test
    fun `project carries its currency code and minor units`() {
        val userId = UUID.randomUUID().toString()
        mockMvc.post("/projects") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Zakopane","currency":"pln"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currency.code") { value("PLN") }
            jsonPath("$.currency.minorUnits") { value(2) }
        }

        mockMvc.get("/projects") { with(asUser(userId)) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].currency.code") { value("PLN") }
                jsonPath("$[0].currency.minorUnits") { value(2) }
            }
    }

    @Test
    fun `minor units follow the currency`() {
        mockMvc.post("/projects") {
            with(asUser(UUID.randomUUID().toString()))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Tokyo","currency":"JPY"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currency.code") { value("JPY") }
            jsonPath("$.currency.minorUnits") { value(0) }
        }
    }

    @Test
    fun `creating a project requires a known currency`() {
        // XAU (gold) is a valid ISO code but has no minor units, so amounts couldn't be stored
        for (body in listOf(
            """{"name":"Trip"}""",
            """{"name":"Trip","currency":"ABC"}""",
            """{"name":"Trip","currency":"XAU"}"""
        )) {
            mockMvc.post("/projects") {
                with(asUser(UUID.randomUUID().toString()))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
    }
}
