package pl.damiankaplon.splitit

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Date

private const val TEST_ENDPOINT = "/test/security/whoami"

/** Exists only in the test classpath, so the tests verify the global security config, not a production endpoint. */
@TestComponent
@RestController
private class SecurityTestEndpoint {
    @GetMapping(TEST_ENDPOINT)
    fun whoami(authentication: Authentication): String = authentication.name
}

@SpringBootTest
@AutoConfigureMockMvc
@Import(
    PostgreTestContainerConfig::class,
    KeycloakTestContainerConfig::class,
    SecurityTestEndpoint::class,
)
class OAuth2SecurityConfigIntegrationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val keycloak: KeycloakContainer,
    ) {
        @Test
        fun `rejects request without authorization header`() {
            mockMvc.get(TEST_ENDPOINT).andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `rejects non-bearer authorization scheme`() {
            mockMvc
                .get(TEST_ENDPOINT) { header(HttpHeaders.AUTHORIZATION, "Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ=") }
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `rejects malformed bearer token`() {
            mockMvc
                .get(TEST_ENDPOINT) { header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt") }
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `rejects well-formed jwt signed with a key unknown to keycloak`() {
            val forged = forgedToken(issuer = KeycloakTestContainerConfig.issuerUri(keycloak))
            mockMvc
                .get(TEST_ENDPOINT) { header(HttpHeaders.AUTHORIZATION, "Bearer $forged") }
                .andExpect { status { isUnauthorized() } }
        }

        @ParameterizedTest
        @ValueSource(strings = ["https://localhost", "capacitor://localhost"])
        fun `answers cors preflight from the mobile apps without a token`(origin: String) {
            mockMvc
                .options(TEST_ENDPOINT) {
                    header(HttpHeaders.ORIGIN, origin)
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                }.andExpect {
                    status { isOk() }
                    header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin) }
                }
        }

        @Test
        fun `rejects cors preflight from an unknown origin`() {
            mockMvc
                .options(TEST_ENDPOINT) {
                    header(HttpHeaders.ORIGIN, "https://evil.example")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                }.andExpect { status { isForbidden() } }
        }

        private fun forgedToken(issuer: String): String {
            val key = RSAKeyGenerator(2048).keyID("forged").generate()
            val claims =
                JWTClaimsSet
                    .Builder()
                    .issuer(issuer)
                    .subject("attacker")
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build()
            val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            jwt.sign(RSASSASigner(key))
            return jwt.serialize()
        }
    }
