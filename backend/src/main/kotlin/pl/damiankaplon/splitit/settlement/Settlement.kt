package pl.damiankaplon.splitit.settlement

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import java.time.Instant
import java.util.*

/**
 * A four-eyes settlement: the debtor declares they paid [amount] to the
 * creditor outside the app, and the debt is reduced only once the creditor
 * confirms they received it.
 */
@Entity
@Table(name = "settlement")
class Settlement(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "debtor_id", nullable = false)
    val debtor: UserId,

    @Column(name = "creditor_id", nullable = false)
    val creditor: UserId,

    @Column(nullable = false)
    val amount: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    val id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.PENDING
        protected set

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
        protected set

    @Version
    @Column(nullable = false)
    var version: Long = 0

    init {
        require(amount > 0) { "Settled amount must be positive, was $amount" }
        require(debtor != creditor) { "Cannot settle a debt with oneself" }
    }

    /**
     * The creditor received the money; the returned event is what reduces the
     * debt.
     */
    fun confirm(now: Instant): SettlementEvent.SettlementConfirmed {
        resolve(Status.CONFIRMED, now)
        return SettlementEvent.SettlementConfirmed(projectId, debtor, creditor, amount)
    }

    /** The creditor did not receive the money. */
    fun reject(now: Instant) = resolve(Status.REJECTED, now)

    /** The debtor withdraws the request. */
    fun cancel(now: Instant) = resolve(Status.CANCELLED, now)

    private fun resolve(next: Status, now: Instant) {
        check(status == Status.PENDING) { "Settlement $id is already $status" }
        status = next
        resolvedAt = now
    }

    enum class Status { PENDING, CONFIRMED, REJECTED, CANCELLED }
}
