package pl.damiankaplon.splitit

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.server.ResponseStatusException

@Configuration(proxyBeanMethods = false)
class OAuth2SecurityConfig {

	/** Every request must carry a bearer JWT issued by the configured Keycloak realm. */
	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.authorizeHttpRequests { it.anyRequest().authenticated() }
			.oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.csrf(AbstractHttpConfigurer<*, *>::disable)
		return http.build()
	}
}

fun Jwt.subjectOrThrow(): String =
    subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject found")

fun Jwt.userIdOrThrow(): UserId =
    subjectOrThrow().let(::UserId)

fun Jwt.usernameOrThrow(): String =
    getClaimAsString("preferred_username") ?: throw ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "No username found"
    )
