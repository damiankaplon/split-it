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
import pl.damiankaplon.splitit.project.ProjectMemberRepository
import pl.damiankaplon.splitit.subjectOrThrow
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/projects/{projectId}")
class ExpenseController(
    private val expenses: ExpenseRepository,
    private val tags: ExpenseTagRepository,
    private val projectMembers: ProjectMemberRepository,
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
        val expense = Expense(
            projectId = projectId,
            title = request.title,
            date = request.date,
            amount = request.amount,
            tag = findOrCreateTag(projectId, request.tag),
            createdBy = userId,
        )
        return expenses.save(expense).toResponse()
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

    @GetMapping("/expenses/{expenseId}")
    @Transactional(readOnly = true)
    fun get(
        @PathVariable projectId: UUID,
        @PathVariable expenseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ExpenseResponse {
        requireMember(projectId, jwt)
        return findExpenseOrThrow(projectId, expenseId).toResponse()
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
        val expense = findExpenseOrThrow(projectId, expenseId)
        expense.title = request.title
        expense.date = request.date
        expense.amount = request.amount
        expense.tag = findOrCreateTag(projectId, request.tag)
        return expense.toResponse()
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
        expenses.delete(findExpenseOrThrow(projectId, expenseId))
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

    private fun requireMember(projectId: UUID, jwt: Jwt): String {
        val userId = jwt.subjectOrThrow()
        if (!projectMembers.existsByProjectIdAndUserId(projectId, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not project=$projectId member")
        }
        return userId
    }

    private fun findExpenseOrThrow(projectId: UUID, expenseId: UUID): Expense =
        expenses.findByIdAndProjectId(expenseId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Expense=$expenseId not found in project=$projectId")

    /** Reuses an existing project tag (case-insensitive) or creates a new one; a blank name means no tag. */
    private fun findOrCreateTag(projectId: UUID, name: String?): ExpenseTag? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tags.findByProjectIdAndNameIgnoreCase(projectId, trimmed)
            ?: tags.save(ExpenseTag(projectId, trimmed))
    }

    private fun Expense.toResponse() = ExpenseResponse(id, projectId, title, date, amount, tag?.name, createdBy)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
