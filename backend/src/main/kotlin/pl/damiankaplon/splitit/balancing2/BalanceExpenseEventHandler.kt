package pl.damiankaplon.splitit.balancing2

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseEvent

private val LOGGER = KotlinLogging.logger {}

/** Keeps the project's [Balance] in line with its expenses. */
@Component
@Transactional
class BalanceExpenseEventHandler(
    private val balances: BalanceRepository,
) {

    fun handle(event: ExpenseEvent) {
        when (event) {
            is ExpenseEvent.ExpenseCreated -> handle(event)
            is ExpenseEvent.ExpenseUpdated -> handle(event)
            is ExpenseEvent.ExpenseDeleted -> handle(event)
        }
    }

    private fun handle(event: ExpenseEvent.ExpenseCreated) {
        LOGGER.info { "Handling : $event" }
        balances.findByProjectIdOrThrow(event.projectId).add(expenseOf(event.payer, event.data.amount, event.splits))
    }

    private fun handle(event: ExpenseEvent.ExpenseUpdated) {
        LOGGER.info { "Handling : $event" }
        balances.findByProjectIdOrThrow(event.projectId).change(
            old = expenseOf(event.payer, event.previousAmount, event.previousSplits),
            new = expenseOf(event.payer, event.data.amount, event.splits),
        )
    }

    private fun handle(event: ExpenseEvent.ExpenseDeleted) {
        LOGGER.info { "Handling : $event" }
        balances.findByProjectIdOrThrow(event.projectId).remove(expenseOf(event.payer, event.amount, event.splits))
    }

    private fun expenseOf(payer: UserId, amount: Int, splits: List<ExpenseEvent.Split>) =
        Expense(payer, amount, splits.associate { it.userId to it.amount })
}
