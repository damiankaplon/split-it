package pl.damiankaplon.splitit.balancing

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SaldoRepository : JpaRepository<Saldo, UUID> {

    fun findByProjectId(projectId: UUID): Saldo?

    fun findByProjectIdOrThrow(projectId: UUID): Saldo =
        findByProjectId(projectId) ?: throw EntityNotFoundException("Saldo of $projectId not found")
}