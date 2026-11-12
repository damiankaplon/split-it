package pl.damiankaplon.splitit.expense

import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import java.util.*

/**
 * What happened to a project's expenses. Every consumer is handed every
 * event and ignores the ones it doesn't care about, the way it will once
 * they go through a message broker.
 */
sealed interface ExpenseEvent {
    val projectId: UUID

    /** A member's share of an expense, in minor units. */
    data class Split(
        val userId: UserId,
        val amount: Int,
    )

    /**
     * An expense was added: [payer] paid [ExpenseData.amount] of [data],
     * divided among the members by [splits], which add up to it.
     */
    data class ExpenseCreated(
        override val projectId: UUID,
        val expenseId: UUID,
        val payer: UserId,
        val data: ExpenseData,
        val tag: Tag?,
        val splits: List<Split>,
    ) : ExpenseEvent

    /**
     * An expense was changed. [previousAmount] and [previousSplits] are what
     * it was before, exactly as the last [ExpenseCreated] or [ExpenseUpdated]
     * of it described; the payer never changes.
     */
    data class ExpenseUpdated(
        override val projectId: UUID,
        val expenseId: UUID,
        val payer: UserId,
        val data: ExpenseData,
        val tag: Tag?,
        val splits: List<Split>,
        val previousAmount: Int,
        val previousSplits: List<Split>,
    ) : ExpenseEvent

    /**
     * An expense was deleted. [amount] and [splits] are exactly what the last
     * [ExpenseCreated] or [ExpenseUpdated] of it described.
     */
    data class ExpenseDeleted(
        override val projectId: UUID,
        val expenseId: UUID,
        val payer: UserId,
        val amount: Int,
        val splits: List<Split>,
    ) : ExpenseEvent
}
