package pl.damiankaplon.splitit.expense

import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import java.util.*

/**
 * What changed about an [Expense], for its members' personal expenses to
 * follow.
 */
sealed interface ProjectExpenseEvent {
    val expenseId: UUID
    val projectId: UUID
    val data: ExpenseData
}

/**
 * A member's share of the expense, emitted for every member each time the
 * expense is created or updated.
 *
 * @param amount - the member's share in minor units; [data] holds the
 *    whole expense's amount
 */
data class ProjectExpenseSplitCalculated(
    override val expenseId: UUID,
    override val projectId: UUID,
    override val data: ExpenseData,
    val tag: Tag?,
    val userId: UserId,
    val amount: Int,
) : ProjectExpenseEvent

data class ProjectExpenseDeleted(
    override val expenseId: UUID,
    override val projectId: UUID,
    override val data: ExpenseData,
) : ProjectExpenseEvent
