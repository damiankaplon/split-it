package pl.damiankaplon.splitit.personal

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.expense.ProjectExpenseDeleted
import pl.damiankaplon.splitit.expense.ProjectExpenseSplitCalculated

private val LOGGER = KotlinLogging.logger {}

/**
 * Keeps each member's share of a project expense among their personal
 * expenses, in the same transaction as the change to the expense.
 */
@Component
@Transactional
class ProjectToPersonalExpenseSynchronization(
    private val personalExpenses: ProjectPersonalExpenseRepository,
    private val tags: PersonalExpenseTagRepository,
) {

    /**
     * Creates or updates the member's share. It's tagged with the member's own
     * tag of the project tag's name, created in the project tag's color if
     * they have none yet.
     */
    fun handle(event: ProjectExpenseSplitCalculated) {
        LOGGER.info { "Handling : $event" }
        val data = event.data.copy(amount = event.amount)
        val tag = event.tag?.let { tags.findOrCreate(event.userId, it.name, it.color) }
        personalExpenses.findByProjectExpenseIdAndOwnerId(event.expenseId, event.userId)?.update(data, tag)
            ?: personalExpenses.save(ProjectPersonalExpense(event.userId, event.expenseId, event.projectId, data, tag))
    }

    fun handle(event: ProjectExpenseDeleted) {
        personalExpenses.deleteAll(personalExpenses.findByProjectExpenseId(event.expenseId))
    }
}
