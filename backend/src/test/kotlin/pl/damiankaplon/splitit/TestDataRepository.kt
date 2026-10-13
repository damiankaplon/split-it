package pl.damiankaplon.splitit

import org.springframework.boot.test.context.TestComponent
import pl.damiankaplon.splitit.balancing.Saldo
import pl.damiankaplon.splitit.balancing.SaldoRepository
import pl.damiankaplon.splitit.expense.ExpenseService
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.LocalDateTime
import java.util.*

/**
 * Test-only repository for prepopulating the database: every state a
 * test starts from is inserted here rather than through the API or the
 * production repositories. It leaves the data as the application would,
 * e.g. an expense also moves the balances and the members' personal
 * expenses.
 */
@TestComponent
class TestDataRepository(
    private val projects: ProjectRepository,
    private val saldos: SaldoRepository,
    private val expenseService: ExpenseService,
) {

    fun createProject(
        ownerId: String,
        vararg memberIds: String,
        name: String = "Trip to Rome",
        ownerName: String = ownerId,
    ): Project {
        val project = Project(name, ProjectMember(ownerId, ownerName), PLN)
        memberIds.forEach { project.add(ProjectMember(it, it)) }
        projects.save(project)
        saldos.save(Saldo(project.id))
        return project
    }

    fun addMember(project: Project, userId: String, name: String = userId): Project =
        projects.save(project.apply { add(ProjectMember(userId, name)) })

    /**
     * [date] is an ISO local date-time, e.g. `2026-10-03T19:30:00`. Returns
     * the expense id.
     */
    fun createExpense(
        project: Project,
        userId: String,
        title: String = "Dinner",
        date: String = "2026-10-03T19:30:00",
        amount: Int = 1000,
        tag: String? = null,
    ): UUID = expenseService.create(
        project.id,
        UserId(userId),
        ExpenseService.ExpenseDetails(title, LocalDateTime.parse(date), amount, tag),
    ).id
}
