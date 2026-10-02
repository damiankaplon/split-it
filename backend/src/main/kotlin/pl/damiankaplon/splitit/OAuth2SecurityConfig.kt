package pl.damiankaplon.splitit

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

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
