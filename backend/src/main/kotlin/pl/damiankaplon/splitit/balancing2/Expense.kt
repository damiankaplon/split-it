package pl.damiankaplon.splitit.balancing2

import pl.damiankaplon.splitit.UserId

/**
 * An expense as balancing sees it, already split by the expense module.
 * [splits] values add up exactly to [amount]. The payer may or may not be
 * in [splits].
 */
data class Expense(val payer: UserId, val amount: Int, val splits: Map<UserId, Int>) {
    init {
        require(amount > 0) { "Expense amount must be positive, was $amount" }
        require(splits.isNotEmpty()) { "Expense must be shared by at least one user" }
        require(splits.values.all { it >= 0 }) { "Shares must not be negative" }
        require(splits.values.sum() == amount) { "Shares must add up to $amount, was ${splits.values.sum()}" }
    }
}
