package pl.damiankaplon.splitit.expense

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing.SaldoExpenseEventHandler
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.personal.ProjectToPersonalExpenseSynchronization
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.Instant

/**
 * The balances learn only by how much an update changed the expense's
 * amount.
 */
class ExpenseServiceTest {

    private val alice = UserId("alice")
    private val project = Project("Trip to Rome", ProjectMember(alice.value, "alice"), PLN)
    private val date = Instant.parse("2026-10-03T19:30:00Z")

    private val expenses = mock(ExpenseRepository::class.java)
    private val projects = mock(ProjectRepository::class.java)
    private val saldo = mock(SaldoExpenseEventHandler::class.java)

    private val service = ExpenseService(
        expenses = expenses,
        tags = mock(ExpenseTagRepository::class.java),
        projects = projects,
        saldoExpenseEventHandler = saldo,
        personalExpenseSynchronization = mock(ProjectToPersonalExpenseSynchronization::class.java),
    )

    /**
     * Alice's expense of [amount] in the project, as the repository returns
     * it.
     */
    private fun existingExpense(amount: Int): Expense {
        val expense = Expense(project.id, ExpenseData("Dinner", date, amount, PLN), null, alice.value)
        `when`(projects.findByIdOrThrow(project.id)).thenReturn(project)
        `when`(expenses.findByIdAndProjectIdOrThrow(expense.id, project.id)).thenReturn(expense)
        return expense
    }

    private fun updateAmount(expense: Expense, amount: Int) =
        service.update(project.id, expense.id, ExpenseService.ExpenseDetails("Dinner", date, amount, null))

    @Test
    fun `raising the amount increases the payer's expenses by the difference`() {
        val expense = existingExpense(1000)

        updateAmount(expense, 3000)

        verify(saldo).handle(ExpenseEvent.ExpensesIncreased(project.id, alice, 2000))
    }

    @Test
    fun `lowering the amount reduces the payer's expenses by the difference`() {
        val expense = existingExpense(1000)

        updateAmount(expense, 400)

        verify(saldo).handle(ExpenseEvent.ExpensesReduced(project.id, alice, 600))
    }

    @Test
    fun `keeping the amount leaves the balances alone`() {
        val expense = existingExpense(1000)

        updateAmount(expense, 1000)

        verifyNoInteractions(saldo)
    }
}
