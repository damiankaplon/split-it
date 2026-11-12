package pl.damiankaplon.splitit.balancing2

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.settlement.SettlementEvent

/**
 * A confirmed settlement is money the debtor paid the creditor, whatever
 * the balance suggested.
 */
@Component
@Transactional
class BalanceSettlementEventHandler(
    private val balances: BalanceRepository,
) {

    fun handle(event: SettlementEvent) {
        when (event) {
            is SettlementEvent.SettlementConfirmed -> balances.findByProjectIdOrThrow(event.projectId)
                .record(Transfer(from = event.debtor, to = event.creditor, amount = event.amount.toLong()))
        }
    }
}
