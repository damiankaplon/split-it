package pl.damiankaplon.splitit.invitation

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProjectInvitationRepository : JpaRepository<Invitation, UUID> {

    fun findByProjectIdAndToken(projectId: UUID, token: String): Invitation?

    fun findByProjectIdAndTokenOrThrow(projectId: UUID, token: String): Invitation =
        findByProjectIdAndToken(projectId, token)
            ?: throw EntityNotFoundException("No invitation found for projectId=$projectId and token=$token")
}
