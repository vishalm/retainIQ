/**
 * Pure domain models for the RetainIQ decisioning engine.
 *
 * All classes in this file are plain Kotlin data classes with no framework dependencies.
 * They represent the core entities that flow through the 5-stage decisioning pipeline:
 * tenants, subscriber profiles, VAS products, decisions, outcomes, and churn results.
 */
package com.retainiq.domain

import java.time.Instant
import java.util.UUID

// ---------------------------------------------------------------------------
// Tenant
// ---------------------------------------------------------------------------

/**
 * Represents an operator tenant with market and regulatory configuration.
 *
 * Each tenant maps to a single telecom operator (e.g. du, STC, Zain). The tenant
 * record controls which market rules apply, where catalog webhooks are sent, and
 * how long audit data is retained.
 *
 * @property id unique tenant identifier (UUID)
 * @property name human-readable operator name
 * @property market primary MENA market for this tenant
 * @property regulatoryProfile market-specific regulatory constraints
 * @property catalogWebhookUrl optional URL for outbound catalog sync notifications
 * @property status lifecycle status of the tenant
 * @property createdAt timestamp when the tenant was provisioned
 */
data class Tenant(
    val id: UUID,
    val name: String,
    val market: Market,
    val regulatoryProfile: RegulatoryProfile,
    val catalogWebhookUrl: String?,
    val status: TenantStatus,
    val createdAt: Instant
)

/**
 * Lifecycle status of a [Tenant].
 *
 * - [PROVISIONING] — tenant record created but not yet fully configured
 * - [ACTIVE] — tenant is live and may call the decisioning API
 * - [SUSPENDED] — tenant access is temporarily revoked (billing, compliance, etc.)
 */
enum class TenantStatus { PROVISIONING, ACTIVE, SUSPENDED }

/**
 * MENA markets supported by RetainIQ.
 *
 * Each entry carries an ISO 3166-1 alpha-2 [code] used for regulatory lookups
 * and catalog filtering.
 */
enum class Market(val code: String) {
    /** United Arab Emirates */
    UAE("AE"),
    /** Kingdom of Saudi Arabia */
    SAUDI("SA"),
    /** Kuwait */
    KUWAIT("KW"),
    /** Bahrain */
    BAHRAIN("BH"),
    /** Oman */
    OMAN("OM")
}

/**
 * Market-specific regulatory constraints applied to a tenant.
 *
 * @property requireArabicDisclosure whether offers must include Arabic disclosure text (mandatory for KSA)
 * @property consentRequired whether explicit subscriber consent is required before activation
 * @property coolingOffHours mandatory cooling-off period (hours) after VAS activation
 * @property auditRetentionMonths how long decision and outcome records must be retained (default 24 months)
 */
data class RegulatoryProfile(
    val requireArabicDisclosure: Boolean,
    val consentRequired: Boolean,
    val coolingOffHours: Int,
    val auditRetentionMonths: Int = 24
)

// ---------------------------------------------------------------------------
// Subscriber
// ---------------------------------------------------------------------------

/**
 * Enriched subscriber data assembled from BSS/CRM cache for churn scoring.
 *
 * This profile is the primary input to the [com.retainiq.service.pipeline.ChurnScorer].
 * Fields map to the five feature groups defined in the churn model:
 * usage, billing, contact, lifecycle, and competitive signals.
 *
 * @property subscriberHash HMAC-SHA256 hash of the subscriber's MSISDN (PII-safe)
 * @property segment CRM segment label (e.g. "postpaid_premium", "prepaid_youth")
 * @property tenureDays number of days since the subscriber's activation date
 * @property arpu average revenue per user (monthly, local currency)
 * @property contractDaysRemaining days until contract expiry, or null if month-to-month
 * @property lastUpgradeDays days since the subscriber's last plan upgrade, or null if never upgraded
 * @property dataUsageDelta7d percentage change in data usage over the last 7 days
 * @property voiceUsageDelta7d percentage change in voice usage over the last 7 days
 * @property billShock whether the subscriber experienced a billing spike last cycle
 * @property paymentDelayDays number of days the most recent payment was overdue
 * @property disputeCount90d billing disputes raised in the last 90 days
 * @property contacts30d number of customer-care contacts in the last 30 days
 * @property priorChurnIntent whether the subscriber has previously expressed cancellation intent
 * @property competitorMention whether a competitor was mentioned in recent interactions
 * @property portInquiry whether the subscriber has inquired about number portability
 */
