package pl.damiankaplon.splitit.balancing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseEvent
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectRepository

@SpringBootTest
@Import(PostgreTestContainerConfig::class)
class SaldoPersistenceIntegrationTest @Autowired constructor(
    private val saldos: SaldoRepository,
    private val projects: ProjectRepository,
    private val tx: TransactionTemplate,
) {
    private val alice = UserId("alice")
    private val bob = UserId("bob")

    private fun newSaldo() = saldos.save(Saldo(projects.save(Project("owner-id", "Trip", PLN)).id))

    private fun update(projectId: java.util.UUID, event: ExpenseEvent) = tx.executeWithoutResult {
        val saldo = saldos.findByProjectIdOrThrow(projectId)
        when (event) {
            is ExpenseEvent.ExpensesIncreased -> saldo.handle(event)
            is ExpenseEvent.ExpensesReduced -> saldo.handle(event)
        }
    }

    @Test
    fun `members and debts survive a round trip and are updated in place`() {
        val projectId = newSaldo().projectId

        update(projectId, ExpenseEvent.ExpensesIncreased(projectId, alice, 100))
        update(projectId, ExpenseEvent.ExpensesIncreased(projectId, bob, 0))
        assertThat(debtsOf(projectId)).containsExactly(Triple(bob, alice, 50))

        update(projectId, ExpenseEvent.ExpensesIncreased(projectId, alice, 100))
        assertThat(debtsOf(projectId)).containsExactly(Triple(bob, alice, 100))

        update(projectId, ExpenseEvent.ExpensesReduced(projectId, alice, 200))
        assertThat(debtsOf(projectId)).isEmpty()
        assertThat(tx.execute { saldos.findByProjectIdOrThrow(projectId).members.size }).isEqualTo(2)
    }

    @Test
    fun `concurrent modification of the same saldo fails with optimistic lock`() {
        val projectId = newSaldo().projectId
        val stale = tx.execute { saldos.findByProjectIdOrThrow(projectId) }

        update(projectId, ExpenseEvent.ExpensesIncreased(projectId, alice, 100))

        assertThatThrownBy {
            tx.executeWithoutResult {
                val merged = saldos.save(stale)
                merged.handle(ExpenseEvent.ExpensesIncreased(projectId, bob, 10))
            }
        }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
    }

    private fun debtsOf(projectId: java.util.UUID) = tx.execute {
        saldos.findByProjectIdOrThrow(projectId).debts.map { Triple(it.debtor, it.creditor, it.amount) }
    }
}
