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
import pl.damiankaplon.splitit.UserId
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
    private val shares: ProjectPersonalExpenseRepository,
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
     * Where a personal expense comes from; only [MANUAL] ones can be changed
     * here.
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
        return ManualPersonalExpense(
            ownerId = ownerId,
            title = request.title,
            date = request.date,
            amount = request.amount,
            currency = request.currencyCode,
            personalTag = findOrCreateTag(ownerId, request.tag),
        ).let(expenses::save).toResponse()
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
        val spec =
            filterSpec(jwt.userIdOrThrow(), tag, title, from, to).and(PersonalExpenseSpecifications.newestFirst())
        val result = expenses.findAll(spec, PageRequest.of(page, size))
        return PersonalExpensePage(
            items = result.content.map { it.toResponse() },
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
            .groupBy { it.currency }
            .map { (currency, group) ->
                CurrencySum(
                    currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
                    total = group.sumOf { it.amount.toLong() },
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
     * [to], both instants inclusive; largest first within a currency. Tags
     * match case-insensitively, since a manual and a project tag may share a
     * name.
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
        val spec = PersonalExpenseSpecifications.ownedBy(jwt.userIdOrThrow())
            .and(PersonalExpenseSpecifications.dateFrom(from.toInstant()))
            .and(PersonalExpenseSpecifications.dateTo(to.toInstant()))
        return expenses.findAll(spec)
            .groupBy { it.currency to it.tag?.lowercase() }
            .map { (key, group) ->
                val (currency) = key
                TagTotalResponse(
                    tag = group.first().tag,
                    tagColor = group.first().tagColor,
                    currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
                    total = group.sumOf { it.amount.toLong() },
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
    ): PersonalExpenseResponse =
        expenses.findByIdAndOwnerIdOrThrow(expenseId, jwt.userIdOrThrow()).toResponse()

    @PutMapping("/expenses/{expenseId}")
    @Transactional
    fun update(
        @PathVariable expenseId: UUID,
        @Valid @RequestBody request: PersonalExpenseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): PersonalExpenseResponse {
        val ownerId = jwt.userIdOrThrow()
        val expense = findManualOrThrow(expenseId, ownerId)
        expense.title = request.title
        expense.date = request.date
        expense.amount = request.amount
        expense.currency = request.currencyCode
        expense.personalTag = findOrCreateTag(ownerId, request.tag)
        return expense.toResponse()
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        expenses.delete(findManualOrThrow(expenseId, jwt.userIdOrThrow()))
    }

    /**
     * Tags the user has already used, including those of their project
     * expenses, for the tag drop-down.
     */
    @GetMapping("/tags")
    @Transactional(readOnly = true)
    fun listTags(@AuthenticationPrincipal jwt: Jwt): List<String> {
        val ownerId = jwt.userIdOrThrow()
        val personalTags = tags.findByOwnerIdOrderByNameAsc(ownerId).map(PersonalExpenseTag::name)
        return (personalTags + shares.findTagNamesByOwnerId(ownerId))
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Project shares follow their project expense, so they can only be changed
     * through it.
     */
    private fun findManualOrThrow(expenseId: UUID, ownerId: UserId): ManualPersonalExpense =
        when (val expense = expenses.findByIdAndOwnerIdOrThrow(expenseId, ownerId)) {
            is ManualPersonalExpense -> expense
            else -> throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Personal expense=$expenseId is a share of a project expense; change the project expense instead",
            )
        }

    /**
     * Reuses an existing tag of the owner (case-insensitive) or creates a new
     * one; a blank name means no tag.
     */
    private fun findOrCreateTag(ownerId: UserId, name: String?): PersonalExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findByOwnerIdAndNameIgnoreCase(ownerId, trimmed)
            ?: tags.save(PersonalExpenseTag(ownerId, trimmed))
    }

    private fun PersonalExpense.toResponse() = PersonalExpenseResponse(
        id = id,
        title = title,
        date = date,
        amount = amount,
        currency = CurrencyResponse(currency.currencyCode, currency.defaultFractionDigits),
        tag = tag,
        tagColor = tagColor,
        source = when (this) {
            is ManualPersonalExpense -> Source.MANUAL
            is ProjectPersonalExpense -> Source.PROJECT
            else -> error("Unknown personal expense type ${this::class}")
        },
        projectId = (this as? ProjectPersonalExpense)?.projectId,
    )
}
