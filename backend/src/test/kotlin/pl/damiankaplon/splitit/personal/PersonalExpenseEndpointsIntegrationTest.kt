package pl.damiankaplon.splitit.personal

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
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class PersonalExpenseEndpointsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val expenses: PersonalExpenseRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", id) }

    /**
     * Each test uses its own user, since personal expenses are scoped to the
     * user only.
     */
    private fun newUser() = "user-${UUID.randomUUID()}"

    private fun createExpense(userId: String, body: String): String =
        mockMvc.post("/personal/expenses") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
            .let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun titles(userId: String, vararg params: Pair<String, String>): List<String> {
        val body = mockMvc.get("/personal/expenses") {
            with(asUser(userId))
            params.forEach { (k, v) -> param(k, v) }
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `user creates and reads a personal expense`() {
        val alice = newUser()

        val expenseId = createExpense(
            alice,
            """{"title":"Groceries","date":"2026-10-03T19:30:00","amount":12050,"currency":"eur","tag":"Food"}"""
        )

        mockMvc.get("/personal/expenses/$expenseId") { with(asUser(alice)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Groceries") }
                jsonPath("$.date") { value("2026-10-03T19:30:00") }
                jsonPath("$.amount") { value(12050) }
                jsonPath("$.currency.code") { value("EUR") }
                jsonPath("$.currency.minorUnits") { value(2) }
                jsonPath("$.tag") { value("Food") }
                jsonPath("$.source") { value("MANUAL") }
                jsonPath("$.projectId") { doesNotExist() }
            }
    }

    @Test
    fun `invalid personal expense is rejected`() {
        val alice = newUser()

        listOf(
            """{"title":" ","date":"2026-10-03T10:00:00","amount":3000,"currency":"PLN"}""",
            """{"title":"Taxi","amount":3000,"currency":"PLN"}""",
            """{"title":"Taxi","date":"2026-10-03T10:00:00","amount":0,"currency":"PLN"}""",
            """{"title":"Taxi","date":"2026-10-03T10:00:00","amount":3000,"currency":"XYZ"}""",
            """{"title":"Taxi","date":"2026-10-03T10:00:00","amount":3000}""",
        ).forEach { body ->
            mockMvc.post("/personal/expenses") {
                with(asUser(alice))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `users see only their own expenses`() {
        val alice = newUser()
        val bob = newUser()
        createExpense(alice, """{"title":"Older","date":"2026-10-01T10:00:00","amount":100,"currency":"PLN"}""")
        createExpense(alice, """{"title":"Newer","date":"2026-10-02T10:00:00","amount":200,"currency":"PLN"}""")
        createExpense(bob, """{"title":"Bob's","date":"2026-10-02T10:00:00","amount":200,"currency":"PLN"}""")

        mockMvc.get("/personal/expenses") { with(asUser(alice)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[0].title") { value("Newer") }
                jsonPath("$.items[1].title") { value("Older") }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.hasNext") { value(false) }
            }
    }

    @Test
    fun `invalid paging parameters are rejected`() {
        val alice = asUser(newUser())

        mockMvc.get("/personal/expenses") { with(alice); param("page", "-1") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/personal/expenses") { with(alice); param("size", "0") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/personal/expenses") { with(alice); param("size", "101") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list filters are optional and combined`() {
        val alice = newUser()
        createExpense(
            alice,
            """{"title":"Pizza dinner","date":"2026-10-01T19:00:00","amount":1,"currency":"PLN","tag":"Food"}"""
        )
        createExpense(
            alice,
            """{"title":"Pizza lunch","date":"2026-10-03T12:00:00","amount":1,"currency":"PLN","tag":"Food"}"""
        )
        createExpense(
            alice,
            """{"title":"Museum","date":"2026-10-02T10:00:00","amount":1,"currency":"PLN","tag":"Culture"}"""
        )
        createExpense(alice, """{"title":"Taxi 100%","date":"2026-10-02T11:00:00","amount":1,"currency":"PLN"}""")

        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles(alice, "tag" to "food"))
        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles(alice, "title" to "PIZZA"))
        assertEquals(listOf("Taxi 100%"), titles(alice, "title" to "%"))
        assertEquals(
            listOf("Taxi 100%", "Museum"),
            titles(alice, "from" to "2026-10-02T00:00:00", "to" to "2026-10-02T23:59:59"),
        )
        assertEquals(4, titles(alice).size)
    }

    @Test
    fun `tags are reused case-insensitively and scoped to the user`() {
        val alice = newUser()
        val bob = newUser()
        createExpense(alice, """{"title":"A","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN","tag":"Food"}""")
        createExpense(
            alice,
            """{"title":"B","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN","tag":" food "}"""
        )
        createExpense(alice, """{"title":"C","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN","tag":"Bars"}""")

        mockMvc.get("/personal/tags") { with(asUser(alice)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0]") { value("Bars") }
                jsonPath("$[1]") { value("Food") }
            }
        mockMvc.get("/personal/tags") { with(asUser(bob)) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `user updates their expense`() {
        val alice = newUser()
        val expenseId = createExpense(
            alice,
            """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":100,"currency":"PLN","tag":"Food"}"""
        )

        mockMvc.put("/personal/expenses/$expenseId") {
            with(asUser(alice))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Lunch","date":"2026-10-04T12:00:00","amount":250,"currency":"JPY"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Lunch") }
            jsonPath("$.date") { value("2026-10-04T12:00:00") }
            jsonPath("$.amount") { value(250) }
            jsonPath("$.currency.code") { value("JPY") }
            jsonPath("$.currency.minorUnits") { value(0) }
            jsonPath("$.tag") { doesNotExist() }
        }
    }

    @Test
    fun `user deletes their expense`() {
        val alice = newUser()
        val expenseId =
            createExpense(alice, """{"title":"A","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN"}""")

        mockMvc.delete("/personal/expenses/$expenseId") { with(asUser(alice)) }
            .andExpect { status { isNoContent() } }

        assertFalse(expenses.existsById(UUID.fromString(expenseId)))
        mockMvc.get("/personal/expenses/$expenseId") { with(asUser(alice)) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `another user's expense is not found`() {
        val alice = newUser()
        val expenseId =
            createExpense(alice, """{"title":"A","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN"}""")
        val stranger = asUser(newUser())

        mockMvc.get("/personal/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isNotFound() } }
        mockMvc.put("/personal/expenses/$expenseId") {
            with(stranger)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Hacked","date":"2026-10-01T10:00:00","amount":1,"currency":"PLN"}"""
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/personal/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isNotFound() } }

        assertEquals(listOf("A"), titles(alice))
    }
}
