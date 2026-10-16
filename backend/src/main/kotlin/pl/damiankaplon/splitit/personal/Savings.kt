package pl.damiankaplon.splitit.personal

import java.time.Instant
import java.util.*

/**
 * Savings of one currency from one delimiting income until the next one
 * ([end], exclusive; null while still running).
 *
 * All amounts are in the currency's minor units.
 */
data class SavingsPeriod(
    val currency: Currency,
    val start: Instant,
    val end: Instant?,
    val income: Long,
    val expenses: Long,
) {
    val saved: Long get() = income - expenses
}

/** An income or an expense reduced to what savings need. */
data class Flow(val date: Instant, val amount: Int, val currency: Currency)

object Savings {

    /**
     * Per currency, periods run from each delimiting income (included) to the
     * next delimiting one (excluded). A period's income is its delimiting
     * income plus the other incomes inside it; its expenses are all expenses
     * inside it. Anything before the first delimiting income of a currency
     * belongs to no period. Ordered by currency code, then newest first.
     */
    fun periods(
        delimitingIncomes: List<Flow>,
        otherIncomes: List<Flow>,
        expenses: List<Flow>,
    ): List<SavingsPeriod> =
        delimitingIncomes.groupBy { it.currency }.flatMap { (currency, delimiting) ->
            val sorted = delimiting.sortedBy { it.date }
            val others = otherIncomes.filter { it.currency == currency }
            val spent = expenses.filter { it.currency == currency }
            sorted.mapIndexed { i, first ->
                val end = sorted.getOrNull(i + 1)?.date
                val inside = { flow: Flow -> flow.date >= first.date && (end == null || flow.date < end) }
                SavingsPeriod(
                    currency = currency,
                    start = first.date,
                    end = end,
                    income = first.amount + others.filter(inside).sumOf { it.amount.toLong() },
                    expenses = spent.filter(inside).sumOf { it.amount.toLong() },
                )
            }
        }.sortedWith(compareBy<SavingsPeriod> { it.currency.currencyCode }.thenByDescending { it.start })
}
