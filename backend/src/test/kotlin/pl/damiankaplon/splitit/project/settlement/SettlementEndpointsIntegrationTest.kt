package pl.damiankaplon.splitit.project.settlement

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.RollbackIntegrationTest
import pl.damiankaplon.splitit.project.internal.Project

/**
 * The settlement workflow. What a confirmed settlement does to the debts is
 * the balancing module's concern, which learns about it from the
 * [pl.damiankaplon.splitit.project.SettlementEvent] it publishes.
 */
class SettlementEndpointsIntegrationTest : RollbackIntegrationTest() {
    private fun projectWith(vararg userIds: String): Project = testData.createProject(userIds.first(), *userIds.drop(1).toTypedArray())

    private fun aliceAndBob(): Project = projectWith("alice-id", "bob-id")

    private fun requestSettlement(
        project: Project,
        debtorId: String,
        body: String,
    ) = mockMvc.post("/projects/${project.id}/settlements") {
        with(asUser(debtorId))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun ResultActionsDsl.settlementId(): String =
        andReturn().response.contentAsString.let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun resolve(
        settlementId: String,
        action: String,
        userId: String,
    ) = mockMvc.post("/settlements/$settlementId/$action") { with(asUser(userId)) }

    @Test
    fun `a settlement is pending until the creditor confirms it`() {
        val project = aliceAndBob()

        val id =
            requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.amount") { value(4000) }
                    jsonPath("$.status") { value("PENDING") }
                }.settlementId()

        resolve(id, "confirm", "alice-id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }
    }

    @Test
    fun `the creditor rejects and the debtor cancels a settlement`() {
        val project = aliceAndBob()

        val rejected =
            requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        resolve(rejected, "reject", "alice-id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
        }
        val cancelled =
            requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        resolve(cancelled, "cancel", "bob-id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
        }
    }

    @Test
    fun `only both sides see the pending settlement`() {
        val project = aliceAndBob()
        testData.addMember(project, "settlement-dave-id", "dave")
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        // A pending settlement in another project of the same members is not listed
        val otherProject = aliceAndBob()
        requestSettlement(
            otherProject,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}""",
        ).andExpect { status { isCreated() } }

        listOf("alice-id", "bob-id").forEach { user ->
            mockMvc.get("/projects/${project.id}/settlements") { with(asUser(user)) }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(id) }
            }
        }
        mockMvc.get("/projects/${project.id}/settlements") { with(asUser("settlement-dave-id")) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        resolve(id, "confirm", "alice-id")
        mockMvc
            .get("/projects/${project.id}/settlements") { with(asUser("bob-id")) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `only the creditor confirms or rejects and only the debtor cancels`() {
        val project = aliceAndBob()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()

        // Settlements are looked up together with the party entitled to the action, so others don't find them
        resolve(id, "confirm", "bob-id").andExpect { status { isNotFound() } }
        resolve(id, "reject", "bob-id").andExpect { status { isNotFound() } }
        resolve(id, "cancel", "alice-id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `invalid settlement requests are rejected`() {
        val project = aliceAndBob()

        // Only one request awaits confirmation at a time
        requestSettlement(
            project,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}""",
        ).andExpect { status { isCreated() } }
        requestSettlement(
            project,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}""",
        ).andExpect { status { isConflict() } }
        requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":0}""")
            .andExpect { status { isBadRequest() } }
        requestSettlement(project, "bob-id", """{"creditorId":"alice-id"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `any member can pay any other member`() {
        val project = projectWith("alice-id", "bob-id", "carol-id")

        val id = requestSettlement(project, "bob-id", """{"creditorId":"carol-id","amount":1000}""").settlementId()

        resolve(id, "confirm", "carol-id").andExpect { status { isOk() } }
    }

    @Test
    fun `payments are only between members of the project`() {
        val project = aliceAndBob()

        requestSettlement(project, "bob-id", """{"creditorId":"stranger-id","amount":1000}""")
            .andExpect { status { isBadRequest() } }
        requestSettlement(project, "stranger-id", """{"creditorId":"alice-id","amount":1000}""")
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `non-members cannot see nor resolve settlements`() {
        val project = aliceAndBob()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        val stranger = asUser("stranger-id")

        mockMvc.get("/projects/${project.id}/settlements") { with(stranger) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        resolve(id, "confirm", "stranger-id").andExpect { status { isNotFound() } }
    }
}
