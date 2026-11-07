package pl.damiankaplon.splitit.expense

import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import pl.damiankaplon.splitit.kernel.ExpenseData
import java.time.Instant
import java.util.*

object ExpenseSpecifications {

    fun inProject(projectId: UUID) = Specification<Expense> { root, _, cb ->
        cb.equal(root.get<UUID>(Expense::projectId.name), projectId)
    }

    /** Case-insensitive match on the tag name; expenses without a tag never match. */
    fun hasTag(tag: String) = Specification<Expense> { root, _, cb ->
        cb.equal(cb.lower(root.get(Expense::tagName.name)), tag.lowercase())
    }

    fun createdBy(userId: String) = Specification<Expense> { root, _, cb ->
        cb.equal(root.get<String>(Expense::createdBy.name), userId)
    }

    /**
     * Case-insensitive "contains" match on the title; `%` and `_` in the input
     * are matched literally.
     */
    fun titleContains(text: String) = Specification<Expense> { root, _, cb ->
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        cb.like(cb.lower(root.data().get(ExpenseData::title.name)), "%$escaped%", '\\')
    }

    fun dateFrom(from: Instant) = Specification<Expense> { root, _, cb ->
        cb.greaterThanOrEqualTo(root.data().get(ExpenseData::date.name), from)
    }

    fun dateTo(to: Instant) = Specification<Expense> { root, _, cb ->
        cb.lessThanOrEqualTo(root.data().get(ExpenseData::date.name), to)
    }

    private fun Root<Expense>.data(): Path<ExpenseData> = get(Expense::data.name)
}
