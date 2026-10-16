package pl.damiankaplon.splitit.personal

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.UserId
import java.util.*

@Repository
interface IncomeRepository : JpaRepository<Income, UUID> {

    fun findByOwnerIdOrderByDateDescIdDesc(ownerId: UserId): List<Income>

    fun findByIdAndOwnerId(id: UUID, ownerId: UserId): Income?

    /**
     * Someone else's income is reported as missing, so its existence isn't
     * revealed.
     */
    fun findByIdAndOwnerIdOrThrow(id: UUID, ownerId: UserId): Income =
        findByIdAndOwnerId(id, ownerId)
            ?: throw EntityNotFoundException("${Income::class.qualifiedName} not found by id=$id, ownerId=${ownerId.value}")
}
