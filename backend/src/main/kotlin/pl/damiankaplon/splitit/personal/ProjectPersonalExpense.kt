package pl.damiankaplon.splitit.personal

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData
import java.util.*

/**
 * The owner's share of a project expense, as divided by the
 * project's split policy. Kept in sync with the expense by
 * [ProjectToPersonalExpenseSynchronization]: everything but the amount
 * is the expense's, and its tag is the owner's tag of the same name.
 */
@Entity
@DiscriminatorValue("PROJECT")
class ProjectPersonalExpense(
    ownerId: UserId,

    @Column(name = "project_expense_id")
    val projectExpenseId: UUID,

    @Column(name = "project_id")
    val projectId: UUID,

    data: ExpenseData,

    tag: PersonalExpenseTag?,
) : PersonalExpense(ownerId, data, tag)
