package pl.damiankaplon.splitit.balancing.internal

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.damiankaplon.splitit.subjectOrThrow
import java.util.UUID

@RestController
@RequestMapping("/projects/{projectId}")
class BalanceController(
    private val balances: BalanceRepository,
) {
    /** `amount` is in the project currency's minor units. */
    data class DebtResponse(
        val debtorId: String,
        val creditorId: String,
        val amount: Long,
    )

    /**
     * The transfers that settle up the project and involve the current user,
     * who pays or is paid; biggest first. Nobody sees what others owe each
     * other, so no membership check is needed: a user who isn't a member has
     * no transfers and gets an empty list.
     */
    @GetMapping("/debts")
    @Transactional(readOnly = true)
    fun listDebts(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<DebtResponse> =
        balances
            .findTransfersByProjectIdAndUserId(projectId, jwt.subjectOrThrow())
            .map { DebtResponse(it.from.value, it.to.value, it.amount) }
            .sortedByDescending { it.amount }
}
