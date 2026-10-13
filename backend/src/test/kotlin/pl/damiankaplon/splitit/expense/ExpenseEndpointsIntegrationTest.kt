package pl.damiankaplon.splitit.expense

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.project.Project

class ExpenseEndpointsIntegrationTest @Autowired constructor(
    private val expenses: ExpenseRepository,
) : RollbackIntegrationTest() {

    private fun projectWithMember(userId: String): Project = testData.createProject(userId)

    @Test
    fun `member creates and reads an expense`() {
        val project = projectWithMember("alice-id")

        val expenseId = mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":12050,"tag":"Food"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString.let(::idOf)

        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Dinner") }
                jsonPath("$.date") { value("2026-10-03T19:30:00") }
                jsonPath("$.amount") { value(12050) }
                jsonPath("$.tag") { value("Food") }
                jsonPath("$.createdBy") { value("alice-id") }
            }
    }

    @Test
    fun `tag is optional`() {
        val project = projectWithMember("alice-id")

        val expenseId = mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Taxi","date":"2026-10-03T10:00:00","amount":3000}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString.let(::idOf)

        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.tag") { doesNotExist() } }
    }

    @Test
    fun `invalid expense is rejected`() {
        val project = projectWithMember("alice-id")

        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":" ","date":"2026-10-03T10:00:00","amount":3000}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser("alice-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Taxi","amount":3000}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `expenses are listed newest first`() {
        val project = projectWithMember("alice-id")
        testData.createExpense(project, "alice-id", "Older", "2026-10-01T10:00:00", 100)
        testData.createExpense(project, "alice-id", "Newer", "2026-10-02T10:00:00", 200)

        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[0].title") { value("Newer") }
                jsonPath("$.items[1].title") { value("Older") }
                jsonPath("$.page") { value(0) }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.hasNext") { value(false) }
            }
    }

    @Test
    fun `pages walk the whole list without gaps or duplicates`() {
        val project = projectWithMember("alice-id")
        // Two expenses share a date so the id tie-breaker is exercised across a page boundary
        val dates = listOf("2026-10-05", "2026-10-04", "2026-10-04", "2026-10-03", "2026-10-02")
        dates.forEachIndexed { i, date ->
            testData.createExpense(project, "alice-id", "E$i", "${date}T10:00:00", 1)
        }

        val seen = (0..2).flatMap { page ->
            val body = mockMvc.get("/projects/${project.id}/expenses") {
                with(asUser("alice-id"))
                param("page", "$page")
                param("size", "2")
            }.andExpect {
                status { isOk() }
                jsonPath("$.page") { value(page) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
                jsonPath("$.hasNext") { value(page < 2) }
            }.andReturn().response.contentAsString
            Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        }

        assertEquals(5, seen.toSet().size)
        assertEquals(listOf("E0", "E3", "E4"), seen.filter { it in setOf("E0", "E3", "E4") })
        assertEquals(setOf("E1", "E2"), seen.subList(1, 3).toSet())
    }

    @Test
    fun `invalid paging parameters are rejected`() {
        val project = projectWithMember("alice-id")

        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("page", "-1") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("size", "0") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")); param("size", "101") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list filters are optional and combined`() {
        val project = projectWithMember("alice-id")
        testData.createExpense(project, "alice-id", "Pizza dinner", "2026-10-01T19:00:00", 1, "Food")
        testData.createExpense(project, "alice-id", "Pizza lunch", "2026-10-03T12:00:00", 1, "Food")
        testData.createExpense(project, "alice-id", "Museum", "2026-10-02T10:00:00", 1, "Culture")
        testData.createExpense(project, "alice-id", "Taxi 100%", "2026-10-02T11:00:00", 1)

        fun titles(vararg params: Pair<String, String>): List<String> {
            val body = mockMvc.get("/projects/${project.id}/expenses") {
                with(asUser("alice-id"))
                params.forEach { (k, v) -> param(k, v) }
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            return Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        }

        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles("tag" to "food"))
        assertEquals(listOf("Pizza lunch", "Pizza dinner"), titles("title" to "PIZZA"))
        // LIKE wildcards in the input are matched literally
        assertEquals(listOf("Taxi 100%"), titles("title" to "%"))
        assertEquals(emptyList<String>(), titles("title" to "_"))
        assertEquals(
            listOf("Taxi 100%", "Museum"),
            titles("from" to "2026-10-02T00:00:00", "to" to "2026-10-02T23:59:59"),
        )
        assertEquals(
            listOf("Pizza lunch"),
            titles("tag" to "Food", "from" to "2026-10-02T00:00:00"),
        )
        assertEquals(4, titles().size)
    }

    @Test
    fun `tags are reused case-insensitively and listed for the drop-down`() {
        val project = projectWithMember("alice-id")
        testData.createExpense(project, "alice-id", "A", "2026-10-01T10:00:00", 1, "Food")
        testData.createExpense(project, "alice-id", "B", "2026-10-01T10:00:00", 1, " food ")
        testData.createExpense(project, "alice-id", "C", "2026-10-01T10:00:00", 1, "Bars")

        mockMvc.get("/projects/${project.id}/tags") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0]") { value("Bars") }
                jsonPath("$[1]") { value("Food") }
            }
        mockMvc.get("/projects/${project.id}/expenses") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.items[?(@.title == 'B')].tag") { value("Food") } }
    }

    @Test
    fun `tags are scoped to the project`() {
        val rome = projectWithMember("alice-id")
        val paris = projectWithMember("alice-id")
        testData.createExpense(rome, "alice-id", "A", "2026-10-01T10:00:00", 1, "Food")

        mockMvc.get("/projects/${paris.id}/tags") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `any member updates an expense`() {
        val project = projectWithMember("alice-id")
        testData.addMember(project, "bob-id", "bob")
        val expenseId = testData.createExpense(project, "alice-id", "Dinner", "2026-10-03T19:30:00", 100, "Food")

        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(asUser("bob-id"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Lunch","date":"2026-10-04T12:00:00","amount":250}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Lunch") }
            jsonPath("$.date") { value("2026-10-04T12:00:00") }
            jsonPath("$.amount") { value(250) }
            jsonPath("$.tag") { doesNotExist() }
        }

        // The previously used tag stays available in the drop-down
        mockMvc.get("/projects/${project.id}/tags") { with(asUser("bob-id")) }
            .andExpect { jsonPath("$[0]") { value("Food") } }
    }

    @Test
    fun `total sums all expenses of the project only`() {
        val project = projectWithMember("alice-id")
        val other = projectWithMember("alice-id")

        mockMvc.get("/projects/${project.id}/expenses/total") { with(asUser("alice-id")) }
            .andExpect { jsonPath("$.total") { value(0) } }

        testData.createExpense(project, "alice-id", "A", "2026-10-01T10:00:00", 2000000000)
        testData.createExpense(project, "alice-id", "B", "2026-10-02T10:00:00", 2000000000, "Food")
        testData.createExpense(other, "alice-id", "C", "2026-10-02T10:00:00", 5)

        mockMvc.get("/projects/${project.id}/expenses/total") { with(asUser("alice-id")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(4000000000) }
            }
        mockMvc.get("/projects/${project.id}/expenses/total") { with(asUser("stranger-id")) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `totals by tag sum the expenses within the inclusive day range, largest first`() {
        val project = projectWithMember("alice-id")
        val other = projectWithMember("alice-id")
        testData.createExpense(project, "alice-id", "Before", "2026-09-30T23:59:59", 1000, "Food")
        testData.createExpense(project, "alice-id", "Pizza", "2026-10-01T00:00:00", 100, "Food")
        testData.createExpense(project, "alice-id", "Pasta", "2026-10-31T23:59:59", 200, "food")
        testData.createExpense(project, "alice-id", "Museum", "2026-10-15T10:00:00", 500, "Culture")
        testData.createExpense(project, "alice-id", "Taxi", "2026-10-15T10:00:00", 50)
        testData.createExpense(project, "alice-id", "After", "2026-11-01T00:00:00", 1000, "Food")
        testData.createExpense(other, "alice-id", "Elsewhere", "2026-10-15T10:00:00", 1000, "Food")

        mockMvc.get("/projects/${project.id}/expenses/totals-by-tag") {
            with(asUser("alice-id"))
            param("from", "2026-10-01")
            param("to", "2026-10-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].tag") { value("Culture") }
            jsonPath("$[0].total") { value(500) }
            jsonPath("$[0].tagColor") { exists() }
            jsonPath("$[1].tag") { value("Food") }
            jsonPath("$[1].total") { value(300) }
            jsonPath("$[2].tag") { doesNotExist() }
            jsonPath("$[2].total") { value(50) }
        }
        mockMvc.get("/projects/${project.id}/expenses/totals-by-tag") {
            with(asUser("alice-id"))
            param("from", "2026-10-02")
            param("to", "2026-10-01")
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/projects/${project.id}/expenses/totals-by-tag") {
            with(asUser("stranger-id"))
            param("from", "2026-10-01")
            param("to", "2026-10-31")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `member deletes an expense`() {
        val project = projectWithMember("alice-id")
        val expenseId = testData.createExpense(project, "alice-id", "A", "2026-10-01T10:00:00", 1)

        mockMvc.delete("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNoContent() } }

        assertFalse(expenses.existsById(expenseId))
        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `expense of another project is not found`() {
        val rome = projectWithMember("alice-id")
        val paris = projectWithMember("alice-id")
        val expenseId = testData.createExpense(rome, "alice-id", "A", "2026-10-01T10:00:00", 1)

        mockMvc.get("/projects/${paris.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
        mockMvc.delete("/projects/${paris.id}/expenses/$expenseId") { with(asUser("alice-id")) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `non-members are forbidden`() {
        val project = projectWithMember("alice-id")
        val expenseId = testData.createExpense(project, "alice-id", "A", "2026-10-01T10:00:00", 1)
        val stranger = asUser("stranger-id")

        mockMvc.post("/projects/${project.id}/expenses") {
            with(stranger)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"A","date":"2026-10-01T10:00:00","amount":1}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/expenses") { with(stranger) }.andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isForbidden() } }
        mockMvc.put("/projects/${project.id}/expenses/$expenseId") {
            with(stranger)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"A","date":"2026-10-01T10:00:00","amount":1}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.delete("/projects/${project.id}/expenses/$expenseId") { with(stranger) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/tags") { with(stranger) }.andExpect { status { isForbidden() } }
    }
}
