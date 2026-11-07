package pl.damiankaplon.splitit.expense

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface ExpenseRepository : JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    /**
     * Sums per tag of the expenses dated in [from, to]; untagged ones are
     * summed under a null tag.
     */
    @Query(
        """
        select new pl.damiankaplon.splitit.expense.TagTotal(e.tagName, t.color, sum(e.data.amount))
        from Expense e left join ExpenseTag t on t.id.projectId = e.projectId and t.id.name = e.tagName
        where e.projectId = :projectId and e.data.date >= :from and e.data.date <= :to
        group by e.tagName, t.color
        """
    )
    fun sumAmountByTag(projectId: UUID, from: Instant, to: Instant): List<TagTotal>

    fun findByIdAndProjectId(id: UUID, projectId: UUID): Expense?

    fun findByIdAndProjectIdOrThrow(id: UUID, projectId: UUID): Expense =
        findByIdAndProjectId(id, projectId)
            ?: throw EntityNotFoundException("${Expense::class.qualifiedName} not found by id=$id, projectId=$projectId")
}
