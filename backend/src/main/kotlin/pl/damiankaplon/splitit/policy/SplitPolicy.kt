package pl.damiankaplon.splitit.policy

import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.policy.SplitPolicy.Split

interface SplitPolicy {

    data class Expense(
        val amount: Int,
        val members: Set<UserId>,
    )

    data class Split(
        val userId: UserId,
        val amount: Int,
    )

    fun calculate(expense: Expense): Set<Split>
}

/**
 * Everyone gets the same share, rounded down; the minor units left over go
 * one each to the first members by [UserId.value], so the shares always
 * add up exactly to the amount.
 */
class EqualShare : SplitPolicy {

    override fun calculate(expense: SplitPolicy.Expense): Set<Split> {
        require(expense.members.isNotEmpty()) { "An expense must be shared by at least one member" }
        val share = expense.amount / expense.members.size
        val remainder = expense.amount % expense.members.size
        return expense.members.sortedBy { it.value }
            .mapIndexed { i, member -> Split(member, share + if (i < remainder) 1 else 0) }
            .toSet()
    }
}

class CustomShare(
    private val shares: Map<UserId, Int>,
) : SplitPolicy {


    override fun calculate(expense: SplitPolicy.Expense): Set<Split> {
        TODO("Not implemented yet")
    }
}
