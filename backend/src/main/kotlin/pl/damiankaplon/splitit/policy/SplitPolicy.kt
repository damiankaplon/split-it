package pl.damiankaplon.splitit.policy

import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.policy.SplitPolicy.Split
import kotlin.math.roundToInt

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

class EqualShare : SplitPolicy {

    override fun calculate(expense: SplitPolicy.Expense): Set<Split> {
        val equalShare = expense.amount.div(expense.members.size.toDouble()).roundToInt()
        return expense.members.map { Split(it, equalShare) }.toSet()
    }
}

class CustomShare(
    private val shares: Map<UserId, Int>,
) : SplitPolicy {


    override fun calculate(expense: SplitPolicy.Expense): Set<Split> {
        TODO("Not implemented yet")
    }
}
