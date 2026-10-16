package pl.damiankaplon.splitit.expense

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing.SaldoExpenseEventHandler
import pl.damiankaplon.splitit.personal.ProjectPersonalExpense
import pl.damiankaplon.splitit.personal.ProjectPersonalExpenseRepository
import pl.damiankaplon.splitit.policy.SplitPolicy
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.Instant
import java.util.*
import kotlin.math.absoluteValue

/**
 * Coordinates every change to an [Expense] with what depends on it: the
 * project's balances and each member's share of it among their personal
 * expenses.
 */
@Service
@Transactional
class ExpenseService(
    private val expenses: ExpenseRepository,
    private val tags: ExpenseTagRepository,
    private val projects: ProjectRepository,
    private val shares: ProjectPersonalExpenseRepository,
    private val saldoExpenseEventHandler: SaldoExpenseEventHandler,
) {

    data class ExpenseDetails(
        val title: String,
        val date: Instant,
        val amount: Int,
        val tag: String?,
    )

    fun create(projectId: UUID, payer: UserId, details: ExpenseDetails): Expense {
        val expense = Expense(
            projectId = projectId,
            title = details.title,
            date = details.date,
            amount = details.amount,
            tag = findOrCreateTag(projectId, details.tag),
            createdBy = payer.value,
        ).let(expenses::save)
        saldoExpenseEventHandler.handle(ExpenseEvent.ExpensesIncreased(projectId, payer, expense.amount))
        propagatePersonalExpenses(projectId, expense)
        return expense
    }

    fun update(projectId: UUID, expenseId: UUID, details: ExpenseDetails): Expense {
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        val payer = UserId(expense.createdBy)
        val amountDiff = details.amount - expense.amount
        val event = if (amountDiff > 0) ExpenseEvent.ExpensesIncreased(projectId, payer, amountDiff)
        else if (amountDiff < 0) ExpenseEvent.ExpensesReduced(projectId, payer, amountDiff.absoluteValue)
        else null
        expense.title = details.title
        expense.date = details.date
        expense.amount = details.amount
        expense.tag = findOrCreateTag(projectId, details.tag)
        event?.run(saldoExpenseEventHandler::handle)
        propagatePersonalExpenses(projectId, expense)
        return expense
    }

    fun delete(projectId: UUID, expenseId: UUID) {
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        shares.deleteAll(shares.findByExpense(expense))
        expenses.delete(expense)
        saldoExpenseEventHandler.handle(
            ExpenseEvent.ExpensesReduced(projectId, UserId(expense.createdBy), expense.amount)
        )
    }

    /**
     * Divides the expense with the project's split policy among its current
     * members: existing shares get the new amounts, missing ones are created
     * and those of anyone left out of the split are removed.
     */
    private fun propagatePersonalExpenses(projectId: UUID, expense: Expense) {
        val project = projects.findByIdOrThrow(projectId)
        val members = project.members.mapTo(linkedSetOf()) { UserId(it.userId) }
        val splits = project.splitPolicy.policy.calculate(SplitPolicy.Expense(expense.amount, members))
            .associate { it.userId to it.amount }
        val existing = shares.findByExpense(expense).associateBy { it.ownerId }
        splits.forEach { (userId, amount) ->
            existing[userId]?.let { it.amount = amount }
                ?: shares.save(ProjectPersonalExpense(userId, expense, amount, project.currency))
        }
        shares.deleteAll(existing.filterKeys { it !in splits }.values)
    }

    /**
     * Reuses an existing project tag (case-insensitive) or creates a new one;
     * a blank name means no tag.
     */
    private fun findOrCreateTag(projectId: UUID, name: String?): ExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findByProjectIdAndNameIgnoreCase(projectId, trimmed)
            ?: tags.save(ExpenseTag(projectId, trimmed))
    }
}
