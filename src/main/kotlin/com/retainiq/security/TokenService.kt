package com.retainiq.security

import com.retainiq.api.dto.TokenResponse
import com.retainiq.domain.User
import com.retainiq.service.TenantService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

private val logger = KotlinLogging.logger {}

/**
 * Issues and validates JWT tokens for machine-to-machine authentication.
 *
 * Tokens are scoped to a specific tenant and include a `permissions` claim listing
 * the allowed API operations (e.g. "decide", "outcome", "catalog.sync").
 * The signing key is derived from the `retainiq.security.jwt.secret` property using HMAC-SHA.
 *
 * In production, client credentials are validated against hashed secrets in `platform.tenants`.
 * The current implementation accepts any valid tenant UUID as the client ID for demo purposes.
 *
 * @property jwtSecret base secret for HMAC-SHA key derivation
 * @property expiryMinutes token validity duration in minutes (default 15)
 * @property tenantService used to verify the tenant exists before issuing a token
 */
@Service
class TokenService(
    @Value("\${retainiq.security.jwt.secret}") private val jwtSecret: String,
    @Value("\${retainiq.security.jwt.expiry-minutes:15}") private val expiryMinutes: Long,
    private val tenantService: TenantService
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    /**
     * Authenticates client credentials and issues a JWT bearer token.
     *
     * @param clientId the tenant UUID presented as the OAuth2 client ID
     * @param clientSecret the shared secret (not validated in demo mode)
     * @return a [TokenResponse] on success, or null if the client ID is invalid
     */
    fun authenticate(clientId: String, clientSecret: String): TokenResponse? {
        // In production: validate against hashed credentials in platform.tenants
        // For demo: accept the demo tenant credentials
        val tenantId = try {
            UUID.fromString(clientId)
        } catch (e: Exception) {
            return null
        }

        val tenant = tenantService.getTenant(tenantId) ?: return null

        val now = Date()
        val expiry = Date(now.time + expiryMinutes * 60 * 1000)

        val token = Jwts.builder()
            .subject(tenantId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("tenant_name", tenant.name)
            .claim("market", tenant.market.code)
            .claim("permissions", listOf("decide", "outcome", "catalog.sync"))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()

        return TokenResponse(
            accessToken = token,
            expiresIn = expiryMinutes * 60
        )
    }

    /**
     * Validates a JWT token and extracts its claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return a map with `tenant_id` and `permissions` claims, or null if invalid/expired
     */
    /**
     * Issues a JWT token for a console user (admin/analyst/viewer).
     *
     * @param user the authenticated user
     * @return a signed JWT string valid for 1 hour
     */
    fun issueUserToken(user: User): String {
        val now = Date()
        val expiry = Date(now.time + 3600 * 1000) // 1 hour

        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("name", user.name)
            .claim("role", user.role.name)
            .apply { if (user.tenantId != null) claim("tenant_id", user.tenantId.toString()) }
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Map<String, Any>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            val result = mutableMapOf<String, Any>(
                "sub" to (claims.subject ?: "")
            )
            claims["tenant_id"]?.let { result["tenant_id"] = it as String }
            claims["permissions"]?.let { result["permissions"] = it as List<*> }
            claims["role"]?.let { result["role"] = it as String }
            claims["email"]?.let { result["email"] = it as String }
            result
        } catch (e: Exception) {
            logger.warn { "Invalid JWT: ${e.message}" }
            null
        }
    }
}
