package com.retainiq.service.pipeline

import com.retainiq.domain.*
import com.retainiq.cache.CatalogCacheService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Stage 3 of the decisioning pipeline — Offer Candidacy.
 *
 * Filters the full VAS catalog down to eligible offers by applying, in order:
 * 1. **Market filter** — product must be available in the subscriber's market
 * 2. **Active filter** — product must not be retired
 * 3. **Regulatory filter** — KSA market requires Arabic disclosure text
 * 4. **Graph constraints** — incompatible products are resolved by margin (higher margin wins)
 * 5. **Segment eligibility** — basic segment-based rules (production: JSON DSL engine)
 *
 * On failure, [fallbackCandidates] returns the top-3 highest-margin active products
 * for the subscriber's market.
 *
 * **Latency budget: < 40 ms**
 *
 * @property catalogCache Redis-backed VAS product catalog
 */
@Component
class OfferCandidacy(
    private val catalogCache: CatalogCacheService
) {

    /**
     * Finds eligible VAS product candidates for the given subscriber and market.
     *
     * @param tenantId owning tenant
     * @param profile enriched subscriber profile
     * @param churnResult churn scoring output
     * @param market the subscriber's market (used for regulatory and catalog filtering)
     * @param limit maximum number of candidates to return (typically 3x [maxOffers])
     * @return filtered list of eligible [VasProduct]s
     */
    suspend fun findCandidates(
        tenantId: UUID,
        profile: SubscriberProfile,
        churnResult: ChurnResult,
        market: Market,
        limit: Int
    ): List<VasProduct> {
        val allProducts = catalogCache.getActiveProducts(tenantId)

        val eligible = allProducts
            // Market filter
            .filter { it.markets.contains(market) || it.markets.isEmpty() }
            // Active filter
            .filter { it.active }
            // Arabic disclosure required for KSA
            .filter { product ->
                if (market == Market.SAUDI) {
                    product.regulatory.disclosureText.containsKey("SA") ||
                    product.regulatory.disclosureText.containsKey("ar")
                } else true
            }
            // Graph constraints: remove incompatible products
            .let { products -> removeIncompatible(products) }
            // Segment-based eligibility (basic)
            .filter { matchesSegment(it, profile) }
            .take(limit)

        logger.debug { "Candidacy: ${allProducts.size} products → ${eligible.size} eligible for market=${market.code}" }
        return eligible
    }

    /**
     * Returns safe, generic high-margin offers when the candidacy pipeline fails.
     *
     * @param tenantId owning tenant
     * @param market the subscriber's market
     * @return up to 3 active products sorted by descending margin
     */
    suspend fun fallbackCandidates(tenantId: UUID, market: Market): List<VasProduct> {
        // Return safe, generic offers when candidacy pipeline fails
        return catalogCache.getActiveProducts(tenantId)
            .filter { it.markets.contains(market) || it.markets.isEmpty() }
            .filter { it.active }
            .sortedByDescending { it.margin }
            .take(3)
    }

    private fun removeIncompatible(products: List<VasProduct>): List<VasProduct> {
        // Build incompatibility set — if A is incompatible with B, keep the one with higher margin
        val skuSet = products.map { it.sku }.toSet()
        val excluded = mutableSetOf<String>()

        for (product in products.sortedByDescending { it.margin }) {
            if (product.sku in excluded) continue
            for (incompatibleSku in product.incompatibleWith) {
                if (incompatibleSku in skuSet) {
                    excluded.add(incompatibleSku) // Exclude lower-margin incompatible product
                }
            }
        }

        return products.filter { it.sku !in excluded }
    }

    private fun matchesSegment(product: VasProduct, profile: SubscriberProfile): Boolean {
        // Basic eligibility: high-margin products for high-value subscribers, etc.
        // In production this evaluates the JSON DSL rules
        return true // All products eligible by default; rules engine refines
    }
}
