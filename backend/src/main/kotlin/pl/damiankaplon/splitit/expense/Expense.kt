package pl.damiankaplon.splitit.expense

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.*

@Entity
// Loads the expenses behind a page of personal expense shares together
@BatchSize(size = 100)
@Table(name = "expense")
class Expense(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var date: Instant,

    @Column(nullable = false)
    var amount: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    var tag: ExpenseTag?,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,
) {
    @Id
    val id: UUID = UUID.randomUUID()

    @Version
    @Column(nullable = false)
    var version: Long = 0
}
