package pl.damiankaplon.splitit.personal

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import pl.damiankaplon.splitit.TagColors
import pl.damiankaplon.splitit.UserId
import java.io.Serializable

/**
 * Key of a tag scoped to its owner: its name is unique among the owner's
 * tags.
 */
@Embeddable
data class PersonalTagId(
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,

    @Column(name = "name", nullable = false)
    val name: String,
) : Serializable

/**
 * Tags are scoped to their owner, so they can be reused across the owner's
 * personal expenses.
 */
@Entity
@BatchSize(size = 100)
@Table(name = "personal_expense_tag")
class PersonalExpenseTag(
    @EmbeddedId
    val id: PersonalTagId,

    /** `#rrggbb`, picked at random when the tag is created. */
    @Column(nullable = false)
    val color: String = TagColors.random(),
) {

    constructor(ownerId: UserId, name: String) : this(PersonalTagId(ownerId, name))

    val ownerId: UserId get() = id.ownerId

    val name: String get() = id.name
}
