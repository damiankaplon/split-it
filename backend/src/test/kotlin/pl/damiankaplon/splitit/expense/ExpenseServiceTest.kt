package pl.damiankaplon.splitit.expense

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing2.BalanceExpenseEventHandler
import pl.damiankaplon.splitit.expense.ExpenseEvent.Split
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.personal.ProjectExpenseEventHandler
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectRepository
import java.time.Instant

/**
 * The balance and personal expenses learn how an expense was split, before
 * and after every change.
 */
class ExpenseServiceTest {

    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val carol = UserId("carol")
    private val project = Project("Trip to Rome", ProjectMember(alice.value, "alice"), PLN)
    private val date = Instant.parse("2026-10-03T19:30:00Z")

    private val expenses = mock(ExpenseRepository::class.java)
    private val projects = mock(ProjectRepository::class.java)
    private val balance = mock(BalanceExpenseEventHandler::class.java)
    private val personal = mock(ProjectExpenseEventHandler::class.java)

    private val service = ExpenseService(
        expenses = expenses,
        tags = mock(ExpenseTagRepository::class.java),
        projects = projects,
        balanceExpenseEventHandler = balance,
        personalExpenseEventHandler = personal,
    )

    init {
        `when`(projects.findByIdOrThrow(project.id)).thenReturn(project)
        `when`(expenses.save(any(Expense::class.java))).thenAnswer { it.arguments[0] }
    }

    private fun data(amount: Int) = ExpenseData("Dinner", date, amount, PLN)

    private fun details(amount: Int) = ExpenseService.ExpenseDetails("Dinner", date, amount, null)

    /**
     * Alice's expense of [amount], split by [splits], as the repository
     * returns it.
     */
    private fun existingExpense(amount: Int, vararg splits: Pair<UserId, Int>): Expense {
        val expenseSplits = if (splits.isEmpty()) setOf(ExpenseSplit(alice, amount))
        else splits.mapTo(mutableSetOf()) { ExpenseSplit(it.first, it.second) }
        val expense = Expense(project.id, data(amount), null, alice.value, expenseSplits)
        `when`(expenses.findByIdAndProjectIdOrThrow(expense.id, project.id)).thenReturn(expense)
        return expense
    }

    private fun updateAmount(expense: Expense, amount: Int) = service.update(project.id, expense.id, details(amount))

    @Test
    fun `a new expense is split equally among the members and published with the splits`() {
        project.add(ProjectMember(bob.value, "bob"))
        project.add(ProjectMember(carol.value, "carol"))

        val expense = service.create(project.id, alice, details(1000))

        val event = ExpenseEvent.ExpenseCreated(
            projectId = project.id,
            expenseId = expense.id,
            payer = alice,
            data = data(1000),
            tag = null,
            splits = listOf(Split(alice, 334), Split(bob, 333), Split(carol, 333)),
        )
        verify(balance).handle(event)
        verify(personal).handle(event)
    }

    @Test
    fun `an update is split among the members it already was, and published with the splits it replaces`() {
        project.add(ProjectMember(bob.value, "bob"))
        val expense = existingExpense(900, alice to 450, bob to 450)
        // joins after the expense, so doesn't share it
        project.add(ProjectMember(carol.value, "carol"))

        updateAmount(expense, 1001)

        val event = ExpenseEvent.ExpenseUpdated(
            projectId = project.id,
            expenseId = expense.id,
            payer = alice,
            data = data(1001),
            tag = null,
            splits = listOf(Split(alice, 501), Split(bob, 500)),
            previousAmount = 900,
            previousSplits = listOf(Split(alice, 450), Split(bob, 450)),
        )
        verify(balance).handle(event)
        verify(personal).handle(event)
    }

    @Test
    fun `an expense that was the payer's alone stays theirs when updated`() {
        project.add(ProjectMember(bob.value, "bob"))
        val expense = existingExpense(900, alice to 900)

        updateAmount(expense, 1000)

        assertThat(expense.splits).containsExactly(ExpenseSplit(alice, 1000))
    }

    @Test
    fun `a deletion is published with the splits it undoes`() {
        val expense = existingExpense(1000, alice to 400, bob to 600)

        service.delete(project.id, expense.id)

        val event = ExpenseEvent.ExpenseDeleted(
            projectId = project.id,
            expenseId = expense.id,
            payer = alice,
            amount = 1000,
            splits = listOf(Split(alice, 400), Split(bob, 600)),
        )
        verify(balance).handle(event)
        verify(personal).handle(event)
    }
}
