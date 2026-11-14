package pl.damiankaplon.splitit.personal.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

@Repository
interface ProjectPersonalExpenseRepository : JpaRepository<ProjectPersonalExpense, UUID> {
    fun findByProjectExpenseId(projectExpenseId: UUID): List<ProjectPersonalExpense>

    fun findByProjectExpenseIdAndOwnerId(
        projectExpenseId: UUID,
        ownerId: UserId,
    ): ProjectPersonalExpense?
}
