package pl.damiankaplon.splitit.balancing

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.expense.ExpenseEvent

@Component
@Transactional
class SaldoExpenseEventHandler(
    private val saldos: SaldoRepository,
) {

    fun handle(event: ExpenseEvent) {
        val saldo = saldos.findByProjectIdOrThrow(event.projectId)
        when (event) {
            is ExpenseEvent.ExpensesIncreased -> saldo.handle(event)
            is ExpenseEvent.ExpensesReduced -> saldo.handle(event)
        }
    }
}
