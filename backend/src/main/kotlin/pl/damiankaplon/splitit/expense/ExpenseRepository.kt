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
import java.util.*

@Repository
interface ExpenseRepository : JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = ["tag"])
    override fun findAll(spec: Specification<Expense>, pageable: Pageable): Page<Expense>

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.projectId = :projectId")
    fun sumAmountByProjectId(projectId: UUID): Long

    @EntityGraph(attributePaths = ["tag"])
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Expense?

    @EntityGraph(attributePaths = ["tag"])
    fun findByIdAndProjectIdOrThrow(id: UUID, projectId: UUID): Expense =
        findByIdAndProjectId(id, projectId)
            ?: throw EntityNotFoundException("${Expense::class.qualifiedName} not found by id=$id, projectId=$projectId")
}
