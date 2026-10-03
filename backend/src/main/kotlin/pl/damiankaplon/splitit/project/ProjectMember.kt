package pl.damiankaplon.splitit.project

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "project_member")
class ProjectMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(nullable = false)
    val username: String,
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
