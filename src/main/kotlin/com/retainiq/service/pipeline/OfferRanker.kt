package com.retainiq.service.pipeline

import com.retainiq.domain.*
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.min

private val logger = KotlinLogging.logger {}

/**
 * Stage 4 of the decisioning pipeline — Offer Ranking.
 *
 * Scores each candidate offer using a multi-objective formula with configurable weights:
 *
 * ```
 * score = alpha * retention_probability
 *       + beta  * normalized_margin
 *       - gamma * spend_cap_pressure
 *       + delta * context_match
 * ```
 *
 * Default weights (overridable via `retainiq.ranking.*` properties):
 * - alpha = 0.45 (retention probability)
 * - beta  = 0.30 (operator margin)
 * - gamma = 0.15 (spend-cap pressure penalty)
 * - delta = 0.10 (channel-context match bonus)
 *
 * **Latency budget: < 50 ms**
 *
 * @property alpha weight for retention probability
 * @property beta weight for normalized margin
 * @property gamma weight for spend-cap pressure (penalty, subtracted)
 * @property delta weight for channel-context match
 */
@Component
class OfferRanker(
    @Value("\${retainiq.ranking.alpha:0.45}") private val alpha: Double,
    @Value("\${retainiq.ranking.beta:0.30}") private val beta: Double,
    @Value("\${retainiq.ranking.gamma:0.15}") private val gamma: Double,
    @Value("\${retainiq.ranking.delta:0.10}") private val delta: Double
) {

    /**
     * Ranks candidate offers and returns the top [maxOffers] with scores, script hints, and deep links.
     *
     * @param candidates eligible VAS products from Stage 3
     * @param churnResult churn scoring output from Stage 2
     * @param profile enriched subscriber profile from Stage 1
     * @param channel the originating customer channel
     * @param maxOffers maximum number of offers to return
     * @return ranked list of [RankedOffer]s, best first
     */
    fun rank(
        candidates: List<VasProduct>,
        churnResult: ChurnResult,
        profile: SubscriberProfile,
        channel: Channel,
        maxOffers: Int
    ): List<RankedOffer> {
        if (candidates.isEmpty()) return emptyList()

        // Normalize margins for scoring
        val maxMargin = candidates.maxOf { it.margin }.coerceAtLeast(1.0)

        val scored = candidates.map { product ->
            val retentionP = estimateRetentionProbability(product, churnResult, profile)
            val marginNorm = product.margin / maxMargin
            val spendCapPressure = estimateSpendCapPressure(product, profile)
            val contextMatch = estimateContextMatch(product, channel, profile)

            val score = (alpha * retentionP) +
                        (beta * marginNorm) -
                        (gamma * spendCapPressure) +
                        (delta * contextMatch)

            ScoredProduct(product, retentionP, score)
        }

        val ranked = scored
            .sortedByDescending { it.score }
            .take(maxOffers)
            .mapIndexed { index, sp ->
                RankedOffer(
                    rank = index + 1,
                    sku = sp.product.sku,
                    name = sp.product.name,
                    retentionProbability = sp.retentionP,
                    marginImpact = sp.product.margin,
                    score = sp.score,
                    scriptHint = generateScriptHint(sp.product, profile, churnResult),
                    deepLink = "https://activate.retainiq.com/${sp.product.sku}",
                    regulatory = sp.product.regulatory.let {
                        OfferRegulatory(
                            consentRequired = it.consentRequired,
                            disclosure = it.disclosureText.values.firstOrNull(),
                            coolingOffHours = it.coolingOffHours
                        )
                    }
                )
            }

        logger.debug { "Ranked ${ranked.size} offers. Top: ${ranked.firstOrNull()?.sku} score=${ranked.firstOrNull()?.score}" }
        return ranked
    }

    /**
     * Estimates the probability that activating this product prevents churn.
     *
     * Base probability depends on churn band (critical = lower base). Category and upgrade-path
     * bonuses are added. Capped at 0.95.
     */
    private fun estimateRetentionProbability(product: VasProduct, churn: ChurnResult, profile: SubscriberProfile): Double {
        // Base retention probability varies by churn band and product category
        val base = when (churn.band) {
            ChurnBand.CRITICAL -> 0.35
            ChurnBand.HIGH -> 0.50
            ChurnBand.MEDIUM -> 0.65
            ChurnBand.LOW -> 0.80
        }

        // Category bonus
        val categoryBonus = when (product.category.lowercase()) {
            "streaming", "entertainment" -> 0.10
            "data" -> 0.08
            "international" -> if (profile.segment == "expat") 0.15 else 0.05
            "loyalty", "discount" -> 0.12
            else -> 0.0
        }

        // Upgrade path bonus (subscriber already has a lower-tier product)
        val upgradeBonus = if (product.upgradeFrom.isNotEmpty()) 0.05 else 0.0

        return min(base + categoryBonus + upgradeBonus, 0.95)
    }

    /**
     * Estimates spend-cap pressure: penalizes high-margin products for low-ARPU subscribers.
     *
     * Returns a value in `[0.0, 0.3]`; zero for subscribers with ARPU >= 100.
     */
    private fun estimateSpendCapPressure(product: VasProduct, profile: SubscriberProfile): Double {
        // Higher margin products for low-ARPU subscribers = more spend cap pressure
        val arpuRatio = profile.arpu / 200.0 // Normalize ARPU against typical high-value threshold
        return if (arpuRatio < 0.5) 0.3 * (1.0 - arpuRatio * 2) else 0.0
    }

    /**
     * Estimates how well a product fits the originating channel context.
     *
     * Digital channels (APP) favor streaming/data; IVR favors loyalty/discount;
     * agent channels (AGENTFORCE, GENESYS) are roughly neutral.
     */
    private fun estimateContextMatch(product: VasProduct, channel: Channel, profile: SubscriberProfile): Double {
        // Digital channels favor self-service products; voice channels favor agent-guided
        return when (channel) {
            Channel.APP -> if (product.category in listOf("streaming", "data", "entertainment")) 0.8 else 0.4
            Channel.IVR -> if (product.category in listOf("loyalty", "discount", "data")) 0.7 else 0.3
            Channel.AGENTFORCE, Channel.GENESYS -> 0.6 // Agent channels: all products roughly equally presentable
        }
    }

    /** Generates a natural-language script hint for agent-assisted channels, personalized by tenure and churn band. */
    private fun generateScriptHint(product: VasProduct, profile: SubscriberProfile, churn: ChurnResult): String {
        val tenure = when {
            profile.tenureDays > 1095 -> "a loyal customer for ${profile.tenureDays / 365} years"
            profile.tenureDays > 365 -> "with us for over a year"
            else -> "a valued customer"
        }

        val urgency = when (churn.band) {
            ChurnBand.CRITICAL -> "I really want to make sure we take care of you today."
            ChurnBand.HIGH -> "I'd love to offer you something special."
            else -> "I have a great option for you."
        }

        return "I can see you've been $tenure. $urgency We can activate ${product.name} " +
               "— it's available right now and I can set it up for you in just a moment."
    }

    private data class ScoredProduct(
        val product: VasProduct,
        val retentionP: Double,
        val score: Double
    )
}
