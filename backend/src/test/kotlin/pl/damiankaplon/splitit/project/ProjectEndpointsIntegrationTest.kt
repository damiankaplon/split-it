package pl.damiankaplon.splitit.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.balancing.SaldoRepository
import java.util.*

class ProjectEndpointsIntegrationTest @Autowired constructor(
    private val saldos: SaldoRepository,
    private val projects: ProjectRepository,
) : RollbackIntegrationTest() {

    private fun asAlice(id: String) = asUser(id, "alice")

	@Test
    fun `creating a project requires authentication`() {
        mockMvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Trip to Rome","currency":"EUR"}"""
        }.andExpect { status { isUnauthorized() } }
	}

	@Test
    fun `creator becomes the owner and a member of the new project`() {
        val userId = UUID.randomUUID().toString()
        val response = mockMvc.post("/projects") {
            with(asAlice(userId))
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
            with(asAlice(userId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Zakopane","currency":"pln"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currency.code") { value("PLN") }
            jsonPath("$.currency.minorUnits") { value(2) }
        }

        mockMvc.get("/projects") { with(asAlice(userId)) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].currency.code") { value("PLN") }
                jsonPath("$[0].currency.minorUnits") { value(2) }
            }
    }

    @Test
    fun `minor units follow the currency`() {
        mockMvc.post("/projects") {
            with(asAlice(UUID.randomUUID().toString()))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Tokyo","currency":"JPY"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currency.code") { value("JPY") }
            jsonPath("$.currency.minorUnits") { value(0) }
        }
    }

    @Test
    fun `split policy defaults to equal share`() {
        val userId = UUID.randomUUID().toString()
        mockMvc.post("/projects") {
            with(asAlice(userId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Zakopane","currency":"PLN"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.splitPolicy") { value("EQUAL_SHARE") }
        }

        mockMvc.get("/projects") { with(asAlice(userId)) }
            .andExpect { jsonPath("$[0].splitPolicy") { value("EQUAL_SHARE") } }
    }

    @Test
    fun `split policy can be chosen from the available ones`() {
        mockMvc.post("/projects") {
            with(asAlice(UUID.randomUUID().toString()))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Zakopane","currency":"PLN","splitPolicy":"EQUAL_SHARE"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.splitPolicy") { value("EQUAL_SHARE") }
        }

        mockMvc.post("/projects") {
            with(asAlice(UUID.randomUUID().toString()))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Zakopane","currency":"PLN","splitPolicy":"WHOEVER_PAYS"}"""
        }.andExpect { status { isBadRequest() } }
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
                with(asAlice(UUID.randomUUID().toString()))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
    }
}
