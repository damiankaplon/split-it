package pl.damiankaplon.splitit.balancing.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.UserId
import pl.damiankaplon.splitit.project.ExpenseEvent
import pl.damiankaplon.splitit.project.ExpenseEvent.Split
import pl.damiankaplon.splitit.project.ProjectEvent
import pl.damiankaplon.splitit.project.SettlementEvent
import java.time.Instant
import java.util.UUID

/**
 * Events are delivered at least once, so the balance applies each of them
 * once, however often it gets it: an event the repository already recorded
 * as applied changes nothing.
 */
class BalanceEventHandlerTest {
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val projectId = UUID.randomUUID()
    private val balance = Balance(projectId)

    private val balances = mock(BalanceRepository::class.java)
    private val handler = BalanceEventHandler(balances)

    /** Event ids the repository has recorded as applied, as its unique key does. */
    private val applied = mutableSetOf<UUID>()

    init {
        `when`(balances.findByProjectIdOrThrow(projectId)).thenReturn(balance)
        `when`(balances.recordEventApplied(any(UUID::class.java) ?: UUID.randomUUID(), any(UUID::class.java) ?: projectId))
            .thenAnswer { if (applied.add(it.arguments[0] as UUID)) 1 else 0 }
    }

    /** alice paid [amount], split equally with bob. */
    private fun created(amount: Int) =
        ExpenseEvent.ExpenseCreated(
            projectId = projectId,
            expenseId = UUID.randomUUID(),
            payer = alice,
            data = ExpenseData("Dinner", Instant.parse("2026-10-03T19:30:00Z"), amount, PLN),
            tag = null,
            splits = listOf(Split(alice, amount / 2), Split(bob, amount / 2)),
        )

    @Test
    fun `applies an expense event once, however often it is delivered`() {
        val event = created(40_00)

        repeat(3) { handler.handle(event) }

        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 20_00))
    }

    @Test
    fun `applies every event of the same change`() {
        handler.handle(created(40_00))
        handler.handle(created(40_00))

        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 40_00))
    }

    @Test
    fun `follows an expense through update and deletion`() {
        val created = created(40_00)
        val updated =
            ExpenseEvent.ExpenseUpdated(
                projectId = projectId,
                expenseId = created.expenseId,
                payer = alice,
                data = created.data.copy(amount = 60_00),
                tag = null,
                splits = listOf(Split(alice, 30_00), Split(bob, 30_00)),
                previousAmount = 40_00,
                previousSplits = created.splits,
            )

        handler.handle(created)
        repeat(2) { handler.handle(updated) }

        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 30_00))

        val deleted = ExpenseEvent.ExpenseDeleted(projectId, created.expenseId, alice, 60_00, updated.splits)
        repeat(2) { handler.handle(deleted) }

        assertThat(balance.settlements()).isEmpty()
    }

    @Test
    fun `applies a confirmed settlement once, however often it is delivered`() {
        val confirmed = SettlementEvent.SettlementConfirmed(projectId, debtor = bob, creditor = alice, amount = 5_00)

        handler.handle(created(40_00))
        repeat(2) { handler.handle(confirmed) }

        assertThat(balance.settlements()).containsExactly(Transfer(bob, alice, 15_00))
    }

    @Test
    fun `a new project gets a balance`() {
        handler.handle(ProjectEvent.ProjectCreated(projectId, "alice"))

        verify(balances).createIfMissing(projectId)
    }
}
