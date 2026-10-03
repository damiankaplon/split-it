package pl.damiankaplon.splitit.project

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.util.*
import java.util.Currency.getInstance
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [IsoCurrencyValidator::class])
annotation class IsoCurrency(
    val message: String = "must be an ISO 4217 currency code with minor units",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class IsoCurrencyValidator : ConstraintValidator<IsoCurrency, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || runCatching<Currency> { getInstance(value.trim().uppercase()) }.getOrNull()
            ?.takeIf { it.defaultFractionDigits >= 0 } != null
}
