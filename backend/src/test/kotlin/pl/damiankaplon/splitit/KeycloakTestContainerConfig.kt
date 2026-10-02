package pl.damiankaplon.splitit

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class KeycloakTestContainerConfig {

	@Bean
	fun keycloakContainer(): KeycloakContainer =
		KeycloakContainer("quay.io/keycloak/keycloak:26.8.0")
			.withRealmImportFile("keycloak/$REALM-realm.json")
			.withStartupTimeout(Duration.ofMinutes(5))

	@Bean
	fun keycloakProperties(keycloak: KeycloakContainer): DynamicPropertyRegistrar =
		DynamicPropertyRegistrar { registry ->
			registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { issuerUri(keycloak) }
		}

	companion object {
		const val REALM = "split_it"

		fun issuerUri(keycloak: KeycloakContainer) = "${keycloak.authServerUrl}/realms/$REALM"
	}
}
