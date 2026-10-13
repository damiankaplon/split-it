package pl.damiankaplon.splitit

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional

/**
 * Base of the integration tests. All subclasses share one Spring context,
 * since the context cache is keyed by this configuration, so keep it here
 * rather than varying it per test class.
 *
 * Data is committed and stays in the database; see
 * [RollbackIntegrationTest] for tests that leave nothing behind.
 * Either way, a test's starting state is inserted with [testData].
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreTestContainerConfig::class, TestDataRepository::class)
abstract class IntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var testData: TestDataRepository

    protected fun asUser(id: String, username: String = id) =
        jwt().jwt { it.subject(id).claim("preferred_username", username) }

    protected fun idOf(response: String) = Regex("\"id\":\"([^\"]+)\"").find(response)!!.groupValues[1]
}

/**
 * Each test runs in a transaction that is rolled back at its end, MockMvc
 * calls included, so tests don't see each other's data. Tests that need
 * real commits (e.g. concurrent updates) extend [IntegrationTest] instead.
 */
@Transactional
abstract class RollbackIntegrationTest : IntegrationTest()
