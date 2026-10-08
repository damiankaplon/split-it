package pl.damiankaplon.splitit.balancing

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.project.ProjectEvent

@Component
@Transactional
class SaldoProjectEventHandler(
    private val saldos: SaldoRepository,
) {

    fun handle(event: ProjectEvent.ProjectCreated) {
        val saldo = Saldo(event.projectId)
        val member = Saldo.Member(
            id = Saldo.MemberId(event.projectId, event.userId.let(::UserId)),
            totalExpensesAmount = 0
        )
        saldo.members += member
        saldos.save(saldo)
    }

    fun handle(event: ProjectEvent.MemberJoined) {
        val saldo = saldos.findByProjectIdOrThrow(event.projectId)
        saldo.handle(event)
        saldos.save(saldo)
    }
}
