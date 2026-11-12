package pl.damiankaplon.splitit.personal

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.expense.ExpenseEvent
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import java.util.*

private val LOGGER = KotlinLogging.logger {}

/**
 * Keeps each member's share of a project expense among their personal
 * expenses: one [ProjectPersonalExpense] per member the expense is split
 * among.
 */
@Component
@Transactional
class ProjectExpenseEventHandler(
    private val personalExpenses: ProjectPersonalExpenseRepository,
    private val tags: PersonalExpenseTagRepository,
) {

    fun handle(event: ExpenseEvent) {
        when (event) {
            is ExpenseEvent.ExpenseCreated -> handle(event)
            is ExpenseEvent.ExpenseUpdated -> handle(event)
            is ExpenseEvent.ExpenseDeleted -> handle(event)
        }
    }

    private fun handle(event: ExpenseEvent.ExpenseCreated) {
        LOGGER.info { "Handling : $event" }
        event.splits.forEach { saveShare(event.expenseId, event.projectId, event.data, event.tag, it) }
    }

    /** Members the expense is no longer split among lose their share. */
    private fun handle(event: ExpenseEvent.ExpenseUpdated) {
        LOGGER.info { "Handling : $event" }
        val sharing = event.splits.map { it.userId }.toSet()
        personalExpenses.findByProjectExpenseId(event.expenseId)
            .filter { it.ownerId !in sharing }
            .let(personalExpenses::deleteAll)
        event.splits.forEach { saveShare(event.expenseId, event.projectId, event.data, event.tag, it) }
    }

    private fun handle(event: ExpenseEvent.ExpenseDeleted) {
        LOGGER.info { "Handling : $event" }
        personalExpenses.deleteAll(personalExpenses.findByProjectExpenseId(event.expenseId))
    }

    /**
     * Creates or updates the member's share. It's tagged with the member's own
     * tag of the project tag's name, created in the project tag's color if
     * they have none yet.
     */
    private fun saveShare(
        expenseId: UUID,
        projectId: UUID,
        expense: ExpenseData,
        tag: Tag?,
        split: ExpenseEvent.Split,
    ) {
        val data = expense.copy(amount = split.amount)
        val ownTag = tag?.let { tags.findOrCreate(split.userId, it.name, it.color) }
        personalExpenses.findByProjectExpenseIdAndOwnerId(expenseId, split.userId)?.update(data, ownTag)
            ?: personalExpenses.save(ProjectPersonalExpense(split.userId, expenseId, projectId, data, ownTag))
    }
}
