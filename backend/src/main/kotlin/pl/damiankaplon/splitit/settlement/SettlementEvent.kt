package pl.damiankaplon.splitit.settlement

import pl.damiankaplon.splitit.UserId
import java.util.*

sealed interface SettlementEvent {
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
    ) : SettlementEvent
}
