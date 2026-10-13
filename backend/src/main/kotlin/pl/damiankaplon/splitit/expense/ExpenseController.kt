package pl.damiankaplon.splitit.expense

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.UserId
import pl.damiankaplon.splitit.project.ProjectRepository
import pl.damiankaplon.splitit.subjectOrThrow
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/projects/{projectId}")
class ExpenseController(
    private val expenses: ExpenseRepository,
    private val tags: ExpenseTagRepository,
    private val projects: ProjectRepository,
    private val expenseService: ExpenseService,
) {

    data class ExpenseRequest(
        @field:NotBlank val title: String,
        val date: LocalDateTime,
        @field:Positive val amount: Int,
        val tag: String? = null,
    )

    data class ExpenseResponse(
        val id: UUID,
        val projectId: UUID,
        val title: String,
        val date: LocalDateTime,
        val amount: Int,
        val tag: String?,
        val tagColor: String?,
        val createdBy: String,
    )

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: ExpenseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpenseResponse {
        val userId = requireMember(projectId, jwt)
        return expenseService.create(projectId, userId, request.toDetails()).toResponse()
    }

    data class ExpensePage(
        val items: List<ExpenseResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean,
    )

    @GetMapping("/expenses")
    @Transactional(readOnly = true)
    fun list(
        @PathVariable projectId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) createdBy: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpensePage {
        requireMember(projectId, jwt)
        if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative")
        if (size !in 1..MAX_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and $MAX_PAGE_SIZE")
        }
        val spec = listOfNotNull(
            ExpenseSpecifications.inProject(projectId),
            tag?.trim()?.takeIf { it.isNotEmpty() }?.let(ExpenseSpecifications::hasTag),
            title?.trim()?.takeIf { it.isNotEmpty() }?.let(ExpenseSpecifications::titleContains),
            createdBy?.trim()?.takeIf { it.isNotEmpty() }?.let(ExpenseSpecifications::createdBy),
            from?.let(ExpenseSpecifications::dateFrom),
            to?.let(ExpenseSpecifications::dateTo),
        ).fold(Specification.unrestricted<Expense>()) { acc, next -> acc.and(next) }
        val pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")))
        val result = expenses.findAll(spec, pageRequest)
        return ExpensePage(
            items = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext(),
        )
    }

    data class ExpenseTotal(val total: Long)

    /**
     * Sum of all the project's expenses in minor units; a Long since the sum
     * can exceed an Int.
     */
    @GetMapping("/expenses/total")
    @Transactional(readOnly = true)
    fun total(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpenseTotal {
        requireMember(projectId, jwt)
        return ExpenseTotal(expenses.sumAmountByProjectId(projectId))
    }

    @GetMapping("/expenses/{expenseId}")
    @Transactional(readOnly = true)
    fun get(
        @PathVariable projectId: UUID,
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpenseResponse {
        requireMember(projectId, jwt)
        return expenses.findByIdAndProjectIdOrThrow(expenseId, projectId).toResponse()
    }

    @PutMapping("/expenses/{expenseId}")
    @Transactional
    fun update(
        @PathVariable projectId: UUID,
        @PathVariable expenseId: UUID,
        @Valid @RequestBody request: ExpenseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpenseResponse {
        requireMember(projectId, jwt)
        return expenseService.update(projectId, expenseId, request.toDetails()).toResponse()
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(
        @PathVariable projectId: UUID,
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        requireMember(projectId, jwt)
        expenseService.delete(projectId, expenseId)
    }

    /** Tags already used in the project, for the tag drop-down. */
    @GetMapping("/tags")
    @Transactional(readOnly = true)
    fun listTags(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<String> {
        requireMember(projectId, jwt)
        return tags.findByProjectIdOrderByNameAsc(projectId).map(ExpenseTag::name)
    }

    private fun requireMember(projectId: UUID, jwt: Jwt): UserId {
        val userId = jwt.subjectOrThrow()
        if (!projects.existsByIdAndMemberUserId(projectId, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=$projectId member")
        }
        return UserId(userId)
    }

    private fun ExpenseRequest.toDetails() = ExpenseService.ExpenseDetails(title, date, amount, tag)

    private fun Expense.toResponse() =
        ExpenseResponse(id, projectId, title, date, amount, tag?.name, tag?.color, createdBy)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
