package pl.damiankaplon.splitit.balancing2

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.UserId
import java.util.*

class BalanceRepositoryIntegrationTest @Autowired constructor(
    private val balances: BalanceRepository,
    private val em: EntityManager,
) : RollbackIntegrationTest() {

    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val carol = UserId("carol")
    private val dave = UserId("dave")

    /**
     * Saved and flushed, and the persistence context is emptied, so reads come
     * from the database.
     */
    private fun savedBalance(vararg expenses: Expense): UUID {
        val projectId = testData.createProject("owner").id
        balances.findByProjectIdOrThrow(projectId).apply { expenses.forEach { add(it) } }
        em.flush()
        em.clear()
        return projectId
    }

    @Test
    fun `finds the stored transfers ordered by who pays and then by who is paid`() {
        val projectId = savedBalance(
            Expense(alice, 100_00, mapOf(bob to 40_00, dave to 60_00)),
            Expense(carol, 50_00, mapOf(dave to 50_00)),
        )

        assertThat(balances.findTransfersByProjectId(projectId)).containsExactly(
            Transfer(bob, carol, 40_00),
            Transfer(dave, alice, 100_00),
            Transfer(dave, carol, 10_00),
        )
    }

    @Test
    fun `finds exactly what the balance stores`() {
        val projectId = savedBalance(
            Expense(alice, 30_00, mapOf(alice to 10_00, bob to 10_00, carol to 10_00)),
        )

        assertThat(balances.findTransfersByProjectId(projectId))
            .containsExactlyElementsOf(balances.findByProjectIdOrThrow(projectId).settlements())
    }

    @Test
    fun `does not find transfers of other projects`() {
        val projectId = savedBalance(Expense(alice, 20_00, mapOf(bob to 20_00)))
        savedBalance(Expense(carol, 70_00, mapOf(dave to 70_00)))

        assertThat(balances.findTransfersByProjectId(projectId)).containsExactly(Transfer(bob, alice, 20_00))
    }

    @Test
    fun `follows the balance as it changes`() {
        val expense = Expense(alice, 20_00, mapOf(bob to 20_00))
        val projectId = savedBalance(expense)

        balances.findByProjectIdOrThrow(projectId).remove(expense)
        em.flush()
        em.clear()

        assertThat(balances.findTransfersByProjectId(projectId)).isEmpty()
    }

    @Test
    fun `finds nothing for a project without a balance`() {
        assertThat(balances.findTransfersByProjectId(UUID.randomUUID())).isEmpty()
    }
}
