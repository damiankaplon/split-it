package pl.damiankaplon.splitit.balancing

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.expense.ExpenseEvent

private val LOGGER = KotlinLogging.logger {}

@Component
@Transactional
class SaldoExpenseEventHandler(
    private val saldos: SaldoRepository,
) {

    fun handle(event: ExpenseEvent) {
        val saldo = saldos.findByProjectIdOrThrow(event.projectId)
        when (event) {
            is ExpenseEvent.ExpensesIncreased -> {
                LOGGER.info { "Handling : $event" }
                saldo.handle(event)
            }

            is ExpenseEvent.ExpensesReduced -> {
                LOGGER.info { "Handling : $event" }
                saldo.handle(event)
            }
        }
    }
}
