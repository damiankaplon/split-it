package pl.damiankaplon.splitit.personal.internal

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import jakarta.persistence.Version
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.UserId
import java.util.UUID

/**
 * An expense visible only to its owner: either entered manually
 * ([ManualPersonalExpense]) or the owner's share of a project expense
 * ([ProjectPersonalExpense]). Either kind is tagged with one of the
 * owner's own tags.
 */
@Entity
@Table(name = "personal_expense")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
abstract class PersonalExpense(
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    data: ExpenseData,
    tag: PersonalExpenseTag?,
) {
    @Id
    val id: UUID = UUID.randomUUID()

    @Version
    @Column(nullable = false)
    var version: Long = 0

    @Embedded
    var data: ExpenseData = data
        protected set

    /**
     * Name of the owner's [PersonalExpenseTag], if any; the tag is the one of
     * [ownerId] with this name.
     */
    @Column(name = "tag_name")
    var tagName: String? = tag?.name
        protected set

    init {
        requireOwnTag(tag)
    }

    fun update(
        data: ExpenseData,
        tag: PersonalExpenseTag?,
    ) {
        requireOwnTag(tag)
        this.data = data
        this.tagName = tag?.name
    }

    /** Only moves the expense to another of the owner's tags. */
    fun retag(tag: PersonalExpenseTag) {
        requireOwnTag(tag)
        this.tagName = tag.name
    }

    private fun requireOwnTag(tag: PersonalExpenseTag?) =
        require(tag == null || tag.ownerId == ownerId) { "Tag $tag is not of owner=${ownerId.value}" }
}
