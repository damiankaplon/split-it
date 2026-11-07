package pl.damiankaplon.splitit.expense

import jakarta.persistence.*
import pl.damiankaplon.splitit.kernel.ExpenseData
import java.util.*

@Entity
@Table(name = "expense")
class Expense(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    data: ExpenseData,

    tag: ExpenseTag?,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,
) {
    @Embedded
    var data: ExpenseData = data
        protected set

    /**
     * Name of the project's [ExpenseTag], if any; the tag is the one of
     * [projectId] with this name.
     */
    @Column(name = "tag_name")
    var tagName: String? = tag?.name
        protected set

    init {
        requireProjectTag(tag)
    }

    fun update(data: ExpenseData, tag: ExpenseTag?) {
        requireProjectTag(tag)
        this.data = data
        this.tagName = tag?.name
    }

    private fun requireProjectTag(tag: ExpenseTag?) =
        require(tag == null || tag.projectId == projectId) { "Tag $tag is not of project=$projectId" }

    @Id
    val id: UUID = UUID.randomUUID()

    @Version
    @Column(nullable = false)
    var version: Long = 0
}