data class SubscriberProfile(
    val subscriberHash: String,
    val segment: String,
    val tenureDays: Int,
    val arpu: Double,
    val contractDaysRemaining: Int?,
    val lastUpgradeDays: Int?,
    val dataUsageDelta7d: Double,
    val voiceUsageDelta7d: Double,
    val billShock: Boolean,
    val paymentDelayDays: Int,
    val disputeCount90d: Int,
    val contacts30d: Int,
    val priorChurnIntent: Boolean,
    val competitorMention: Boolean,
    val portInquiry: Boolean
)

// ---------------------------------------------------------------------------
// VAS Product (catalog graph node)
// ---------------------------------------------------------------------------

/**
 * A node in the VAS catalog graph with regulatory metadata and relationship edges.
 *
 * Products form a directed graph through [bundleWith], [incompatibleWith], and [upgradeFrom]
 * edges. The [com.retainiq.service.pipeline.OfferCandidacy] stage traverses these edges to
 * enforce catalog constraints during offer selection.
 *
 * @property sku unique stock-keeping unit identifier (e.g. "VAS-STREAM-PLUS")
 * @property name English display name
 * @property nameAr Arabic display name, required for KSA market compliance
 * @property category product category used for scoring context match (e.g. "streaming", "data", "loyalty")
 * @property margin operator margin per activation (local currency)
 * @property markets list of [Market]s where this product is available
 * @property eligibilityRules JSON DSL rules evaluated during candidacy (e.g. "tenure_days > 365")
 * @property bundleWith SKUs that can be bundled with this product
 * @property incompatibleWith SKUs that cannot coexist with this product
 * @property upgradeFrom SKUs that this product is a natural upgrade from
 * @property regulatory per-product regulatory metadata
 * @property active whether this product is currently available for offer
 */
data class VasProduct(
    val sku: String,
    val name: String,
    val nameAr: String?,
    val category: String,
    val margin: Double,
    val markets: List<Market>,
    val eligibilityRules: List<String>,
    val bundleWith: List<String>,
    val incompatibleWith: List<String>,
    val upgradeFrom: List<String>,
    val regulatory: ProductRegulatory,
    val active: Boolean
)

/**
 * Regulatory metadata attached to a [VasProduct].
 *
 * @property consentRequired whether explicit subscriber consent is needed before activation
 * @property disclosureText market-code-keyed disclosure text (e.g. "AE" -> English, "SA" -> Arabic)
 * @property coolingOffHours product-specific cooling-off period, overrides the tenant default if set
 */
data class ProductRegulatory(
    val consentRequired: Boolean,
    val disclosureText: Map<String, String>,
    val coolingOffHours: Int?
)

// ---------------------------------------------------------------------------
// Decision
// ---------------------------------------------------------------------------

/**
 * An immutable audit record of a single decisioning event.
 *
 * Created at the end of the 5-stage pipeline and persisted asynchronously to PostgreSQL
 * for regulatory audit compliance (24-month retention). Every field is captured at decision
 * time to enable post-hoc analysis and model retraining.
 *
 * @property id unique decision identifier returned in the API response header
 * @property tenantId owning tenant
 * @property subscriberHash PII-safe subscriber identifier
 * @property channel the customer-facing channel that triggered the decision
 * @property churnScore raw churn probability [0.0, 1.0]
 * @property churnBand discretized churn risk band
 * @property offersRanked the ranked list of offers presented to the subscriber
 * @property rulesApplied SKUs of products that passed the candidacy filter
 * @property degraded true if any pipeline stage fell back to a degraded path
 * @property confidence overall confidence in the decision quality
 * @property latencyMs end-to-end pipeline latency in milliseconds
 * @property createdAt timestamp when the decision was made
 */
data class Decision(
    val id: UUID,
    val tenantId: UUID,
    val subscriberHash: String,
    val channel: Channel,
    val churnScore: Double,
    val churnBand: ChurnBand,
    val offersRanked: List<RankedOffer>,
    val rulesApplied: List<String>,
    val degraded: Boolean,
    val confidence: Confidence,
    val latencyMs: Long,
    val createdAt: Instant
)

/**
 * Customer-facing channel that triggered the decisioning request.
 *
 * - [AGENTFORCE] — Salesforce Agentforce integration
 * - [GENESYS] — Genesys Cloud contact centre
 * - [APP] — operator mobile/web app (self-service)
 * - [IVR] — interactive voice response system
 */
