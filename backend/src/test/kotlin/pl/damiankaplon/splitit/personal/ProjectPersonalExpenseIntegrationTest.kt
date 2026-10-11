package pl.damiankaplon.splitit.personal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.balancing.Saldo
import pl.damiankaplon.splitit.balancing.SaldoRepository
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.util.*

/**
 * Project expenses show up among every member's personal expenses as their
 * share.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class ProjectPersonalExpenseIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projects: ProjectRepository,
    private val saldos: SaldoRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", id) }

    /**
     * Each test uses its own users, since personal expenses are scoped to the
     * user only.
     */
    private fun newUser() = "user-${UUID.randomUUID()}"

    private fun project(owner: String, vararg others: String): Project {
        val project = Project("Trip to Rome", ProjectMember(owner, owner), PLN)
        others.forEach { project.add(ProjectMember(it, it)) }
        projects.save(project)
        saldos.save(Saldo(project.id))
        return project
    }

    private fun idOf(response: String) = Regex("\"id\":\"([^\"]+)\"").find(response)!!.groupValues[1]

    private fun createExpense(project: Project, userId: String, body: String): String =
        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString.let(::idOf)

    private fun createManualExpense(userId: String, body: String): String =
        mockMvc.post("/personal/expenses") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString.let(::idOf)

    private fun personalTitles(userId: String, vararg params: Pair<String, String>): List<String> {
        val body = mockMvc.get("/personal/expenses") {
            with(asUser(userId))
            params.forEach { (k, v) -> param(k, v) }
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `every member gets an equal share of a new project expense`() {
        val alice = newUser()
        val bob = newUser()
        val project = project(alice, bob)

        createExpense(
            project, alice,
            """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":1000,"tag":"Food"}"""
        )

        for (member in listOf(alice, bob)) {
            mockMvc.get("/personal/expenses") { with(asUser(member)) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.items[0].title") { value("Dinner") }
                    jsonPath("$.items[0].date") { value("2026-10-03T19:30:00") }
                    jsonPath("$.items[0].amount") { value(500) }
                    jsonPath("$.items[0].currency.code") { value("PLN") }
                    jsonPath("$.items[0].tag") { value("Food") }
                    jsonPath("$.items[0].source") { value("PROJECT") }
                    jsonPath("$.items[0].projectId") { value(project.id.toString()) }
                }
        }
    }

    @Test
    fun `updating a project expense updates every share`() {
        val alice = newUser()
        val bob = newUser()
        val project = project(alice, bob)
        val expenseId = createExpense(
            project, alice,
            """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":1000,"tag":"Food"}"""
        )

        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(asUser(bob))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Lunch","date":"2026-10-04T12:00:00","amount":3000,"tag":"Bars"}"""
        }.andExpect { status { isOk() } }

        for (member in listOf(alice, bob)) {
            mockMvc.get("/personal/expenses") { with(asUser(member)) }
                .andExpect {
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.items[0].title") { value("Lunch") }
                    jsonPath("$.items[0].date") { value("2026-10-04T12:00:00") }
                    jsonPath("$.items[0].amount") { value(1500) }
                    jsonPath("$.items[0].tag") { value("Bars") }
                }
        }
    }

    @Test
    fun `deleting a project expense removes every share`() {
        val alice = newUser()
        val bob = newUser()
        val project = project(alice, bob)
        val expenseId =
            createExpense(project, alice, """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":1000}""")

        mockMvc.delete("/projects/${project.id}/expenses/$expenseId") { with(asUser(alice)) }
            .andExpect { status { isNoContent() } }

        assertEquals(emptyList<String>(), personalTitles(alice))
        assertEquals(emptyList<String>(), personalTitles(bob))
    }

    @Test
    fun `manual expenses and project shares are listed and filtered together`() {
        val alice = newUser()
        val bob = newUser()
        val project = project(alice, bob)
        createManualExpense(
            bob,
            """{"title":"Pizza at home","date":"2026-10-01T19:00:00","amount":1,"currency":"PLN","tag":"Food"}"""
        )
        createManualExpense(bob, """{"title":"Cinema","date":"2026-10-02T20:00:00","amount":1,"currency":"PLN"}""")
        createExpense(
            project,
            alice,
            """{"title":"Pizza in Rome","date":"2026-10-03T19:00:00","amount":2,"tag":"food"}"""
        )

        assertEquals(listOf("Pizza in Rome", "Cinema", "Pizza at home"), personalTitles(bob))
        assertEquals(listOf("Pizza in Rome", "Pizza at home"), personalTitles(bob, "title" to "PIZZA"))
        assertEquals(listOf("Pizza in Rome", "Pizza at home"), personalTitles(bob, "tag" to "FOOD"))
        assertEquals(listOf("Pizza in Rome", "Cinema"), personalTitles(bob, "from" to "2026-10-02T00:00:00"))
        assertEquals(listOf("Cinema", "Pizza at home"), personalTitles(bob, "to" to "2026-10-02T23:59:59"))
        assertEquals(listOf("Cinema"), personalTitles(bob, "page" to "1", "size" to "1"))

        mockMvc.get("/personal/expenses") { with(asUser(bob)); param("title", "pizza") }
            .andExpect { jsonPath("$.totalElements") { value(2) } }
        // The same tag from both kinds is offered once
        mockMvc.get("/personal/tags") { with(asUser(bob)) }
            .andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0]") { value("Food") }
            }
    }

    @Test
    fun `project shares can be read but only changed through the project expense`() {
        val alice = newUser()
        val project = project(alice)
        createExpense(project, alice, """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":1000}""")
        val shareId = mockMvc.get("/personal/expenses") { with(asUser(alice)) }
            .andReturn().response.contentAsString.let(::idOf)

        mockMvc.get("/personal/expenses/$shareId") { with(asUser(alice)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.source") { value("PROJECT") }
                jsonPath("$.amount") { value(1000) }
            }
        mockMvc.put("/personal/expenses/$shareId") {
            with(asUser(alice))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Changed","date":"2026-10-03T19:30:00","amount":1,"currency":"PLN"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.delete("/personal/expenses/$shareId") { with(asUser(alice)) }
            .andExpect { status { isConflict() } }

        assertEquals(listOf("Dinner"), personalTitles(alice))
    }
}
