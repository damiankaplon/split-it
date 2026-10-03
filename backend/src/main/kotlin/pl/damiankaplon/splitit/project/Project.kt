package pl.damiankaplon.splitit.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "project")
class Project(
	@Column(name = "owner_id", nullable = false)
	var ownerId: UUID,

	@Column(nullable = false)
	var name: String,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}
