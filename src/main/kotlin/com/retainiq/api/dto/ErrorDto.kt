/**
 * Standard error response DTOs used by the [GlobalExceptionHandler][com.retainiq.api.GlobalExceptionHandler].
 */
package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Top-level error response envelope.
 *
 * @property error the error detail object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: ErrorDetail
)

/**
 * Structured error detail included in every error response.
 *
 * @property code machine-readable error code (e.g. "validation_error", "tenant_not_found")
 * @property message human-readable error description
 * @property traceId unique trace ID for log correlation (auto-generated UUID)
 * @property details optional additional context (e.g. field-level validation errors)
 */
data class ErrorDetail(
    val code: String,
    val message: String,
    @JsonProperty("trace_id")
    val traceId: String = UUID.randomUUID().toString(),
    val details: Any? = null
)
