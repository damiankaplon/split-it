package pl.damiankaplon.splitit.balancing.internal

import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BalanceRepository : JpaRepository<Balance, UUID> {
    /**
     * The project's [Balance], locked until the end of the transaction, so
     * changes to it are applied one after another rather than conflicting.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByProjectId(projectId: UUID): Balance?

    fun findByProjectIdOrThrow(projectId: UUID): Balance =
        findByProjectId(projectId) ?: throw EntityNotFoundException("Balance of $projectId not found")

    /**
     * The stored transfers that settle up the project and that [userId] pays
     * or is paid, ordered by who pays and then by who is paid. Empty if the
     * project has no [Balance] or nothing to settle. Reads only the transfers:
     * the [Balance] itself and its amounts are not loaded. A plain `String`
     * rather than a `UserId`, which Kotlin would mangle the method name for.
     */
    @Query(
        """
        select t
        from Balance b join b.transfers t
        where b.projectId = :projectId and (t.from = :userId or t.to = :userId)
        order by t.from, t.to
        """,
    )
    fun findTransfersByProjectIdAndUserId(
        projectId: UUID,
        userId: String,
    ): List<Transfer>

    /**
     * Creates the project's empty [Balance] unless it already exists, also
     * when another transaction is creating it at the same time.
     */
    @Modifying
    @Query(
        nativeQuery = true,
        value = "insert into balance (project_id, version) values (:projectId, 0) on conflict do nothing",
    )
    fun createIfMissing(projectId: UUID)

    /** Records [eventId] as applied; 0 if it already was. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
        insert into balance_applied_event (event_id, project_id, applied_at)
        values (:eventId, :projectId, now())
        on conflict do nothing
        """,
    )
    fun recordEventApplied(
        eventId: UUID,
        projectId: UUID,
    ): Int
}
