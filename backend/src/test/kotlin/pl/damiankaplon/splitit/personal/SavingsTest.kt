package pl.damiankaplon.splitit.personal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class SavingsTest {

    private val eur = Currency.getInstance("EUR")
    private val pln = Currency.getInstance("PLN")

    private fun flow(date: String, amount: Int, currency: Currency) =
        Flow(Instant.parse("2026-${date}T00:00:00Z"), amount, currency)

    @Test
    fun `periods are separate per currency`() {
        val periods = Savings.periods(
            delimitingIncomes = listOf(flow("01-10", 100, eur), flow("01-15", 100, pln)),
            otherIncomes = emptyList(),
            expenses = listOf(
                flow("01-09", 5, eur),
                flow("01-10", 30, eur),
                flow("01-20", 40, pln),
                flow("01-20", 7, eur)
            ),
        )

        assertEquals(listOf("EUR" to 63L, "PLN" to 60L), periods.map { it.currency.currencyCode to it.saved })
    }

    @Test
    fun `next delimiting income closes the period and is excluded from it`() {
        val periods = Savings.periods(
            delimitingIncomes = listOf(flow("01-15", 100, pln), flow("02-10", 100, pln)),
            otherIncomes = emptyList(),
            expenses = listOf(flow("01-15", 10, pln), flow("02-09", 20, pln), flow("02-10", 5, pln)),
        )

        assertEquals(listOf(95L, 70L), periods.map { it.saved })
        assertEquals(Instant.parse("2026-02-10T00:00:00Z"), periods.last().end)
        assertEquals(null, periods.first().end)
    }

    @Test
    fun `non delimiting income adds to the period it falls into`() {
        val periods = Savings.periods(
            delimitingIncomes = listOf(flow("01-01", 100, pln), flow("02-01", 100, pln)),
            otherIncomes = listOf(flow("01-20", 50, pln), flow("01-20", 9, eur), flow("02-05", 25, pln)),
            expenses = emptyList(),
        )

        assertEquals(listOf(125L, 150L), periods.map { it.income })
    }

    @Test
    fun `without a delimiting income there are no savings`() {
        assertEquals(
            emptyList<SavingsPeriod>(),
            Savings.periods(emptyList(), listOf(flow("01-01", 1, pln)), listOf(flow("01-01", 1, pln))),
        )
    }
}
