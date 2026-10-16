package pl.damiankaplon.splitit.personal

import org.hamcrest.Matchers.equalToIgnoringCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.project.Project
import java.util.*

/**
 * Project expenses show up among every member's personal expenses as their
 * share.
 */
class ProjectPersonalExpenseIntegrationTest : RollbackIntegrationTest() {

    /**
     * Each test uses its own users, since personal expenses are scoped to the
     * user only.
     */
    private fun newUser() = "user-${UUID.randomUUID()}"

    private fun project(owner: String, vararg others: String): Project = testData.createProject(owner, *others)

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

        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser(alice))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Dinner","date":"2026-10-03T19:30:00Z","amount":1000,"tag":"Food"}"""
        }.andExpect { status { isCreated() } }

        for (member in listOf(alice, bob)) {
            mockMvc.get("/personal/expenses") { with(asUser(member)) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.items[0].title") { value("Dinner") }
                    jsonPath("$.items[0].date") { value("2026-10-03T19:30:00Z") }
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
        val expenseId = testData.createExpense(project, alice, "Dinner", "2026-10-03T19:30:00Z", 1000, "Food")

        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(asUser(bob))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Lunch","date":"2026-10-04T12:00:00Z","amount":3000,"tag":"Bars"}"""
        }.andExpect { status { isOk() } }

        for (member in listOf(alice, bob)) {
            mockMvc.get("/personal/expenses") { with(asUser(member)) }
                .andExpect {
                    jsonPath("$.totalElements") { value(1) }
                    jsonPath("$.items[0].title") { value("Lunch") }
                    jsonPath("$.items[0].date") { value("2026-10-04T12:00:00Z") }
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
            testData.createExpense(project, alice, "Dinner", "2026-10-03T19:30:00Z", 1000)

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
            """{"title":"Pizza at home","date":"2026-10-01T19:00:00Z","amount":1,"currency":"PLN","tag":"Food"}"""
        )
        createManualExpense(bob, """{"title":"Cinema","date":"2026-10-02T20:00:00Z","amount":1,"currency":"PLN"}""")
        testData.createExpense(project, alice, "Pizza in Rome", "2026-10-03T19:00:00Z", 2, "food")

        assertEquals(listOf("Pizza in Rome", "Cinema", "Pizza at home"), personalTitles(bob))
        assertEquals(listOf("Pizza in Rome", "Pizza at home"), personalTitles(bob, "title" to "PIZZA"))
        assertEquals(listOf("Pizza in Rome", "Pizza at home"), personalTitles(bob, "tag" to "FOOD"))
        assertEquals(listOf("Pizza in Rome", "Cinema"), personalTitles(bob, "from" to "2026-10-02T00:00:00Z"))
        assertEquals(listOf("Cinema", "Pizza at home"), personalTitles(bob, "to" to "2026-10-02T23:59:59Z"))
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
        testData.createExpense(project, alice, "Dinner", "2026-10-03T19:30:00Z", 1000)
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
            content = """{"title":"Changed","date":"2026-10-03T19:30:00Z","amount":1,"currency":"PLN"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.delete("/personal/expenses/$shareId") { with(asUser(alice)) }
            .andExpect { status { isConflict() } }

        assertEquals(listOf("Dinner"), personalTitles(alice))
    }

    @Test
    fun `totals by tag combine manual expenses and project shares per currency`() {
        val alice = newUser()
        val bob = newUser()
        val project = project(alice, bob)
        createManualExpense(
            alice,
            """{"title":"Groceries","date":"2026-10-05T10:00:00Z","amount":300,"currency":"PLN","tag":"Food"}"""
        )
        createManualExpense(
            alice,
            """{"title":"Croissant","date":"2026-10-06T10:00:00Z","amount":10,"currency":"EUR","tag":"Food"}"""
        )
        createManualExpense(
            alice,
            """{"title":"Later","date":"2026-11-01T00:00:00Z","amount":999,"currency":"PLN","tag":"Food"}"""
        )
        createManualExpense(alice, """{"title":"Bus","date":"2026-10-31T23:59:59Z","amount":40,"currency":"PLN"}""")
        testData.createExpense(project, alice, "Dinner", "2026-10-07T19:00:00Z", 1000, "food")

        mockMvc.get("/personal/expenses/totals-by-tag") {
            with(asUser(alice))
            param("from", "2026-10-01T00:00:00Z")
            param("to", "2026-10-31T23:59:59.999Z")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].currency.code") { value("EUR") }
            jsonPath("$[0].total") { value(10) }
            jsonPath("$[1].currency.code") { value("PLN") }
            jsonPath("$[1].tag") { value(equalToIgnoringCase("food")) }
            // Alice's 500 share of the project dinner plus her own 300
            jsonPath("$[1].total") { value(800) }
            jsonPath("$[2].tag") { doesNotExist() }
            jsonPath("$[2].total") { value(40) }
        }
        mockMvc.get("/personal/expenses/totals-by-tag") {
            with(asUser(bob))
            param("from", "2026-10-01T00:00:00Z")
            param("to", "2026-10-31T23:59:59.999Z")
        }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].total") { value(500) }
        }
    }
}
