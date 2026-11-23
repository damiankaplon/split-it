package pl.damiankaplon.splitit.recognition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

/**
 * Answers every prompt with [answer] and remembers the prompts. Has the
 * options of the OpenAI model, since the ChatClient builds a prompt's options
 * from them and falls back to schema-in-prompt for options without
 * structured output support.
 */
private class FakeChatModel(
    private val answer: () -> String,
) : ChatModel {
    val prompts = mutableListOf<Prompt>()

    override fun getOptions(): ChatOptions = OpenAiChatOptions.builder().build()

    override fun call(prompt: Prompt): ChatResponse {
        prompts += prompt
        return ChatResponse(listOf(Generation(AssistantMessage(answer()))))
    }
}

class RecognitionTest {
    private fun controller(model: ChatModel) = RecognitionController(Recognizer(ChatClient.builder(model)))

    private fun picture(type: String = "image/jpeg") = MockMultipartFile("file", "receipt.jpg", type, byteArrayOf(1, 2, 3))

    @Test
    fun `returns every expense the model found, amounts with the currency's decimal places`() {
        val model =
            FakeChatModel {
                """
                {"expenses": [
                  {"title": "Lidl", "total": 123.5, "currency": "pln", "date": "2026-11-20"},
                  {"title": "Lidl", "total": 123.5, "currency": "PLN", "date": "2026-11-20"},
                  {"title": "Sushi", "total": 1500.0, "currency": "JPY", "date": "2026-11-21"}
                ]}
                """.trimIndent()
            }

        val expenses = controller(model).recognize(picture())

        assertThat(expenses).containsExactly(
            RecognitionController.RecognizedExpenseResponse(
                "Lidl",
                LocalDate.of(2026, 11, 20),
                "123.50",
                RecognitionController.CurrencyResponse("PLN", 2),
            ),
            RecognitionController.RecognizedExpenseResponse(
                "Lidl",
                LocalDate.of(2026, 11, 20),
                "123.50",
                RecognitionController.CurrencyResponse("PLN", 2),
            ),
            RecognitionController.RecognizedExpenseResponse(
                "Sushi",
                LocalDate.of(2026, 11, 21),
                "1500",
                RecognitionController.CurrencyResponse("JPY", 0),
            ),
        )
    }

    @Test
    fun `sends the picture to the model`() {
        val model = FakeChatModel { """{"expenses": []}""" }

        controller(model).recognize(picture("image/png"))

        val user =
            model.prompts
                .single()
                .instructions
                .filterIsInstance<UserMessage>()
                .single()
        assertThat(user.media).singleElement().satisfies({
            assertThat(it.mimeType.toString()).isEqualTo("image/png")
            assertThat(it.dataAsByteArray).containsExactly(1, 2, 3)
        })
    }

    private fun FakeChatModel.userMessage() =
        prompts
            .single()
            .instructions
            .filterIsInstance<UserMessage>()
            .single()

    @Test
    fun `sends a PDF to the model as a named file`() {
        val model = FakeChatModel { """{"expenses": []}""" }

        controller(model).recognize(MockMultipartFile("file", "statement", "application/pdf", byteArrayOf(1, 2)))

        assertThat(model.userMessage().media).singleElement().satisfies({
            assertThat(it.mimeType.toString()).isEqualTo("application/pdf")
            assertThat(it.name).isEqualTo("statement.pdf")
            assertThat(it.dataAsByteArray).containsExactly(1, 2)
        })
    }

    @Test
    fun `a PDF is told by its name when the browser sends no type`() {
        val model = FakeChatModel { """{"expenses": []}""" }

        controller(model).recognize(MockMultipartFile("file", "invoice.PDF", "application/octet-stream", byteArrayOf(1)))

        assertThat(
            model
                .userMessage()
                .media
                .single()
                .mimeType
                .toString(),
        ).isEqualTo("application/pdf")
    }

    @Test
    fun `sends a CSV to the model as text, braces included`() {
        val model = FakeChatModel { """{"expenses": []}""" }
        val csv = "Date;Description;Amount\n2026-11-02;Shop {online};-12,50\n"

        // what browsers on Windows send for .csv files
        controller(model).recognize(MockMultipartFile("file", "mbank.csv", "application/vnd.ms-excel", csv.toByteArray()))

        val user = model.userMessage()
        assertThat(user.media).isEmpty()
        assertThat(user.text).contains("mbank.csv").contains(csv)
    }

    @Test
    fun `a CSV that is not UTF-8 is read as Windows-1250`() {
        val model = FakeChatModel { """{"expenses": []}""" }
        val csv = "Opis;Kwota\nŻabka Łódź;-12,50\n"

        controller(model).recognize(MockMultipartFile("file", "pko.csv", "text/csv", csv.toByteArray(charset("windows-1250"))))

        assertThat(model.userMessage().text).contains("Żabka Łódź")
    }

