package pl.damiankaplon.splitit.personal

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import java.time.Instant
import java.util.*

/** Entered by the owner, who may change or delete it. */
@Entity
@DiscriminatorValue(ManualPersonalExpense.KIND)
class ManualPersonalExpense(
    ownerId: UserId,

    @Column(name = "title")
    override var title: String,

    @Column(name = "date")
    override var date: Instant,

    amount: Int,

    currency: Currency,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    var personalTag: PersonalExpenseTag?,
) : PersonalExpense(ownerId, amount, currency) {

    override val tag: String? get() = personalTag?.name

    override val tagColor: String? get() = personalTag?.color

    companion object {
        const val KIND = "MANUAL"
    }
}
