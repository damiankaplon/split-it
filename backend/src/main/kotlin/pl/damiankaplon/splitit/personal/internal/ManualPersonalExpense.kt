package pl.damiankaplon.splitit.personal.internal

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.kernel.UserId

/** Entered by the owner, who may change or delete it. */
@Entity
@DiscriminatorValue("MANUAL")
class ManualPersonalExpense(
    ownerId: UserId,
    data: ExpenseData,
    tag: PersonalExpenseTag?,
) : PersonalExpense(ownerId, data, tag)
