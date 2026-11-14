package pl.damiankaplon.splitit.balancing.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.kernel.UserId
import pl.damiankaplon.splitit.project.ExpenseEvent
import pl.damiankaplon.splitit.project.ProjectEvent
import pl.damiankaplon.splitit.project.SettlementEvent
import java.util.UUID

private val LOGGER = KotlinLogging.logger {}

@Component
@Transactional
class BalanceEventHandler(
    private val balances: BalanceRepository,
) {
    /**
     * Every project gets an empty [Balance] when it is created, before its
     * first expense or settlement. A member who joins needs nothing: they
     * have no entry until an expense involves them. Creating it twice
     * creates it once.
     */
    @ApplicationModuleListener
    fun handle(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.ProjectCreated -> balances.createIfMissing(event.projectId)
            is ProjectEvent.MemberJoined -> Unit
        }
    }

    @ApplicationModuleListener
    fun handle(event: ExpenseEvent) {
        val balance = balances.findByProjectIdOrThrow(event.projectId)
        balance.applyOnce(event.eventId) {
            when (event) {
                is ExpenseEvent.ExpenseCreated -> {
                    this.add(expenseOf(event.payer, event.data.amount, event.splits))
                }

                is ExpenseEvent.ExpenseUpdated -> {
                    this.change(
                        old = expenseOf(event.payer, event.previousAmount, event.previousSplits),
                        new = expenseOf(event.payer, event.data.amount, event.splits),
                    )
                }

                is ExpenseEvent.ExpenseDeleted -> {
                    this.remove(expenseOf(event.payer, event.amount, event.splits))
                }
            }
        }
    }

    @ApplicationModuleListener
    fun handle(event: SettlementEvent) {
        val balance = balances.findByProjectIdOrThrow(event.projectId)
        balance.applyOnce(event.eventId) {
            when (event) {
                is SettlementEvent.SettlementConfirmed -> {
                    record(Transfer(from = event.debtor, to = event.creditor, amount = event.amount.toLong()))
                }
            }
        }
    }

    private fun Balance.applyOnce(
        eventId: UUID,
        apply: Balance.() -> Unit,
    ) {
        val isAppliedAlready = balances.recordEventApplied(eventId, this.projectId) == 0
        if (isAppliedAlready) {
            LOGGER.info { "Not applying $eventId to ${this.projectId} balance! It has been applied already." }
            return
        }
        this.apply()
    }

    private fun expenseOf(
        payer: UserId,
        amount: Int,
        splits: List<ExpenseEvent.Split>,
    ) = Expense(payer, amount, splits.associate { it.userId to it.amount })
}
