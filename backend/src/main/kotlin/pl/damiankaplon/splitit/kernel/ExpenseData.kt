package pl.damiankaplon.splitit.kernel

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant
import java.util.*

/**
 * What every expense consists of, whether a project or a personal one; a
 * project expense hands it over to its members' personal expenses.
 *
 * @param amount - amount in minor units
 */
@Embeddable
data class ExpenseData(
    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "date", nullable = false)
    val date: Instant,

    /** In the currency's minor units. */
    @Column(name = "amount", nullable = false)
    val amount: Int,

    @Column(name = "currency", nullable = false)
    val currency: Currency,
)