enum class Channel { AGENTFORCE, GENESYS, APP, IVR }

/**
 * Discretized churn risk band derived from the raw churn score.
 *
 * | Band | Score range |
 * |------|-------------|
 * | [LOW] | < 0.3 |
 * | [MEDIUM] | 0.3 -- 0.6 |
 * | [HIGH] | 0.6 -- 0.8 |
 * | [CRITICAL] | >= 0.8 |
 */
enum class ChurnBand { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * Overall confidence in the decision quality.
 *
 * - [HIGH] — all pipeline stages completed normally
 * - [MEDIUM] — minor degradation (e.g. stale cache data)
 * - [LOW] — one or more stages fell back to degraded paths
 */
enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * A scored offer with rank, script hint, and deep link.
 *
 * Produced by [com.retainiq.service.pipeline.OfferRanker] and included in the
 * [DecideResponse][com.retainiq.api.dto.DecideResponse].
 *
 * @property rank 1-based rank within the decision (1 = best)
 * @property sku product SKU
 * @property name product display name
 * @property retentionProbability estimated probability that this offer prevents churn [0.0, 1.0]
 * @property marginImpact operator margin per activation
 * @property score composite ranking score
 * @property scriptHint suggested agent script snippet for voice/chat channels
 * @property deepLink activation URL for self-service channels
 * @property regulatory per-offer regulatory metadata for the agent/UI
 */
data class RankedOffer(
    val rank: Int,
    val sku: String,
    val name: String,
    val retentionProbability: Double,
    val marginImpact: Double,
    val score: Double,
    val scriptHint: String?,
    val deepLink: String?,
    val regulatory: OfferRegulatory?
)

/**
 * Regulatory metadata surfaced alongside a [RankedOffer].
 *
 * @property consentRequired whether the agent must obtain explicit consent
 * @property disclosure localized disclosure text to read/display
 * @property coolingOffHours cooling-off period the subscriber may exercise after activation
 */
data class OfferRegulatory(
    val consentRequired: Boolean,
    val disclosure: String?,
    val coolingOffHours: Int?
)

// ---------------------------------------------------------------------------
// Outcome
// ---------------------------------------------------------------------------

/**
 * Feedback record capturing what happened after a decision was presented.
 *
 * Outcomes close the attribution loop and feed the churn-model retraining pipeline.
 * Persisted to PostgreSQL alongside the originating [Decision].
 *
 * @property id unique outcome identifier
 * @property decisionId the [Decision.id] this outcome refers to
 * @property offerSku the SKU of the offer the subscriber responded to
 * @property result whether the offer was accepted, declined, or received no response
 * @property revenueDelta incremental revenue attributed to this outcome (nullable)
 * @property churnPrevented whether churn was prevented (nullable, may be unknown at recording time)
 * @property createdAt timestamp when the outcome was recorded
 */
data class Outcome(
    val id: UUID,
    val decisionId: UUID,
    val offerSku: String,
    val result: OutcomeResult,
    val revenueDelta: Double?,
    val churnPrevented: Boolean?,
    val createdAt: Instant
)

/**
 * Result of a subscriber's response to a presented offer.
 *
 * - [ACCEPTED] — subscriber activated the offer
 * - [DECLINED] — subscriber explicitly refused
 * - [NO_RESPONSE] — no response within the attribution window
 */
enum class OutcomeResult { ACCEPTED, DECLINED, NO_RESPONSE }

// ---------------------------------------------------------------------------
// Churn Score Result
// ---------------------------------------------------------------------------

/**
 * Output of the churn scoring model.
 *
 * Produced by [com.retainiq.service.pipeline.ChurnScorer] and consumed by downstream
 * pipeline stages (candidacy, ranking, response assembly).
 *
 * @property score raw churn probability in [0.0, 1.0]
 * @property band discretized [ChurnBand]
 * @property topRiskFactors the top contributing risk factors, sorted by descending impact
 */
data class ChurnResult(
    val score: Double,
    val band: ChurnBand,
    val topRiskFactors: List<RiskFactor>
)

/**
 * A single risk factor contributing to the churn score.
 *
 * @property name human-readable factor name (e.g. "usage_decline", "billing_stress")
 * @property impact weighted contribution of this factor to the overall churn score
 */
data class RiskFactor(
    val name: String,
    val impact: Double
)
