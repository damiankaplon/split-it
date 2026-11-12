package pl.damiankaplon.splitit.balancing2

import pl.damiankaplon.splitit.UserId
import kotlin.random.Random

/**
 * An expense of a random payer, shared by a random subset of [users] (some
 * shares may be 0).
 */
internal fun randomExpense(random: Random, users: List<UserId>): Expense {
    val amount = random.nextInt(1, 10_000)
    val participants = users.shuffled(random).take(random.nextInt(1, users.size + 1))
    val cuts = List(participants.size - 1) { random.nextInt(0, amount + 1) }.sorted()
    val bounds = listOf(0) + cuts + amount
    val shares = participants.withIndex().associate { (i, user) -> user to bounds[i + 1] - bounds[i] }
    return Expense(users.random(random), amount, shares)
}

internal fun randomTransfer(random: Random, users: List<UserId>): Transfer {
    val (from, to) = users.shuffled(random)
    return Transfer(from, to, random.nextLong(1, 5_000))
}
