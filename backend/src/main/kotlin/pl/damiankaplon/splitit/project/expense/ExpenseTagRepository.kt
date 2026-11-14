package pl.damiankaplon.splitit.project.expense

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExpenseTagRepository : JpaRepository<ExpenseTag, ProjectTagId> {
    fun findByIdProjectIdAndIdNameIgnoreCase(
        projectId: UUID,
        name: String,
    ): ExpenseTag?

    fun findByIdProjectIdOrderByIdNameAsc(projectId: UUID): List<ExpenseTag>
}
