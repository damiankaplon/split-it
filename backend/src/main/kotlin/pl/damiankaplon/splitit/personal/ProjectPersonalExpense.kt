package pl.damiankaplon.splitit.personal

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.Expense
import java.time.LocalDateTime
import java.util.*

/**
 * The owner's share of a project [Expense], as divided by the project's
 * split policy. Title, date and tag always come from the expense; the
 * amount is kept in sync by the expense's changes.
 */
@Entity
@DiscriminatorValue(ProjectPersonalExpense.KIND)
class ProjectPersonalExpense(
    ownerId: UserId,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    val expense: Expense,

    amount: Int,

    currency: Currency,
) : PersonalExpense(ownerId, amount, currency) {

    override val title: String get() = expense.title

    override val date: LocalDateTime get() = expense.date

    override val tag: String? get() = expense.tag?.name

    val projectId: UUID get() = expense.projectId

    companion object {
        const val KIND = "PROJECT"
    }
}
