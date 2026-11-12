package pl.damiankaplon.splitit.expense

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
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

    splits: Set<ExpenseSplit>,
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

    /**
     * How [data]'s amount was divided among the members when the expense was
     * last created or updated. Kept, so what depends on the expense can be
     * told exactly what it undoes when the expense changes or is deleted.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "expense_split", joinColumns = [JoinColumn(name = "expense_id")])
    private val _splits: MutableSet<ExpenseSplit> = splits.toMutableSet()

    val splits: Set<ExpenseSplit> get() = _splits.toSet()

    init {
        requireProjectTag(tag)
        requireSplitsOf(data, splits)
    }

    fun update(data: ExpenseData, tag: ExpenseTag?, splits: Set<ExpenseSplit>) {
        requireProjectTag(tag)
        requireSplitsOf(data, splits)
        this.data = data
        this.tagName = tag?.name
        _splits.retainAll(splits)
        _splits.addAll(splits)
    }

    private fun requireProjectTag(tag: ExpenseTag?) =
        require(tag == null || tag.projectId == projectId) { "Tag $tag is not of project=$projectId" }

    private fun requireSplitsOf(data: ExpenseData, splits: Set<ExpenseSplit>) {
        require(splits.isNotEmpty()) { "Expense must be split among at least one member" }
        require(splits.map { it.userId }.toSet().size == splits.size) { "A member can have only one split" }
        require(splits.sumOf { it.amount } == data.amount) {
            "Splits must add up to ${data.amount}, was ${splits.sumOf { it.amount }}"
        }
    }

    @Id
    val id: UUID = UUID.randomUUID()

    @Version
    @Column(nullable = false)
    var version: Long = 0
}

/** A member's share of an [Expense], in minor units. */
@Embeddable
data class ExpenseSplit(
    @Column(name = "user_id", nullable = false)
    val userId: UserId,

    @Column(name = "amount", nullable = false)
    val amount: Int,
)
