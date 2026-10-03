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
) {
	@Id
    val id: UUID = UUID.randomUUID()
}
