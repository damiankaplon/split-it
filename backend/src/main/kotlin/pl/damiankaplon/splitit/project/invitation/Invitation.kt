package pl.damiankaplon.splitit.project.invitation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(name = "project_invitation")
class Invitation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(nullable = false, unique = true)
    val token: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "accepted_by", nullable = false)
    var acceptedBy: String? = null,
) {

    val isAccepted: Boolean get() = acceptedBy != null
}
