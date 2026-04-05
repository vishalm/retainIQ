package com.retainiq.service

import com.retainiq.api.dto.*
import com.retainiq.domain.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class TenantConfigService(private val tenantService: TenantService) {

    // Extended config storage (supplements TenantService)
    private val configs = ConcurrentHashMap<UUID, TenantConfig>()
    private val credentials = ConcurrentHashMap<UUID, ApiCredentialsDto>()

    init {
        val demoId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        configs[demoId] = TenantConfig(
            id = demoId,
            name = "demo-operator",
            displayName = "Demo Operator",
            market = Market.UAE,
            status = TenantStatus.ACTIVE,
            regulatoryProfile = RegulatoryProfile(false, false, 24, 24),
            catalogWebhookUrl = null,
            bssConfig = null,
            rankingWeights = RankingWeights(),
            rateLimits = RateLimits(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        credentials[demoId] = ApiCredentialsDto(
            clientId = demoId.toString(),
            clientSecret = null
        )
    }

    fun createTenant(request: CreateTenantRequest): Pair<TenantResponse, String> {
        val id = UUID.randomUUID()
        val market = parseMarket(request.market)
        val clientSecret = UUID.randomUUID().toString().replace("-", "")

        val tenant = Tenant(
            id = id,
            name = request.name,
            market = market,
            regulatoryProfile = RegulatoryProfile(
                requireArabicDisclosure = request.regulatoryProfile.requireArabicDisclosure,
                consentRequired = request.regulatoryProfile.consentRequired,
                coolingOffHours = request.regulatoryProfile.coolingOffHours,
                auditRetentionMonths = request.regulatoryProfile.auditRetentionMonths
            ),
            catalogWebhookUrl = null,
            status = TenantStatus.ACTIVE,
            createdAt = Instant.now()
        )
        tenantService.createTenant(tenant)

        val config = TenantConfig(
            id = id,
            name = request.name,
            displayName = request.displayName,
            market = market,
            status = TenantStatus.ACTIVE,
            regulatoryProfile = tenant.regulatoryProfile,
            catalogWebhookUrl = null,
            bssConfig = request.bssConfig?.let {
                BssConfig(it.type, it.endpoint, it.authType, it.authConfig, it.fieldMapping, it.timeoutMs, it.retryCount)
            },
            rankingWeights = request.rankingWeights?.let { RankingWeights(it.alpha, it.beta, it.gamma, it.delta) } ?: RankingWeights(),
            rateLimits = request.rateLimits?.let { RateLimits(it.requestsPerSecond, it.burstSize) } ?: RateLimits(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        configs[id] = config
        credentials[id] = ApiCredentialsDto(clientId = id.toString(), clientSecret = clientSecret)

        logger.info { "Created tenant ${config.displayName} (${config.name}) market=${market.code}" }
        return Pair(toResponse(config, showSecret = true, secret = clientSecret), clientSecret)
    }

    fun updateTenant(id: UUID, request: UpdateTenantRequest): TenantResponse? {
        val config = configs[id] ?: return null
        val updated = config.copy(
            displayName = request.displayName ?: config.displayName,
            regulatoryProfile = request.regulatoryProfile?.let {
                RegulatoryProfile(it.requireArabicDisclosure, it.consentRequired, it.coolingOffHours, it.auditRetentionMonths)
            } ?: config.regulatoryProfile,
            catalogWebhookUrl = request.catalogWebhookUrl ?: config.catalogWebhookUrl,
            bssConfig = request.bssConfig?.let {
                BssConfig(it.type, it.endpoint, it.authType, it.authConfig, it.fieldMapping, it.timeoutMs, it.retryCount)
            } ?: config.bssConfig,
            rankingWeights = request.rankingWeights?.let { RankingWeights(it.alpha, it.beta, it.gamma, it.delta) } ?: config.rankingWeights,
            rateLimits = request.rateLimits?.let { RateLimits(it.requestsPerSecond, it.burstSize) } ?: config.rateLimits,
            updatedAt = Instant.now()
        )
        configs[id] = updated
        logger.info { "Updated tenant ${updated.displayName}" }
        return toResponse(updated)
    }

    fun getTenant(id: UUID): TenantResponse? {
        return configs[id]?.let { toResponse(it) }
    }

    fun listTenants(): List<TenantResponse> {
        return configs.values.sortedByDescending { it.createdAt }.map { toResponse(it) }
    }

    fun activateTenant(id: UUID): TenantResponse? {
        val config = configs[id] ?: return null
        val updated = config.copy(status = TenantStatus.ACTIVE, updatedAt = Instant.now())
        configs[id] = updated
        return toResponse(updated)
    }

    fun suspendTenant(id: UUID): TenantResponse? {
        val config = configs[id] ?: return null
        val updated = config.copy(status = TenantStatus.SUSPENDED, updatedAt = Instant.now())
        configs[id] = updated
        return toResponse(updated)
    }

    fun testBssConnection(id: UUID): BssTestResult {
        val config = configs[id] ?: return BssTestResult(false, 0, "Tenant not found")
        val bss = config.bssConfig ?: return BssTestResult(false, 0, "BSS not configured for this tenant")

        // Simulate BSS connection test
        val startTime = System.currentTimeMillis()
        val latency = System.currentTimeMillis() - startTime + 50 // simulated

        return BssTestResult(
            success = true,
            responseTimeMs = latency,
            message = "Connection successful to ${bss.endpoint}",
            details = mapOf(
                "type" to bss.type,
                "auth_type" to bss.authType,
                "fields_mapped" to bss.fieldMapping.size
            )
        )
    }

    fun regenerateCredentials(id: UUID): ApiCredentialsDto? {
        if (!configs.containsKey(id)) return null
        val newSecret = UUID.randomUUID().toString().replace("-", "")
        val creds = ApiCredentialsDto(clientId = id.toString(), clientSecret = newSecret)
        credentials[id] = creds
        return creds
    }

    private fun toResponse(config: TenantConfig, showSecret: Boolean = false, secret: String? = null): TenantResponse {
        return TenantResponse(
            id = config.id,
            name = config.name,
            displayName = config.displayName,
            market = config.market.code,
            status = config.status.name,
            regulatoryProfile = RegulatoryProfileDto(
                config.regulatoryProfile.requireArabicDisclosure,
                config.regulatoryProfile.consentRequired,
                config.regulatoryProfile.coolingOffHours,
                config.regulatoryProfile.auditRetentionMonths
            ),
            bssConfig = config.bssConfig?.let {
                BssConfigDto(it.type, it.endpoint, it.authType, it.authConfig, it.fieldMapping, it.timeoutMs, it.retryCount)
            },
            catalogWebhookUrl = config.catalogWebhookUrl,
            rankingWeights = RankingWeightsDto(
                config.rankingWeights.alpha, config.rankingWeights.beta,
                config.rankingWeights.gamma, config.rankingWeights.delta
            ),
            rateLimits = RateLimitsDto(config.rateLimits.requestsPerSecond, config.rateLimits.burstSize),
            apiCredentials = if (showSecret) ApiCredentialsDto(config.id.toString(), secret) else ApiCredentialsDto(config.id.toString()),
            createdAt = config.createdAt.toString(),
            updatedAt = config.updatedAt.toString()
        )
    }

    private fun parseMarket(market: String): Market = when (market.uppercase()) {
        "AE" -> Market.UAE
        "SA" -> Market.SAUDI
        "KW" -> Market.KUWAIT
        "BH" -> Market.BAHRAIN
        "OM" -> Market.OMAN
        else -> Market.UAE
    }
}
