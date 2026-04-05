package com.retainiq.api

import com.retainiq.api.dto.ErrorDetail
import com.retainiq.api.dto.ErrorResponse
import com.retainiq.api.dto.TokenRequest
import com.retainiq.api.dto.TokenResponse
import com.retainiq.security.TokenService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * OAuth2 client-credentials token endpoint.
 *
 * Issues JWT bearer tokens for machine-to-machine authentication. Clients provide
 * their tenant UUID as the `client_id` and a shared secret as `client_secret`.
 * Only the `client_credentials` grant type is supported.
 *
 * @property tokenService handles credential validation and JWT generation
 */
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val tokenService: TokenService
) {

    /**
     * Issues a JWT access token for the given client credentials.
     *
     * @param request token request containing grant_type, client_id, and client_secret
     * @return [TokenResponse][com.retainiq.api.dto.TokenResponse] on success, or an error body on failure
     */
    @PostMapping("/token")
    suspend fun token(@RequestBody request: TokenRequest): ResponseEntity<Any> {
        if (request.grantType != "client_credentials") {
            return ResponseEntity.badRequest().body(
                ErrorResponse(ErrorDetail("unsupported_grant_type", "Only client_credentials is supported"))
            )
        }

        val token = tokenService.authenticate(request.clientId, request.clientSecret)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(ErrorDetail("invalid_client", "Invalid client credentials"))
            )

        logger.info { "Token issued for client=${request.clientId}" }
        return ResponseEntity.ok(token)
    }
}
