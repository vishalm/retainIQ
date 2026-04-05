package com.retainiq.service.pipeline

import com.retainiq.api.dto.*
import com.retainiq.domain.*
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Stage 5 of the decisioning pipeline — Response Assembly.
 *
 * Assembles the final [DecideResponse][com.retainiq.api.dto.DecideResponse] from pipeline outputs.
 * Determines the recommended action:
 * - `present_offers` — normal path with one or more ranked offers
 * - `escalate` — critical churn with insufficient offers; route to supervisor
 * - `no_action` — no eligible offers found
 *
 * Sets the `fallback` flag when the response contains offers but the pipeline ran in degraded mode.
 *
 * **Latency budget: < 20 ms**
 */
@Component
class ResponseAssembler {

    /**
     * Builds the API response from the outputs of Stages 1--4.
     *
     * @param decisionId unique identifier for this decision
     * @param profile enriched subscriber profile
     * @param churnResult churn scoring output
     * @param rankedOffers scored and ranked offers
     * @param degraded whether any pipeline stage fell back
     * @param confidence overall decision confidence
     * @param latencyMs end-to-end pipeline latency
     * @return a fully populated [DecideResponse][com.retainiq.api.dto.DecideResponse]
     */
    fun assemble(
        decisionId: UUID,
        profile: SubscriberProfile,
        churnResult: ChurnResult,
        rankedOffers: List<RankedOffer>,
        degraded: Boolean,
        confidence: Confidence,
        latencyMs: Long
    ): DecideResponse {
        val action = when {
            rankedOffers.isEmpty() -> "no_action"
            churnResult.band == ChurnBand.CRITICAL && rankedOffers.size < 2 -> "escalate"
            else -> "present_offers"
        }

        return DecideResponse(
            decisionId = decisionId,
            subscriber = SubscriberSummaryDto(
                segment = profile.segment,
                churnScore = churnResult.score,
                churnBand = churnResult.band.name,
                tenureDays = profile.tenureDays
            ),
            offers = rankedOffers.map { offer ->
                OfferDto(
                    rank = offer.rank,
                    sku = offer.sku,
                    name = offer.name,
                    retentionProbability = offer.retentionProbability,
                    marginImpact = offer.marginImpact,
                    scriptHint = offer.scriptHint,
                    deepLink = offer.deepLink,
                    regulatory = offer.regulatory?.let {
                        RegulatoryDto(
                            consentRequired = it.consentRequired,
                            disclosure = it.disclosure,
                            coolingOffHours = it.coolingOffHours
                        )
                    }
                )
            },
            action = action,
            fallback = degraded && rankedOffers.isNotEmpty(),
            degraded = degraded,
            confidence = confidence.name,
            latencyMs = latencyMs
        )
    }
}
