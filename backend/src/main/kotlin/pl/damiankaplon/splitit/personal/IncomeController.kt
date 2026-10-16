package pl.damiankaplon.splitit.personal

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.Time
import pl.damiankaplon.splitit.project.IsoCurrency
import pl.damiankaplon.splitit.userIdOrThrow
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*

/** Every endpoint works only on the calling user's own incomes. */
@RestController
@RequestMapping("/personal")
class IncomeController(
    private val incomes: IncomeRepository,
    private val expenses: PersonalExpenseRepository,
    private val time: Time,
) {

    data class IncomeRequest(
        @field:NotBlank val title: String,
        val date: Instant,
        val delimiting: Boolean,
        @field:Positive val amount: Int,
        @field:NotBlank @field:IsoCurrency val currency: String,
    ) {
        val currencyCode: Currency by lazy { Currency.getInstance(currency.trim().uppercase()) }
    }

    data class CurrencyResponse(
        val code: String,
        val minorUnits: Int,
    )

    data class IncomeResponse(
        val id: UUID,
        val title: String,
        val date: Instant,
        val delimiting: Boolean,
        val amount: Int,
        val currency: CurrencyResponse,
    )

    @PostMapping("/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(
        @Valid @RequestBody request: IncomeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): IncomeResponse = Income(
        ownerId = jwt.userIdOrThrow(),
        title = request.title.trim(),
        date = request.date,
        delimiting = request.delimiting,
        amount = request.amount,
        currency = request.currencyCode,
    ).let(incomes::save).toResponse()

    /** Newest first. */
    @GetMapping("/incomes")
    @Transactional(readOnly = true)
    fun list(@AuthenticationPrincipal jwt: Jwt): List<IncomeResponse> =
        incomes.findByOwnerIdOrderByDateDescIdDesc(jwt.userIdOrThrow()).map { it.toResponse() }

    @PutMapping("/incomes/{incomeId}")
    @Transactional
    fun update(
        @PathVariable incomeId: UUID,
        @Valid @RequestBody request: IncomeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): IncomeResponse {
        val income = incomes.findByIdAndOwnerIdOrThrow(incomeId, jwt.userIdOrThrow())
        income.title = request.title.trim()
        income.date = request.date
        income.delimiting = request.delimiting
        income.amount = request.amount
        income.currency = request.currencyCode
        return income.toResponse()
    }

    @DeleteMapping("/incomes/{incomeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(
        @PathVariable incomeId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        incomes.delete(incomes.findByIdAndOwnerIdOrThrow(incomeId, jwt.userIdOrThrow()))
    }

    data class SavingsResponse(
        val currency: CurrencyResponse,
        /** The delimiting income starting the period. */
        val start: Instant,
        /**
         * The next delimiting income in the currency, exclusive; null while the
         * period is running.
         */
        val end: Instant?,
        /** In the currency's minor units. */
        val income: Long,
        val expenses: Long,
        val saved: Long,
    )

    /**
     * Savings per currency, one per period between delimiting incomes. Returns
     * the periods overlapping the instants [from]..[to] (zoned ISO date-times,
     * both inclusive; default: the year up to now, a year counted in the zone
     * of [to], else of [from], else UTC); the periods themselves are never
     * cut. Currencies by code, newest period first.
     */
    @GetMapping("/savings")
    @Transactional(readOnly = true)
    fun savings(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime?,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<SavingsResponse> {
        val zone = (to ?: from)?.zone ?: time.zone()
        val rangeTo = to?.toInstant() ?: time.now()
        val rangeFrom = from?.toInstant() ?: rangeTo.atZone(zone).minusYears(1).toInstant()
        if (rangeFrom.isAfter(rangeTo)) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "from must not be after to"
        )

        val ownerId = jwt.userIdOrThrow()
        val (delimiting, other) = incomes.findByOwnerIdOrderByDateDescIdDesc(ownerId).partition { it.delimiting }
        if (delimiting.isEmpty()) return emptyList()
        val firstStart = delimiting.minOf { it.date }
        val spent = expenses.findAll(
            PersonalExpenseSpecifications.ownedBy(ownerId).and(PersonalExpenseSpecifications.dateFrom(firstStart))
        )

        return Savings.periods(
            delimitingIncomes = delimiting.map { Flow(it.date, it.amount, it.currency) },
            otherIncomes = other.map { Flow(it.date, it.amount, it.currency) },
            expenses = spent.map { Flow(it.date, it.amount, it.currency) },
        ).filter { it.start <= rangeTo && (it.end == null || it.end > rangeFrom) }
            .map {
                SavingsResponse(
                    currency = CurrencyResponse(it.currency.currencyCode, it.currency.defaultFractionDigits),
                    start = it.start,
                    end = it.end,
                    income = it.income,
                    expenses = it.expenses,
                    saved = it.saved,
                )
            }
    }

    private fun Income.toResponse() = IncomeResponse(
        id = id,
        title = title,
        date = date,
        delimiting = delimiting,
        amount = amount,
        currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
    )
}
