package pl.damiankaplon.splitit.personal

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.UserId
import java.util.*

/**
 * Tags and project expenses are associations of the subtypes, so they
 * can't be fetched with an entity graph from here; their entities are
 * batch-fetched instead (see `@BatchSize`).
 */
@Repository
interface PersonalExpenseRepository : JpaRepository<PersonalExpense, UUID>, JpaSpecificationExecutor<PersonalExpense> {

    fun findByIdAndOwnerId(id: UUID, ownerId: UserId): PersonalExpense?

    /**
     * Someone else's expense is reported as missing, so its existence isn't
     * revealed.
     */
    fun findByIdAndOwnerIdOrThrow(id: UUID, ownerId: UserId): PersonalExpense =
        findByIdAndOwnerId(id, ownerId)
            ?: throw EntityNotFoundException("${PersonalExpense::class.qualifiedName} not found by id=$id, ownerId=${ownerId.value}")
}
