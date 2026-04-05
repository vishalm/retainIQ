package com.retainiq.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.retainiq.domain.*
import com.retainiq.observability.RetainIQMetrics
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import kotlinx.coroutines.reactive.awaitFirstOrNull
import java.time.Duration
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Redis cache for the VAS product catalog.
 *
 * Key pattern: `tenant:{tenantId}:catalog`.
 * Default TTL: 15 minutes (configurable via `retainiq.cache.catalog-ttl-seconds`).
 *
 * On cache miss or Redis failure, returns a built-in default catalog of 6 MENA-focused
 * VAS products suitable for demo and fallback scenarios.
 *
 * @property redisTemplate reactive Redis string template
 * @property objectMapper Jackson mapper for JSON serialization
 * @property ttlSeconds cache entry time-to-live in seconds
 */
@Service
class CatalogCacheService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${retainiq.cache.catalog-ttl-seconds:900}") private val ttlSeconds: Long,
    private val metrics: RetainIQMetrics
) {
    private fun key(tenantId: UUID) = "tenant:$tenantId:catalog"

    /**
     * Retrieves the active VAS product catalog for a tenant.
     *
     * Falls back to the built-in [defaultCatalog] on cache miss or Redis failure.
     *
     * @param tenantId owning tenant
     * @return list of active [VasProduct]s
     */
    suspend fun getActiveProducts(tenantId: UUID): List<VasProduct> {
        return try {
            val json = redisTemplate.opsForValue().get(key(tenantId)).awaitFirstOrNull()
            if (json != null) {
                metrics.cacheHit("catalog", tenantId.toString())
                objectMapper.readValue(json, object : TypeReference<List<VasProduct>>() {})
            } else {
                metrics.cacheMiss("catalog", tenantId.toString())
                logger.info { "Catalog cache miss for tenant $tenantId, returning defaults" }
                defaultCatalog()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Redis read failed for catalog, returning defaults" }
            defaultCatalog()
        }
    }

    /**
     * Stores the VAS product catalog in Redis with the configured TTL.
     *
     * @param tenantId owning tenant
     * @param products the full list of active products to cache
     */
    suspend fun putProducts(tenantId: UUID, products: List<VasProduct>) {
        try {
            val json = objectMapper.writeValueAsString(products)
            redisTemplate.opsForValue()
                .set(key(tenantId), json, Duration.ofSeconds(ttlSeconds))
                .awaitFirstOrNull()
        } catch (e: Exception) {
            logger.warn(e) { "Redis write failed for catalog cache" }
        }
    }

    /**
     * Built-in default catalog with 6 MENA-focused VAS products.
     *
     * Used as a fallback when Redis is unavailable or the tenant has not yet synced a catalog.
     * Products: StreamPlus, Double Data, GCC Roaming, Gold Loyalty, StreamBasic, Family Plan.
     */
    private fun defaultCatalog(): List<VasProduct> = listOf(
        VasProduct(
            sku = "VAS-STREAM-PLUS", name = "StreamPlus 3-month free trial", nameAr = "ستريم بلس - تجربة مجانية 3 أشهر",
            category = "streaming", margin = 12.50,
            markets = listOf(Market.UAE, Market.SAUDI, Market.KUWAIT, Market.BAHRAIN, Market.OMAN),
            eligibilityRules = emptyList(), bundleWith = listOf("VAS-DATA-BOOST"),
            incompatibleWith = listOf("VAS-STREAM-BASIC"), upgradeFrom = listOf("VAS-STREAM-BASIC"),
            regulatory = ProductRegulatory(consentRequired = false, disclosureText = mapOf("AE" to "Free for 3 months, then AED 29/mo. Cancel anytime.", "SA" to "مجاني لمدة 3 أشهر، ثم 29 ريال/شهر. إلغاء في أي وقت."), coolingOffHours = 24),
            active = true
        ),
        VasProduct(
            sku = "VAS-DATA-BOOST", name = "Double Data 6 months", nameAr = "بيانات مضاعفة 6 أشهر",
            category = "data", margin = 8.00,
            markets = listOf(Market.UAE, Market.SAUDI, Market.KUWAIT, Market.BAHRAIN, Market.OMAN),
            eligibilityRules = emptyList(), bundleWith = listOf("VAS-STREAM-PLUS"),
            incompatibleWith = emptyList(), upgradeFrom = emptyList(),
            regulatory = ProductRegulatory(consentRequired = false, disclosureText = mapOf("AE" to "Double your data for 6 months at no extra cost.", "SA" to "ضاعف بياناتك لمدة 6 أشهر بدون تكلفة إضافية."), coolingOffHours = null),
            active = true
        ),
        VasProduct(
            sku = "VAS-ROAM-FREE", name = "Free GCC Roaming 30 days", nameAr = "تجوال مجاني في دول الخليج 30 يوم",
            category = "international", margin = 15.00,
            markets = listOf(Market.UAE, Market.SAUDI, Market.KUWAIT, Market.BAHRAIN, Market.OMAN),
            eligibilityRules = emptyList(), bundleWith = emptyList(),
            incompatibleWith = emptyList(), upgradeFrom = emptyList(),
            regulatory = ProductRegulatory(consentRequired = true, disclosureText = mapOf("AE" to "Free GCC roaming for 30 days. Auto-renews at AED 49/mo.", "SA" to "تجوال مجاني في دول الخليج لمدة 30 يوم. يتجدد تلقائياً بسعر 49 ريال/شهر."), coolingOffHours = 24),
            active = true
        ),
        VasProduct(
            sku = "VAS-LOYALTY-GOLD", name = "Gold Loyalty Discount 20%", nameAr = "خصم الولاء الذهبي 20%",
            category = "loyalty", margin = 5.00,
            markets = listOf(Market.UAE, Market.SAUDI),
            eligibilityRules = listOf("tenure_days > 365"), bundleWith = emptyList(),
            incompatibleWith = listOf("VAS-LOYALTY-SILVER"), upgradeFrom = listOf("VAS-LOYALTY-SILVER"),
            regulatory = ProductRegulatory(consentRequired = false, disclosureText = mapOf("AE" to "20% discount on your plan for 12 months.", "SA" to "خصم 20% على باقتك لمدة 12 شهر."), coolingOffHours = null),
            active = true
        ),
        VasProduct(
            sku = "VAS-STREAM-BASIC", name = "StreamBasic 1-month trial", nameAr = "ستريم بيسك - تجربة شهر",
            category = "streaming", margin = 6.00,
            markets = listOf(Market.UAE, Market.SAUDI, Market.KUWAIT, Market.BAHRAIN, Market.OMAN),
            eligibilityRules = emptyList(), bundleWith = emptyList(),
            incompatibleWith = listOf("VAS-STREAM-PLUS"), upgradeFrom = emptyList(),
            regulatory = ProductRegulatory(consentRequired = false, disclosureText = mapOf("AE" to "Free for 1 month, then AED 15/mo.", "SA" to "مجاني لمدة شهر، ثم 15 ريال/شهر."), coolingOffHours = 24),
            active = true
        ),
        VasProduct(
            sku = "VAS-FAMILY-PLAN", name = "Family Plan Add 3 Lines", nameAr = "باقة العائلة - أضف 3 خطوط",
            category = "family", margin = 20.00,
            markets = listOf(Market.UAE, Market.SAUDI),
            eligibilityRules = listOf("arpu > 150"), bundleWith = listOf("VAS-DATA-BOOST"),
            incompatibleWith = emptyList(), upgradeFrom = emptyList(),
            regulatory = ProductRegulatory(consentRequired = true, disclosureText = mapOf("AE" to "Add up to 3 family lines at 50% discount for 12 months.", "SA" to "أضف حتى 3 خطوط عائلية بخصم 50% لمدة 12 شهر."), coolingOffHours = 48),
            active = true
        )
    )
}
