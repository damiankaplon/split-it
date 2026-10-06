package pl.damiankaplon.splitit.balancing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseEvent
import pl.damiankaplon.splitit.settlement.SettlementEvent
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

    private fun Saldo.reduced(user: UserId, amount: Int) =
        handle(ExpenseEvent.ExpensesReduced(projectId, user, amount))

    private fun Saldo.settled(debtor: UserId, creditor: UserId, amount: Int) =
        handle(SettlementEvent.SettlementConfirmed(projectId, debtor, creditor, amount))

    @Test
    fun `no debts when only one member`() {
        val saldo = saldo()

        saldo.paid(alice, 1_00)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `two members split 50-50`() {
        val saldo = saldo()

        saldo.paid(alice, 1_00)
        saldo.paid(bob, 0)

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 50))
    }

    @Test
    fun `no debts when everyone paid the same`() {
        val saldo = saldo()

        saldo.paid(alice, 10_00)
        saldo.paid(bob, 10_00)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `debts are minimized instead of everyone paying everyone`() {
        val saldo = saldo()

        saldo.paid(alice, 90_00)
        saldo.paid(bob, 30_00)
        saldo.paid(carol, 0)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(carol, alice, 40_00),
            Transfer(bob, alice, 10_00),
        )
    }

    @Test
    fun `fractional debt is rounded up`() {
        val saldo = saldo()

        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)
        saldo.paid(carol, 0)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(bob, alice, 33_34),
            Transfer(carol, alice, 33_34),
        )
    }

    @Test
    fun `three members paying 2000, 1000 and 1000`() {
        val saldo = saldo()

        saldo.paid(alice, 2000_00)
        saldo.paid(bob, 1000_00)
        saldo.paid(carol, 1000_00)

        // share is 1333.33, so bob and carol each owe alice 333.33 rounded up
        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(bob, alice, 333_34),
            Transfer(carol, alice, 333_34),
        )
    }

    @Test
    fun `reducing expenses recalculates debts`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        saldo.handle(ExpenseEvent.ExpensesReduced(projectId, alice, 60_00))

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 20_00))
    }

    @Test
    fun `reducing expenses to equal amounts clears debts`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 40_00)

        saldo.handle(ExpenseEvent.ExpensesReduced(projectId, alice, 60_00))

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `reducing expenses of unknown member fails`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)

        assertThatThrownBy { saldo.handle(ExpenseEvent.ExpensesReduced(projectId, bob, 10_00)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `debts are recalculated after every new expense`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        saldo.paid(bob, 100_00)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `GIVEN settled project of 2 with saldo 0 WHEN someones expenses are reduced THEN he will get debt`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 50_00)
        saldo.settled(bob, alice, 25_00)
        saldo.reduced(alice, 10_00)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(alice, bob, 5_00),
        )
    }

    @Test
    fun `GIVEN settled project saldo to 0 WHEN someones expenses are reduced THEN he will get debt`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 50_00)
        saldo.paid(carol, 10_00)
        saldo.settled(carol, alice, 43_34)
        saldo.settled(bob, alice, 3_33)
        saldo.reduced(alice, 10_00)

        assertThat(saldo.transfers()).containsExactlyInAnyOrder(
            Transfer(alice, bob, 3_33),
            Transfer(alice, carol, 3_34),
        )
    }

    @Test
    fun `settling the whole debt clears it`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        saldo.settled(bob, alice, 50_00)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `partial settlement reduces the debt`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        saldo.settled(bob, alice, 20_00)

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 30_00))
    }

    @Test
    fun `paying a rounded up debt leaves no sub-unit debt behind`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)
        saldo.paid(carol, 0)

        saldo.settled(bob, alice, 33_34)

        assertThat(saldo.transfers()).containsExactly(Transfer(carol, alice, 33_33))

        saldo.settled(carol, alice, 33_33)

        assertThat(saldo.transfers()).isEmpty()
    }

    @Test
    fun `settlements are kept when expenses change later`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)
        saldo.settled(bob, alice, 50_00)

        saldo.paid(alice, 40_00)

        assertThat(saldo.transfers()).containsExactly(Transfer(bob, alice, 20_00))
    }

    @Test
    fun `cannot settle more than the debt`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        assertThatThrownBy { saldo.settled(bob, alice, 51_00) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `cannot settle a debt that does not exist`() {
        val saldo = saldo()
        saldo.paid(alice, 100_00)
        saldo.paid(bob, 0)

        assertThatThrownBy { saldo.settled(alice, bob, 10_00) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
