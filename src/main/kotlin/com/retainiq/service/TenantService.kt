package com.retainiq.service

import com.retainiq.domain.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Tenant registry for the RetainIQ platform.
 *
 * Currently backed by an in-memory [ConcurrentHashMap] with a pre-seeded demo tenant
 * (`00000000-0000-0000-0000-000000000001`, "Demo Operator", UAE market).
 * Production deployments will be backed by the `platform.tenants` PostgreSQL table.
 */
@Service
class TenantService {
    // In production: backed by PostgreSQL platform.tenants table
    // For now: in-memory with a default demo tenant
    private val tenants = ConcurrentHashMap<UUID, Tenant>()

    init {
        val demoTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        tenants[demoTenantId] = Tenant(
            id = demoTenantId,
            name = "Demo Operator",
            market = Market.UAE,
            regulatoryProfile = RegulatoryProfile(
                requireArabicDisclosure = false,
                consentRequired = false,
                coolingOffHours = 24,
                auditRetentionMonths = 24
            ),
            catalogWebhookUrl = null,
            status = TenantStatus.ACTIVE,
            createdAt = Instant.now()
        )
        logger.info { "Demo tenant initialized: $demoTenantId" }
    }

    /**
     * Retrieves a tenant by ID, or null if not found.
     *
     * @param tenantId the tenant UUID
     * @return the [Tenant], or null
     */
    fun getTenant(tenantId: UUID): Tenant? = tenants[tenantId]

    /**
     * Registers a new tenant in the in-memory store.
     *
     * @param tenant the tenant to create
     * @return the created tenant
     */
    fun createTenant(tenant: Tenant): Tenant {
        tenants[tenant.id] = tenant
        return tenant
    }
}
