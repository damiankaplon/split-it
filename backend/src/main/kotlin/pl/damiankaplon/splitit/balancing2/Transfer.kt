package pl.damiankaplon.splitit.balancing2

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import pl.damiankaplon.splitit.UserId

/**
 * Money moved from one user to another, in minor units. Also a row of
 * `balance_transfer`: a transfer [Balance] suggests to settle up.
 */
@Embeddable
data class Transfer(
    @Column(name = "from_user_id", nullable = false)
    val from: UserId,

    @Column(name = "to_user_id", nullable = false)
    val to: UserId,

    @Column(name = "amount", nullable = false)
    val amount: Long,
) {
    init {
        require(amount > 0) { "Transfer amount must be positive, was $amount" }
        require(from != to) { "Cannot transfer to oneself" }
    }
}
