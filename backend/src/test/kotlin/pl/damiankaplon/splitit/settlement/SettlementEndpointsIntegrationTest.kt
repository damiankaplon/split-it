package pl.damiankaplon.splitit.settlement

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pl.damiankaplon.splitit.PLN
import pl.damiankaplon.splitit.PostgreTestContainerConfig
import pl.damiankaplon.splitit.balancing.Saldo
import pl.damiankaplon.splitit.balancing.SaldoRepository
import pl.damiankaplon.splitit.project.Project
import pl.damiankaplon.splitit.project.ProjectMember
import pl.damiankaplon.splitit.project.ProjectMemberRepository
import pl.damiankaplon.splitit.project.ProjectRepository

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class)
class SettlementEndpointsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projects: ProjectRepository,
    private val projectMembers: ProjectMemberRepository,
    private val saldos: SaldoRepository,
) {

    private fun asUser(id: String) = jwt().jwt { it.subject(id).claim("preferred_username", id) }

    private fun projectWith(vararg userIds: String): Project {
        val project = projects.save(Project(userIds.first(), "Trip to Rome", PLN))
        userIds.forEach { projectMembers.save(ProjectMember(project, it, it)) }
        saldos.save(Saldo(project.id))
        return project
    }

    /** alice paid 100.00 and bob 20.00, so bob owes alice 40.00. */
    private fun bobOwesAlice4000(): Project {
        val project = projectWith("alice-id", "bob-id")
        addExpense(project, "alice-id", 10000)
        addExpense(project, "bob-id", 2000)
        return project
    }

    private fun addExpense(project: Project, userId: String, amount: Int) {
        mockMvc.post("/projects/${project.id}/expenses") {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Dinner","date":"2026-10-03T19:30:00","amount":$amount}"""
        }.andExpect { status { isCreated() } }
    }

    private fun requestSettlement(project: Project, debtorId: String, body: String) =
        mockMvc.post("/projects/${project.id}/settlements") {
            with(asUser(debtorId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.settlementId(): String =
        andReturn().response.contentAsString.let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun resolve(settlementId: String, action: String, userId: String) =
        mockMvc.post("/settlements/$settlementId/$action") { with(asUser(userId)) }

    private fun expectDebt(project: Project, amount: Int?) =
        mockMvc.get("/projects/${project.id}/debts") { with(asUser("alice-id")) }.andExpect {
            status { isOk() }
            if (amount == null) {
                jsonPath("$.length()") { value(0) }
            } else {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].debtorId") { value("bob-id") }
                jsonPath("$[0].creditorId") { value("alice-id") }
                jsonPath("$[0].amount") { value(amount) }
            }
        }

    @Test
    fun `members see who owes whom`() {
        val project = bobOwesAlice4000()

        expectDebt(project, 4000)
    }

    @Test
    fun `settlement of the whole debt clears it once the creditor confirms`() {
        val project = bobOwesAlice4000()

        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").andExpect {
            status { isCreated() }
            jsonPath("$.amount") { value(4000) }
            jsonPath("$.status") { value("PENDING") }
        }.settlementId()
        // The debt stays until confirmed
        expectDebt(project, 4000)

        resolve(id, "confirm", "alice-id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }
        expectDebt(project, null)
    }

    @Test
    fun `confirmed partial settlement reduces the debt`() {
        val project = bobOwesAlice4000()

        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":1500}""").settlementId()
        resolve(id, "confirm", "alice-id").andExpect { status { isOk() } }

        expectDebt(project, 2500)
    }

    @Test
    fun `rejected or cancelled settlement leaves the debt`() {
        val project = bobOwesAlice4000()

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

        expectDebt(project, 4000)
        resolve(rejected, "confirm", "alice-id").andExpect { status { isConflict() } }
    }

    @Test
    fun `only both sides see the pending settlement`() {
        val project = bobOwesAlice4000()
        projectMembers.save(ProjectMember(project, "settlement-dave-id", "dave"))
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        // A pending settlement in another project of the same members is not listed
        val otherProject = bobOwesAlice4000()
        requestSettlement(
            otherProject,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}"""
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
        mockMvc.get("/projects/${project.id}/settlements") { with(asUser("bob-id")) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `only the creditor confirms or rejects and only the debtor cancels`() {
        val project = bobOwesAlice4000()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()

        // Settlements are looked up together with the party entitled to the action, so others don't find them
        resolve(id, "confirm", "bob-id").andExpect { status { isNotFound() } }
        resolve(id, "reject", "bob-id").andExpect { status { isNotFound() } }
        resolve(id, "cancel", "alice-id").andExpect { status { isNotFound() } }
        expectDebt(project, 4000)
    }

    @Test
    fun `invalid settlement requests are rejected`() {
        val project = bobOwesAlice4000()

        // Only one request awaits confirmation at a time
        requestSettlement(
            project,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}"""
        ).andExpect { status { isCreated() } }
        requestSettlement(
            project,
            "bob-id",
            """{"creditorId":"alice-id","amount":4000}"""
        ).andExpect { status { isConflict() } }
        requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":0}""")
            .andExpect { status { isBadRequest() } }
        requestSettlement(project, "bob-id", """{"creditorId":"alice-id"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `settling more than owed fails on confirmation`() {
        val project = bobOwesAlice4000()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4001}""").settlementId()

        resolve(id, "confirm", "alice-id").andExpect { status { isConflict() } }
        expectDebt(project, 4000)
    }

    @Test
    fun `confirming fails when the debt shrank below the declared payment`() {
        val project = bobOwesAlice4000()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()

        addExpense(project, "bob-id", 2000)

        resolve(id, "confirm", "alice-id").andExpect { status { isConflict() } }
        expectDebt(project, 3000)
    }

    @Test
    fun `non-members cannot see debts nor resolve settlements`() {
        val project = bobOwesAlice4000()
        val id = requestSettlement(project, "bob-id", """{"creditorId":"alice-id","amount":4000}""").settlementId()
        val stranger = asUser("stranger-id")

        mockMvc.get("/projects/${project.id}/debts") { with(stranger) }.andExpect { status { isForbidden() } }
        mockMvc.get("/projects/${project.id}/settlements") { with(stranger) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        resolve(id, "confirm", "stranger-id").andExpect { status { isNotFound() } }
    }
}
