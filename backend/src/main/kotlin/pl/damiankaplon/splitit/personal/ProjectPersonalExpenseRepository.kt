package pl.damiankaplon.splitit.personal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.Expense
import java.util.*

@Repository
interface ProjectPersonalExpenseRepository : JpaRepository<ProjectPersonalExpense, UUID> {

    fun findByExpense(expense: Expense): List<ProjectPersonalExpense>

    /** Tags of the project expenses the owner has a share in. */
    @Query("select distinct t.name from ProjectPersonalExpense p join p.expense e join e.tag t where p.ownerId = :ownerId")
    fun findTagNamesByOwnerId(@Param("ownerId") ownerId: UserId): List<String>
}
