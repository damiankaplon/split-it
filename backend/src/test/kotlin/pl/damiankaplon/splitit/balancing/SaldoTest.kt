package pl.damiankaplon.splitit.balancing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseEvent
import java.util.*

class SaldoTest {

    private val projectId = UUID.randomUUID()
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val carol = UserId("carol")

    private fun saldo() = Saldo(projectId)

    private data class Transfer(val debtor: UserId, val creditor: UserId, val amount: Int)

    private fun Saldo.transfers() = debts.map { Transfer(it.debtor, it.creditor, it.amount) }

    private fun Saldo.paid(user: UserId, amount: Int) =
        handle(ExpenseEvent.ExpensesIncreased(projectId, user, amount))

    @Test
    fun `no debts when only one member`() {
        val saldo = saldo()

        saldo.paid(alice, 100)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `two members split 50-50`() {
        val saldo = saldo()

        saldo.paid(alice, 100)
        saldo.paid(bob, 0)

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 50))
    }

    @Test
    fun `no debts when everyone paid the same`() {
        val saldo = saldo()

        saldo.paid(alice, 50)
        saldo.paid(bob, 50)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `debts are minimized instead of everyone paying everyone`() {
        val saldo = saldo()

        saldo.paid(alice, 90)
        saldo.paid(bob, 30)
        saldo.paid(carol, 0)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(carol, alice, 40),
            Transfer(bob, alice, 10),
        )
    }

    @Test
    fun `fractional debt is rounded up`() {
        val saldo = saldo()

        saldo.paid(alice, 100)
        saldo.paid(bob, 0)
        saldo.paid(carol, 0)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(bob, alice, 34),
            Transfer(carol, alice, 34),
        )
    }

    @Test
    fun `three members paying 2000, 1000 and 1000`() {
        val saldo = saldo()

        saldo.paid(alice, 2000)
        saldo.paid(bob, 1000)
        saldo.paid(carol, 1000)

        // share is 1333.33, so bob and carol each owe alice 333.33 rounded up
        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(bob, alice, 334),
            Transfer(carol, alice, 334),
        )
    }

    @Test
    fun `reducing expenses recalculates debts`() {
        val saldo = saldo()
        saldo.paid(alice, 100)
        saldo.paid(bob, 0)

        saldo.handle(ExpenseEvent.ExpensesReduced(projectId, alice, 60))

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 20))
    }

    @Test
    fun `reducing expenses to equal amounts clears debts`() {
        val saldo = saldo()
        saldo.paid(alice, 100)
        saldo.paid(bob, 40)

        saldo.handle(ExpenseEvent.ExpensesReduced(projectId, alice, 60))

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `reducing expenses of unknown member fails`() {
        val saldo = saldo()
        saldo.paid(alice, 100)

        assertThatThrownBy { saldo.handle(ExpenseEvent.ExpensesReduced(projectId, bob, 10)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `debts are recalculated after every new expense`() {
        val saldo = saldo()
        saldo.paid(alice, 100)
        saldo.paid(bob, 0)

        saldo.paid(bob, 100)

        assertThat(saldo.transfers()).isEmpty()
    }
}
