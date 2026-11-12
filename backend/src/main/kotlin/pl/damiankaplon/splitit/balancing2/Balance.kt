package pl.damiankaplon.splitit.balancing2

import jakarta.persistence.*
import pl.damiankaplon.splitit.UserId
import java.util.*

/**
 * What a project's users owe each other, kept as one signed amount per
 * user in minor units: positive means the group owes them, negative means
 * they owe the group. The amounts always add up to 0.
 *
 * It is a stored aggregate: every operation applies exactly the change
 * it describes, so nothing needs the project's expenses or settlements
 * loaded. [remove] and [change] must therefore receive exactly the
 * [Expense] that was added. The transfers that settle everyone up are
 * stored as well, and recalculated by every change.
 */
@Entity
@Table(name = "balance")
class Balance(
    @Id
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
) {

    /**
     * Only users with a non-zero amount are in it, each at most once.
     *
     * A collection of values rather than of entities on purpose: an amount is
     * changed by replacing its [UserBalance], which makes the collection dirty
     * and bumps [version], so concurrent edits conflict.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "balance_entry", joinColumns = [JoinColumn(name = "project_id")])
    private val userBalances: MutableSet<UserBalance> = mutableSetOf()

    /**
     * The transfers that bring every amount to 0, at most one per pair of
     * users. Stored the same way as [userBalances] and brought up to date at
     * the end of every change of them, so reading them calculates nothing.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "balance_transfer", joinColumns = [JoinColumn(name = "project_id")])
    private val transfers: MutableSet<Transfer> = mutableSetOf()

    /**
     * The payer balance increases by [Expense.amount], everyone in
     * [Expense.splits] balance decreases.
     */
    fun add(expense: Expense) = applyChanges { accumulate(expense, 1) }

    /** The exact opposite of [add]. */
    fun remove(expense: Expense) = applyChanges { accumulate(expense, -1) }

    /**
     * Same as [remove] of [old] followed by [add] of [new], applied as one net
     * change, so a user who ends up where they started is left untouched.
     */
    fun change(old: Expense, new: Expense) = applyChanges {
        accumulate(old, -1)
        accumulate(new, 1)
    }

    /**
     * [Transfer.from] has paid [Transfer.to] [Transfer.amount]. Any transfer
     * between any two users is fine, not only one of [settlements]: whoever
     * pays more than they owed is then owed the difference, and [settlements]
     * are recalculated to match.
     */
    fun record(transfer: Transfer) = applyChanges {
        accumulate(transfer.from, transfer.amount)
        accumulate(transfer.to, -transfer.amount)
    }

    /** The user's net amount; 0 if they have no entry. */
    fun of(user: UserId): Long = userBalanceOf(user)?.amount ?: 0

    /**
     * The stored transfers that bring every user's amount to 0, in at most
     * (users with a non-zero amount - 1) transfers. Nothing is calculated
     * here, see [updateSettlements]. Ordered by who pays and then by who is
     * paid, so the result is deterministic.
     */
    fun settlements(): List<Transfer> = transfers.sortedWith(compareBy({ it.from.value }, { it.to.value }))

    /**
     * [changes] are summed per user first and applied afterwards, so the order
     * they are described in doesn't matter and no user is created just to be
     * dropped again.
     */
    private fun applyChanges(changes: MutableMap<UserId, Long>.() -> Unit) {
        mutableMapOf<UserId, Long>().apply(changes).forEach { (user, change) -> adjust(user, change) }
        check(userBalances.sumOf { it.amount } == 0L) { "Amounts of project $projectId do not add up to 0" }
        updateSettlements()
    }

    /**
     * Updates [transfers] in place instead of replacing them, so only the
     * transfers that differ cost a delete or an insert.
     */
    private fun updateSettlements() {
        val calculated = calculateSettlements()
        transfers.retainAll(calculated)
        transfers.addAll(calculated)
    }

    /**
     * Greedy minimal set of transfers that brings every user's amount to 0:
     * the biggest debtor pays the biggest creditor, until nobody is left. Each
     * transfer settles at least one user, and the last one settles two, so
     * there are at most (users with a non-zero amount - 1) of them, and no two
     * users are paired twice.
     *
     * Ties are broken by [UserId.value], so the result is deterministic.
     */
    private fun calculateSettlements(): Set<Transfer> {
        val biggestFirst = compareByDescending<Pair<UserId, Long>> { it.second }.thenBy { it.first.value }
        val creditors = PriorityQueue(biggestFirst)
        val debtors = PriorityQueue(biggestFirst)
        userBalances.forEach { (user, amount) ->
            if (amount > 0) creditors.add(user to amount)
            if (amount < 0) debtors.add(user to -amount)
        }

        val result = linkedSetOf<Transfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, credit) = creditors.poll()
            val (debtor, debt) = debtors.poll()
            val settled = minOf(credit, debt)
            result += Transfer(from = debtor, to = creditor, amount = settled)
            if (credit > settled) creditors.add(creditor to credit - settled)
            if (debt > settled) debtors.add(debtor to debt - settled)
        }
        return result
    }

    private fun adjust(user: UserId, change: Long) {
        if (change == 0L) return
        val current = userBalanceOf(user)
        val amount = Math.addExact(current?.amount ?: 0L, change)
        if (current != null) userBalances.remove(current)
        if (amount != 0L) userBalances.add(UserBalance(user, amount))
    }

    private fun userBalanceOf(user: UserId) = userBalances.find { it.userId == user }

    private fun MutableMap<UserId, Long>.accumulate(user: UserId, change: Long) {
        this[user] = Math.addExact(this[user] ?: 0L, change)
    }

    /** [direction] is 1 to apply the expense and -1 to undo it. */
    private fun MutableMap<UserId, Long>.accumulate(expense: Expense, direction: Long) {
        accumulate(expense.payer, direction * expense.amount)
        expense.splits.forEach { (user, share) -> accumulate(user, -direction * share) }
    }

    @Version
    @Column(nullable = false)
    var version: Long = 0

    /**
     * One row of `balance_entry`: [amount] is the user's net amount in minor
     * units.
     */
    @Embeddable
    private data class UserBalance(
        @Column(name = "user_id", nullable = false)
        val userId: UserId,

        @Column(name = "amount", nullable = false)
        val amount: Long,
    )
}
