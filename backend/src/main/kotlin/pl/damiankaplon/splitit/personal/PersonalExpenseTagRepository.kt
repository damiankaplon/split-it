package pl.damiankaplon.splitit.personal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.UserId
import java.util.*

@Repository
interface PersonalExpenseTagRepository : JpaRepository<PersonalExpenseTag, UUID> {

    fun findByOwnerIdAndNameIgnoreCase(ownerId: UserId, name: String): PersonalExpenseTag?

    fun findByOwnerIdOrderByNameAsc(ownerId: UserId): List<PersonalExpenseTag>
}
