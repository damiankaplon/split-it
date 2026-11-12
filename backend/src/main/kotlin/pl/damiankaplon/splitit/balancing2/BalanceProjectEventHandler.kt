package pl.damiankaplon.splitit.balancing2

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.damiankaplon.splitit.project.ProjectEvent

/**
 * Every project gets an empty [Balance] when it is created. A member who
 * joins needs nothing: they have no entry until an expense involves them.
 */
@Component
@Transactional
class BalanceProjectEventHandler(
    private val balances: BalanceRepository,
) {

    fun handle(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.ProjectCreated -> balances.save(Balance(event.projectId))
            is ProjectEvent.MemberJoined -> Unit
        }
    }
}
