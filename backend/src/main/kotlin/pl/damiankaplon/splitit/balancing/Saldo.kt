package pl.damiankaplon.splitit.balancing

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.expense.ExpenseEvent
import pl.damiankaplon.splitit.project.ProjectEvent
import pl.damiankaplon.splitit.settlement.SettlementEvent
import java.io.Serializable
import java.util.*

@Entity
@Table(name = "saldo")
class Saldo(
    @Id
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
) {
    @Version
    @Column(nullable = false)
    var version: Long = 0

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    val members: MutableSet<Member> = mutableSetOf()

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    val debts: MutableSet<Debt> = mutableSetOf()

    fun handle(event: ExpenseEvent.ExpensesIncreased) {
        val member = members.singleOrNull { it.userId == event.userId }
        if (member == null) {
            members.add(Member(MemberId(projectId, event.userId), event.increasedBy.toLong()))
        } else {
            member.totalExpensesAmount += event.increasedBy
        }
        recalculateDebts()
    }

    fun handle(event: ExpenseEvent.ExpensesReduced) {
        val member = checkNotNull(members.singleOrNull { it.userId == event.userId }) {
            "Cannot reduce expenses of ${event.userId.value}, they have none in project $projectId"
        }
        member.totalExpensesAmount -= event.reducedBy
        recalculateDebts()
    }


    fun handle(event: SettlementEvent.SettlementConfirmed) {
        val (_, debtor, creditor, amount) = event
        require(amount > 0) { "Settled amount must be positive, was $amount" }
        val debt = requireNotNull(debts.singleOrNull { it.debtor == debtor && it.creditor == creditor }) {
            "${debtor.value} owes nothing to ${creditor.value} in project $projectId"
        }
        require(amount <= debt.amount) { "Cannot settle $amount, the debt is only ${debt.amount}" }
        members.single { it.userId == debtor }.settledAmount += amount
        members.single { it.userId == creditor }.settledAmount -= amount
        recalculateDebts()
    }

    fun handle(event: ProjectEvent.MemberJoined) {
        val member = Member(
            id = MemberId(event.projectId, event.userId.let(::UserId)),
            totalExpensesAmount = 0
        )
        members += member
        recalculateDebts()
    }

    /**
     * Updates [debts] in place (instead of replacing the collection) so orphan
     * removal and the (project_id, debtor_id, creditor_id) primary key keep
     * working: unchanged pairs are updated, stale ones removed.
     */
    private fun recalculateDebts() {
        val calculated = calculateDebts()
        debts.removeIf { it.id !in calculated }
        calculated.forEach { (id, amount) ->
            val existing = debts.singleOrNull { it.id == id }
            if (existing != null) existing.amount = amount else debts.add(Debt(id, amount))
        }
    }

    /**
     * Equal split between all members. Computes net balances (paid - fair
     * share) and greedily settles the biggest debtor with the biggest
     * creditor, which yields at most (members - 1) transfers. Balances are
     * scaled by the members count so the math stays exact in Long. Settlements
     * count as money paid. Balances below one minor unit are ignored: they are
     * what is left after paying a rounded-up debt.
     */
    private fun calculateDebts(): Map<DebtId, Int> {
        val membersCount = members.size
        if (membersCount < 2) return emptyMap()
        val total = members.sumOf { it.totalExpensesAmount }

        val creditors = PriorityQueue<Pair<UserId, Long>>(compareByDescending { it.second })
        val debtors = PriorityQueue<Pair<UserId, Long>>(compareByDescending { it.second })
        members.forEach { member ->
            val balance = (member.totalExpensesAmount + member.settledAmount) * membersCount - total
            if (balance >= membersCount) creditors.add(member.userId to balance)
            if (balance <= -membersCount) debtors.add(member.userId to -balance)
        }

        val result = mutableMapOf<DebtId, Int>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, credit) = creditors.poll()
            val (debtor, debt) = debtors.poll()
            val settled = minOf(credit, debt)
            result[DebtId(projectId, debtor, creditor)] = Math.ceilDiv(settled, membersCount.toLong()).toInt()
            if (credit > settled) creditors.add(creditor to credit - settled)
            if (debt > settled) debtors.add(debtor to debt - settled)
        }
        return result
    }

    @Embeddable
    data class MemberId(
        @Column(name = "project_id", nullable = false)
        val projectId: UUID,

        @Column(name = "user_id", nullable = false)
        val userId: UserId,
    ) : Serializable

    @Entity
    @Table(name = "saldo_member")
    class Member(
        @EmbeddedId
        val id: MemberId,

        @Column(name = "total_expenses_amount", nullable = false)
        var totalExpensesAmount: Long,
    ) {
        /** Paid to other members to settle debts, minus received from them. */
        @Column(name = "settled_amount", nullable = false)
        var settledAmount: Long = 0

        val userId: UserId get() = id.userId

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Member) return false
            return this.id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    @Embeddable
    data class DebtId(
        @Column(name = "project_id", nullable = false)
        val projectId: UUID,

        @Column(name = "debtor_id", nullable = false)
        val debtor: UserId,

        @Column(name = "creditor_id", nullable = false)
        val creditor: UserId,
    ) : Serializable

    @Entity
    @Table(name = "saldo_debt")
    class Debt(
        @EmbeddedId
        val id: DebtId,

        @Column(nullable = false)
        var amount: Int,
    ) {
        val debtor: UserId get() = id.debtor
        val creditor: UserId get() = id.creditor

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Debt) return false
            return this.id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }
}
