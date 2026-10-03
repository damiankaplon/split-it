package pl.damiankaplon.splitit.expense

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ExpenseTagRepository : JpaRepository<ExpenseTag, UUID> {
    fun findByProjectIdAndNameIgnoreCase(projectId: UUID, name: String): ExpenseTag?
    fun findByProjectIdOrderByNameAsc(projectId: UUID): List<ExpenseTag>
}
