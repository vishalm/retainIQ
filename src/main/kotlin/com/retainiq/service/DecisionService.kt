package com.retainiq.service

import com.retainiq.api.dto.*
import com.retainiq.domain.*
import com.retainiq.exception.*
import com.retainiq.observability.RetainIQMetrics
import com.retainiq.service.pipeline.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates the 5-stage VAS offer decisioning pipeline with graceful degradation.
 *
 * Each stage has a latency budget, and failures are caught individually so that the
 * pipeline continues with degraded (but safe) fallback data rather than failing outright:
 *
 * 1. **Signal Enrichment** (< 20 ms) — load/merge subscriber profile
 * 2. **Churn Scoring** (< 30 ms) — compute churn probability
 * 3. **Offer Candidacy** (< 40 ms) — filter eligible VAS products
 * 4. **Offer Ranking** (< 50 ms) — multi-objective scoring
 * 5. **Response Assembly** (< 20 ms) — build API response with scripts and audit data
 *
 * Decisions are persisted asynchronously to PostgreSQL for audit compliance.
 *
 * @see com.retainiq.service.pipeline.SignalEnricher
 * @see com.retainiq.service.pipeline.ChurnScorer
 * @see com.retainiq.service.pipeline.OfferCandidacy
 * @see com.retainiq.service.pipeline.OfferRanker
 * @see com.retainiq.service.pipeline.ResponseAssembler
 */
