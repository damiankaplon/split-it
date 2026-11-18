package pl.damiankaplon.splitit.project.internal.expense

import jakarta.persistence.EntityNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import pl.damiankaplon.splitit.kernel.UserId
import pl.damiankaplon.splitit.project.ExpenseEvent
import pl.damiankaplon.splitit.project.internal.Project
import pl.damiankaplon.splitit.project.internal.ProjectMember
import pl.damiankaplon.splitit.project.internal.ProjectRepository
import pl.damiankaplon.splitit.project.internal.policy.SplitPolicy
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * Changes [Expense]s and publishes an [ExpenseEvent] for each change, for
 * the modules that depend on them: the project's balance and each member's
 * share among their personal expenses. The event is stored with the change
 * and delivered to them after it is committed.
 */
@Service
@Transactional
class ExpenseService(
    private val expenses: ExpenseRepository,
    private val tags: ExpenseTagRepository,
    private val projects: ProjectRepository,
    private val events: ApplicationEventPublisher,
) {
    data class ExpenseDetails(
        val title: String,
        val date: Instant,
        val amount: Int,
        val tag: String?,
    )

    fun create(
        projectId: UUID,
        payer: UserId,
        details: ExpenseDetails,
    ): Expense {
        val project = projects.findByIdOrThrow(projectId)
        val tag = findOrCreateTag(projectId, details.tag)
        val data = details.toData(project.currency)
        val expense =
            Expense(
                projectId = projectId,
                data = data,
                tag = tag,
                createdBy = payer.value,
                splits = project.split(data.amount),
            ).let(expenses::save)

        events.publishEvent(
            ExpenseEvent.ExpenseCreated(
                projectId = projectId,
                expenseId = expense.id,
                payer = payer,
                data = expense.data,
                tag = tag?.toTag(),
                splits = expense.splits.toEvent(),
            ),
        )
        return expense
    }

    fun update(
        projectId: UUID,
        expenseId: UUID,
        details: ExpenseDetails,
    ): Expense {
        val project = projects.findByIdOrThrow(projectId)
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        val payer = UserId(expense.createdBy)
        val previousAmount = expense.data.amount
        val previousSplits = expense.splits.toEvent()
        val tag = findOrCreateTag(projectId, details.tag)
        val sharing = expense.splits.mapTo(mutableSetOf()) { it.userId }
        expense.update(details.toData(expense.data.currency), tag, project.split(details.amount, among = sharing))

        events.publishEvent(
            ExpenseEvent.ExpenseUpdated(
                projectId = projectId,
                expenseId = expense.id,
                payer = payer,
                data = expense.data,
                tag = tag?.toTag(),
                splits = expense.splits.toEvent(),
                previousAmount = previousAmount,
                previousSplits = previousSplits,
            ),
        )
        return expense
    }

    fun delete(
        projectId: UUID,
        expenseId: UUID,
    ) {
        val expense = expenses.findByIdAndProjectIdOrThrow(expenseId, projectId)
        expenses.delete(expense)

        events.publishEvent(
            ExpenseEvent.ExpenseDeleted(
                projectId = projectId,
                expenseId = expense.id,
                payer = UserId(expense.createdBy),
                amount = expense.data.amount,
                splits = expense.splits.toEvent(),
            ),
        )
    }

    /**
     * Moves every expense of the project's tag [from] to the tag [to] and
     * removes [from]. Only the project's expenses change: no event is
     * published, so members' personal expenses keep their own tags, and
     * balances are untouched as no amount changes. Merging a tag into itself
     * changes nothing.
     */
    fun mergeTags(
        projectId: UUID,
        from: String,
        to: String,
    ) {
        val source = findTagOrThrow(projectId, from)
        val target = findTagOrThrow(projectId, to)
        if (source.id == target.id) return
        expenses.findByProjectIdAndTagName(projectId, source.name).forEach { it.retag(target) }
        tags.delete(source)
    }

    private fun findTagOrThrow(
        projectId: UUID,
        name: String,
    ): ExpenseTag =
        tags.findByIdProjectIdAndIdNameIgnoreCase(projectId, name.trim())
            ?: throw EntityNotFoundException("${ExpenseTag::class.qualifiedName} not found by name=$name, projectId=$projectId")

    private fun ExpenseDetails.toData(currency: Currency) = ExpenseData(title, date, amount, currency)

    /**
     * Divides [amount] with the project's split policy among [among]: by
     * default the project's current members. An updated expense stays shared
     * by the members it already was, so editing it never charges someone
     * who joined later, nor anyone an expense that was the payer's alone.
     */
    private fun Project.split(
        amount: Int,
        among: Set<UserId> =
            this.members
                .map(ProjectMember::userId)
                .map(::UserId)
                .toSet(),
    ): Set<ExpenseSplit> {
        val expense = SplitPolicy.Expense(amount, among)
        return splitPolicy.policy.calculate(expense).mapTo(linkedSetOf()) { ExpenseSplit(it.userId, it.amount) }
    }

    /**
     * Reuses an existing project tag (case-insensitive) or creates a new one;
     * a blank name means no tag.
     */
    private fun findOrCreateTag(
        projectId: UUID,
        name: String?,
    ): ExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findByIdProjectIdAndIdNameIgnoreCase(projectId, trimmed)
            ?: tags.save(ExpenseTag(projectId, trimmed))
    }

    private fun ExpenseTag.toTag() = Tag(this.name, this.color)

    /** Ordered by user, so events are deterministic. */
    private fun Collection<ExpenseSplit>.toEvent(): List<ExpenseEvent.Split> =
        sortedBy { it.userId.value }.map { ExpenseEvent.Split(it.userId, it.amount) }
}
