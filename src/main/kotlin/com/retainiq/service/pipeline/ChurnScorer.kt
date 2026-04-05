package com.retainiq.service.pipeline

import com.retainiq.domain.*
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.max

private val logger = KotlinLogging.logger {}

/**
 * Stage 2 of the decisioning pipeline — Churn Scoring.
 *
 * Computes a churn probability in `[0.0, 1.0]` using five weighted feature groups:
 *
 * | Group | Weight | Inputs |
 * |-------|--------|--------|
 * | Usage | 35 % | 7-day data/voice usage deltas |
 * | Billing | 25 % | Bill shock, payment delay, disputes |
 * | Contact | 20 % | Contact frequency, prior churn intent |
 * | Lifecycle | 12 % | Tenure, contract end, upgrade staleness |
 * | Competitive | 8 % | Competitor mentions, port inquiries |
 *
 * The weighted sub-scores are combined and passed through a shifted sigmoid to produce
 * the final probability. In production, this is replaced by a LightGBM gradient-boosted
 * tree model; this implementation uses a logistic approximation suitable for demo and testing.
 *
 * **Latency budget: < 30 ms**
 */
@Component
class ChurnScorer {

    companion object {
        /** Weight for the usage-decline feature group (data + voice delta). */
        const val USAGE_WEIGHT = 0.35
        /** Weight for the billing-stress feature group (bill shock, payment delay, disputes). */
        const val BILLING_WEIGHT = 0.25
        /** Weight for the contact-frequency feature group (care contacts, churn intent). */
        const val CONTACT_WEIGHT = 0.20
        /** Weight for the lifecycle-risk feature group (tenure, contract, upgrade staleness). */
        const val LIFECYCLE_WEIGHT = 0.12
        /** Weight for the competitive-signals feature group (competitor mention, port inquiry). */
        const val COMPETITIVE_WEIGHT = 0.08
    }

    /**
     * Scores churn probability for the given subscriber profile.
     *
     * @param profile enriched subscriber profile from Stage 1
     * @return a [ChurnResult] containing score, band, and top risk factors
     */
    fun score(profile: SubscriberProfile): ChurnResult {
        // Compute sub-scores per feature group
        val usageScore = computeUsageScore(profile)
        val billingScore = computeBillingScore(profile)
        val contactScore = computeContactScore(profile)
        val lifecycleScore = computeLifecycleScore(profile)
        val competitiveScore = computeCompetitiveScore(profile)

        // Weighted combination
        val rawScore = (usageScore * USAGE_WEIGHT) +
                       (billingScore * BILLING_WEIGHT) +
                       (contactScore * CONTACT_WEIGHT) +
                       (lifecycleScore * LIFECYCLE_WEIGHT) +
                       (competitiveScore * COMPETITIVE_WEIGHT)

        // Sigmoid to normalize to [0, 1]
        val churnScore = sigmoid(rawScore)
        val band = toBand(churnScore)

        // Top risk factors (sorted by contribution)
        val factors = listOf(
            RiskFactor("usage_decline", usageScore * USAGE_WEIGHT),
            RiskFactor("billing_stress", billingScore * BILLING_WEIGHT),
            RiskFactor("contact_frequency", contactScore * CONTACT_WEIGHT),
            RiskFactor("lifecycle_risk", lifecycleScore * LIFECYCLE_WEIGHT),
            RiskFactor("competitive_signals", competitiveScore * COMPETITIVE_WEIGHT)
        ).sortedByDescending { it.impact }.take(3)

        logger.debug { "Churn score: $churnScore band=$band factors=${factors.map { it.name }}" }
        return ChurnResult(score = churnScore, band = band, topRiskFactors = factors)
    }

    /**
     * Returns a conservative medium-risk fallback when scoring fails.
     *
     * @param profile the subscriber profile (unused in fallback, kept for interface consistency)
     * @return a [ChurnResult] with score 0.5 and [ChurnBand.MEDIUM]
     */
    fun fallbackScore(profile: SubscriberProfile): ChurnResult {
        // Conservative fallback: medium risk
        return ChurnResult(
            score = 0.5,
            band = ChurnBand.MEDIUM,
            topRiskFactors = listOf(RiskFactor("fallback_estimate", 0.5))
        )
    }

    /** Computes usage-decline sub-score from 7-day data and voice deltas. Negative delta = higher risk. */
    private fun computeUsageScore(p: SubscriberProfile): Double {
        // Negative usage delta = higher churn risk
        val dataDelta = clamp(-p.dataUsageDelta7d / 50.0, 0.0, 1.0)  // -50% delta = max risk
        val voiceDelta = clamp(-p.voiceUsageDelta7d / 50.0, 0.0, 1.0)
        return (dataDelta * 0.6 + voiceDelta * 0.4)
    }

    /** Computes billing-stress sub-score from bill shock, payment delays, and dispute count. */
    private fun computeBillingScore(p: SubscriberProfile): Double {
        val billShockFactor = if (p.billShock) 0.5 else 0.0
        val paymentDelay = clamp(p.paymentDelayDays / 30.0, 0.0, 1.0)
        val disputes = clamp(p.disputeCount90d / 3.0, 0.0, 1.0)
        return (billShockFactor + paymentDelay * 0.3 + disputes * 0.2)
    }

    /** Computes contact-frequency sub-score from care contacts and prior churn intent signals. */
    private fun computeContactScore(p: SubscriberProfile): Double {
        val contactFreq = clamp(p.contacts30d / 5.0, 0.0, 1.0)
        val churnIntent = if (p.priorChurnIntent) 0.6 else 0.0
        return (contactFreq * 0.4 + churnIntent)
    }

    /** Computes lifecycle-risk sub-score. Short tenure, near-expiry contracts, and stale upgrades increase risk. */
    private fun computeLifecycleScore(p: SubscriberProfile): Double {
        // Short tenure OR near contract end = higher risk
        val tenureRisk = clamp(1.0 - (p.tenureDays / 730.0), 0.0, 1.0) // <2 years = risk
        val contractRisk = p.contractDaysRemaining?.let { clamp(1.0 - (it / 90.0), 0.0, 1.0) } ?: 0.3
        val upgradeStale = p.lastUpgradeDays?.let { clamp(it / 365.0, 0.0, 1.0) } ?: 0.5
        return (tenureRisk * 0.4 + contractRisk * 0.4 + upgradeStale * 0.2)
    }

    /** Computes competitive-signals sub-score from competitor mentions and port inquiries. */
    private fun computeCompetitiveScore(p: SubscriberProfile): Double {
        var score = 0.0
        if (p.competitorMention) score += 0.5
        if (p.portInquiry) score += 0.5
        return clamp(score, 0.0, 1.0)
    }

    /** Shifted sigmoid function that maps the weighted score to a `[0, 1]` probability. */
    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x * 4 + 2))

    /** Maps a continuous churn score to a discrete [ChurnBand]. */
    private fun toBand(score: Double): ChurnBand = when {
        score < 0.3 -> ChurnBand.LOW
        score < 0.6 -> ChurnBand.MEDIUM
        score < 0.8 -> ChurnBand.HIGH
        else -> ChurnBand.CRITICAL
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
