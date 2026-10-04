package pl.damiankaplon.splitit.balancing

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.project.ProjectEvent

@Component
@Transactional
class SaldoProjectEventHandler(
    private val saldos: SaldoRepository,
) {

    fun handle(event: ProjectEvent.ProjectCreated) {
        if (!saldos.existsById(event.projectId)) {
            saldos.save(Saldo(event.projectId))
        }
    }
}
