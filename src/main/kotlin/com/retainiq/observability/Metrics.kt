package com.retainiq.observability

import io.micrometer.core.instrument.*
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Centralized custom metrics for RetainIQ observability.
 *
 * Covers the decision pipeline stages, cache performance, churn model output,
 * and business KPIs (offer attach rate, revenue impact).
 */
@Component
class RetainIQMetrics(private val registry: MeterRegistry) {

    // --- Pipeline Stage Timers ---

    fun timerEnrich(tenantId: String): Timer.Sample = Timer.start(registry)

    fun recordEnrich(sample: Timer.Sample, tenantId: String, cacheHit: Boolean) {
        sample.stop(Timer.builder("retainiq.pipeline.enrich.duration")
            .tag("tenant", tenantId)
            .tag("cache_hit", cacheHit.toString())
            .publishPercentileHistogram()
            .register(registry))
    }

    fun timerScore(): Timer.Sample = Timer.start(registry)

    fun recordScore(sample: Timer.Sample, tenantId: String) {
        sample.stop(Timer.builder("retainiq.pipeline.score.duration")
            .tag("tenant", tenantId)
            .publishPercentileHistogram()
            .register(registry))
    }

    fun timerCandidacy(): Timer.Sample = Timer.start(registry)

    fun recordCandidacy(sample: Timer.Sample, tenantId: String, candidateCount: Int) {
        sample.stop(Timer.builder("retainiq.pipeline.candidacy.duration")
            .tag("tenant", tenantId)
            .publishPercentileHistogram()
            .register(registry))
        registry.gauge("retainiq.pipeline.candidacy.count", Tags.of("tenant", tenantId), candidateCount)
    }

    fun timerRank(): Timer.Sample = Timer.start(registry)

    fun recordRank(sample: Timer.Sample, tenantId: String, offerCount: Int) {
        sample.stop(Timer.builder("retainiq.pipeline.rank.duration")
            .tag("tenant", tenantId)
            .publishPercentileHistogram()
            .register(registry))
    }

    fun timerAssemble(): Timer.Sample = Timer.start(registry)

    fun recordAssemble(sample: Timer.Sample, tenantId: String) {
        sample.stop(Timer.builder("retainiq.pipeline.assemble.duration")
            .tag("tenant", tenantId)
            .publishPercentileHistogram()
            .register(registry))
    }

    // --- Cache Metrics ---

    fun cacheHit(entity: String, tenantId: String) {
        registry.counter("retainiq.cache.hit", "entity", entity, "tenant", tenantId).increment()
    }

    fun cacheMiss(entity: String, tenantId: String) {
        registry.counter("retainiq.cache.miss", "entity", entity, "tenant", tenantId).increment()
    }

    // --- Churn Score Distribution ---

    fun recordChurnScore(score: Double, band: String, tenantId: String) {
        DistributionSummary.builder("retainiq.churn.score")
            .tag("band", band)
            .tag("tenant", tenantId)
            .publishPercentileHistogram()
            .register(registry)
            .record(score)
    }

    // --- Business Metrics ---

    fun recordDecision(tenantId: String, channel: String, churnBand: String, degraded: Boolean, offerCount: Int) {
        registry.counter("retainiq.decide.total",
            "tenant", tenantId,
            "channel", channel,
            "churn_band", churnBand,
            "degraded", degraded.toString()
        ).increment()

        registry.counter("retainiq.offers.shown",
            "tenant", tenantId
        ).increment(offerCount.toDouble())
    }

    fun recordOutcome(tenantId: String, outcome: String, sku: String) {
        registry.counter("retainiq.outcome.total",
            "tenant", tenantId,
            "result", outcome,
            "sku", sku
        ).increment()
    }

    fun recordCatalogSync(tenantId: String, productCount: Int, event: String) {
        registry.counter("retainiq.catalog.sync",
            "tenant", tenantId,
            "event", event
        ).increment()
        registry.gauge("retainiq.catalog.products", Tags.of("tenant", tenantId), productCount)
    }

    // --- SLO Tracking ---

    fun recordLatencySLO(latencyMs: Long, tenantId: String) {
        DistributionSummary.builder("retainiq.decide.latency.ms")
            .tag("tenant", tenantId)
            .tag("slo_met", (latencyMs <= 200).toString())
            .publishPercentileHistogram()
            .serviceLevelObjectives(50.0, 100.0, 150.0, 180.0, 200.0, 500.0)
            .register(registry)
            .record(latencyMs.toDouble())
    }
}
