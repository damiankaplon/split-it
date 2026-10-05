package pl.damiankaplon.splitit.settlement

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import pl.damiankaplon.splitit.UserId
import java.time.Instant
import java.util.*

class SettlementTest {

    private val projectId = UUID.randomUUID()
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    private fun pending() = Settlement(projectId, bob, alice, 4000, now)

    @Test
    fun `confirming emits the event that settles the debt`() {
        val settlement = pending()

        val event = settlement.confirm(now)

        assertThat(event).isEqualTo(SettlementEvent.SettlementConfirmed(projectId, bob, alice, 4000))
        assertThat(settlement.status).isEqualTo(Settlement.Status.CONFIRMED)
        assertThat(settlement.resolvedAt).isEqualTo(now)
    }

    @Test
    fun `rejecting and cancelling resolve without an event`() {
        val rejected = pending().apply { reject(now) }
        val cancelled = pending().apply { cancel(now) }

        assertThat(rejected.status).isEqualTo(Settlement.Status.REJECTED)
        assertThat(cancelled.status).isEqualTo(Settlement.Status.CANCELLED)
    }

    @Test
    fun `a resolved settlement cannot be resolved again`() {
        val settlement = pending().apply { reject(now) }

        assertThatThrownBy { settlement.confirm(now) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { settlement.cancel(now) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `amount must be positive and parties distinct`() {
        assertThatThrownBy { Settlement(projectId, bob, alice, 0, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Settlement(projectId, bob, bob, 100, now) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
