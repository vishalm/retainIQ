/**
 * Data transfer objects for the `/v1/decide` endpoint.
 *
 * Contains the request model ([DecideRequest]) with inline signal, context, and option payloads,
 * and the response model ([DecideResponse]) with subscriber summary, ranked offers, and regulatory metadata.
 */
package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.util.UUID

/**
 * Inbound request for the `/v1/decide` endpoint.
 *
 * @property subscriberId raw subscriber identifier (MSISDN or alias); hashed server-side
 * @property channel originating channel (agentforce, genesys, app, ivr)
 * @property signals optional real-time signals from the channel
 * @property context optional call/session context (reason code, market override)
 * @property options optional request-level overrides (max offers, explain, dry run)
 */
data class DecideRequest(
    @field:NotBlank
    @JsonProperty("subscriber_id")
    val subscriberId: String,

    val channel: String = "app",

    val signals: SignalsDto? = null,
    val context: ContextDto? = null,
    val options: OptionsDto? = null
)

/**
 * Real-time signals captured from the active channel session.
 *
 * @property frustrationScore sentiment/frustration score from NLP analysis [0.0, 1.0]
 * @property intent detected customer intent (e.g. "cancel", "competitor", "upgrade")
 * @property sessionDurationS session duration in seconds at the time of the request
 * @property priorContacts30d number of customer-care contacts in the last 30 days
 */
data class SignalsDto(
    @JsonProperty("frustration_score")
    val frustrationScore: Double? = null,
    val intent: String? = null,
    @JsonProperty("session_duration_s")
    val sessionDurationS: Int? = null,
    @JsonProperty("prior_contacts_30d")
    val priorContacts30d: Int? = null
)

/**
 * Optional call/session context for the decision request.
 *
 * @property reasonCode CRM reason code for the current interaction
 * @property market ISO 3166-1 alpha-2 market override (defaults to tenant's primary market)
 */
data class ContextDto(
    @JsonProperty("reason_code")
    val reasonCode: String? = null,
    val market: String? = null
)

/**
 * Request-level options for tuning decisioning behavior.
 *
 * @property maxOffers maximum number of ranked offers to return (1--10, default 3)
 * @property explain if true, include detailed scoring explanations in the response
 * @property dryRun if true, execute the pipeline but do not persist the decision
 */
data class OptionsDto(
    @JsonProperty("max_offers")
    @field:Min(1) @field:Max(10)
    val maxOffers: Int = 3,
    val explain: Boolean = false,
    @JsonProperty("dry_run")
    val dryRun: Boolean = false
)

/**
 * Response from the `/v1/decide` endpoint containing ranked VAS offers.
 *
 * @property decisionId unique identifier for this decision (also in `X-Decision-ID` header)
 * @property subscriber summary of the subscriber's churn profile
 * @property offers ranked list of VAS offers, best first
 * @property action recommended action: "present_offers", "escalate", or "no_action"
 * @property fallback true if the response contains offers but the pipeline ran in degraded mode
 * @property degraded true if any pipeline stage fell back to a degraded path
 * @property confidence overall confidence level (HIGH, MEDIUM, LOW)
 * @property latencyMs end-to-end pipeline latency in milliseconds
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DecideResponse(
    @JsonProperty("decision_id")
    val decisionId: UUID,
    val subscriber: SubscriberSummaryDto,
    val offers: List<OfferDto>,
    val action: String,
    val fallback: Boolean = false,
    val degraded: Boolean = false,
    val confidence: String,
    @JsonProperty("latency_ms")
    val latencyMs: Long
)

/**
 * Summary of the subscriber's churn risk profile included in the decision response.
 *
 * @property segment CRM segment label
 * @property churnScore raw churn probability [0.0, 1.0]
 * @property churnBand discretized churn band (LOW, MEDIUM, HIGH, CRITICAL)
 * @property tenureDays subscriber tenure in days
 */
data class SubscriberSummaryDto(
    val segment: String,
    @JsonProperty("churn_score")
    val churnScore: Double,
    @JsonProperty("churn_band")
    val churnBand: String,
    @JsonProperty("tenure_days")
    val tenureDays: Int
)

/**
 * A single ranked offer in the decision response.
 *
 * @property rank 1-based rank (1 = best)
 * @property sku product SKU
 * @property name product display name
 * @property retentionProbability estimated probability this offer prevents churn
 * @property marginImpact operator margin per activation
 * @property scriptHint suggested agent script snippet (null for self-service channels)
 * @property deepLink activation URL for self-service channels
 * @property regulatory per-offer regulatory metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OfferDto(
    val rank: Int,
    val sku: String,
    val name: String,
    @JsonProperty("retention_probability")
    val retentionProbability: Double,
    @JsonProperty("margin_impact")
    val marginImpact: Double,
    @JsonProperty("script_hint")
    val scriptHint: String? = null,
    @JsonProperty("deep_link")
    val deepLink: String? = null,
    val regulatory: RegulatoryDto? = null
)

/**
 * Regulatory metadata for a single offer, surfaced to the agent or self-service UI.
 *
 * @property consentRequired whether explicit subscriber consent is needed
 * @property disclosure localized disclosure text to read/display
 * @property coolingOffHours cooling-off period after activation
 */
data class RegulatoryDto(
    @JsonProperty("consent_required")
    val consentRequired: Boolean,
    val disclosure: String? = null,
    @JsonProperty("cooling_off_hours")
    val coolingOffHours: Int? = null
)
