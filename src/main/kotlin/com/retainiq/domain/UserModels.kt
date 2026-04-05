package com.retainiq.domain

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val email: String,
    val name: String,
    val passwordHash: String,
    val role: UserRole,
    val tenantId: UUID?,
    val active: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant
)

enum class UserRole {
    SUPER_ADMIN,   // RetainIQ platform admin — can manage all tenants
    TENANT_ADMIN,  // Telco admin — can configure their own tenant
    ANALYST,       // Can view dashboards, tune rules, run A/B tests
    VIEWER         // Read-only access to dashboards
}

data class TenantConfig(
    val id: UUID,
    val name: String,
    val displayName: String,
    val market: Market,
    val status: TenantStatus,
    val regulatoryProfile: RegulatoryProfile,
    val catalogWebhookUrl: String?,
    val bssConfig: BssConfig?,
    val rankingWeights: RankingWeights,
    val rateLimits: RateLimits,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class BssConfig(
    val type: String,           // "rest" | "soap"
    val endpoint: String,
    val authType: String,       // "oauth2" | "basic" | "api_key" | "mtls"
    val authConfig: Map<String, String>,
    val fieldMapping: Map<String, String>,  // RetainIQ field -> BSS field
    val timeoutMs: Int,
    val retryCount: Int
)

data class RankingWeights(
    val alpha: Double = 0.45,
    val beta: Double = 0.30,
    val gamma: Double = 0.15,
    val delta: Double = 0.10
)

data class RateLimits(
    val requestsPerSecond: Int = 1000,
    val burstSize: Int = 5000
)
