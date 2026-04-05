/**
 * Custom exception hierarchy for the RetainIQ decisioning engine.
 *
 * Each exception maps to a specific HTTP status code via the
 * [GlobalExceptionHandler][com.retainiq.api.GlobalExceptionHandler].
 */
package com.retainiq.exception

/**
 * Thrown when the `X-Tenant-ID` header references an unknown or suspended tenant.
 *
 * Maps to **403 Forbidden**.
 */
class TenantNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when an outcome submission references a decision ID that does not exist.
 *
 * Maps to **404 Not Found**.
 */
class DecisionNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when the HMAC-SHA256 signature on a catalog sync request does not match.
 *
 * Maps to **401 Unauthorized**.
 */
class InvalidSignatureException(message: String = "HMAC signature verification failed") : RuntimeException(message)

/**
 * Thrown when a tenant exceeds its per-second request quota.
 *
 * Maps to **429 Too Many Requests** with a `Retry-After` header.
 *
 * @property retryAfterSeconds number of seconds the client should wait before retrying
 */
class RateLimitExceededException(val retryAfterSeconds: Int = 60) : RuntimeException("Rate limit exceeded")

/**
 * Thrown when the subscriber ID cannot be resolved in the BSS or cache.
 *
 * Maps to **422 Unprocessable Entity**.
 */
class SubscriberNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a catalog sync operation fails (e.g. malformed product data, cache write failure).
 *
 * Maps to **500 Internal Server Error** via the catch-all handler.
 */
class CatalogSyncException(message: String) : RuntimeException(message)
