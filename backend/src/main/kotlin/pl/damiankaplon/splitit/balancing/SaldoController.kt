package pl.damiankaplon.splitit.balancing

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.project.ProjectRepository
import pl.damiankaplon.splitit.subjectOrThrow
import java.util.*

@RestController
@RequestMapping("/projects/{projectId}")
class SaldoController(
    private val saldos: SaldoRepository,
    private val projects: ProjectRepository,
) {

    /** `amount` is in the project currency's minor units. */
    data class DebtResponse(
        val debtorId: String,
        val creditorId: String,
        val amount: Int,
    )

    /** All open debts in the project, so members can see who owes whom. */
    @GetMapping("/debts")
    @Transactional(readOnly = true)
    fun listDebts(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<DebtResponse> {
        if (!projects.existsByIdAndMemberUserId(projectId, jwt.subjectOrThrow())) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=$projectId member")
        }
        return saldos.findByProjectIdOrThrow(projectId).debts
            .map { DebtResponse(it.debtor.value, it.creditor.value, it.amount) }
            .sortedByDescending { it.amount }
    }
}
