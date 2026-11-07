package pl.damiankaplon.splitit.personal

import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData
import java.time.Instant
import java.util.*

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
        cb.equal(cb.lower(root.get(PersonalExpense::tagName.name)), tag.lowercase())
    }

    /**
     * Case-insensitive "contains" match on the title; `%` and `_` in the input
     * are matched literally.
     */
    fun titleContains(text: String) = Specification<PersonalExpense> { root, _, cb ->
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        cb.like(cb.lower(root.data().get(ExpenseData::title.name)), "%$escaped%", '\\')
    }

    fun dateFrom(from: Instant) = Specification<PersonalExpense> { root, _, cb ->
        cb.greaterThanOrEqualTo(root.data().get(ExpenseData::date.name), from)
    }

    fun dateTo(to: Instant) = Specification<PersonalExpense> { root, _, cb ->
        cb.lessThanOrEqualTo(root.data().get(ExpenseData::date.name), to)
    }

    /**
     * Orders by date, newest first, with the id as a tie-breaker so pages
     * never overlap.
     */
    fun newestFirst() = Specification<PersonalExpense> { root, query, cb ->
        query.orderBy(
            cb.desc(root.data().get<Instant>(ExpenseData::date.name)),
            cb.desc(root.get<UUID>(PersonalExpense::id.name)),
        )
        null
    }

    private fun Root<PersonalExpense>.data(): Path<ExpenseData> = get(PersonalExpense::data.name)
}
