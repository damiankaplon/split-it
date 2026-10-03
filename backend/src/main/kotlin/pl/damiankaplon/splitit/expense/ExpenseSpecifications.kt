package pl.damiankaplon.splitit.expense

import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.util.*

object ExpenseSpecifications {

    fun inProject(projectId: UUID) = Specification<Expense> { root, _, cb ->
        cb.equal(root.get<UUID>("projectId"), projectId)
    }

    /** Case-insensitive match on the tag name; expenses without a tag never match. */
    fun hasTag(tag: String) = Specification<Expense> { root, _, cb ->
        cb.equal(cb.lower(root.join<Expense, ExpenseTag>("tag").get("name")), tag.lowercase())
    }

    /** Case-insensitive "contains" match on the title; `%` and `_` in the input are matched literally. */
    fun titleContains(text: String) = Specification<Expense> { root, _, cb ->
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        cb.like(cb.lower(root.get("title")), "%$escaped%", '\\')
    }

    fun dateFrom(from: LocalDateTime) = Specification<Expense> { root, _, cb ->
        cb.greaterThanOrEqualTo(root.get("date"), from)
    }

    fun dateTo(to: LocalDateTime) = Specification<Expense> { root, _, cb ->
        cb.lessThanOrEqualTo(root.get("date"), to)
    }
}
