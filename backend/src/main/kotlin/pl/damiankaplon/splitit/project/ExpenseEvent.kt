package pl.damiankaplon.splitit.project

import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

/**
 * What happened to a project's expenses. Published to other modules, which
 * get them after the change is committed and possibly more than once: an
 * event a listener didn't complete is republished on restart. [eventId]
 * stays the same across those deliveries.
 */
sealed interface ExpenseEvent {
    val eventId: UUID
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
        override val eventId: UUID = UUID.randomUUID(),
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
        override val eventId: UUID = UUID.randomUUID(),
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
        override val eventId: UUID = UUID.randomUUID(),
    ) : ExpenseEvent
}
