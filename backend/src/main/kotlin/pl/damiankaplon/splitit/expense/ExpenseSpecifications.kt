package pl.damiankaplon.splitit.expense

import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.util.*

object ExpenseSpecifications {

    fun inProject(projectId: UUID) = Specification<Expense> { root, _, cb ->
        cb.equal(root.get<UUID>(Expense::projectId.name), projectId)
    }

    /** Case-insensitive match on the tag name; expenses without a tag never match. */
    fun hasTag(tag: String) = Specification<Expense> { root, _, cb ->
        val tagName = root.join<Expense, ExpenseTag>(Expense::tag.name).get<String>(ExpenseTag::name.name)
        cb.equal(cb.lower(tagName), tag.lowercase())
    }

    /** Case-insensitive "contains" match on the title; `%` and `_` in the input are matched literally. */
    fun titleContains(text: String) = Specification<Expense> { root, _, cb ->
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        cb.like(cb.lower(root.get(Expense::title.name)), "%$escaped%", '\\')
    }

    fun dateFrom(from: LocalDateTime) = Specification<Expense> { root, _, cb ->
        cb.greaterThanOrEqualTo(root.get(Expense::date.name), from)
    }

    fun dateTo(to: LocalDateTime) = Specification<Expense> { root, _, cb ->
        cb.lessThanOrEqualTo(root.get(Expense::date.name), to)
    }
}