    @Test
    fun `CSVs with a byte order mark lose it`() {
        assertThat(decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Żabka".toByteArray()))
            .isEqualTo("Żabka")
        assertThat(decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Żabka".toByteArray(Charsets.UTF_16LE)))
            .isEqualTo("Żabka")
    }

    @Test
    fun `a CSV over 1 MB is too large`() {
        val model = FakeChatModel { """{"expenses": []}""" }
        val csv = MockMultipartFile("file", "statement.csv", "text/csv", ByteArray(1024 * 1024 + 1) { 'a'.code.toByte() })

        assertThatThrownBy { controller(model).recognize(csv) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
            }
        assertThat(model.prompts).isEmpty()
    }

    @Test
    fun `asks OpenAI for a strict structured output`() {
        val model = FakeChatModel { """{"expenses": []}""" }

        controller(model).recognize(picture())

        val options = model.prompts.single().options as OpenAiChatOptions
        assertThat(options.strict).isTrue()
        assertStrictSchema(JsonMapper().readTree(options.outputSchema))
    }

    /** OpenAI strict mode: every object lists all its properties as required and allows no others. */
    private fun assertStrictSchema(node: JsonNode) {
        if (node.isObject) {
            node.get("properties")?.let { properties ->
                val required: Set<String> =
                    node
                        .get("required")
                        .values()
                        .map { it.asString() }
                        .toSet()
                assertThat(required).isEqualTo(properties.propertyNames().toSet())
                assertThat(node.get("additionalProperties").asBoolean(true)).isFalse()
            }
            node.values().forEach(::assertStrictSchema)
        } else if (node.isArray) {
            node.forEach(::assertStrictSchema)
        }
    }

    @Test
    fun `partially recognized expenses are returned with what was recognized`() {
        val model =
            FakeChatModel {
                """
                {"expenses": [
                  {"title": "Only title", "total": null, "currency": null, "date": null},
                  {"title": null, "total": null, "currency": null, "date": "2026-11-02"},
                  {"title": null, "total": 9.99, "currency": null, "date": null},
                  {"title": null, "total": null, "currency": "EUR", "date": null}
                ]}
                """.trimIndent()
            }

        val expenses = controller(model).recognize(picture())

        assertThat(expenses).containsExactly(
            RecognitionController.RecognizedExpenseResponse("Only title", null, null, null),
            RecognitionController.RecognizedExpenseResponse(null, LocalDate.of(2026, 11, 2), null, null),
            RecognitionController.RecognizedExpenseResponse(null, null, "9.99", null),
            RecognitionController.RecognizedExpenseResponse(
                null,
                null,
                null,
                RecognitionController.CurrencyResponse("EUR", 2),
            ),
        )
    }

    @Test
    fun `unusable details count as not recognized`() {
        val model =
            FakeChatModel {
                """
                {"expenses": [
                  {"title": " ", "total": 5.005, "currency": "EUR", "date": null},
                  {"title": "Pseudo-currency", "total": 9.99, "currency": "XXX", "date": null},
                  {"title": "Unknown code", "total": 9.99, "currency": "XYZ", "date": null},
                  {"title": "Free", "total": 0, "currency": "EUR", "date": null},
                  {"title": "Unflipped", "total": -12.50, "currency": "EUR", "date": null},
                  {"title": "", "total": 0, "currency": "XXX", "date": null}
                ]}
                """.trimIndent()
            }

        val expenses = controller(model).recognize(picture())

        assertThat(expenses).containsExactly(
            RecognitionController.RecognizedExpenseResponse(
                null,
                null,
                "5.01",
                RecognitionController.CurrencyResponse("EUR", 2),
            ),
            RecognitionController.RecognizedExpenseResponse("Pseudo-currency", null, "9.99", null),
            RecognitionController.RecognizedExpenseResponse("Unknown code", null, "9.99", null),
            RecognitionController.RecognizedExpenseResponse(
                "Free",
                null,
                null,
                RecognitionController.CurrencyResponse("EUR", 2),
            ),
            RecognitionController.RecognizedExpenseResponse(
                "Unflipped",
                null,
                "12.50",
                RecognitionController.CurrencyResponse("EUR", 2),
            ),
        )
    }

    @Test
    fun `no expenses on the picture is an empty list`() {
        assertThat(controller(FakeChatModel { """{"expenses": []}""" }).recognize(picture())).isEmpty()
    }

    @Test
    fun `a failing model is a bad gateway`() {
        val model = FakeChatModel { throw IllegalStateException("401 Unauthorized") }

        assertThatThrownBy { controller(model).recognize(picture()) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
            }
    }

    @Test
    fun `only images, PDFs and CSVs are recognized`() {
        val model = FakeChatModel { """{"expenses": []}""" }
        val spreadsheet =
            MockMultipartFile(
                "file",
                "statement.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                byteArrayOf(1),
            )

        assertThatThrownBy { controller(model).recognize(spreadsheet) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            }
        assertThat(model.prompts).isEmpty()
    }

    @Test
    fun `an empty file is a bad request`() {
        val empty = MockMultipartFile("file", "receipt.jpg", "image/jpeg", ByteArray(0))

        assertThatThrownBy { controller(FakeChatModel { "" }).recognize(empty) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }
}
