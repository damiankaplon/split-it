package pl.damiankaplon.splitit.recognition

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.MimeType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.Currency

private val log = KotlinLogging.logger {}

/** Image types the vision model accepts. */
private val SUPPORTED_IMAGE_TYPES =
    setOf(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.parseMediaType("image/webp"))

/**
 * Content types browsers send for `.csv` files; Windows ones often send
 * `application/vnd.ms-excel`, so the file name decides as well.
 */
private val CSV_TYPES =
    setOf(MediaType.parseMediaType("text/csv"), MediaType.parseMediaType("application/csv"))

/**
 * A CSV is sent to the model as text, so its size is bounded by the model's
 * context rather than by the upload limit; 1 MB is roughly 250k tokens,
 * years of transactions of a personal account.
 */
private const val MAX_CSV_BYTES = 1024 * 1024

/**
 * Recognizes expenses in a file: a photo of receipts or invoices, a banking
 * app screenshot, an invoice or a bank statement as PDF, or a bank
 * statement exported as CSV. The result is a suggestion for the user to
 * review; nothing is saved.
 */
@RestController
@RequestMapping("/api/recognition")
class RecognitionController(
    private val recognizer: Recognizer,
) {
    data class CurrencyResponse(
        val code: String,
        val minorUnits: Int,
    )

    /**
     * An expense as far as it was recognized: any detail may be null, but
     * not all of them. The user completes or drops it.
     */
    data class RecognizedExpenseResponse(
        /** Merchant or payee. */
        val title: String?,
        /** Date of the expense. */
        val date: LocalDate?,
        /**
         * Positive decimal with a dot, e.g. `"1234.56"`, rather than minor
         * units like the other endpoints: those depend on the currency, which
         * may be unknown. With the currency's decimal places when it is known.
         */
        val amount: String?,
        val currency: CurrencyResponse?,
    )

    /**
     * One element per expense found in [file]: per receipt, invoice or
     * outgoing transaction. Empty when the file shows none.
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun recognize(
        @RequestParam file: MultipartFile,
    ): List<RecognizedExpenseResponse> {
        if (file.isEmpty) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty")
        val input = file.toRecognitionInput()
        val expenses =
            try {
                recognizer.recognize(input)
            } catch (e: Exception) {
                log.warn(e) { "Expense recognition failed" }
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Expense recognition failed")
            }
        return expenses.map { it.toResponse() }
    }

    private fun MultipartFile.toRecognitionInput(): RecognitionInput {
        val type = contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
        val name = originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
        val extension = name?.substringAfterLast('.', "")?.lowercase()
        return when {
            type != null && SUPPORTED_IMAGE_TYPES.any { it.equalsTypeAndSubtype(type) } -> {
                RecognitionInput.Document(bytes, type, name ?: "upload")
            }

            type?.equalsTypeAndSubtype(MediaType.APPLICATION_PDF) == true || extension == "pdf" -> {
                RecognitionInput.Document(
                    bytes,
                    MediaType.APPLICATION_PDF,
                    // OpenAI tells the file type by its name
                    name?.takeIf { extension == "pdf" } ?: "${name ?: "upload"}.pdf",
                )
            }

            CSV_TYPES.any { it.equalsTypeAndSubtype(type) } || extension == "csv" -> {
                if (size > MAX_CSV_BYTES) {
                    throw ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "CSV files are limited to 1 MB")
                }
                RecognitionInput.Table(decodeText(bytes), name ?: "statement.csv")
            }

            else -> {
                throw ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Supported files are JPEG, PNG, GIF and WEBP images, PDFs and CSVs",
                )
            }
        }
    }

    private fun RecognizedExpense.toResponse() =
        RecognizedExpenseResponse(
            title = title,
            date = date,
            amount = amount?.toPlainString(),
            currency = currency?.let { CurrencyResponse(it.currencyCode, it.defaultFractionDigits) },
        )
}

/** A file to recognize expenses in. */
sealed interface RecognitionInput {
    /** An image or a PDF, which the model reads itself. */
    class Document(
        val content: ByteArray,
        val type: MimeType,
        val name: String,
    ) : RecognitionInput

    /** A CSV export, which the model gets as text. */
    class Table(
        val content: String,
        val name: String,
    ) : RecognitionInput
}

/**
 * Decodes an uploaded text file. Banks export CSVs as UTF-8 (with or without
 * a BOM), UTF-16 with a BOM, or in the legacy Windows code page of their
 * country; text that is not valid UTF-8 is taken as Windows-1250 (Central
 * European, used by Polish banks).
 */
internal fun decodeText(bytes: ByteArray): String {
    fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
    return when {
        startsWith(0xEF, 0xBB, 0xBF) -> {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        startsWith(0xFF, 0xFE) -> {
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }

        startsWith(0xFE, 0xFF) -> {
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        else -> {
            try {
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1250"))
            }
        }
    }
}

/**
 * An expense as far as it was recognized; any of its details may be
 * missing, but not all of them.
 *
 * @param amount - positive; with [currency]'s number of decimal places
 * when it is known
 */
data class RecognizedExpense(
    val title: String?,
    val date: LocalDate?,
    val amount: BigDecimal?,
    val currency: Currency?,
)

/**
 * Finds expenses in a file with a single call to a model, which both tells
 * the documents or transactions apart and reads their amounts.
 */
@Component
class Recognizer(
    chatClientBuilder: ChatClient.Builder,
) {
    /**
     * What the model answers with, enforced by OpenAI Structured Outputs.
     * Strict mode requires every property to be required; a value the model
     * may not know is nullable, and marked required explicitly, since the
     * schema would leave nullable Kotlin properties out of `required`.
     */
    private data class ModelAnswer(
        val expenses: List<ModelExpense>,
    )

    data class ModelExpense(
        @get:JsonProperty(required = true) val title: String?,
        @get:JsonProperty(required = true) val total: BigDecimal?,
        @get:JsonProperty(required = true) val currency: String?,
        @get:JsonProperty(required = true) val date: LocalDate?,
    )

    private val chatClient =
        chatClientBuilder
            .defaultSystem(INSTRUCTIONS)
            .defaultOptions(OpenAiChatOptions.builder().strict(true))
            .build()

    /** Expenses in the order they appear in the file; may repeat. */
    fun recognize(input: RecognitionInput): List<RecognizedExpense> {
        // A message rather than the user text spec, which would treat braces in a CSV as
        // template placeholders
        val message =
            when (input) {
                is RecognitionInput.Document -> {
                    UserMessage
                        .builder()
                        .text("List the expenses in this file.")
                        .media(
                            Media
                                .builder()
                                .mimeType(input.type)
                                .data(input.content)
                                .name(input.name)
                                .build(),
                        ).build()
                }

                is RecognitionInput.Table -> {
                    UserMessage
                        .builder()
                        .text("List the expenses in this CSV file, \"${input.name}\":\n\n${input.content}")
                        .build()
                }
            }
        val answer =
            chatClient
                .prompt()
                .messages(message)
                .call()
                .entity(ModelAnswer::class.java) { it.useProviderStructuredOutput() }
        return answer?.expenses.orEmpty().mapNotNull { it.toRecognizedExpense() }
    }

    /**
     * Keeps whatever part of the expense was recognized; a detail that is
     * unusable (a blank title, a zero total, a code that is not a currency
     * with minor units) counts as not recognized. Null only when nothing
     * was.
     */
    private fun ModelExpense.toRecognizedExpense(): RecognizedExpense? {
        val currency =
            currency
                ?.let { code -> runCatching { Currency.getInstance(code.trim().uppercase()) }.getOrNull() }
                ?.takeIf { it.defaultFractionDigits >= 0 }
        // Asked for a positive amount; a negative one is an outgoing amount left unflipped
        val amount =
            total
                ?.abs()
                ?.takeIf { it.signum() > 0 }
                ?.let { if (currency != null) it.setScale(currency.defaultFractionDigits, RoundingMode.HALF_UP) else it }
        val expense =
            RecognizedExpense(
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                date = date,
                amount = amount,
                currency = currency,
            )
        return expense.takeUnless { it.title == null && it.date == null && it.amount == null && it.currency == null }
    }

    private companion object {
        val INSTRUCTIONS =
            """
            You extract expenses from a file for an expense-splitting app. The file may be:
            - a picture of receipts, invoices, bills or banking or payment app screenshots,
              possibly several at once, photographed at an angle or partly cut off;
            - a PDF of an invoice, a bill or a bank statement;
            - a bank statement exported as CSV, in the format of any bank and in any
              language; work out its columns from its header and rows.

            Ignore income records.

            Return one expense per payment the file shows:
            - A receipt, invoice or bill is one expense: its final total actually paid, after
              discounts and including tax and tip. Never return its line items, subtotals, tax
              lines, change given or amount tendered as separate expenses.
            - Several receipts or invoices in one file are several expenses, one each, even
              when they are identical.
            - A bank statement or banking app screenshot shows one expense per outgoing
              transaction: card payment, transfer sent, direct debit, withdrawal, bank fee.
              Return every one of them for the whole period the statement covers, in the
              order they are listed. Outgoing amounts are often negative or in a debit
              column; return them as positive. Skip incoming money, refunds, opening and
              closing balances, limits, blocked amounts or pending authorisations, and
              transfers between the account holder's own accounts.
            - If a receipt and a card terminal slip for the same purchase are both shown, they
              are one expense.

            For every expense give:
            - title: short name of the merchant or payee, e.g. "Lidl" or "Electricity bill";
              in the language printed on the document. For bank transactions take it from the
              description or counterparty, not the transaction type.
            - total: the amount as a positive decimal number with a dot as the decimal
              separator, e.g. 1234.56 for "1 234,56 zł". For a bank transaction in a foreign
              currency, the amount charged to the account, in the account's currency.
            - currency: its ISO 4217 code, e.g. PLN for "zł", EUR for "€". Infer it from the
              symbol, the account, the country or the language when it is not printed; null
              if you cannot tell.
            - date: the date of the payment as YYYY-MM-DD; for bank transactions the
              transaction date rather than the booking date. Null if the file shows none.

            Return every expense you can make out, even when you can read only some of its
            details, e.g. only the title or only the amount: give null for each detail you
            cannot read or infer, and never guess one. If the file shows no expense at all,
            return an empty list.
            """.trimIndent()
    }
}
