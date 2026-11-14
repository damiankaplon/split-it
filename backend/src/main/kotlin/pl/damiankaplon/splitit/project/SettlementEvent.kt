package pl.damiankaplon.splitit.project

import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

/**
 * What happened to a project's settlements. Delivered at least once, see
 * [ExpenseEvent].
 */
sealed interface SettlementEvent {
    val eventId: UUID
    val projectId: UUID

    /**
     * The creditor confirmed receiving [amount] from the debtor. The debtor
     * paid (part of) their debt directly to the creditor, who confirmed it.
     * Partial settlements are allowed; the rest of the debt stays open.
     */
    data class SettlementConfirmed(
        override val projectId: UUID,
        val debtor: UserId,
        val creditor: UserId,
        val amount: Int,
        override val eventId: UUID = UUID.randomUUID(),
    ) : SettlementEvent
}
