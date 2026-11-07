package pl.damiankaplon.splitit.personal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.UserId

@Repository
interface PersonalExpenseTagRepository : JpaRepository<PersonalExpenseTag, PersonalTagId> {

    fun findByIdOwnerIdAndIdNameIgnoreCase(ownerId: UserId, name: String): PersonalExpenseTag?

    fun findByIdOwnerIdOrderByIdNameAsc(ownerId: UserId): List<PersonalExpenseTag>

    /**
     * Reuses the owner's tag of the name (case-insensitive) or creates one in
     * [color].
     */
    fun findOrCreate(ownerId: UserId, name: String, color: String): PersonalExpenseTag =
        findByIdOwnerIdAndIdNameIgnoreCase(ownerId, name)
            ?: save(PersonalExpenseTag(PersonalTagId(ownerId, name), color))
}
