package pl.damiankaplon.splitit.expense

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface ExpenseRepository : JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = ["tag"])
    override fun findAll(spec: Specification<Expense>, pageable: Pageable): Page<Expense>

    /**
     * Sums per tag of the expenses dated in [from, until); untagged ones are
     * summed under a null tag.
     */
    @Query(
        """
        select new pl.damiankaplon.splitit.expense.TagTotal(t.name, t.color, sum(e.amount))
        from Expense e left join e.tag t
        where e.projectId = :projectId and e.date >= :from and e.date < :until
        group by t.id, t.name, t.color
        """
    )
    fun sumAmountByTag(projectId: UUID, from: LocalDateTime, until: LocalDateTime): List<TagTotal>

    @EntityGraph(attributePaths = ["tag"])
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Expense?

    @EntityGraph(attributePaths = ["tag"])
    fun findByIdAndProjectIdOrThrow(id: UUID, projectId: UUID): Expense =
        findByIdAndProjectId(id, projectId)
            ?: throw EntityNotFoundException("${Expense::class.qualifiedName} not found by id=$id, projectId=$projectId")
}
