package pl.damiankaplon.splitit.balancing2

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseService
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.settlement.Settlement
import pl.damiankaplon.splitit.settlement.SettlementRepository
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * What happened in production: a payer's expenses imported straight into
 * the database (version 5 ids) before saldo existed were never shared,
 * but V20 split them among all the members. Flyway has already run the
 * migration on an empty database, so the script is run again here.
 * Everything is rolled back.
 */
class UnshareImportedExpensesMigrationIntegrationTest @Autowired constructor(
    private val balances: BalanceRepository,
    private val expenseService: ExpenseService,
    private val settlements: SettlementRepository,
    private val settlementHandler: BalanceSettlementEventHandler,
    private val jdbc: JdbcTemplate,
    private val em: EntityManager,
) : RollbackIntegrationTest() {

    private val migration = ClassPathResource("db/migration/V22__unshare_imported_expenses.sql")
        .getContentAsString(UTF_8)
    private val date = Instant.parse("2026-10-03T19:30:00Z")
    private val payer = "a-user"
    private val other = "b-user"

    /**
     * Imported with a version 5 id and split equally among both members, as
     * V20 left it.
     */
    private fun importedExpense(project: Project, amount: Int): UUID {
        val id = UUID.nameUUIDFromBytes("$project-$amount".toByteArray()).let {
            // nameUUIDFromBytes makes version 3 ids; set the version to 5 like the imported ones
            UUID(it.mostSignificantBits and 0xF000L.inv() or 0x5000L, it.leastSignificantBits)
        }
        jdbc.update(
            "insert into expense (id, project_id, title, date, amount, created_by, currency) values (?, ?, 'Old', ?, ?, ?, 'PLN')",
            id, project.id, Timestamp.from(date), amount, payer,
        )
        jdbc.update("insert into expense_split (expense_id, user_id, amount) values (?, ?, ?)", id, payer, amount / 2)
        jdbc.update(
            "insert into expense_split (expense_id, user_id, amount) values (?, ?, ?)",
            id,
            other,
            amount - amount / 2
        )
        return id
    }

    private fun saldo(project: Project, vararg members: Triple<String, Long, Long>) {
        jdbc.update("insert into saldo (project_id) values (?)", project.id)
        members.forEach { (user, paid, settled) ->
            jdbc.update(
                "insert into saldo_member (project_id, user_id, total_expenses_amount, settled_amount) values (?, ?, ?, ?)",
                project.id, user, paid, settled,
            )
        }
    }

    private fun splitsOf(expenseId: UUID): Map<String, Int> =
        jdbc.query("select user_id, amount from expense_split where expense_id = ?", { rs, _ ->
            rs.getString(1) to rs.getInt(2)
        }, expenseId).toMap()

    /**
     * A project like the one in production, with its balance as V21 left it:
     * never changed since.
     */
    private fun productionLikeProject(): Pair<Project, UUID> {
        val project = testData.createProject(payer, other)
        em.flush()
        val imported = importedExpense(project, 5000)
        // Shared ones, through the application: an amount that doesn't divide by 2 gets rounded
        expenseService.create(project.id, UserId(payer), ExpenseService.ExpenseDetails("Dinner", date, 1001, null))
        expenseService.create(project.id, UserId(other), ExpenseService.ExpenseDetails("Taxi", date, 200, null))
        val settlement = settlements.save(Settlement(project.id, UserId(other), UserId(payer), 100, date))
        settlementHandler.handle(settlement.confirm(date))
        em.flush()
        jdbc.update("update balance set version = 0 where project_id = ?", project.id)
        return project to imported
    }

    private fun migrate() {
        em.flush()
        jdbc.execute(migration)
        em.clear()
    }

    @Test
    fun `imported expenses become their payer's alone and the balance agrees with saldo again`() {
        val (project, imported) = productionLikeProject()
        // Saldo never counted the imported 50.00: payer 10.01, other 2.00 and 1.00 settled by other
        saldo(project, Triple(payer, 1001, -100), Triple(other, 200, 100))

        migrate()

        assertThat(splitsOf(imported)).isEqualTo(mapOf(payer to 5000))
        val balance = balances.findByProjectIdOrThrow(project.id)
        // Saldo says 300.5 exactly; the 10.01 was split 5.01 for the payer (first by id), 5.00 for the other
        assertThat(balance.of(UserId(payer))).isEqualTo(300)
        assertThat(balance.of(UserId(other))).isEqualTo(-300)
        assertThat(balance.settlements()).containsExactly(Transfer(UserId(other), UserId(payer), 300))
    }

    @Test
    fun `nothing changes when the balance would still differ from saldo`() {
        val (project, _) = productionLikeProject()
        saldo(project, Triple(payer, 1501, -100), Triple(other, 200, 100))

        assertThatThrownBy { migrate() }.hasMessageContaining("Balances differ from saldo in projects: ${project.id}")
    }

    @Test
    fun `a balance changed since V21 is not compared with saldo, which no longer follows the expenses`() {
        val (project, imported) = productionLikeProject()
        saldo(project, Triple(payer, 1501, -100), Triple(other, 200, 100))
        jdbc.update("update balance set version = 1 where project_id = ?", project.id)

        migrate()

        assertThat(splitsOf(imported)).isEqualTo(mapOf(payer to 5000))
        assertThat(balances.findByProjectIdOrThrow(project.id).of(UserId(payer))).isEqualTo(300)
    }

    @Test
    fun `an updated imported expense stays its payer's alone`() {
        val (project, imported) = productionLikeProject()
        saldo(project, Triple(payer, 1001, -100), Triple(other, 200, 100))
        migrate()

        expenseService.update(project.id, imported, ExpenseService.ExpenseDetails("Old", date, 6000, null))
        em.flush()
        em.clear()

        assertThat(splitsOf(imported)).isEqualTo(mapOf(payer to 6000))
        assertThat(balances.findByProjectIdOrThrow(project.id).of(UserId(payer))).isEqualTo(300)
    }
}
