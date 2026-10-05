package pl.damiankaplon.splitit.balancing

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.settlement.SettlementEvent

@Component
@Transactional
class SaldoSettlementEventHandler(
    private val saldos: SaldoRepository,
) {

    fun handle(event: SettlementEvent) {
        val saldo = saldos.findByProjectIdOrThrow(event.projectId)
        when (event) {
            is SettlementEvent.SettlementConfirmed -> saldo.handle(event)
        }
    }
}
