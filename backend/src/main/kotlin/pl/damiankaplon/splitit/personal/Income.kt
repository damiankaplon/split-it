package pl.damiankaplon.splitit.personal

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import java.time.Instant
import java.util.*

/**
 * Money the owner received. A [delimiting] income starts a new savings
 * period in its currency; the other ones only add to the current period.
 *
 * @param amount - amount in minor units
 */
@Entity
@Table(name = "income")
class Income(
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var date: Instant,

    @Column(nullable = false)
    var delimiting: Boolean,

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
}
