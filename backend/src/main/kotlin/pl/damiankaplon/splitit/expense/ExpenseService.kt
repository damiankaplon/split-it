package pl.damiankaplon.splitit.expense

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing2.BalanceExpenseEventHandler
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import pl.damiankaplon.splitit.policy.SplitPolicy
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.Instant
import java.util.*
import pl.damiankaplon.splitit.personal.ProjectExpenseEventHandler as PersonalExpenseEventHandler

/**
 * Coordinates every change to an [Expense] with what depends on it: the
 * project's balance and each member's share of it among their personal
 * expenses. They learn about it from [ExpenseEvent]s, handed over
 * directly and in the same transaction until there is a message broker.
 */
@Service
@Transactional
class ExpenseService(
    private val expenses: ExpenseRepository,
    private val tags: ExpenseTagRepository,
    private val projects: ProjectRepository,
    private val balanceExpenseEventHandler: BalanceExpenseEventHandler,
    private val personalExpenseEventHandler: PersonalExpenseEventHandler,
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
        val data = details.toData(project.currency)
        val expense = Expense(
            projectId = projectId,
            data = data,
            tag = tag,
            createdBy = payer.value,
            splits = project.split(data.amount),
        ).let(expenses::save)

        publish(
            ExpenseEvent.ExpenseCreated(
                projectId = projectId,
                expenseId = expense.id,
                payer = payer,
                data = expense.data,
                tag = tag?.toTag(),
                splits = expense.splits.toEvent(),
            )
        )
        return expense
    }

    fun update(projectId: UUID, expenseId: UUID, details: ExpenseDetails): Expense {
        val project = projects.findByIdOrThrow(projectId)
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        val payer = UserId(expense.createdBy)
        val previousAmount = expense.data.amount
        val previousSplits = expense.splits.toEvent()
        val tag = findOrCreateTag(projectId, details.tag)
        val sharing = expense.splits.mapTo(mutableSetOf()) { it.userId }
        expense.update(details.toData(expense.data.currency), tag, project.split(details.amount, among = sharing))

        publish(
            ExpenseEvent.ExpenseUpdated(
                projectId = projectId,
                expenseId = expense.id,
                payer = payer,
                data = expense.data,
                tag = tag?.toTag(),
                splits = expense.splits.toEvent(),
                previousAmount = previousAmount,
                previousSplits = previousSplits,
            )
        )
        return expense
    }

    fun delete(projectId: UUID, expenseId: UUID) {
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        expenses.delete(expense)

        publish(
            ExpenseEvent.ExpenseDeleted(
                projectId = projectId,
                expenseId = expense.id,
                payer = UserId(expense.createdBy),
                amount = expense.data.amount,
                splits = expense.splits.toEvent(),
            )
        )
    }

    /** Stands in for a message broker: every consumer gets every event. */
    private fun publish(event: ExpenseEvent) {
        balanceExpenseEventHandler.handle(event)
        personalExpenseEventHandler.handle(event)
    }

    private fun ExpenseDetails.toData(currency: Currency) = ExpenseData(title, date, amount, currency)

    /**
     * Divides [amount] with the project's split policy among [among]: by
     * default the project's current members. An updated expense stays shared
     * by the members it already was, so editing it never charges someone
     * who joined later, nor anyone an expense that was the payer's alone.
     */
    private fun Project.split(
        amount: Int,
        among: Set<UserId> = this.members.map(ProjectMember::userId).map(::UserId).toSet(),
    ): Set<ExpenseSplit> {
        val expense = SplitPolicy.Expense(amount, among)
        return splitPolicy.policy.calculate(expense).mapTo(linkedSetOf()) { ExpenseSplit(it.userId, it.amount) }
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

    /** Ordered by user, so events are deterministic. */
    private fun Collection<ExpenseSplit>.toEvent(): List<ExpenseEvent.Split> =
        sortedBy { it.userId.value }.map { ExpenseEvent.Split(it.userId, it.amount) }
}
