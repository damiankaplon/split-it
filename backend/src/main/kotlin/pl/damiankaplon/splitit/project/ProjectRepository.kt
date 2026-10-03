package pl.damiankaplon.splitit.project

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByIdOrThrow(id: UUID): Project =
        findById(id).orElseThrow { EntityNotFoundException("Project with id=$id not found") }
}
