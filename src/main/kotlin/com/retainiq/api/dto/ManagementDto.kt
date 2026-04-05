package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

// --- Tenant/Telco Config ---
data class CreateTenantRequest(
    val name: String,
    @JsonProperty("display_name") val displayName: String,
    val market: String,
    @JsonProperty("regulatory_profile") val regulatoryProfile: RegulatoryProfileDto,
    @JsonProperty("bss_config") val bssConfig: BssConfigDto? = null,
    @JsonProperty("ranking_weights") val rankingWeights: RankingWeightsDto? = null,
    @JsonProperty("rate_limits") val rateLimits: RateLimitsDto? = null
)

data class UpdateTenantRequest(
    @JsonProperty("display_name") val displayName: String? = null,
    @JsonProperty("regulatory_profile") val regulatoryProfile: RegulatoryProfileDto? = null,
    @JsonProperty("bss_config") val bssConfig: BssConfigDto? = null,
    @JsonProperty("catalog_webhook_url") val catalogWebhookUrl: String? = null,
    @JsonProperty("ranking_weights") val rankingWeights: RankingWeightsDto? = null,
    @JsonProperty("rate_limits") val rateLimits: RateLimitsDto? = null
)

data class RegulatoryProfileDto(
    @JsonProperty("require_arabic_disclosure") val requireArabicDisclosure: Boolean = false,
    @JsonProperty("consent_required") val consentRequired: Boolean = false,
    @JsonProperty("cooling_off_hours") val coolingOffHours: Int = 24,
    @JsonProperty("audit_retention_months") val auditRetentionMonths: Int = 24
)

data class BssConfigDto(
    val type: String,
    val endpoint: String,
    @JsonProperty("auth_type") val authType: String,
    @JsonProperty("auth_config") val authConfig: Map<String, String> = emptyMap(),
    @JsonProperty("field_mapping") val fieldMapping: Map<String, String> = emptyMap(),
    @JsonProperty("timeout_ms") val timeoutMs: Int = 5000,
    @JsonProperty("retry_count") val retryCount: Int = 2
)

data class RankingWeightsDto(
    val alpha: Double = 0.45,
    val beta: Double = 0.30,
    val gamma: Double = 0.15,
    val delta: Double = 0.10
)

data class RateLimitsDto(
    @JsonProperty("requests_per_second") val requestsPerSecond: Int = 1000,
    @JsonProperty("burst_size") val burstSize: Int = 5000
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TenantResponse(
    val id: UUID,
    val name: String,
    @JsonProperty("display_name") val displayName: String,
    val market: String,
    val status: String,
    @JsonProperty("regulatory_profile") val regulatoryProfile: RegulatoryProfileDto,
    @JsonProperty("bss_config") val bssConfig: BssConfigDto?,
    @JsonProperty("catalog_webhook_url") val catalogWebhookUrl: String?,
    @JsonProperty("ranking_weights") val rankingWeights: RankingWeightsDto,
    @JsonProperty("rate_limits") val rateLimits: RateLimitsDto,
    @JsonProperty("api_credentials") val apiCredentials: ApiCredentialsDto?,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

data class ApiCredentialsDto(
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String? = null  // Only shown on create
)

// --- User Management ---
data class CreateUserRequest(
    val email: String,
    val name: String,
    val password: String,
    val role: String,
    @JsonProperty("tenant_id") val tenantId: UUID? = null
)

data class UpdateUserRequest(
    val name: String? = null,
    val role: String? = null,
    val active: Boolean? = null
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val role: String,
    @JsonProperty("tenant_id") val tenantId: UUID?,
    @JsonProperty("tenant_name") val tenantName: String?,
    val active: Boolean,
    @JsonProperty("last_login_at") val lastLoginAt: String?,
    @JsonProperty("created_at") val createdAt: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String = "Bearer",
    @JsonProperty("expires_in") val expiresIn: Long,
    val user: UserResponse
)

// --- Dashboard Stats ---
data class DashboardStatsResponse(
    @JsonProperty("total_decisions_today") val totalDecisionsToday: Long,
    @JsonProperty("total_decisions_7d") val totalDecisions7d: Long,
    @JsonProperty("avg_latency_ms") val avgLatencyMs: Double,
    @JsonProperty("p99_latency_ms") val p99LatencyMs: Double,
    @JsonProperty("offer_attach_rate") val offerAttachRate: Double,
    @JsonProperty("degraded_rate") val degradedRate: Double,
    @JsonProperty("active_tenants") val activeTenants: Int,
    @JsonProperty("total_users") val totalUsers: Int,
    @JsonProperty("decisions_by_channel") val decisionsByChannel: Map<String, Long>,
    @JsonProperty("decisions_by_churn_band") val decisionsByChurnBand: Map<String, Long>,
    @JsonProperty("top_offers") val topOffers: List<TopOfferDto>
)

data class TopOfferDto(
    val sku: String,
    val name: String,
    @JsonProperty("times_offered") val timesOffered: Long,
    @JsonProperty("times_accepted") val timesAccepted: Long,
    @JsonProperty("attach_rate") val attachRate: Double
)

// --- BSS Connection Test ---
data class BssTestResult(
    val success: Boolean,
    @JsonProperty("response_time_ms") val responseTimeMs: Long,
    val message: String,
    val details: Map<String, Any>? = null
)
