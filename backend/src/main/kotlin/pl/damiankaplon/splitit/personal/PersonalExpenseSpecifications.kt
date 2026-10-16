package pl.damiankaplon.splitit.personal

import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.Expense
import pl.damiankaplon.splitit.expense.ExpenseTag
import java.time.Instant
import java.util.*

/**
 * Title, date and tag live on the manual expense itself but on the expense
 * a project share points to, so every filter reads whichever of the two is
 * set.
 */
object PersonalExpenseSpecifications {

    fun ownedBy(ownerId: UserId) = Specification<PersonalExpense> { root, _, cb ->
        // UserId is an inline value class, so the persisted attribute is its underlying String
        cb.equal(root.get<String>(PersonalExpense::ownerId.name), ownerId.value)
    }

    /**
     * Case-insensitive match on the tag name; expenses without a tag never
     * match.
     */
    fun hasTag(tag: String) = Specification<PersonalExpense> { root, _, cb ->
        val manualTag = cb.treat(root, ManualPersonalExpense::class.java)
            .join<ManualPersonalExpense, PersonalExpenseTag>(ManualPersonalExpense::personalTag.name, JoinType.LEFT)
            .get<String>(PersonalExpenseTag::name.name)
        val projectTag = root.projectExpense(cb)
            .join<Expense, ExpenseTag>(Expense::tag.name, JoinType.LEFT)
            .get<String>(ExpenseTag::name.name)
        cb.equal(cb.lower(cb.coalesce(manualTag, projectTag)), tag.lowercase())
    }

    /**
     * Case-insensitive "contains" match on the title; `%` and `_` in the input
     * are matched literally.
     */
    fun titleContains(text: String) = Specification<PersonalExpense> { root, _, cb ->
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        cb.like(cb.lower(root.title(cb)), "%$escaped%", '\\')
    }

    fun dateFrom(from: Instant) = Specification<PersonalExpense> { root, _, cb ->
        cb.greaterThanOrEqualTo(root.date(cb), from)
    }

    fun dateTo(to: Instant) = Specification<PersonalExpense> { root, _, cb ->
        cb.lessThanOrEqualTo(root.date(cb), to)
    }

    /**
     * Orders by date, newest first, with the id as a tie-breaker so pages
     * never overlap. Applied here since a pageable's sort can only name
     * attributes of [PersonalExpense] itself.
     */
    fun newestFirst() = Specification<PersonalExpense> { root, query, cb ->
        query.orderBy(cb.desc(root.date(cb)), cb.desc(root.get<UUID>(PersonalExpense::id.name)))
        null
    }

    private fun Root<PersonalExpense>.projectExpense(cb: CriteriaBuilder): Join<ProjectPersonalExpense, Expense> =
        cb.treat(this, ProjectPersonalExpense::class.java)
            .join(ProjectPersonalExpense::expense.name, JoinType.LEFT)

    private fun Root<PersonalExpense>.title(cb: CriteriaBuilder): Expression<String> = cb.coalesce(
        cb.treat(this, ManualPersonalExpense::class.java).get(ManualPersonalExpense::title.name),
        projectExpense(cb).get(Expense::title.name),
    )

    private fun Root<PersonalExpense>.date(cb: CriteriaBuilder): Expression<Instant> = cb.coalesce(
        cb.treat(this, ManualPersonalExpense::class.java).get(ManualPersonalExpense::date.name),
        projectExpense(cb).get(Expense::date.name),
    )
}
