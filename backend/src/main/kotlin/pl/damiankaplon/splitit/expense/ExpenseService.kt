package pl.damiankaplon.splitit.expense

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing.SaldoExpenseEventHandler
import pl.damiankaplon.splitit.expense.ExpenseEvent.ExpensesIncreased
import pl.damiankaplon.splitit.expense.ExpenseEvent.ExpensesReduced
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import pl.damiankaplon.splitit.personal.ProjectToPersonalExpenseSynchronization
import pl.damiankaplon.splitit.policy.SplitPolicy
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
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
    private val saldoExpenseEventHandler: SaldoExpenseEventHandler,
    private val personalExpenseSynchronization: ProjectToPersonalExpenseSynchronization,
) {

    data class ExpenseDetails(
        val title: String,
        val date: Instant,
        val amount: Int,
        val tag: String?,
    )

    fun create(projectId: UUID, payer: UserId, details: ExpenseDetails): Expense {
        val project = projects.findByIdOrThrow(projectId)
        val tag = findOrCreateTag(projectId, details.tag)
        val expense = Expense(
            projectId = projectId,
            data = details.toData(project.currency),
            tag = tag,
            createdBy = payer.value,
        ).let(expenses::save)

        ExpensesIncreased(projectId, payer, expense.data.amount)
            .run(saldoExpenseEventHandler::handle)

        synchronizePersonalExpenses(expense, tag, project.split(expense.data.amount))
        return expense
    }

    fun update(projectId: UUID, expenseId: UUID, details: ExpenseDetails): Expense {
        val project = projects.findByIdOrThrow(projectId)
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        val payer = UserId(expense.createdBy)
        val amountDiff = details.amount - expense.data.amount
        val tag = findOrCreateTag(projectId, details.tag)
        expense.update(details.toData(expense.data.currency), tag)

        val event = if (amountDiff > 0) ExpensesIncreased(projectId, payer, amountDiff)
        else if (amountDiff < 0) ExpensesReduced(projectId, payer, amountDiff.absoluteValue)
        else null
        event?.run(saldoExpenseEventHandler::handle)

        synchronizePersonalExpenses(expense, tag, project.split(expense.data.amount))
        return expense
    }

    fun delete(projectId: UUID, expenseId: UUID) {
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        expenses.delete(expense)

        ExpensesReduced(projectId, UserId(expense.createdBy), expense.data.amount)
            .run(saldoExpenseEventHandler::handle)

        personalExpenseSynchronization.handle(ProjectExpenseDeleted(expense.id, projectId, expense.data))
    }

    /**
     * Hands each member's share of the expense over to their personal
     * expenses.
     */
    private fun synchronizePersonalExpenses(
        expense: Expense,
        tag: ExpenseTag?,
        splits: Set<SplitPolicy.Split>,
    ) {
        splits.forEach {
            personalExpenseSynchronization.handle(
                ProjectExpenseSplitCalculated(
                    expense.id,
                    expense.projectId,
                    expense.data,
                    tag?.toTag(),
                    it.userId,
                    it.amount
                )
            )
        }
    }

    private fun ExpenseDetails.toData(currency: Currency) = ExpenseData(title, date, amount, currency)

    /**
     * Divides [amount] among the project's current members with its split
     * policy.
     */
    private fun Project.split(amount: Int): Set<SplitPolicy.Split> {
        val users = this.members.map(ProjectMember::userId).map(::UserId).toSet()
        val expense = SplitPolicy.Expense(amount, users)
        return splitPolicy.policy.calculate(expense)
    }

    /**
     * Reuses an existing project tag (case-insensitive) or creates a new one;
     * a blank name means no tag.
     */
    private fun findOrCreateTag(projectId: UUID, name: String?): ExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findByIdProjectIdAndIdNameIgnoreCase(projectId, trimmed)
            ?: tags.save(ExpenseTag(projectId, trimmed))
    }

    private fun ExpenseTag.toTag() = Tag(this.name, this.color)
}
