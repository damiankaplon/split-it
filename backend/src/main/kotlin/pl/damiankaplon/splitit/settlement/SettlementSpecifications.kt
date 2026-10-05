package pl.damiankaplon.splitit.settlement

import org.springframework.data.jpa.domain.Specification
import pl.damiankaplon.splitit.UserId
import java.util.*

object SettlementSpecifications {

    fun inProject(projectId: UUID) = Specification<Settlement> { root, _, cb ->
        cb.equal(root.get<UUID>(Settlement::projectId.name), projectId)
    }

    fun hasStatus(status: Settlement.Status) = Specification<Settlement> { root, _, cb ->
        cb.equal(root.get<Settlement.Status>(Settlement::status.name), status)
    }

    /** The user is either side of the settlement. */
    fun involves(userId: UserId) = Specification<Settlement> { root, _, cb ->
        // UserId is an inline value class, so the persisted attribute is its underlying String
        cb.or(
            cb.equal(root.get<String>(Settlement::debtor.name), userId.value),
            cb.equal(root.get<String>(Settlement::creditor.name), userId.value),
        )
    }
}
