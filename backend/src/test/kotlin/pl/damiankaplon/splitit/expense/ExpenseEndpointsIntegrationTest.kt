package pl.damiankaplon.splitit.expense

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectMemberRepository
import pl.damiankaplon.splitit.project.ProjectRepository
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ExpenseEndpointsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
    private val expenses: ExpenseRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", id) }

    private fun projectWithMember(userId: String): Project {
        val project = projects.save(Project(userId, "Trip to Rome"))
        projectMembers.save(ProjectMember(project, userId, userId))
        return project
    }

    private fun createExpense(project: Project, userId: String, body: String): String =
        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
            .let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    @Test
    fun `member creates and reads an expense`() {
        val project = projectWithMember("alice-id")

        val expenseId = createExpense(
            project, "alice-id",
            """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":12050,"tag":"Food"}"""
        )

        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Dinner") }
                jsonPath("$.date") { value("2026-10-03T19:30:00") }
                jsonPath("$.amount") { value(12050) }
                jsonPath("$.tag") { value("Food") }
                jsonPath("$.createdBy") { value("alice-id") }
            }
    }

    @Test
    fun `tag is optional`() {
        val project = projectWithMember("alice-id")

        val expenseId = createExpense(
            project, "alice-id",
            """{"title":"Taxi","date":"2026-10-03T10:00:00","amount":3000}"""
        )

        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.tag") { doesNotExist() } }
    }

    @Test
    fun `invalid expense is rejected`() {
        val project = projectWithMember("alice-id")

        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":" ","date":"2026-10-03T10:00:00","amount":3000}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Taxi","amount":3000}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `expenses are listed newest first`() {
        val project = projectWithMember("alice-id")
        createExpense(project, "alice-id", """{"title":"Older","date":"2026-10-01T10:00:00","amount":100}""")
        createExpense(project, "alice-id", """{"title":"Newer","date":"2026-10-02T10:00:00","amount":200}""")

        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[0].title") { value("Newer") }
                jsonPath("$.items[1].title") { value("Older") }
                jsonPath("$.page") { value(0) }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.hasNext") { value(false) }
            }
    }

    @Test
    fun `pages walk the whole list without gaps or duplicates`() {
        val project = projectWithMember("alice-id")
        // Two expenses share a date so the id tie-breaker is exercised across a page boundary
        val dates = listOf("2026-10-05", "2026-10-04", "2026-10-04", "2026-10-03", "2026-10-02")
        dates.forEachIndexed { i, date ->
            createExpense(project, "alice-id", """{"title":"E$i","date":"${date}T10:00:00","amount":1}""")
        }

        val seen = (0..2).flatMap { page ->
            val body = mockMvc.get("/projects/${project.id}/expenses") {
                with(asUser("alice-id"))
                param("page", "$page")
                param("size", "2")
            }.andExpect {
                status { isOk() }
                jsonPath("$.page") { value(page) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
                jsonPath("$.hasNext") { value(page < 2) }
            }.andReturn().response.contentAsString
            Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        }

        assertEquals(5, seen.toSet().size)
        assertEquals(listOf("E0", "E3", "E4"), seen.filter { it in setOf("E0", "E3", "E4") })
        assertEquals(setOf("E1", "E2"), seen.subList(1, 3).toSet())
    }

    @Test
    fun `invalid paging parameters are rejected`() {
        val project = projectWithMember("alice-id")

        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("page", "-1") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("size", "0") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("size", "101") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list filters are optional and combined`() {
        val project = projectWithMember("alice-id")
        createExpense(project, "alice-id", """{"title":"Pizza dinner","date":"2026-10-01T19:00:00","amount":1,"tag":"Food"}""")
        createExpense(project, "alice-id", """{"title":"Pizza lunch","date":"2026-10-03T12:00:00","amount":1,"tag":"Food"}""")
        createExpense(project, "alice-id", """{"title":"Museum","date":"2026-10-02T10:00:00","amount":1,"tag":"Culture"}""")
        createExpense(project, "alice-id", """{"title":"Taxi 100%","date":"2026-10-02T11:00:00","amount":1}""")

        fun titles(vararg params: Pair<String, String>): List<String> {
            val body = mockMvc.get("/projects/${project.id}/expenses") {
                with(asUser("alice-id"))
                params.forEach { (k, v) -> param(k, v) }
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            return Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        }

        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles("tag" to "food"))
        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles("title" to "PIZZA"))
        // LIKE wildcards in the input are matched literally
        assertEquals(listOf("Taxi 100%"), titles("title" to "%"))
        assertEquals(emptyList<String>(), titles("title" to "_"))
        assertEquals(
            listOf("Taxi 100%", "Museum"),
            titles("from" to "2026-10-02T00:00:00", "to" to "2026-10-02T23:59:59"),
        )
        assertEquals(
            listOf("Pizza lunch"),
            titles("tag" to "Food", "from" to "2026-10-02T00:00:00"),
        )
        assertEquals(4, titles().size)
    }

    @Test
    fun `tags are reused case-insensitively and listed for the drop-down`() {
        val project = projectWithMember("alice-id")
        createExpense(project, "alice-id", """{"title":"A","date":"2026-10-01T10:00:00","amount":1,"tag":"Food"}""")
        createExpense(project, "alice-id", """{"title":"B","date":"2026-10-01T10:00:00","amount":1,"tag":" food "}""")
        createExpense(project, "alice-id", """{"title":"C","date":"2026-10-01T10:00:00","amount":1,"tag":"Bars"}""")

        mockMvc.get("/projects/${project.id}/tags") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0]") { value("Bars") }
                jsonPath("$[1]") { value("Food") }
            }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.items[?(@.title == 'B')].tag") { value("Food") } }
    }

    @Test
    fun `tags are scoped to the project`() {
        val rome = projectWithMember("alice-id")
        val paris = projectWithMember("alice-id")
        createExpense(rome, "alice-id", """{"title":"A","date":"2026-10-01T10:00:00","amount":1,"tag":"Food"}""")

        mockMvc.get("/projects/${paris.id}/tags") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `any member updates an expense`() {
        val project = projectWithMember("alice-id")
        projectMembers.save(ProjectMember(project, "bob-id", "bob"))
        val expenseId = createExpense(
            project, "alice-id",
            """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":100,"tag":"Food"}"""
        )

        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(asUser("bob-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Lunch","date":"2026-10-04T12:00:00","amount":250}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Lunch") }
            jsonPath("$.date") { value("2026-10-04T12:00:00") }
            jsonPath("$.amount") { value(250) }
            jsonPath("$.tag") { doesNotExist() }
        }

        // The previously used tag stays available in the drop-down
        mockMvc.get("/projects/${project.id}/tags") { with(asUser("bob-id")) }
            .andExpect { jsonPath("$[0]") { value("Food") } }
    }

    @Test
    fun `member deletes an expense`() {
        val project = projectWithMember("alice-id")
        val expenseId = createExpense(project, "alice-id", """{"title":"A","date":"2026-10-01T10:00:00","amount":1}""")

        mockMvc.delete("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNoContent() } }

        assertFalse(expenses.existsById(UUID.fromString(expenseId)))
        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `expense of another project is not found`() {
        val rome = projectWithMember("alice-id")
        val paris = projectWithMember("alice-id")
        val expenseId = createExpense(rome, "alice-id", """{"title":"A","date":"2026-10-01T10:00:00","amount":1}""")

        mockMvc.get("/projects/${paris.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
        mockMvc.delete("/projects/${paris.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `non-members are forbidden`() {
        val project = projectWithMember("alice-id")
        val expenseId = createExpense(project, "alice-id", """{"title":"A","date":"2026-10-01T10:00:00","amount":1}""")
        val stranger = asUser("stranger-id")

        mockMvc.post("/projects/${project.id}/expenses") {
            with(stranger)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"A","date":"2026-10-01T10:00:00","amount":1}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(stranger) }.andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isForbidden() } }
        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(stranger)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"A","date":"2026-10-01T10:00:00","amount":1}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.delete("/projects/${project.id}/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/tags") { with(stranger) }.andExpect { status { isForbidden() } }
    }
}
