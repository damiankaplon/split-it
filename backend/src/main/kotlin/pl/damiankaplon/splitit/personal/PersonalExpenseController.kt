package pl.damiankaplon.splitit.personal

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.TagColors
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.kernel.ExpenseData
import pl.damiankaplon.splitit.project.IsoCurrency
import pl.damiankaplon.splitit.userIdOrThrow
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*

private const val DEFAULT_PAGE_SIZE = 20
private const val MAX_PAGE_SIZE = 100L

/** Every endpoint works only on the calling user's own personal expenses. */
@RestController
@RequestMapping("/personal")
class PersonalExpenseController(
    private val expenses: PersonalExpenseRepository,
    private val tags: PersonalExpenseTagRepository,
) {

    data class PersonalExpenseRequest(
        @field:NotBlank val title: String,
        val date: Instant,
        @field:Positive val amount: Int,
        @field:NotBlank @field:IsoCurrency val currency: String,
        val tag: String? = null,
    ) {
        val currencyCode: Currency by lazy { Currency.getInstance(currency.trim().uppercase()) }
    }

    data class CurrencyResponse(
        val code: String,
        val minorUnits: Int,
    )

    /**
     * Where a personal expense comes from. Either kind can be changed here,
     * but a [PROJECT] one is overwritten (or recreated, if deleted) whenever
     * its project expense changes.
     */
    enum class Source { MANUAL, PROJECT }

    data class PersonalExpenseResponse(
        val id: UUID,
        val title: String,
        val date: Instant,
        val amount: Int,
        val currency: CurrencyResponse,
        val tag: String?,
        val tagColor: String?,
        val source: Source,
        /** The project of the expense this is a share of; null for manual ones. */
        val projectId: UUID?,
    )

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(
        @Valid @RequestBody request: PersonalExpenseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): PersonalExpenseResponse {
        val ownerId = jwt.userIdOrThrow()
        val tag = findOrCreateTag(ownerId, request.tag)
        return ManualPersonalExpense(
            ownerId = ownerId,
            data = request.toData(),
            tag = tag,
        ).let(expenses::save)
            .toResponse(tag?.color)
    }

    data class PersonalExpensePage(
        val items: List<PersonalExpenseResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean,
    )

    @GetMapping("/expenses")
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime?,
        @AuthenticationPrincipal jwt: Jwt,
    ): PersonalExpensePage {
        val ownerId = jwt.userIdOrThrow()
        val spec = filterSpec(ownerId, tag, title, from, to).and(PersonalExpenseSpecifications.newestFirst())
        val result = expenses.findAll(spec, PageRequest.of(page, size))
        val tagColors = tagColors(ownerId)
        return PersonalExpensePage(
            items = result.content.map { expense: PersonalExpense ->
                expense.toResponse(
                    tagColor = expense.tagName?.let(tagColors::get)
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext(),
        )
    }

    private fun filterSpec(
        ownerId: UserId,
        tag: String?,
        title: String?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): Specification<PersonalExpense> = listOfNotNull(
        PersonalExpenseSpecifications.ownedBy(ownerId),
        tag?.trim()?.takeIf { it.isNotEmpty() }?.let(PersonalExpenseSpecifications::hasTag),
        title?.trim()?.takeIf { it.isNotEmpty() }?.let(PersonalExpenseSpecifications::titleContains),
        from?.toInstant()?.let(PersonalExpenseSpecifications::dateFrom),
        to?.toInstant()?.let(PersonalExpenseSpecifications::dateTo),
    ).fold(Specification.unrestricted()) { acc, next -> acc.and(next) }

    data class CurrencySum(
        val currency: CurrencyResponse,
        /** In the currency's minor units. */
        val total: Long,
    )

    /**
     * Sums of the expenses matching the same optional filters as the list; one
     * per currency, by code.
     */
    @GetMapping("/expenses/total")
    @Transactional(readOnly = true)
    fun total(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime?,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<CurrencySum> =
        expenses.findAll(filterSpec(jwt.userIdOrThrow(), tag, title, from, to))
            .groupBy { it.data.currency }
            .map { (currency, group) ->
                CurrencySum(
                    currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
                    total = group.sumOf { it.data.amount.toLong() },
                )
            }
            .sortedBy { it.currency.code }

    data class TagTotalResponse(
        val tag: String?,
        val tagColor: String?,
        val currency: CurrencyResponse,
        /** In the currency's minor units. */
        val total: Long,
    )

    /**
     * Sums of the user's expenses per currency and tag between [from] and
     * [to], both instants inclusive; largest first within a currency.
     */
    @GetMapping("/expenses/totals-by-tag")
    @Transactional(readOnly = true)
    fun totalsByTag(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<TagTotalResponse> {
        if (from.toInstant().isAfter(to.toInstant())) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "from must not be after to"
        )
        val ownerId = jwt.userIdOrThrow()
        val spec = PersonalExpenseSpecifications.ownedBy(ownerId)
            .and(PersonalExpenseSpecifications.dateFrom(from.toInstant()))
            .and(PersonalExpenseSpecifications.dateTo(to.toInstant()))
        val tagColors = tagColors(ownerId)
        return expenses.findAll(spec)
            .groupBy { it.data.currency to it.tagName }
            .map { (key, group) ->
                val (currency, tag) = key
                TagTotalResponse(
                    tag = tag,
                    tagColor = tag?.let(tagColors::get),
                    currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
                    total = group.sumOf { it.data.amount.toLong() },
                )
            }
            .sortedWith(
                compareBy<TagTotalResponse> { it.currency.code }
                    .thenByDescending { it.total }
                    .thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.tag }
            )
    }

    @GetMapping("/expenses/{expenseId}")
    @Transactional(readOnly = true)
    fun get(
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): PersonalExpenseResponse {
        val expense = expenses.findByIdAndOwnerIdOrThrow(expenseId, jwt.userIdOrThrow())
        val tag = findOrCreateTag(expense.ownerId, expense.tagName)
        return expense.toResponse(tag?.color)
    }

    @PutMapping("/expenses/{expenseId}")
    @Transactional
    fun update(
        @PathVariable expenseId: UUID,
        @Valid @RequestBody request: PersonalExpenseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): PersonalExpenseResponse {
        val ownerId = jwt.userIdOrThrow()
        val expense = expenses.findByIdAndOwnerIdOrThrow(expenseId, ownerId)
        val tag = findOrCreateTag(ownerId, request.tag)
        expense.update(request.toData(), tag)
        return expense.toResponse(tag?.color)
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        expenses.delete(expenses.findByIdAndOwnerIdOrThrow(expenseId, jwt.userIdOrThrow()))
    }

    /**
     * Tags the user has already used, including those carried over from their
     * project expenses, for the tag drop-down.
     */
    @GetMapping("/tags")
    @Transactional(readOnly = true)
    fun listTags(@AuthenticationPrincipal jwt: Jwt): List<String> =
        tags.findByIdOwnerIdOrderByIdNameAsc(jwt.userIdOrThrow()).map(PersonalExpenseTag::name)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /**
     * Reuses an existing tag of the owner (case-insensitive) or creates a new
     * one; a blank name means no tag.
     */
    private fun findOrCreateTag(ownerId: UserId, name: String?): PersonalExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findOrCreate(ownerId, trimmed, TagColors.random())
    }

    private fun PersonalExpenseRequest.toData() = ExpenseData(title, date, amount, currencyCode)

    /** Colors of the owner's tags by name. */
    private fun tagColors(ownerId: UserId): Map<String, String> =
        tags.findByIdOwnerIdOrderByIdNameAsc(ownerId).associate { it.name to it.color }

    private fun PersonalExpense.toResponse(
        tagColor: String?,
    ) = PersonalExpenseResponse(
        id = id,
        title = data.title,
        date = data.date,
        amount = data.amount,
        currency = CurrencyResponse(data.currency.currencyCode, data.currency.defaultFractionDigits),
        tag = tagName,
        tagColor = tagColor,
        source = when (this) {
            is ManualPersonalExpense -> Source.MANUAL
            is ProjectPersonalExpense -> Source.PROJECT
            else -> error("Unknown personal expense type ${this::class}")
        },
        projectId = (this as? ProjectPersonalExpense)?.projectId,
    )
}
