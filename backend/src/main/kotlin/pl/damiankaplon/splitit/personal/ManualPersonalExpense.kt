package pl.damiankaplon.splitit.personal

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData

/** Entered by the owner, who may change or delete it. */
@Entity
@DiscriminatorValue("MANUAL")
class ManualPersonalExpense(
    ownerId: UserId,

    data: ExpenseData,

    tag: PersonalExpenseTag?,
) : PersonalExpense(ownerId, data, tag)
