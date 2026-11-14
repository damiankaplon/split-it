package pl.damiankaplon.splitit.balancing.internal

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

class BalanceRepositoryIntegrationTest
    @Autowired
    constructor(
        private val balances: BalanceRepository,
        private val em: EntityManager,
    ) : RollbackIntegrationTest() {
        private val alice = UserId("alice")
        private val bob = UserId("bob")
        private val carol = UserId("carol")
        private val dave = UserId("dave")

        /** An empty balance of a new project, which this module knows only by id. */
        private fun emptyBalance(): UUID = balances.save(Balance(UUID.randomUUID())).projectId

        /**
         * Saved and flushed, and the persistence context is emptied, so reads come
         * from the database.
         */
        private fun savedBalance(vararg expenses: Expense): UUID {
            val projectId = emptyBalance()
            balances.findByProjectIdOrThrow(projectId).apply { expenses.forEach { add(it) } }
            em.flush()
            em.clear()
            return projectId
        }

        private fun transfersOf(projectId: UUID): List<Transfer> =
            em
                .createQuery(
                    "select t from Balance b join b.transfers t where b.projectId = :projectId order by t.from, t.to",
                    Transfer::class.java,
                ).setParameter("projectId", projectId)
                .resultList

        @Test
        fun `finds the stored transfers ordered by who pays and then by who is paid`() {
            val projectId =
                savedBalance(
                    Expense(alice, 100_00, mapOf(bob to 40_00, dave to 60_00)),
                    Expense(carol, 50_00, mapOf(dave to 50_00)),
                )

            assertThat(transfersOf(projectId)).containsExactly(
                Transfer(bob, carol, 40_00),
                Transfer(dave, alice, 100_00),
                Transfer(dave, carol, 10_00),
            )
        }

        @Test
        fun `finds exactly what the balance stores`() {
            val projectId =
                savedBalance(
                    Expense(alice, 30_00, mapOf(alice to 10_00, bob to 10_00, carol to 10_00)),
                )

            assertThat(transfersOf(projectId))
                .containsExactlyElementsOf(balances.findByProjectIdOrThrow(projectId).settlements())
        }

        @Test
        fun `does not find transfers of other projects`() {
            val projectId = savedBalance(Expense(alice, 20_00, mapOf(bob to 20_00)))
            savedBalance(Expense(carol, 70_00, mapOf(dave to 70_00)))

            assertThat(transfersOf(projectId)).containsExactly(Transfer(bob, alice, 20_00))
        }

        @Test
        fun `finds the transfers a user pays or is paid`() {
            val projectId =
                savedBalance(
                    Expense(alice, 100_00, mapOf(bob to 40_00, dave to 60_00)),
                    Expense(carol, 50_00, mapOf(dave to 50_00)),
                )

            assertThat(balances.findTransfersByProjectIdAndUserId(projectId, dave.value)).containsExactly(
                Transfer(dave, alice, 100_00),
                Transfer(dave, carol, 10_00),
            )
            assertThat(balances.findTransfersByProjectIdAndUserId(projectId, carol.value)).containsExactly(
                Transfer(bob, carol, 40_00),
                Transfer(dave, carol, 10_00),
            )
            assertThat(balances.findTransfersByProjectIdAndUserId(projectId, "stranger")).isEmpty()
        }

        @Test
        fun `follows the balance as it changes`() {
            val expense = Expense(alice, 20_00, mapOf(bob to 20_00))
            val projectId = savedBalance(expense)

            balances.findByProjectIdOrThrow(projectId).remove(expense)
            em.flush()
            em.clear()

            assertThat(transfersOf(projectId)).isEmpty()
        }

        @Test
        fun `finds nothing for a project without a balance`() {
            assertThat(transfersOf(UUID.randomUUID())).isEmpty()
        }

        @Test
        fun `records an event as applied only once`() {
            val projectId = emptyBalance()
            val eventId = UUID.randomUUID()

            assertThat(balances.recordEventApplied(eventId, projectId)).isEqualTo(1)
            assertThat(balances.recordEventApplied(eventId, projectId)).isEqualTo(0)
            assertThat(balances.recordEventApplied(UUID.randomUUID(), projectId)).isEqualTo(1)
        }

        @Test
        fun `creates a missing balance and leaves an existing one alone`() {
            val projectId = savedBalance(Expense(alice, 20_00, mapOf(bob to 20_00)))
            balances.createIfMissing(projectId)
            em.clear()

            assertThat(transfersOf(projectId)).containsExactly(Transfer(bob, alice, 20_00))
        }
    }
