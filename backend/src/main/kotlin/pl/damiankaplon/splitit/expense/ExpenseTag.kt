package pl.damiankaplon.splitit.expense

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import pl.damiankaplon.splitit.TagColors
import java.io.Serializable
import java.util.*

/** Key of a tag scoped to a project: its name is unique within the project. */
@Embeddable
data class ProjectTagId(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "name", nullable = false)
    val name: String,
) : Serializable

/**
 * Tags are scoped to a project and shared by all its members, so they can
 * be reused across expenses.
 */
@Entity
@BatchSize(size = 100)
@Table(name = "expense_tag")
class ExpenseTag(
    @EmbeddedId
    val id: ProjectTagId,

    /** `#rrggbb`, picked at random when the tag is created. */
    @Column(nullable = false)
    val color: String = TagColors.random(),
) {

    constructor(projectId: UUID, name: String) : this(ProjectTagId(projectId, name))

    val projectId: UUID get() = id.projectId

    val name: String get() = id.name
}
