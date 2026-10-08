package pl.damiankaplon.splitit.project

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface ProjectRepository : JpaRepository<Project, UUID> {

    fun findByIdOrThrow(id: UUID): Project =
        findById(id).orElseThrow { EntityNotFoundException("Project with id=$id not found") }

    @Query("select p from Project p join p._members m where m.userId = :userId")
    fun findByMemberUserId(@Param("userId") userId: String): Set<Project>

    @Query("select count(p) > 0 from Project p join p._members m where p.id = :id and m.userId = :userId")
    fun existsByIdAndMemberUserId(@Param("id") id: UUID, @Param("userId") userId: String): Boolean
}
