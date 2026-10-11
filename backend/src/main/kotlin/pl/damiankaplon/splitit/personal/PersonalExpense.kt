package pl.damiankaplon.splitit.personal

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import java.time.LocalDateTime
import java.util.*

/**
 * An expense visible only to its owner: either entered manually
 * ([ManualPersonalExpense]) or the owner's share of a project expense
 * ([ProjectPersonalExpense]).
 *
 * @param amount - amount in minor units
 */
@Entity
@Table(name = "personal_expense")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
abstract class PersonalExpense(
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,

    /** In the currency's minor units. */
    @Column(nullable = false)
    var amount: Int,

    @Column(nullable = false)
    var currency: Currency,
) {
    @Id
    val id: UUID = UUID.randomUUID()

    @Version
    @Column(nullable = false)
    var version: Long = 0

    abstract val title: String

    abstract val date: LocalDateTime

    /** Name of the tag, if any. */
    abstract val tag: String?
}
