package pl.damiankaplon.splitit.settlement

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.Time
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.balancing.SaldoSettlementEventHandler
import pl.damiankaplon.splitit.subjectOrThrow
import pl.damiankaplon.splitit.userIdOrThrow
import java.time.Instant
import java.util.*

@RestController
class SettlementController(
    private val settlements: SettlementRepository,
    private val saldoSettlementEventHandler: SaldoSettlementEventHandler,
    private val time: Time,
) {

    data class SettlementResponse(
        val id: UUID,
        val debtorId: String,
        val creditorId: String,
        val amount: Int,
        val status: Settlement.Status,
        val createdAt: Instant,
    )

    @GetMapping("/projects/{projectId}/settlements")
    @Transactional(readOnly = true)
    fun listPending(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<SettlementResponse> {
        val currentUserId = jwt.subjectOrThrow().let(::UserId)
        val spec = SettlementSpecifications.inProject(projectId)
            .and(SettlementSpecifications.hasStatus(Settlement.Status.PENDING))
            .and(SettlementSpecifications.involves(currentUserId))
        val oldestFirst = Sort.by(Sort.Order.asc(Settlement::createdAt.name))
        return settlements.findAll(spec, oldestFirst).map { it.toResponse() }
    }

    data class SettlementRequest(
        @field:NotBlank val creditorId: String,
        @field:Positive val amount: Int,
    )

    /**
     * The current user declares they paid (part of) their debt to the
     * creditor. The debt stays unchanged until the creditor confirms.
     */
    @PostMapping("/projects/{projectId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun request(
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: SettlementRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): SettlementResponse {
        val currentUserId = jwt.subjectOrThrow().let(::UserId)
        val creditor = request.creditorId.let(::UserId)
        val alreadyPendingSettlement = settlements.existsByProjectIdAndCreditorAndStatus(
            projectId,
            creditor,
            Settlement.Status.PENDING
        )
        if (alreadyPendingSettlement) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A settlement with ${request.creditorId} awaits confirmation"
            )
        }
        val settlement = Settlement(
            projectId = projectId,
            debtor = currentUserId,
            creditor = creditor,
            amount = request.amount,
            createdAt = time.now()
        )
        settlements.save(settlement)
        return settlement.toResponse()
    }

    /** The creditor confirms they received the money, which reduces the debt. */
    @PostMapping("/settlements/{settlementId}/confirm")
    @Transactional
    fun confirm(
        @PathVariable settlementId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): SettlementResponse {
        val currentUserId = jwt.userIdOrThrow()
        val settlement = settlements.findByIdAndCreditorOrThrow(settlementId, currentUserId)
        val settlementConfirmed = settlement.confirm(time.now())
        throwConflictOnError { saldoSettlementEventHandler.handle(settlementConfirmed) }
        return settlement.toResponse()
    }

    /** The creditor did not receive the money; the debt stays. */
    @PostMapping("/settlements/{settlementId}/reject")
    @Transactional
    fun reject(
        @PathVariable settlementId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): SettlementResponse {
        val currentUserId = jwt.userIdOrThrow()
        val settlement = settlements.findByIdAndCreditorOrThrow(settlementId, currentUserId)
        settlement.reject(time.now())
        return settlement.toResponse()
    }

    /** The debtor withdraws their request. */
    @PostMapping("/settlements/{settlementId}/cancel")
    @Transactional
    fun cancel(
        @PathVariable settlementId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): SettlementResponse {
        val currentUserId = jwt.userIdOrThrow()
        val settlement = settlements.findByIdAndDebtorOrThrow(settlementId, currentUserId)
        settlement.cancel(time.now())
        return settlement.toResponse()
    }

    private fun throwConflictOnError(action: () -> Unit) {
        try {
            action()
        } catch (e: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, e.message, e)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, e.message, e)
        }
    }

    private fun Settlement.toResponse() =
        SettlementResponse(id, debtor.value, creditor.value, amount, status, createdAt)
}
