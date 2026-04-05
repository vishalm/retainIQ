package com.retainiq.api

import com.retainiq.api.dto.ErrorDetail
import com.retainiq.api.dto.ErrorResponse
import com.retainiq.exception.*
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

private val logger = KotlinLogging.logger {}

/**
 * Global exception handler that maps domain exceptions to standard HTTP error responses.
 *
 * Every error response includes a unique `trace_id` for correlation with server-side logs.
 * The mapping follows these conventions:
 *
 * | Exception | HTTP Status |
 * |-----------|-------------|
 * | [WebExchangeBindException] | 400 Bad Request |
 * | [TenantNotFoundException] | 403 Forbidden |
 * | [DecisionNotFoundException] | 404 Not Found |
 * | [InvalidSignatureException] | 401 Unauthorized |
 * | [RateLimitExceededException] | 429 Too Many Requests |
 * | [SubscriberNotFoundException] | 422 Unprocessable Entity |
 * | [Exception] (catch-all) | 500 Internal Server Error |
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** Handles Jakarta Bean Validation failures and returns field-level error details. */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(ex: WebExchangeBindException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest().body(
            ErrorResponse(ErrorDetail("validation_error", "Request validation failed", details = details))
        )
    }

    /** Handles unknown or suspended tenant IDs. Returns 403 Forbidden. */
    @ExceptionHandler(TenantNotFoundException::class)
    fun handleTenantNotFound(ex: TenantNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(ErrorDetail("tenant_not_found", ex.message ?: "Tenant not found"))
        )
    }

    /** Handles missing decision references in outcome submissions. Returns 404 Not Found. */
    @ExceptionHandler(DecisionNotFoundException::class)
    fun handleDecisionNotFound(ex: DecisionNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(ErrorDetail("decision_not_found", ex.message ?: "Decision not found"))
        )
    }

    /** Handles HMAC signature verification failures on catalog sync. Returns 401 Unauthorized. */
    @ExceptionHandler(InvalidSignatureException::class)
    fun handleInvalidSignature(ex: InvalidSignatureException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(ErrorDetail("invalid_signature", ex.message ?: "HMAC signature verification failed"))
        )
    }

    /** Handles rate-limit breaches. Returns 429 Too Many Requests with a `Retry-After` header. */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(ErrorResponse(ErrorDetail("rate_limit_exceeded", "Rate limit exceeded. Retry after ${ex.retryAfterSeconds}s")))
    }

    /** Handles unresolvable subscriber IDs. Returns 422 Unprocessable Entity. */
    @ExceptionHandler(SubscriberNotFoundException::class)
    fun handleSubscriberNotFound(ex: SubscriberNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(ErrorDetail("invalid_subscriber", ex.message ?: "Subscriber not found"))
        )
    }

    /** Catch-all for unhandled exceptions. Logs the full stack trace and returns 500 Internal Server Error. */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unhandled exception" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(ErrorDetail("internal_error", "An unexpected error occurred"))
        )
    }
}
