package com.retainiq.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * WebFlux security configuration for the RetainIQ API.
 *
 * Public (unauthenticated) endpoints:
 * - `/health` — liveness probe
 * - `/actuator` (all sub-paths) — Spring Boot actuator
 * - `/v1/auth` (all sub-paths) — token issuance
 * - `/webjars`, `/v3/api-docs` (all sub-paths) — OpenAPI/Swagger UI
 *
 * All other endpoints require a valid JWT bearer token in the `Authorization` header.
 * The [JwtAuthFilter] extracts and validates the token, then sets the tenant context
 * on the exchange attributes.
 *
 * @property tokenService used by the JWT filter to validate tokens
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val tokenService: TokenService
) {

    /**
     * Builds the [SecurityWebFilterChain] with CSRF/basic/form disabled and JWT auth enabled.
     *
     * @param http the reactive HTTP security DSL
     * @return configured filter chain
     */
    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/health", "/actuator/**", "/v1/auth/**", "/v1/manage/login", "/webjars/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                    .anyExchange().authenticated()
            }
            .addFilterBefore(JwtAuthFilter(tokenService), SecurityWebFiltersOrder.AUTHENTICATION)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { exchange, _ ->
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    Mono.empty()
                }
            }
            .build()
    }
}

/**
 * WebFilter that extracts and validates JWT bearer tokens from the `Authorization` header.
 *
 * On success, sets `tenantId` and `permissions` as exchange attributes and injects a
 * [ReactiveSecurityContextHolder] authentication. On failure, returns 401 Unauthorized.
 * Public endpoints (health, actuator, auth, docs) are skipped.
 *
 * @property tokenService used to parse and verify JWT tokens
 */
class JwtAuthFilter(private val tokenService: TokenService) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()

        // Skip auth for public endpoints
        if (path.startsWith("/health") || path.startsWith("/actuator") ||
            path.startsWith("/v1/auth") || path == "/v1/manage/login" ||
            path.startsWith("/webjars") || path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-ui")) {
            return chain.filter(exchange)
        }

        val authHeader = exchange.request.headers.getFirst("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return Mono.empty()
        }

        val token = authHeader.removePrefix("Bearer ")
        val claims = tokenService.validateToken(token)
        if (claims == null) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return Mono.empty()
        }

        // Add tenant context to request attributes (ConcurrentHashMap doesn't allow null values)
        claims["tenant_id"]?.let { exchange.attributes["tenantId"] = it }
        claims["permissions"]?.let { exchange.attributes["permissions"] = it }
        claims["role"]?.let { exchange.attributes["role"] = it }

        // Create a security context with an authenticated token
        val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            claims["tenant_id"], null, emptyList()
        )

        return chain.filter(exchange)
            .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder
                .withAuthentication(auth))
    }
}

/**
 * Utility object for extracting tenant identity from the coroutine context if needed.
 *
 * Currently a placeholder; production implementations may use Kotlin coroutine context
 * elements to propagate the authenticated tenant ID without exchange attributes.
 */
object TenantContext {
    // Utility for extracting tenant from coroutine context if needed
}
