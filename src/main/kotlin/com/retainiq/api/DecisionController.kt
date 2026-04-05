package com.retainiq.api

import com.retainiq.api.dto.*
import com.retainiq.service.DecisionService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Primary REST API surface for the RetainIQ decisioning engine.
 *
 * Exposes three endpoints under `/v1`:
 * - `POST /v1/decide` — real-time churn-risk scoring and VAS offer ranking
 * - `POST /v1/outcome` — feedback loop for attribution and model retraining
 * - `POST /v1/catalog/sync` — HMAC-verified webhook for VAS catalog updates
 *
 * All endpoints require a valid JWT bearer token and an `X-Tenant-ID` header.
 * Metrics (latency, counters) are recorded via Micrometer for each call.
 *
 * @property decisionService orchestrates the 5-stage decisioning pipeline
 * @property meterRegistry Micrometer registry for recording operational metrics
 */
@RestController
@RequestMapping("/v1")
class DecisionController(
    private val decisionService: DecisionService,
    private val meterRegistry: MeterRegistry
) {

    /**
     * Real-time decision endpoint — scores churn risk and returns ranked VAS offers.
     *
     * Target latency: < 200 ms p99. The response includes a `X-Decision-ID` header
     * for correlation and a `X-Latency-Ms` header for client-side observability.
     *
     * @param request the decision request containing subscriber ID, channel, signals, and options
     * @param tenantId tenant identifier from the `X-Tenant-ID` request header
     * @return ranked offers wrapped in a [DecideResponse][com.retainiq.api.dto.DecideResponse]
     */
    @PostMapping("/decide")
    suspend fun decide(
        @Valid @RequestBody request: DecideRequest,
        @RequestHeader("X-Tenant-ID") tenantId: UUID
    ): ResponseEntity<DecideResponse> {
        val timer = Timer.start(meterRegistry)

        logger.info { "Decide request for subscriber=${request.subscriberId} tenant=$tenantId channel=${request.channel}" }

        val response = decisionService.decide(tenantId, request)

        timer.stop(meterRegistry.timer("retainiq.decide.duration", "tenant", tenantId.toString(), "channel", request.channel))
        meterRegistry.counter("retainiq.decide.total", "tenant", tenantId.toString(), "degraded", response.degraded.toString()).increment()

        return ResponseEntity.ok()
            .header("X-Decision-ID", response.decisionId.toString())
            .header("X-Latency-Ms", response.latencyMs.toString())
            .body(response)
    }

    /**
     * Records the outcome of a previously presented decision for attribution and model retraining.
     *
     * Channels call this endpoint after the subscriber accepts, declines, or ignores the offer.
     * Outcomes are persisted to PostgreSQL and fed back into the churn model training pipeline.
     *
     * @param request outcome details including the decision ID, selected SKU, and result
     * @param tenantId tenant identifier from the `X-Tenant-ID` request header
     * @return 204 No Content on success
     */
    @PostMapping("/outcome")
    suspend fun outcome(
        @Valid @RequestBody request: OutcomeRequest,
        @RequestHeader("X-Tenant-ID") tenantId: UUID
    ): ResponseEntity<Void> {
        logger.info { "Outcome for decision=${request.decisionId} sku=${request.offerSku} result=${request.outcome}" }

        decisionService.recordOutcome(tenantId, request)

        meterRegistry.counter("retainiq.outcome.total", "tenant", tenantId.toString(), "result", request.outcome).increment()

        return ResponseEntity.noContent().build()
    }

    /**
     * HMAC-verified webhook for VAS catalog updates.
     *
     * Called by the operator's catalog management system when products are created, updated,
     * or retired. The request body must be signed with HMAC-SHA256 and the signature passed
     * in the `X-Signature` header. Supports both incremental and full-sync modes.
     *
     * @param request catalog sync payload containing the event type and product list
     * @param tenantId tenant identifier from the `X-Tenant-ID` request header
     * @param signature HMAC-SHA256 signature of the request body
     * @return 202 Accepted with a sync ID for tracking
     */
    @PostMapping("/catalog/sync")
    suspend fun catalogSync(
        @Valid @RequestBody request: CatalogSyncRequest,
        @RequestHeader("X-Tenant-ID") tenantId: UUID,
        @RequestHeader("X-Signature") signature: String
    ): ResponseEntity<CatalogSyncResponse> {
        logger.info { "Catalog sync: event=${request.event} products=${request.products.size} fullSync=${request.fullSync}" }

        val response = decisionService.syncCatalog(tenantId, signature, request)

        meterRegistry.counter("retainiq.catalog.sync.total", "tenant", tenantId.toString(), "event", request.event).increment()

        return ResponseEntity.accepted().body(response)
    }
}
