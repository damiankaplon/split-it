package pl.damiankaplon.splitit.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "project")
class Project(
	@Column(name = "owner_id", nullable = false)
    val ownerId: String,

    @Column(name = "name", nullable = false)
    val name: String,

    /** Currency of every expense in the project; stored as its ISO 4217 code. */
    @Column(name = "currency", nullable = false)
    val currency: Currency,
) {
	@Id
    val id: UUID = UUID.randomUUID()
}
