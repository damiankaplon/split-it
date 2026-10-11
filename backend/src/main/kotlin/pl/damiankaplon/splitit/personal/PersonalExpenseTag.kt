package pl.damiankaplon.splitit.personal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import pl.damiankaplon.splitit.UserId
import java.util.*

/**
 * Tags are scoped to their owner, so they can be reused across the owner's
 * personal expenses.
 */
@Entity
@BatchSize(size = 100)
@Table(name = "personal_expense_tag")
class PersonalExpenseTag(
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,

    @Column(nullable = false)
    val name: String,
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
