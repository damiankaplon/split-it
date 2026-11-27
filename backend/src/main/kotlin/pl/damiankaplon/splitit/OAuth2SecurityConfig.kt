package pl.damiankaplon.splitit

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ResponseStatusException
import pl.damiankaplon.splitit.kernel.UserId

@Configuration(proxyBeanMethods = false)
class OAuth2SecurityConfig {
    /**
     * Every request must carry a bearer JWT issued by the configured Keycloak
     * realm.
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Answers CORS preflights before authentication, which they carry no token for
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf(AbstractHttpConfigurer<*, *>::disable)
        return http.build()
    }

    /**
     * The web app is served from the API's own origin, so it needs no CORS. The
     * Android and iOS apps run the frontend in a WebView whose origin is
     * https://localhost (Android) or capacitor://localhost (iOS), and call the
     * API cross-origin.
     */
    @Bean
    fun corsConfigurationSource(
        @Value("\${splitit.cors.allowed-origins}") allowedOrigins: List<String>,
    ): UrlBasedCorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                this.allowedOrigins = allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }
}

fun Jwt.subjectOrThrow(): String = subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No subject found")

fun Jwt.userIdOrThrow(): UserId = subjectOrThrow().let(::UserId)

fun Jwt.usernameOrThrow(): String =
    getClaimAsString("preferred_username") ?: throw ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "No username found",
    )
