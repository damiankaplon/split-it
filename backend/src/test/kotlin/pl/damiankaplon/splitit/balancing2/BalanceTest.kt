package pl.damiankaplon.splitit.balancing2

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import pl.damiankaplon.splitit.UserId
import java.util.*
import kotlin.random.Random

class BalanceTest {

    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val carol = UserId("carol")
    private val dave = UserId("dave")
    private val everyone = listOf(alice, bob, carol, dave)

    private fun balance() = Balance(UUID.randomUUID())

    /** [payer] paid for [shares], whose total is the expense amount. */
    private fun expense(payer: UserId, vararg shares: Pair<UserId, Int>) =
        Expense(payer, shares.sumOf { it.second }, shares.toMap())

    private fun Balance.amounts() = everyone.associateWith { of(it) }

    private fun Balance.withExpenses(vararg expenses: Expense) = apply { expenses.forEach { add(it) } }

    @Test
    fun `empty balance has nothing to settle`() {
        val balance = balance()

        assertThat(balance.settlements()).isEmpty()
        assertThat(balance.amounts().values).containsOnly(0L)
    }

    @Test
    fun `one person pays and shares equally among 3 - the others pay them back`() {
        val balance = balance()

        balance.add(expense(alice, alice to 30_00, bob to 30_00, carol to 30_00))

        assertThat(balance.of(alice)).isEqualTo(60_00)
        assertThat(balance.of(bob)).isEqualTo(-30_00)
        assertThat(balance.of(carol)).isEqualTo(-30_00)
        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 30_00),
            Transfer(carol, alice, 30_00),
        )
    }

    @Test
    fun `custom 50-25-25 split gives exact amounts`() {
        val balance = balance()

        balance.add(expense(alice, alice to 50_00, bob to 25_00, carol to 25_00))

        assertThat(balance.of(alice)).isEqualTo(50_00)
        assertThat(balance.of(bob)).isEqualTo(-25_00)
        assertThat(balance.of(carol)).isEqualTo(-25_00)
        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 25_00),
            Transfer(carol, alice, 25_00),
        )
    }

    @Test
    fun `amounts are exact for shares that do not divide evenly`() {
        val balance = balance()

        balance.add(expense(alice, alice to 33_34, bob to 33_33, carol to 33_33))

        assertThat(balance.amounts().values.sum()).isZero()
        assertThat(balance.of(alice)).isEqualTo(66_66)
        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 33_33),
            Transfer(carol, alice, 33_33),
        )
    }

    @Test
    fun `payer who is not in the shares paid on someone else's behalf`() {
        val balance = balance()

        balance.add(expense(alice, bob to 30_00, carol to 30_00))

        assertThat(balance.of(alice)).isEqualTo(60_00)
        assertThat(balance.of(bob)).isEqualTo(-30_00)
        assertThat(balance.of(carol)).isEqualTo(-30_00)
    }

    @Test
    fun `paying only for yourself changes nothing`() {
        val balance = balance()

        balance.add(expense(alice, alice to 50_00))

        assertThat(balance.amounts().values).containsOnly(0L)
        assertThat(balance.settlements()).isEmpty()
    }

    @Test
    fun `a user with a zero share is not affected`() {
        val balance = balance()

        balance.add(expense(alice, bob to 50_00, carol to 0))

        assertThat(balance.of(carol)).isZero()
        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 50_00))
    }

    @Test
    fun `remove restores the previous state exactly`() {
        val first = expense(alice, alice to 30_00, bob to 30_00, carol to 30_00)
        val second = expense(bob, alice to 10_00, carol to 25_01, dave to 7_00)
        val balance = balance().withExpenses(first)
        val before = balance.amounts()
        val settlementsBefore = balance.settlements()

        balance.add(second)
        balance.remove(second)

        assertThat(balance.amounts()).isEqualTo(before)
        assertThat(balance.settlements()).isEqualTo(settlementsBefore)
    }

    @Test
    fun `removing the only expense leaves nothing to settle`() {
        val expense = expense(alice, alice to 30_00, bob to 30_00, carol to 30_00)
        val balance = balance().withExpenses(expense)

        balance.remove(expense)

        assertThat(balance.amounts().values).containsOnly(0L)
        assertThat(balance.settlements()).isEmpty()
    }

    @Test
    fun `change to a different payer equals add of the new expense`() {
        assertChangeEqualsAddingNew(
            old = expense(alice, alice to 30_00, bob to 30_00),
            new = expense(bob, alice to 30_00, bob to 30_00),
        )
    }

    @Test
    fun `change to a different amount equals add of the new expense`() {
        assertChangeEqualsAddingNew(
            old = expense(alice, alice to 30_00, bob to 30_00),
            new = expense(alice, alice to 45_00, bob to 45_00),
        )
    }

    @Test
    fun `change to different participants equals add of the new expense`() {
        assertChangeEqualsAddingNew(
            old = expense(alice, alice to 30_00, bob to 30_00),
            new = expense(alice, alice to 20_00, carol to 20_00, dave to 20_00),
        )
    }

    @Test
    fun `change to the same expense changes nothing`() {
        val expense = expense(alice, alice to 30_00, bob to 30_00)
        val balance = balance().withExpenses(expense)
        val before = balance.amounts()

        balance.change(expense, expense)

        assertThat(balance.amounts()).isEqualTo(before)
    }

    private fun assertChangeEqualsAddingNew(old: Expense, new: Expense) {
        val other = expense(carol, alice to 5_00, dave to 15_00)
        val changed = balance().withExpenses(other, old)
        val fresh = balance().withExpenses(other, new)

        changed.change(old, new)

        assertThat(changed.amounts()).isEqualTo(fresh.amounts())
        assertThat(changed.settlements()).isEqualTo(fresh.settlements())
    }

    @Test
    fun `a transfer reduces the debt`() {
        val balance = balance().withExpenses(expense(alice, bob to 100_00))

        balance.record(Transfer(bob, alice, 40_00))

        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 60_00))
    }

    @Test
    fun `an exact repayment leaves nothing to settle`() {
        val balance = balance().withExpenses(expense(alice, bob to 100_00))

        balance.record(Transfer(bob, alice, 100_00))

        assertThat(balance.amounts().values).containsOnly(0L)
        assertThat(balance.settlements()).isEmpty()
    }

    @Test
    fun `overpaying flips the direction of the debt`() {
        val balance = balance().withExpenses(expense(alice, bob to 100_00))

        balance.record(Transfer(bob, alice, 150_00))

        assertThat(balance.settlements()).containsExactly(Transfer(alice, bob, 50_00))
    }

    @Test
    fun `a transfer between users with no debt between them is accepted`() {
        val balance = balance().withExpenses(expense(alice, bob to 100_00))

        balance.record(Transfer(carol, dave, 10_00))

        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 100_00),
            Transfer(dave, carol, 10_00),
        )
    }

    @Test
    fun `a user who is no longer in new expenses keeps their amount`() {
        val balance = balance().withExpenses(expense(alice, alice to 50_00, bob to 50_00))

        balance.add(expense(alice, alice to 15_00, carol to 15_00))

        assertThat(balance.of(bob)).isEqualTo(-50_00)
    }

    @Test
    fun `a user with no entry has amount 0`() {
        assertThat(balance().withExpenses(expense(alice, bob to 10_00)).of(dave)).isZero()
    }

    @Test
    fun `biggest debtor pays biggest creditor`() {
        val balance = balance().withExpenses(
            expense(alice, carol to 30_00, dave to 40_00),
            expense(bob, carol to 20_00),
        )
        // alice +70, bob +20, carol -50, dave -40

        assertThat(balance.settlements()).containsExactly(
            Transfer(carol, alice, 50_00),
            Transfer(dave, alice, 20_00),
            Transfer(dave, bob, 20_00),
        )
    }

    @Test
    fun `equal creditors are paid in user id order whatever order they appeared in`() {
        val balance = balance().withExpenses(
            expense(bob, dave to 30_00),
            expense(alice, dave to 30_00),
        )

        assertThat(balance.settlements()).containsExactly(
            Transfer(dave, alice, 30_00),
            Transfer(dave, bob, 30_00),
        )
    }

    @Test
    fun `equal debtors pay in user id order whatever order they appeared in`() {
        val balance = balance().withExpenses(expense(alice, dave to 30_00, carol to 30_00, bob to 30_00))

        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 30_00),
            Transfer(carol, alice, 30_00),
            Transfer(dave, alice, 30_00),
        )
    }

    @Test
    fun `debtors that become equal while settling are told apart by user id`() {
        val balance = balance().withExpenses(
            expense(bob, dave to 30_00),
            expense(alice, dave to 30_00),
            expense(dave, carol to 10_00, bob to 10_00),
        )
        // alice +30, bob +20, carol -10, dave -40

        assertThat(balance.settlements()).containsExactly(
            Transfer(carol, bob, 10_00),
            Transfer(dave, alice, 30_00), // dave is then left owing 10, as much as carol
            Transfer(dave, bob, 10_00),
        )
    }

    @Test
    fun `ties decide who pays whom`() {
        val balance = balance().withExpenses(
            expense(dave, alice to 10_00),
            expense(carol, bob to 10_00),
        )
        // alice -10, bob -10, carol +10, dave +10: both debtors and both creditors are tied. Going by who
        // appeared first would have alice pay dave and bob pay carol.

        assertThat(balance.settlements()).containsExactly(
            Transfer(alice, carol, 10_00),
            Transfer(bob, dave, 10_00),
        )
    }

    @Test
    fun `settlements are ordered by who pays and then by who is paid, not by size`() {
        val balance = balance().withExpenses(expense(alice, dave to 23_00, carol to 8_00, bob to 2_00))

        // dave is the biggest debtor and so pays first when settling, but is last in the result
        assertThat(balance.settlements()).containsExactly(
            Transfer(bob, alice, 2_00),
            Transfer(carol, alice, 8_00),
            Transfer(dave, alice, 23_00),
        )
    }

    @Test
    fun `there are fewer settlements than users with a debt or credit`() {
        val balance = balance().withExpenses(
            expense(alice, alice to 10_00, bob to 20_00, carol to 30_00, dave to 40_00),
            expense(bob, alice to 17_00, dave to 3_00),
        )
        // alice +73, bob 0, carol -30, dave -43

        assertThat(balance.settlements()).containsExactly(
            Transfer(carol, alice, 30_00),
            Transfer(dave, alice, 43_00),
        )
    }

    @Test
    fun `amounts add up to 0 and are settled in few transfers after any sequence of operations`() {
        repeat(300) { seed ->
            val random = Random(seed)
            val balance = balance()
            val live = mutableListOf<Expense>()
            val transfers = mutableListOf<Transfer>()

            repeat(40) {
                when (random.nextInt(if (live.isEmpty()) 2 else 4)) {
                    0 -> randomExpense(random, everyone).also { live += it; balance.add(it) }
                    1 -> randomTransfer(random, everyone).also { transfers += it; balance.record(it) }
                    2 -> live.removeAt(random.nextInt(live.size)).also(balance::remove)
                    else -> {
                        val index = random.nextInt(live.size)
                        val new = randomExpense(random, everyone)
                        balance.change(live[index], new)
                        live[index] = new
                    }
                }
                assertConsistent(balance, live, transfers)
            }

            balance.settlements().forEach(balance::record)
            assertThat(balance.amounts().values).containsOnly(0L)
            assertThat(balance.settlements()).isEmpty()
        }
    }

    /**
     * Compares [balance] with amounts worked out from scratch from what has
     * happened to it.
     */
    private fun assertConsistent(balance: Balance, expenses: List<Expense>, transfers: List<Transfer>) {
        val expected = everyone.associateWith { user ->
            expenses.sumOf { (if (it.payer == user) it.amount.toLong() else 0L) - (it.splits[user] ?: 0).toLong() } +
                    transfers.sumOf { (if (it.from == user) it.amount else 0L) - (if (it.to == user) it.amount else 0L) }
        }
        assertThat(balance.amounts()).isEqualTo(expected)
        assertThat(expected.values.sum()).isZero()

        val settlements = balance.settlements()
        val users = expected.count { it.value != 0L }
        assertThat(settlements.size).isLessThanOrEqualTo(maxOf(users - 1, 0))
        // the stored transfers are keyed by the two users
        assertThat(settlements.map { it.from to it.to }).doesNotHaveDuplicates()
        val settled = expected.toMutableMap()
        settlements.forEach {
            settled[it.from] = settled.getValue(it.from) + it.amount
            settled[it.to] = settled.getValue(it.to) - it.amount
        }
        assertThat(settled.values).containsOnly(0L)
    }

    @Test
    fun `transfer amount must be positive`() {
        assertThatThrownBy { Transfer(alice, bob, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Transfer(alice, bob, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `cannot transfer to oneself`() {
        assertThatThrownBy { Transfer(alice, alice, 10_00) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `expense amount must be positive`() {
        assertThatThrownBy { Expense(alice, 0, mapOf(bob to 0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Expense(alice, -10, mapOf(bob to -10)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `expense must be shared by someone`() {
        assertThatThrownBy { Expense(alice, 10_00, emptyMap()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `shares must not be negative`() {
        assertThatThrownBy { Expense(alice, 10_00, mapOf(bob to 15_00, carol to -5_00)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `shares must add up to the amount`() {
        assertThatThrownBy { Expense(alice, 10_00, mapOf(bob to 5_00, carol to 4_99)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Expense(alice, 10_00, mapOf(bob to 5_00, carol to 5_01)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