@Service
class DecisionService(
    private val enricher: SignalEnricher,
    private val scorer: ChurnScorer,
    private val candidacy: OfferCandidacy,
    private val ranker: OfferRanker,
    private val assembler: ResponseAssembler,
    private val decisionRepository: DecisionRepository,
    private val outcomeRepository: OutcomeRepository,
    private val catalogService: CatalogService,
    private val tenantService: TenantService,
    private val metrics: RetainIQMetrics
) {

    /**
     * Executes the full 5-stage decisioning pipeline and returns ranked VAS offers.
     *
     * @param tenantId the authenticated tenant
     * @param request the inbound decision request from the channel
     * @return a [DecideResponse][com.retainiq.api.dto.DecideResponse] with ranked offers
     * @throws TenantNotFoundException if the tenant ID is invalid
     */
    suspend fun decide(tenantId: UUID, request: DecideRequest): DecideResponse {
        val startTime = System.currentTimeMillis()
        val decisionId = UUID.randomUUID()
        var degraded = false
        var confidence = Confidence.HIGH

        // Validate tenant
        val tenant = tenantService.getTenant(tenantId)
            ?: throw TenantNotFoundException("Tenant $tenantId not found")

        val channel = parseChannel(request.channel)
        val market = request.context?.market?.let { parseMarket(it) } ?: tenant.market

        // Stage 1: Signal Enrichment (<20ms)
        val enrichSample = metrics.timerEnrich(tenantId.toString())
        val profile = try {
            enricher.enrich(tenantId, request.subscriberId, request.signals)
        } catch (e: Exception) {
            logger.warn(e) { "Enrichment degraded for subscriber=${request.subscriberId}" }
            degraded = true
            confidence = Confidence.LOW
            enricher.fallbackProfile(request.subscriberId, request.signals)
        }
        metrics.recordEnrich(enrichSample, tenantId.toString(), cacheHit = !degraded)

        // Stage 2: Churn Scoring (<30ms)
        val scoreSample = metrics.timerScore()
        val churnResult = try {
            scorer.score(profile)
        } catch (e: Exception) {
            logger.warn(e) { "Churn scoring degraded" }
            degraded = true
            confidence = Confidence.LOW
            scorer.fallbackScore(profile)
        }
        metrics.recordScore(scoreSample, tenantId.toString())
        metrics.recordChurnScore(churnResult.score, churnResult.band.name, tenantId.toString())

        // Stage 3: Offer Candidacy (<40ms)
        val candidacySample = metrics.timerCandidacy()
        val maxOffers = request.options?.maxOffers ?: 3
        val candidates = try {
            candidacy.findCandidates(tenantId, profile, churnResult, market, maxOffers * 3)
        } catch (e: Exception) {
            logger.warn(e) { "Candidacy degraded, using generic offers" }
            degraded = true
            confidence = Confidence.LOW
            candidacy.fallbackCandidates(tenantId, market)
        }
        metrics.recordCandidacy(candidacySample, tenantId.toString(), candidates.size)

        // Stage 4: Offer Ranking (<50ms)
        val rankSample = metrics.timerRank()
        val rankedOffers = ranker.rank(
            candidates = candidates,
            churnResult = churnResult,
            profile = profile,
            channel = channel,
            maxOffers = maxOffers
        )
        metrics.recordRank(rankSample, tenantId.toString(), rankedOffers.size)

        // Stage 5: Response Assembly (<20ms)
        val assembleSample = metrics.timerAssemble()
        val latencyMs = System.currentTimeMillis() - startTime
        val response = assembler.assemble(
            decisionId = decisionId,
            profile = profile,
            churnResult = churnResult,
            rankedOffers = rankedOffers,
            degraded = degraded,
            confidence = confidence,
            latencyMs = latencyMs
        )
        metrics.recordAssemble(assembleSample, tenantId.toString())

        metrics.recordDecision(tenantId.toString(), channel.name, churnResult.band.name, degraded, rankedOffers.size)
        metrics.recordLatencySLO(latencyMs, tenantId.toString())

        // Async: persist decision for audit
        decisionRepository.saveAsync(
            Decision(
                id = decisionId,
                tenantId = tenantId,
                subscriberHash = profile.subscriberHash,
                channel = channel,
                churnScore = churnResult.score,
                churnBand = churnResult.band,
                offersRanked = rankedOffers,
                rulesApplied = candidates.map { it.sku },
                degraded = degraded,
                confidence = confidence,
                latencyMs = latencyMs,
                createdAt = Instant.now()
            )
        )

        logger.info { "Decision $decisionId: churn=${churnResult.band} offers=${rankedOffers.size} degraded=$degraded latency=${latencyMs}ms" }
        return response
    }

    /**
     * Records the outcome of a previously presented decision.
     *
     * Persists the outcome to PostgreSQL for attribution analysis and model retraining.
     *
     * @param tenantId the authenticated tenant
     * @param request outcome details (decision ID, SKU, result)
     * @throws TenantNotFoundException if the tenant ID is invalid
     */
    suspend fun recordOutcome(tenantId: UUID, request: OutcomeRequest) {
        tenantService.getTenant(tenantId)
            ?: throw TenantNotFoundException("Tenant $tenantId not found")

        val outcome = Outcome(
            id = UUID.randomUUID(),
            decisionId = request.decisionId,
            offerSku = request.offerSku,
            result = parseOutcome(request.outcome),
            revenueDelta = request.revenueDelta,
            churnPrevented = request.churnPrevented,
            createdAt = Instant.now()
        )

        outcomeRepository.save(tenantId, outcome)
        metrics.recordOutcome(tenantId.toString(), request.outcome, request.offerSku)
        logger.info { "Outcome recorded: decision=${request.decisionId} sku=${request.offerSku} result=${request.outcome}" }
    }

    /**
     * Processes an HMAC-verified VAS catalog sync webhook.
     *
     * @param tenantId the authenticated tenant
     * @param signature HMAC-SHA256 signature from the `X-Signature` header
     * @param request the catalog sync payload
     * @return a [CatalogSyncResponse][com.retainiq.api.dto.CatalogSyncResponse] with the sync ID
     * @throws TenantNotFoundException if the tenant ID is invalid
     * @throws InvalidSignatureException if the HMAC signature does not match
     */
    suspend fun syncCatalog(tenantId: UUID, signature: String, request: CatalogSyncRequest): CatalogSyncResponse {
        tenantService.getTenant(tenantId)
            ?: throw TenantNotFoundException("Tenant $tenantId not found")

        catalogService.verifySyncSignature(tenantId, signature, request)
        val syncId = catalogService.processCatalogSync(tenantId, request)
        metrics.recordCatalogSync(tenantId.toString(), request.products.size, request.event)

        return CatalogSyncResponse(
            syncId = syncId,
            status = "accepted",
            productsReceived = request.products.size
        )
    }

    private fun parseChannel(channel: String): Channel = when (channel.lowercase()) {
        "agentforce" -> Channel.AGENTFORCE
        "genesys" -> Channel.GENESYS
        "app" -> Channel.APP
        "ivr" -> Channel.IVR
        else -> Channel.APP
    }

    private fun parseMarket(market: String): Market = when (market.uppercase()) {
        "AE" -> Market.UAE
        "SA" -> Market.SAUDI
        "KW" -> Market.KUWAIT
        "BH" -> Market.BAHRAIN
        "OM" -> Market.OMAN
        else -> Market.UAE
    }

    private fun parseOutcome(outcome: String): OutcomeResult = when (outcome.lowercase()) {
        "accepted" -> OutcomeResult.ACCEPTED
        "declined" -> OutcomeResult.DECLINED
        else -> OutcomeResult.NO_RESPONSE
    }
}
