package pl.damiankaplon.splitit.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProjectMemberRepository : JpaRepository<ProjectMember, UUID> {

    fun findByUserId(userId: String): Set<ProjectMember>
    fun findByProjectId(projectId: UUID): Set<ProjectMember>
    fun findByProject(project: Project): Set<ProjectMember>
    fun existsByProjectIdAndUserId(projectId: UUID, userId: String): Boolean
}
