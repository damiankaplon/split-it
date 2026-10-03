package pl.damiankaplon.splitit.expense

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

/** Tags are scoped to a project and shared by all its members, so they can be reused across expenses. */
@Entity
@Table(name = "expense_tag")
class ExpenseTag(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(nullable = false)
    val name: String,
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
