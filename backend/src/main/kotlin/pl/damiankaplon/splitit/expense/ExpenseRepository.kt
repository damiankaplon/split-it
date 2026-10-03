package pl.damiankaplon.splitit.expense

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ExpenseRepository : JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = ["tag"])
    override fun findAll(spec: Specification<Expense>, pageable: Pageable): Page<Expense>

    @EntityGraph(attributePaths = ["tag"])
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Expense?
}
