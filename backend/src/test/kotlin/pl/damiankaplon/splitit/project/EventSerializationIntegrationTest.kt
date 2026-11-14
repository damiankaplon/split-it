package pl.damiankaplon.splitit.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.events.core.EventSerializer
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.Tag
import pl.damiankaplon.splitit.kernel.UserId
import java.time.Instant
import java.util.UUID

/**
 * Events are stored serialized in `event_publication`, and the ones not yet
 * handled are read back on restart, so they must come back exactly as they
 * were, [ExpenseEvent.eventId] included.
 */
class EventSerializationIntegrationTest
    @Autowired
    constructor(
        private val serializer: EventSerializer,
    ) : RollbackIntegrationTest() {
        private val alice = UserId("alice")
        private val bob = UserId("bob")

        /** As the registry does: read back as the concrete class it stored. */
        private fun roundTrip(event: Any): Any = serializer.deserialize(serializer.serialize(event), event.javaClass)

        @Test
        fun `expense events survive being stored`() {
            val splits = listOf(ExpenseEvent.Split(alice, 500), ExpenseEvent.Split(bob, 500))
            val data = ExpenseData("Dinner", Instant.parse("2026-10-03T19:30:00Z"), 1000, PLN)
            val events =
                listOf(
                    ExpenseEvent.ExpenseCreated(UUID.randomUUID(), UUID.randomUUID(), alice, data, Tag("Food", "#ef4444"), splits),
                    ExpenseEvent.ExpenseUpdated(UUID.randomUUID(), UUID.randomUUID(), alice, data, null, splits, 900, splits),
                    ExpenseEvent.ExpenseDeleted(UUID.randomUUID(), UUID.randomUUID(), alice, 1000, splits),
                )

            events.forEach { assertThat(roundTrip(it)).isEqualTo(it) }
        }

        @Test
        fun `settlement and project events survive being stored`() {
            val confirmed = SettlementEvent.SettlementConfirmed(UUID.randomUUID(), bob, alice, 4000)
            val created = ProjectEvent.ProjectCreated(UUID.randomUUID(), "alice")
            val joined = ProjectEvent.MemberJoined(UUID.randomUUID(), "bob")

            assertThat(roundTrip(confirmed)).isEqualTo(confirmed)
            assertThat(roundTrip(created)).isEqualTo(created)
            assertThat(roundTrip(joined)).isEqualTo(joined)
        }
    }
