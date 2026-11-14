package pl.damiankaplon.splitit.project.internal.settlement

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

@Repository
interface SettlementRepository :
    JpaRepository<Settlement, UUID>,
    JpaSpecificationExecutor<Settlement> {
    fun existsByProjectIdAndDebtorAndCreditorAndStatus(
        projectId: UUID,
        debtor: UserId,
        creditor: UserId,
        status: Settlement.Status,
    ): Boolean

    fun findByIdAndCreditor(
        settlementId: UUID,
        creditor: UserId,
    ): Settlement?

    fun findByIdAndCreditorOrThrow(
        settlementId: UUID,
        creditor: UserId,
    ): Settlement =
        findByIdAndCreditor(settlementId, creditor)
            ?: throw EntityNotFoundException("No settlement found by id: $settlementId and creditor: ${creditor.value}")

    fun findByIdAndDebtor(
        settlementId: UUID,
        debtor: UserId,
    ): Settlement?

    fun findByIdAndDebtorOrThrow(
        settlementId: UUID,
        debtor: UserId,
    ) = findByIdAndDebtor(settlementId, debtor)
        ?: throw EntityNotFoundException("No settlement found by id: $settlementId and debtor: ${debtor.value}")
}
