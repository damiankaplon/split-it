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
import pl.damiankaplon.splitit.balancing2.BalanceSettlementEventHandler
import pl.damiankaplon.splitit.project.ProjectRepository
import pl.damiankaplon.splitit.subjectOrThrow
import pl.damiankaplon.splitit.userIdOrThrow
import java.time.Instant
import java.util.*

@RestController
class SettlementController(
    private val settlements: SettlementRepository,
    private val projects: ProjectRepository,
    private val balanceSettlementEventHandler: BalanceSettlementEventHandler,
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
     * The current user declares they paid the creditor, any other member of
     * the project, any amount: not necessarily one the balance suggests. The
     * balance stays unchanged until the creditor confirms.
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
        if (!projects.existsByIdAndMemberUserId(projectId, currentUserId.value)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=$projectId member")
        }
        if (!projects.existsByIdAndMemberUserId(projectId, creditor.value)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "${creditor.value} is not project=$projectId member")
        }
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

    /**
     * The creditor confirms they received the money, which the balance then
     * records.
     */
    @PostMapping("/settlements/{settlementId}/confirm")
    @Transactional
    fun confirm(
        @PathVariable settlementId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): SettlementResponse {
        val currentUserId = jwt.userIdOrThrow()
        val settlement = settlements.findByIdAndCreditorOrThrow(settlementId, currentUserId)
        val settlementConfirmed = settlement.confirm(time.now())
        balanceSettlementEventHandler.handle(settlementConfirmed)
        return settlement.toResponse()
    }

    /** The creditor did not receive the money; the balance stays. */
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

    private fun Settlement.toResponse() =
        SettlementResponse(id, debtor.value, creditor.value, amount, status, createdAt)
}
