package com.retainiq.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.retainiq.domain.Decision
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Async persistence layer for [Decision] audit records.
 *
 * Decisions are inserted into `platform.decisions` using R2DBC. The [saveAsync] method
 * launches a fire-and-forget coroutine on [Dispatchers.IO] so that persistence does not
 * block the request path. Records are retained for 24 months per regulatory requirements.
 *
 * @property databaseClient R2DBC database client
 * @property objectMapper Jackson mapper for serializing JSONB columns
 */
@Repository
class DecisionRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Persists a decision record asynchronously (fire-and-forget).
     *
     * Errors are logged but do not propagate to the caller.
     *
     * @param decision the decision to persist
     */
    fun saveAsync(decision: Decision) {
        scope.launch {
            try {
                databaseClient.sql("""
                    INSERT INTO platform.decisions (id, tenant_id, subscriber_hash, channel, churn_score, churn_band,
                        offers_ranked, rules_applied, degraded, confidence, latency_ms, created_at)
                    VALUES (:id, :tenantId, :subscriberHash, :channel, :churnScore, :churnBand,
                        :offersRanked::jsonb, :rulesApplied, :degraded, :confidence, :latencyMs, :createdAt)
                """.trimIndent())
                    .bind("id", decision.id)
                    .bind("tenantId", decision.tenantId)
                    .bind("subscriberHash", decision.subscriberHash)
                    .bind("channel", decision.channel.name)
                    .bind("churnScore", decision.churnScore)
                    .bind("churnBand", decision.churnBand.name)
                    .bind("offersRanked", objectMapper.writeValueAsString(decision.offersRanked))
                    .bind("rulesApplied", decision.rulesApplied.toTypedArray())
                    .bind("degraded", decision.degraded)
                    .bind("confidence", decision.confidence.name)
                    .bind("latencyMs", decision.latencyMs)
                    .bind("createdAt", decision.createdAt)
                    .fetch()
                    .rowsUpdated()
                    .subscribe()

                logger.debug { "Decision ${decision.id} persisted" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to persist decision ${decision.id}" }
            }
        }
    }

    /**
     * Looks up a decision by tenant and decision ID (for outcome validation).
     *
     * @param tenantId owning tenant
     * @param decisionId the decision to retrieve
     * @return the [Decision], or null if not found (simplified stub)
     */
    suspend fun findById(tenantId: UUID, decisionId: UUID): Decision? {
        // Implementation for outcome validation
        return null // Simplified — full implementation reads from DB
    }
}
