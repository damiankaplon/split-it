package pl.damiankaplon.splitit.personal

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.RollbackIntegrationTest
import java.time.LocalDate
import java.util.*

class SavingsEndpointIntegrationTest : RollbackIntegrationTest() {

    private fun newUser() = "user-${UUID.randomUUID()}"

    private fun post(userId: String, path: String, body: String) =
        mockMvc.post(path) {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }

    private fun income(userId: String, date: LocalDate, delimiting: Boolean, amount: Int, currency: String) =
        post(
            userId,
            "/personal/incomes",
            """{"title":"Salary","date":"${date}T08:00:00Z","delimiting":$delimiting,"amount":$amount,"currency":"$currency"}"""
        )

    private fun expense(userId: String, date: LocalDate, amount: Int, currency: String) =
        post(
            userId,
            "/personal/expenses",
            """{"title":"Spend","date":"${date}T12:00:00Z","amount":$amount,"currency":"$currency"}"""
        )

    @Test
    fun `savings are grouped by currency and delimiting incomes`() {
        val alice = newUser()
        val today = LocalDate.now()
        income(alice, today.minusDays(60), true, 10000, "EUR")
        income(alice, today.minusDays(50), true, 10000, "PLN")
        income(alice, today.minusDays(20), true, 20000, "PLN")
        expense(alice, today.minusDays(55), 1000, "EUR")
        expense(alice, today.minusDays(40), 2500, "PLN")
        expense(alice, today.minusDays(10), 500, "PLN")

        mockMvc.get("/personal/savings") { with(asUser(alice)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].currency.code") { value("EUR") }
                jsonPath("$[0].saved") { value(9000) }
                jsonPath("$[1].currency.code") { value("PLN") }
                jsonPath("$[1].saved") { value(19500) }
                jsonPath("$[1].end") { doesNotExist() }
                jsonPath("$[2].saved") { value(7500) }
            }
    }

    @Test
    fun `periods older than the requested range are left out`() {
        val alice = newUser()
        val today = LocalDate.now()
        income(alice, today.minusYears(3), true, 10000, "PLN")
        income(alice, today.minusYears(2), true, 10000, "PLN")

        mockMvc.get("/personal/savings") { with(asUser(alice)) }
            .andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].start") { exists() }
            }
        mockMvc.get("/personal/savings") {
            with(asUser(alice))
            param("from", "${today.minusYears(4)}T00:00:00Z")
        }.andExpect { jsonPath("$.length()") { value(2) } }
    }
}
