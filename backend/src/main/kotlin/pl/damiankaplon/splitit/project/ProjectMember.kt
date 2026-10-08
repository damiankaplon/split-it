package pl.damiankaplon.splitit.project

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Value object owned by [Project]; identified within a project by
 * [userId].
 */
@Embeddable
class ProjectMember(
    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "username", nullable = false)
    val username: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return (other as ProjectMember).userId == this.userId

    }

    override fun hashCode(): Int {
        return userId.hashCode()
    }

    override fun toString(): String {
        return "ProjectMember(userId='$userId', username='$username')"
    }
}
