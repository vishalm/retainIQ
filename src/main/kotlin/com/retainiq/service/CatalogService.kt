package com.retainiq.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.retainiq.api.dto.CatalogSyncRequest
import com.retainiq.cache.CatalogCacheService
import com.retainiq.domain.*
import com.retainiq.exception.InvalidSignatureException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Handles VAS catalog sync webhooks from the operator's catalog management system.
 *
 * Responsibilities:
 * 1. Verify the HMAC-SHA256 signature of the incoming request body
 * 2. Map [ProductDto][com.retainiq.api.dto.ProductDto] DTOs to domain [VasProduct] entities
 * 3. Apply incremental or full-sync updates to the Redis catalog cache
 *
 * @property catalogCache Redis-backed catalog cache
 * @property objectMapper Jackson mapper for HMAC body serialization
 * @property hmacSecret shared secret for signature verification
 */
@Service
class CatalogService(
    private val catalogCache: CatalogCacheService,
    private val objectMapper: ObjectMapper,
    @Value("\${retainiq.security.hmac.secret}") private val hmacSecret: String
) {

    /**
     * Verifies the HMAC-SHA256 signature of a catalog sync request.
     *
     * @param tenantId owning tenant (used in log messages)
     * @param signature hex-encoded HMAC signature from the `X-Signature` header
     * @param request the catalog sync payload whose JSON body is signed
     * @throws InvalidSignatureException if the signature does not match
     */
    fun verifySyncSignature(tenantId: UUID, signature: String, request: CatalogSyncRequest) {
        val body = objectMapper.writeValueAsString(request)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        val expected = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }

        if (!signature.equals(expected, ignoreCase = true)) {
            logger.warn { "HMAC signature mismatch for tenant $tenantId" }
            throw InvalidSignatureException()
        }
    }

    /**
     * Processes a catalog sync: maps DTOs to domain products and updates the Redis cache.
     *
     * Supports two modes:
     * - **Full sync** (`fullSync = true`): replaces the entire catalog
     * - **Incremental** (`fullSync = false`): upserts/removes individual products
     *
     * @param tenantId owning tenant
     * @param request the catalog sync payload
     * @return a unique sync ID for tracking
     */
    suspend fun processCatalogSync(tenantId: UUID, request: CatalogSyncRequest): String {
        val syncId = UUID.randomUUID().toString()

        val products = request.products.map { dto ->
            VasProduct(
                sku = dto.sku,
                name = dto.name,
                nameAr = dto.nameAr,
                category = dto.category,
                margin = dto.margin,
                markets = dto.markets.mapNotNull { code ->
                    Market.entries.find { it.code == code.uppercase() }
                },
                eligibilityRules = dto.eligibilityRules,
                bundleWith = dto.bundleWith,
                incompatibleWith = dto.incompatibleWith,
                upgradeFrom = dto.upgradeFrom,
                regulatory = ProductRegulatory(
                    consentRequired = dto.regulatory?.consentRequired ?: false,
                    disclosureText = dto.regulatory?.disclosureText ?: emptyMap(),
                    coolingOffHours = dto.regulatory?.coolingOffHours
                ),
                active = request.event != "product.retired"
            )
        }

        if (request.fullSync) {
            catalogCache.putProducts(tenantId, products)
        } else {
            val existing = catalogCache.getActiveProducts(tenantId).toMutableList()
            for (product in products) {
                existing.removeAll { it.sku == product.sku }
                if (product.active) existing.add(product)
            }
            catalogCache.putProducts(tenantId, existing)
        }

        logger.info { "Catalog sync $syncId: ${products.size} products processed for tenant $tenantId (fullSync=${request.fullSync})" }
        return syncId
    }
}
