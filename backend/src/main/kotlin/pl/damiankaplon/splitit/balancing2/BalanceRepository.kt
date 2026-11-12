package pl.damiankaplon.splitit.balancing2

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BalanceRepository : JpaRepository<Balance, UUID> {

    fun findByProjectId(projectId: UUID): Balance?

    fun findByProjectIdOrThrow(projectId: UUID): Balance =
        findByProjectId(projectId) ?: throw EntityNotFoundException("Balance of $projectId not found")

    /**
     * The stored transfers that settle up the project, ordered by who pays and
     * then by who is paid. Empty if the project has no [Balance] or nothing to
     * settle. Reads only the transfers: the [Balance] itself and its amounts
     * are not loaded.
     */
    @Query(
        """
        select t
        from Balance b join b.transfers t
        where b.projectId = :projectId
        order by t.from, t.to
        """
    )
    fun findTransfersByProjectId(projectId: UUID): List<Transfer>
}
