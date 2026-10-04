package pl.damiankaplon.splitit.expense

import pl.damiankaplon.splitit.UserId
import java.util.*

sealed interface ExpenseEvent {
    val projectId: UUID

    data class ExpensesReduced(
        override val projectId: UUID,
        val userId: UserId,
        val reducedBy: Int,
    ) : ExpenseEvent

    data class ExpensesIncreased(
        override val projectId: UUID,
        val userId: UserId,
        val increasedBy: Int,
    ) : ExpenseEvent
}